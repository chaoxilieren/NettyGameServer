# WebSocket 与 HTTP 通信区别详解

## 一、基本概念对比

### 1.1 HTTP 通信

**特点**：
- **请求-响应模式**：客户端发送请求，服务器返回响应，然后连接关闭
- **无状态**：每次请求都是独立的，服务器不保存客户端状态
- **单向通信**：客户端主动发起请求，服务器被动响应
- **短连接**：每次请求完成后连接关闭（HTTP/1.0）或保持一段时间后关闭（HTTP/1.1 Keep-Alive）

**通信流程**：
```
客户端                   服务器
  |                        |
  |---- HTTP Request ---->|
  |                        |
  |<--- HTTP Response ----|
  |                        |
  |     (连接关闭)          |
```

### 1.2 WebSocket 通信

**特点**：
- **全双工通信**：客户端和服务器都可以主动发送消息
- **长连接**：建立连接后保持连接，可以持续通信
- **实时性**：消息可以实时推送，不需要客户端轮询
- **低开销**：相比 HTTP，WebSocket 的协议头更小

**通信流程**：
```
客户端                   服务器
  |                        |
  |---- HTTP Upgrade ---->|
  |                        |
  |<--- 101 Switching ----|
  |                        |
  |   (WebSocket 连接建立)  |
  |                        |
  |<==== 双向通信 =======>|
  |                        |
  |<==== 双向通信 =======>|
  |                        |
  |<==== 双向通信 =======>|
  |                        |
```

## 二、技术实现对比

### 2.1 HTTP 服务器实现

**Netty Pipeline 配置**：
```java
pipeline.addLast(new HttpServerCodec());              // HTTP 编解码
pipeline.addLast(new HttpObjectAggregator(8192));    // 聚合 HTTP 消息
pipeline.addLast(new HttpServerHandler());            // 业务处理器
```

**特点**：
- 每次请求都是新的 HTTP 消息
- 需要解析 HTTP 请求头、请求体
- 响应后连接可能关闭

### 2.2 WebSocket 服务器实现

**Netty Pipeline 配置**：
```java
pipeline.addLast(new HttpServerCodec());                    // HTTP 编解码（用于握手）
pipeline.addLast(new HttpObjectAggregator(8192));          // 聚合 HTTP 消息
pipeline.addLast(new WebSocketServerProtocolHandler("/ws")); // WebSocket 协议处理
pipeline.addLast(new WebSocketServerHandler());             // 业务处理器
```

**特点**：
- 初始握手使用 HTTP 协议（Upgrade 请求）
- 握手成功后，切换到 WebSocket 协议
- 后续通信使用 WebSocket 帧（Frame）

## 三、详细对比表

| 特性 | HTTP | WebSocket |
|------|------|-----------|
| **连接方式** | 短连接（请求-响应后关闭） | 长连接（建立后保持） |
| **通信方向** | 单向（客户端→服务器） | 双向（客户端↔服务器） |
| **实时性** | 需要轮询 | 实时推送 |
| **协议开销** | 每次请求都包含完整 HTTP 头 | 初始握手后，帧头很小（2-14 字节） |
| **状态保持** | 无状态（需要 Cookie/Session） | 有状态（连接即状态） |
| **适用场景** | 网页浏览、RESTful API | 实时聊天、游戏、股票行情 |
| **浏览器支持** | 所有浏览器 | 现代浏览器（IE10+） |
| **消息格式** | HTTP 请求/响应 | WebSocket 帧（文本/二进制） |
| **心跳检测** | 不支持（需要应用层实现） | 内置 Ping/Pong 支持 |

## 四、代码示例对比

### 4.1 HTTP 通信示例

**服务器端**：
```java
// HTTP 服务器处理器
public class HttpServerHandler extends SimpleChannelInboundHandler<FullHttpRequest> {
    @Override
    protected void channelRead0(ChannelHandlerContext ctx, FullHttpRequest request) {
        // 处理 HTTP 请求
        String content = "Hello from HTTP Server";
        
        // 构造 HTTP 响应
        FullHttpResponse response = new DefaultFullHttpResponse(
            HttpVersion.HTTP_1_1, 
            HttpResponseStatus.OK,
            Unpooled.copiedBuffer(content, CharsetUtil.UTF_8)
        );
        response.headers().set(HttpHeaderNames.CONTENT_TYPE, "text/plain; charset=UTF-8");
        response.headers().set(HttpHeaderNames.CONTENT_LENGTH, response.content().readableBytes());
        
        // 发送响应
        ctx.writeAndFlush(response);
        // 连接可能关闭
    }
}
```

**客户端请求**：
```http
GET /api/hello HTTP/1.1
Host: 127.0.0.1:9000
Connection: keep-alive
```

**服务器响应**：
```http
HTTP/1.1 200 OK
Content-Type: text/plain; charset=UTF-8
Content-Length: 23

Hello from HTTP Server
```

### 4.2 WebSocket 通信示例

**服务器端**：
```java
// WebSocket 服务器处理器
public class WebSocketServerHandler extends SimpleChannelInboundHandler<WebSocketFrame> {
    @Override
    protected void channelRead0(ChannelHandlerContext ctx, WebSocketFrame frame) {
        if (frame instanceof TextWebSocketFrame) {
            // 处理文本消息
            String text = ((TextWebSocketFrame) frame).text();
            
            // 发送回复（双向通信）
            ctx.channel().writeAndFlush(new TextWebSocketFrame("Echo: " + text));
        }
    }
}
```

**客户端握手请求**：
```http
GET /websocket HTTP/1.1
Host: 127.0.0.1:9001
Upgrade: websocket
Connection: Upgrade
Sec-WebSocket-Key: dGhlIHNhbXBsZSBub25jZQ==
Sec-WebSocket-Version: 13
```

**服务器握手响应**：
```http
HTTP/1.1 101 Switching Protocols
Upgrade: websocket
Connection: Upgrade
Sec-WebSocket-Accept: s3pPLMBiTxaQ9kYGzzhZRbK+xOo=
```

**后续通信（WebSocket 帧）**：
```
客户端发送: TextWebSocketFrame("Hello")
服务器回复: TextWebSocketFrame("Echo: Hello")
（连接保持，可以继续通信）
```

## 五、性能对比

### 5.1 协议开销

**HTTP 请求头示例**（约 200-500 字节）：
```
GET /api/data HTTP/1.1
Host: example.com
User-Agent: Mozilla/5.0
Accept: application/json
Cookie: session=abc123
Connection: keep-alive
```

**WebSocket 帧头**（2-14 字节）：
```
FIN(1) + RSV(3) + Opcode(4) + Mask(1) + Payload Length(7/7+16/7+64)
```

### 5.2 实时性对比

**HTTP 轮询方式**：
```
客户端                   服务器
  |                        |
  |---- 请求数据 --------->|
  |<--- 返回数据 ----------|
  |     (等待 1 秒)         |
  |---- 请求数据 --------->|
  |<--- 返回数据 ----------|
  |     (等待 1 秒)         |
  |---- 请求数据 --------->|
  |<--- 返回数据 ----------|
```
- **延迟**：1-2 秒（取决于轮询间隔）
- **服务器压力**：高（大量无效请求）
- **带宽消耗**：高（每次请求都包含完整 HTTP 头）

**WebSocket 推送方式**：
```
客户端                   服务器
  |                        |
  |   (建立连接)            |
  |                        |
  |<--- 推送数据 ----------|
  |<--- 推送数据 ----------|
  |<--- 推送数据 ----------|
```
- **延迟**：毫秒级（实时推送）
- **服务器压力**：低（只在有数据时推送）
- **带宽消耗**：低（只有数据帧，无 HTTP 头）

## 六、使用场景建议

### 6.1 使用 HTTP 的场景

✅ **适合使用 HTTP**：
- 网页浏览（HTML、CSS、JS）
- RESTful API（CRUD 操作）
- 文件下载/上传
- 一次性数据查询
- 不需要实时性的场景

**示例**：
- 用户登录、注册
- 查询商品列表
- 提交订单
- 下载文件

### 6.2 使用 WebSocket 的场景

✅ **适合使用 WebSocket**：
- 实时聊天应用
- 在线游戏
- 股票行情推送
- 实时协作编辑
- 监控系统实时数据
- 通知推送

**示例**：
- 聊天室消息推送
- 游戏中的实时位置同步
- 股票价格实时更新
- 在线文档协作编辑
- 系统监控大屏

## 七、Netty 实现要点

### 7.1 HTTP 服务器要点

1. **Pipeline 配置**：
   ```java
   pipeline.addLast(new HttpServerCodec());
   pipeline.addLast(new HttpObjectAggregator(8192));
   pipeline.addLast(new HttpServerHandler());
   ```

2. **处理请求**：
   - 解析 HTTP 请求（方法、路径、参数）
   - 构造 HTTP 响应
   - 发送响应

3. **连接管理**：
   - 每次请求可能创建新连接
   - 或使用 Keep-Alive 复用连接

### 7.2 WebSocket 服务器要点

1. **Pipeline 配置**：
   ```java
   pipeline.addLast(new HttpServerCodec());                    // 握手阶段需要
   pipeline.addLast(new HttpObjectAggregator(8192));          // 握手阶段需要
   pipeline.addLast(new WebSocketServerProtocolHandler("/ws")); // 自动处理握手和帧编解码
   pipeline.addLast(new WebSocketServerHandler());             // 业务处理
   ```

2. **握手处理**：
   - `WebSocketServerProtocolHandler` 自动处理 HTTP Upgrade 请求
   - 自动发送 101 Switching Protocols 响应
   - 握手成功后切换到 WebSocket 协议

3. **消息处理**：
   - 接收 `WebSocketFrame`（TextWebSocketFrame、BinaryWebSocketFrame 等）
   - 可以双向发送消息
   - 支持 Ping/Pong 心跳

4. **连接管理**：
   - 连接建立后保持
   - 需要手动管理连接生命周期
   - 支持心跳检测保持连接活跃

## 八、总结

### 8.1 核心区别

| 方面 | HTTP | WebSocket |
|------|------|-----------|
| **本质** | 请求-响应协议 | 全双工通信协议 |
| **连接** | 短连接 | 长连接 |
| **实时性** | 需要轮询 | 实时推送 |
| **开销** | 每次请求完整 HTTP 头 | 初始握手后帧头很小 |

### 8.2 选择建议

- **需要实时双向通信** → 使用 **WebSocket**
- **简单的请求-响应** → 使用 **HTTP**
- **需要推送通知** → 使用 **WebSocket**
- **RESTful API** → 使用 **HTTP**
- **在线游戏、聊天** → 使用 **WebSocket**
- **网页浏览、文件下载** → 使用 **HTTP**

### 8.3 混合使用

在实际项目中，通常**混合使用** HTTP 和 WebSocket：
- **HTTP**：用于用户认证、数据查询、文件上传等
- **WebSocket**：用于实时消息推送、实时数据同步等

例如：
- 用户登录使用 HTTP POST
- 登录成功后建立 WebSocket 连接
- 实时消息通过 WebSocket 推送
- 历史消息查询使用 HTTP GET

