package com.snowcattle.game.service.message.decoder;

import com.snowcattle.game.service.message.AbstractNetProtoBufMessage;
import com.snowcattle.game.service.message.NetProtoBufMessageBody;
import com.snowcattle.game.common.constant.Loggers;
import com.snowcattle.game.common.exception.CodecException;
import com.snowcattle.game.bootstrap.manager.LocalMananger;
import com.snowcattle.game.service.message.NetUdpMessageHead;
import com.snowcattle.game.service.message.registry.MessageRegistry;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.springframework.stereotype.Service;

/**
 * Created by jiangwenping on 17/2/20.
 */
@Service
public class NetProtoBufUdpMessageDecoderFactory implements INetProtoBufUdpMessageDecoderFactory{
    @Override
    public AbstractNetProtoBufMessage praseMessage(ByteBuf byteBuf) throws CodecException {
        //读取head
        NetUdpMessageHead netMessageHead = new NetUdpMessageHead();
        //head为两个字节，跳过
        byteBuf.skipBytes(2);
        netMessageHead.setLength(byteBuf.readInt());
        netMessageHead.setVersion(byteBuf.readByte());

        //读取内容
        short cmd = byteBuf.readShort();
        netMessageHead.setCmd(cmd);
        netMessageHead.setSerial(byteBuf.readInt());
        
        //读取tocken
        netMessageHead.setPlayerId(byteBuf.readLong());
        netMessageHead.setTocken(byteBuf.readInt());

        //读取body
        NetProtoBufMessageBody netMessageBody = new NetProtoBufMessageBody();
        int byteLength = byteBuf.readableBytes();
        byte[] bytes = new byte[byteLength];
        byteBuf.getBytes(byteBuf.readerIndex(), bytes);
        netMessageBody.setBytes(bytes);

        // 支持测试环境：如果 Spring 未初始化，抛出异常
        AbstractNetProtoBufMessage netMessage;
        try {
            LocalMananger localMananger = LocalMananger.getInstance();
            if (localMananger != null && localMananger.getLocalSpringServiceManager() != null) {
                MessageRegistry messageRegistry = localMananger.getLocalSpringServiceManager().getMessageRegistry();
                if (messageRegistry != null) {
                    netMessage = messageRegistry.getMessage(cmd);
                    if (netMessage != null) {
                        netMessage.setNetMessageHead(netMessageHead);
                        netMessage.setNetMessageBody(netMessageBody);
                        try {
                            netMessage.decoderNetProtoBufMessageBody();
                            netMessage.releaseMessageBody();
                        } catch (Exception e) {
                            throw new CodecException("message cmd " + cmd + "decoder error", e);
                        }
                        if (Loggers.sessionLogger.isDebugEnabled()) {
                            Loggers.sessionLogger.debug("revice net message" + netMessage.toAllInfoString());
                        }
                        return netMessage;
                    }
                }
            }
        } catch (Exception e) {
            System.out.println("警告: Spring 未初始化，UDP 消息解码失败。错误: " + e.getMessage());
        }
        throw new CodecException("无法创建消息对象，cmd=" + cmd + "，请确保 Spring 上下文已启动且消息已注册");
    }
}
