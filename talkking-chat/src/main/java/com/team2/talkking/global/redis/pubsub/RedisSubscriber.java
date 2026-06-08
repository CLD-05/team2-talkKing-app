package com.team2.talkking.global.redis.pubsub;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.team2.talkking.domain.chat.dto.ChatMessageDto; // 💡 DTO 패키지 임포트
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.messaging.simp.SimpMessageSendingOperations;
import org.springframework.stereotype.Service;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class RedisSubscriber implements MessageListener {

    private final ObjectMapper objectMapper;
    private final SimpMessageSendingOperations messagingTemplate;
    private final RedisTemplate<String, Object> redisTemplate;

    @Override
    public void onMessage(Message message, byte[] pattern) {
        try {
            // 1. 레디스에서 가져온 byte 데이터를 문자열(JSON)로 변환
            String publishMessage = (String) redisTemplate.getValueSerializer().deserialize(message.getBody());
            String channel = new String(message.getChannel());
            
            log.info("Redis Sub - Received from Channel [{}]: {}", channel, publishMessage);

            // 2. 🎯 JSON 문자열을 ChatMessageDto 객체로 역직렬화
            ChatMessageDto dto = objectMapper.readValue(publishMessage, ChatMessageDto.class);
            
            // 3. 🚀 자바스크립트가 구독 중인 주소로 브로드캐스팅!
            // 프론트의 stompClient.subscribe 주소 규칙과 맞춰야 합니다.
            // 만약 프론트에서 /sub/chat/room/test-room 구조를 쓰고 있다면 아래와 같이 보냅니다.
            messagingTemplate.convertAndSend("/sub/chat/room/" + dto.getRoomId(), dto);
            
        } catch (Exception e) {
            log.error("Redis 구독 메시지 처리 중 에러 발생: {}", e.getMessage(), e);
        }
    }
}