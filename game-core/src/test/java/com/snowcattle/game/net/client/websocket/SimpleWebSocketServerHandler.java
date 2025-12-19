package com.snowcattle.game.net.client.websocket;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.websocketx.*;

import java.io.ByteArrayInputStream;
import java.io.ObjectInputStream;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * WebSocket 服务器业务处理器
 * 
 * 功能：
 * 1. 处理文本消息（TextWebSocketFrame）
 * 2. 处理二进制消息（BinaryWebSocketFrame）
 * 3. 处理 Ping/Pong 心跳
 * 4. 处理连接关闭
 * 5. 自动回复客户端消息
 */
public class SimpleWebSocketServerHandler extends SimpleChannelInboundHandler<WebSocketFrame> {
    
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    
    @Override
    protected void channelRead0(ChannelHandlerContext ctx, WebSocketFrame frame) throws Exception {
        // 处理不同类型的 WebSocket 帧
        
        if (frame instanceof TextWebSocketFrame) {
            // 处理文本消息
            handleTextFrame(ctx, (TextWebSocketFrame) frame);
            
        } else if (frame instanceof BinaryWebSocketFrame) {
            // 处理二进制消息
            handleBinaryFrame(ctx, (BinaryWebSocketFrame) frame);
            
        } else if (frame instanceof PingWebSocketFrame) {
            // 处理 Ping 帧（心跳检测）
            System.out.println("收到 Ping，自动回复 Pong");
            ctx.channel().writeAndFlush(new PongWebSocketFrame(frame.content().retain()));
            
        } else if (frame instanceof PongWebSocketFrame) {
            // 处理 Pong 帧（心跳响应）
            System.out.println("收到 Pong 响应");
            
        } else if (frame instanceof CloseWebSocketFrame) {
            // 处理关闭帧
            System.out.println("客户端请求关闭连接");
            ctx.channel().close();
            
        } else {
            // 未知类型的帧
            String message = "不支持的消息类型: " + frame.getClass().getName();
            System.out.println(message);
            ctx.channel().writeAndFlush(new TextWebSocketFrame(message));
        }
    }
    
    /**
     * 处理文本消息
     */
    private void handleTextFrame(ChannelHandlerContext ctx, TextWebSocketFrame frame) {
        String requestText = frame.text();
        System.out.println("[" + getCurrentTime() + "] 收到客户端消息: " + requestText);
        
        // 根据消息内容进行不同的处理
        String response;
        if (requestText.equalsIgnoreCase("ping")) {
            response = "pong";
        } else if (requestText.equalsIgnoreCase("time")) {
            response = "当前时间: " + getCurrentTime();
        } else if (requestText.startsWith("echo:")) {
            // 回显消息
            response = "服务器回显: " + requestText.substring(5);
        } else {
            // 默认回复
            response = "服务器收到: " + requestText + " (时间: " + getCurrentTime() + ")";
        }
        
        // 发送回复
        ctx.channel().writeAndFlush(new TextWebSocketFrame(response));
        System.out.println("[" + getCurrentTime() + "] 发送回复: " + response);
    }
    
    /**
     * 处理二进制消息
     */
    private void handleBinaryFrame(ChannelHandlerContext ctx, BinaryWebSocketFrame frame) {
        ByteBuf content = frame.content();
        int dataLength = content.readableBytes();
        System.out.println("[" + getCurrentTime() + "] 收到二进制消息，长度: " + dataLength + " 字节");
        
        // 保存原始读取位置
        int originalReaderIndex = content.readerIndex();
        
        // 尝试解析为位置数据（24 字节：4+4+4+4+8）
        if (dataLength == 24) {
            try {
                PositionData position = parsePositionData(content);
                System.out.println("[" + getCurrentTime() + "] 解析为位置数据:");
                System.out.println("  PlayerId: " + position.playerId);
                System.out.println("  位置: (" + position.x + ", " + position.y + ", " + position.z + ")");
                System.out.println("  时间戳: " + position.timestamp);
                
                // 回传确认消息
                String response = String.format("收到位置数据: PlayerId=%d, 位置=(%.2f, %.2f, %.2f)", 
                    position.playerId, position.x, position.y, position.z);
                ctx.channel().writeAndFlush(new TextWebSocketFrame(response));
                return;
            } catch (Exception e) {
                // 解析失败，恢复读取位置，尝试其他格式
                content.readerIndex(originalReaderIndex);
            }
        }
        
        // 尝试解析为 Java 对象（序列化对象）
        try {
            content.readerIndex(originalReaderIndex);  // 重置读取位置
            Object obj = receiveJavaObject(frame);
            if (obj != null) {
                System.out.println("[" + getCurrentTime() + "] 解析为 Java 对象:");
                System.out.println("  对象类型: " + obj.getClass().getName());
                System.out.println("  对象内容: " + obj.toString());
                
                // 回传确认消息
                String response = "收到 Java 对象: " + obj.getClass().getSimpleName() + " - " + obj.toString();
                ctx.channel().writeAndFlush(new TextWebSocketFrame(response));
                return;
            }
        } catch (Exception e) {
            // 解析失败，按普通二进制数据处理
            System.out.println("[" + getCurrentTime() + "] Java 对象解析失败: " + e.getMessage());
            content.readerIndex(originalReaderIndex);
        }
        
        // 普通二进制数据处理：读取并回传
        content.readerIndex(originalReaderIndex);  // 重置读取位置
        byte[] bytes = new byte[dataLength];
        content.readBytes(bytes);
        
        // 回传二进制数据
        ByteBuf responseBuffer = ctx.alloc().buffer(bytes.length);
        responseBuffer.writeBytes(bytes);
        ctx.channel().writeAndFlush(new BinaryWebSocketFrame(responseBuffer));
        System.out.println("[" + getCurrentTime() + "] 已回传二进制数据");
    }
    
    /**
     * 解析位置数据
     * 数据格式：[playerId(4字节)][x(4字节)][y(4字节)][z(4字节)][timestamp(8字节)]
     * 
     * 注意：直接使用 ByteBuf 的读取方法，而不是转换为 ByteBuffer
     * 因为 ByteBuf 可能是 DirectByteBuf，array() 方法可能返回 null
     */
    private PositionData parsePositionData(ByteBuf content) {
        // 保存当前读取位置，以便出错时恢复
        int readerIndex = content.readerIndex();
        
        try {
            // 直接使用 ByteBuf 的读取方法（推荐方式）
            // ByteBuf 默认使用大端序（网络字节序）
            int playerId = content.readInt();
            float x = content.readFloat();
            float y = content.readFloat();
            float z = content.readFloat();
            long timestamp = content.readLong();
            
            return new PositionData(playerId, x, y, z, timestamp);
        } catch (Exception e) {
            // 如果读取失败，恢复读取位置
            content.readerIndex(readerIndex);
            throw new RuntimeException("解析位置数据失败: " + e.getMessage(), e);
        }
    }
    
    /**
     * 接收并反序列化 Java 对象
     */
    private Object receiveJavaObject(BinaryWebSocketFrame frame) throws Exception {
        ByteBuf content = frame.content();
        byte[] bytes = new byte[content.readableBytes()];
        content.readBytes(bytes);
        
        ByteArrayInputStream bais = new ByteArrayInputStream(bytes);
        ObjectInputStream ois = new ObjectInputStream(bais);
        return ois.readObject();
    }
    
    /**
     * 位置数据内部类
     */
    private static class PositionData {
        final int playerId;
        final float x, y, z;
        final long timestamp;
        
        PositionData(int playerId, float x, float y, float z, long timestamp) {
            this.playerId = playerId;
            this.x = x;
            this.y = y;
            this.z = z;
            this.timestamp = timestamp;
        }
    }
    
    /**
     * 客户端连接建立时调用
     */
    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        System.out.println("[" + getCurrentTime() + "] 客户端连接: " + ctx.channel().remoteAddress());
        // 可以在这里发送欢迎消息
        ctx.channel().writeAndFlush(new TextWebSocketFrame("欢迎连接到 WebSocket 服务器！"));
    }
    
    /**
     * 客户端断开连接时调用
     */
    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        System.out.println("[" + getCurrentTime() + "] 客户端断开连接: " + ctx.channel().remoteAddress());
    }
    
    /**
     * 发生异常时调用
     */
    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        System.err.println("[" + getCurrentTime() + "] 发生异常: " + cause.getMessage());
        cause.printStackTrace();
        ctx.close();
    }
    
    /**
     * 获取当前时间字符串
     */
    private String getCurrentTime() {
        return LocalDateTime.now().format(FORMATTER);
    }
}

