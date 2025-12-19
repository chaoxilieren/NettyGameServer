package com.snowcattle.game.common.http.advanced;

import io.netty.handler.codec.http.*;
import io.netty.handler.codec.http.QueryStringDecoder;

import java.util.*;

/**
 * HTTP 请求上下文
 * 
 * 封装请求信息，方便处理器使用
 */
public class HttpContext {
    private final FullHttpRequest request;
    private final QueryStringDecoder queryDecoder;
    private final Map<String, String> pathParams;
    private final Map<String, String> queryParams;
    private final Map<String, String> headers;
    private String requestBody;
    
    public HttpContext(FullHttpRequest request, Map<String, String> pathParams) {
        this.request = request;
        this.pathParams = pathParams;
        this.queryDecoder = new QueryStringDecoder(request.uri());
        this.queryParams = new HashMap<>();
        this.headers = new HashMap<>();
        
        // 解析查询参数
        queryDecoder.parameters().forEach((key, values) -> {
            if (!values.isEmpty()) {
                queryParams.put(key, values.get(0));
            }
        });
        
        // 解析请求头
        request.headers().forEach(entry -> {
            headers.put(entry.getKey().toLowerCase(), entry.getValue());
        });
    }
    
    /**
     * 获取请求方法
     */
    public HttpMethod method() {
        return request.method();
    }
    
    /**
     * 获取请求路径（不含查询参数）
     */
    public String path() {
        return queryDecoder.path();
    }
    
    /**
     * 获取完整 URI
     */
    public String uri() {
        return request.uri();
    }
    
    /**
     * 获取路径参数（如 /api/users/:id 中的 id）
     */
    public String pathParam(String name) {
        return pathParams.get(name);
    }
    
    /**
     * 获取所有路径参数
     */
    public Map<String, String> pathParams() {
        return Collections.unmodifiableMap(pathParams);
    }
    
    /**
     * 获取查询参数（如 ?name=张三 中的 name）
     */
    public String queryParam(String name) {
        return queryParams.get(name);
    }
    
    /**
     * 获取查询参数，带默认值
     */
    public String queryParam(String name, String defaultValue) {
        return queryParams.getOrDefault(name, defaultValue);
    }
    
    /**
     * 获取所有查询参数
     */
    public Map<String, String> queryParams() {
        return Collections.unmodifiableMap(queryParams);
    }
    
    /**
     * 获取请求头
     */
    public String header(String name) {
        return headers.get(name.toLowerCase());
    }
    
    /**
     * 获取所有请求头
     */
    public Map<String, String> headers() {
        return Collections.unmodifiableMap(headers);
    }
    
    /**
     * 获取请求体（POST/PUT 请求）
     */
    public String body() {
        if (requestBody == null && request.content() != null) {
            requestBody = request.content().toString(io.netty.util.CharsetUtil.UTF_8);
        }
        return requestBody;
    }
    
    /**
     * 获取原始请求对象
     */
    public FullHttpRequest request() {
        return request;
    }
    
    /**
     * 判断是否为 JSON 请求
     */
    public boolean isJsonRequest() {
        String contentType = header("content-type");
        return contentType != null && contentType.contains("application/json");
    }
}

