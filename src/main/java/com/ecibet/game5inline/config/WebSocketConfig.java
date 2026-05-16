package com.ecibet.game5inline.config;

import com.ecibet.game5inline.websocket.GameWebSocketHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketConfigurer {

    private final GameWebSocketHandler gameWebSocketHandler;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(gameWebSocketHandler, "/ws")
                .setAllowedOrigins("https://ecibet-front.vercel.app")
                .withSockJS()
                .setWebSocketEnabled(true)
                .setHttpMessageCacheSize(1000)
                .setDisconnectDelay(5000);
    }
}