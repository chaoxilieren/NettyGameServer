package com.snowcattle.game.common.codec.lengthfield;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;
import io.netty.handler.codec.string.StringDecoder;
import io.netty.handler.codec.string.StringEncoder;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;

import java.nio.charset.StandardCharsets;

/**
 * LengthFieldBasedFrameDecoder 示例服务器
 * 
 * 演示不同参数配置的使用场景
 */
public class LengthFieldServer {
    
    private static final int PORT = 9998;
    
    public static void main(String[] args) throws Exception {
        EventLoopGroup bossGroup = new NioEventLoopGroup(1);
        EventLoopGroup workerGroup = new NioEventLoopGroup();
        
        try {
            ServerBootstrap bootstrap = new ServerBootstrap();
            bootstrap.group(bossGroup, workerGroup)
                    .channel(NioServerSocketChannel.class)
                    .option(ChannelOption.SO_BACKLOG, 1024)
                    .childHandler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel ch) throws Exception {
                            ChannelPipeline pipeline = ch.pipeline();
                            
                            // 示例 1: 最简单的协议 [长度(4字节)][数据]
                            // 参数说明：
                            // - maxFrameLength: 1024 (最大帧长度 1KB)
                            // - lengthFieldOffset: 0 (长度字段在开头)
                            // - lengthFieldLength: 4 (长度字段占 4 字节)
                            // - lengthAdjustment: 0 (不需要调整)
                            // - initialBytesToStrip: 4 (删除长度字段，只保留数据)
                            pipeline.addLast("frameDecoder", 
                                new LengthFieldBasedFrameDecoder(
                                    1024,  // 最大帧长度
                                    0,     // 长度字段偏移量
                                    4,     // 长度字段长度
                                    0,     // 长度调整值
                                    4      // 初始跳过字节数
                                ));
                            
                            // 将 ByteBuf 转换为 String
                            pipeline.addLast("stringDecoder", new StringDecoder(StandardCharsets.UTF_8));
                            pipeline.addLast("stringEncoder", new StringEncoder(StandardCharsets.UTF_8));
                            
                            // 日志
                            pipeline.addLast("logger", new LoggingHandler(LogLevel.INFO));
                            
                            // 业务处理器
                            pipeline.addLast("handler", new SimpleServerHandler());
                        }
                    });
            
            ChannelFuture future = bootstrap.bind(PORT).sync();
            System.out.println("========================================");
            System.out.println("LengthField 服务器已启动！");
            System.out.println("监听端口: " + PORT);
            System.out.println("========================================");
            System.out.println("协议格式: [长度(4字节)][数据(N字节)]");
            System.out.println("示例: 发送 'Hello' (5字节)");
            System.out.println("  长度字段: 00 00 00 05");
            System.out.println("  数据: Hello");
            System.out.println("========================================");
            
            future.channel().closeFuture().sync();
            
        } finally {
            bossGroup.shutdownGracefully();
            workerGroup.shutdownGracefully();
        }
    }
    
    /**
     * 简单的服务器处理器
     */
    private static class SimpleServerHandler extends ChannelInboundHandlerAdapter {
        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
            // 接收到的已经是解码后的字符串（不包含长度字段）
            String received = (String) msg;
            System.out.println("服务器收到: " + received);
            
            // 回复消息（需要添加长度字段）
            String response = "服务器回复: " + received;
            byte[] responseBytes = response.getBytes(StandardCharsets.UTF_8);
            ByteBuf responseBuf = Unpooled.buffer(4 + responseBytes.length);
            responseBuf.writeInt(responseBytes.length);  // 写入长度字段
            responseBuf.writeBytes(responseBytes);        // 写入数据
            ctx.writeAndFlush(responseBuf);
        }
        
        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
            cause.printStackTrace();
            ctx.close();
        }
    }
}

