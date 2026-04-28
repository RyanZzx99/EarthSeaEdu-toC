package com.earthseaedu.backend.config;

import com.earthseaedu.backend.websocket.AiChatWebSocketHandler;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
public class WebSocketProxyConfig implements WebSocketConfigurer {

    private final AiChatWebSocketHandler aiChatWebSocketHandler;

    public WebSocketProxyConfig(AiChatWebSocketHandler aiChatWebSocketHandler) {
        this.aiChatWebSocketHandler = aiChatWebSocketHandler;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(aiChatWebSocketHandler, "/ws/ai-chat")
            .setAllowedOriginPatterns("*");
    }
}
