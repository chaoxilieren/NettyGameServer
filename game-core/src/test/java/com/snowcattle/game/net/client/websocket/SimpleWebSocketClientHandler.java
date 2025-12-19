package com.snowcattle.game.net.client.websocket;

import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.websocketx.*;
import io.netty.util.CharsetUtil;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * WebSocket 客户端处理器
 * 
 * 功能：
 * 1. 处理 WebSocket 握手
 * 2. 接收服务器消息
 * 3. 处理各种 WebSocket 帧
 */
public class SimpleWebSocketClientHandler extends SimpleChannelInboundHandler<Object> {
    
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    
    private final WebSocketClientHandshaker handshaker;
    private ChannelPromise handshakeFuture;
    
    public SimpleWebSocketClientHandler(WebSocketClientHandshaker handshaker) {
        this.handshaker = handshaker;
    }
    
    /**
     * 获取握手 Future
     */
    public ChannelFuture handshakeFuture() {
        return handshakeFuture;
    }
    
    @Override
    public void handlerAdded(ChannelHandlerContext ctx) {
        handshakeFuture = ctx.newPromise();
    }
    
    /**
     * 连接建立时，开始握手
     */
    @Override
    public void channelActive(ChannelHandlerContext ctx) {
        handshaker.handshake(ctx.channel());
    }
    
    /**
     * 连接断开时
     */
    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        System.out.println("[" + getCurrentTime() + "] WebSocket 连接已断开");
    }
    
    /**
     * 接收消息
     */
    @Override
    protected void channelRead0(ChannelHandlerContext ctx, Object msg) throws Exception {
        Channel channel = ctx.channel();
        
        // 如果握手未完成，处理握手响应
        if (!handshaker.isHandshakeComplete()) {
            handshaker.finishHandshake(channel, (FullHttpResponse) msg);
            System.out.println("[" + getCurrentTime() + "] WebSocket 握手完成！");
            handshakeFuture.setSuccess();
            return;
        }
        
        // 如果收到 HTTP 响应（不应该发生）
        if (msg instanceof FullHttpResponse) {
            FullHttpResponse response = (FullHttpResponse) msg;
            throw new IllegalStateException(
                    "意外的 HTTP 响应: status=" + response.status() +
                    ", content=" + response.content().toString(CharsetUtil.UTF_8));
        }
        
        // 处理 WebSocket 帧
        WebSocketFrame frame = (WebSocketFrame) msg;
        
        if (frame instanceof TextWebSocketFrame) {
            // 处理文本消息
            TextWebSocketFrame textFrame = (TextWebSocketFrame) frame;
            System.out.println("[" + getCurrentTime() + "] 收到服务器消息: " + textFrame.text());
            
        } else if (frame instanceof BinaryWebSocketFrame) {
            // 处理二进制消息
            BinaryWebSocketFrame binaryFrame = (BinaryWebSocketFrame) frame;
            System.out.println("[" + getCurrentTime() + "] 收到二进制消息，长度: " + 
                    binaryFrame.content().readableBytes() + " 字节");
            
        } else if (frame instanceof PongWebSocketFrame) {
            // 处理 Pong 响应
            System.out.println("[" + getCurrentTime() + "] 收到 Pong 响应");
            
        } else if (frame instanceof CloseWebSocketFrame) {
            // 处理关闭帧
            System.out.println("[" + getCurrentTime() + "] 服务器请求关闭连接");
            channel.close();
            
        } else {
            System.out.println("[" + getCurrentTime() + "] 收到未知类型的帧: " + frame.getClass().getName());
        }
    }
    
    /**
     * 发生异常时
     */
    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        cause.printStackTrace();
        if (!handshakeFuture.isDone()) {
            handshakeFuture.setFailure(cause);
        }
        ctx.close();
    }
    
    /**
     * 获取当前时间字符串
     */
    private String getCurrentTime() {
        return LocalDateTime.now().format(FORMATTER);
    }
}

