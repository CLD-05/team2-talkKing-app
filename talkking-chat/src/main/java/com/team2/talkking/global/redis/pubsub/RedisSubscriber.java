package com.team2.talkking.global.redis.pubsub;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class RedisSubscriber implements MessageListener {

    private final ObjectMapper objectMapper;
    private final RedisTemplate<String, Object> redisTemplate;
    // 💡 실제 브라우저로 메시지를 쏠 메인 서비스 인터페이스 (채팅방 전송 로직 등과 연결됩니다)
    // private final ChatMessageSender chatMessageSender; 

    /**
     * 레디스 채널에 메시지가 발행되면 자동으로 이 리스너 메서드가 동작합니다.
     */
    @Override
    public void onMessage(Message message, byte[] pattern) {
        try {
            // 1. 레디스 byte 바디 데이터를 디시리얼라이즈하여 JSON 문자열로 변환
            String publishMessage = (String) redisTemplate.getValueSerializer().deserialize(message.getBody());
            String channel = new String(message.getChannel());
            
            log.info("Redis Sub - Received from Channel [{}]: {}", channel, publishMessage);

            // 2. 비즈니스 로직 연동 (예: 채팅 채널 'room:*' 에서 온 신호일 경우)
            if (channel.startsWith("room:")) {
                // ChatMessageDto dto = objectMapper.readValue(publishMessage, ChatMessageDto.class);
                // chatMessageSender.sendToUser(dto);
            }
            
        } catch (Exception e) {
            log.error("Redis 구독 메시지 처리 중 역직렬화 에러 발생: {}", e.getMessage());
        }
    }
}