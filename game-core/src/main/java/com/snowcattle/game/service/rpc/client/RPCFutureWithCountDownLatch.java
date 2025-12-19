package com.snowcattle.game.service.rpc.client;

import com.snowcattle.game.bootstrap.manager.LocalMananger;
import com.snowcattle.game.common.constant.Loggers;
import com.snowcattle.game.service.config.GameServerConfigService;
import com.snowcattle.game.service.net.tcp.RpcRequest;
import com.snowcattle.game.service.net.tcp.RpcResponse;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.locks.ReentrantLock;

public class RPCFutureWithCountDownLatch implements IRPCFuture {

    private final Logger logger = Loggers.rpcLogger;
    private final RpcRequest request;
    private RpcResponse response;
    private final long startTime;

    private final long responseTimeThreshold = 5000;

    private final List<AsyncRPCCallback> pendingCallbacks = new ArrayList<AsyncRPCCallback>();
    private final ReentrantLock lock = new ReentrantLock();

    private CountDownLatch latch = new CountDownLatch(1) ;

    public RPCFutureWithCountDownLatch(RpcRequest request) {
        this.request = request;
        this.startTime = System.currentTimeMillis();
    }

    @Override
    public boolean cancel(boolean mayInterruptIfRunning) {
        return false;
    }

    @Override
    public boolean isCancelled() {
        return false;
    }

    @Override
    public boolean isDone() {
        return latch.getCount() == 0;
    }

    @Override
    public Object get() throws InterruptedException, ExecutionException {
        latch.await();
        if (this.response != null) {
            return this.response.getResult();
        }

        return null;
    }

    @Override
    public Object get(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
        boolean success = latch.await(timeout, unit);
        if (success) {
            if (this.response != null) {
                return this.response.getResult();
            }

            return null;
        } else {
            throw new RuntimeException("Timeout exception. Request id: " + this.request.getRequestId()
                    + ". Request class name: " + this.request.getClassName()
                    + ". Request method: " + this.request.getMethodName());
        }
    }

    public void done(RpcResponse response) {
        this.response = response;
        latch.countDown();
        invokeCallbacks();
        // Threshold
        long responseTime = System.currentTimeMillis() - startTime;
        if (responseTime > this.responseTimeThreshold) {
            logger.warn("Service response time is too slow. Request id = " + response.getRequestId() + ". Response Time = " + responseTime + "ms");
        }
    }

    public boolean isTimeout(){
        long responseTime = System.currentTimeMillis() - startTime;
        GameServerConfigService gameServerConfigService = LocalMananger.getInstance().getLocalSpringServiceManager().getGameServerConfigService();
        int timeOut = gameServerConfigService.getGameServerConfig().getRpcFutureDeleteTimeOut();
        if (responseTime >= timeOut) {
            return true;
        }
        return false;
    }

    private void invokeCallbacks() {
        lock.lock();
        try {
            for (final AsyncRPCCallback callback : pendingCallbacks) {
                runCallback(callback);
            }
        } finally {
            lock.unlock();
        }
    }

    @Override
    public IRPCFuture addCallback(AsyncRPCCallback callback) {
        lock.lock();
        try {
            if (isDone()) {
                runCallback(callback);
            } else {
                this.pendingCallbacks.add(callback);
            }
        } finally {
            lock.unlock();
        }
        return this;
    }

    private void runCallback(final AsyncRPCCallback callback) {
        final RpcResponse res = this.response;
        RpcProxyService rpcProxyService = LocalMananger.getInstance().getLocalSpringServiceManager().getRpcProxyService();
        rpcProxyService.submit(new Runnable() {
            @Override
            public void run() {
                if (!res.isError()) {
                    callback.success(res.getResult());
                } else {
                    callback.fail(new RuntimeException("Response error", new Throwable(res.getError())));
                }
            }
        });
    }
}
