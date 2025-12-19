package com.snowcattle.game.common.codec.lengthfield;

import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;
import io.netty.handler.codec.string.StringDecoder;
import io.netty.handler.codec.string.StringEncoder;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;

import java.nio.charset.StandardCharsets;

/**
 * LengthFieldBasedFrameDecoder 示例客户端
 */
public class LengthFieldClient {
    
    private static final String HOST = "127.0.0.1";
    private static final int PORT = 9998;
    
    public static void main(String[] args) throws Exception {
        EventLoopGroup group = new NioEventLoopGroup();
        
        try {
            Bootstrap bootstrap = new Bootstrap();
            bootstrap.group(group)
                    .channel(NioSocketChannel.class)
                    .handler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel ch) throws Exception {
                            ChannelPipeline pipeline = ch.pipeline();
                            
                            // 与服务器相同的解码器配置
                            pipeline.addLast("frameDecoder",
                                new LengthFieldBasedFrameDecoder(
                                    1024, 0, 4, 0, 4
                                ));
                            
                            pipeline.addLast("stringDecoder", new StringDecoder(StandardCharsets.UTF_8));
                            pipeline.addLast("stringEncoder", new StringEncoder(StandardCharsets.UTF_8));
                            pipeline.addLast("logger", new LoggingHandler(LogLevel.INFO));
                            pipeline.addLast("handler", new SimpleClientHandler());
                        }
                    });
            
            ChannelFuture future = bootstrap.connect(HOST, PORT).sync();
            System.out.println("已连接到服务器: " + HOST + ":" + PORT);
            
            // 发送几条测试消息
            Channel channel = future.channel();
            sendMessage(channel, "Hello");
            Thread.sleep(500);
            sendMessage(channel, "World");
            Thread.sleep(500);
            sendMessage(channel, "你好，世界！");
            
            // 等待响应
            Thread.sleep(2000);
            channel.close();
            
            future.channel().closeFuture().sync();
            
        } finally {
            group.shutdownGracefully();
        }
    }
    
    /**
     * 发送带长度字段的消息
     */
    private static void sendMessage(Channel channel, String message) {
        byte[] messageBytes = message.getBytes(StandardCharsets.UTF_8);
        ByteBuf buf = Unpooled.buffer(4 + messageBytes.length);
        buf.writeInt(messageBytes.length);  // 写入长度字段（4字节）
        buf.writeBytes(messageBytes);       // 写入数据
        channel.writeAndFlush(buf);
        System.out.println("客户端发送: " + message + " (长度: " + messageBytes.length + ")");
    }
    
    /**
     * 简单的客户端处理器
     */
    private static class SimpleClientHandler extends ChannelInboundHandlerAdapter {
        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
            String received = (String) msg;
            System.out.println("客户端收到: " + received);
        }
        
        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
            cause.printStackTrace();
            ctx.close();
        }
    }
}

