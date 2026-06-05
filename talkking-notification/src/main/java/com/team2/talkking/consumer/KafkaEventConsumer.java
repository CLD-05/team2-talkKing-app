package com.team2.talkking.consumer;



import com.team2.talkking.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class KafkaEventConsumer {

    private final NotificationService notificationService;

    /**
     * 카프카 토픽 리스너 틀 (3주차 @KafkaListener 장착 예정)
     */
    public void consumeChatEvent() {
        // TODO: 카프카로부터 메시지 Event 역직렬화 및 수신
        // notificationService.sendChatNotification(...);
    }
}