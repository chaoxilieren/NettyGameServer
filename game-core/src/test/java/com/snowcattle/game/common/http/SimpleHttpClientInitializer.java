package com.snowcattle.game.common.http;

import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.http.HttpClientCodec;
import io.netty.handler.codec.http.HttpObjectAggregator;

/**
 * HTTP 客户端 Channel 初始化器
 * 
 * 作用：配置客户端连接的 Pipeline（处理链）
 * 
 * Pipeline 顺序说明：
 * 1. HttpClientCodec: HTTP 客户端编解码器（包含请求编码器和响应解码器）
 * 2. HttpObjectAggregator: 将 HTTP 响应的多个部分聚合成完整的 FullHttpResponse
 * 3. SimpleHttpClientHandler: 业务处理器，发送请求并处理响应
 */
public class SimpleHttpClientInitializer extends ChannelInitializer<SocketChannel> {
    
    @Override
    protected void initChannel(SocketChannel ch) throws Exception {
        ChannelPipeline pipeline = ch.pipeline();
        
        // 1. HTTP 客户端编解码器：同时包含请求编码和响应解码功能
        pipeline.addLast("codec", new HttpClientCodec());
        
        // 2. HTTP 对象聚合器：将 HTTP 响应的多个部分聚合成完整的 FullHttpResponse
        // 参数 1048576 表示最大聚合内容长度（1MB）
        pipeline.addLast("aggregator", new HttpObjectAggregator(1048576));
        
        // 3. 业务处理器：发送请求并处理响应
        pipeline.addLast("handler", new SimpleHttpClientHandler());
    }
}

