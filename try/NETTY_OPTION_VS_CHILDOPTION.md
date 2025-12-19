# Netty option() 和 childOption() 的区别

## 核心概念

在 Netty 的 `ServerBootstrap` 中，存在两种 Channel：
1. **ServerChannel**（服务端监听 Channel）：用于监听和接受客户端连接
2. **Child Channel**（客户端连接 Channel）：每个客户端连接创建的 Channel

```
ServerBootstrap
  ├── ServerChannel (监听端口，接受连接)
  │   └── option() 配置这个 Channel
  │
  └── Child Channels (每个客户端连接)
      └── childOption() 配置这些 Channel
```

## 区别详解

### option() - 配置 ServerChannel

**作用对象：** 服务端监听 Channel（`ServerSocketChannel`）

**用途：** 配置服务端监听 socket 的属性

**典型使用场景：**
- 配置监听端口的 backlog（等待连接队列大小）
- 配置服务端 socket 的其他属性

### childOption() - 配置 Child Channel

**作用对象：** 每个客户端连接创建的 Channel（`SocketChannel`）

**用途：** 配置客户端连接 socket 的属性

**典型使用场景：**
- 配置 TCP 连接参数（如 TCP_NODELAY、SO_KEEPALIVE）
- 配置缓冲区大小（SO_RCVBUF、SO_SNDBUF）
- 配置 ByteBuf 分配器（ALLOCATOR）
- 配置连接超时等

## 参数分类

### 适用于 option() 的参数

| 参数 | 说明 | 典型值 |
|------|------|--------|
| `SO_BACKLOG` | 等待连接队列的最大长度 | 1024, 2048 |
| `SO_REUSEADDR` | 是否重用地址（服务端监听） | true/false |
| `SO_RCVBUF` | 服务端接收缓冲区大小 | 65536 |
| `SO_SNDBUF` | 服务端发送缓冲区大小 | 65536 |

**关键点：**
- `SO_BACKLOG` **只能**用于 `option()`，因为它只对监听 socket 有意义
- 其他参数如果同时用于 `option()` 和 `childOption()`，`option()` 会影响 ServerChannel，`childOption()` 会影响每个客户端连接

### 适用于 childOption() 的参数

| 参数 | 说明 | 典型值 |
|------|------|--------|
| `TCP_NODELAY` | 禁用 Nagle 算法，减少延迟 | true |
| `SO_KEEPALIVE` | 启用 TCP keepalive | true |
| `SO_REUSEADDR` | 重用地址（客户端连接） | true/false |
| `SO_RCVBUF` | 接收缓冲区大小 | 65536 |
| `SO_SNDBUF` | 发送缓冲区大小 | 65536 |
| `SO_LINGER` | 关闭时等待数据发送的时间 | 0（立即关闭） |
| `ALLOCATOR` | ByteBuf 分配器 | PooledByteBufAllocator |
| `CONNECT_TIMEOUT_MILLIS` | 连接超时时间（毫秒） | 1000 |
| `AUTO_READ` | 是否自动读取数据 | true/false |
| `WRITE_BUFFER_WATER_MARK` | 写缓冲区水位标记 | 低水位、高水位 |

**关键点：**
- `TCP_NODELAY` **通常**用于 `childOption()`，因为它是针对每个连接的
- 大部分 TCP socket 选项都应该在 `childOption()` 中配置

### 可以同时使用的参数

某些参数可以同时用于 `option()` 和 `childOption()`，但作用对象不同：

| 参数 | option() 作用 | childOption() 作用 |
|------|--------------|-------------------|
| `SO_REUSEADDR` | 服务端监听 socket | 客户端连接 socket |
| `SO_RCVBUF` | 服务端接收缓冲区 | 客户端接收缓冲区 |
| `SO_SNDBUF` | 服务端发送缓冲区 | 客户端发送缓冲区 |
| `ALLOCATOR` | ServerChannel 的分配器 | 每个连接的分配器 |

## 实际代码示例

### 标准服务端配置

```java
ServerBootstrap serverBootstrap = new ServerBootstrap();
serverBootstrap.group(bossGroup, workerGroup)
    .channel(NioServerSocketChannel.class)
    // option() - 配置 ServerChannel（监听 socket）
    .option(ChannelOption.SO_BACKLOG, 1024)  // ✅ 只能用于 option()
    .option(ChannelOption.SO_REUSEADDR, true)  // 服务端监听地址重用
    
    // childOption() - 配置每个客户端连接 Channel
    .childOption(ChannelOption.TCP_NODELAY, true)  // ✅ 通常用于 childOption()
    .childOption(ChannelOption.SO_KEEPALIVE, true)
    .childOption(ChannelOption.SO_REUSEADDR, true)  // 客户端连接地址重用
    .childOption(ChannelOption.SO_RCVBUF, 65536)
    .childOption(ChannelOption.SO_SNDBUF, 65536)
    .childOption(ChannelOption.ALLOCATOR, new PooledByteBufAllocator(false))
    .childOption(ChannelOption.CONNECT_TIMEOUT_MILLIS, 1000)
    
    .handler(new LoggingHandler(LogLevel.INFO))  // ServerChannel 的 handler
    .childHandler(new ChannelInitializer());  // 客户端连接的 handler
```

### 客户端配置（Bootstrap）

**注意：** 客户端 `Bootstrap` 只有 `option()`，没有 `childOption()`

```java
Bootstrap bootstrap = new Bootstrap();
bootstrap.group(group)
    .channel(NioSocketChannel.class)
    .option(ChannelOption.TCP_NODELAY, true)  // ✅ 客户端直接使用 option()
    .option(ChannelOption.SO_KEEPALIVE, true)
    .option(ChannelOption.SO_RCVBUF, 65536)
    .option(ChannelOption.SO_SNDBUF, 65536)
    .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 1000)
    .handler(new ChannelInitializer());
```

## 常见错误

### ❌ 错误1：在 option() 中使用 TCP_NODELAY

```java
// ❌ 错误：TCP_NODELAY 对 ServerChannel 没有意义
.option(ChannelOption.TCP_NODELAY, true)

// ✅ 正确：应该在 childOption() 中配置
.childOption(ChannelOption.TCP_NODELAY, true)
```

**原因：** `TCP_NODELAY` 是针对每个 TCP 连接的选项，而 ServerChannel 是监听 socket，不是连接 socket。

### ❌ 错误2：在 childOption() 中使用 SO_BACKLOG

```java
// ❌ 错误：SO_BACKLOG 对客户端连接没有意义
.childOption(ChannelOption.SO_BACKLOG, 1024)

// ✅ 正确：SO_BACKLOG 只能用于 option()
.option(ChannelOption.SO_BACKLOG, 1024)
```

**原因：** `SO_BACKLOG` 是监听 socket 的等待连接队列大小，只对 ServerChannel 有意义。

### ❌ 错误3：混淆 handler 和 childHandler

```java
// ❌ 错误：在 handler() 中配置客户端连接的处理器
.handler(new ClientChannelInitializer())

// ✅ 正确：使用 childHandler() 配置客户端连接
.childHandler(new ClientChannelInitializer())
```

## 完整对比表

| 特性 | option() | childOption() |
|------|---------|---------------|
| **作用对象** | ServerChannel（监听 socket） | Child Channel（客户端连接 socket） |
| **数量** | 1 个（服务端只有一个监听 Channel） | N 个（每个客户端连接一个） |
| **典型参数** | SO_BACKLOG | TCP_NODELAY, SO_KEEPALIVE |
| **配置时机** | 在 bind() 之前 | 在 accept() 创建连接时 |
| **对应 handler** | handler() | childHandler() |

## 代码库中的实际使用

从代码库中可以看到的标准模式：

```java
// AbstractNettyTcpServerService.java
serverBootstrap.channel(NioServerSocketChannel.class)
    .option(ChannelOption.SO_BACKLOG, 1024)  // ✅ ServerChannel
    .childOption(ChannelOption.SO_REUSEADDR, true)  // ✅ Child Channel
    .childOption(ChannelOption.SO_RCVBUF, 65536)  // ✅ Child Channel
    .childOption(ChannelOption.SO_SNDBUF, 65536)  // ✅ Child Channel
    .childOption(ChannelOption.TCP_NODELAY, true)  // ✅ Child Channel
    .childOption(ChannelOption.SO_KEEPALIVE, true)  // ✅ Child Channel
    .childOption(ChannelOption.ALLOCATOR, new PooledByteBufAllocator(false))  // ✅ Child Channel
    .handler(new LoggingHandler(LogLevel.INFO))  // ServerChannel 的 handler
    .childHandler(channelInitializer);  // Child Channel 的 handler
```

## 记忆技巧

1. **option() = ServerChannel = 监听 = 1 个**
   - 服务端只有一个监听 socket
   - 主要用于 `SO_BACKLOG`

2. **childOption() = Child Channel = 连接 = N 个**
   - 每个客户端连接都有一个 socket
   - 主要用于 TCP 连接参数（`TCP_NODELAY`、`SO_KEEPALIVE` 等）

3. **类比：**
   - `option()` 就像配置"门卫"（ServerChannel）
   - `childOption()` 就像配置每个"访客"（Child Channel）

## 总结

| 配置项 | 使用位置 | 原因 |
|--------|---------|------|
| `SO_BACKLOG` | `option()` | 只对监听 socket 有意义 |
| `TCP_NODELAY` | `childOption()` | 针对每个 TCP 连接 |
| `SO_KEEPALIVE` | `childOption()` | 针对每个 TCP 连接 |
| `SO_RCVBUF/SO_SNDBUF` | `childOption()` | 通常配置客户端连接 |
| `ALLOCATOR` | `childOption()` | 每个连接需要独立的分配器 |
| `CONNECT_TIMEOUT_MILLIS` | `childOption()` | 连接超时针对每个连接 |

**核心原则：**
- **监听相关的配置** → `option()`
- **连接相关的配置** → `childOption()`

