package com.team2.talkking.global.websocket.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.*;

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // ✅ 채팅 엔드포인트
        // setAllowedOriginPatterns("*")로 통일 (SecurityConfig와 일관성)
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns("*")  // ✅ 수정: Origins 제거, Patterns으로 통일
                .withSockJS();
        
        // ✅ 알림 엔드포인트
        // setAllowedOriginPatterns("*")로 통일 (SecurityConfig와 일관성)
        registry.addEndpoint("/ws-notif")
                .setAllowedOriginPatterns("*")  // ✅ 수정: Origins 제거, Patterns으로 통일
                .withSockJS();
    }	

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        // 메시지 브로커 규칙 설정
        // ✅ 확인: /topic 추가됨 (알림용)
        registry.enableSimpleBroker("/sub", "/topic");
        registry.setApplicationDestinationPrefixes("/pub");
    }
}
