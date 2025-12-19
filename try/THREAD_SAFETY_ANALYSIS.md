# com.snowcattle.game.db.sharding.CustomerContextHolder 线程安全性分析

## 一、类结构分析

```java
public final class CustomerContextHolder {
    private static final ThreadLocal<String> contextHolder = new ThreadLocal<String>();
    
    public static String getCustomerType() {
        return contextHolder.get();
    }
    
    public static void setCustomerType(String customerType) {
        contextHolder.set(customerType);
    }
}
```

## 二、ThreadLocal 特性

### 2.1 ThreadLocal 工作原理

`ThreadLocal` 是 Java 提供的线程本地变量，每个线程都有自己独立的副本：
- **线程A** 调用 `setCustomerType("db1")` → 只影响线程A的 ThreadLocal
- **线程B** 调用 `getCustomerType()` → 只能获取线程B自己的值（或 `null`）
- **线程之间互不干扰**

### 2.2 线程安全性

✅ **类本身是线程安全的**：
- `ThreadLocal` 是线程安全的
- 每个线程有独立的副本，不存在并发竞争

## 三、使用场景分析

### 3.1 正常使用场景（同步模式）

```java
// EntityService.insertEntity()
public long insertEntity(T entity) {
    long selectId = getShardingId(entity);
    // 1. 在当前线程设置
    CustomerContextHolder.setCustomerType(getEntityServiceShardingStrategy().getShardingDBKeyByUserId(selectId));
    
    // 2. 在同一线程中使用
    IDBMapper<T> idbMapper = getTemplateMapper(entity);
    result = idbMapper.insertEntity(entity);  // MyBatis 会调用 getCustomerType()
    
    return result;
}
```

**分析**：
- ✅ `setCustomerType()` 和 `getCustomerType()` 在**同一个线程**中执行
- ✅ 设置后立即使用，不会出现线程切换问题
- ✅ **这种情况下是安全的**

### 3.2 异步存库场景

```java
// AsyncDBSaveTransactionEntity.commit() - 在异步线程中执行
public void commit() throws GameTransactionException {
    // ... 从Redis队列取出数据 ...
    
    // 调用 EntityService.insertEntity()
    entityService.insertEntity(abstractEntity);
    // ↑ 这个方法内部会：
    //   1. setCustomerType() - 在异步线程中设置
    //   2. idbMapper.insertEntity() - 在同一个异步线程中使用
}
```

**分析**：
- ✅ 异步线程执行 `commit()`
- ✅ `EntityService.insertEntity()` 在**同一个异步线程**中执行
- ✅ `setCustomerType()` 和 `getCustomerType()` 都在**同一个线程**
- ✅ **这种情况下也是安全的**

## 四、潜在问题分析

### 4.1 问题1：ThreadLocal 内存泄漏

**问题描述**：
- `ThreadLocal` 如果没有及时清理，可能导致内存泄漏
- 特别是线程池复用线程的场景

**当前代码问题**：
```java
// ❌ 没有清理方法
public static void setCustomerType(String customerType) {
    contextHolder.set(customerType);
    // 缺少：contextHolder.remove() 或清理逻辑
}
```

**影响**：
- 如果线程被线程池复用，上一个任务设置的 `ThreadLocal` 值可能残留
- 如果下一个任务没有调用 `setCustomerType()`，可能会获取到错误的值

**实际影响评估**：
- ⚠️ **中等风险**：因为每个 `EntityService` 方法都会先调用 `setCustomerType()`，会覆盖旧值
- ⚠️ **但如果方法执行异常，可能没有覆盖，导致残留**

### 4.2 问题2：线程切换场景

**问题场景**：
```java
// 线程A
CustomerContextHolder.setCustomerType("db1");

// 任务提交到线程池，由线程B执行
executorService.submit(() -> {
    // 线程B
    String type = CustomerContextHolder.getCustomerType();  // ❌ 获取不到线程A设置的值
});
```

**当前代码是否存在此问题**：
- ❌ **不存在**：因为 `setCustomerType()` 和 `getCustomerType()` 都在同一个方法调用栈中
- ✅ 每次数据库操作前都会重新设置，不会跨线程使用

### 4.3 问题3：批量操作中的线程切换

**查看批量操作代码**：
```java
// EntityService.insertEntityBatch()
for (T entity : entityList) {
    long selectId = getShardingId(entity);
    CustomerContextHolder.setCustomerType(...);  // 每次循环都重新设置
    // ... 执行操作 ...
}
```

**分析**：
- ✅ 每次循环都重新设置，即使线程切换也不会有问题
- ✅ **安全**

## 五、结论

### 5.1 线程安全性

✅ **类本身是线程安全的**：
- `ThreadLocal` 保证了线程隔离
- 每个线程有独立的副本

✅ **使用场景是安全的**：
- `setCustomerType()` 和 `getCustomerType()` 在同一个线程中执行
- 每次数据库操作前都会重新设置
- 不会出现跨线程访问的问题

### 5.2 潜在风险

⚠️ **ThreadLocal 内存泄漏风险**：
- 当前代码没有清理 `ThreadLocal` 的逻辑
- 虽然实际影响较小（因为每次都会重新设置），但建议添加清理逻辑

## 六、改进建议

### 6.1 添加清理方法

```java
public final class CustomerContextHolder {
    private static final ThreadLocal<String> contextHolder = new ThreadLocal<String>();
    
    public static String getCustomerType() {
        return contextHolder.get();
    }
    
    public static void setCustomerType(String customerType) {
        contextHolder.set(customerType);
    }
    
    // ✅ 添加清理方法
    public static void clearCustomerType() {
        contextHolder.remove();
    }
}
```

### 6.2 在 EntityService 中使用 try-finally 清理

```java
public long insertEntity(T entity) {
    long selectId = getShardingId(entity);
    String customerType = getEntityServiceShardingStrategy().getShardingDBKeyByUserId(selectId);
    
    try {
        CustomerContextHolder.setCustomerType(customerType);
        // ... 执行数据库操作 ...
        return result;
    } finally {
        // ✅ 确保清理
        CustomerContextHolder.clearCustomerType();
    }
}
```

### 6.3 使用 Spring 的 RequestContextHolder 模式（可选）

如果希望更严格的控制，可以考虑使用 Spring 的 `RequestContextHolder` 类似的模式，在方法入口和出口自动管理 `ThreadLocal`。

## 七、总结

| 问题 | 是否存在 | 风险等级 | 说明 |
|------|---------|---------|------|
| 线程安全性 | ❌ 不存在 | - | `ThreadLocal` 保证线程隔离 |
| 跨线程访问 | ❌ 不存在 | - | 设置和使用在同一线程 |
| ThreadLocal 泄漏 | ⚠️ 存在 | 中等 | 缺少清理逻辑，但影响较小 |
| 数据源选择错误 | ❌ 不存在 | - | 每次操作前都重新设置 |

**总体评价**：
- ✅ **当前实现是线程安全的**
- ⚠️ **建议添加 ThreadLocal 清理逻辑，避免潜在的内存泄漏**

