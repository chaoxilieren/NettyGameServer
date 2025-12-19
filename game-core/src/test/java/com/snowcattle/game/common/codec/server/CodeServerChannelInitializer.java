package com.snowcattle.game.common.codec.server;

import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.LineBasedFrameDecoder;
import io.netty.handler.codec.string.StringDecoder;
import io.netty.handler.codec.string.StringEncoder;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;

/**
 * Created by jiangwenping on 2017/11/13.
 */

public class CodeServerChannelInitializer extends ChannelInitializer<NioSocketChannel> {
    @Override
    protected void initChannel(NioSocketChannel nioSocketChannel) throws Exception {
        ChannelPipeline channelPipLine = nioSocketChannel.pipeline();
        channelPipLine.addLast(new LineBasedFrameDecoder(1024));
        channelPipLine.addLast(new StringDecoder());
        channelPipLine.addLast(new StringEncoder());
        channelPipLine.addLast("logger", new LoggingHandler(LogLevel.INFO));
        channelPipLine.addLast(new TwoStringDecoder());
        channelPipLine.addLast(new CodeSocketServerHandler());
        channelPipLine.addLast(new CodeSocketTwoServerHandler());
    }
}
