package com.snowcattle.game.common.socket.message;

import com.snowcattle.game.service.message.AbstractNetMessage;

/**
 * 测试用的简单消息类，用于测试环境（不依赖 Spring）
 * Created for testing purposes when Spring context is not initialized.
 */
public class TestNetMessage extends AbstractNetMessage {
    
    public TestNetMessage() {
        // 空构造函数，用于测试
    }
    
    @Override
    public String toString() {
        return "TestNetMessage{cmd=" + getCmd() + ", serial=" + getSerial() + "}";
    }
}

