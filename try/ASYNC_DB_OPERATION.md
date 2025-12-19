# 异步存库机制详细分析

## 一、流程概述

你的分析基本正确！异步存库机制确实是通过Redis队列实现的。让我详细分析并验证：

### 1.1 核心流程

```
业务层调用EntityService
  ↓
EntityAysncServiceProxy代理拦截
  ↓
AsyncDbRegisterCenter注册到Redis
  ├─→ Redis List: EntityClassName#unionKey (玩家级别的操作队列)
  └─→ Redis Set: ay_db#dbId#EntityClassName (表级别的玩家key集合)
  ↓
AsyncDbOperationCenter定时任务（每5秒）
  ↓
AsyncDbOperation.run()消费
  ├─→ 从Redis Set中spop取出playerKey
  ├─→ TransactionService加锁（Redis锁）
  ├─→ 从Redis List中lpop取出操作数据
  └─→ 执行数据库操作
```

---

## 二、详细流程分析

### 2.1 数据入队阶段

**触发点**: 业务层调用`EntityService.insertEntity/updateEntity/deleteEntity`

**代理拦截**: `EntityAysncServiceProxy.intercept()`
- 检查`@DbOperation`注解
- 如果实体实现了`AsyncSave`接口，调用异步存储

**注册到Redis**: `AsyncDbRegisterCenter.asyncEntity()`

**Redis数据结构**:
1. **玩家级别队列（List）**: `EntityClassName#unionKey`
   - 存储该玩家的所有操作（按顺序）
   - 使用`rPushString()`从右侧入队
   - 保证同一玩家的操作顺序

2. **表级别集合（Set）**: `ay_db#dbId#EntityClassName`
   - 存储需要保存的玩家key集合
   - 使用`saddString()`添加
   - 用于快速查找哪些玩家有数据需要保存

**关键代码**:
```java
// 必须先push再sadd
String aysncUnionKey = simapleClassName + '#' + unionKey;
redisService.rPushString(aysncUnionKey, asyncEntityWrapper.serialize());  // List入队
redisService.saddString(AsyncRedisKeyEnum.ASYNC_DB.getKey() + dbSelectId + '#' + entity.getClass().getSimpleName(), aysncUnionKey);  // Set添加
```

### 2.2 定时任务启动

**启动位置**: `AsyncDbOperationCenter.startup()`

**线程池**: `ScheduledExecutorService`（带缓存的线程池）
- 线程数: `asyncDbOperationSaveWorkerSize`（配置项，默认1）
- 调度间隔: 每5秒执行一次

**任务注册**: 为每个`AsyncDbOperation`实现类注册定时任务

### 2.3 数据消费阶段

**定时执行**: `AsyncDbOperation.run()`（每5秒）

**消费流程**:
1. 遍历所有数据库（分库数量）
2. 从Redis Set中`spop`取出playerKey（原子操作，避免重复处理）
3. 使用`TransactionService`进行Redis加锁
4. 从Redis List中`lpop`取出操作数据（按顺序）
5. 反序列化`AsyncEntityWrapper`
6. 执行数据库操作（insert/update/delete）
7. 如果事务失败，将playerKey重新放回Set

**Redis加锁机制**:
- 使用`redis-game-transaction`库实现分布式锁
- 锁的key: `playerKey`（EntityClassName#unionKey）
- 确保同一玩家的操作不会被并发处理

---

## 三、PlantUML流程图

```plantuml
@startuml 异步存库完整流程
skinparam ParticipantPadding 20
skinparam BoxPadding 10

participant "业务层" as Business
participant "EntityService" as EntityService
participant "EntityAysncServiceProxy" as Proxy
participant "AsyncDbRegisterCenter" as RegisterCenter
database "Redis" as Redis {
  participant "List队列\nEntityClassName#unionKey" as RedisList
  participant "Set集合\nay_db#dbId#EntityClassName" as RedisSet
}
participant "AsyncDbOperationCenter" as OperationCenter
participant "ScheduledExecutorService" as ScheduledPool
participant "AsyncDbOperation" as AsyncOperation
participant "TransactionService" as TransactionService
participant "AsyncDBSaveTransactionEntity" as TransactionEntity
database "MySQL" as MySQL

== 数据入队阶段 ==

Business -> EntityService : insertEntity/updateEntity/deleteEntity
activate EntityService

EntityService -> Proxy : intercept() (CGLIB代理)
activate Proxy

Proxy -> Proxy : 检查@DbOperation注解\n检查AsyncSave接口
Proxy -> RegisterCenter : asyncRegisterEntity()
activate RegisterCenter

RegisterCenter -> RegisterCenter : 计算分库分表索引\ndbSelectId
RegisterCenter -> RegisterCenter : 获取unionKey\n(玩家唯一标识)
RegisterCenter -> RegisterCenter : 创建AsyncEntityWrapper\n(序列化操作数据)

RegisterCenter -> RedisList : rPushString(aysncUnionKey, serializedData)
note right: 玩家级别队列\n保证操作顺序
RedisList -> RedisList : 数据入队

RegisterCenter -> RedisSet : saddString(dbRedisKey, aysncUnionKey)
note right: 表级别集合\n标记需要保存的玩家
RedisSet -> RedisSet : 添加到集合

deactivate RegisterCenter
deactivate Proxy
deactivate EntityService

== 定时任务启动（服务器启动时） ==

OperationCenter -> ScheduledPool : startup()\nnewScheduledThreadPool(workerSize)
activate ScheduledPool

OperationCenter -> OperationCenter : 获取所有AsyncDbOperation实现类
loop 每个AsyncDbOperation
  OperationCenter -> ScheduledPool : scheduleAtFixedRate(asyncDbOperation, 0, 5, SECONDS)
  note right: 每5秒执行一次
end

deactivate ScheduledPool

== 数据消费阶段（定时执行） ==

ScheduledPool -> AsyncOperation : run() (每5秒)
activate AsyncOperation

AsyncOperation -> AsyncOperation : 遍历所有数据库(dbCount)
loop 每个数据库
  AsyncOperation -> RedisSet : scardString(dbRedisKey)\n获取待处理数量
  RedisSet -> AsyncOperation : saveSize
  
  loop 处理每个playerKey
    AsyncOperation -> RedisSet : spopString(dbRedisKey)\n原子弹出playerKey
    RedisSet -> AsyncOperation : playerKey
    
    alt playerKey为空
      AsyncOperation -> AsyncOperation : break
    else playerKey不为空
      AsyncOperation -> TransactionService : commitTransaction()\n创建事务实体
      activate TransactionService
      
      TransactionService -> TransactionEntity : 创建AsyncDBSaveTransactionEntity\n(使用playerKey作为锁key)
      activate TransactionEntity
      
      TransactionService -> Redis : 尝试获取Redis锁\n(基于playerKey)
      note right: redis-game-transaction\n分布式锁机制
      
      alt 获取锁成功
        TransactionEntity -> RedisList : lpop(playerKey)\n从队列取出操作数据
        RedisList -> TransactionEntity : serializedData
        
        loop 处理队列中的所有操作
          TransactionEntity -> TransactionEntity : deserialize()\n反序列化AsyncEntityWrapper
          TransactionEntity -> TransactionEntity : saveAsyncEntityWrapper()
          
          alt 操作类型判断
            TransactionEntity -> EntityService : insertEntity()
            TransactionEntity -> EntityService : updateEntity()
            TransactionEntity -> EntityService : deleteEntity()
          end
          
          EntityService -> MySQL : 执行SQL操作
          MySQL -> EntityService : 返回结果
          
          TransactionEntity -> TransactionEntity : asyncDbOperationMonitor.monitor()\n性能监控
        end
        
        TransactionService -> Redis : 释放Redis锁
        TransactionService -> AsyncOperation : SUCCESS
        
      else 获取锁失败
        TransactionService -> AsyncOperation : FAIL
        AsyncOperation -> RedisSet : saddString(dbRedisKey, playerKey)\n重新放回集合
        note right: 下次继续处理
      end
      
      deactivate TransactionEntity
      deactivate TransactionService
    end
  end
end

AsyncOperation -> AsyncOperation : asyncDbOperationMonitor.printInfo()\n打印统计信息

deactivate AsyncOperation

@enduml
```

---

## 四、Redis数据结构详解

### 4.1 玩家级别队列（List）

**Key格式**: `EntityClassName#unionKey`

**示例**: `PlayerEntity#playerId_12345`

**存储内容**: 序列化后的`AsyncEntityWrapper`对象

**操作**:
- **入队**: `rPushString()` - 从右侧入队，保证顺序
- **出队**: `lpop()` - 从左侧出队，FIFO顺序

**作用**: 保证同一玩家的所有操作按顺序执行

### 4.2 表级别集合（Set）

**Key格式**: `ay_db#dbId#EntityClassName`

**示例**: `ay_db#0#PlayerEntity`

**存储内容**: 玩家级别的key（`EntityClassName#unionKey`）

**操作**:
- **添加**: `saddString()` - 添加到集合
- **弹出**: `spopString()` - 原子弹出，避免重复处理

**作用**: 快速查找哪些玩家有数据需要保存

### 4.3 Redis锁

**Key格式**: `playerKey`（即`EntityClassName#unionKey`）

**实现**: 使用`redis-game-transaction`库

**作用**: 确保同一玩家的操作不会被并发处理

---

## 五、关键代码位置

| 组件 | 文件路径 | 关键方法 |
|------|----------|----------|
| **代理拦截** | `game-db/.../proxy/EntityAysncServiceProxy.java` | `intercept()` |
| **注册中心** | `game-db/.../async/AsyncDbRegisterCenter.java` | `asyncEntity()` |
| **操作中心** | `game-db/.../async/AsyncDbOperationCenter.java` | `startup()` |
| **定时任务** | `game-db/.../async/thread/AsyncDbOperation.java` | `run()`, `saveDb()` |
| **事务实体** | `game-db/.../transaction/entity/AsyncDBSaveTransactionEntity.java` | `commit()` |
| **数据包装** | `game-db/.../async/AsyncEntityWrapper.java` | `serialize()`, `deserialize()` |

---

## 六、设计亮点

1. **双重队列设计**:
   - List队列：保证同一玩家的操作顺序
   - Set集合：快速查找待处理玩家

2. **分布式锁机制**:
   - 使用Redis锁避免并发冲突
   - 事务失败自动重试

3. **分库分表支持**:
   - 按数据库ID分组处理
   - 支持多数据库并行处理

4. **定时批量处理**:
   - 每5秒批量处理一次
   - 减少数据库压力

5. **容错机制**:
   - 事务失败自动放回队列
   - 支持重试机制

---

## 七、验证你的分析

✅ **正确**: 对同一个表的操作行为存放在redis队列中
- List队列：`EntityClassName#unionKey`
- Set集合：`ay_db#dbId#EntityClassName`

✅ **正确**: 服务器启动时启动带缓存的线程池
- `ScheduledExecutorService`（定时线程池）
- 每5秒执行一次

✅ **正确**: 线程池从redis中取出操作行为
- `spopString()`从Set中取出playerKey
- `lpop()`从List中取出操作数据

✅ **正确**: 更新到对应数据库表中
- `EntityService.insertEntity/updateEntity/deleteEntity`

✅ **正确**: 通过redis加锁
- `TransactionService`使用`redis-game-transaction`实现分布式锁

---

## 八、数据查询处理机制（解决异步存库期间的数据一致性问题）

### 8.1 问题背景

在异步存库模式下，存在一个关键问题：
- **写入操作**：先放入Redis队列，然后异步落地到MySQL（有延迟）
- **查询操作**：如果直接查询MySQL，可能读到旧数据（因为异步落地还没完成）
- **如果查询Redis**：虽然数据是最新的，但可能担心效率问题

### 8.2 解决方案：写入时先更新Redis缓存

系统采用**"写入即缓存"**的策略来解决这个问题：

#### 8.2.1 写入流程（关键：先更新Redis，再入队）

```80:90:game-db/src/main/java/com/snowcattle/game/db/service/proxy/EntityAysncServiceProxy.java
                case query:
                    abstractEntity = (AbstractEntity) args[0];
                    if (abstractEntity != null) {
                        if (abstractEntity instanceof RedisInterface) {
                            RedisInterface redisInterface = (RedisInterface) abstractEntity;
                            result = redisService.getObjectFromHash(EntityUtils.getRedisKey(redisInterface), abstractEntity.getClass());
                        } else {
                            proxyLogger.error("query interface RedisListInterface " + abstractEntity.getClass().getSimpleName() + " use RedisInterface " + abstractEntity.toString());
                        }
                    }
                    break;
```

**关键代码**（写入时先更新Redis）：

```49:62:game-db/src/main/java/com/snowcattle/game/db/service/proxy/EntityAysncServiceProxy.java
                case insert:
                    AbstractEntity abstractEntity = (AbstractEntity) args[0];
                    EntityUtils.updateAllFieldEntity(redisService, abstractEntity);
                    asyncSaveEntity((EntityService) obj, dbOperationEnum, abstractEntity);
                    break;
                case insertBatch:
                    List<AbstractEntity> entityList = (List<AbstractEntity>) args[0];
                    EntityUtils.updateAllFieldEntityList(redisService, entityList);
                    asyncBatchSaveEntity((EntityService)obj, dbOperationEnum, entityList);
                    break;
                case update:
                    abstractEntity = (AbstractEntity) args[0];
                    EntityUtils.updateChangedFieldEntity(redisService, abstractEntity);
                    asyncSaveEntity((EntityService) obj, dbOperationEnum, abstractEntity);
                    break;
```

**流程说明**：
1. **insert操作**：先调用`EntityUtils.updateAllFieldEntity()`更新Redis缓存（所有字段）
2. **update操作**：先调用`EntityUtils.updateChangedFieldEntity()`更新Redis缓存（只更新变化字段）
3. **delete操作**：先调用`EntityUtils.deleteEntity()`删除Redis缓存
4. **然后**：调用`asyncSaveEntity()`将操作放入异步队列

#### 8.2.2 查询流程（只查Redis，不查数据库）

```80:90:game-db/src/main/java/com/snowcattle/game/db/service/proxy/EntityAysncServiceProxy.java
                case query:
                    abstractEntity = (AbstractEntity) args[0];
                    if (abstractEntity != null) {
                        if (abstractEntity instanceof RedisInterface) {
                            RedisInterface redisInterface = (RedisInterface) abstractEntity;
                            result = redisService.getObjectFromHash(EntityUtils.getRedisKey(redisInterface), abstractEntity.getClass());
                        } else {
                            proxyLogger.error("query interface RedisListInterface " + abstractEntity.getClass().getSimpleName() + " use RedisInterface " + abstractEntity.toString());
                        }
                    }
                    break;
```

**关键点**：
- **只从Redis查询**：`redisService.getObjectFromHash()`
- **不查数据库**：与同步模式不同，异步模式下如果Redis中没有数据，直接返回`null`，不会回退到数据库查询

#### 8.2.3 对比：同步模式 vs 异步模式

**同步模式（EntityServiceProxy）**：
```64:81:game-db/src/main/java/com/snowcattle/game/db/service/proxy/EntityServiceProxy.java
                case query:
                    abstractEntity = (AbstractEntity) args[0];
                    if (abstractEntity != null) {
                        if (abstractEntity instanceof RedisInterface) {
                            RedisInterface redisInterface = (RedisInterface) abstractEntity;
                            result = redisService.getObjectFromHash(EntityUtils.getRedisKey(redisInterface), abstractEntity.getClass());
                        } else {
                            proxyLogger.error("query interface RedisListInterface " + abstractEntity.getClass().getSimpleName() + " use RedisInterface " + abstractEntity.toString());
                        }
                    }
                    if (result == null) {
                        result = methodProxy.invokeSuper(obj, args);
                        if(result != null){
                            abstractEntity = (AbstractEntity) result;
                            EntityUtils.updateAllFieldEntity(redisService, abstractEntity);
                        }
                    }
                    break;
```
- 先查Redis，如果miss则查数据库，然后更新缓存

**异步模式（EntityAysncServiceProxy）**：
- 只查Redis，不查数据库
- 如果Redis中没有数据，返回`null`

### 8.3 设计优势

1. **数据一致性保证**：
   - 写入时先更新Redis，确保Redis中的数据始终是最新的
   - 查询时只查Redis，读取的一定是最新数据
   - 即使异步落地还没完成，也不影响查询准确性

2. **性能优势**：
   - Redis查询速度快（内存操作）
   - 避免了数据库查询的开销
   - 解决了"查询Redis效率低"的担忧（实际上Redis查询非常快）

3. **避免数据不准确**：
   - 不会出现查询数据库导致数据不准确的问题
   - 因为根本不查数据库，只查Redis

### 8.4 潜在风险与应对

**风险**：如果Redis缓存丢失或失效，数据就查不到了

**应对措施**（系统设计假设）：
1. **Redis持久化**：通常配置了RDB或AOF持久化
2. **热数据特性**：游戏数据通常都是热数据，会频繁更新，Redis中一般都有数据
3. **冷启动恢复**：如果真的Redis丢失，可以通过其他机制恢复（比如从数据库重新加载到Redis）

### 8.5 异步写入与查询完整流程图

```
写入操作：
业务层 -> EntityService.insertEntity()
  ↓
EntityAysncServiceProxy.intercept()
  ↓
【关键】先更新Redis缓存
  ├─→ EntityUtils.updateAllFieldEntity(redisService, entity)
  └─→ Redis缓存已更新（最新数据）
  ↓
然后放入异步队列
  ├─→ asyncSaveEntity()
  └─→ AsyncDbRegisterCenter.asyncRegisterEntity()
  └─→ Redis队列（等待异步落地）

查询操作：
业务层 -> EntityService.getEntity()
  ↓
EntityAysncServiceProxy.intercept()
  ↓
【关键】只查Redis，不查数据库
  ├─→ redisService.getObjectFromHash()
  └─→ 返回Redis中的数据（最新数据）
  ↓
如果Redis中没有数据 -> 返回null
（不会回退到数据库查询）
```

### 8.6 总结

**系统完美解决了你提出的两个担忧**：

1. ✅ **不会查询数据库导致数据不准确**：
   - 异步模式下，查询时根本不查数据库，只查Redis
   - 因为写入时先更新Redis，所以Redis中的数据一定是最新的

2. ✅ **Redis查询效率高**：
   - Redis是内存数据库，查询速度非常快
   - 避免了数据库查询的开销
   - 实际上比查询数据库更高效

**核心设计理念**：
- **写入即缓存**：写入时立即更新Redis，保证Redis中的数据是最新的
- **查询只查缓存**：查询时只查Redis，不查数据库，保证数据准确性
- **异步落地**：数据库操作异步进行，不影响业务响应速度

---

## 九、优化建议

1. **批量处理**: 可以一次处理多个playerKey，提高吞吐量
2. **动态调整**: 根据队列长度动态调整处理频率
3. **监控告警**: 队列积压时发送告警
4. **失败重试**: 增加重试次数限制，避免无限重试
5. **缓存预热**: 服务器启动时，可以从数据库加载热数据到Redis
6. **缓存降级**: 如果Redis不可用，可以考虑降级到同步模式（查数据库）

