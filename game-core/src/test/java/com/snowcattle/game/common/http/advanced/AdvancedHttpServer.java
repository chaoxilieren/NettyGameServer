package com.snowcattle.game.common.http.advanced;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;

/**
 * 进阶版 HTTP 服务器
 * 
 * 功能特性：
 * 1. RESTful API 路由系统
 * 2. JSON 请求/响应支持
 * 3. 支持 GET、POST、PUT、DELETE 等方法
 * 4. 文件上传/下载
 * 5. 静态资源服务
 * 6. 统一错误处理
 * 7. 请求日志记录
 * 
 * 使用示例：
 * 1. 运行 main 方法启动服务器
 * 2. GET  http://127.0.0.1:9001/api/users        - 获取用户列表
 * 3. GET  http://127.0.0.1:9001/api/users/1       - 获取指定用户
 * 4. POST http://127.0.0.1:9001/api/users        - 创建用户（JSON）
 * 5. PUT  http://127.0.0.1:9001/api/users/1      - 更新用户（JSON）
 * 6. DELETE http://127.0.0.1:9001/api/users/1    - 删除用户
 * 7. GET  http://127.0.0.1:9001/static/index.html - 访问静态资源
 */
public class AdvancedHttpServer {
    
    /** 服务器监听端口 */
    private static final int PORT = 9001;
    
    public static void main(String[] args) throws Exception {
        // 1. 创建线程组
        EventLoopGroup bossGroup = new NioEventLoopGroup(1);
        EventLoopGroup workerGroup = new NioEventLoopGroup();
        
        try {
            // 2. 创建服务器启动器
            ServerBootstrap bootstrap = new ServerBootstrap();
            
            // 3. 配置服务器
            bootstrap.group(bossGroup, workerGroup)
                    .channel(NioServerSocketChannel.class)
                    .option(io.netty.channel.ChannelOption.SO_BACKLOG, 1024)
                    .option(io.netty.channel.ChannelOption.SO_REUSEADDR, true)
                    .childOption(io.netty.channel.ChannelOption.SO_KEEPALIVE, true)
                    .childOption(io.netty.channel.ChannelOption.TCP_NODELAY, true)
                    .handler(new LoggingHandler(LogLevel.INFO))
                    .childHandler(new AdvancedHttpServerInitializer());
            
            // 4. 绑定端口并启动
            ChannelFuture future = bootstrap.bind(PORT).sync();
            
            System.out.println("========================================");
            System.out.println("进阶版 HTTP 服务器已启动！");
            System.out.println("监听端口: " + PORT);
            System.out.println("========================================");
            System.out.println("API 示例：");
            System.out.println("  GET    http://127.0.0.1:" + PORT + "/api/users");
            System.out.println("  POST   http://127.0.0.1:" + PORT + "/api/users");
            System.out.println("  GET    http://127.0.0.1:" + PORT + "/api/users/1");
            System.out.println("  PUT    http://127.0.0.1:" + PORT + "/api/users/1");
            System.out.println("  DELETE http://127.0.0.1:" + PORT + "/api/users/1");
            System.out.println("  静态资源: http://127.0.0.1:" + PORT + "/static/");
            System.out.println("========================================");
            
            // 5. 等待服务器关闭
            future.channel().closeFuture().sync();
            
        } finally {
            // 6. 优雅关闭
            bossGroup.shutdownGracefully();
            workerGroup.shutdownGracefully();
        }
    }
}

