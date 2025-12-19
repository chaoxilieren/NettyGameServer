# Netty HTTP 通信简单示例

## 简介

这是一个通俗易懂的 Netty HTTP 通信示例，包含：
- **SimpleHttpServer**: 简单的 HTTP 服务器
- **SimpleHttpClient**: 简单的 HTTP 客户端

## 文件说明

### 服务器端
- `SimpleHttpServer.java` - 服务器启动类
- `SimpleHttpServerInitializer.java` - 服务器 Pipeline 配置
- `SimpleHttpServerHandler.java` - 服务器业务处理器

### 客户端
- `SimpleHttpClient.java` - 客户端启动类
- `SimpleHttpClientInitializer.java` - 客户端 Pipeline 配置
- `SimpleHttpClientHandler.java` - 客户端业务处理器

## 快速开始

### 1. 启动服务器

运行 `SimpleHttpServer` 的 `main` 方法：

```java
public static void main(String[] args) throws Exception {
    // 运行 SimpleHttpServer.main()
}
```

控制台输出：
```
========================================
HTTP 服务器已启动！
访问地址: http://127.0.0.1:8080
========================================
```

### 2. 测试方式

#### 方式1：使用浏览器访问

在浏览器中访问：
- `http://127.0.0.1:8080/hello` - 返回问候页面
- `http://127.0.0.1:8080/hello?name=张三` - 带参数的问候页面
- `http://127.0.0.1:8080/info` - 显示请求信息

#### 方式2：使用客户端程序

运行 `SimpleHttpClient` 的 `main` 方法，会自动发送请求并打印响应。

## 代码结构说明

### Pipeline（处理链）概念

Netty 使用 Pipeline 来处理数据，数据按照添加顺序依次经过各个 Handler：

**服务器 Pipeline：**
```
网络数据 
  → HttpRequestDecoder (解码 HTTP 请求)
  → HttpObjectAggregator (聚合完整的请求)
  → HttpResponseEncoder (编码 HTTP 响应)
  → SimpleHttpServerHandler (业务处理)
```

**客户端 Pipeline：**
```
业务代码
  → HttpClientCodec (编码请求/解码响应)
  → HttpObjectAggregator (聚合完整的响应)
  → SimpleHttpClientHandler (处理响应)
  → 网络发送
```

### 关键组件说明

#### 1. HttpRequestDecoder
- **作用**：将接收到的字节流解码为 HTTP 请求对象
- **输入**：ByteBuf（字节流）
- **输出**：HttpRequest、HttpContent 等对象

#### 2. HttpObjectAggregator
- **作用**：将 HTTP 请求/响应的多个部分聚合成完整的对象
- **为什么需要**：HTTP 请求可能分多次传输（请求头、请求体），这个组件将它们合并
- **输出**：FullHttpRequest 或 FullHttpResponse

#### 3. HttpResponseEncoder / HttpClientCodec
- **作用**：将 HTTP 响应/请求对象编码为字节流
- **输入**：HttpResponse、HttpContent 等对象
- **输出**：ByteBuf（字节流）

#### 4. SimpleHttpServerHandler / SimpleHttpClientHandler
- **作用**：业务逻辑处理
- **输入**：FullHttpRequest（服务器）或 FullHttpResponse（客户端）
- **输出**：FullHttpResponse（服务器）或打印响应（客户端）

## 示例请求/响应

### 请求示例

**GET /hello**
```
GET /hello HTTP/1.1
Host: 127.0.0.1:8080
Connection: close
Accept-Encoding: gzip
```

### 响应示例

**200 OK**
```
HTTP/1.1 200 OK
Content-Type: text/html; charset=UTF-8
Content-Length: 123

<html><body>
<h1>你好，访客！</h1>
<p>欢迎使用 Netty HTTP 服务器</p>
</body></html>
```

## 扩展功能

### 添加新的请求路径

在 `SimpleHttpServerHandler` 中添加新的处理方法：

```java
@Override
protected void channelRead0(ChannelHandlerContext ctx, FullHttpRequest request) {
    String path = request.uri().split("\\?")[0];
    String responseContent;
    
    if ("/hello".equals(path)) {
        responseContent = handleHello(request);
    } else if ("/newpath".equals(path)) {  // 添加新路径
        responseContent = handleNewPath(request);
    } else {
        responseContent = "404 - 页面未找到";
    }
    
    FullHttpResponse response = createResponse(responseContent);
    ctx.writeAndFlush(response);
}

private String handleNewPath(FullHttpRequest request) {
    return "<html><body><h1>新路径处理</h1></body></html>";
}
```

### 处理 POST 请求

```java
if (request.method() == HttpMethod.POST) {
    // 获取 POST 请求体
    String body = request.content().toString(CharsetUtil.UTF_8);
    // 处理 POST 数据...
}
```

### 返回 JSON 响应

```java
private FullHttpResponse createJsonResponse(String json) {
    ByteBuf content = Unpooled.copiedBuffer(json, CharsetUtil.UTF_8);
    FullHttpResponse response = new DefaultFullHttpResponse(
        HTTP_1_1, OK, content
    );
    response.headers().set(HttpHeaderNames.CONTENT_TYPE, "application/json; charset=UTF-8");
    response.headers().set(HttpHeaderNames.CONTENT_LENGTH, content.readableBytes());
    return response;
}
```

## 与原有示例的区别

### 原有示例（HttpSnoopServer）的特点：
- ✅ 功能完整（包含 SSL、Cookie、分块传输等）
- ❌ 代码复杂，不易理解
- ❌ 注释较少

### 新示例（SimpleHttpServer）的特点：
- ✅ 代码简洁，易于理解
- ✅ 注释详细，每个步骤都有说明
- ✅ 专注于核心功能（HTTP 请求/响应）
- ✅ 适合学习和入门

## 常见问题

### Q: 为什么需要 HttpObjectAggregator？
A: HTTP 请求/响应可能分多次传输，HttpObjectAggregator 将它们聚合成完整的对象，简化处理。

### Q: 服务器和客户端的 Pipeline 为什么不同？
A: 
- 服务器需要：解码请求 + 编码响应
- 客户端需要：编码请求 + 解码响应
- HttpClientCodec 同时包含编码和解码功能

### Q: 如何修改端口？
A: 修改 `SimpleHttpServer` 中的 `PORT` 常量。

### Q: 如何支持 HTTPS？
A: 参考原有的 `HttpSnoopServer` 示例，添加 SSL 处理器。

## 总结

这个示例展示了 Netty HTTP 通信的核心流程：
1. **配置 Pipeline**：添加编解码器和业务处理器
2. **处理请求**：在 Handler 中处理业务逻辑
3. **返回响应**：创建并发送 HTTP 响应

通过这个简单的示例，可以快速理解 Netty HTTP 通信的基本原理。

