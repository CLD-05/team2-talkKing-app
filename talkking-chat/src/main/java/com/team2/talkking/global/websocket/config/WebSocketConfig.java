package com.team2.talkking.global.websocket.config; // 💡 실제 본인 패키지 경로

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.*;

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // 🎯 [핵심 체크] 프론트엔드의 /ws 주소와 완전히 일치시키고, 뒤에 반드시 .withSockJS()를 붙여야 합니다!
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns("*") // CORS 허용
                .withSockJS();                 // 👈 이게 빠져있으면 프론트엔드 SockJS 통신 시 404가 터집니다!
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        // 채팅방 구독(sub) 및 발행(pub) prefix 규칙 설정 (본인 프로젝트 포맷에 맞게 유지)
        registry.enableSimpleBroker("/sub");
        registry.setApplicationDestinationPrefixes("/pub");
    }
}