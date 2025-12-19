package com.snowcattle.game.net.client.websocket;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;

/**
 * 简单的 WebSocket 服务器示例
 * 
 * 功能：
 * 1. 监听 9001 端口
 * 2. 处理 WebSocket 握手请求
 * 3. 支持文本和二进制消息
 * 4. 支持心跳检测（Ping/Pong）
 * 
 * 使用方法：
 * 1. 运行 main 方法启动服务器
 * 2. 运行 SimpleWebSocketClient 连接服务器
 * 3. 或者使用浏览器 WebSocket 客户端连接 ws://127.0.0.1:9001/websocket
 * 
 * WebSocket vs HTTP 区别：
 * - HTTP: 请求-响应模式，每次请求都需要建立连接
 * - WebSocket: 全双工通信，建立连接后可以双向实时通信
 */
public class SimpleWebSocketServer {
    
    /** 服务器监听端口 */
    private static final int PORT = 9001;
    
    public static void main(String[] args) throws Exception {
        // 1. 创建两个线程组
        // bossGroup: 负责接受客户端连接
        EventLoopGroup bossGroup = new NioEventLoopGroup(1);
        // workerGroup: 负责处理客户端连接的 I/O 操作
        EventLoopGroup workerGroup = new NioEventLoopGroup();
        
        try {
            // 2. 创建服务器启动器
            ServerBootstrap bootstrap = new ServerBootstrap();
            
            // 3. 配置服务器
            bootstrap.group(bossGroup, workerGroup)  // 设置线程组
                    .channel(NioServerSocketChannel.class)  // 使用 NIO 方式
                    .option(io.netty.channel.ChannelOption.SO_BACKLOG, 1024)  // 设置连接队列大小
                    .handler(new LoggingHandler(LogLevel.INFO))  // 添加日志处理器（可选）
                    .childHandler(new SimpleWebSocketServerInitializer());  // 设置客户端连接的处理器
            
            // 4. 绑定端口并启动服务器
            ChannelFuture future = bootstrap.bind(PORT).sync();
            
            System.out.println("========================================");
            System.out.println("WebSocket 服务器已启动！");
            System.out.println("WebSocket 地址: ws://127.0.0.1:" + PORT + "/websocket");
            System.out.println("========================================");
            System.out.println("提示：运行 SimpleWebSocketClient 连接服务器");
            System.out.println("========================================");
            
            // 5. 等待服务器关闭
            future.channel().closeFuture().sync();
            
        } finally {
            // 6. 优雅关闭线程组
            bossGroup.shutdownGracefully();
            workerGroup.shutdownGracefully();
        }
    }
}

