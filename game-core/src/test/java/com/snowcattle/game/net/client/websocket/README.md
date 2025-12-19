# WebSocket 通信示例

本目录包含使用 Netty 实现的 WebSocket 通信示例，包括服务器和客户端。

## 文件说明

### 服务器端
- **SimpleWebSocketServer.java** - WebSocket 服务器主类
- **SimpleWebSocketServerInitializer.java** - 服务器 Channel 初始化器
- **SimpleWebSocketServerHandler.java** - 服务器业务处理器

### 客户端
- **SimpleWebSocketClient.java** - WebSocket 客户端主类
- **SimpleWebSocketClientHandler.java** - 客户端处理器

### 文档
- **WebSocket与HTTP通信区别说明.md** - 详细的 WebSocket 与 HTTP 对比说明
- **TCP与WebSocket通信对比详解.md** - 详细的 TCP 与 WebSocket 对比说明
- **BinaryWebSocketFrame应用场景说明.md** - 二进制帧通信应用场景详解

## 快速开始

### 1. 启动服务器

运行 `SimpleWebSocketServer` 的 `main` 方法：

```bash
# 服务器将在 9001 端口启动
WebSocket 服务器已启动！
WebSocket 地址: ws://127.0.0.1:9001/websocket
```

### 2. 启动客户端

运行 `SimpleWebSocketClient` 的 `main` 方法：

```bash
正在连接到 WebSocket 服务器: ws://127.0.0.1:9001/websocket
WebSocket 连接已建立！
========================================
使用说明：
  - 输入消息并按回车发送
  - 输入 'ping' 发送心跳
  - 输入 'time' 请求服务器时间
  - 输入 'echo:xxx' 测试回显
  - 输入 'bye' 退出
========================================
```

### 3. 测试功能

在客户端控制台输入以下命令：

- **普通消息**：直接输入文本，服务器会回复
- **`ping`**：发送心跳包，服务器会回复 `pong`
- **`time`**：请求服务器当前时间
- **`echo:xxx`**：测试回显功能
- **`bye`**：关闭连接并退出

## 功能特性

### 服务器功能
- ✅ 处理文本消息（TextWebSocketFrame）
- ✅ 处理二进制消息（BinaryWebSocketFrame）
- ✅ 自动处理 Ping/Pong 心跳
- ✅ 连接建立时发送欢迎消息
- ✅ 记录连接和断开日志

### 客户端功能
- ✅ 自动 WebSocket 握手
- ✅ 发送文本消息
- ✅ 发送 Ping 心跳
- ✅ 接收服务器消息
- ✅ 优雅关闭连接

## 使用浏览器测试

你也可以使用浏览器的 WebSocket 客户端测试：

```javascript
// 在浏览器控制台运行
const ws = new WebSocket('ws://127.0.0.1:9001/websocket');

ws.onopen = () => {
    console.log('连接已建立');
    ws.send('Hello Server');
};

ws.onmessage = (event) => {
    console.log('收到消息:', event.data);
};

ws.onerror = (error) => {
    console.error('错误:', error);
};

ws.onclose = () => {
    console.log('连接已关闭');
};
```

## 代码结构说明

### Pipeline 配置

**服务器 Pipeline**：
```
HttpServerCodec → ChunkedWriteHandler → HttpObjectAggregator 
→ WebSocketServerProtocolHandler → SimpleWebSocketServerHandler
```

**客户端 Pipeline**：
```
HttpClientCodec → HttpObjectAggregator → SimpleWebSocketClientHandler
```

### 关键组件

1. **WebSocketServerProtocolHandler**
   - 自动处理 WebSocket 握手（HTTP Upgrade）
   - 自动处理 WebSocket 帧的编解码
   - 自动处理 Ping/Pong 心跳

2. **WebSocketFrame**
   - `TextWebSocketFrame` - 文本消息
   - `BinaryWebSocketFrame` - 二进制消息
   - `PingWebSocketFrame` - Ping 心跳
   - `PongWebSocketFrame` - Pong 响应
   - `CloseWebSocketFrame` - 关闭连接

## 通信协议对比

### WebSocket vs HTTP

详细对比请参考：**WebSocket与HTTP通信区别说明.md**

**核心区别**：
- HTTP：短连接、请求-响应模式
- WebSocket：长连接、双向实时通信

### WebSocket vs TCP

详细对比请参考：**TCP与WebSocket通信对比详解.md**

**核心区别**：
- TCP：传输层协议，需要处理粘包/拆包，不支持浏览器
- WebSocket：应用层协议（基于 TCP），自动处理消息边界，支持浏览器

### 选择建议

| 场景 | 推荐协议 |
|------|---------|
| Web 应用实时通信 | WebSocket |
| 服务器间通信 | TCP |
| 游戏客户端（原生） | TCP |
| 游戏客户端（Web） | WebSocket |
| 简单请求-响应 | HTTP |

### 核心区别

| 特性 | HTTP | WebSocket |
|------|------|-----------|
| 连接方式 | 短连接 | 长连接 |
| 通信方向 | 单向 | 双向 |
| 实时性 | 需要轮询 | 实时推送 |
| 协议开销 | 每次完整 HTTP 头 | 初始握手后帧头很小 |

## 扩展功能

### 添加认证

可以在握手阶段添加认证：

```java
@Override
protected void channelRead0(ChannelHandlerContext ctx, FullHttpRequest request) {
    // 检查认证信息
    String token = request.headers().get("Authorization");
    if (!isValidToken(token)) {
        ctx.close();
        return;
    }
    // 继续处理握手
}
```

### 添加消息路由

可以根据消息内容进行路由：

```java
if (frame instanceof TextWebSocketFrame) {
    String text = ((TextWebSocketFrame) frame).text();
    if (text.startsWith("/chat")) {
        handleChatMessage(ctx, text);
    } else if (text.startsWith("/game")) {
        handleGameMessage(ctx, text);
    }
}
```

### 添加连接管理

可以维护所有连接的集合：

```java
private static final Set<Channel> channels = ConcurrentHashMap.newKeySet();

@Override
public void channelActive(ChannelHandlerContext ctx) {
    channels.add(ctx.channel());
}

@Override
public void channelInactive(ChannelHandlerContext ctx) {
    channels.remove(ctx.channel());
}

// 广播消息
public void broadcast(String message) {
    TextWebSocketFrame frame = new TextWebSocketFrame(message);
    channels.forEach(ch -> ch.writeAndFlush(frame));
}
```

## 常见问题

### 1. 连接失败

- 检查服务器是否启动
- 检查端口是否正确（默认 9001）
- 检查防火墙设置

### 2. 握手失败

- 检查 WebSocket 路径是否正确（默认 `/websocket`）
- 检查 HTTP 头是否正确

### 3. 消息丢失

- 确保使用 `writeAndFlush()` 而不是 `write()`
- 检查网络连接是否稳定

## 参考资料

- [WebSocket RFC 6455](https://tools.ietf.org/html/rfc6455)
- [Netty WebSocket 文档](https://netty.io/4.1/api/io/netty/handler/codec/http/websocketx/package-summary.html)
- [MDN WebSocket API](https://developer.mozilla.org/en-US/docs/Web/API/WebSocket)

