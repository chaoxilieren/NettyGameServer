# OrderedQueuePoolExecutor 与 NonOrderedQueuePoolExecutor 深度分析

## 目录
1. [两个线程池类的核心区别](#两个线程池类的核心区别)
2. [为什么 OrderedQueuePoolExecutor 需要单独维护队列](#为什么-orderedqueuepoolexecutor-需要单独维护队列)
3. [OrderedQueuePoolExecutor 的链式执行机制](#orderedqueuepoolexecutor-的链式执行机制)
4. [线程绑定队列的疑问与解答](#线程绑定队列的疑问与解答)
5. [使用场景对比](#使用场景对比)

---

## 两个线程池类的核心区别

### OrderedQueuePoolExecutor（有序线程池）

**特点：**
- ✅ 保证相同 `orderId` 的任务按提交顺序执行
- 使用分片队列：每个 `orderId` 映射到固定队列，相同 `orderId` 的任务进入同一队列

**构造函数：**
```java
OrderedQueuePoolExecutor(String name, int handlerSize, int orderQueueMaxSize, RejectedExecutionHandler handler)
```

**核心方法：**
```java
void addTask(long orderId, Runnable task)
```

**使用场景：**
- `AsyncEventService`：按 `shardingId` 保证事件顺序处理
- `GameUdpMessageOrderProcessor`：按 `playerId` 保证玩家消息顺序处理

### NonOrderedQueuePoolExecutor（无序线程池）

**特点：**
- ❌ 不保证任务执行顺序
- 所有任务共享一个任务队列，由线程池并发执行

**构造函数：**
```java
NonOrderedQueuePoolExecutor(String name, int size)
NonOrderedQueuePoolExecutor(String name, int coreSize, int maxSize, RejectedExecutionHandler handler)
```

**核心方法：**
```java
void execute(Runnable task)
void executeWork(Runnable task)
```

**使用场景：**
- `UpdateExecutorService`：更新任务不需要保证顺序
- `DisruptorExecutorService`：Disruptor 工作线程池

### 对比总结

| 特性 | OrderedQueuePoolExecutor | NonOrderedQueuePoolExecutor |
|------|-------------------------|----------------------------|
| **顺序保证** | ✅ 相同 orderId 的任务按顺序执行 | ❌ 不保证顺序 |
| **队列结构** | 多个队列（每个 orderId 分片） | 单个共享队列 |
| **分片机制** | 根据 orderId 分片到固定队列 | 无分片，统一处理 |
| **适用场景** | 需要保证顺序的场景（如玩家消息） | 不需要顺序的场景（如更新任务） |
| **性能特点** | 可能负载不均（某些队列忙） | 负载均衡更好 |

---

## 为什么 OrderedQueuePoolExecutor 需要单独维护队列

### ThreadPoolExecutor 的标准队列机制限制

**ThreadPoolExecutor 的标准结构：**
```java
ThreadPoolExecutor {
    BlockingQueue<Runnable> workQueue;  // 只有一个共享队列
    Thread[] workers;                    // 多个工作线程
    
    // 所有任务都进入同一个队列
    void execute(Runnable task) {
        workQueue.offer(task);  // 所有任务混在一起
    }
    
    // 多个线程从同一个队列取任务
    void worker.run() {
        while (true) {
            Runnable task = workQueue.take();  // 任意线程可能取到任意任务
            task.run();
        }
    }
}
```

**问题：** 所有任务进入同一个队列，多个线程并发取任务，无法保证相同 `orderId` 的任务顺序执行。

### 为什么需要多个独立队列？

**场景示例：玩家 A (playerId=1001) 发送 3 条消息**

**使用 ThreadPoolExecutor（单队列）：**
```
任务队列: [消息1, 消息2, 消息3, 其他玩家消息...]
         ↓
线程1取到消息1 → 执行
线程2取到消息3 → 执行  ❌ 顺序乱了！
线程1取到消息2 → 执行
```

**使用 OrderedQueuePoolExecutor（多队列）：**
```
orderId = 1001 % 5 = 1

队列0: [其他玩家任务...]
队列1: [消息1, 消息2, 消息3]  ← 玩家A的所有消息都在这里
队列2: [其他玩家任务...]
队列3: [其他玩家任务...]
队列4: [其他玩家任务...]
         ↓
线程1只处理队列1 → 消息1 → 消息2 → 消息3  ✅ 保证顺序！
```

### 实际使用示例

```java
// AsyncEventService.java
long shardignId = event.getShardingId();
long shardingResult = shardingExpresson.getValue(shardignId);
orderedQueuePoolExecutor.addTask(shardingResult, new SingleEventWork(eventBus, event));

// GameUdpMessageOrderProcessor.java
long playerId = abstractNetProtoBufUdpMessage.getPlayerId();
long index = playerId % workSize;
orderedQueuePoolExecutor.addTask(index, new UdpWorker(msg));
```

---

## OrderedQueuePoolExecutor 的链式执行机制

### 关键发现：afterExecute 钩子方法

通过分析 `OrderedQueuePoolExecutor` 的 `afterExecute` 方法，发现它**几乎不使用 ThreadPoolExecutor 的标准队列**，而是通过链式执行机制保证顺序。

### afterExecute 方法分析

```java
protected void afterExecute(Runnable r, Throwable t) {
    super.afterExecute(r, t);
    AbstractWork work = (AbstractWork)r;
    TasksQueue<AbstractWork> queue = work.getTasksQueue();  // 从任务对象获取队列引用
    
    if (queue != null) {
        AbstractWork afterWork = null;
        synchronized(queue) {  // 同步保护队列操作
            afterWork = (AbstractWork)queue.poll();  // 取出下一个任务
            if (afterWork == null) {
                queue.setProcessingCompleted(true);  // 标记队列处理完成
            }
        }
        
        if (afterWork != null) {
            this.execute(afterWork);  // 继续执行下一个任务（链式调用）
        }
    } else {
        this.logger.error("执行队列为空");
    }
}
```

### 核心机制：任务对象携带队列引用

**关键点：**
1. 每个 `AbstractWork` 任务对象都保存了它所属的 `TasksQueue` 引用
2. `afterExecute` 从任务对象中获取队列引用：`work.getTasksQueue()`
3. 从同一个队列中取出下一个任务，形成链式执行

### addTask 方法的逻辑（推断）

```java
public void addTask(long orderId, Runnable task) {
    // 1. 根据 orderId 找到或创建对应的队列
    TasksQueue<AbstractWork> queue = getOrCreateQueue(orderId);
    
    // 2. 将任务放入队列（而不是直接 execute）
    queue.offer((AbstractWork)task);
    
    // 3. 设置任务对象的队列引用
    ((AbstractWork)task).setTasksQueue(queue);
    
    // 4. 关键：只有当队列之前是空闲状态时，才启动第一个任务
    synchronized(queue) {
        if (queue.isProcessingCompleted()) {  // 队列之前是空的
            AbstractWork firstTask = queue.poll();
            this.execute(firstTask);  // 只启动第一个任务
            queue.setProcessingCompleted(false);
        }
        // 如果队列正在处理中，任务已经在队列中等待，不需要启动
    }
}
```

### 执行流程详解

```
场景：orderId=1 的 3 个任务依次提交

┌─────────────────────────────────────────────────────────┐
│ addTask(1, 任务A)                                        │
│   → 找到队列1                                            │
│   → 任务A 放入队列1                                      │
│   → 设置任务A.tasksQueue = 队列1                        │
│   → 队列1 是空的，execute(任务A) 启动                   │
└─────────────────────────────────────────────────────────┘
                    ↓
┌─────────────────────────────────────────────────────────┐
│ ThreadPoolExecutor.execute(任务A)                       │
│   → 任务A 进入 workQueue                                 │
│   → 线程1 从 workQueue 取到任务A                        │
│   → 执行任务A.run()                                     │
└─────────────────────────────────────────────────────────┘
                    ↓
┌─────────────────────────────────────────────────────────┐
│ addTask(1, 任务B) - 在任务A执行期间提交                 │
│   → 任务B 放入队列1                                     │
│   → 设置任务B.tasksQueue = 队列1                        │
│   → 队列1 正在处理中，不启动新任务                      │
│   → 任务B 在队列1中等待                                 │
└─────────────────────────────────────────────────────────┘
                    ↓
┌─────────────────────────────────────────────────────────┐
│ afterExecute(任务A, null)                               │
│   → 从任务A.getTasksQueue() 获取队列1                   │
│   → 从队列1.poll() 取出任务B                            │
│   → execute(任务B) - 继续执行                           │
│   → 注意：此时线程1还在执行 afterExecute，              │
│           任务B 可能被线程1继续执行（线程复用）          │
└─────────────────────────────────────────────────────────┘
                    ↓
┌─────────────────────────────────────────────────────────┐
│ afterExecute(任务B, null)                               │
│   → 从任务B.getTasksQueue() 获取队列1                   │
│   → 从队列1.poll() 取出任务C                            │
│   → execute(任务C) - 继续执行                           │
└─────────────────────────────────────────────────────────┘
```

### 设计要点

1. **链式执行**：任务执行完立即从队列取下一个，形成链式调用
2. **线程绑定**：每个队列由固定线程处理，保证顺序
3. **同步保护**：使用 `synchronized(queue)` 保证队列操作的线程安全
4. **状态标记**：队列为空时设置 `processingCompleted`，用于触发新任务启动

### 与标准 ThreadPoolExecutor 的对比

| 特性 | ThreadPoolExecutor | OrderedQueuePoolExecutor |
|------|-------------------|-------------------------|
| **队列使用** | workQueue 存储所有任务 | workQueue 仅用于启动，orderQueues 存储任务 |
| **任务调度** | 多线程竞争取任务 | 每个队列固定线程，链式执行 |
| **顺序保证** | ❌ 无法保证 | ✅ 相同 orderId 保证顺序 |
| **线程模型** | 线程池线程从共享队列取 | 线程执行完任务后，从专属队列取下一个 |

---

## 线程绑定队列的疑问与解答

### 疑问

**问题：** 如果所有任务都通过 `execute()` 提交给 ThreadPoolExecutor，多个线程会竞争从 `workQueue` 取任务，那怎么保证一个线程绑定一个队列？

### 解答

**关键理解：不是"一个线程绑定一个队列"，而是"一个队列的任务链式执行"**

#### 1. 任务对象携带队列引用

每个任务对象都保存了它所属的队列引用：
```java
AbstractWork work = (AbstractWork)r;
TasksQueue<AbstractWork> queue = work.getTasksQueue();  // 任务对象中保存了队列引用！
```

#### 2. 链式执行机制

虽然任务通过 `execute()` 提交，但通过 `afterExecute` 钩子实现链式执行：
- 任务A执行完 → `afterExecute` 从队列1取任务B → `execute(任务B)`
- 任务B执行完 → `afterExecute` 从队列1取任务C → `execute(任务C)`
- 形成链式调用，保证顺序

#### 3. 线程复用

虽然理论上存在竞争，但实际中：
- `afterExecute` 在同一个线程中执行
- 立即 `execute(nextTask)` 后，由于线程刚执行完任务，很可能立即从 `workQueue` 中取到刚提交的任务
- 即使被其他线程取到，由于任务对象中保存了队列引用，`afterExecute` 仍然会从正确的队列取下一个任务

#### 4. 更精确的设计

```java
// addTask 只启动第一个任务
// 后续任务都在队列中等待
// afterExecute 链式执行，保证顺序

addTask(1, A) → execute(A) → 线程1执行A
addTask(1, B) → 放入队列1，等待
addTask(1, C) → 放入队列1，等待

线程1: A → afterExecute → 从队列1取B → execute(B) → 线程1继续执行B
线程1: B → afterExecute → 从队列1取C → execute(C) → 线程1继续执行C
```

### 总结

- ✅ **任务对象携带队列引用**：`work.getTasksQueue()` 总是返回同一个队列
- ✅ **链式执行**：`afterExecute` 从同一个队列取下一个任务
- ✅ **线程复用**：虽然通过 `execute()` 提交，但通过链式调用和线程复用，大概率由同一线程连续执行
- ✅ **只启动第一个任务**：`addTask` 只启动第一个任务，后续任务在队列中等待链式执行

这种设计通过**任务对象携带队列引用 + afterExecute 钩子**实现链式执行，从而保证顺序。

---

## 使用场景对比

### OrderedQueuePoolExecutor 使用场景

**1. 玩家消息处理（GameUdpMessageOrderProcessor）**
```java
long playerId = abstractNetProtoBufUdpMessage.getPlayerId();
long index = playerId % workSize;
orderedQueuePoolExecutor.addTask(index, new UdpWorker(msg));
```
- **需求**：保证同一玩家的消息按顺序处理
- **原因**：避免消息乱序导致的逻辑错误

**2. 异步事件处理（AsyncEventService）**
```java
long shardignId = event.getShardingId();
long shardingResult = shardingExpresson.getValue(shardignId);
orderedQueuePoolExecutor.addTask(shardingResult, new SingleEventWork(eventBus, event));
```
- **需求**：保证相同分片ID的事件按顺序处理
- **原因**：保证事件处理的因果关系

### NonOrderedQueuePoolExecutor 使用场景

**1. 更新任务执行（UpdateExecutorService）**
```java
nonOrderedQueuePoolExecutor.execute(lockSupportUpdateFutureThread);
```
- **需求**：更新任务可以并发执行，不需要保证顺序
- **原因**：提高并发性能，最大化吞吐量

**2. Disruptor 工作线程池（DisruptorExecutorService）**
```java
executorService = new NonOrderedQueuePoolExecutor(poolName, excutorSize);
workerPool.start(executorService);
```
- **需求**：Disruptor 的 Worker 线程池，不需要顺序保证
- **原因**：Disruptor 本身已经保证了顺序，线程池只需要提供线程资源

### 选择建议

| 场景 | 选择 | 原因 |
|------|------|------|
| 玩家消息处理 | OrderedQueuePoolExecutor | 需要保证同一玩家的消息顺序 |
| 事件处理（需要顺序） | OrderedQueuePoolExecutor | 需要保证事件的因果关系 |
| 更新任务 | NonOrderedQueuePoolExecutor | 不需要顺序，追求性能 |
| 通用并发任务 | NonOrderedQueuePoolExecutor | 标准线程池，负载均衡好 |

---

## 总结

1. **OrderedQueuePoolExecutor** 通过多队列 + 链式执行机制，保证相同 `orderId` 的任务顺序执行
2. **NonOrderedQueuePoolExecutor** 使用标准线程池机制，追求最大并发性能
3. **关键设计**：任务对象携带队列引用 + `afterExecute` 钩子实现链式执行
4. **适用场景**：需要顺序保证的业务（如玩家消息）使用 Ordered，不需要顺序的场景使用 NonOrdered

这种设计既利用了 ThreadPoolExecutor 的线程管理能力，又实现了顺序执行的需求，是一个巧妙的设计。

