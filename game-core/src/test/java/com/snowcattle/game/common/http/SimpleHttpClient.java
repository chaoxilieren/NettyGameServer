package com.snowcattle.game.common.http;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioSocketChannel;

/**
 * 简单的 HTTP 客户端示例
 * 
 * 功能：
 * 1. 连接到 HTTP 服务器
 * 2. 发送 HTTP GET 请求
 * 3. 接收并打印服务器响应
 * 
 * 使用方法：
 * 1. 先运行 SimpleHttpServer 启动服务器
 * 2. 再运行本类的 main 方法
 */
public class SimpleHttpClient {
    
    /** 服务器地址 */
    private static final String HOST = "127.0.0.1";
    /** 服务器端口 */
    private static final int PORT = 9000;
    
    public static void main(String[] args) throws Exception {
        // 1. 创建客户端线程组
        EventLoopGroup group = new NioEventLoopGroup();
        
        try {
            // 2. 创建客户端启动器
            Bootstrap bootstrap = new Bootstrap();
            
            // 3. 配置客户端
            bootstrap.group(group)  // 设置线程组
                    .channel(NioSocketChannel.class)  // 使用 NIO 方式
                    .handler(new SimpleHttpClientInitializer());  // 设置处理器
            
            // 4. 连接到服务器
            System.out.println("正在连接到服务器: " + HOST + ":" + PORT);
            ChannelFuture future = bootstrap.connect(HOST, PORT).sync();
            
            // 5. 等待连接关闭
            future.channel().closeFuture().sync();
            
        } finally {
            // 6. 优雅关闭线程组
            group.shutdownGracefully();
        }
    }
}

