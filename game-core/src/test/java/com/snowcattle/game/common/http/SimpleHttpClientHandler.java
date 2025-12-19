package com.snowcattle.game.common.http;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.*;

import static io.netty.handler.codec.http.HttpMethod.GET;
import static io.netty.handler.codec.http.HttpVersion.HTTP_1_1;

/**
 * HTTP 客户端业务处理器
 * 
 * 功能：
 * 1. 连接建立后自动发送 HTTP 请求
 * 2. 接收服务器响应并打印
 */
public class SimpleHttpClientHandler extends SimpleChannelInboundHandler<FullHttpResponse> {
    
    /**
     * 连接建立后自动调用
     */
    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        // 连接建立后，立即发送 HTTP 请求
        
        // 示例1：发送 GET /hello 请求
        sendGetRequest(ctx, "/hello");
        
        // 示例2：发送 GET /hello?name=张三 请求（带参数）
        // sendGetRequest(ctx, "/hello?name=张三");
        
        // 示例3：发送 GET /info 请求
        // sendGetRequest(ctx, "/info");
    }
    
    /**
     * 发送 GET 请求
     */
    private void sendGetRequest(ChannelHandlerContext ctx, String path) {
        // 1. 创建 HTTP 请求
        FullHttpRequest request = new DefaultFullHttpRequest(
            HTTP_1_1,  // HTTP 版本
            GET,       // 请求方法
            path       // 请求路径
        );
        
        // 2. 设置请求头
        request.headers().set(HttpHeaderNames.HOST, "127.0.0.1:8080");
        request.headers().set(HttpHeaderNames.CONNECTION, HttpHeaderValues.CLOSE);
        request.headers().set(HttpHeaderNames.ACCEPT_ENCODING, HttpHeaderValues.GZIP);
        
        // 3. 发送请求
        System.out.println("发送请求: GET " + path);
        ctx.writeAndFlush(request);
    }
    
    /**
     * 接收服务器响应
     */
    @Override
    protected void channelRead0(ChannelHandlerContext ctx, FullHttpResponse response) throws Exception {
        // 1. 获取响应状态码
        HttpResponseStatus status = response.status();
        System.out.println("========================================");
        System.out.println("收到服务器响应:");
        System.out.println("状态码: " + status.code() + " " + status.reasonPhrase());
        
        // 2. 打印响应头
        System.out.println("\n响应头:");
        response.headers().forEach(entry -> {
            System.out.println("  " + entry.getKey() + ": " + entry.getValue());
        });
        
        // 3. 打印响应内容
        System.out.println("\n响应内容:");
        String content = response.content().toString(java.nio.charset.StandardCharsets.UTF_8);
        System.out.println(content);
        System.out.println("========================================");
        
        // 4. 关闭连接
        ctx.close();
    }
    
    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        // 发生异常时打印错误并关闭连接
        cause.printStackTrace();
        ctx.close();
    }
}

