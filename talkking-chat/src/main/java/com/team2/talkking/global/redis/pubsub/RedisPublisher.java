package com.team2.talkking.global.redis.pubsub;

import com.team2.talkking.domain.chat.dto.ChatMessageDto;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.stereotype.Service;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class RedisPublisher {

    private final RedisTemplate<String, Object> redisTemplate;

    public void publish(ChannelTopic topic, ChatMessageDto message) {
        // 💡 지정된 레디스 채널 토픽으로 메시지를 발행합니다.
        // 우리 RedisConfig에 등록한 Jackson 직렬화에 의해 JSON으로 이쁘게 변환되어 날아갑니다.
        redisTemplate.convertAndSend(topic.getTopic(), message);
    }
}