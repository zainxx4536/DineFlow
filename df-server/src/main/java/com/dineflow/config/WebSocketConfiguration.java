package com.dineflow.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.server.standard.ServerEndpointExporter;

/**
 * 注册 WebSocket 服务端组件
 * 通过 ServerEndpointExporter 扫描并注册带有 @ServerEndpoint 的类
 * 让内嵌 Tomcat 的 WebSocket 容器真正识别这些端点
 */
@Configuration
public class WebSocketConfiguration {

    @Bean
    public ServerEndpointExporter serverEndpointExporter() {
        return new ServerEndpointExporter();
    }
}
