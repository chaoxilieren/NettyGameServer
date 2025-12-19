package com.snowcattle.game.service.message.encoder;

import com.snowcattle.game.bootstrap.manager.LocalMananger;
import com.snowcattle.game.service.message.AbstractNetProtoBufUdpMessage;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.socket.DatagramPacket;
import io.netty.handler.codec.MessageToMessageEncoder;
import io.netty.util.CharsetUtil;

import java.nio.charset.Charset;
import java.util.List;

/**
 * Created by jwp on 2017/2/16.
 */
public class NetProtoBufMessageUDPEncoder extends MessageToMessageEncoder<AbstractNetProtoBufUdpMessage> {

    private final Charset charset;

    private INetProtoBufUdpMessageEncoderFactory iNetMessageEncoderFactory;

    public NetProtoBufMessageUDPEncoder() {
        this(CharsetUtil.UTF_8);
        try {
            LocalMananger localMananger = LocalMananger.getInstance();
            if (localMananger != null && localMananger.getLocalSpringBeanManager() != null) {
                NetProtoBufUdpMessageEncoderFactory netProtoBufUdpMessageEncoderFactory = 
                    localMananger.getLocalSpringBeanManager().getNetProtoBufUdpMessageEncoderFactory();
                if (netProtoBufUdpMessageEncoderFactory != null) {
                    this.iNetMessageEncoderFactory = netProtoBufUdpMessageEncoderFactory;
                    return;
                }
            }
        } catch (Exception e) {
            System.out.println("警告: Spring 未初始化，UDP 编码器将无法工作。错误: " + e.getMessage());
        }
        throw new IllegalStateException("NetProtoBufUdpMessageEncoderFactory 未初始化，请确保 Spring 上下文已启动");
    }

    public NetProtoBufMessageUDPEncoder(Charset charset) {
        if(charset == null) {
            throw new NullPointerException("charset");
        } else {
            this.charset = charset;
        }
    }

    @Override
    protected void encode(ChannelHandlerContext ctx, AbstractNetProtoBufUdpMessage msg, List<Object> out) throws Exception {
        ByteBuf netMessageBuf = iNetMessageEncoderFactory.createByteBuf(msg);
        out.add(new DatagramPacket(netMessageBuf, msg.getReceive()));
    }
}