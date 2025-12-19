package com.snowcattle.game.common.codec.server;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;

/**
 * Created by jiangwenping on 2017/11/13.
 */
public class CodeSocketTwoServerHandler extends SimpleChannelInboundHandler<String> {

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, String msg) throws Exception {
        Thread.sleep(1000L);
//        ctx.writeAndFlush(msg);
        System.out.println("服务端收到："+msg);
        ctx.writeAndFlush("服务器端返回消息：" + msg + '\n') ;
    }


    @Override
    public void channelReadComplete(ChannelHandlerContext ctx) {
        ctx.flush();
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        // Close the connection when an exception is raised.
        cause.printStackTrace();
        ctx.close();
    }



}

