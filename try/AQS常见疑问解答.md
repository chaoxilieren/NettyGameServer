# AQS 常见疑问解答

本文档针对学习 AQS 过程中的常见疑问进行详细解答。

---

## 疑问 1：共享模式下多个线程获取锁，会不会导致数据混乱？

### 1.1 核心理解：共享模式 ≠ 写锁

**关键点**：共享模式**不是用来保护写操作的**，而是用于以下场景：

1. **资源计数**（如 Semaphore）
2. **读操作**（如 ReadWriteLock 的读锁）
3. **等待条件满足**（如 CountDownLatch）

### 1.2 共享模式的应用场景

#### 场景 1：信号量 (Semaphore) - 控制并发数量

```java
// 假设有一个连接池，最多允许 10 个并发连接
Semaphore semaphore = new Semaphore(10);

// 多个线程可以同时获取许可（共享模式）
public void useConnection() throws InterruptedException {
    semaphore.acquire();  // 获取一个许可（共享模式）
    try {
        // 使用连接（多个线程可以同时执行，但最多 10 个）
        doSomething();
    } finally {
        semaphore.release();  // 释放许可
    }
}
```

**为什么不会乱套？**
- 信号量控制的是**资源数量**，不是数据访问
- 每个线程获取许可后，使用自己的资源（如连接）
- 多个线程可以同时使用不同的资源，互不干扰

#### 场景 2：读写锁 (ReadWriteLock) - 读操作共享

```java
ReadWriteLock lock = new ReentrantReadWriteLock();
Lock readLock = lock.readLock();   // 共享锁
Lock writeLock = lock.writeLock();  // 独占锁

// 多个线程可以同时读取（共享模式）
public String readData() {
    readLock.lock();  // 共享模式：多个线程可以同时获取
    try {
        return data;  // 只读操作，不会修改数据
    } finally {
        readLock.unlock();
    }
}

// 只有一个线程可以写入（独占模式）
public void writeData(String newData) {
    writeLock.lock();  // 独占模式：同一时刻只有一个线程
    try {
        data = newData;  // 写操作，需要互斥
    } finally {
        writeLock.unlock();
    }
}
```

**为什么不会乱套？**
- **读锁（共享模式）**：多个线程可以同时读取，因为读操作不会修改数据
- **写锁（独占模式）**：只有一个线程可以写入，保证数据一致性
- 读写互斥：有写操作时，所有读操作都会被阻塞

#### 场景 3：CountDownLatch - 等待条件满足

```java
CountDownLatch latch = new CountDownLatch(3);

// 多个线程等待同一个条件（共享模式）
public void waitForReady() throws InterruptedException {
    latch.await();  // 共享模式：多个线程可以同时等待
    // 当计数器减到 0 时，所有等待的线程都会被唤醒
    doSomething();
}

// 其他线程减少计数
public void signal() {
    latch.countDown();  // 减少计数
}
```

**为什么不会乱套？**
- CountDownLatch 只是用来**同步等待**，不涉及数据修改
- 多个线程同时等待同一个条件，条件满足后一起继续执行

### 1.3 如果共享模式用于写操作会怎样？

**错误示例**（会导致数据混乱）：

```java
// ❌ 错误：使用共享锁保护写操作
SingleLock lock = new SingleLock();  // 共享模式，初始资源数为 1

private int counter = 0;

public void increment() {
    lock.lock();  // 共享模式：多个线程可能同时获取
    try {
        counter++;  // ⚠️ 多个线程同时修改，会导致数据不一致
    } finally {
        lock.unlock();
    }
}
```

**问题分析**：
- `SingleLock` 使用共享模式，虽然初始资源数为 1，但理论上多个线程可能同时获取
- 多个线程同时执行 `counter++`，会导致：
  - 丢失更新（Lost Update）
  - 数据不一致

**正确做法**（使用独占锁）：

```java
// ✅ 正确：使用独占锁保护写操作
ReentrantLock lock = new ReentrantLock();  // 独占模式

private int counter = 0;

public void increment() {
    lock.lock();  // 独占模式：同一时刻只有一个线程
    try {
        counter++;  // ✅ 只有一个线程能修改，保证数据一致性
    } finally {
        lock.unlock();
    }
}
```

### 1.4 总结

| 模式 | 适用场景 | 是否保护写操作 | 示例 |
|------|---------|---------------|------|
| **共享模式** | 资源计数、读操作、等待条件 | ❌ 不保护写操作 | Semaphore、ReadLock、CountDownLatch |
| **独占模式** | 写操作、互斥访问 | ✅ 保护写操作 | ReentrantLock、WriteLock |

**核心原则**：
- **共享模式**：用于**不修改共享数据**的场景（读、计数、等待）
- **独占模式**：用于**修改共享数据**的场景（写、互斥）

---

## 疑问 2：可重入锁的设计意义

### 2.1 什么是可重入锁？

**可重入锁（Reentrant Lock）**：同一个线程可以**多次获取同一把锁**，而不会导致死锁。

### 2.2 为什么需要可重入锁？

#### 场景 1：递归调用

```java
// ❌ 如果锁不可重入，会导致死锁
public class Calculator {
    private final Lock lock = new NonReentrantLock();  // 假设不可重入
    
    public int factorial(int n) {
        lock.lock();
        try {
            if (n <= 1) {
                return 1;
            }
            // 递归调用：同一个线程再次尝试获取锁
            return n * factorial(n - 1);  // ⚠️ 死锁！锁已经被当前线程持有
        } finally {
            lock.unlock();
        }
    }
}
```

**问题**：
- 线程 A 调用 `factorial(5)`
- `factorial(5)` 获取锁，然后调用 `factorial(4)`
- `factorial(4)` 尝试获取锁，但锁已被当前线程持有
- 如果锁不可重入，`factorial(4)` 会一直等待，导致**死锁**

**可重入锁的解决方案**：

```java
// ✅ 使用可重入锁
public class Calculator {
    private final ReentrantLock lock = new ReentrantLock();  // 可重入
    
    public int factorial(int n) {
        lock.lock();
        try {
            if (n <= 1) {
                return 1;
            }
            // 递归调用：同一个线程可以再次获取锁
            return n * factorial(n - 1);  // ✅ 正常执行
        } finally {
            lock.unlock();
        }
    }
}
```

#### 场景 2：方法调用链

```java
public class BankAccount {
    private final ReentrantLock lock = new ReentrantLock();
    private int balance = 1000;
    
    // 方法 A 获取锁
    public void transfer(BankAccount target, int amount) {
        lock.lock();
        try {
            withdraw(amount);        // 调用方法 B（需要锁）
            target.deposit(amount);
        } finally {
            lock.unlock();
        }
    }
    
    // 方法 B 也需要获取锁
    public void withdraw(int amount) {
        lock.lock();  // ✅ 可重入：同一个线程可以再次获取
        try {
            if (balance >= amount) {
                balance -= amount;
            }
        } finally {
            lock.unlock();
        }
    }
    
    public void deposit(int amount) {
        lock.lock();
        try {
            balance += amount;
        } finally {
            lock.unlock();
        }
    }
}
```

**如果锁不可重入**：
- `transfer()` 获取锁
- 调用 `withdraw()`，尝试再次获取锁
- 如果锁不可重入，`withdraw()` 会一直等待，导致死锁

**可重入锁的优势**：
- `transfer()` 获取锁（state = 1）
- `withdraw()` 再次获取锁（state = 2，重入计数）
- `withdraw()` 释放锁（state = 1）
- `transfer()` 释放锁（state = 0）

### 2.3 可重入锁的实现原理

```java
// ReentrantLock 的可重入实现
protected final boolean tryAcquire(int acquires) {
    final Thread current = Thread.currentThread();
    int c = getState();
    
    if (c == 0) {
        // 无锁状态，尝试获取
        if (!hasQueuedPredecessors() && compareAndSetState(0, acquires)) {
            setExclusiveOwnerThread(current);
            return true;
        }
    } else if (current == getExclusiveOwnerThread()) {
        // ✅ 关键：当前线程已持有锁，允许重入
        int nextc = c + acquires;  // 增加重入计数
        if (nextc < 0)
            throw new Error("Maximum lock count exceeded");
        setState(nextc);  // 更新重入计数
        return true;
    }
    return false;
}

protected final boolean tryRelease(int releases) {
    int c = getState() - releases;
    if (Thread.currentThread() != getExclusiveOwnerThread())
        throw new IllegalMonitorStateException();
    
    boolean free = false;
    if (c == 0) {
        // ✅ 重入计数减到 0，真正释放锁
        free = true;
        setExclusiveOwnerThread(null);
    }
    setState(c);  // 更新重入计数
    return free;
}
```

**关键点**：
- **state 表示重入次数**：0 = 无锁，1 = 第一次获取，2 = 第二次获取（重入），...
- **检查持有线程**：`current == getExclusiveOwnerThread()` 判断是否是同一线程
- **释放时递减**：每次 `unlock()` 将 state 减 1，减到 0 时真正释放锁

### 2.4 可重入锁的设计意义总结

| 方面 | 说明 |
|------|------|
| **避免死锁** | 同一线程多次获取锁不会阻塞 |
| **支持递归** | 递归函数可以安全地使用锁 |
| **简化设计** | 方法调用链中不需要考虑锁的获取顺序 |
| **提高灵活性** | 可以在已持有锁的情况下调用其他需要锁的方法 |

**核心价值**：让锁的使用更加**自然和灵活**，避免因锁的嵌套使用导致死锁。

---

## 疑问 3：公平锁与非公平锁的实现区别

### 3.1 什么是公平锁和非公平锁？

- **公平锁（Fair Lock）**：按照线程请求锁的**顺序**分配锁，先来先得（FIFO）
- **非公平锁（Non-fair Lock）**：允许"插队"，新来的线程可能比等待队列中的线程先获取锁

### 3.2 性能对比

| 特性 | 公平锁 | 非公平锁 |
|------|--------|---------|
| **吞吐量** | 较低 | 较高 |
| **延迟** | 较高（需要排队） | 较低（可能直接获取） |
| **适用场景** | 需要保证公平性 | 追求高性能 |

### 3.3 实现区别详解

#### 3.3.1 非公平锁实现

```java
// ReentrantLock 非公平锁的 tryAcquire
static final class NonfairSync extends Sync {
    protected final boolean tryAcquire(int acquires) {
        final Thread current = Thread.currentThread();
        int c = getState();
        
        if (c == 0) {
            // ⚠️ 关键：直接尝试获取锁，不检查队列
            if (compareAndSetState(0, acquires)) {
                setExclusiveOwnerThread(current);
                return true;
            }
        } else if (current == getExclusiveOwnerThread()) {
            // 重入逻辑
            int nextc = c + acquires;
            if (nextc < 0)
                throw new Error("Maximum lock count exceeded");
            setState(nextc);
            return true;
        }
        return false;
    }
}
```

**特点**：
- **不检查队列**：新线程可以直接尝试获取锁
- **可能"插队"**：即使队列中有等待线程，新线程也可能先获取锁

#### 3.3.2 公平锁实现

```java
// ReentrantLock 公平锁的 tryAcquire
static final class FairSync extends Sync {
    protected final boolean tryAcquire(int acquires) {
        final Thread current = Thread.currentThread();
        int c = getState();
        
        if (c == 0) {
            // ✅ 关键：检查是否有等待的线程（hasQueuedPredecessors）
            if (!hasQueuedPredecessors() && compareAndSetState(0, acquires)) {
                setExclusiveOwnerThread(current);
                return true;
            }
        } else if (current == getExclusiveOwnerThread()) {
            // 重入逻辑（与非公平锁相同）
            int nextc = c + acquires;
            if (nextc < 0)
                throw new Error("Maximum lock count exceeded");
            setState(nextc);
            return true;
        }
        return false;
    }
}
```

**关键方法：hasQueuedPredecessors()**

```java
// AQS 中的实现
public final boolean hasQueuedPredecessors() {
    Node t = tail;  // 队列尾节点
    Node h = head;  // 队列头节点
    Node s;
    
    // 判断队列中是否有其他线程在等待
    return h != t &&  // 队列不为空
           ((s = h.next) == null || s.thread != Thread.currentThread());
           // 头节点的下一个节点存在，且不是当前线程
}
```

**逻辑说明**：
- `h != t`：队列不为空（有等待的线程）
- `s.thread != Thread.currentThread()`：等待的线程不是当前线程
- 如果返回 `true`，说明有其他线程在等待，当前线程应该排队

### 3.4 执行流程对比

#### 非公平锁流程

```
时刻 T1: 线程 A 持有锁
时刻 T2: 线程 B 尝试获取锁 → 进入队列等待
时刻 T3: 线程 C 尝试获取锁 → 进入队列等待
时刻 T4: 线程 A 释放锁
时刻 T5: 线程 D 新来，尝试获取锁
         ├─ 直接 CAS 尝试获取锁 ✅ 成功（插队！）
         └─ 线程 B 和 C 继续等待
```

**结果**：线程 D 插队成功，先于 B 和 C 获取锁。

#### 公平锁流程

```
时刻 T1: 线程 A 持有锁
时刻 T2: 线程 B 尝试获取锁 → 进入队列等待
时刻 T3: 线程 C 尝试获取锁 → 进入队列等待
时刻 T4: 线程 A 释放锁
时刻 T5: 线程 D 新来，尝试获取锁
         ├─ 检查 hasQueuedPredecessors() → true（B 和 C 在等待）
         ├─ 不尝试获取锁，直接进入队列
         └─ 唤醒线程 B（队列中的第一个）
时刻 T6: 线程 B 获取锁 ✅
```

**结果**：按照队列顺序，B 先获取锁，D 排在 C 后面。

### 3.5 代码示例对比

```java
public class FairVsNonFairLockDemo {
    
    // 非公平锁（默认）
    private final ReentrantLock nonFairLock = new ReentrantLock(false);
    
    // 公平锁
    private final ReentrantLock fairLock = new ReentrantLock(true);
    
    public void testNonFairLock() {
        System.out.println("=== 非公平锁测试 ===");
        for (int i = 0; i < 5; i++) {
            final int threadId = i;
            new Thread(() -> {
                nonFairLock.lock();
                try {
                    System.out.println("线程 " + threadId + " 获取锁");
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                } finally {
                    nonFairLock.unlock();
                }
            }).start();
        }
        // 可能的输出（顺序不确定）：
        // 线程 0 获取锁
        // 线程 4 获取锁  ← 可能插队
        // 线程 1 获取锁
        // ...
    }
    
    public void testFairLock() {
        System.out.println("=== 公平锁测试 ===");
        for (int i = 0; i < 5; i++) {
            final int threadId = i;
            new Thread(() -> {
                fairLock.lock();
                try {
                    System.out.println("线程 " + threadId + " 获取锁");
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                } finally {
                    fairLock.unlock();
                }
            }).start();
        }
        // 输出（严格按照请求顺序）：
        // 线程 0 获取锁
        // 线程 1 获取锁
        // 线程 2 获取锁
        // ...
    }
}
```

### 3.6 性能影响分析

#### 非公平锁的优势

1. **减少上下文切换**：新线程可能直接获取锁，避免进入队列和唤醒的开销
2. **提高吞吐量**：减少线程阻塞和唤醒的次数
3. **降低延迟**：新线程不需要等待队列中的线程

#### 公平锁的优势

1. **保证公平性**：避免线程饥饿（某些线程一直获取不到锁）
2. **可预测性**：锁的获取顺序是可预测的
3. **适合低竞争场景**：在竞争不激烈时，性能差异不大

### 3.7 选择建议

| 场景 | 推荐 | 原因 |
|------|------|------|
| **高并发、高竞争** | 非公平锁 | 追求吞吐量 |
| **需要保证公平性** | 公平锁 | 避免线程饥饿 |
| **低竞争场景** | 两者均可 | 性能差异不大 |
| **需要可预测性** | 公平锁 | 顺序可预测 |

### 3.8 实现区别总结

| 方面 | 非公平锁 | 公平锁 |
|------|---------|--------|
| **tryAcquire 逻辑** | 直接尝试 CAS 获取锁 | 先检查 `hasQueuedPredecessors()` |
| **是否插队** | ✅ 允许插队 | ❌ 不允许插队 |
| **性能** | 更高吞吐量 | 较低吞吐量 |
| **公平性** | 不保证公平 | 保证公平（FIFO） |

**核心区别**：公平锁在 `tryAcquire` 中增加了 `hasQueuedPredecessors()` 检查，确保队列中的线程优先获取锁。

---

## 总结

1. **共享模式不会导致数据混乱**：因为共享模式用于读操作、资源计数、等待条件等场景，不用于保护写操作。写操作应该使用独占模式。

2. **可重入锁的设计意义**：避免同一线程多次获取锁时发生死锁，支持递归调用和方法调用链，让锁的使用更加自然和灵活。

3. **公平锁与非公平锁的区别**：公平锁在 `tryAcquire` 中检查 `hasQueuedPredecessors()`，确保按照队列顺序分配锁；非公平锁允许新线程插队，性能更高但可能不公平。

