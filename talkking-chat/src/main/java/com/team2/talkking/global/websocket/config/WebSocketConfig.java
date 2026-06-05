package com.team2.talkking.global.websocket.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

@Configuration
@EnableWebSocketMessageBroker // 💡 STOMP 메시지 브로커를 활성화합니다.
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        // 📥 1. 내장 브로커 지원 (구독 요청 주소 접두사)
        // 프론트엔드에서 이 주소로 시작하는 채널을 subscribe(구독)하고 있으면 메시지를 실시간으로 받습니다.
        registry.enableSimpleBroker("/sub");

        // 📤 2. 도착지 접두사 (발행 요청 주소 접두사)
        // 프론트엔드에서 메시지를 보낼(publish) 때 이 주소를 앞에 붙여서 던집니다.
        // @MessageMapping이 붙은 컨트롤러 메서드로 매핑됩니다.
        registry.setApplicationDestinationPrefixes("/pub");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // 🔌 3. 최초 웹소켓 핸드셰이크를 맺을 엔드포인트 지정
        // 우리 인프라 Ingress(3-routing-ingress.yaml)에서 /ws 경로를 chat-service로 뚫어놨던 것과 100% 매칭됩니다!
    	registry.addEndpoint("/ws")
        .setAllowedOriginPatterns("*") // 모든 오리진 주소 허용 패턴 하나만 깔끔하게 유지
        .withSockJS();
    }
}