package com.snowcattle.game.common.http.advanced;

import java.util.*;

/**
 * JSON 工具类（简化版）
 * 
 * 注意：实际项目中应使用 Jackson、Gson 等成熟的 JSON 库
 * 这里提供一个简单的实现用于演示
 */
public class JsonUtil {
    
    /**
     * 将对象转换为 JSON 字符串（简化实现）
     */
    public static String toJson(Object obj) {
        if (obj == null) {
            return "null";
        }
        
        if (obj instanceof String) {
            return "\"" + escape((String) obj) + "\"";
        }
        
        if (obj instanceof Number || obj instanceof Boolean) {
            return obj.toString();
        }
        
        if (obj instanceof Map) {
            return mapToJson((Map<?, ?>) obj);
        }
        
        if (obj instanceof Collection) {
            return collectionToJson((Collection<?>) obj);
        }
        
        // 检查是否为 JDK 内部类（避免反射访问限制）
        if (isJdkInternalClass(obj.getClass())) {
            // 对于 JDK 内部类，使用 toString() 或返回类型信息
            return "\"" + escape(obj.toString()) + "\"";
        }
        
        // 简单对象转 JSON（通过反射获取字段）
        return objectToJson(obj);
    }
    
    /**
     * 判断是否为 JDK 内部类
     */
    private static boolean isJdkInternalClass(Class<?> clazz) {
        String packageName = clazz.getPackage() != null ? clazz.getPackage().getName() : "";
        // JDK 核心包的类
        return packageName.startsWith("java.") && 
               !packageName.startsWith("java.lang.reflect") &&
               !clazz.getName().startsWith("com.snowcattle");
    }
    
    /**
     * Map 转 JSON
     */
    private static String mapToJson(Map<?, ?> map) {
        if (map.isEmpty()) {
            return "{}";
        }
        
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            if (!first) {
                sb.append(",");
            }
            first = false;
            sb.append("\"").append(entry.getKey()).append("\":");
            sb.append(toJson(entry.getValue()));
        }
        sb.append("}");
        return sb.toString();
    }
    
    /**
     * Collection 转 JSON 数组
     */
    private static String collectionToJson(Collection<?> collection) {
        if (collection.isEmpty()) {
            return "[]";
        }
        
        StringBuilder sb = new StringBuilder("[");
        boolean first = true;
        for (Object item : collection) {
            if (!first) {
                sb.append(",");
            }
            first = false;
            sb.append(toJson(item));
        }
        sb.append("]");
        return sb.toString();
    }
    
    /**
     * 对象转 JSON（简化版，使用反射）
     * 注意：JDK 17 模块系统限制了对 JDK 内部类的反射访问
     */
    private static String objectToJson(Object obj) {
        try {
            Class<?> clazz = obj.getClass();
            
            // 再次检查是否为 JDK 内部类
            if (isJdkInternalClass(clazz)) {
                return "\"" + escape(obj.toString()) + "\"";
            }
            
            java.lang.reflect.Field[] fields = clazz.getDeclaredFields();
            StringBuilder sb = new StringBuilder("{");
            boolean first = true;
            
            for (java.lang.reflect.Field field : fields) {
                // 跳过静态字段和序列化相关字段
                if (java.lang.reflect.Modifier.isStatic(field.getModifiers()) ||
                    field.getName().equals("serialVersionUID")) {
                    continue;
                }
                
                try {
                    // 尝试设置可访问（可能失败，如果模块系统阻止）
                    field.setAccessible(true);
                    Object value = field.get(obj);
                    
                    if (value != null) {
                        if (!first) {
                            sb.append(",");
                        }
                        first = false;
                        sb.append("\"").append(field.getName()).append("\":");
                        sb.append(toJson(value));
                    }
                } catch (IllegalAccessException | SecurityException e) {
                    // 如果无法访问字段（JDK 17 模块限制），跳过该字段
                    // 不抛出异常，继续处理其他字段
                    System.err.println("警告: 无法访问字段 " + field.getName() + " - " + e.getMessage());
                }
            }
            
            // 如果没有成功序列化任何字段，返回类型信息
            if (first) {
                return "\"" + escape(clazz.getSimpleName() + "@" + Integer.toHexString(obj.hashCode())) + "\"";
            }
            
            sb.append("}");
            return sb.toString();
        } catch (Exception e) {
            // 如果反射完全失败，返回错误信息
            return "{\"error\":\"" + escape("序列化失败: " + e.getMessage()) + "\"}";
        }
    }
    
    /**
     * 转义字符串中的特殊字符
     */
    private static String escape(String str) {
        if (str == null) {
            return "";
        }
        return str.replace("\\", "\\\\")
                 .replace("\"", "\\\"")
                 .replace("\n", "\\n")
                 .replace("\r", "\\r")
                 .replace("\t", "\\t");
    }
    
    /**
     * 创建成功响应 JSON
     */
    public static String success(Object data) {
        return "{\"code\":200,\"message\":\"success\",\"data\":" + toJson(data) + "}";
    }
    
    /**
     * 创建错误响应 JSON
     */
    public static String error(int code, String message) {
        return String.format("{\"code\":%d,\"message\":\"%s\"}", code, escape(message));
    }
}

