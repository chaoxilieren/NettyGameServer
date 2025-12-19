package com.snowcattle.game.common.http;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;

/**
 * 简单的 HTTP 服务器示例
 * 
 * 功能：
 * 1. 监听 8080 端口
 * 2. 接收 HTTP 请求
 * 3. 返回简单的响应
 * 
 * 使用方法：
 * 1. 运行 main 方法启动服务器
 * 2. 在浏览器访问 http://127.0.0.1:8080
 * 3. 或者运行 SimpleHttpClient 发送请求
 */
public class SimpleHttpServer {
    
    /** 服务器监听端口 */
    private static final int PORT = 9000;
    
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
                    .childHandler(new SimpleHttpServerInitializer());  // 设置客户端连接的处理器
            
            // 4. 绑定端口并启动服务器
            ChannelFuture future = bootstrap.bind(PORT).sync();
            
            System.out.println("========================================");
            System.out.println("HTTP 服务器已启动！");
            System.out.println("访问地址: http://127.0.0.1:" + PORT);
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

