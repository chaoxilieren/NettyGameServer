package com.snowcattle.game.common.socket.server;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;

/**
 * Created by jiangwenping on 17/1/22.
 */
public final class EchoServer {

    public static  final int Port = 9999;

    public static void main(String[] args) {
        EventLoopGroup bossGroup = new NioEventLoopGroup(1);
        EventLoopGroup workerGroup = new NioEventLoopGroup();

        try{
            ServerBootstrap serverBootstrap = new ServerBootstrap();
            serverBootstrap = serverBootstrap.group(bossGroup, workerGroup);
            serverBootstrap.channel(NioServerSocketChannel.class)
                    .option(ChannelOption.SO_BACKLOG, 1024)
                    .childOption(ChannelOption.TCP_NODELAY, true)
                    .handler(new LoggingHandler(LogLevel.INFO))
//                    .childHandler(new ServerChannelInitializer());
//                    .childHandler(new StringServerChannelInitializer());
//                    .childHandler(new LengthStringServerChannelInitializer());
                    .childHandler(new NetMessageServerChannleInitializer());
            ChannelFuture serverChannelFuture = serverBootstrap.bind(Port).sync();

            System.out.println("EchoServer 启动成功，监听端口: " + Port);
            serverChannelFuture.channel().closeFuture().sync();
        }catch (Exception e){
            System.err.println("EchoServer 启动失败: " + e.getMessage());
            e.printStackTrace();
        }finally {
            bossGroup.shutdownGracefully();
            workerGroup.shutdownGracefully();
        }
    }
}
