package com.snowcattle.game.common.http.advanced;

import java.util.*;

/**
 * 用户控制器示例
 * 
 * 演示 RESTful API 的实现
 */
public class UserController {
    
    // 模拟数据库（实际项目中应使用真实数据库）
    private static final Map<Integer, User> users = new HashMap<>();
    private static int nextId = 1;
    
    static {
        // 初始化一些测试数据
        users.put(1, new User(1, "张三", 25, "zhangsan@example.com"));
        users.put(2, new User(2, "李四", 30, "lisi@example.com"));
        users.put(3, new User(3, "王五", 28, "wangwu@example.com"));
        nextId = 4;
    }
    
    /**
     * GET /api/users - 获取用户列表
     */
    public String listUsers(HttpContext ctx) {
        List<User> userList = new ArrayList<>(users.values());
        return JsonUtil.success(userList);
    }
    
    /**
     * GET /api/users/:id - 获取指定用户
     */
    public String getUser(HttpContext ctx) {
        String idStr = ctx.pathParam("id");
        if (idStr == null) {
            return JsonUtil.error(400, "缺少用户ID");
        }
        
        try {
            int id = Integer.parseInt(idStr);
            User user = users.get(id);
            if (user == null) {
                return JsonUtil.error(404, "用户不存在");
            }
            return JsonUtil.success(user);
        } catch (NumberFormatException e) {
            return JsonUtil.error(400, "无效的用户ID");
        }
    }
    
    /**
     * POST /api/users - 创建用户
     */
    public String createUser(HttpContext ctx) {
        try {
            // 解析 JSON 请求体（简化版）
            String body = ctx.body();
            if (body == null || body.trim().isEmpty()) {
                return JsonUtil.error(400, "请求体不能为空");
            }
            
            // 简单解析 JSON（实际应使用 JSON 库）
            User user = parseUserFromJson(body);
            if (user == null) {
                return JsonUtil.error(400, "无效的用户数据");
            }
            
            // 创建用户
            user.id = nextId++;
            users.put(user.id, user);
            
            return JsonUtil.success(user);
            
        } catch (Exception e) {
            return JsonUtil.error(500, "创建用户失败: " + e.getMessage());
        }
    }
    
    /**
     * PUT /api/users/:id - 更新用户
     */
    public String updateUser(HttpContext ctx) {
        String idStr = ctx.pathParam("id");
        if (idStr == null) {
            return JsonUtil.error(400, "缺少用户ID");
        }
        
        try {
            int id = Integer.parseInt(idStr);
            User existingUser = users.get(id);
            if (existingUser == null) {
                return JsonUtil.error(404, "用户不存在");
            }
            
            // 解析 JSON 请求体
            String body = ctx.body();
            if (body == null || body.trim().isEmpty()) {
                return JsonUtil.error(400, "请求体不能为空");
            }
            
            User updatedUser = parseUserFromJson(body);
            if (updatedUser == null) {
                return JsonUtil.error(400, "无效的用户数据");
            }
            
            // 更新用户信息
            existingUser.name = updatedUser.name;
            existingUser.age = updatedUser.age;
            existingUser.email = updatedUser.email;
            
            return JsonUtil.success(existingUser);
            
        } catch (NumberFormatException e) {
            return JsonUtil.error(400, "无效的用户ID");
        } catch (Exception e) {
            return JsonUtil.error(500, "更新用户失败: " + e.getMessage());
        }
    }
    
    /**
     * DELETE /api/users/:id - 删除用户
     */
    public String deleteUser(HttpContext ctx) {
        String idStr = ctx.pathParam("id");
        if (idStr == null) {
            return JsonUtil.error(400, "缺少用户ID");
        }
        
        try {
            int id = Integer.parseInt(idStr);
            User user = users.remove(id);
            if (user == null) {
                return JsonUtil.error(404, "用户不存在");
            }
            return JsonUtil.success("用户已删除");
        } catch (NumberFormatException e) {
            return JsonUtil.error(400, "无效的用户ID");
        }
    }
    
    /**
     * 从 JSON 字符串解析用户（简化版）
     * 实际项目中应使用 Jackson、Gson 等库
     */
    private User parseUserFromJson(String json) {
        try {
            // 简单解析 JSON（实际应使用 JSON 库）
            json = json.trim();
            if (!json.startsWith("{") || !json.endsWith("}")) {
                return null;
            }
            
            User user = new User();
            
            // 提取 name
            int nameStart = json.indexOf("\"name\"");
            if (nameStart > 0) {
                int nameValueStart = json.indexOf("\"", nameStart + 7) + 1;
                int nameValueEnd = json.indexOf("\"", nameValueStart);
                user.name = json.substring(nameValueStart, nameValueEnd);
            }
            
            // 提取 age
            int ageStart = json.indexOf("\"age\"");
            if (ageStart > 0) {
                int ageValueStart = json.indexOf(":", ageStart) + 1;
                int ageValueEnd = json.indexOf(",", ageValueStart);
                if (ageValueEnd < 0) ageValueEnd = json.indexOf("}", ageValueStart);
                user.age = Integer.parseInt(json.substring(ageValueStart, ageValueEnd).trim());
            }
            
            // 提取 email
            int emailStart = json.indexOf("\"email\"");
            if (emailStart > 0) {
                int emailValueStart = json.indexOf("\"", emailStart + 8) + 1;
                int emailValueEnd = json.indexOf("\"", emailValueStart);
                user.email = json.substring(emailValueStart, emailValueEnd);
            }
            
            return user;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }
    
    /**
     * 用户实体类
     */
    public static class User {
        public int id;
        public String name;
        public int age;
        public String email;
        
        public User() {}
        
        public User(int id, String name, int age, String email) {
            this.id = id;
            this.name = name;
            this.age = age;
            this.email = email;
        }
    }
}

