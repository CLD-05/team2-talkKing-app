package com.team2.talkking.global.rabbitmq.pubsub;

import com.team2.talkking.domain.chat.dto.ChatMessageDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.ExchangeTypes; // 💡 상수를 쓰기 위해 임포트 추가
import org.springframework.amqp.rabbit.annotation.Queue;
import org.springframework.amqp.rabbit.annotation.QueueBinding;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.messaging.simp.SimpMessageSendingOperations;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class RabbitListenerService {

    private final SimpMessageSendingOperations messagingTemplate;

    /**
     * RabbitMQ로부터 메시지가 들어오면 동적으로 가로채는 리스너입니다.
     * 각 서버 인스턴스(8080, 8081)마다 고유한 임시 큐를 자동 생성하고 Exchange에 바인딩합니다.
     */
    @RabbitListener(bindings = @QueueBinding(
            value = @Queue(
                    exclusive = "true", // 💡 어노테이션 내부 속성 셋팅 시 안정성을 위해 문자열 형태로 선언합니다.
                    autoDelete = "true"
            ),
            exchange = @org.springframework.amqp.rabbit.annotation.Exchange(
                    value = "chat.exchange", 
                    type = ExchangeTypes.TOPIC // 🎯 [핵심 수정] "topic" 문자열 대신 ExchangeTypes.TOPIC 상수를 사용합니다!
            ),
            key = "room.*"
    ))
    public void receiveMessage(ChatMessageDto dto) {
        try {
            log.info("RabbitMQ Sub - Received message for Room [{}]: {}", dto.getRoomId(), dto.getMessage());

            // 🚀 최종적으로 프론트엔드가 구독하고 있는 웹소켓 주소로 브로드캐스팅!
            messagingTemplate.convertAndSend("/sub/chat/room/" + dto.getRoomId(), dto);
            
        } catch (Exception e) {
            log.error("RabbitMQ 메시지 처리 중 에러 발생: {}", e.getMessage());
        }
    }
}