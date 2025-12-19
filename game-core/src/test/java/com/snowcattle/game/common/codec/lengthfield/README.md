# LengthFieldBasedFrameDecoder 使用指南

## 快速开始

### 1. 运行示例

**启动服务器：**
```bash
运行 LengthFieldServer.main()
```

**启动客户端：**
```bash
运行 LengthFieldClient.main()
```

### 2. 查看详细讲解

阅读 `LengthFieldBasedFrameDecoder讲解.md` 文件，包含：
- 通俗易懂的概念解释
- 每个参数的详细说明
- 多个实际场景示例
- 常见问题解答

### 3. 参考代码示例

查看 `LengthFieldExamples.java`，包含 6 种常见协议格式的配置示例。

---

## 核心参数速查表

| 参数 | 含义 | 常见值 |
|------|------|--------|
| maxFrameLength | 最大帧长度 | 1024, 65535 |
| lengthFieldOffset | 长度字段偏移量 | 0, 2, 4 |
| lengthFieldLength | 长度字段长度 | 1, 2, 4, 8 |
| lengthAdjustment | 长度调整值 | -4, 0, 2 |
| initialBytesToStrip | 初始跳过字节数 | 0, 4, 6 |

---

## 常见协议格式

### 格式 1: [长度][数据]
```java
new LengthFieldBasedFrameDecoder(1024, 0, 4, 0, 4)
```

### 格式 2: [版本][长度][数据]
```java
new LengthFieldBasedFrameDecoder(1024, 2, 4, 0, 6)
```

### 格式 3: [长度(含自身)][数据]
```java
new LengthFieldBasedFrameDecoder(1024, 0, 4, -4, 4)
```

### 格式 4: [长度][数据][校验]
```java
new LengthFieldBasedFrameDecoder(1024, 0, 4, 2, 4)
```

---

## 调试技巧

1. **添加日志处理器：**
```java
pipeline.addLast("logger", new LoggingHandler(LogLevel.DEBUG));
```

2. **打印原始字节：**
```java
byte[] bytes = ...;
System.out.println("原始数据: " + Arrays.toString(bytes));
```

3. **逐步测试：**
- 先测试最简单的协议格式
- 逐步添加其他字段
- 验证每个参数的效果

---

## 注意事项

1. **字节序：** 默认使用大端序（Big-Endian），如果需要小端序，需要使用 `ByteOrder.LITTLE_ENDIAN`

2. **长度字段范围：** 
   - 1 字节：0-255
   - 2 字节：0-65535
   - 4 字节：0-2^32-1

3. **内存安全：** 设置合理的 `maxFrameLength` 防止内存溢出

4. **粘包拆包：** LengthFieldBasedFrameDecoder 自动处理 TCP 粘包拆包问题

---

## 相关资源

- [Netty 官方文档](https://netty.io/4.1/api/io/netty/handler/codec/LengthFieldBasedFrameDecoder.html)
- [详细讲解文档](./LengthFieldBasedFrameDecoder讲解.md)
- [代码示例](./LengthFieldExamples.java)

