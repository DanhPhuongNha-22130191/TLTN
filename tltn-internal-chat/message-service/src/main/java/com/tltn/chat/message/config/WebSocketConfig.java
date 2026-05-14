package com.tltn.chat.message.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns("*")
                .withSockJS();
        
        // Pure WebSocket connection without SockJS fallback as an alternative
        registry.addEndpoint("/ws").setAllowedOriginPatterns("*");
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        // Use /app for mapping incoming payloads to `@MessageMapping`
        registry.setApplicationDestinationPrefixes("/app");
        // Use /topic for broadcasting (groups) and /queue for direct msgs (personal)
        registry.enableSimpleBroker("/topic", "/queue", "/user");
        // Enables `convertAndSendToUser` mapping
        registry.setUserDestinationPrefix("/user");
    }
}
