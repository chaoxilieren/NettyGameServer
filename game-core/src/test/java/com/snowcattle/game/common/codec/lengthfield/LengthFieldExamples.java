package com.snowcattle.game.common.codec.lengthfield;

import io.netty.handler.codec.LengthFieldBasedFrameDecoder;

/**
 * LengthFieldBasedFrameDecoder 各种场景示例
 * 
 * 展示不同协议格式下的参数配置
 */
public class LengthFieldExamples {
    
    /**
     * 示例 1: 最简单的协议
     * 协议格式: [长度(4字节)][数据(N字节)]
     * 
     * 数据示例:
     *   长度字段: 00 00 00 05
     *   数据: Hello
     */
    public static LengthFieldBasedFrameDecoder example1_Simple() {
        return new LengthFieldBasedFrameDecoder(
            1024,  // 最大帧长度: 1KB
            0,     // 长度字段偏移: 0（长度字段在开头）
            4,     // 长度字段长度: 4 字节（int 类型）
            0,     // 长度调整: 0（长度值就是数据长度）
            4      // 初始跳过: 4 字节（删除长度字段，只保留数据）
        );
    }
    
    /**
     * 示例 2: 带版本号的协议
     * 协议格式: [版本(2字节)][长度(4字节)][数据(N字节)]
     * 
     * 数据示例:
     *   版本: 01 00
     *   长度: 00 00 00 05
     *   数据: Hello
     */
    public static LengthFieldBasedFrameDecoder example2_WithVersion() {
        return new LengthFieldBasedFrameDecoder(
            1024,  // 最大帧长度
            2,     // 长度字段偏移: 2（跳过版本号 2 字节）
            4,     // 长度字段长度: 4 字节
            0,     // 长度调整: 0
            6      // 初始跳过: 6 字节（删除版本+长度，只保留数据）
        );
    }
    
    /**
     * 示例 3: 长度字段包含自身
     * 协议格式: [长度(4字节，值=4+N)][数据(N字节)]
     * 
     * 数据示例:
     *   长度: 00 00 00 09（9 = 4 + 5）
     *   数据: Hello（5字节）
     */
    public static LengthFieldBasedFrameDecoder example3_LengthIncludesSelf() {
        return new LengthFieldBasedFrameDecoder(
            1024,  // 最大帧长度
            0,     // 长度字段偏移: 0
            4,     // 长度字段长度: 4 字节
            -4,    // 长度调整: -4（减去长度字段本身）
            4      // 初始跳过: 4 字节（删除长度字段）
        );
    }
    
    /**
     * 示例 4: 带尾部校验和
     * 协议格式: [长度(4字节)][数据(N字节)][校验和(2字节)]
     * 
     * 数据示例:
     *   长度: 00 00 00 05（只包含数据长度）
     *   数据: Hello
     *   校验: AB CD
     */
    public static LengthFieldBasedFrameDecoder example4_WithChecksum() {
        return new LengthFieldBasedFrameDecoder(
            1024,  // 最大帧长度
            0,     // 长度字段偏移: 0
            4,     // 长度字段长度: 4 字节
            2,     // 长度调整: +2（需要多读 2 字节的校验和）
            4      // 初始跳过: 4 字节（删除长度字段，保留数据和校验和）
        );
    }
    
    /**
     * 示例 5: 复杂协议（魔数+版本+长度+命令+数据）
     * 协议格式: [魔数(2)][版本(1)][长度(4)][命令(2)][数据(N)]
     * 
     * 数据示例:
     *   魔数: AB CD
     *   版本: 01
     *   长度: 00 00 00 05
     *   命令: 00 01
     *   数据: Hello
     */
    public static LengthFieldBasedFrameDecoder example5_Complex() {
        return new LengthFieldBasedFrameDecoder(
            65535, // 最大帧长度: 64KB
            3,     // 长度字段偏移: 3（跳过魔数2+版本1）
            4,     // 长度字段长度: 4 字节
            2,     // 长度调整: +2（需要多读 2 字节的命令字段）
            7      // 初始跳过: 7 字节（删除魔数+版本+长度，保留命令+数据）
        );
    }
    
    /**
     * 示例 6: 长度字段在数据后面（不常见，但支持）
     * 协议格式: [数据(N字节)][长度(4字节)]
     * 
     * 注意：这种场景下，需要先读取数据，再读取长度，通常不推荐
     * 这里仅作为示例展示
     */
    public static LengthFieldBasedFrameDecoder example6_LengthAtEnd() {
        // 这种情况下，需要先知道数据的大概范围
        // 或者使用其他解码策略
        // 这里仅作演示，实际不推荐
        return new LengthFieldBasedFrameDecoder(
            1024,
            0,     // 实际上长度字段不在开头，需要特殊处理
            4,
            0,
            0      // 不删除，保留所有数据
        );
    }
    
    /**
     * 参数记忆口诀：
     * 
     * Offset: 从哪里开始找长度字段（跳过前面的字节）
     * Length: 长度字段本身占几个字节（1/2/4/8）
     * Adjustment: 长度值需要怎么调整（+/- 多少字节）
     * Strip: 结果中要删除前面的多少字节（只保留需要的部分）
     */
}

