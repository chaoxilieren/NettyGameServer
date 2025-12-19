package com.snowcattle.game.net.client.websocket;

import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.codec.http.websocketx.WebSocketServerProtocolHandler;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;
import io.netty.handler.stream.ChunkedWriteHandler;

/**
 * WebSocket 服务器 Channel 初始化器
 * 
 * Pipeline 配置说明：
 * 1. HttpServerCodec: HTTP 编解码器（用于处理 WebSocket 握手请求）
 * 2. ChunkedWriteHandler: 支持大文件传输
 * 3. HttpObjectAggregator: 将 HTTP 消息聚合为 FullHttpRequest/FullHttpResponse
 * 4. WebSocketServerProtocolHandler: WebSocket 协议处理器（处理握手、帧编解码）
 * 5. SimpleWebSocketServerHandler: 自定义业务处理器
 */
public class SimpleWebSocketServerInitializer extends ChannelInitializer<SocketChannel> {
    
    @Override
    protected void initChannel(SocketChannel ch) throws Exception {
        ChannelPipeline pipeline = ch.pipeline();
        
        // 1. HTTP 编解码器（WebSocket 握手使用 HTTP 协议）
        pipeline.addLast(new HttpServerCodec());
        
        // 2. 支持大文件传输
        pipeline.addLast(new ChunkedWriteHandler());
        
        // 3. 将 HTTP 消息聚合为完整的消息对象
        // 参数 8192 表示最大聚合的消息大小
        pipeline.addLast(new HttpObjectAggregator(8192));
        
        // 4. WebSocket 协议处理器
        // 参数 "/websocket" 是 WebSocket 的路径
        // 这个处理器会自动处理：
        //   - WebSocket 握手（HTTP Upgrade 请求）
        //   - WebSocket 帧的编解码
        //   - Ping/Pong 心跳
        //   - 连接关闭
        pipeline.addLast(new WebSocketServerProtocolHandler("/websocket", null, true));
        
        // 5. 自定义业务处理器（处理实际的业务逻辑）
        pipeline.addLast(new SimpleWebSocketServerHandler());
        
        // 6. 日志处理器（可选，用于调试）
        pipeline.addLast(new LoggingHandler(LogLevel.DEBUG));
    }
}

