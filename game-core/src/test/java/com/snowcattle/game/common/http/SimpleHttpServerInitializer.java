package com.snowcattle.game.common.http;

import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpRequestDecoder;
import io.netty.handler.codec.http.HttpResponseEncoder;

/**
 * HTTP 服务器 Channel 初始化器
 * 
 * 作用：配置每个客户端连接的 Pipeline（处理链）
 * 
 * Pipeline 顺序说明（从网络到业务）：
 * 1. HttpRequestDecoder: 将接收到的字节流解码为 HTTP 请求对象
 * 2. HttpObjectAggregator: 将 HTTP 请求的多个部分（请求头、请求体）聚合成完整的 FullHttpRequest
 * 3. HttpResponseEncoder: 将 HTTP 响应对象编码为字节流发送给客户端
 * 4. SimpleHttpServerHandler: 业务处理器，处理具体的 HTTP 请求
 */
public class SimpleHttpServerInitializer extends ChannelInitializer<SocketChannel> {
    
    @Override
    protected void initChannel(SocketChannel ch) throws Exception {
        ChannelPipeline pipeline = ch.pipeline();
        
        // 1. HTTP 请求解码器：将字节流解码为 HTTP 请求对象
        // 参数 4096 表示初始缓冲区大小，8192 表示最大缓冲区大小
        pipeline.addLast("decoder", new HttpRequestDecoder(4096, 8192, 8192));
        
        // 2. HTTP 对象聚合器：将 HTTP 请求的多个部分聚合成完整的 FullHttpRequest
        // 参数 1048576 表示最大聚合内容长度（1MB）
        // 如果不添加这个，需要自己处理分块的 HTTP 请求（比较复杂）
        pipeline.addLast("aggregator", new HttpObjectAggregator(1048576));
        
        // 3. HTTP 响应编码器：将 HTTP 响应对象编码为字节流
        pipeline.addLast("encoder", new HttpResponseEncoder());
        
        // 4. 业务处理器：处理具体的 HTTP 请求并返回响应
        pipeline.addLast("handler", new SimpleHttpServerHandler());
    }
}

