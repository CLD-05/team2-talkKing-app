package com.team2.talkking.domain.notification.listener;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.simp.SimpMessageSendingOperations;
import org.springframework.messaging.support.MessageBuilder; // 🎯 추가
import org.springframework.util.MimeTypeUtils; // 🎯 추가
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationListener {

    private final SimpMessageSendingOperations messagingTemplate;

    @RabbitListener(queues = "notification.queue")
    public void receiveNotification(
            com.team2.talkking.domain.chat.dto.ChatMessageDto notice,
            @Header(AmqpHeaders.RECEIVED_ROUTING_KEY) String routingKey) {
        
        try {
            String targetUserId = routingKey.replace("user.", "");
            
            log.info("🔔 [알림 서버 수신 완료] 실제 수신 대상(타겟 유저 키): {}, 보낸 사람: {}, 메시지 내용: {}", 
                    targetUserId, notice.getSenderNickname(), notice.getMessage());
            
            String destination = "/topic/user." + targetUserId; 
            
            // 🎯 [빨간 줄 박멸] MessageHeaders.CONTENT_TYPE을 사용하여 안전하게 래핑합니다.
            org.springframework.messaging.Message<com.team2.talkking.domain.chat.dto.ChatMessageDto> message = 
                    MessageBuilder.withPayload(notice)
                            .setHeader(org.springframework.messaging.MessageHeaders.CONTENT_TYPE, MimeTypeUtils.APPLICATION_JSON)
                            .build();

            // 헤더가 포함된 래핑 메시지로 발송
            messagingTemplate.convertAndSend(destination, message);
            log.info("🚀 [웹소켓 발송 완료 - 헤더 매핑형] 목적지 주소: {}", destination);
            
        } catch (Exception e) {
            log.error("❌ 알림 라우팅 중 오류 발생: ", e);
        }
    }
}