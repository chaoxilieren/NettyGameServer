package com.snowcattle.game.common.codec.lengthfield;

/**
 * Netty 日志解析示例
 * 
 * 演示如何阅读十六进制转储日志
 */
public class 日志解析示例 {
    
    /**
     * 示例日志：
     * 
     * +-------------------------------------------------+
     * |  0  1  2  3  4  5  6  7  8  9  a  b  c  d  e  f |
     * +--------+-------------------------------------------------+----------------+
     * |00000000| 00 00 00 16 e6 9c 8d e5 8a a1 e5 99 a8 e5 9b 9e |................|
     * |00000010| e5 a4 8d 3a 20 48 65 6c 6c 6f                   |...: Hello      |
     * +--------+-------------------------------------------------+----------------+
     */
    public static void 解析示例日志() {
        System.out.println("========================================");
        System.out.println("日志解析示例");
        System.out.println("========================================");
        
        // 第一行数据（偏移 0x00000000）
        System.out.println("\n【第一行 - 偏移 0x00000000】");
        System.out.println("00 00 00 16  ← 长度字段（4字节）");
        System.out.println("   十六进制: 00 00 00 16");
        System.out.println("   十进制: " + 0x16);
        System.out.println("   含义: 后面有 22 字节的数据");
        
        System.out.println("\ne6 9c 8d  ← UTF-8 编码的'服'字（3字节）");
        System.out.println("e5 8a a1  ← UTF-8 编码的'务'字（3字节）");
        System.out.println("e5 99 a8  ← UTF-8 编码的'器'字（3字节）");
        System.out.println("e5 9b 9e  ← UTF-8 编码的'回'字（3字节）");
        
        // 第二行数据（偏移 0x00000010 = 16）
        System.out.println("\n【第二行 - 偏移 0x00000010 (16字节)】");
        System.out.println("e5 a4 8d  ← UTF-8 编码的'复'字（3字节）");
        System.out.println("3a        ← ASCII ':' (冒号)");
        System.out.println("20        ← ASCII ' ' (空格)");
        System.out.println("48        ← ASCII 'H'");
        System.out.println("65        ← ASCII 'e'");
        System.out.println("6c        ← ASCII 'l'");
        System.out.println("6c        ← ASCII 'l'");
        System.out.println("6f        ← ASCII 'o'");
        
        // 完整内容
        System.out.println("\n【完整数据内容】");
        System.out.println("长度字段: 00 00 00 16 (22 字节)");
        System.out.println("数据内容: '服务器回复: Hello'");
        System.out.println("   - '服务器回复' = 5 个汉字 × 3 字节 = 15 字节");
        System.out.println("   - ':' = 1 字节");
        System.out.println("   - ' ' = 1 字节");
        System.out.println("   - 'Hello' = 5 字节");
        System.out.println("   - 总计: 15 + 1 + 1 + 5 = 22 字节 ✓");
        
        // ASCII 部分说明
        System.out.println("\n【右侧 ASCII 部分说明】");
        System.out.println("|................|  ← 前 16 个字节");
        System.out.println("   - '.' 表示不可打印的 ASCII 字符");
        System.out.println("   - 中文字符在 UTF-8 中占 3 字节，不是单字节 ASCII");
        System.out.println("   - 所以显示为 '.'");
        System.out.println("\n|...: Hello      |  ← 后 10 个字节");
        System.out.println("   - ':' 和 'Hello' 是可打印的 ASCII 字符");
        System.out.println("   - 所以直接显示");
    }
    
    /**
     * 十六进制转十进制示例
     */
    public static void 十六进制转换示例() {
        System.out.println("\n========================================");
        System.out.println("十六进制转十进制示例");
        System.out.println("========================================");
        
        // 单个字节
        System.out.println("\n【单个字节】");
        System.out.println("0x00 = " + 0x00 + " (十进制)");
        System.out.println("0x16 = " + 0x16 + " (十进制)");
        System.out.println("0x20 = " + 0x20 + " (空格字符)");
        System.out.println("0x48 = " + 0x48 + " (字符 'H')");
        System.out.println("0x65 = " + 0x65 + " (字符 'e')");
        
        // 多字节（大端序）
        System.out.println("\n【多字节（大端序）】");
        System.out.println("0x00 0x00 0x00 0x16 = " + 
            (0x00 << 24 | 0x00 << 16 | 0x00 << 8 | 0x16) + " (十进制)");
        
        // 使用 Java 方法
        byte[] lengthBytes = {0x00, 0x00, 0x00, 0x16};
        int length = (lengthBytes[0] << 24) | 
                    ((lengthBytes[1] & 0xFF) << 16) | 
                    ((lengthBytes[2] & 0xFF) << 8) | 
                    (lengthBytes[3] & 0xFF);
        System.out.println("Java 计算: " + length);
    }
    
    /**
     * ASCII 字符对照
     */
    public static void ASCII字符对照() {
        System.out.println("\n========================================");
        System.out.println("常见 ASCII 字符对照");
        System.out.println("========================================");
        
        System.out.println("\n【可打印字符】");
        System.out.println("0x20 (32)  = ' ' (空格)");
        System.out.println("0x30 (48)  = '0'");
        System.out.println("0x39 (57)  = '9'");
        System.out.println("0x41 (65)  = 'A'");
        System.out.println("0x5A (90)  = 'Z'");
        System.out.println("0x61 (97)  = 'a'");
        System.out.println("0x7A (122) = 'z'");
        System.out.println("0x3A (58)  = ':' (冒号)");
        System.out.println("0x2C (44)  = ',' (逗号)");
        
        System.out.println("\n【控制字符】");
        System.out.println("0x00 (0)   = \\0 (空字符)");
        System.out.println("0x0A (10)  = \\n (换行)");
        System.out.println("0x0D (13)  = \\r (回车)");
        System.out.println("0x09 (9)   = \\t (制表符)");
    }
    
    public static void main(String[] args) {
        解析示例日志();
        十六进制转换示例();
        ASCII字符对照();
    }
}


