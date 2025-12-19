package com.snowcattle.game.service.message.decoder;

import com.snowcattle.game.bootstrap.manager.LocalMananger;
import com.snowcattle.game.service.message.AbstractNetProtoBufUdpMessage;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.socket.DatagramPacket;
import io.netty.handler.codec.MessageToMessageDecoder;
import io.netty.util.CharsetUtil;

import java.nio.charset.Charset;
import java.util.List;

/**
 * Created by jwp on 2017/2/16.
 */
public class NetProtoBufMessageUDPDecoder extends MessageToMessageDecoder<DatagramPacket> {

    private final Charset charset;

    private INetProtoBufUdpMessageDecoderFactory iNetMessageDecoderFactory;

    public NetProtoBufMessageUDPDecoder() {
        this(CharsetUtil.UTF_8);
        try {
            LocalMananger localMananger = LocalMananger.getInstance();
            if (localMananger != null && localMananger.getLocalSpringBeanManager() != null) {
                NetProtoBufUdpMessageDecoderFactory netProtoBufUdpMessageDecoderFactory = 
                    localMananger.getLocalSpringBeanManager().getNetProtoBufUdpMessageDecoderFactory();
                if (netProtoBufUdpMessageDecoderFactory != null) {
                    this.iNetMessageDecoderFactory = netProtoBufUdpMessageDecoderFactory;
                    return;
                }
            }
        } catch (Exception e) {
            System.out.println("警告: Spring 未初始化，UDP 解码器将无法工作。错误: " + e.getMessage());
        }
        throw new IllegalStateException("NetProtoBufUdpMessageDecoderFactory 未初始化，请确保 Spring 上下文已启动");
    }

    @Override
    protected void decode(ChannelHandlerContext ctx, DatagramPacket msg, List<Object> out) throws Exception {
        AbstractNetProtoBufUdpMessage netProtoBufUDPMessage = (AbstractNetProtoBufUdpMessage) iNetMessageDecoderFactory.praseMessage(msg.content());
        netProtoBufUDPMessage.setSend(msg.sender());
        out.add(netProtoBufUDPMessage);
    }

    public NetProtoBufMessageUDPDecoder(Charset charset) {
        if(charset == null) {
            throw new NullPointerException("charset");
        } else {
            this.charset = charset;
        }
    }
}