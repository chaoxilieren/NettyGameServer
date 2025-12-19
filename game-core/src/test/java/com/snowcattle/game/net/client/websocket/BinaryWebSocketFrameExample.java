package com.snowcattle.game.net.client.websocket;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.websocketx.BinaryWebSocketFrame;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;

/**
 * BinaryWebSocketFrame 二进制帧通信详细示例
 * 
 * 本类展示了 BinaryWebSocketFrame 的各种实际应用场景和用法
 */
public class BinaryWebSocketFrameExample {
    
    /**
     * 场景 1: 发送简单的字节数组
     * 适用场景：简单的二进制数据传输
     */
    public static void sendSimpleBytes(ChannelHandlerContext ctx, byte[] data) {
        ByteBuf buffer = Unpooled.wrappedBuffer(data);
        BinaryWebSocketFrame frame = new BinaryWebSocketFrame(buffer);
        ctx.writeAndFlush(frame);
    }
    
    /**
     * 场景 2: 发送 Java 对象（序列化）
     * 适用场景：游戏状态同步、复杂数据结构传输
     */
    public static void sendJavaObject(ChannelHandlerContext ctx, Object obj) throws Exception {
        // 序列化对象为字节数组
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ObjectOutputStream oos = new ObjectOutputStream(baos);
        oos.writeObject(obj);
        oos.close();
        byte[] bytes = baos.toByteArray();
        
        // 发送二进制帧
        ByteBuf buffer = Unpooled.wrappedBuffer(bytes);
        BinaryWebSocketFrame frame = new BinaryWebSocketFrame(buffer);
        ctx.writeAndFlush(frame);
    }
    
    /**
     * 接收并反序列化 Java 对象
     */
    public static Object receiveJavaObject(BinaryWebSocketFrame frame) throws Exception {
        ByteBuf content = frame.content();
        byte[] bytes = new byte[content.readableBytes()];
        content.readBytes(bytes);
        
        ByteArrayInputStream bais = new ByteArrayInputStream(bytes);
        ObjectInputStream ois = new ObjectInputStream(bais);
        return ois.readObject();
    }
    
    /**
     * 场景 3: 发送自定义协议消息（游戏消息）
     * 适用场景：游戏协议、RPC 调用、自定义消息格式
     * 
     * 消息格式：
     * [消息类型(2字节)][消息ID(4字节)][消息体长度(4字节)][消息体(N字节)]
     */
    public static void sendGameMessage(ChannelHandlerContext ctx, 
                                       short messageType, 
                                       int messageId, 
                                       byte[] messageBody) {
        // 计算总长度：消息类型(2) + 消息ID(4) + 消息体长度(4) + 消息体
        int totalLength = 2 + 4 + 4 + messageBody.length;
        
        ByteBuf buffer = Unpooled.buffer(totalLength);
        buffer.writeShort(messageType);      // 消息类型
        buffer.writeInt(messageId);          // 消息ID
        buffer.writeInt(messageBody.length); // 消息体长度
        buffer.writeBytes(messageBody);      // 消息体
        
        BinaryWebSocketFrame frame = new BinaryWebSocketFrame(buffer);
        ctx.writeAndFlush(frame);
    }
    
    /**
     * 解析游戏消息
     */
    public static GameMessage parseGameMessage(BinaryWebSocketFrame frame) {
        ByteBuf content = frame.content();
        
        short messageType = content.readShort();
        int messageId = content.readInt();
        int bodyLength = content.readInt();
        byte[] messageBody = new byte[bodyLength];
        content.readBytes(messageBody);
        
        return new GameMessage(messageType, messageId, messageBody);
    }
    
    /**
     * 游戏消息数据结构
     */
    public static class GameMessage {
        private final short messageType;
        private final int messageId;
        private final byte[] messageBody;
        
        public GameMessage(short messageType, int messageId, byte[] messageBody) {
            this.messageType = messageType;
            this.messageId = messageId;
            this.messageBody = messageBody;
        }
        
        // Getters
        public short getMessageType() { return messageType; }
        public int getMessageId() { return messageId; }
        public byte[] getMessageBody() { return messageBody; }
    }
    
    /**
     * 场景 4: 发送图片数据
     * 适用场景：实时图片传输、截图分享、头像上传
     */
    public static void sendImage(ChannelHandlerContext ctx, byte[] imageData, String imageType) {
        // 消息格式：[图片类型长度(1字节)][图片类型][图片数据]
        byte[] typeBytes = imageType.getBytes(StandardCharsets.UTF_8);
        
        ByteBuf buffer = Unpooled.buffer(1 + typeBytes.length + imageData.length);
        buffer.writeByte(typeBytes.length);  // 图片类型长度
        buffer.writeBytes(typeBytes);        // 图片类型（如 "png", "jpg"）
        buffer.writeBytes(imageData);        // 图片数据
        
        BinaryWebSocketFrame frame = new BinaryWebSocketFrame(buffer);
        ctx.writeAndFlush(frame);
    }
    
    /**
     * 场景 5: 发送位置坐标（游戏位置同步）
     * 适用场景：实时位置同步、多人游戏、GPS 追踪
     * 
     * 数据格式：使用固定字节数，避免序列化开销
     */
    public static void sendPosition(ChannelHandlerContext ctx, 
                                    int playerId, 
                                    float x, 
                                    float y, 
                                    float z, 
                                    long timestamp) {
        // 使用 ByteBuffer 确保字节序一致
        ByteBuffer buffer = ByteBuffer.allocate(4 + 4 + 4 + 4 + 8);
        buffer.order(ByteOrder.BIG_ENDIAN);  // 网络字节序
        
        buffer.putInt(playerId);    // 4 字节
        buffer.putFloat(x);          // 4 字节
        buffer.putFloat(y);          // 4 字节
        buffer.putFloat(z);          // 4 字节
        buffer.putLong(timestamp);   // 8 字节
        
        ByteBuf byteBuf = Unpooled.wrappedBuffer(buffer.array());
        BinaryWebSocketFrame frame = new BinaryWebSocketFrame(byteBuf);
        ctx.writeAndFlush(frame);
    }
    
    /**
     * 解析位置坐标
     */
    public static PositionData parsePosition(BinaryWebSocketFrame frame) {
        ByteBuf content = frame.content();
        ByteBuffer buffer = ByteBuffer.wrap(content.array());
        buffer.order(ByteOrder.BIG_ENDIAN);
        
        int playerId = buffer.getInt();
        float x = buffer.getFloat();
        float y = buffer.getFloat();
        float z = buffer.getFloat();
        long timestamp = buffer.getLong();
        
        return new PositionData(playerId, x, y, z, timestamp);
    }
    
    /**
     * 位置数据结构
     */
    public static class PositionData {
        private final int playerId;
        private final float x, y, z;
        private final long timestamp;
        
        public PositionData(int playerId, float x, float y, float z, long timestamp) {
            this.playerId = playerId;
            this.x = x;
            this.y = y;
            this.z = z;
            this.timestamp = timestamp;
        }
        
        // Getters
        public int getPlayerId() { return playerId; }
        public float getX() { return x; }
        public float getY() { return y; }
        public float getZ() { return z; }
        public long getTimestamp() { return timestamp; }
    }
    
    /**
     * 场景 6: 发送 Protobuf 消息（推荐用于游戏）
     * 适用场景：高性能游戏协议、RPC 调用、跨语言通信
     * 
     * Protobuf 优势：
     * - 序列化/反序列化速度快
     * - 数据体积小
     * - 跨语言支持
     * - 向后兼容
     */
    public static void sendProtobufMessage(ChannelHandlerContext ctx, 
                                          com.google.protobuf.Message protobufMessage) {
        // Protobuf 序列化
        byte[] data = protobufMessage.toByteArray();
        
        ByteBuf buffer = Unpooled.wrappedBuffer(data);
        BinaryWebSocketFrame frame = new BinaryWebSocketFrame(buffer);
        ctx.writeAndFlush(frame);
    }
    
    /**
     * 场景 7: 发送压缩数据
     * 适用场景：大量数据传输、带宽优化
     */
    public static void sendCompressedData(ChannelHandlerContext ctx, byte[] originalData) throws Exception {
        // 使用 GZIP 压缩（示例，实际可以使用其他压缩算法）
        java.util.zip.GZIPOutputStream gzipOut = null;
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            gzipOut = new java.util.zip.GZIPOutputStream(baos);
            gzipOut.write(originalData);
            gzipOut.finish();
            
            byte[] compressed = baos.toByteArray();
            
            // 添加压缩标志：1字节标志(1=压缩) + 原始长度(4字节) + 压缩数据
            ByteBuf buffer = Unpooled.buffer(1 + 4 + compressed.length);
            buffer.writeByte(1);  // 压缩标志
            buffer.writeInt(originalData.length);  // 原始数据长度
            buffer.writeBytes(compressed);  // 压缩数据
            
            BinaryWebSocketFrame frame = new BinaryWebSocketFrame(buffer);
            ctx.writeAndFlush(frame);
        } finally {
            if (gzipOut != null) {
                gzipOut.close();
            }
        }
    }
    
    /**
     * 场景 8: 发送文件分块传输
     * 适用场景：大文件传输、断点续传
     * 
     * 消息格式：[文件ID(4字节)][块序号(4字节)][总块数(4字节)][块数据长度(4字节)][块数据]
     */
    public static void sendFileChunk(ChannelHandlerContext ctx,
                                    int fileId,
                                    int chunkIndex,
                                    int totalChunks,
                                    byte[] chunkData) {
        ByteBuf buffer = Unpooled.buffer(4 + 4 + 4 + 4 + chunkData.length);
        buffer.writeInt(fileId);
        buffer.writeInt(chunkIndex);
        buffer.writeInt(totalChunks);
        buffer.writeInt(chunkData.length);
        buffer.writeBytes(chunkData);
        
        BinaryWebSocketFrame frame = new BinaryWebSocketFrame(buffer);
        ctx.writeAndFlush(frame);
    }
    
    /**
     * 场景 9: 发送音频/视频流数据
     * 适用场景：实时音视频传输、语音聊天、视频会议
     */
    public static void sendMediaStream(ChannelHandlerContext ctx,
                                      int streamId,
                                      long timestamp,
                                      byte[] mediaData,
                                      boolean isKeyFrame) {
        // 消息格式：[流ID(4字节)][时间戳(8字节)][关键帧标志(1字节)][数据长度(4字节)][数据]
        ByteBuf buffer = Unpooled.buffer(4 + 8 + 1 + 4 + mediaData.length);
        buffer.writeInt(streamId);
        buffer.writeLong(timestamp);
        buffer.writeByte(isKeyFrame ? 1 : 0);
        buffer.writeInt(mediaData.length);
        buffer.writeBytes(mediaData);
        
        BinaryWebSocketFrame frame = new BinaryWebSocketFrame(buffer);
        ctx.writeAndFlush(frame);
    }
    
    /**
     * 场景 10: 发送加密数据
     * 适用场景：安全通信、敏感数据传输
     */
    public static void sendEncryptedData(ChannelHandlerContext ctx,
                                        byte[] encryptedData,
                                        int encryptionType) {
        // 消息格式：[加密类型(1字节)][数据长度(4字节)][加密数据]
        ByteBuf buffer = Unpooled.buffer(1 + 4 + encryptedData.length);
        buffer.writeByte(encryptionType);
        buffer.writeInt(encryptedData.length);
        buffer.writeBytes(encryptedData);
        
        BinaryWebSocketFrame frame = new BinaryWebSocketFrame(buffer);
        ctx.writeAndFlush(frame);
    }
}

