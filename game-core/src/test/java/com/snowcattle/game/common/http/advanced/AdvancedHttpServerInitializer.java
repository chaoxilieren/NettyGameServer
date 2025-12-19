package com.snowcattle.game.common.http.advanced;

import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.stream.ChunkedWriteHandler;

/**
 * 进阶版 HTTP 服务器 Channel 初始化器
 * 
 * 配置 Channel Pipeline：
 * 1. HttpServerCodec - HTTP 编解码器
 * 2. HttpObjectAggregator - 聚合 HTTP 消息（支持完整请求体）
 * 3. ChunkedWriteHandler - 支持大文件传输
 * 4. AdvancedHttpServerHandler - 业务处理器
 */
public class AdvancedHttpServerInitializer extends ChannelInitializer<SocketChannel> {
    
    // 创建路由并注册
    private static final HttpRouter router = createRouter();
    
    private static HttpRouter createRouter() {
        HttpRouter router = new HttpRouter();
        
        // 注册用户相关路由
        UserController userController = new UserController();
        router.get("/api/users", userController::listUsers);
        router.get("/api/users/:id", userController::getUser);
        router.post("/api/users", userController::createUser);
        router.put("/api/users/:id", userController::updateUser);
        router.delete("/api/users/:id", userController::deleteUser);
        
        // 注册文件相关路由
        FileController fileController = new FileController();
        router.get("/api/files", fileController::listFiles);
        router.post("/api/files/upload", fileController::uploadFile);
        
        // 健康检查
        router.get("/api/health", ctx -> JsonUtil.success("服务器运行正常"));
        
        return router;
    }
    
    @Override
    protected void initChannel(SocketChannel ch) throws Exception {
        ChannelPipeline pipeline = ch.pipeline();
        
        // 1. HTTP 编解码器（将字节流转换为 HTTP 请求/响应对象）
        pipeline.addLast("httpCodec", new HttpServerCodec());
        
        // 2. HTTP 消息聚合器（将多个 HTTP 消息片段聚合成完整的 FullHttpRequest）
        // 最大聚合 10MB 的内容（用于处理大文件上传）
        pipeline.addLast("aggregator", new HttpObjectAggregator(10 * 1024 * 1024));
        
        // 3. 分块写入处理器（支持大文件传输）
        pipeline.addLast("chunkedWriter", new ChunkedWriteHandler());
        
        // 4. 业务处理器（处理路由、JSON、静态文件等）
        pipeline.addLast("handler", new AdvancedHttpServerHandler(router));
    }
}

