# NettyGameServer 消息流动详细流程图

## 文档目标
- 把 TCP 消息从“网络入站”到“业务处理并响应”的完整链路可视化。
- 按阶段拆图，降低复杂度，便于定位问题与学习源码。
- 每个阶段都标注关键类与核心方法。

## 总览分阶段
- 阶段A：连接建立与入站入口
- 阶段B：消息解码（ByteBuf -> 具体消息对象）
- 阶段C：消息分发（Pipeline -> Session 队列）
- 阶段D：消息处理与响应（Facade -> Handler -> writeAndFlush）

---

## 阶段A：连接建立与入站入口

```mermaid
flowchart TD
    A1[客户端发送TCP包] --> A2[Netty Channel Pipeline]
    A2 --> A3[LengthFieldBasedFrameDecoder\n按帧拆包]
    A3 --> A4[NetProtoBufMessageTCPDecoder.decode]
    A4 --> A5[GameNetMessageTcpServerHandler.channelRead]
    A5 --> A6[DefaultTcpServerPipeLine.dispatchAction]

    A7[连接建立时创建NettyTcpSession] --> A8[NetTcpSessionLoopUpService保存session]
    A8 --> A6
```

### 关键点
- `GameNetMessageTcpServerHandler` 只做转发：把已解码消息交给 `DefaultTcpServerPipeLine`。
- `NettyTcpSession` 在连接建立时注册，后续分发阶段依赖它做会话绑定。

---

## 阶段B：消息解码（ByteBuf -> Message）

```mermaid
flowchart TD
    B1[NetProtoBufMessageTCPDecoder.decode] --> B2[iNetMessageDecoderFactory.praseMessage]
    B2 --> B3[NetProtoBufTcpMessageDecoderFactory.praseMessage]
    B3 --> B4[读取包头\nlength/version/cmd/serial]
    B4 --> B5[MessageRegistry.getMessage cmd]
    B5 --> B6[创建具体消息类实例\n例如 OnlineLoginClientTcpMessage]
    B6 --> B7[读取body bytes]
    B7 --> B8[message.decoderNetProtoBufMessageBody]
    B8 --> B9[返回AbstractNetProtoBufMessage]
```

### 关键点
- `cmd` 是解码阶段最核心索引键。
- `MessageRegistry` 完成 `cmd -> 消息类` 的第一层映射。
- 每个消息类自行实现 protobuf 反序列化逻辑（`decoderNetProtoBufMessageBody`）。

---

## 阶段C：消息分发（Pipeline -> Session 队列）

```mermaid
flowchart TD
    C1[DefaultTcpServerPipeLine.dispatchAction] --> C2[根据channel取sessionId]
    C2 --> C3[NetTcpSessionLoopUpService.lookup]
    C3 --> C4[得到NettyTcpSession]
    C4 --> C5[校验messageCommand.bo_id\n是否当前服可处理]
    C5 --> C6[消息挂载属性\nDISPATCH_SESSION=session]
    C6 --> C7[GameTcpMessageProcessor.directPutTcpMessage]
    C7 --> C8[QueueTcpMessageExecutorProcessor.directPutTcpMessage]
    C8 --> C9[NetMessageTcpDispatchLogic.dispatchTcpMessage]
    C9 --> C10[clientSession.addNetMessage]
    C10 --> C11[进入NetProtoBufMessageProcess队列]
```

### 关键点
- 这一阶段的本质是“绑定上下文 + 进入会话队列”。
- `DISPATCH_SESSION` 属性保证后续处理知道该写回哪个会话。

---

## 阶段D：消息处理与响应（Facade -> Handler -> writeAndFlush）

```mermaid
flowchart TD
    D1[NettyTcpSession.update] --> D2[NetProtoBufMessageProcess.processNetMessage]
    D2 --> D3[NetMessageProcessLogic.processMessage]
    D3 --> D4[GameFacade.dispatch]
    D4 --> D5[按cmd获取IMessageHandler]
    D5 --> D6[反射调用目标方法\n例如 OnlineTcpHandlerImpl.handleOnlineLoginClientTcpMessage]
    D6 --> D7[得到响应消息AbstractNetProtoBufMessage]
    D7 --> D8[回填serial]
    D8 --> D9[nettySession.write]
    D9 --> D10[channel.writeAndFlush]
    D10 --> D11[客户端收到响应包]
```

### 关键点
- `GameFacade` 完成第二层映射：`cmd -> handler method`。
- `serial` 回填用于请求-响应对齐。
- 最终统一由 `NettySession.write` 执行网络发送。

---

## 全链路时序图（精简版）

```mermaid
sequenceDiagram
    participant C as 客户端
    participant P as Netty Pipeline
    participant D as TCP解码工厂
    participant L as DefaultTcpServerPipeLine
    participant Q as Session消息队列
    participant F as GameFacade
    participant H as 业务Handler
    participant S as NettySession

    C->>P: 发送TCP二进制包
    P->>D: ByteBuf
    D->>D: 读取cmd并实例化消息类
    D-->>L: AbstractNetProtoBufMessage
    L->>L: 绑定DISPATCH_SESSION并校验路由
    L->>Q: addNetMessage
    Q->>F: processMessage -> dispatch
    F->>H: 根据cmd反射调用
    H-->>F: 返回响应消息
    F-->>S: nettySession.write(response)
    S-->>C: channel.writeAndFlush(response)
```

---

## 快速排障定位建议
- 解码异常：优先看 `NetProtoBufTcpMessageDecoderFactory.praseMessage` 的 `cmd` 与 body 解析。
- 分发不到 Handler：检查消息类与处理器方法的 `@MessageCommandAnnotation(command=...)` 是否一致。
- 有处理无回包：检查 `NetMessageProcessLogic` 中返回值是否为空、`NettySession.write` 是否抛异常。
- 回包错乱：检查 `serial` 回填与客户端关联逻辑。
