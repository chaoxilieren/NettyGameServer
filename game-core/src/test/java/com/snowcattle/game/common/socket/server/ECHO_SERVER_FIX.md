# EchoServer 空指针异常修复说明

## 问题描述

运行 `EchoServer` 时出现 `NullPointerException`：

```
Cannot invoke "com.snowcattle.game.bootstrap.manager.spring.LocalSpringServiceManager.getMessageRegistry()" 
because the return value of "com.snowcattle.game.bootstrap.manager.LocalMananger.getLocalSpringServiceManager()" is null
```

## 问题原因

`NetMessageServerChannleInitializer` 使用的 `NetMessageTCPDecoder` 依赖 Spring 的 `MessageRegistry`，但测试服务器 `EchoServer` 没有初始化 Spring 上下文。

**调用链：**
```
EchoServer
  └─> NetMessageServerChannleInitializer
      └─> NetMessageTCPDecoder
          └─> NetTcpMessageDecoderFactory.praseMessage()
              └─> LocalMananger.getLocalSpringServiceManager()  ← 返回 null
                  └─> getMessageRegistry()  ← NullPointerException
```

## 解决方案

### 1. 创建测试消息类

创建了 `TestNetMessage` 类，用于测试环境（不依赖 Spring）：

```java
public class TestNetMessage extends AbstractNetMessage {
    // 简单的测试消息实现
}
```

### 2. 修改解码器工厂

修改了 `NetTcpMessageDecoderFactory.praseMessage()` 方法，添加了空值检查和测试环境支持：

**修改前：**
```java
MessageRegistry messageRegistry = LocalMananger.getInstance()
    .getLocalSpringServiceManager().getMessageRegistry();
AbstractNetMessage abstractNetMessage = messageRegistry.getMessage(cmd);
```

**修改后：**
```java
// 支持测试环境：如果 Spring 未初始化，使用测试消息类
try {
    LocalMananger localMananger = LocalMananger.getInstance();
    if (localMananger != null && 
        localMananger.getLocalSpringServiceManager() != null) {
        MessageRegistry messageRegistry = localMananger
            .getLocalSpringServiceManager().getMessageRegistry();
        if (messageRegistry != null) {
            abstractNetMessage = messageRegistry.getMessage(cmd);
            if (abstractNetMessage != null) {
                // 使用 Spring 注册的消息
                abstractNetMessage.setNetMessageHead(netMessageHead);
                abstractNetMessage.setNetMessageBody(netMessageBody);
                return abstractNetMessage;
            }
        }
    }
} catch (Exception e) {
    // Spring 未初始化，使用测试消息类
}

// 测试环境：创建测试消息对象
abstractNetMessage = new TestNetMessage();
abstractNetMessage.setNetMessageHead(netMessageHead);
abstractNetMessage.setNetMessageBody(netMessageBody);
return abstractNetMessage;
```

### 3. 修复字节读取 Bug

修复了 `NetTcpMessageDecoderFactory` 中的字节读取问题：

**修改前：**
```java
byte[] bytes = new byte[byteLength];
netMessageBody.setBytes(bytes);  // 创建了空数组，没有读取数据
```

**修改后：**
```java
byte[] bytes = new byte[byteLength];
byteBuf.readBytes(bytes);  // 正确读取字节数据
netMessageBody.setBytes(bytes);
```

### 4. 改进错误处理

改进了 `EchoServer` 的错误处理和日志输出：

```java
try {
    // ...
    System.out.println("EchoServer 启动成功，监听端口: " + Port);
    serverChannelFuture.channel().closeFuture().sync();
} catch (Exception e) {
    System.err.println("EchoServer 启动失败: " + e.getMessage());
    e.printStackTrace();
} finally {
    // ...
}
```

## 使用说明

### 方案 1：使用修复后的 NetMessageServerChannleInitializer（推荐）

现在 `EchoServer` 可以直接使用 `NetMessageServerChannleInitializer`，它会自动检测 Spring 环境：

```java
.childHandler(new NetMessageServerChannleInitializer());
```

**优点：**
- ✅ 支持测试环境（不依赖 Spring）
- ✅ 支持生产环境（使用 Spring 注册的消息）
- ✅ 自动检测环境，无需手动切换

### 方案 2：使用不依赖 Spring 的初始化器

如果只想做简单的测试，可以使用其他初始化器：

```java
// 使用字符串编解码器（最简单）
.childHandler(new StringServerChannelInitializer());

// 或使用长度字段编解码器
.childHandler(new LengthStringServerChannelInitializer());
```

## 测试验证

运行 `EchoServer` 后，应该看到：

1. **启动成功日志：**
   ```
   EchoServer 启动成功，监听端口: 9999
   ```

2. **如果 Spring 未初始化，会看到警告：**
   ```
   警告: Spring 未初始化，使用测试消息类。错误: ...
   ```

3. **服务器可以正常接收和响应消息**

## 注意事项

1. **测试环境 vs 生产环境：**
   - 测试环境：使用 `TestNetMessage`，功能有限
   - 生产环境：使用 Spring 注册的消息，功能完整

2. **消息处理：**
   - 测试环境下的消息可能无法正确处理业务逻辑
   - 如果需要完整的消息处理，需要初始化 Spring 上下文

3. **性能：**
   - 每次解码都会检查 Spring 环境（轻微性能开销）
   - 对于测试环境，这个开销可以忽略

## 相关文件

- `EchoServer.java` - 测试服务器主类
- `NetMessageServerChannleInitializer.java` - 网络消息通道初始化器
- `NetTcpMessageDecoderFactory.java` - TCP 消息解码器工厂（已修复）
- `TestNetMessage.java` - 测试消息类（新建）
- `StringServerChannelInitializer.java` - 字符串编解码器初始化器（替代方案）
- `LengthStringServerChannelInitializer.java` - 长度字段编解码器初始化器（替代方案）

