package com.team2.talkking.global.websocket.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.*;

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketNotificationConfig implements WebSocketMessageBrokerConfigurer {

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // 알림 전용 소켓 통로 이름을 /ws-notif 로 유지하여 개설합니다.
        registry.addEndpoint("/ws-notif")
                .setAllowedOriginPatterns("*") // CORS 완벽 허용
                .withSockJS();
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        // 🎯 [핵심 교정] 에러를 유발하는 Netty 기반 릴레이 대신 안전한 기본 브로커를 가동합니다.
        // 주소 프리픽스는 프론트엔드와 싱크를 맞추기 위해 "/topic"을 지정합니다.
        registry.enableSimpleBroker("/topic");
        
        // 프론트엔드가 메시지를 전송할 때 사용할 프리픽스
        registry.setApplicationDestinationPrefixes("/pub");
    }
}