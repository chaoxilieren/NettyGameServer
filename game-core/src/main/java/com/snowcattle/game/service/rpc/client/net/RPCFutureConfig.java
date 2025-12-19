package com.snowcattle.game.service.rpc.client.net;

import com.snowcattle.game.service.rpc.client.IRPCFuture;
import com.snowcattle.game.service.rpc.client.RPCFuture;

/**
 * Future 类型配置工具类，用于测试时切换不同的 Future 实现
 */
public class RPCFutureConfig {
    private static final ThreadLocal<Class<? extends IRPCFuture>> futureClassThreadLocal = new ThreadLocal<>();
    private static volatile Class<? extends IRPCFuture> globalFutureClass = null;
    
    /**
     * 设置当前线程使用的 Future 类型
     */
    public static void setFutureClass(Class<? extends IRPCFuture> futureClass) {
        futureClassThreadLocal.set(futureClass);
    }
    
    /**
     * 清除当前线程的 Future 类型设置
     */
    public static void clearFutureClass() {
        futureClassThreadLocal.remove();
    }
    
    /**
     * 设置全局 Future 类型（所有线程共享）
     */
    public static void setGlobalFutureClass(Class<? extends IRPCFuture> futureClass) {
        globalFutureClass = futureClass;
    }
    
    /**
     * 获取当前应该使用的 Future 类型
     * 优先级：ThreadLocal > 全局设置 > 系统属性 > 默认 RPCFuture
     */
    @SuppressWarnings("unchecked")
    public static Class<? extends IRPCFuture> getFutureClass() {
        // 1. 检查 ThreadLocal
        Class<? extends IRPCFuture> threadLocalClass = futureClassThreadLocal.get();
        if (threadLocalClass != null) {
            return threadLocalClass;
        }
        
        // 2. 检查全局设置
        if (globalFutureClass != null) {
            return globalFutureClass;
        }
        
        // 3. 检查系统属性
        String systemFutureType = System.getProperty("rpc.future.type");
        if (systemFutureType != null) {
            try {
                if ("COUNT_DOWN_LATCH".equalsIgnoreCase(systemFutureType) || "CDL".equalsIgnoreCase(systemFutureType)) {
                    return (Class<? extends IRPCFuture>) Class.forName("com.snowcattle.game.service.rpc.client.RPCFutureWithCountDownLatch");
                }
            } catch (ClassNotFoundException e) {
                // 忽略，使用默认
            }
        }
        
        // 4. 默认使用 RPCFuture
        return RPCFuture.class;
    }
}


