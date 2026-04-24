# NettyGameServer 发现记录

## 约定
- 只记录与“消息链路、缓存、落地”相关的信息。
- 每条发现尽量附带文件路径，便于回看。

## 当前发现
- 消息链路采用双层分发：
  - 第一层：`cmd -> Message类`，由 `MessageRegistry` 扫描消息类注解完成。
  - 第二层：`cmd -> Handler方法`，由 `GameFacade` 扫描处理器方法注解完成。
- TCP 主链路核心：
  - `GameNetProtoMessageTcpServerChannelInitializer`
  - `NetProtoBufMessageTCPDecoder`
  - `NetProtoBufTcpMessageDecoderFactory`
  - `GameNetMessageTcpServerHandler`
  - `DefaultTcpServerPipeLine`
  - `NetMessageProcessLogic`
  - `GameFacade`
- HTTP 与 TCP 差异：
  - HTTP 在 `HttpServerHandler` 中收到 `HttpContent` 后即走处理逻辑。
  - TCP 先入 `NettyTcpSession` 队列，再由 update 线程处理。
- 缓存与落地主链路：
  - 内存对象：`GamePlayerLoopUpService`、`NetTcpSessionLoopUpService`。
  - 脏标记：`EntityProxy`（`dirtyFlag` + `changeParamSet`）。
  - 异步注册：`EntityAysncServiceProxy` -> `AsyncDbRegisterCenter`（Redis list + set）。
  - 定时刷盘：`AsyncDbOperationCenter` 每 5 秒调度。
  - 执行落库：`AsyncDBSaveTransactionEntity` -> `EntityService` -> `IDBMapper`。
- 风险点（待重点验证）：
  - `lpop` 后失败重试链路不明显，可能有丢消息窗口。
  - Redis set/list 双写非原子导致悬挂风险。
  - 玩家对象 remove 路径不明显，可能存在生命周期管理风险。

## 阶段5补充：登录消息映射闭环
- 登录消息命令常量：
  - `MessageCommandIndex.ONLINE_LOGIN_TCP_CLIENT_MESSAGE = 5`
  - 文件：`game-core/src/main/java/com/snowcattle/game/service/message/command/MessageCommandIndex.java`
- 消息类映射（解码目标）：
  - `OnlineLoginClientTcpMessage` 上标注 `@MessageCommandAnnotation(command = MessageCommandIndex.ONLINE_LOGIN_TCP_CLIENT_MESSAGE)`
  - 文件：`game-core/src/main/java/com/snowcattle/game/message/logic/tcp/online/client/OnlineLoginClientTcpMessage.java`
- 处理器映射（业务执行）：
  - `OnlineTcpHandlerImpl.handleOnlineLoginClientTcpMessage(...)` 上标注相同命令注解。
  - 文件：`game-core/src/main/java/com/snowcattle/game/message/handler/impl/online/OnlineTcpHandlerImpl.java`
- 解码与分发闭环：
  - `NetProtoBufTcpMessageDecoderFactory` 读取 `cmd` 后调用 `MessageRegistry.getMessage(cmd)` 创建消息对象并反序列化。
  - `GameFacade.dispatch(...)` 根据同一 `cmd` 找到处理器并通过反射调用目标方法。
  - 因此同一命令值在“消息类注解”与“处理器方法注解”两端形成闭环映射。

## 阶段6补充：登录消息分发与回包闭环
- 入站转发：
  - `GameNetMessageTcpServerHandler.channelRead(...)` 将解码后的消息交给 `DefaultTcpServerPipeLine.dispatchAction(...)`。
- 分发前处理：
  - `DefaultTcpServerPipeLine` 绑定 `DISPATCH_SESSION`，并调用 `gameTcpMessageProcessor.directPutTcpMessage(...)`。
- 投递到会话队列：
  - `NetMessageTcpDispatchLogic.dispatchTcpMessage(...)` 从消息属性读取 `DISPATCH_SESSION`，执行 `clientSesion.addNetMessage(msg)`。
- 会话线程消费：
  - `NetProtoBufMessageProcess.processNetMessage()` 循环从队列取消息并调用 `NetMessageProcessLogic.processMessage(...)`。
- 业务执行与回包：
  - `NetMessageProcessLogic` 调用 `GameFacade.dispatch(message)`，获得响应后设置 `serial` 并执行 `nettySession.write(respone)`。
  - `GameFacade.dispatch(...)` 依据 `cmd` 命中 `IMessageHandler` 与对应 method（登录消息命中 `OnlineTcpHandlerImpl.handleOnlineLoginClientTcpMessage`）。
- 回包发送落点：
  - `NettySession.write(...)` 实际执行 `channel.writeAndFlush(msg)`。

## 阶段6.7补充：事件状态机设计取舍（Disruptor + EventBus）
- Create/Update/Finish 不是简单重复，而是把“注册、循环执行、销毁”三类生命周期动作显式建模，便于统一调度和可插拔执行器切换。
- 复杂度来源主要有三点：
  - 事件语义层（EventBus + Listener）与线程调度层（Dispatch + Worker）叠加；
  - Disruptor 路径前仍保留 `blockingQueue -> ringBuffer` 中转；
  - UpdateEvent 自回投形成循环，阅读时不易建立全局心智模型。
- 性能收益与成本并存：
  - 收益：高负载下调度开销更低、吞吐更高；
  - 成本：排障路径更长、理解门槛更高。
- 简化方向（不改变核心思想）：
  - 合并部分生命周期事件语义；
  - 收敛中转路径；
  - 强化“入口-状态-出口”文档与监控指标，降低维护复杂度。

## 阶段6.8补充：同玩家消息顺序保证与边界
- 当前 TCP 主链路下，“同一会话内”消息通常按入队顺序处理：
  - Netty 入站按 channel 串行回调；
  - 消息进入同一个 `NettyTcpSession` 队列；
  - `NetProtoBufMessageProcess.processNetMessage()` 按队列顺序消费。
- Disruptor 的并发消费主要发生在 `IUpdate` 层（不同 update/不同 session 之间），不是同一条消息被多个 handler 重复处理。
- 顺序风险边界主要在“玩家维度跨会话/跨入口/跨线程”：
  - 同一玩家多连接并发；
  - 玩家状态被旁路线程异步修改；
  - 不同协议入口未统一串行上下文。
- 规避建议：
  - 若要求“同玩家全局严格顺序”，采用 `playerId` 分片串行执行模型；
  - 所有玩家状态修改回到统一串行上下文；
  - 对关键链路加顺序与幂等监控（序号、延迟、重放）。

## 阶段7进展（第一段）：属性变更到异步入队
- 采用 `Order.status` 作为样本链路（测试实体）：
  - `Order.setStatus(...)` 带 `@MethodSaveProxy(proxy="status")`；
  - `Order` 同时实现 `RedisInterface` 与 `AsyncSave`，满足“先缓存后异步落库”条件。
- 标脏与变更采集：
  - `EntityProxy.intercept(...)` 在 `collectFlag=true` 时比较旧值与新值；
  - 若字段变化，则 `dirtyFlag=true` 且把字段写入 `changeParamSet`。
- 业务 update 调用后的代理行为：
  - `OrderService.updateOrder(...)` -> `EntityService.updateEntity(...)`（被 `EntityAysncServiceProxy` 拦截）；
  - `DbOperationEnum.update` 分支执行：
    - `EntityUtils.updateChangedFieldEntity(...)`（先写 Redis 变更字段）；
    - `asyncSaveEntity(...)` -> `AsyncDbRegisterCenter.asyncRegisterEntity(...)`（再入异步队列）。
- 异步注册中心关键动作：
  - 生成 `AsyncEntityWrapper`（update 时只记录变更字段 map）；
  - 计算分库索引与 `unionKey`；
  - `rPush(aysncUnionKey, serializedWrapper)`；
  - `sadd(ASYNC_DB#dbIndex#EntityClass, aysncUnionKey)`。
- 到这里链路完成了“本地变更 -> 缓存更新 -> 异步任务可见”，尚未真正落 MySQL。

## 阶段7.1补充：代理机制全景（异步链路前置）
- 异步链路确实是“以代理为入口”：
  - **实体代理**：`EntityProxyFactory` + `EntityProxy`（CGLIB）；
  - **服务代理（同步缓存）**：`EntityServiceProxyFactory` + `EntityServiceProxy`；
  - **服务代理（异步落库）**：`EntityAysncServiceProxyFactory` + `EntityAysncServiceProxy`。
- 实体代理负责“字段变更采集”：
  - 仅当 setter 带 `@MethodSaveProxy` 且 `collectFlag=true` 时采集；
  - 变化字段写入 `changeParamSet`，并置 `dirtyFlag=true`；
  - 后续 update 只基于变化字段写缓存/写库。
- 服务代理负责“统一拦截 DbOperation”：
  - `EntityServiceProxy.intercept(...)` 解析 `@DbOperation`；
  - 同步代理策略：先/后执行 DB，再更新 Redis（按操作类型分支）。
- 异步服务代理负责“先缓存，后异步注册”：
  - `EntityAysncServiceProxy.intercept(...)` 在 `update` 分支：
    - `EntityUtils.updateChangedFieldEntity(...)` 写缓存；
    - `asyncSaveEntity(...)` -> `AsyncDbRegisterCenter.asyncRegisterEntity(...)` 入异步队列。
- 工厂装配方式（关键）：
  - 三个 Factory 都用 CGLIB `Enhancer.setSuperclass + setCallback` 生成代理子类；
  - `BeanUtils.copyProperties(...)` 把原 service/entity 的属性拷贝到代理实例，保证代理对象可直接替换原对象使用。
- 直接结论：
  - 没有经过代理的实体/服务，不会自动获得“变更采集、缓存同步、异步落库注册”能力；
  - 因此排查“为什么没入异步队列”时，第一步应先确认对象是否为代理实例、方法是否命中 `@DbOperation` / `@MethodSaveProxy`。

## 阶段7补充：以 Order update 为例的双份 Redis 数据视角
- 在 `EntityAysncServiceProxy` 的 `update` 分支里（`EntityUtils.updateChangedFieldEntity(...)` + `asyncSaveEntity(...)`），会出现你说的“两份数据”现象：
  - 第一份：正常访问缓存（业务读写使用）
    - 由 `EntityUtils.updateChangedFieldEntity(...)` 写入；
    - key 形态是 `RedisKey + unionKey`（例如 `PLAYER + userId#orderId`）；
    - 存的是实体在缓存中的当前状态（hash 字段）。
  - 第二份：异步落库队列数据（临时）
    - 由 `AsyncDbRegisterCenter.asyncRegisterEntity(...)` 写入；
    - `rpush(aysncUnionKey, AsyncEntityWrapper.serialize())` 存的是待落库 payload；
    - 同时 `sadd(ASYNC_DB#dbIndex#EntityClass, aysncUnionKey)` 作为待处理索引。
- 细化说明：
  - 第二份更准确是“异步变更日志/任务数据”，update 场景通常是变化字段 map，不一定是完整 Order 全量镜像。
  - 这份数据在 `AsyncDBSaveTransactionEntity.commit()` 中被 `lpop` 消费后会自然减少；索引集合也会在调度消费后逐步清理。

## 阶段7进展（第二段）：异步任务消费与清理闭环
- 触发源：
  - `AsyncDbOperationCenter.startup()` 按配置启动 `scheduledExecutorService`；
  - 每个 `AsyncDbOperation` 以固定周期 `scheduleAtFixedRate(..., 0, 5, TimeUnit.SECONDS)` 执行。
- 扫描待处理索引集合（set）：
  - `AsyncDbOperation.saveDb(...)` 先算 `dbRedisKey = ay_db#dbId#EntityClass`；
  - `scard` 获取数量后循环 `spop(dbRedisKey)` 取出 `playerKey/unionKey`。
- 消费异步日志列表（list）并落库：
  - 为每个 `playerKey` 构建 `AsyncDBSaveTransactionEntity` 并走事务提交；
  - `AsyncDBSaveTransactionEntity.commit()` 内循环 `lpop(playerKey)`；
  - 每次 `lpop` 反序列化 `AsyncEntityWrapper`，根据 `DbOperationEnum` 调 `EntityService` 执行落库。
- 清理行为细化：
  - list 侧：`lpop` 成功即从列表移除，直到为空；
  - set 侧：`spop` 时就从索引集合移除当前 `playerKey`；
  - 当事务提交失败时，`AsyncDbOperation.saveDb(...)` 会把 `playerKey` `sadd` 回 set，等待下次重试。
- 关键结论：
  - “第二份 Redis 数据”确实是临时异步任务数据；
  - 正常路径下会被 `spop + lpop` 消费清理；
  - 异常路径下通过“set 回补”保证后续可重试，但 list 内单条失败后的重入策略较弱（需结合业务容错）。

## 阶段7.3补充：`AsyncDbOperation.saveDb` 锁与事务正确性评估
- 分析范围：`AsyncDbOperation.saveDb(...)` + `AsyncDBSaveTransactionEntity.commit(...)` + `EntityService.updateEntity(...)`。

### 1) 现有设计意图（正向）
- set（`ay_db#dbId#EntityClass`）做“待处理 playerKey 索引”；
- list（`playerKey`）做“待落库日志队列”；
- `saveDb` 先 `spop` 取一个 `playerKey`，再走 `transactionService.commitTransaction(...)`；
- 提交失败时把 `playerKey` `sadd` 回 set，等待后续轮次重试。

### 2) 锁粒度判断
- 锁语义来自 `redis-game-transaction`（外部库），本仓库未展开实现细节；
- 从调用参数看，锁实体围绕 `playerKey`（本项目中为 `Class#unionKey`）；
- 粒度上是“按实体主键/玩家键串行”，方向正确，可避免同键并发落库冲突。

### 3) 事务边界判断
- 当前“事务”主要是 Redis 事务锁层，不是数据库原子事务；
- DB 落库在 `saveAsyncEntityWrapper(...)` 内调用 `entityService.*` 执行；
- 因此这条链路本质是“分布式互斥 + 最终一致性”，不是严格 ACID 一致性事务。

### 4) 关键风险（高优先级）
- **风险A：`lpop` 后失败可能丢日志**
  - `AsyncDBSaveTransactionEntity.commit()` 先 `lpop`，后落库；
  - 若 `saveAsyncEntityWrapper` 失败，当前日志已弹出，代码无重入队逻辑，存在丢失窗口。
- **风险B：DB 失败可能被吞掉**
  - `EntityService.updateEntity(...)` 内部 catch 异常仅记录日志，不抛出；
  - 上层可能无法感知失败，`commitTransaction` 仍可能返回成功，导致“日志已消费但DB未更新”。
- **风险C：异常路径未全覆盖回补**
  - `saveDb(...)` 仅在 `commitResult != SUCCESS` 时回补 set；
  - 若 `commitTransaction(...)` 抛运行时异常，当前方法没有 try-catch 包裹该调用点，可能提前中断且不回补。
- **风险D：成功日志存在误导**
  - 当前在每轮末尾直接打印 `async save success`，即使前面 commit 失败也可能出现成功语义日志（不利排障）。

### 5) 结论：是否“使用正确”
- **方向上正确**：按键串行 + 异步批处理 + 失败回补思路是合理的。
- **实现上不够稳健**：在“日志出队时机、异常传播、失败重试闭环”上存在一致性漏洞，需要加固后才能认为可靠。

### 6) 最小改造建议（按收益排序）
- 1) 把 `lpop` 改为“处理成功后删除”（或失败时显式重入队）；
- 2) `EntityService` 层不要吞异常，至少向上抛出以驱动事务失败回补；
- 3) 给 `commitTransaction(...)` 调用外层补 try-catch，异常时强制 `sadd` 回补；
- 4) 调整成功日志只在 `commitResult == SUCCESS` 且无异常时打印；
- 5) 增加死信队列/失败计数，避免长期重试黑洞。

## 阶段7.4补充：端到端验收与排障清单（不改代码版）

### A. 端到端验收路径（以 Order.status 更新为例）
1. 构造代理实体并修改 `status`（触发 `@MethodSaveProxy`）。
2. 调用 `OrderService.updateOrder(...)`（命中 `EntityAysncServiceProxy` 的 `update` 分支）。
3. 验证缓存侧：
   - 业务缓存 key（`RedisKey + unionKey`）字段已更新；
   - 异步索引 set（`ay_db#dbId#Order`）出现对应 `playerKey`；
   - 异步日志 list（`playerKey`）存在 wrapper 数据。
4. 等待调度窗口（默认 5 秒），验证消费：
   - set 中该 `playerKey` 被 `spop` 消费；
   - list 逐步 `lpop` 变少/清空；
   - DB 对应行 `status` 更新成功。

### B. 快速定位顺序（建议固定按此排查）
1. **先看代理是否命中**：对象是否代理、方法是否有 `@DbOperation`/`@MethodSaveProxy`。
2. **再看缓存写入**：`EntityUtils.updateChangedFieldEntity(...)` 是否执行成功。
3. **再看入队**：`AsyncDbRegisterCenter.asyncRegisterEntity(...)` 的 `rpush/sadd` 是否生效。
4. **再看调度**：`AsyncDbOperationCenter` 定时任务是否触发，`AsyncDbOperation.run()` 是否执行。
5. **最后看落库**：`AsyncDBSaveTransactionEntity.commit()` 与 `EntityService.updateEntity()` 是否执行且无异常吞没。

### C. 重点观测指标（运行期）
- set 积压量：`scard(ay_db#dbId#EntityClass)`。
- list 积压量：`llen(playerKey)`。
- 单轮消费量：每次 `saveDb` 实际处理 `playerKey` 数。
- 落库错误数：`EntityService` 异常日志计数。
- 事务失败回补数：`commitResult != SUCCESS` 次数。

### D. 阶段7总收口结论
- 已完成从“字段变更”到“异步消费落库”的端到端机制拆解。
- 当前实现可满足基础最终一致性流程，但在异常传播与失败回补闭环上仍存在稳健性风险（已在阶段7.3标注）。

## 阶段8补充：缓存落地稳定性改造方案（仅方案，不改代码）

### 目标
- 在不推翻现有架构前提下，优先消除“日志丢失、失败吞没、回补不完整”三类高风险点。
- 建立可观测、可回放、可追责的异步落库保障体系。

### 改造分期（建议按优先级执行）

### 第一期：止血（高优先级，低改动）
- **异常传播修复**  
  - `EntityService.updateEntity(...)` 等关键落库路径不再吞异常，至少向上抛出到事务层。
- **回补兜底补全**  
  - 给 `commitTransaction(...)` 调用点增加外层 try-catch；出现异常时统一 `sadd` 回补 `playerKey`。
- **成功日志语义修正**  
  - 仅在 `commitResult == SUCCESS` 且无异常时记录成功日志，避免误导排障。
- **最小监控补齐**  
  - 增加 set/list 积压、落库失败数、回补次数、单轮消费量统计。

### 第二期：稳态（中优先级，中改动）
- **出队语义优化**  
  - 从“先 `lpop` 再处理”改为“成功后确认删除”或“失败重入队”，降低丢日志窗口。
- **幂等保障**  
  - 按 `entityKey + version/timestamp` 设计幂等写入，防止重试导致脏覆盖。
- **失败分类与重试策略**  
  - 区分可重试（锁冲突/短暂故障）与不可重试（数据非法）；
  - 引入指数退避，避免热点 key 高频重试。

### 第三期：增强（中高优先级，中高改动）
- **死信队列（DLQ）**  
  - 多次失败后转入死信，支持人工回放与审计。
- **落库流水追踪**  
  - 为异步 wrapper 增加 traceId/eventId，贯通“入队 -> 消费 -> 落库 -> 清理”。
- **统一治理面板**  
  - 对接监控看板：积压趋势、失败分布、重试成功率、平均落库时延。

### 验收标准（方案层）
- 不再出现“DB失败但日志被消费且无回补”的静默丢失。
- 异步队列在峰值后可回落，set/list 不长期堆积。
- 任意一条落库失败可追踪到具体 key、原因、重试轨迹与最终状态。

### 推荐推进顺序
1. 先完成第一期（止血）再上线；
2. 第二期按模块灰度（先 Order 类，再扩展到其他实体）；
3. 第三期与运维监控体系联动实施。
