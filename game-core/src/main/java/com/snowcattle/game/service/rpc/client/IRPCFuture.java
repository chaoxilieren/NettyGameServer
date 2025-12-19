package com.snowcattle.game.service.rpc.client;

import com.snowcattle.game.service.net.tcp.RpcResponse;

import java.util.concurrent.Future;

/**
 * RPCFuture 接口，统一不同的 Future 实现
 */
public interface IRPCFuture extends Future<Object> {
    
    /**
     * 设置 RPC 响应结果
     * @param response RPC 响应
     */
    void done(RpcResponse response);
    
    /**
     * 检查是否超时
     * @return true 如果超时，false 否则
     */
    boolean isTimeout();
    
    /**
     * 添加回调
     * @param callback 回调接口
     * @return 当前 Future 实例
     */
    IRPCFuture addCallback(AsyncRPCCallback callback);
}


