package com.snowcattle.game.net.client.websocket;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * 序列化方式对比：Java 对象序列化 vs 二进制格式
 * 
 * 本类演示发送相同内容时，两种方式的效率和数据大小差异
 */
public class SerializationComparison {
    
    /**
     * 位置数据类（用于 Java 序列化）
     */
    public static class PositionObject implements Serializable {
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
        
        // Setters
        public void setPlayerId(int playerId) { this.playerId = playerId; }
        public void setX(float x) { this.x = x; }
        public void setY(float y) { this.y = y; }
        public void setZ(float z) { this.z = z; }
        public void setTimestamp(long timestamp) { this.timestamp = timestamp; }
    }
    
    /**
     * 方式1：Java 对象序列化
     * 使用 ObjectOutputStream 序列化
     */
    public static byte[] serializeWithJavaObject(PositionObject position) throws Exception {
        long startTime = System.nanoTime();
        
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ObjectOutputStream oos = new ObjectOutputStream(baos);
        oos.writeObject(position);
        oos.close();
        byte[] bytes = baos.toByteArray();
        
        long endTime = System.nanoTime();
        long duration = endTime - startTime;
        
        System.out.println("=== Java 对象序列化 ===");
        System.out.println("  数据大小: " + bytes.length + " 字节");
        System.out.println("  序列化耗时: " + (duration / 1000.0) + " 微秒");
        
        return bytes;
    }
    
    /**
     * 方式2：二进制格式
     * 直接写入原始数据类型
     */
    public static byte[] serializeWithBinary(int playerId, float x, float y, float z, long timestamp) {
        long startTime = System.nanoTime();
        
        ByteBuffer buffer = ByteBuffer.allocate(4 + 4 + 4 + 4 + 8);
        buffer.order(ByteOrder.BIG_ENDIAN);
        
        buffer.putInt(playerId);
        buffer.putFloat(x);
        buffer.putFloat(y);
        buffer.putFloat(z);
        buffer.putLong(timestamp);
        
        byte[] bytes = buffer.array();
        
        long endTime = System.nanoTime();
        long duration = endTime - startTime;
        
        System.out.println("=== 二进制格式 ===");
        System.out.println("  数据大小: " + bytes.length + " 字节");
        System.out.println("  序列化耗时: " + (duration / 1000.0) + " 微秒");
        
        return bytes;
    }
    
    /**
     * 反序列化：Java 对象
     */
    public static PositionObject deserializeJavaObject(byte[] bytes) throws Exception {
        long startTime = System.nanoTime();
        
        ByteArrayInputStream bais = new ByteArrayInputStream(bytes);
        ObjectInputStream ois = new ObjectInputStream(bais);
        PositionObject position = (PositionObject) ois.readObject();
        
        long endTime = System.nanoTime();
        long duration = endTime - startTime;
        
        System.out.println("  Java 反序列化耗时: " + (duration / 1000.0) + " 微秒");
        
        return position;
    }
    
    /**
     * 反序列化：二进制格式
     */
    public static PositionData deserializeBinary(byte[] bytes) {
        long startTime = System.nanoTime();
        
        ByteBuffer buffer = ByteBuffer.wrap(bytes);
        buffer.order(ByteOrder.BIG_ENDIAN);
        
        int playerId = buffer.getInt();
        float x = buffer.getFloat();
        float y = buffer.getFloat();
        float z = buffer.getFloat();
        long timestamp = buffer.getLong();
        
        PositionData position = new PositionData(playerId, x, y, z, timestamp);
        
        long endTime = System.nanoTime();
        long duration = endTime - startTime;
        
        System.out.println("  二进制反序列化耗时: " + (duration / 1000.0) + " 微秒");
        
        return position;
    }
    
    /**
     * 位置数据（用于二进制格式）
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
        
        public int getPlayerId() { return playerId; }
        public float getX() { return x; }
        public float getY() { return y; }
        public float getZ() { return z; }
        public long getTimestamp() { return timestamp; }
    }
    
    /**
     * 性能对比测试
     */
    public static void main(String[] args) throws Exception {
        System.out.println("========================================");
        System.out.println("序列化方式对比测试");
        System.out.println("========================================");
        System.out.println();
        
        // 准备测试数据
        int playerId = 12345;
        float x = 100.5f;
        float y = 200.3f;
        float z = 50.1f;
        long timestamp = System.currentTimeMillis();
        
        PositionObject positionObj = new PositionObject(playerId, x, y, z, timestamp);
        
        System.out.println("测试数据:");
        System.out.println("  PlayerId: " + playerId);
        System.out.println("  位置: (" + x + ", " + y + ", " + z + ")");
        System.out.println("  时间戳: " + timestamp);
        System.out.println();
        
        // 测试 Java 对象序列化
        byte[] javaBytes = serializeWithJavaObject(positionObj);
        System.out.println();
        
        // 测试二进制格式
        byte[] binaryBytes = serializeWithBinary(playerId, x, y, z, timestamp);
        System.out.println();
        
        // 对比结果
        System.out.println("========================================");
        System.out.println("对比结果:");
        System.out.println("========================================");
        System.out.println("数据大小对比:");
        System.out.println("  Java 序列化: " + javaBytes.length + " 字节");
        System.out.println("  二进制格式: " + binaryBytes.length + " 字节");
        System.out.println("  大小差异: " + (javaBytes.length - binaryBytes.length) + " 字节");
        System.out.println("  二进制节省: " + String.format("%.1f", 
            (1.0 - (double)binaryBytes.length / javaBytes.length) * 100) + "%");
        System.out.println();
        
        // 性能测试（多次测试取平均值）
        System.out.println("性能测试（10000 次）:");
        int testCount = 10000;
        
        // Java 序列化性能
        long javaSerializeTime = 0;
        long javaDeserializeTime = 0;
        for (int i = 0; i < testCount; i++) {
            long start = System.nanoTime();
            byte[] bytes = serializeWithJavaObject(positionObj);
            javaSerializeTime += (System.nanoTime() - start);
            
            start = System.nanoTime();
            deserializeJavaObject(bytes);
            javaDeserializeTime += (System.nanoTime() - start);
        }
        
        // 二进制格式性能
        long binarySerializeTime = 0;
        long binaryDeserializeTime = 0;
        for (int i = 0; i < testCount; i++) {
            long start = System.nanoTime();
            byte[] bytes = serializeWithBinary(playerId, x, y, z, timestamp);
            binarySerializeTime += (System.nanoTime() - start);
            
            start = System.nanoTime();
            deserializeBinary(bytes);
            binaryDeserializeTime += (System.nanoTime() - start);
        }
        
        System.out.println("Java 序列化:");
        System.out.println("  平均序列化耗时: " + (javaSerializeTime / testCount / 1000.0) + " 微秒");
        System.out.println("  平均反序列化耗时: " + (javaDeserializeTime / testCount / 1000.0) + " 微秒");
        System.out.println("  总耗时: " + ((javaSerializeTime + javaDeserializeTime) / testCount / 1000.0) + " 微秒");
        System.out.println();
        
        System.out.println("二进制格式:");
        System.out.println("  平均序列化耗时: " + (binarySerializeTime / testCount / 1000.0) + " 微秒");
        System.out.println("  平均反序列化耗时: " + (binaryDeserializeTime / testCount / 1000.0) + " 微秒");
        System.out.println("  总耗时: " + ((binarySerializeTime + binaryDeserializeTime) / testCount / 1000.0) + " 微秒");
        System.out.println();
        
        // 性能提升
        double javaTotal = (javaSerializeTime + javaDeserializeTime) / 1000.0;
        double binaryTotal = (binarySerializeTime + binaryDeserializeTime) / 1000.0;
        double speedup = javaTotal / binaryTotal;
        
        System.out.println("性能提升:");
        System.out.println("  二进制格式比 Java 序列化快: " + String.format("%.1f", speedup) + " 倍");
        System.out.println();
        
        System.out.println("========================================");
        System.out.println("结论:");
        System.out.println("========================================");
        System.out.println("1. 数据大小: 二进制格式 < Java 序列化");
        System.out.println("   - 二进制格式: " + binaryBytes.length + " 字节（固定）");
        System.out.println("   - Java 序列化: " + javaBytes.length + " 字节（包含类信息、字段名等）");
        System.out.println();
        System.out.println("2. 序列化速度: 二进制格式 > Java 序列化");
        System.out.println("   - 二进制格式: 直接内存操作，速度快");
        System.out.println("   - Java 序列化: 需要写入类信息、字段描述等，速度慢");
        System.out.println();
        System.out.println("3. 反序列化速度: 二进制格式 > Java 序列化");
        System.out.println("   - 二进制格式: 直接按字节偏移读取，速度快");
        System.out.println("   - Java 序列化: 需要解析类信息、创建对象等，速度慢");
        System.out.println();
        System.out.println("4. 适用场景:");
        System.out.println("   - 二进制格式: 游戏位置同步、高频数据传输、性能敏感场景");
        System.out.println("   - Java 序列化: 复杂对象、需要跨 JVM 传输、不追求极致性能");
    }
}

