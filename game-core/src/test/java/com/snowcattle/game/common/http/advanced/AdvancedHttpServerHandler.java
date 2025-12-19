package com.snowcattle.game.common.http.advanced;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.*;
import io.netty.util.CharsetUtil;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

import static io.netty.handler.codec.http.HttpResponseStatus.*;
import static io.netty.handler.codec.http.HttpVersion.HTTP_1_1;

/**
 * 进阶版 HTTP 服务器处理器
 * 
 * 功能：
 * 1. 路由匹配和处理
 * 2. JSON 请求/响应
 * 3. 静态资源服务
 * 4. 统一错误处理
 */
public class AdvancedHttpServerHandler extends SimpleChannelInboundHandler<FullHttpRequest> {
    
    private final HttpRouter router;
    
    // 静态资源目录（相对于项目根目录）
    private static final String STATIC_DIR = "game-core/src/test/resources/static";
    
    public AdvancedHttpServerHandler(HttpRouter router) {
        this.router = router;
    }
    
    @Override
    protected void channelRead0(ChannelHandlerContext ctx, FullHttpRequest request) throws Exception {
        try {
            String path = new io.netty.handler.codec.http.QueryStringDecoder(request.uri()).path();
            HttpMethod method = request.method();
            
            // 记录请求日志
            System.out.println(String.format("[%s] %s %s", 
                method, path, request.headers().get(HttpHeaderNames.USER_AGENT, "Unknown")));
            
            // 1. 尝试匹配路由（API 请求）
            HttpRouter.RouteMatch match = router.findRoute(method, path);
            if (match != null) {
                handleRoute(ctx, request, match);
                return;
            }
            
            // 2. 处理静态资源请求
            if (path.startsWith("/static/")) {
                handleStaticFile(ctx, request, path);
                return;
            }
            
            // 3. 404 未找到
            sendError(ctx, NOT_FOUND, "路径未找到: " + path);
            
        } catch (Exception e) {
            e.printStackTrace();
            sendError(ctx, INTERNAL_SERVER_ERROR, "服务器内部错误: " + e.getMessage());
        }
    }
    
    /**
     * 处理路由请求
     */
    private void handleRoute(ChannelHandlerContext ctx, FullHttpRequest request, 
                             HttpRouter.RouteMatch match) {
        try {
            // 创建请求上下文
            HttpContext context = new HttpContext(request, match.params);
            
            // 调用路由处理器
            String responseContent = match.getHandler().handle(context);
            
            // 发送响应
            sendResponse(ctx, request, OK, responseContent, "application/json; charset=UTF-8");
            
        } catch (Exception e) {
            e.printStackTrace();
            sendError(ctx, INTERNAL_SERVER_ERROR, "处理请求时出错: " + e.getMessage());
        }
    }
    
    /**
     * 处理静态文件请求
     */
    private void handleStaticFile(ChannelHandlerContext ctx, FullHttpRequest request, String path) {
        try {
            // 移除 /static 前缀，获取实际文件路径
            String filePath = path.substring(7);  // "/static".length() = 7
            if (filePath.startsWith("/")) {
                filePath = filePath.substring(1);
            }
            
            // 构建完整文件路径
            File file = new File(STATIC_DIR, filePath);
            
            // 安全检查：防止路径遍历攻击
            if (!file.getCanonicalPath().startsWith(new File(STATIC_DIR).getCanonicalPath())) {
                sendError(ctx, FORBIDDEN, "禁止访问");
                return;
            }
            
            if (!file.exists() || !file.isFile()) {
                sendError(ctx, NOT_FOUND, "文件不存在: " + path);
                return;
            }
            
            // 读取文件内容
            byte[] fileContent = Files.readAllBytes(file.toPath());
            
            // 根据文件扩展名确定 Content-Type
            String contentType = getContentType(file.getName());
            
            // 发送文件
            ByteBuf content = Unpooled.copiedBuffer(fileContent);
            FullHttpResponse response = new DefaultFullHttpResponse(HTTP_1_1, OK, content);
            response.headers().set(HttpHeaderNames.CONTENT_TYPE, contentType);
            response.headers().set(HttpHeaderNames.CONTENT_LENGTH, content.readableBytes());
            response.headers().set(HttpHeaderNames.CACHE_CONTROL, "public, max-age=3600");
            
            ctx.writeAndFlush(response);
            
        } catch (IOException e) {
            e.printStackTrace();
            sendError(ctx, INTERNAL_SERVER_ERROR, "读取文件失败: " + e.getMessage());
        }
    }
    
    /**
     * 根据文件名获取 Content-Type
     */
    private String getContentType(String fileName) {
        String lowerName = fileName.toLowerCase();
        if (lowerName.endsWith(".html")) return "text/html; charset=UTF-8";
        if (lowerName.endsWith(".css")) return "text/css; charset=UTF-8";
        if (lowerName.endsWith(".js")) return "application/javascript; charset=UTF-8";
        if (lowerName.endsWith(".json")) return "application/json; charset=UTF-8";
        if (lowerName.endsWith(".png")) return "image/png";
        if (lowerName.endsWith(".jpg") || lowerName.endsWith(".jpeg")) return "image/jpeg";
        if (lowerName.endsWith(".gif")) return "image/gif";
        if (lowerName.endsWith(".ico")) return "image/x-icon";
        return "application/octet-stream";
    }
    
    /**
     * 发送响应
     */
    private void sendResponse(ChannelHandlerContext ctx, FullHttpRequest request, 
                              HttpResponseStatus status, String content, String contentType) {
        ByteBuf contentBuf = Unpooled.copiedBuffer(content, CharsetUtil.UTF_8);
        
        FullHttpResponse response = new DefaultFullHttpResponse(HTTP_1_1, status, contentBuf);
        response.headers().set(HttpHeaderNames.CONTENT_TYPE, contentType);
        response.headers().set(HttpHeaderNames.CONTENT_LENGTH, contentBuf.readableBytes());
        response.headers().set(HttpHeaderNames.CONNECTION, HttpHeaderValues.KEEP_ALIVE);
        
        // CORS 支持（允许跨域请求）
        response.headers().set(HttpHeaderNames.ACCESS_CONTROL_ALLOW_ORIGIN, "*");
        response.headers().set(HttpHeaderNames.ACCESS_CONTROL_ALLOW_METHODS, "GET, POST, PUT, DELETE, OPTIONS");
        response.headers().set(HttpHeaderNames.ACCESS_CONTROL_ALLOW_HEADERS, "Content-Type");
        
        ctx.writeAndFlush(response);
    }
    
    /**
     * 发送错误响应
     */
    private void sendError(ChannelHandlerContext ctx, HttpResponseStatus status, String message) {
        String errorJson = JsonUtil.error(status.code(), message);
        sendResponse(ctx, null, status, errorJson, "application/json; charset=UTF-8");
    }
    
    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        cause.printStackTrace();
        sendError(ctx, INTERNAL_SERVER_ERROR, "服务器异常: " + cause.getMessage());
    }
}

