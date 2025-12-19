package com.snowcattle.game.common.http;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.*;
import io.netty.handler.codec.http.QueryStringDecoder;
import io.netty.util.CharsetUtil;

import java.util.List;
import java.util.Map;

import static io.netty.handler.codec.http.HttpResponseStatus.*;
import static io.netty.handler.codec.http.HttpVersion.HTTP_1_1;

/**
 * HTTP 服务器业务处理器
 * 
 * 功能：
 * 1. 接收 HTTP 请求
 * 2. 解析请求路径和参数
 * 3. 返回 HTTP 响应
 * 
 * 示例请求：
 * - GET http://127.0.0.1:8080/hello
 * - GET http://127.0.0.1:8080/hello?name=张三
 */
public class SimpleHttpServerHandler extends SimpleChannelInboundHandler<FullHttpRequest> {
    
    @Override
    protected void channelRead0(ChannelHandlerContext ctx, FullHttpRequest request) throws Exception {
        // 1. 获取请求信息
        String uri = request.uri();  // 例如：/hello?name=%E5%BC%A0%E4%B8%89（URL编码后的）
        HttpMethod method = request.method();  // GET、POST 等
        
        System.out.println("收到请求: " + method + " " + uri);
        
        // 2. 使用 QueryStringDecoder 解析 URI（自动处理 URL 解码）
        // QueryStringDecoder 会将 %E5%BC%A0%E4%B8%89 解码为 "张三"
        QueryStringDecoder queryDecoder = new QueryStringDecoder(uri);
        String path = queryDecoder.path();  // 解码后的路径，例如：/hello
        
        // 3. 根据路径处理不同的请求
        String responseContent;
        if ("/hello".equals(path)) {
            // 处理 /hello 路径，传入 QueryStringDecoder 以便获取解码后的参数
            responseContent = handleHello(request, queryDecoder);
        } else if ("/info".equals(path)) {
            // 处理 /info 路径
            responseContent = handleInfo(request, queryDecoder);
        } else {
            // 404 未找到
            responseContent = "404 - 页面未找到: " + path;
        }
        
        // 4. 创建 HTTP 响应
        FullHttpResponse response = createResponse(responseContent);
        
        // 5. 发送响应
        ctx.writeAndFlush(response);
    }
    
    /**
     * 处理 /hello 路径
     */
    private String handleHello(FullHttpRequest request, QueryStringDecoder queryDecoder) {
        // 从 QueryStringDecoder 中获取查询参数（已自动解码）
        // 例如：name=%E5%BC%A0%E4%B8%89 会被解码为 name=张三
        Map<String, List<String>> params = queryDecoder.parameters();
        String name = "访客";
        
        // 获取 name 参数（如果有多个值，取第一个）
        if (params.containsKey("name") && !params.get("name").isEmpty()) {
            name = params.get("name").get(0);  // 已自动URL解码，中文正常显示
        }
        
        return String.format(
            "<html><body>" +
            "<h1>你好，%s！</h1>" +
            "<p>欢迎使用 Netty HTTP 服务器</p>" +
            "<p>请求方法: %s</p>" +
            "<p>请求路径: %s</p>" +
            "<p>原始URI: %s</p>" +
            "</body></html>",
            name, request.method(), queryDecoder.path(), request.uri()
        );
    }
    
    /**
     * 处理 /info 路径
     */
    private String handleInfo(FullHttpRequest request, QueryStringDecoder queryDecoder) {
        StringBuilder info = new StringBuilder();
        info.append("<html><body>");
        info.append("<h1>服务器信息</h1>");
        info.append("<p><strong>请求方法:</strong> ").append(request.method()).append("</p>");
        info.append("<p><strong>请求路径:</strong> ").append(queryDecoder.path()).append("</p>");
        info.append("<p><strong>原始URI:</strong> ").append(request.uri()).append("</p>");
        info.append("<p><strong>HTTP 版本:</strong> ").append(request.protocolVersion()).append("</p>");
        
        // 显示查询参数（已解码）
        Map<String, List<String>> params = queryDecoder.parameters();
        if (!params.isEmpty()) {
            info.append("<h2>查询参数（已解码）:</h2>");
            info.append("<ul>");
            params.forEach((key, values) -> {
                info.append("<li><strong>").append(key).append(":</strong> ");
                info.append(String.join(", ", values)).append("</li>");
            });
            info.append("</ul>");
        }
        
        info.append("<h2>请求头:</h2>");
        info.append("<ul>");
        request.headers().forEach(entry -> {
            info.append("<li><strong>").append(entry.getKey())
                .append(":</strong> ").append(entry.getValue()).append("</li>");
        });
        info.append("</ul>");
        info.append("</body></html>");
        return info.toString();
    }
    
    /**
     * 创建 HTTP 响应
     */
    private FullHttpResponse createResponse(String content) {
        // 将响应内容转换为 ByteBuf
        ByteBuf contentBuf = Unpooled.copiedBuffer(content, CharsetUtil.UTF_8);
        
        // 创建完整的 HTTP 响应
        FullHttpResponse response = new DefaultFullHttpResponse(
            HTTP_1_1,  // HTTP 版本
            OK,        // 状态码 200
            contentBuf // 响应内容
        );
        
        // 设置响应头
        response.headers().set(HttpHeaderNames.CONTENT_TYPE, "text/html; charset=UTF-8");
        response.headers().set(HttpHeaderNames.CONTENT_LENGTH, contentBuf.readableBytes());
        
        return response;
    }
    
    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        // 发生异常时关闭连接
        cause.printStackTrace();
        ctx.close();
    }
}

