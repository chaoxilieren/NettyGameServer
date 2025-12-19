package com.snowcattle.game.common.http.advanced;

import io.netty.handler.codec.http.HttpMethod;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * HTTP 路由处理器
 * 
 * 功能：
 * 1. 支持 RESTful 路由注册
 * 2. 支持路径参数（如 /api/users/:id）
 * 3. 支持不同 HTTP 方法的路由
 * 4. 路由匹配和参数提取
 */
public class HttpRouter {
    
    /**
     * 路由处理器接口
     */
    @FunctionalInterface
    public interface RouteHandler {
        /**
         * 处理请求
         * @param context 请求上下文
         * @return 响应内容
         */
        String handle(HttpContext context);
    }
    
    /**
     * 路由定义
     */
    static class Route {
        final HttpMethod method;
        final Pattern pattern;
        final List<String> paramNames;
        final RouteHandler handler;
        
        Route(HttpMethod method, String path, RouteHandler handler) {
            this.method = method;
            this.handler = handler;
            this.paramNames = new ArrayList<>();
            
            // 将路径参数（:id）转换为正则表达式
            // 例如：/api/users/:id -> /api/users/([^/]+)
            String regex = path.replaceAll(":([^/]+)", "([^/]+)");
            // 提取参数名
            Pattern paramPattern = Pattern.compile(":([^/]+)");
            Matcher matcher = paramPattern.matcher(path);
            while (matcher.find()) {
                paramNames.add(matcher.group(1));
            }
            
            this.pattern = Pattern.compile("^" + regex + "$");
        }
        
        /**
         * 匹配路由并提取参数
         */
        Map<String, String> match(String path) {
            Matcher matcher = pattern.matcher(path);
            if (matcher.matches()) {
                Map<String, String> params = new HashMap<>();
                for (int i = 0; i < paramNames.size(); i++) {
                    params.put(paramNames.get(i), matcher.group(i + 1));
                }
                return params;
            }
            return null;
        }
    }
    
    // 存储所有路由：方法 -> 路径 -> 路由
    private final Map<HttpMethod, List<Route>> routes = new ConcurrentHashMap<>();
    
    /**
     * 注册 GET 路由
     */
    public void get(String path, RouteHandler handler) {
        addRoute(HttpMethod.GET, path, handler);
    }
    
    /**
     * 注册 POST 路由
     */
    public void post(String path, RouteHandler handler) {
        addRoute(HttpMethod.POST, path, handler);
    }
    
    /**
     * 注册 PUT 路由
     */
    public void put(String path, RouteHandler handler) {
        addRoute(HttpMethod.PUT, path, handler);
    }
    
    /**
     * 注册 DELETE 路由
     */
    public void delete(String path, RouteHandler handler) {
        addRoute(HttpMethod.DELETE, path, handler);
    }
    
    /**
     * 添加路由
     */
    private void addRoute(HttpMethod method, String path, RouteHandler handler) {
        routes.computeIfAbsent(method, k -> new ArrayList<>()).add(new Route(method, path, handler));
    }
    
    /**
     * 查找匹配的路由
     * @return 路由和参数，如果未找到返回 null
     */
    public RouteMatch findRoute(HttpMethod method, String path) {
        List<Route> methodRoutes = routes.get(method);
        if (methodRoutes == null) {
            return null;
        }
        
        for (Route route : methodRoutes) {
            Map<String, String> params = route.match(path);
            if (params != null) {
                return new RouteMatch(route, params);
            }
        }
        
        return null;
    }
    
    /**
     * 路由匹配结果
     */
    public static class RouteMatch {
        private final Route route;
        public final Map<String, String> params;
        
        RouteMatch(Route route, Map<String, String> params) {
            this.route = route;
            this.params = params;
        }
        
        /**
         * 获取路由处理器
         */
        public RouteHandler getHandler() {
            return route.handler;
        }
    }
}

