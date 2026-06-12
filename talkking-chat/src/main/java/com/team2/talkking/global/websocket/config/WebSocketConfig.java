package com.team2.talkking.global.websocket.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.*;

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // 1. 기존 채팅용 엔드포인트
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns("*")
                .withSockJS();
        
        // 2. 🔥 [추가 필수] 실시간 알림용 엔드포인트 대개방!
        // 프론트엔드의 /ws-notif 및 인그레스 패스와 완벽하게 일치해야 합니다.
        registry.addEndpoint("/ws-notif")
                .setAllowedOriginPatterns("*") // CORS 허용
                .withSockJS();                 // 👈 이게 있어야 프론트엔드 SockJS가 /info 주소를 찾아서 붙습니다!
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        // 메시지 브로커 규칙 설정
        // 알림에 사용하는 /topic 주소 체계도 처리할 수 있도록 심플 브로커에 추가해 줍니다.
        registry.enableSimpleBroker("/sub", "/topic"); // 👈 기존 "/sub" 뒤에 ", \"/topic\""을 추가해 주세요!
        registry.setApplicationDestinationPrefixes("/pub");
    }
}