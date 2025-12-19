# Netty LoggingHandler 正确添加方式

## 问题

在 Netty 中，如果想要 `LoggingHandler` 生效且不被覆盖，应该怎么做？

## 错误方式 ❌

### 方式1：在 Bootstrap 中多次调用 `.handler()`

```java
Bootstrap b = new Bootstrap();
b.group(group).channel(NioSocketChannel.class)
    .handler(new LoggingHandler(LogLevel.INFO))  // ❌ 会被覆盖
    .handler(new CodeCStringClientChannelInitializer());  // 这个会覆盖上面的
```

**问题：**
- 第二个 `.handler()` 会覆盖第一个
- `LoggingHandler` 不会被添加到 pipeline 中

### 方式2：在 ServerBootstrap 中使用 `.handler()` 而不是 `.childHandler()`

```java
ServerBootstrap serverBootstrap = new ServerBootstrap();
serverBootstrap.group(bossGroup, workerGroup)
    .handler(new LoggingHandler(LogLevel.INFO))  // ❌ 这是用于 ServerChannel 的，不是客户端连接
    .childHandler(new CodeServerChannelInitializer());
```

**问题：**
- `.handler()` 是用于 ServerChannel（服务端监听 socket）的
- `.childHandler()` 才是用于客户端连接的 Channel
- 如果要在客户端连接中添加 LoggingHandler，应该使用 `.childHandler()`

## 正确方式 ✅

### 方式1：在 ChannelInitializer 的 pipeline 中添加（推荐）

**客户端：**

```java
public class CodeCStringClientChannelInitializer extends ChannelInitializer<NioSocketChannel> {
    @Override
    protected void initChannel(NioSocketChannel ch) throws Exception {
        ChannelPipeline pipeline = ch.pipeline();
        pipeline.addLast(new LineBasedFrameDecoder(1024));
        pipeline.addLast(new StringDecoder());
        pipeline.addLast(new StringEncoder());
        pipeline.addLast("logger", new LoggingHandler(LogLevel.INFO));  // ✅ 正确方式
        pipeline.addLast(new EchoStringSocketClientHandler());
    }
}
```

**服务端：**

```java
public class CodeServerChannelInitializer extends ChannelInitializer<NioSocketChannel> {
    @Override
    protected void initChannel(NioSocketChannel ch) throws Exception {
        ChannelPipeline pipeline = ch.pipeline();
        pipeline.addLast(new LineBasedFrameDecoder(1024));
        pipeline.addLast(new StringDecoder());
        pipeline.addLast(new StringEncoder());
        pipeline.addLast("logger", new LoggingHandler(LogLevel.INFO));  // ✅ 正确方式
        pipeline.addLast(new TwoStringDecoder());
        pipeline.addLast(new CodeSocketServerHandler());
        pipeline.addLast(new CodeSocketTwoServerHandler());
    }
}
```

**使用方式：**

```java
// 客户端
Bootstrap b = new Bootstrap();
b.group(group).channel(NioSocketChannel.class)
    .handler(new CodeCStringClientChannelInitializer());  // 只设置一个 ChannelInitializer

// 服务端
ServerBootstrap serverBootstrap = new ServerBootstrap();
serverBootstrap.group(bossGroup, workerGroup)
    .childHandler(new CodeServerChannelInitializer());  // 使用 childHandler
```

### 方式2：在 ServerBootstrap 的 `.handler()` 中添加（仅用于服务端监听日志）

如果需要记录服务端监听 Channel 的日志（不是客户端连接的日志），可以使用：

```java
ServerBootstrap serverBootstrap = new ServerBootstrap();
serverBootstrap.group(bossGroup, workerGroup)
    .handler(new LoggingHandler(LogLevel.INFO))  // ✅ 用于 ServerChannel 的日志
    .childHandler(new CodeServerChannelInitializer());  // 客户端连接的 ChannelInitializer
```

**说明：**
- `.handler()` 添加的 LoggingHandler 只记录服务端监听 socket 的事件（如绑定、接受连接等）
- 不会记录客户端连接中传输的数据
- 如果需要记录客户端连接的数据，必须在 `childHandler` 的 ChannelInitializer 中添加

## LoggingHandler 的位置

### 推荐位置1：解码器之后，业务 Handler 之前

```java
pipeline.addLast(new LineBasedFrameDecoder(1024));
pipeline.addLast(new StringDecoder());
pipeline.addLast(new StringEncoder());
pipeline.addLast("logger", new LoggingHandler(LogLevel.INFO));  // ✅ 记录解码后的消息
pipeline.addLast(new BusinessHandler());
```

**优点：**
- 记录的是解码后的业务消息（如 String、自定义对象等）
- 日志更易读，便于调试业务逻辑

### 推荐位置2：Pipeline 最前面（记录原始数据）

```java
pipeline.addLast("logger", new LoggingHandler(LogLevel.DEBUG));  // ✅ 记录所有原始数据
pipeline.addLast(new LineBasedFrameDecoder(1024));
pipeline.addLast(new StringDecoder());
pipeline.addLast(new StringEncoder());
pipeline.addLast(new BusinessHandler());
```

**优点：**
- 记录所有原始 ByteBuf 数据
- 可以完整追踪网络传输的字节流
- 适合调试编解码问题

**缺点：**
- 日志量较大
- 二进制数据不易阅读

## LogLevel 选择

- `LogLevel.TRACE`: 最详细的日志，包括所有事件
- `LogLevel.DEBUG`: 调试信息，包括数据读写
- `LogLevel.INFO`: 一般信息，包括连接建立、关闭等
- `LogLevel.WARN`: 警告信息
- `LogLevel.ERROR`: 错误信息

**推荐：**
- 开发环境：`LogLevel.DEBUG` 或 `LogLevel.INFO`
- 生产环境：`LogLevel.WARN` 或 `LogLevel.ERROR`（或者不添加 LoggingHandler）

## 完整示例

### 客户端完整代码

```java
public class CodeCClient {
    public void connect(String addr, int port) throws Exception {
        final EventLoopGroup group = new NioEventLoopGroup();
        try {
            Bootstrap b = new Bootstrap();
            b.group(group).channel(NioSocketChannel.class)
                    .option(ChannelOption.TCP_NODELAY, true)
                    .handler(new CodeCStringClientChannelInitializer());  // ✅ 只设置一个
            ChannelFuture f = b.connect(addr, port).sync();
            f.channel().closeFuture().sync();
        } finally {
            group.shutdownGracefully();
        }
    }
}

// ChannelInitializer 中
public class CodeCStringClientChannelInitializer extends ChannelInitializer<NioSocketChannel> {
    @Override
    protected void initChannel(NioSocketChannel ch) throws Exception {
        ch.pipeline()
            .addLast(new LineBasedFrameDecoder(1024))
            .addLast(new StringDecoder())
            .addLast(new StringEncoder())
            .addLast("logger", new LoggingHandler(LogLevel.INFO))  // ✅ 在这里添加
            .addLast(new EchoStringSocketClientHandler());
    }
}
```

### 服务端完整代码

```java
public class CodeServer {
    public static void main(String[] args) {
        EventLoopGroup bossGroup = new NioEventLoopGroup(1);
        EventLoopGroup workerGroup = new NioEventLoopGroup();
        try {
            ServerBootstrap serverBootstrap = new ServerBootstrap();
            serverBootstrap.group(bossGroup, workerGroup)
                    .channel(NioServerSocketChannel.class)
                    .option(ChannelOption.SO_BACKLOG, 1024)
                    .childOption(ChannelOption.TCP_NODELAY, true)
                    .handler(new LoggingHandler(LogLevel.INFO))  // ✅ 可选：服务端监听日志
                    .childHandler(new CodeServerChannelInitializer());  // ✅ 客户端连接
            ChannelFuture future = serverBootstrap.bind(9999).sync();
            future.channel().closeFuture().sync();
        } finally {
            bossGroup.shutdownGracefully();
            workerGroup.shutdownGracefully();
        }
    }
}

// ChannelInitializer 中
public class CodeServerChannelInitializer extends ChannelInitializer<NioSocketChannel> {
    @Override
    protected void initChannel(NioSocketChannel ch) throws Exception {
        ch.pipeline()
            .addLast(new LineBasedFrameDecoder(1024))
            .addLast(new StringDecoder())
            .addLast(new StringEncoder())
            .addLast("logger", new LoggingHandler(LogLevel.INFO))  // ✅ 在这里添加
            .addLast(new TwoStringDecoder())
            .addLast(new CodeSocketServerHandler())
            .addLast(new CodeSocketTwoServerHandler());
    }
}
```

## Pipeline 执行顺序

### 入站（接收数据）

```
网络数据 
  → LoggingHandler (记录原始数据，如果在最前面)
  → LineBasedFrameDecoder (按行分割)
  → StringDecoder (ByteBuf → String)
  → LoggingHandler (记录解码后的消息，如果在解码器之后)
  → BusinessHandler (业务处理)
```

### 出站（发送数据）

```
BusinessHandler.writeAndFlush("消息")
  → LoggingHandler (记录要发送的消息)
  → StringEncoder (String → ByteBuf)
  → 网络发送
```

## 总结

1. ✅ **正确方式**：在 `ChannelInitializer.initChannel()` 方法中，通过 `pipeline.addLast()` 添加 `LoggingHandler`
2. ❌ **错误方式**：在 `Bootstrap.handler()` 中多次调用，后面的会覆盖前面的
3. 📍 **位置选择**：
   - 解码器之后：记录业务消息（推荐）
   - Pipeline 最前面：记录原始数据（调试用）
4. 🎯 **命名**：使用 `pipeline.addLast("logger", ...)` 给 handler 命名，方便后续查找和移除

## 验证

添加 LoggingHandler 后，运行程序应该能看到类似以下日志：

### 标准 LoggingHandler 输出

由于 LoggingHandler 内部使用 Netty 的 `InternalLogger`，日志格式取决于 log4j 配置：

**如果 log4j 配置为 `%C{1}.%M`（只显示最后一个包名）：**
```
14:57:20 [INFO ] - AbstractInternalLogger.log - [id: 0xaf78d7e4, L:/127.0.0.1:9999 - R:/127.0.0.1:56775] WRITE: 服务器端返回消息：转发二次编码请求8
14:57:20 [INFO ] - AbstractInternalLogger.log - [id: 0xaf78d7e4, L:/127.0.0.1:9999 - R:/127.0.0.1:56775] FLUSH
14:57:20 [INFO ] - AbstractInternalLogger.log - [id: 0xaf78d7e4, L:/127.0.0.1:9999 - R:/127.0.0.1:56775] READ: 请求9
```

**如果 log4j 配置为 `%C.%M`（显示完整类名）：**
```
14:34:00 [INFO ] - io.netty.handler.logging.LoggingHandler.log - [id: 0x18ac8907, L:/127.0.0.1:9999 - R:/127.0.0.1:53470] READ: 请求1
14:34:00 [INFO ] - io.netty.handler.logging.LoggingHandler.log - [id: 0x18ac8907, L:/127.0.0.1:9999 - R:/127.0.0.1:53470] WRITE: 服务器端返回消息：转发二次编码请求1
```

**重要说明：**
- ✅ 即使显示的是 `AbstractInternalLogger.log`，**LoggingHandler 仍然正常工作**
- ✅ 日志内容（如 `WRITE: 服务器端返回消息...`）证明 LoggingHandler 在记录数据
- 📝 类名显示取决于 log4j 的 `ConversionPattern` 配置

### 如何让日志显示 LoggingHandler 字样

**方式1：修改 log4j 配置（推荐）**

修改 `log4j.properties`：
```properties
# 修改前
log4j.appender.stdout.layout.ConversionPattern=%d{HH:mm:ss} [%-5p] - %C{1}.%M - %m%n

# 修改后（显示完整类名）
log4j.appender.stdout.layout.ConversionPattern=%d{HH:mm:ss} [%-5p] - %C.%M - %m%n
```

**方式2：使用自定义 LoggingHandler**

创建自定义的 LoggingHandler，使用自定义 Logger：
```java
public class CustomLoggingHandler extends ChannelInboundHandlerAdapter {
    private static final Logger logger = LoggerFactory.getLogger("NettyLoggingHandler");
    // ... 实现日志记录逻辑
}
```

这样日志会显示为：
```
14:57:20 [INFO ] - NettyLoggingHandler.channelRead - [id: 0x...] READ: 请求9
```

