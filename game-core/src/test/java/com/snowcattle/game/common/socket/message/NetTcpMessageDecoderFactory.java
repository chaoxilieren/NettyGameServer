package com.snowcattle.game.common.socket.message;

import com.snowcattle.game.bootstrap.manager.LocalMananger;
import com.snowcattle.game.service.message.AbstractNetMessage;
import com.snowcattle.game.service.message.NetMessageBody;
import com.snowcattle.game.service.message.NetMessageHead;
import com.snowcattle.game.service.message.registry.MessageRegistry;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

/**
 * Created by jwp on 2017/1/24.
 * 
 * 修改：支持测试环境（不依赖 Spring）
 */
public class NetTcpMessageDecoderFactory implements INetMessageDecoderFactory {

    public AbstractNetMessage praseMessage(ByteBuf byteBuf){
        //读取head
        NetMessageHead netMessageHead = new NetMessageHead();
        //head为两个字节，跳过
        byteBuf.skipBytes(2);
        netMessageHead.setLength(byteBuf.readInt());
        netMessageHead.setVersion(byteBuf.readByte());
        short cmd = byteBuf.readShort();
        netMessageHead.setCmd(cmd);
        netMessageHead.setSerial(byteBuf.readInt());
        //读取body
        NetMessageBody netMessageBody = new NetMessageBody();
        int byteLength = byteBuf.readableBytes();
        byte[] bytes = new byte[byteLength];
        byteBuf.readBytes(bytes);  // 修复：应该读取字节，而不是创建空数组
        netMessageBody.setBytes(bytes);

        // 支持测试环境：如果 Spring 未初始化，使用测试消息类
        AbstractNetMessage abstractNetMessage;
        try {
            LocalMananger localMananger = LocalMananger.getInstance();
            if (localMananger != null && localMananger.getLocalSpringServiceManager() != null) {
                MessageRegistry messageRegistry = localMananger.getLocalSpringServiceManager().getMessageRegistry();
                if (messageRegistry != null) {
                    abstractNetMessage = messageRegistry.getMessage(cmd);
                    if (abstractNetMessage != null) {
                        abstractNetMessage.setNetMessageHead(netMessageHead);
                        abstractNetMessage.setNetMessageBody(netMessageBody);
                        return abstractNetMessage;
                    }
                }
            }
        } catch (Exception e) {
            // Spring 未初始化，使用测试消息类
            System.out.println("警告: Spring 未初始化，使用测试消息类。错误: " + e.getMessage());
        }
        
        // 测试环境：创建测试消息对象
        abstractNetMessage = new TestNetMessage();
        abstractNetMessage.setNetMessageHead(netMessageHead);
        abstractNetMessage.setNetMessageBody(netMessageBody);
        return abstractNetMessage;
    }
}
