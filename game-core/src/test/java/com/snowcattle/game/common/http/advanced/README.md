# 进阶版 Netty HTTP 服务器

这是一个功能完整的 Netty HTTP 服务器示例，包含路由系统、JSON 支持、静态资源服务等功能。

## 功能特性

### 1. RESTful API 路由系统
- 支持 GET、POST、PUT、DELETE 等方法
- 支持路径参数（如 `/api/users/:id`）
- 自动参数提取和 URL 解码

### 2. JSON 请求/响应
- 自动处理 JSON 请求体
- 统一的 JSON 响应格式
- 支持中文编码

### 3. 静态资源服务
- 支持 HTML、CSS、JS、图片等静态文件
- 自动 Content-Type 识别
- 缓存控制

### 4. 统一错误处理
- 标准化的错误响应格式
- HTTP 状态码支持
- 异常捕获和处理

### 5. CORS 支持
- 跨域请求支持
- 可配置的 CORS 策略

## 快速开始

### 1. 启动服务器

运行 `AdvancedHttpServer.main()` 方法，服务器将在 `9001` 端口启动。

### 2. API 示例

#### 获取用户列表
```bash
GET http://127.0.0.1:9001/api/users
```

响应：
```json
{
  "code": 200,
  "message": "success",
  "data": [
    {"id": 1, "name": "张三", "age": 25, "email": "zhangsan@example.com"},
    {"id": 2, "name": "李四", "age": 30, "email": "lisi@example.com"}
  ]
}
```

#### 获取指定用户
```bash
GET http://127.0.0.1:9001/api/users/1
```

#### 创建用户
```bash
POST http://127.0.0.1:9001/api/users
Content-Type: application/json

{
  "name": "赵六",
  "age": 35,
  "email": "zhaoliu@example.com"
}
```

#### 更新用户
```bash
PUT http://127.0.0.1:9001/api/users/1
Content-Type: application/json

{
  "name": "张三（已更新）",
  "age": 26,
  "email": "zhangsan_new@example.com"
}
```

#### 删除用户
```bash
DELETE http://127.0.0.1:9001/api/users/1
```

#### 健康检查
```bash
GET http://127.0.0.1:9001/api/health
```

### 3. 静态资源

将静态文件放在 `game-core/src/test/resources/static/` 目录下，然后通过以下 URL 访问：

```
http://127.0.0.1:9001/static/index.html
http://127.0.0.1:9001/static/css/style.css
http://127.0.0.1:9001/static/js/app.js
```

## 代码结构

```
advanced/
├── AdvancedHttpServer.java              # 服务器主类
├── AdvancedHttpServerInitializer.java   # Channel 初始化器
├── AdvancedHttpServerHandler.java       # 业务处理器
├── HttpRouter.java                      # 路由系统
├── HttpContext.java                     # 请求上下文
├── JsonUtil.java                        # JSON 工具类
├── UserController.java                  # 用户控制器示例
├── FileController.java                  # 文件控制器示例
└── README.md                            # 本文档
```

## 核心组件说明

### HttpRouter（路由系统）

负责路由注册和匹配：

```java
HttpRouter router = new HttpRouter();
router.get("/api/users", ctx -> { /* 处理逻辑 */ });
router.post("/api/users", ctx -> { /* 处理逻辑 */ });
router.get("/api/users/:id", ctx -> { 
    String id = ctx.pathParam("id");  // 获取路径参数
});
```

### HttpContext（请求上下文）

封装请求信息，方便使用：

```java
// 获取路径参数
String id = ctx.pathParam("id");

// 获取查询参数
String name = ctx.queryParam("name");

// 获取请求头
String contentType = ctx.header("content-type");

// 获取请求体
String body = ctx.body();
```

### JsonUtil（JSON 工具）

提供 JSON 序列化和标准响应格式：

```java
// 成功响应
JsonUtil.success(data);

// 错误响应
JsonUtil.error(404, "资源未找到");
```

## 扩展指南

### 添加新路由

在 `AdvancedHttpServerInitializer.createRouter()` 方法中添加：

```java
router.get("/api/products", productController::listProducts);
router.post("/api/products", productController::createProduct);
```

### 添加中间件

可以在 `AdvancedHttpServerHandler` 中添加中间件逻辑，例如：

```java
// 请求日志
System.out.println("Request: " + method + " " + path);

// 身份验证
if (!isAuthenticated(ctx)) {
    sendError(ctx, UNAUTHORIZED, "未授权");
    return;
}
```

### 使用真实 JSON 库

当前 `JsonUtil` 是简化实现，实际项目中应使用 Jackson 或 Gson：

```java
// 使用 Jackson
ObjectMapper mapper = new ObjectMapper();
String json = mapper.writeValueAsString(obj);
User user = mapper.readValue(json, User.class);
```

## 注意事项

1. **JSON 解析**：当前使用的是简化版 JSON 解析，生产环境应使用成熟的 JSON 库
2. **文件上传**：当前文件上传功能未完整实现，需要使用 `HttpPostRequestDecoder` 处理 multipart/form-data
3. **线程安全**：`UserController` 中的 `users` Map 在多线程环境下需要同步处理
4. **错误处理**：可以根据需要扩展更详细的错误处理逻辑
5. **性能优化**：对于高并发场景，可以考虑连接池、异步处理等优化

## 测试工具

可以使用以下工具测试 API：

- **浏览器**：直接访问 GET 请求
- **Postman**：测试 POST、PUT、DELETE 请求
- **curl**：命令行测试

```bash
# 获取用户列表
curl http://127.0.0.1:9001/api/users

# 创建用户
curl -X POST http://127.0.0.1:9001/api/users \
  -H "Content-Type: application/json" \
  -d '{"name":"测试用户","age":20,"email":"test@example.com"}'
```

## 下一步

- [ ] 集成 Jackson/Gson 进行 JSON 处理
- [ ] 实现完整的文件上传功能
- [ ] 添加数据库支持
- [ ] 实现 Session 和 Cookie 管理
- [ ] 添加请求限流和认证
- [ ] 支持 WebSocket
- [ ] 添加单元测试

