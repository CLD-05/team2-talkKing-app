package com.team2.talkking.domain.notification.listener;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.messaging.simp.SimpMessageSendingOperations;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationListener {

    private final SimpMessageSendingOperations messagingTemplate;

    @RabbitListener(queues = "notification.queue")
    public void receiveNotification(com.team2.talkking.domain.chat.dto.ChatMessageDto notice) {
        // 이제 notice.getSender()에 null 대신 실제 유저 ID 숫자가 정직하게 박힙니다!
        log.info("🔔 [알림 서버 수신 완료] 타겟 유저 키: {}, 메시지 내용: {}", notice.getSender(), notice.getMessage());
        
        if (notice.getSender() != null) {
            // 심플 브로커 프리픽스(/topic)에 맞춰 유저 소켓 채널로 토스!
            String destination = "/topic/user." + notice.getSender(); 
            messagingTemplate.convertAndSend(destination, notice);
            log.info("🚀 [웹소켓 발송 완료] 목적지 주소: {}", destination);
        }
    }
}