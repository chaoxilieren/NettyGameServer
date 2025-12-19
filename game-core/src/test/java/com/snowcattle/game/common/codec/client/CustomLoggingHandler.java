package com.snowcattle.game.common.codec.client;

import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.handler.logging.LogLevel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 自定义 LoggingHandler，使用自定义 Logger 以便在日志中显示正确的类名
 * Created for codec test
 */
public class CustomLoggingHandler extends ChannelDuplexHandler {
    
    private static final Logger logger = LoggerFactory.getLogger("NettyLoggingHandler");
    private final LogLevel level;
    
    public CustomLoggingHandler(LogLevel level) {
        this.level = level;
    }
    
    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        if (isEnabled()) {
            logger.info("[{}] READ: {}", ctx.channel().id(), msg);
        }
        ctx.fireChannelRead(msg);
    }
    
    @Override
    public void channelReadComplete(ChannelHandlerContext ctx) throws Exception {
        if (isEnabled()) {
            logger.info("[{}] READ COMPLETE", ctx.channel().id());
        }
        ctx.fireChannelReadComplete();
    }
    
    @Override
    public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
        if (isEnabled()) {
            logger.info("[{}] WRITE: {}", ctx.channel().id(), msg);
        }
        ctx.write(msg, promise);
    }
    
    @Override
    public void flush(ChannelHandlerContext ctx) throws Exception {
        if (isEnabled()) {
            logger.info("[{}] FLUSH", ctx.channel().id());
        }
        ctx.flush();
    }
    
    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        logger.error("[{}] EXCEPTION", ctx.channel().id(), cause);
        ctx.fireExceptionCaught(cause);
    }
    
    private boolean isEnabled() {
        switch (level) {
            case TRACE:
                return logger.isTraceEnabled();
            case DEBUG:
                return logger.isDebugEnabled();
            case INFO:
                return logger.isInfoEnabled();
            case WARN:
                return logger.isWarnEnabled();
            case ERROR:
                return logger.isErrorEnabled();
            default:
                return false;
        }
    }
}

