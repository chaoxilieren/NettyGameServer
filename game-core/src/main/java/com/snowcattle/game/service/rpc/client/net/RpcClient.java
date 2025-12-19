package com.snowcattle.game.service.rpc.client.net;

import com.snowcattle.game.common.constant.Loggers;
import com.snowcattle.game.bootstrap.manager.LocalMananger;
import com.snowcattle.game.service.net.tcp.RpcRequest;
import com.snowcattle.game.service.net.tcp.RpcResponse;
import com.snowcattle.game.service.rpc.client.IRPCFuture;
import com.snowcattle.game.service.rpc.client.RPCFuture;
import com.snowcattle.game.service.rpc.client.RPCFutureService;
import com.snowcattle.game.service.rpc.server.RpcNodeInfo;
import io.netty.channel.socket.nio.NioSocketChannel;
import org.slf4j.Logger;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Created by jiangwenping on 17/3/14.
 * 检查客户端连接
 */
public class RpcClient
{
    private final Logger logger = Loggers.rpcLogger;
    private final RpcClientConnection rpcClientConnection;


    public RpcClient(RpcNodeInfo rpcNodeInfo, ExecutorService threadPool){
        rpcClientConnection = new RpcClientConnection(this, rpcNodeInfo, threadPool);
    }
    public RPCFuture sendRequest(RpcRequest request) {
        // 从配置中获取 Future 类型
        Class<? extends IRPCFuture> futureClass = RPCFutureConfig.getFutureClass();
        System.out.println("当前的futureClass = " + futureClass.getSimpleName());
        return sendRequest(request, futureClass);
    }
    
    /**
     * 发送请求，支持指定 Future 实现类型
     * @param request RPC 请求
     * @param futureClass Future 实现类
     * @return RPCFuture 实例
     */
    public RPCFuture sendRequest(RpcRequest request, Class<? extends IRPCFuture> futureClass) {
        IRPCFuture rpcFuture;
        try {
            if (futureClass == null || futureClass == RPCFuture.class) {
                rpcFuture = new RPCFuture(request);
            } else {
                rpcFuture = futureClass.getConstructor(RpcRequest.class).newInstance(request);
            }
        } catch (Exception e) {
            logger.error("创建 RPCFuture 失败，使用默认实现", e);
            rpcFuture = new RPCFuture(request);
        }
        
        RPCFutureService rpcFutureService = LocalMananger.getInstance().getLocalSpringServiceManager().getRPCFutureService();
        // 转换为 RPCFuture 以兼容现有代码
        RPCFuture result = rpcFuture instanceof RPCFuture ? (RPCFuture) rpcFuture : new RPCFutureAdapter(rpcFuture, request);
        rpcFutureService.addRPCFuture(request.getRequestId(), result);
        rpcClientConnection.writeRequest(request);
        return result;
    }
    
    /**
     * RPCFuture 适配器，用于包装其他 Future 实现
     * 注意：这是一个简化的适配器，主要用于测试
     */
    private static class RPCFutureAdapter extends RPCFuture {
        private final IRPCFuture delegate;
        
        public RPCFutureAdapter(IRPCFuture delegate, RpcRequest request) {
            super(request);
            this.delegate = delegate;
        }
        
        @Override
        public void done(RpcResponse response) {
            delegate.done(response);
        }
        
        @Override
        public boolean isTimeout() {
            return delegate.isTimeout();
        }
        
        @Override
        public Object get() throws InterruptedException, ExecutionException {
            return delegate.get();
        }
        
        @Override
        public Object get(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
            return delegate.get(timeout, unit);
        }
        
        @Override
        public boolean isDone() {
            return delegate.isDone();
        }
    }

    public NioSocketChannel getChannel() {
        return rpcClientConnection.getChannel();
    }

    public void close(){
        logger.info("rpc client close");
        if(rpcClientConnection != null) {
            rpcClientConnection.close();
        }
    }

    public void handleRpcResponser(RpcResponse rpcResponse){
        String requestId = rpcResponse.getRequestId();
        RPCFutureService rpcFutureService = LocalMananger.getInstance().getLocalSpringServiceManager().getRPCFutureService();
        RPCFuture rpcFuture = rpcFutureService.getRPCFuture(requestId);
        if (rpcFuture != null) {
            boolean removeFlag = rpcFutureService.removeRPCFuture(requestId, rpcFuture);
            if(removeFlag) {
                rpcFuture.done(rpcResponse);
            }else{
                //表示服务器已经处理过了,可能已经超时了
                logger.error("rpcFuture is remove " + requestId);
            }
        }
    }

    public RpcClientConnection getRpcClientConnection() {
        return rpcClientConnection;
    }

}
