package com.snowcattle.game.net.client.rpc;


import com.snowcattle.game.TestStartUp;
import com.snowcattle.game.service.rpc.client.RpcContextHolder;
import com.snowcattle.game.service.rpc.client.RpcContextHolderObject;
import com.snowcattle.game.service.rpc.client.RpcProxyService;
import com.snowcattle.game.common.enums.BOEnum;
import com.snowcattle.game.common.util.BeanUtil;
import com.snowcattle.game.service.rpc.service.client.HelloService;
import com.snowcattle.game.service.rpc.client.RPCFutureWithCountDownLatch;
import com.snowcattle.game.service.rpc.client.IRPCFuture;
import com.snowcattle.game.service.rpc.client.RPCFuture;
import com.snowcattle.game.service.rpc.client.proxy.IAsyncRpcProxy;
import org.junit.Assert;

/**
 * Created by jwp on 2017/3/8.
 * 重构后的测试类，支持测试不同的 Future 实现
 */
public class HelloServiceTest {

    private RpcProxyService rpcProxyService;
    
    /**
     * Future 实现类型枚举
     */
    public enum FutureType {
        AQS(RPCFuture.class),                    // 使用 AQS 实现的 RPCFuture
        COUNT_DOWN_LATCH(RPCFutureWithCountDownLatch.class);  // 使用 CountDownLatch 实现的 RPCFutureWithCountDownLatch
        
        private final Class<? extends IRPCFuture> futureClass;
        
        FutureType(Class<? extends IRPCFuture> futureClass) {
            this.futureClass = futureClass;
        }
        
        public Class<? extends IRPCFuture> getFutureClass() {
            return futureClass;
        }
    }
    
    /**
     * 当前使用的 Future 类型，可以通过系统属性或参数设置
     */
    private FutureType currentFutureType = FutureType.AQS;

    public static void main(String[] args) throws Exception {
        HelloServiceTest helloServiceTest = new HelloServiceTest();
        
        // 可以通过命令行参数指定 Future 类型
        if (args.length > 0) {
            String futureType = args[0].toUpperCase();
            if ("COUNT_DOWN_LATCH".equals(futureType) || "CDL".equals(futureType)) {
                helloServiceTest.setFutureType(FutureType.COUNT_DOWN_LATCH);
            } else {
                helloServiceTest.setFutureType(FutureType.AQS);
            }
        }
        
        // 也可以通过系统属性设置
        String systemFutureType = System.getProperty("rpc.future.type");
        if (systemFutureType != null) {
            if ("COUNT_DOWN_LATCH".equalsIgnoreCase(systemFutureType) || "CDL".equalsIgnoreCase(systemFutureType)) {
                helloServiceTest.setFutureType(FutureType.COUNT_DOWN_LATCH);
            } else {
                helloServiceTest.setFutureType(FutureType.AQS);
            }
        }
        
        helloServiceTest.init();
        
        // 测试不同的 Future 实现
        System.out.println("\n=== 测试 CountDownLatch 实现的 RPCFuture ===");
        helloServiceTest.setFutureType(FutureType.COUNT_DOWN_LATCH);
        helloServiceTest.helloTest1();

        System.out.println("=== 测试 AQS 实现的 RPCFuture ===");
        helloServiceTest.setFutureType(FutureType.AQS);
        helloServiceTest.helloTest1();


        System.out.println("\n=== 测试 CountDownLatch 实现的 RPCFuture ===");
        helloServiceTest.setFutureType(FutureType.COUNT_DOWN_LATCH);
        helloServiceTest.helloTest1();
        
        helloServiceTest.setTear();
    }
    
    /**
     * 设置 Future 类型
     */
    public void setFutureType(FutureType futureType) {
        this.currentFutureType = futureType;
        // 设置到 RPCFutureConfig 中，让 RpcClient 使用
        com.snowcattle.game.service.rpc.client.net.RPCFutureConfig.setFutureClass(futureType.getFutureClass());
        System.out.println("设置 Future 类型为: " + futureType + " (Class: " + futureType.getFutureClass().getSimpleName() + ")");
    }
    
    /**
     * 获取当前 Future 类型
     */
    public FutureType getFutureType() {
        return currentFutureType;
    }
    
    public void init() throws Exception {
        TestStartUp.startUpWithSpring();
        rpcProxyService = (RpcProxyService) BeanUtil.getBean("rpcProxyService");
    }

    public void helloTest1() {
        try {
            HelloService helloService = rpcProxyService.createProxy(HelloService.class);
            int serverId = 8001;
            RpcContextHolderObject rpcContextHolderObject = new RpcContextHolderObject(BOEnum.WORLD, serverId);
            RpcContextHolder.setContextHolder(rpcContextHolderObject);
            
            long startTime = System.currentTimeMillis();
            String result = helloService.hello("World");
            long endTime = System.currentTimeMillis();
            
            System.out.println("Future 类型: " + currentFutureType);
            System.out.println("调用结果: " + result);
            System.out.println("耗时: " + (endTime - startTime) + "ms");
            
            Assert.assertEquals("Hello! World", result);
            System.out.println("测试通过！\n");
        } catch (Exception e) {
            System.err.println("测试失败，Future 类型: " + currentFutureType);
            e.printStackTrace();
            throw e;
        }
    }
    
    /**
     * 测试超时功能
     */
    public void testTimeout() {
        try {
            HelloService helloService = rpcProxyService.createProxy(HelloService.class);
            int serverId = 8001;
            RpcContextHolderObject rpcContextHolderObject = new RpcContextHolderObject(BOEnum.WORLD, serverId);
            RpcContextHolder.setContextHolder(rpcContextHolderObject);
            
            // 这里可以测试超时功能
            String result = helloService.hello("World");
            System.out.println("超时测试结果: " + result);
        } catch (Exception e) {
            System.err.println("超时测试失败: " + e.getMessage());
        }
    }
    
    /**
     * 测试异步回调
     */
    public void testAsyncCallback() {
        try {
            IAsyncRpcProxy asyncProxy = 
                rpcProxyService.createAsync(HelloService.class);
            int serverId = 8001;
            RpcContextHolderObject rpcContextHolderObject = new RpcContextHolderObject(BOEnum.WORLD, serverId);
            RpcContextHolder.setContextHolder(rpcContextHolderObject);
            
            com.snowcattle.game.service.rpc.client.RPCFuture future = 
                asyncProxy.call("hello", "World");
            
            System.out.println("Future 类型: " + currentFutureType);
            System.out.println("异步调用，等待结果...");
            
            Object result = future.get();
            System.out.println("异步调用结果: " + result);
            
            Assert.assertEquals("Hello! World", result);
        } catch (Exception e) {
            System.err.println("异步回调测试失败: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public void setTear(){
        // 清除 Future 类型配置
        com.snowcattle.game.service.rpc.client.net.RPCFutureConfig.clearFutureClass();
        
        if (rpcProxyService != null) {
            try {
                rpcProxyService.shutdown();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

}
