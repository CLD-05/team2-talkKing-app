package com.team2.talkking.domain.chat.controller;

import com.team2.talkking.domain.chat.dto.ChatMessageDto;
import com.team2.talkking.global.redis.pubsub.RedisPublisher;
import com.team2.talkking.global.redis.pubsub.RedisSubscriber;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.data.redis.listener.adapter.MessageListenerAdapter;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@RestController
@RequiredArgsConstructor
public class ChatController {

    private final RedisPublisher redisPublisher;
    private final RedisSubscriber redisSubscriber;
    private final RedisMessageListenerContainer redisMessageListenerContainer;

    // 💡 채팅방 ID별로 레디스 토픽(채널) 매핑 정보를 캐싱할 스레드 안전한 맵
    private final Map<String, ChannelTopic> topics = new ConcurrentHashMap<>();

    /**
     * 클라이언트가 /pub/chat/message 주소로 보낸 메시지를 수신합니다.
     */
    @MessageMapping("/chat/message")
    public void message(ChatMessageDto message) {
        String roomId = message.getRoomId();
        
        // 1. 유저가 방에 입장할 때의 전처리 로직
        if (ChatMessageDto.MessageType.ENTER.equals(message.getType())) {
            message.setMessage(message.getSender() + "님이 입장하셨습니다.");
            
            // 이 방의 레디스 채널 토픽이 없다면 동적으로 생성하고 구독(Subscribe) 리스너에 등록
            topics.computeIfAbsent(roomId, id -> {
                ChannelTopic topic = new ChannelTopic("room:" + id);
                // 글로벌 구독 엔진(RedisSubscriber)을 리스너 어댑터로 감싸서 컨테이너에 바인딩
                redisMessageListenerContainer.addMessageListener(
                        new MessageListenerAdapter(redisSubscriber), 
                        topic
                );
                log.info("새로운 레디스 채널 토픽 생성 및 리스너 등록 완료 - Room ID: {}", id);
                return topic;
            });
        }

        // 2. [선택] 필요 시 데이터베이스(RDB)나 몽고디비에 메시지 내역 영속화
        // chatMessageService.save(message);

        // 3. 🎯 핵심: 글로벌 레디스 엔진을 통해 메시지 브로드캐스팅!
        // 어떤 파드에 붙어있던 유저든 이 신호를 받아 웹소켓으로 메시지를 수신하게 됩니다.
        ChannelTopic topic = topics.get(roomId);
        if (topic != null) {
            redisPublisher.publish(topic, message);
        } else {
            log.warn("메시지 전송 실패 - 활성화된 토픽을 찾을 수 없음. Room ID: {}", roomId);
        }
    }
}