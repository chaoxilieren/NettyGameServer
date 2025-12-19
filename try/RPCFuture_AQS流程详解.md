# com.snowcattle.game.service.rpc.client.RPCFuture 中 AQS 使用流程详解

## 一、核心设计思想

`RPCFuture` 使用 AQS 实现了一个**一次性信号机制**（类似 CountDownLatch），而不是传统的锁。

**关键点**：
- **不是互斥锁**：这里不是用来保护共享资源的互斥访问
- **是信号机制**：用来通知等待线程"结果已准备好"
- **一次性使用**：state 从 `pending(0)` 变为 `done(1)` 后，所有等待的线程都会被唤醒

---

## 二、完整流程分析

### 2.1 初始状态

```java
public RPCFuture(RpcRequest request) {
    this.sync = new Sync();  // 创建 Sync 实例
    this.request = request;
    this.startTime = System.currentTimeMillis();
}
```

**AQS 状态**：
- `state = 0` (pending) - 表示 RPC 请求还未完成
- 队列：空

### 2.2 线程 A 调用 get() 方法

```java
public Object get() throws InterruptedException, ExecutionException {
    sync.acquire(-1);  // 尝试获取"锁"（实际上是等待信号）
    if (this.response != null) {
        return this.response.getResult();
    } else {
        return null;
    }
}
```

#### 步骤 1：acquire(-1) 内部调用 tryAcquire(-1)

```java
protected boolean tryAcquire(int acquires) {
    return getState() == done ? true : false;  // state == 1 才能获取成功
}
```

**此时状态**：
- `state = 0` (pending)
- `tryAcquire(-1)` 返回 `false`（因为 state != done）

**注意**：参数 `-1` 在这里**没有被使用**，`tryAcquire` 只检查 state 的值。

#### 步骤 2：获取失败，进入 AQS 的 acquire 流程

```java
// AQS 的 acquire 方法（简化版）
public final void acquire(int arg) {
    if (!tryAcquire(arg) &&                    // tryAcquire 返回 false
        acquireQueued(addWaiter(Node.EXCLUSIVE), arg))  // 加入队列并阻塞
        selfInterrupt();
}
```

**详细流程**：

1. **addWaiter(Node.EXCLUSIVE)**：将线程 A 包装成 Node，加入 CLH 队列
   ```
   队列状态：
   [head] -> [Node(Thread-A)] -> [tail]
   ```

2. **acquireQueued(node, arg)**：在队列中自旋等待
   ```java
   for (;;) {
       final Node p = node.predecessor();
       if (p == head && tryAcquire(arg)) {  // 再次尝试获取
           setHead(node);
           return interrupted;
       }
       // 检查是否需要阻塞
       if (shouldParkAfterFailedAcquire(p, node) &&
           parkAndCheckInterrupt())  // ⚠️ 线程 A 在这里被阻塞（park）
           interrupted = true;
   }
   ```

**此时状态**：
- `state = 0` (pending)
- 线程 A：**阻塞（park）**，等待被唤醒
- 队列：`[head] -> [Node(Thread-A)] -> [tail]`

### 2.3 线程 B 调用 get() 方法（如果存在）

如果此时有另一个线程 B 也调用 `get()`：

**流程相同**：
1. `tryAcquire(-1)` 返回 `false`
2. 线程 B 加入队列并阻塞

**此时状态**：
- `state = 0` (pending)
- 线程 A：阻塞
- 线程 B：阻塞
- 队列：`[head] -> [Node(Thread-A)] -> [Node(Thread-B)] -> [tail]`

### 2.4 RPC 响应到达，调用 done() 方法

```java
public void done(RpcResponse reponse) {
    this.response = reponse;  // 1. 保存响应结果
    sync.release(1);          // 2. 释放"锁"（实际上是发送信号）
    invokeCallbacks();
    // ...
}
```

#### 步骤 1：release(1) 内部调用 tryRelease(1)

```java
protected boolean tryRelease(int releases) {
    if (getState() == pending) {  // state == 0
        if (compareAndSetState(pending, done)) {  // CAS: 0 -> 1
            return true;
        }
    }
    return false;
}
```

**CAS 操作**：
- `compareAndSetState(0, 1)`：将 state 从 `pending(0)` 改为 `done(1)`
- 成功返回 `true`

**此时状态**：
- `state = 1` (done) ✅ **关键变化**
- 线程 A：仍然阻塞（还未被唤醒）
- 线程 B：仍然阻塞
- 队列：`[head] -> [Node(Thread-A)] -> [Node(Thread-B)] -> [tail]`

#### 步骤 2：release(1) 唤醒等待的线程

```java
// AQS 的 release 方法（简化版）
public final boolean release(int arg) {
    if (tryRelease(arg)) {           // tryRelease 返回 true
        Node h = head;
        if (h != null && h.waitStatus != 0)
            unparkSuccessor(h);      // ⚠️ 唤醒队列中的第一个线程（线程 A）
        return true;
    }
    return false;
}
```

**unparkSuccessor(h)**：
- 找到头节点的下一个节点（线程 A 的 Node）
- 调用 `LockSupport.unpark(threadA)` 唤醒线程 A

**此时状态**：
- `state = 1` (done)
- 线程 A：**被唤醒**，准备继续执行
- 线程 B：仍然阻塞
- 队列：`[head] -> [Node(Thread-A)] -> [Node(Thread-B)] -> [tail]`

### 2.5 线程 A 被唤醒，继续执行

线程 A 在 `acquireQueued` 的循环中继续执行：

```java
for (;;) {
    final Node p = node.predecessor();
    if (p == head && tryAcquire(arg)) {  // ⚠️ 再次调用 tryAcquire
        setHead(node);                   // 设置为头节点
        p.next = null;                   // 帮助 GC
        return interrupted;
    }
    // ...
}
```

#### 步骤 1：再次调用 tryAcquire(-1)

```java
protected boolean tryAcquire(int acquires) {
    return getState() == done ? true : false;  // state == 1，返回 true ✅
}
```

**此时**：
- `state = 1` (done)
- `tryAcquire(-1)` 返回 `true`

#### 步骤 2：获取成功，退出循环

```java
if (p == head && tryAcquire(arg)) {  // true
    setHead(node);                    // 将线程 A 的节点设为头节点
    p.next = null;                    // 断开前驱节点的引用
    return interrupted;               // 返回，acquire 方法结束
}
```

**此时状态**：
- `state = 1` (done)
- 线程 A：**继续执行**，`acquire(-1)` 返回
- 线程 B：仍然阻塞
- 队列：`[head(Thread-A)] -> [Node(Thread-B)] -> [tail]`

#### 步骤 3：get() 方法继续执行

```java
public Object get() throws InterruptedException, ExecutionException {
    sync.acquire(-1);  // ✅ 返回，继续执行
    if (this.response != null) {  // response 已被 done() 设置
        return this.response.getResult();  // 返回结果
    } else {
        return null;
    }
}
```

**线程 A 完成**：返回 RPC 响应结果

### 2.6 线程 B 被唤醒（传播唤醒）

当线程 A 的节点被设置为头节点后，AQS 会**传播唤醒**后续的共享节点（虽然这里是独占模式，但唤醒机制类似）。

**线程 B 的流程**：
1. 被唤醒
2. 再次调用 `tryAcquire(-1)`，此时 `state == 1`，返回 `true`
3. 获取成功，继续执行 `get()` 方法
4. 返回结果

**最终状态**：
- `state = 1` (done)
- 线程 A：已完成
- 线程 B：已完成
- 队列：`[head(Thread-B)] -> [tail]`

---

## 三、关键问题解答

### 3.1 锁什么时候释放？

**答案：这里的"锁"实际上不需要手动释放！**

**原因**：
1. **这不是传统意义上的锁**：`RPCFuture` 使用 AQS 实现的是**一次性信号机制**，不是互斥锁
2. **state 的变化是单向的**：从 `pending(0)` → `done(1)`，不会回到 `pending`
3. **所有等待线程都会被唤醒**：一旦 `state` 变为 `done(1)`，所有调用 `get()` 的线程都能通过 `tryAcquire` 检查

**类比**：
- 传统锁：获取 → 使用 → 释放（可以重复获取）
- RPCFuture：等待信号 → 信号到达 → 所有等待者都被唤醒（一次性）

### 3.2 AQS 中 state 的变化过程

```
时间线：

T0: 创建 RPCFuture
    state = 0 (pending)
    └─ Sync 构造函数：默认 state = 0

T1: 线程 A 调用 get()
    state = 0 (pending)
    └─ tryAcquire(-1): state == 0，返回 false
    └─ 线程 A 进入队列并阻塞

T2: 线程 B 调用 get()（如果存在）
    state = 0 (pending)
    └─ tryAcquire(-1): state == 0，返回 false
    └─ 线程 B 进入队列并阻塞

T3: RPC 响应到达，调用 done()
    state = 0 (pending) → state = 1 (done) ✅
    └─ tryRelease(1): compareAndSetState(0, 1) 成功
    └─ 唤醒线程 A

T4: 线程 A 被唤醒
    state = 1 (done)
    └─ tryAcquire(-1): state == 1，返回 true ✅
    └─ 线程 A 继续执行，返回结果

T5: 线程 B 被唤醒（如果存在）
    state = 1 (done)
    └─ tryAcquire(-1): state == 1，返回 true ✅
    └─ 线程 B 继续执行，返回结果

T6: 后续所有调用 get() 的线程
    state = 1 (done)
    └─ tryAcquire(-1): state == 1，返回 true ✅
    └─ 直接返回，不阻塞
```

**关键点**：
- `state` 只变化一次：`0 → 1`
- 一旦变为 `1`，永远不会回到 `0`
- 所有后续的 `get()` 调用都不会阻塞

### 3.3 acquire(-1) 中的 -1 参数是什么意思？

**答案：在这个实现中，-1 参数实际上没有被使用！**

**原因**：
```java
protected boolean tryAcquire(int acquires) {
    return getState() == done ? true : false;  // 只检查 state，不关心 acquires 参数
}
```

**为什么传 -1？**
- 这是 AQS 的约定：`acquire(int arg)` 需要一个参数
- 在 `RPCFuture` 的实现中，这个参数没有实际意义
- 可以传任何值（-1, 0, 1 都可以），因为 `tryAcquire` 不使用它

**对比其他实现**：
- `ReentrantLock`：`acquire(1)` 表示获取 1 个锁
- `Semaphore`：`acquire(1)` 表示获取 1 个许可
- `RPCFuture`：`acquire(-1)` 参数无意义，只是占位

---

## 四、完整时序图

```
线程 A                线程 B                RPC 响应线程
  |                     |                        |
  | get()               |                        |
  |---acquire(-1)       |                        |
  |   |                 |                        |
  |   tryAcquire(-1)    |                        |
  |   state=0, false    |                        |
  |   |                 |                        |
  |   加入队列          |                        |
  |   阻塞(park)        |                        |
  |   |                 |                        |
  |   |                 | get()                  |
  |   |                 |---acquire(-1)          |
  |   |                 |   tryAcquire(-1)       |
  |   |                 |   state=0, false       |
  |   |                 |   |                    |
  |   |                 |   加入队列              |
  |   |                 |   阻塞(park)           |
  |   |                 |   |                    |
  |   |                 |   |                    |
  |   |                 |   |                    done()
  |   |                 |   |                    |
  |   |                 |   |                    release(1)
  |   |                 |   |                    |
  |   |                 |   |                    tryRelease(1)
  |   |                 |   |                    state: 0→1 ✅
  |   |                 |   |                    |
  |   |                 |   |                    unpark(线程A)
  |   |                 |   |                    |
  |   被唤醒            |   |                    |
  |   |                 |   |                    |
  |   tryAcquire(-1)    |   |                    |
  |   state=1, true ✅  |   |                    |
  |   |                 |   |                    |
  |   继续执行          |   |                    |
  |   返回结果          |   |                    |
  |   |                 |   |                    |
  |   |                 |   被唤醒（传播）        |
  |   |                 |   |                    |
  |   |                 |   tryAcquire(-1)       |
  |   |                 |   state=1, true ✅    |
  |   |                 |   |                    |
  |   |                 |   继续执行              |
  |   |                 |   返回结果              |
```

---

## 五、代码执行流程总结

### 5.1 get() 方法的完整流程

```java
public Object get() throws InterruptedException, ExecutionException {
    // 步骤 1: 调用 acquire(-1)
    sync.acquire(-1);
    //   ↓
    // AQS.acquire(-1)
    //   ↓
    // tryAcquire(-1) → false (state == 0)
    //   ↓
    // addWaiter(Node.EXCLUSIVE) → 加入队列
    //   ↓
    // acquireQueued(node, -1) → 阻塞等待
    //   ↓
    // [等待 done() 被调用]
    //   ↓
    // [被唤醒]
    //   ↓
    // tryAcquire(-1) → true (state == 1) ✅
    //   ↓
    // acquire(-1) 返回
    
    // 步骤 2: 返回结果
    if (this.response != null) {
        return this.response.getResult();
    } else {
        return null;
    }
}
```

### 5.2 done() 方法的完整流程

```java
public void done(RpcResponse reponse) {
    // 步骤 1: 保存响应
    this.response = reponse;
    
    // 步骤 2: 释放"锁"（发送信号）
    sync.release(1);
    //   ↓
    // AQS.release(1)
    //   ↓
    // tryRelease(1)
    //   ↓
    // compareAndSetState(0, 1) → true ✅
    //   ↓
    // unparkSuccessor(head) → 唤醒等待线程
    
    // 步骤 3: 执行回调
    invokeCallbacks();
    
    // 步骤 4: 检查响应时间
    // ...
}
```

---

## 六、设计模式分析

### 6.1 为什么使用 AQS 而不是简单的 wait/notify？

**优势**：
1. **更灵活**：支持超时（`tryAcquireNanos`）
2. **更安全**：避免虚假唤醒
3. **更高效**：使用 park/unpark 代替 wait/notify
4. **更统一**：与 JDK 其他同步器保持一致

### 6.2 为什么使用独占模式而不是共享模式？

**原因**：
- 虽然多个线程可以同时获取（因为 state == 1 时都返回 true），但这里使用独占模式更符合语义
- 实际上，由于 `tryAcquire` 的实现，多个线程确实可以"同时获取"，但这不影响功能

---

## 七、总结

### 7.1 核心要点

1. **RPCFuture 使用 AQS 实现一次性信号机制**，不是传统锁
2. **state 变化**：`0 (pending) → 1 (done)`，单向变化
3. **锁不需要释放**：因为这是一次性信号，所有等待线程都会被唤醒
4. **acquire(-1) 中的 -1 参数无意义**，只是占位符

### 7.2 关键理解

- **不是互斥锁**：多个线程可以同时"获取成功"（当 state == 1 时）
- **是信号机制**：用来通知"结果已准备好"
- **一次性使用**：state 变为 1 后，所有后续调用都不会阻塞

### 7.3 类比

`RPCFuture` 的 AQS 使用类似于：
- **CountDownLatch**：等待计数器减到 0
- **Future.get()**：等待异步任务完成
- **信号量（但只有 0 和 1 两个状态）**：等待信号到达

**核心区别**：这里不是保护共享资源，而是**等待异步结果**。

