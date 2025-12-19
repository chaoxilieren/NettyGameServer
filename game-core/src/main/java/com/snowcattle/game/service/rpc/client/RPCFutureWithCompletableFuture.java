package com.snowcattle.game.service.rpc.client;

import com.snowcattle.game.service.net.tcp.RpcRequest;
import com.snowcattle.game.service.net.tcp.RpcResponse;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Function;

public class RPCFutureWithCompletableFuture {
    private final CompletableFuture<Object> future = new CompletableFuture<>();
    private final RpcRequest request;
    private final long startTime;

    public RPCFutureWithCompletableFuture(RpcRequest request) {
        this.request = request;
        this.startTime = System.currentTimeMillis();
    }

    public Object get() throws InterruptedException, ExecutionException {
        return future.get();  // 阻塞等待
    }

    public Object get(long timeout, TimeUnit unit)
            throws InterruptedException, ExecutionException, TimeoutException {
        return future.get(timeout, unit);
    }

    public void done(RpcResponse response) {
        if (response.isError()) {
            future.completeExceptionally(
                new RuntimeException("RPC error: " + response.getError())
            );
        } else {
            future.complete(response.getResult());  // 完成 Future
        }
    }

    public boolean isDone() {
        return future.isDone();
    }

    // CompletableFuture 的额外功能
    public CompletableFuture<Object> thenApply(Function<Object, Object> fn) {
        return future.thenApply(fn);
    }

    public CompletableFuture<Object> thenCompose(Function<Object, CompletableFuture<Object>> fn) {
        return future.thenCompose(fn);
    }

    // ... 其他方法
}