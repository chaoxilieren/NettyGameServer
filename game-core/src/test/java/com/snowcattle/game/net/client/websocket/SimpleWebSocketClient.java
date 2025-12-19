package com.snowcattle.game.net.client.websocket;

import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.http.DefaultHttpHeaders;
import io.netty.handler.codec.http.HttpClientCodec;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.websocketx.*;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URI;
import java.io.ByteArrayOutputStream;
import java.io.ObjectOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * 简单的 WebSocket 客户端示例
 * 
 * 功能：
 * 1. 连接到 WebSocket 服务器
 * 2. 发送文本消息
 * 3. 发送二进制消息
 * 4. 支持 Ping/Pong 心跳
 * 5. 接收服务器消息
 * 
 * 使用方法：
 * 1. 先启动 SimpleWebSocketServer
 * 2. 运行本类的 main 方法
 * 3. 在控制台输入消息，按回车发送
 * 4. 输入 "bye" 退出
 * 5. 输入 "ping" 发送心跳
 */
public class SimpleWebSocketClient {
    
    /** WebSocket 服务器地址 */
    private static final String WS_URL = "ws://127.0.0.1:9001/websocket";
    
    public static void main(String[] args) throws Exception {
        URI uri = new URI(WS_URL);
        String scheme = uri.getScheme() == null ? "ws" : uri.getScheme();
        final String host = uri.getHost() == null ? "127.0.0.1" : uri.getHost();
        final int port;
        
        if (uri.getPort() == -1) {
            if ("ws".equalsIgnoreCase(scheme)) {
                port = 80;
            } else if ("wss".equalsIgnoreCase(scheme)) {
                port = 443;
            } else {
                port = -1;
            }
        } else {
            port = uri.getPort();
        }
        
        if (!"ws".equalsIgnoreCase(scheme) && !"wss".equalsIgnoreCase(scheme)) {
            System.err.println("只支持 WS 或 WSS 协议");
            return;
        }
        
        EventLoopGroup group = new NioEventLoopGroup();
        try {
            // 创建 WebSocket 握手器
            final SimpleWebSocketClientHandler handler = new SimpleWebSocketClientHandler(
                    WebSocketClientHandshakerFactory.newHandshaker(
                            uri, WebSocketVersion.V13, null, true, new DefaultHttpHeaders()));
            
            // 创建客户端启动器
            Bootstrap bootstrap = new Bootstrap();
            bootstrap.group(group)
                    .channel(NioSocketChannel.class)
                    .handler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel ch) throws Exception {
                            ChannelPipeline pipeline = ch.pipeline();
                            
                            // 1. HTTP 编解码器（用于 WebSocket 握手）
                            pipeline.addLast(new HttpClientCodec());
                            
                            // 2. HTTP 消息聚合器
                            pipeline.addLast(new HttpObjectAggregator(8192));
                            
                            // 3. WebSocket 客户端处理器
                            pipeline.addLast(handler);
                        }
                    });
            
            // 连接到服务器
            System.out.println("正在连接到 WebSocket 服务器: " + WS_URL);
            Channel channel = bootstrap.connect(host, port).sync().channel();
            
            // 等待握手完成
            handler.handshakeFuture().sync();
            System.out.println("WebSocket 连接已建立！");
            System.out.println("========================================");
            System.out.println("使用说明：");
            System.out.println("  - 输入消息并按回车发送（文本消息）");
            System.out.println("  - 输入 'ping' 发送心跳");
            System.out.println("  - 输入 'time' 请求服务器时间");
            System.out.println("  - 输入 'echo:xxx' 测试回显");
            System.out.println("  - 输入 'binary' 发送 Java 对象（二进制序列化）");
            System.out.println("  - 输入 'position' 发送位置数据（二进制格式）");
            System.out.println("  - 输入 'compare' 对比两种序列化方式的效率和大小");
            System.out.println("  - 输入 'bye' 退出");
            System.out.println("========================================");
            
            // 从控制台读取输入
            BufferedReader console = new BufferedReader(new InputStreamReader(System.in));
            while (true) {
                String input = console.readLine();
                if (input == null) {
                    break;
                }
                
                if ("bye".equalsIgnoreCase(input)) {
                    // 发送关闭帧
                    channel.writeAndFlush(new CloseWebSocketFrame());
                    channel.closeFuture().sync();
                    break;
                } else if ("ping".equalsIgnoreCase(input)) {
                    // 发送 Ping 帧
                    WebSocketFrame frame = new PingWebSocketFrame(Unpooled.wrappedBuffer(new byte[]{8, 1, 8, 1}));
                    channel.writeAndFlush(frame);
                    System.out.println("已发送 Ping");
                } else if ("binary".equalsIgnoreCase(input)) {
                    // 发送 Java 对象（二进制序列化）
                    sendJavaObject(channel);
                } else if ("position".equalsIgnoreCase(input)) {
                    // 发送位置数据（二进制格式示例）
                    sendPositionData(channel);
                } else if ("compare".equalsIgnoreCase(input)) {
                    // 对比两种序列化方式
                    compareSerializationMethods(channel);
                } else {
                    // 发送文本消息
                    WebSocketFrame frame = new TextWebSocketFrame(input);
                    channel.writeAndFlush(frame);
                }
            }
            
        } finally {
            group.shutdownGracefully();
        }
    }
    
    /**
     * 发送 Java 对象示例（二进制序列化）
     * 演示如何使用 BinaryWebSocketFrame 发送序列化的 Java 对象
     */
    private static void sendJavaObject(Channel channel) {
        try {
            // 创建一个示例对象
            UserInfo userInfo = new UserInfo();
            userInfo.setUserId(12345);
            userInfo.setUsername("测试用户");
            userInfo.setLevel(50);
            userInfo.setScore(9999);
            userInfo.setOnline(true);
            
            // 序列化对象为字节数组
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ObjectOutputStream oos = new ObjectOutputStream(baos);
            oos.writeObject(userInfo);
            oos.close();
            byte[] bytes = baos.toByteArray();
            
            // 发送二进制帧
            ByteBuf buffer = Unpooled.wrappedBuffer(bytes);
            BinaryWebSocketFrame frame = new BinaryWebSocketFrame(buffer);
            channel.writeAndFlush(frame);
            
            System.out.println("已发送 Java 对象（二进制序列化）:");
            System.out.println("  用户ID: " + userInfo.getUserId());
            System.out.println("  用户名: " + userInfo.getUsername());
            System.out.println("  等级: " + userInfo.getLevel());
            System.out.println("  分数: " + userInfo.getScore());
            System.out.println("  在线状态: " + userInfo.isOnline());
            System.out.println("  序列化后大小: " + bytes.length + " 字节");
        } catch (Exception e) {
            System.err.println("发送 Java 对象失败: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * 发送位置数据示例（二进制格式）
     * 演示如何使用 BinaryWebSocketFrame 发送结构化数据
     */
    private static void sendPositionData(Channel channel) {
        // 模拟位置数据
        int playerId = 12345;
        float x = 100.5f;
        float y = 200.3f;
        float z = 50.1f;
        long timestamp = System.currentTimeMillis();
        
        // 使用 ByteBuffer 确保字节序一致
        ByteBuffer buffer = ByteBuffer.allocate(4 + 4 + 4 + 4 + 8);
        buffer.order(ByteOrder.BIG_ENDIAN);  // 网络字节序
        
        buffer.putInt(playerId);
        buffer.putFloat(x);
        buffer.putFloat(y);
        buffer.putFloat(z);
        buffer.putLong(timestamp);
        
        // 创建 BinaryWebSocketFrame
        BinaryWebSocketFrame frame = new BinaryWebSocketFrame(Unpooled.wrappedBuffer(buffer.array()));
        channel.writeAndFlush(frame);
        
        System.out.println("已发送位置数据（二进制格式）:");
        System.out.println("  PlayerId: " + playerId);
        System.out.println("  位置: (" + x + ", " + y + ", " + z + ")");
        System.out.println("  时间戳: " + timestamp);
        System.out.println("  数据大小: " + buffer.array().length + " 字节");
    }
    
    /**
     * 对比两种序列化方式（发送相同内容）
     * 演示 Java 对象序列化和二进制格式的效率和大小差异
     */
    private static void compareSerializationMethods(Channel channel) {
        System.out.println("========================================");
        System.out.println("序列化方式对比测试");
        System.out.println("========================================");
        
        // 准备相同的数据
        int playerId = 12345;
        float x = 100.5f;
        float y = 200.3f;
        float z = 50.1f;
        long timestamp = System.currentTimeMillis();
        
        try {
            // 方式1：Java 对象序列化
            PositionObject positionObj = new PositionObject(playerId, x, y, z, timestamp);
            
            long startTime = System.nanoTime();
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ObjectOutputStream oos = new ObjectOutputStream(baos);
            oos.writeObject(positionObj);
            oos.close();
            byte[] javaBytes = baos.toByteArray();
            long javaSerializeTime = System.nanoTime() - startTime;
            
            // 方式2：二进制格式
            startTime = System.nanoTime();
            ByteBuffer buffer = ByteBuffer.allocate(4 + 4 + 4 + 4 + 8);
            buffer.order(ByteOrder.BIG_ENDIAN);
            buffer.putInt(playerId);
            buffer.putFloat(x);
            buffer.putFloat(y);
            buffer.putFloat(z);
            buffer.putLong(timestamp);
            byte[] binaryBytes = buffer.array();
            long binarySerializeTime = System.nanoTime() - startTime;
            
            // 输出对比结果
            System.out.println();
            System.out.println("数据大小对比:");
            System.out.println("  Java 对象序列化: " + javaBytes.length + " 字节");
            System.out.println("  二进制格式: " + binaryBytes.length + " 字节");
            System.out.println("  大小差异: " + (javaBytes.length - binaryBytes.length) + " 字节");
            System.out.println("  二进制节省: " + String.format("%.1f", 
                (1.0 - (double)binaryBytes.length / javaBytes.length) * 100) + "%");
            
            System.out.println();
            System.out.println("序列化速度对比:");
            System.out.println("  Java 对象序列化: " + (javaSerializeTime / 1000.0) + " 微秒");
            System.out.println("  二进制格式: " + (binarySerializeTime / 1000.0) + " 微秒");
            if (javaSerializeTime > 0) {
                double speedup = (double)javaSerializeTime / binarySerializeTime;
                System.out.println("  二进制格式快: " + String.format("%.1f", speedup) + " 倍");
            }
            
            System.out.println();
            System.out.println("========================================");
            System.out.println("结论:");
            System.out.println("========================================");
            System.out.println("1. 数据大小: 二进制格式 < Java 序列化");
            System.out.println("   - 二进制格式: " + binaryBytes.length + " 字节（固定）");
            System.out.println("   - Java 序列化: " + javaBytes.length + " 字节（包含类信息等）");
            System.out.println();
            System.out.println("2. 序列化速度: 二进制格式 > Java 序列化");
            System.out.println("   - 二进制格式: 直接内存操作，速度快");
            System.out.println("   - Java 序列化: 需要写入类信息等，速度慢");
            System.out.println();
            System.out.println("3. 适用场景:");
            System.out.println("   - 二进制格式: 高频数据传输、性能敏感场景（推荐）");
            System.out.println("   - Java 序列化: 低频数据、复杂对象结构");
            System.out.println("========================================");
            
            // 发送两种格式的数据到服务器进行对比
            System.out.println();
            System.out.println("发送 Java 序列化数据到服务器...");
            ByteBuf javaBuffer = Unpooled.wrappedBuffer(javaBytes);
            channel.writeAndFlush(new BinaryWebSocketFrame(javaBuffer));
            
            Thread.sleep(500);  // 等待一下
            
            System.out.println("发送二进制格式数据到服务器...");
            ByteBuf binaryBuffer = Unpooled.wrappedBuffer(binaryBytes);
            channel.writeAndFlush(new BinaryWebSocketFrame(binaryBuffer));
            
        } catch (Exception e) {
            System.err.println("对比测试失败: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * 位置对象类（用于 Java 序列化对比）
     */
    public static class PositionObject implements java.io.Serializable {
        private static final long serialVersionUID = 1L;
        
        private int playerId;
        private float x;
        private float y;
        private float z;
        private long timestamp;
        
        public PositionObject() {}
        
        public PositionObject(int playerId, float x, float y, float z, long timestamp) {
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
        
        @Override
        public String toString() {
            return "PositionObject{playerId=" + playerId + ", x=" + x + ", y=" + y + ", z=" + z + ", timestamp=" + timestamp + "}";
        }
    }
    
    /**
     * 示例用户信息类（用于序列化测试）
     * 注意：必须实现 Serializable 接口才能序列化
     */
    public static class UserInfo implements java.io.Serializable {
        private static final long serialVersionUID = 1L;
        
        private int userId;
        private String username;
        private int level;
        private int score;
        private boolean online;
        
        // Getters and Setters
        public int getUserId() { return userId; }
        public void setUserId(int userId) { this.userId = userId; }
        
        public String getUsername() { return username; }
        public void setUsername(String username) { this.username = username; }
        
        public int getLevel() { return level; }
        public void setLevel(int level) { this.level = level; }
        
        public int getScore() { return score; }
        public void setScore(int score) { this.score = score; }
        
        public boolean isOnline() { return online; }
        public void setOnline(boolean online) { this.online = online; }
        
        @Override
        public String toString() {
            return "UserInfo{" +
                    "userId=" + userId +
                    ", username='" + username + '\'' +
                    ", level=" + level +
                    ", score=" + score +
                    ", online=" + online +
                    '}';
        }
    }
}

