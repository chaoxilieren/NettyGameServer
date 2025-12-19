# MyBatis + Spring 配置加载与数据库操作完整流程分析

## 一、配置文件结构概览

### 1.1 配置文件层次

```
game-db/src/test/resources/
├── bean/
│   ├── db_applicationContext.xml              # Spring主配置文件（组件扫描、属性配置）
│   ├── db_applicationContext_datasource.xml  # 数据源和MyBatis配置
│   └── db-applicationContext-redis.xml       # Redis配置
├── mybatis3/
│   ├── sqlMapConfig.xml                       # MyBatis全局配置
│   └── mappers/
│       ├── orderMapper.xml                    # Order实体Mapper XML
│       ├── tockenMapper.xml                   # Tocken实体Mapper XML
│       └── moreOrderMapper.xml                # MoreOrder实体Mapper XML
└── db_jdbc.properties                         # 数据库连接属性
```

### 1.2 配置文件加载顺序

1. **db_applicationContext.xml** - 主配置，最先加载
2. **db_applicationContext_datasource.xml** - 数据源配置
3. **db-applicationContext-redis.xml** - Redis配置
4. **mybatis3/sqlMapConfig.xml** - MyBatis配置（由Spring加载）

---

## 二、Spring配置加载流程

### 2.1 第一阶段：主配置文件加载（db_applicationContext.xml）

```xml
<context:annotation-config />                    <!-- 启用注解驱动 -->
<aop:aspectj-autoproxy />                        <!-- 启用AOP代理 -->
<context:component-scan base-package="com.snowcattle.game.db" />  <!-- 组件扫描 -->
<context:property-placeholder location="classpath:db_jdbc.properties" />  <!-- 属性文件 -->
```

**执行流程**：

1. **组件扫描** (`component-scan`)
   - 扫描 `com.snowcattle.game.db` 包下的所有类
   - 识别 `@Service`、`@Component`、`@Repository` 等注解
   - 注册为Spring Bean
   - **关键Bean**：
     - `OrderService` (extends `EntityService<Order>`)
     - `CustomerContextHolder`
     - `DynamicDataSource`
     - 其他Service类

### 2.1.1 Spring注解区别说明

#### @Service、@Component、@Repository 的区别

这三个注解在**功能上完全相同**，都是 `@Component` 的派生注解，用于将类注册为Spring Bean。它们的区别主要体现在**语义和用途**上：

| 注解 | 语义 | 典型用途 | Spring处理 |
|------|------|---------|-----------|
| `@Component` | 通用组件 | 工具类、配置类、通用组件 | 普通Bean，无特殊处理 |
| `@Service` | 业务服务层 | 业务逻辑处理、服务类 | 普通Bean，无特殊处理 |
| `@Repository` | 数据访问层 | DAO、Mapper、数据访问 | **特殊处理**：自动转换数据访问异常 |

**关键区别**：

1. **@Repository 的特殊性**：
   ```java
   // @Repository 会自动将数据访问异常转换为Spring的DataAccessException
   @Repository
   public class UserDao {
       // 如果抛出SQLException，Spring会自动转换为DataAccessException
   }
   ```

2. **@Service 和 @Component**：
   - 功能完全相同，只是语义不同
   - `@Service` 更明确表示这是业务服务层
   - `@Component` 更通用，适合各种组件

#### 项目中的使用情况

**项目统一使用 `@Service` 注解**，原因如下：

1. **项目架构特点**：
   - 这是一个**数据库服务模块**（game-db）
   - 所有类都是**服务性质的组件**
   - 没有严格的分层架构（Controller-Service-DAO）

2. **项目中的实际使用**：
   ```java
   // 业务服务类
   @Service
   public class OrderService extends EntityService<Order> { }
   
   // 工具/工厂类
   @Service
   public class EntityServiceProxyFactory { }
   
   // 配置类
   @Service
   public class DbConfig { }
   
   // 基础设施类
   @Service
   public class RedisService { }
   
   // 上下文持有者
   @Service
   public class CustomerContextHolder { }
   ```

3. **使用 `@Service` 的理由**：

   ✅ **统一性**：
   - 所有组件都使用同一个注解，代码风格统一
   - 便于理解和维护

   ✅ **语义清晰**：
   - 虽然功能相同，但 `@Service` 更符合"服务模块"的定位
   - 明确表示这些类都是提供服务的组件

   ✅ **简化选择**：
   - 不需要纠结用哪个注解
   - 减少决策成本

   ✅ **项目特点**：
   - 这是一个**数据库服务模块**，不是完整的MVC应用
   - 没有严格的分层，所有类都是"服务"性质的
   - 不需要 `@Repository` 的异常转换特性（因为使用MyBatis，异常处理已封装）

4. **为什么不使用 `@Repository`**：

   ❌ **不需要异常转换**：
   - MyBatis已经处理了数据访问异常
   - Spring的异常转换在这里不是必需的

   ❌ **不是传统DAO层**：
   - 项目使用MyBatis的Mapper接口，不是传统的DAO类
   - Mapper接口由 `MapperScannerConfigurer` 扫描，不需要 `@Repository`

   ❌ **架构简化**：
   - 项目采用服务层直接调用Mapper的模式
   - 没有独立的DAO层，所以不需要 `@Repository`

5. **为什么不使用 `@Component`**：

   ❌ **语义不够明确**：
   - `@Component` 太通用，不能明确表达"服务"的含义
   - `@Service` 更符合项目的服务模块定位

**总结**：
- 项目统一使用 `@Service` 是**合理的选择**
- 符合项目的架构特点（服务模块）
- 代码风格统一，便于维护
- 虽然功能上三个注解相同，但 `@Service` 的语义更符合项目定位

2. **属性占位符解析** (`property-placeholder`)
   - 加载 `db_jdbc.properties`
   - 解析 `${jdbc-driver}`、`${jdbc-url-0}` 等占位符
   - 为后续数据源配置提供属性值

3. **AOP代理配置** (`aspectj-autoproxy`)
   - 为后续的代理类（如 `EntityServiceProxy`）提供支持

### 2.2 第二阶段：数据源配置加载（db_applicationContext_datasource.xml）

#### 2.2.1 数据源Bean创建

```xml
<!-- 父数据源模板 -->
<bean id="parent_datasource" class="org.apache.commons.dbcp.BasicDataSource">
    <property name="maxActive" value="8" />
    <property name="maxWait" value="1500" />
    <!-- ... 其他连接池配置 ... -->
</bean>

<!-- 具体数据源 -->
<bean id="jdbc_player_db0" parent="parent_datasource">
    <property name="driverClassName" value="${jdbc-driver}" />
    <property name="url" value="${jdbc-url-0}" />
    <property name="username" value="${jdbc-user-0}" />
    <property name="password" value="${jdbc-password-0}" />
</bean>
```

**Bean创建顺序**：
1. `parent_datasource` - 父模板Bean
2. `jdbc_player_db0`、`jdbc_player_db1`、`jdbc_player_db2` - 具体数据源
3. `dynamicDataSource` - 动态数据源（路由数据源）

#### 2.2.2 动态数据源配置

```xml
<bean id="dynamicDataSource" class="com.snowcattle.game.db.sharding.DynamicDataSource">
    <property name="targetDataSources">
        <map key-type="java.lang.String">
            <entry key="jdbc_player_db0" value-ref="jdbc_player_db0"/>
            <entry key="jdbc_player_db1" value-ref="jdbc_player_db1"/>
            <entry key="jdbc_player_db2" value-ref="jdbc_player_db2"/>
        </map>
    </property>
    <property name="defaultTargetDataSource" ref="jdbc_player_db0"/>
</bean>
```

**工作原理**：
- `DynamicDataSource` 继承 `AbstractRoutingDataSource`
- 通过 `determineCurrentLookupKey()` 方法返回数据源key
- 根据 `CustomerContextHolder.getCustomerType()` 的值选择对应的数据源

#### 2.2.3 事务管理器配置

```xml
<bean id="transactionManager" class="org.springframework.jdbc.datasource.DataSourceTransactionManager">
    <property name="dataSource" ref="dynamicDataSource" />
</bean>
```

---

## 三、MyBatis配置加载流程

### 3.1 SqlSessionFactoryBean创建

```xml
<bean id="sqlSessionFactory" class="org.mybatis.spring.SqlSessionFactoryBean">
    <property name="dataSource" ref="dynamicDataSource" />
    <property name="typeAliasesPackage" value="com.snowcattle.game.db.service.jdbc.entity"/>
    <property name="typeHandlersPackage" value="com.snowcattle.game.db.service.jdbc.handler"/>
    <property name="configLocation" value="classpath:mybatis3/sqlMapConfig.xml"/>
    <property name="plugins">
        <array>
            <bean class="com.github.pagehelper.PageInterceptor">
                <!-- 分页插件配置 -->
            </bean>
        </array>
    </property>
</bean>
```

**加载流程**：

1. **创建 SqlSessionFactoryBean**
   - Spring调用 `SqlSessionFactoryBean.afterPropertiesSet()`
   - 读取 `configLocation` 指定的 `sqlMapConfig.xml`

2. **解析 sqlMapConfig.xml**
   ```xml
   <configuration>
       <settings>
           <setting name="cacheEnabled" value="true"/>
           <setting name="lazyLoadingEnabled" value="true"/>
           <!-- ... 其他配置 ... -->
       </settings>
       <typeAliases>
           <typeAlias alias="order" type="com.snowcattle.game.db.service.jdbc.entity.Order"/>
       </typeAliases>
       <mappers>
           <mapper resource="mybatis3/mappers/orderMapper.xml"/>
           <mapper resource="mybatis3/mappers/tockenMapper.xml"/>
           <mapper resource="mybatis3/mappers/moreOrderMapper.xml"/>
       </mappers>
   </configuration>
   ```

3. **加载Mapper XML文件**
   - 解析每个 `<mapper resource="..."/>` 标签
   - 读取XML文件内容
   - 解析SQL语句、参数映射、结果映射
   - 注册到 `Configuration` 对象中

4. **构建 SqlSessionFactory**
   - 使用 `Configuration` 对象创建 `SqlSessionFactory`
   - 设置数据源、类型别名、类型处理器、插件等

### 3.2 SqlSessionTemplate创建

```xml
<!-- 普通SqlSessionTemplate -->
<bean id="sqlSessionTemplate" class="org.mybatis.spring.SqlSessionTemplate" scope="prototype">
    <constructor-arg index="0" ref="sqlSessionFactory" />
</bean>

<!-- 批量SqlSessionTemplate -->
<bean id="sqlSessionBatchTemplate" class="org.mybatis.spring.SqlSessionTemplate" scope="prototype">
    <constructor-arg index="0" ref="sqlSessionFactory" />
    <constructor-arg>
        <value type="org.apache.ibatis.session.ExecutorType">BATCH</value>
    </constructor-arg>
</bean>
```

**关键点**：
- `scope="prototype"` - 每次获取都创建新实例
- `sqlSessionBatchTemplate` 使用 `ExecutorType.BATCH` - 批量执行模式

### 3.3 Mapper接口扫描与注册

```xml
<bean class="org.mybatis.spring.mapper.MapperScannerConfigurer">
    <property name="basePackage" value="com.snowcattle.game.db.service.jdbc.mapper"/>
    <property name="sqlSessionFactoryBeanName" value="sqlSessionFactory"/>
</bean>
```

**扫描流程**：

1. **扫描Mapper接口**
   - 扫描 `com.snowcattle.game.db.service.jdbc.mapper` 包
   - 找到所有Mapper接口：
     - `OrderMapper extends IDBMapper<Order>`
     - `TockenMapper extends IDBMapper<Tocken>`
     - `MoreOrderMapper extends IDBMapper<MoreOrder>`

2. **创建Mapper代理对象**
   - 为每个Mapper接口创建JDK动态代理
   - 代理对象实现该接口
   - 代理方法调用 `SqlSession.getMapper()` 获取真正的Mapper

3. **注册到Spring容器**
   - 将Mapper代理对象注册为Spring Bean
   - Bean名称：接口类名（首字母小写），如 `orderMapper`

4. **关联Mapper XML**
   - 通过 `namespace` 属性关联：
     ```xml
     <mapper namespace="com.snowcattle.game.db.service.jdbc.mapper.OrderMapper">
     ```
   - MyBatis根据接口全限定名匹配XML中的namespace

---

## 四、EntityService#insertEntity 完整调用流程

### 4.1 调用入口

```java
// OrderService.insertOrder()
public long insertOrder(Order order) {
    return insertEntity(order);  // 调用父类方法
}
```

### 4.2 EntityService.insertEntity() 执行流程

```java
@Override
@DbOperation(operation = DbOperationEnum.insert)
public long insertEntity(T entity) {
    // 步骤1: 计算分库分表索引
    long selectId = getShardingId(entity);
    
    // 步骤2: 设置数据源路由key（ThreadLocal）
    CustomerContextHolder.setCustomerType(
        getEntityServiceShardingStrategy().getShardingDBKeyByUserId(selectId)
    );
    
    // 步骤3: 设置分表索引
    entity.setSharding_table_index(
        getEntityServiceShardingStrategy().getShardingDBTableIndexByUserId(selectId)
    );
    
    // 步骤4: 获取Mapper
    IDBMapper<T> idbMapper = getTemplateMapper(entity);
    
    // 步骤5: 执行插入
    long result = -1;
    try {
        result = idbMapper.insertEntity(entity);
    } catch (Exception e) {
        logger.error(e.toString(), e);
    }
    return result;
}
```

### 4.3 详细步骤分析

#### 步骤1: 计算分库分表索引

```java
public long getShardingId(T entity) {
    long shardingId = entity.getUserId();  // 默认使用userId
    if (entity.getEntityKeyShardingStrategyEnum() == EntityKeyShardingStrategyEnum.ID) {
        // 如果使用ID作为分片键
        if(entity instanceof BaseLongIDEntity) {
            shardingId = baseLongIDEntity.getId();
        }
    }
    return shardingId;
}
```

#### 步骤2: 设置数据源路由key

```java
// EntityServiceShardingStrategy.getShardingDBKeyByUserId()
public String getShardingDBKeyByUserId(long userId) {
    int dbIndex = getShardingDBTableIndexByUserId(userId);
    return getDataSource() + dbIndex;  // 返回 "jdbc_player_db0" 等
}

// CustomerContextHolder.setCustomerType()
public static void setCustomerType(String customerType) {
    contextHolder.set(customerType);  // 存储到ThreadLocal
}
```

**数据源路由机制**：
- `DynamicDataSource.determineCurrentLookupKey()` 被调用
- 返回 `CustomerContextHolder.getCustomerType()` 的值
- Spring根据这个key从 `targetDataSources` Map中选择对应的数据源

#### 步骤3: 获取Mapper

```java
public IDBMapper<T> getTemplateMapper(T entity) {
    // 从实体类注解获取Mapper接口
    DbMapper mapper = entity.getClass().getAnnotation(DbMapper.class);
    // Order类上有: @DbMapper(mapper = OrderMapper.class)
    
    // 从SqlSessionTemplate获取Mapper代理对象
    return (IDBMapper<T>) sqlSessionTemplate.getMapper(mapper.mapper());
}
```

**Mapper获取流程**：
1. 从实体类 `@DbMapper` 注解获取Mapper接口类
2. 调用 `sqlSessionTemplate.getMapper(OrderMapper.class)`
3. `SqlSessionTemplate` 内部：
   - 获取当前线程的 `SqlSession`
   - 调用 `sqlSession.getMapper(OrderMapper.class)`
   - MyBatis返回 `OrderMapper` 的代理对象

#### 步骤4: 执行SQL

```java
// idbMapper.insertEntity(entity) 调用链：
OrderMapper.insertEntity()
  ↓ (JDK动态代理)
MapperProxy.invoke()
  ↓
SqlSession.insert("com.snowcattle.game.db.service.jdbc.mapper.OrderMapper.insertEntity", entity)
  ↓
SqlSessionTemplate.insert()
  ↓
DynamicDataSource.getConnection()  // 获取数据库连接
  ↓
determineCurrentLookupKey()  // 返回 "jdbc_player_db0"
  ↓
选择对应的数据源 (jdbc_player_db0)
  ↓
获取数据库连接
  ↓
执行SQL: INSERT INTO `t_order_${sharding_table_index}` ...
```

**SQL执行细节**：

1. **SQL语句解析**
   ```xml
   <insert id="insertEntity" parameterType="order">
       INSERT INTO `t_order_${sharding_table_index}` (id, user_id, status)
       VALUES (#{id},#{userId},#{status})
   </insert>
   ```

2. **参数替换**
   - `${sharding_table_index}` → 实体对象的 `sharding_table_index` 属性值（如 `0`）
   - `#{id}` → `entity.getId()`
   - `#{userId}` → `entity.getUserId()`
   - `#{status}` → `entity.getStatus()`

3. **最终SQL**
   ```sql
   INSERT INTO `t_order_0` (id, user_id, status)
   VALUES (123, 456, 'active')
   ```

4. **执行并返回**
   - 通过JDBC执行SQL
   - 返回影响行数（MyBatis转换为long）

---

## 五、批量操作的不同之处

### 5.1 批量操作流程（insertEntityBatch）

```java
@Override
@DbOperation(operation = DbOperationEnum.insertBatch)
public List<Long> insertEntityBatch(List<T> entityList) {
    List<Long> result = new ArrayList<>();
    
    // 关键1: 获取批量SqlSession
    SqlSession sqlSession = getBatchSession();
    
    try {
        // 关键2: 循环处理每个实体
        for (T entity : entityList) {
            long selectId = getShardingId(entity);
            // 每次循环都重新设置数据源（因为可能分到不同库）
            CustomerContextHolder.setCustomerType(...);
            entity.setSharding_table_index(...);
            
            // 关键3: 使用批量SqlSession获取Mapper
            IDBMapper<T> mapper = getBatchTemplateMapper(sqlSession, entity);
            long insertResult = mapper.insertEntity(entity);
            result.add(insertResult);
        }
        
        // 关键4: 提交批量事务
        commitBatchSession();
    } catch (Exception e) {
        rollbackBatchSession();
    } finally {
        // 关键5: 关闭SqlSession
        closeBatchSession();
    }
    return result;
}
```

### 5.2 批量操作的关键差异

#### 差异1: SqlSession的获取方式

**普通操作**：
```java
// 使用注入的sqlSessionTemplate（每次调用都获取新的SqlSession）
IDBMapper<T> idbMapper = getTemplateMapper(entity);
// 内部: sqlSessionTemplate.getMapper() → 自动管理SqlSession生命周期
```

**批量操作**：
```java
// 手动获取并管理SqlSession
SqlSession sqlSession = getBatchSession();
// getBatchSession() 实现:
public SqlSession getBatchSession() {
    SqlSession session = threadLocal.get();
    if (session == null) {
        // 使用BATCH执行器类型创建SqlSession
        session = sqlSessionBatchTemplate.getSqlSessionFactory()
            .openSession(ExecutorType.BATCH, true);
        threadLocal.set(session);
    }
    return session;
}
```

#### 差异2: ExecutorType的不同

**普通操作**：
- 使用 `ExecutorType.SIMPLE`（默认）
- 每条SQL立即执行
- 自动提交（如果配置了自动提交）

**批量操作**：
- 使用 `ExecutorType.BATCH`
- SQL语句先缓存到 `BatchExecutor` 中
- 调用 `commit()` 时批量执行所有SQL
- 性能更高（减少网络往返）

#### 差异3: 事务管理

**普通操作**：
```java
// SqlSessionTemplate自动管理事务
// 每次操作后自动提交（如果配置了自动提交）
result = idbMapper.insertEntity(entity);
```

**批量操作**：
```java
// 手动管理事务
try {
    for (T entity : entityList) {
        mapper.insertEntity(entity);  // 只添加到批量缓存，不执行
    }
    commitBatchSession();  // 批量执行并提交
} catch (Exception e) {
    rollbackBatchSession();  // 回滚
} finally {
    closeBatchSession();  // 关闭SqlSession
}
```

#### 差异4: 数据源切换处理

**普通操作**：
- 每次操作只涉及一个数据源
- 设置一次 `CustomerContextHolder` 即可

**批量操作**：
- 可能涉及多个数据源（不同实体可能分到不同库）
- **每次循环都重新设置** `CustomerContextHolder`
- 但使用同一个 `SqlSession`（可能导致问题，见下文）

#### 差异5: Mapper获取方式

**普通操作**：
```java
// 从SqlSessionTemplate获取
public IDBMapper<T> getTemplateMapper(T entity) {
    DbMapper mapper = entity.getClass().getAnnotation(DbMapper.class);
    return (IDBMapper<T>) sqlSessionTemplate.getMapper(mapper.mapper());
}
```

**批量操作**：
```java
// 从手动管理的SqlSession获取
public IDBMapper<T> getBatchTemplateMapper(SqlSession sqlSession, T entity) {
    DbMapper mapper = entity.getClass().getAnnotation(DbMapper.class);
    return (IDBMapper<T>) sqlSession.getMapper(mapper.mapper());
}
```

### 5.3 批量操作的潜在问题

⚠️ **注意**：批量操作中，如果实体分到不同的数据源，可能会有问题：

```java
for (T entity : entityList) {
    // 每次循环都切换数据源
    CustomerContextHolder.setCustomerType("jdbc_player_db0");  // 实体1
    mapper.insertEntity(entity1);
    
    CustomerContextHolder.setCustomerType("jdbc_player_db1");  // 实体2
    mapper.insertEntity(entity2);  // ⚠️ 但SqlSession已经绑定了db0的连接
}
```

**原因**：
- `SqlSession` 在创建时已经获取了数据库连接
- 后续切换 `CustomerContextHolder` 不会影响已创建的连接

**解决方案**：
- 批量操作应该保证所有实体都在同一个数据源中
- 或者按数据源分组，每组使用独立的 `SqlSession`

---

## 六、完整流程图

### 6.1 Spring启动流程

```
Spring容器启动
  ↓
加载 db_applicationContext.xml
  ├─→ 组件扫描 (@Service, @Component)
  ├─→ 属性占位符解析 (${jdbc-driver})
  └─→ AOP代理配置
  ↓
加载 db_applicationContext_datasource.xml
  ├─→ 创建数据源Bean (jdbc_player_db0, db1, db2)
  ├─→ 创建DynamicDataSource
  ├─→ 创建SqlSessionFactoryBean
  │   ├─→ 读取 sqlMapConfig.xml
  │   ├─→ 加载Mapper XML文件
  │   └─→ 构建SqlSessionFactory
  ├─→ 创建SqlSessionTemplate (普通)
  ├─→ 创建SqlSessionTemplate (批量)
  └─→ MapperScannerConfigurer扫描Mapper接口
      └─→ 注册Mapper代理Bean到Spring容器
  ↓
加载 db-applicationContext-redis.xml
  └─→ 创建Redis相关Bean
  ↓
所有Bean创建完成，Spring容器就绪
```

### 6.2 EntityService#insertEntity 调用流程

```
业务代码: OrderService.insertOrder(order)
  ↓
EntityService.insertEntity(entity)
  ↓
步骤1: getShardingId(entity) → 计算分片ID
  ↓
步骤2: CustomerContextHolder.setCustomerType("jdbc_player_db0")
  ↓
步骤3: entity.setSharding_table_index(0)
  ↓
步骤4: getTemplateMapper(entity)
  ├─→ 从@DbMapper注解获取OrderMapper.class
  ├─→ sqlSessionTemplate.getMapper(OrderMapper.class)
  └─→ 返回OrderMapper代理对象
  ↓
步骤5: idbMapper.insertEntity(entity)
  ├─→ MapperProxy.invoke()
  ├─→ SqlSessionTemplate.insert()
  ├─→ DynamicDataSource.getConnection()
  │   ├─→ determineCurrentLookupKey()
  │   │   └─→ CustomerContextHolder.getCustomerType() → "jdbc_player_db0"
  │   └─→ 选择jdbc_player_db0数据源
  ├─→ 获取数据库连接
  ├─→ 解析SQL: INSERT INTO `t_order_0` ...
  ├─→ 参数绑定: #{id}, #{userId}, #{status}
  ├─→ 执行SQL
  └─→ 返回影响行数
```

### 6.3 批量操作流程对比

```
普通操作 (insertEntity):
  sqlSessionTemplate.getMapper()
    → 自动获取SqlSession
    → 执行SQL
    → 自动提交/关闭

批量操作 (insertEntityBatch):
  getBatchSession()
    → 手动创建BATCH类型SqlSession
    → ThreadLocal存储
  ↓
  for循环:
    getBatchTemplateMapper(sqlSession, entity)
      → 从手动SqlSession获取Mapper
    mapper.insertEntity(entity)
      → 添加到批量缓存（不执行）
  ↓
  commitBatchSession()
    → 批量执行所有SQL
    → 提交事务
  ↓
  closeBatchSession()
    → 关闭SqlSession
    → 清理ThreadLocal
```

---

## 七、关键配置说明

### 7.1 数据源配置

| 配置项 | 说明 | 值 |
|--------|------|-----|
| `maxActive` | 最大连接数 | 8 |
| `maxWait` | 最大等待时间(ms) | 1500 |
| `maxIdle` | 最大空闲连接 | 2 |
| `minIdle` | 最小空闲连接 | 1 |
| `initialSize` | 初始连接数 | 2 |

### 7.2 MyBatis配置

| 配置项 | 说明 | 值 |
|--------|------|-----|
| `cacheEnabled` | 二级缓存 | true |
| `lazyLoadingEnabled` | 延迟加载 | true |
| `defaultExecutorType` | 默认执行器 | SIMPLE |
| `mapUnderscoreToCamelCase` | 驼峰命名转换 | true |

### 7.3 分库分表配置

```java
EntityServiceShardingStrategy:
  - dbCount: 3        // 3个数据库
  - tableCount: 2     // 每个库2张表
  - dataSource: "jdbc_player_db"  // 数据源前缀
```

**分片算法**：
```java
// 分库索引
int dbIndex = (int)(userId % dbCount);  // 0, 1, 2

// 分表索引
int tableIndex = (int)(userId % tableCount);  // 0, 1

// 数据源key
String dataSourceKey = "jdbc_player_db" + dbIndex;  // jdbc_player_db0, db1, db2

// 表名
String tableName = "t_order_" + tableIndex;  // t_order_0, t_order_1
```

---

## 八、总结

### 8.1 配置加载顺序

1. **Spring主配置** → 组件扫描、属性解析
2. **数据源配置** → 创建数据源、动态路由
3. **MyBatis配置** → SqlSessionFactory、Mapper扫描
4. **Redis配置** → Redis服务

### 8.2 数据库操作流程

1. **计算分片** → 确定数据源和表
2. **设置路由** → ThreadLocal存储数据源key
3. **获取Mapper** → 从SqlSessionTemplate获取代理对象
4. **执行SQL** → 动态数据源路由 → 执行SQL → 返回结果

### 8.3 批量操作特点

1. **手动管理SqlSession** → 使用BATCH执行器
2. **批量缓存SQL** → 延迟执行
3. **手动事务控制** → try-catch-finally管理
4. **注意数据源切换** → 需保证同库或分组处理

---

## 九、参考资料

- **配置文件位置**: `game-db/src/test/resources/`
- **关键类**:
  - `EntityService` - 实体服务基类
  - `DynamicDataSource` - 动态数据源
  - `CustomerContextHolder` - 数据源路由上下文
  - `SqlSessionTemplate` - MyBatis-Spring集成
  - `MapperScannerConfigurer` - Mapper扫描器

