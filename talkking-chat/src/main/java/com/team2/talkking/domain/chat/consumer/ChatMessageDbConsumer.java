package com.team2.talkking.domain.chat.consumer;

import com.team2.talkking.domain.chat.dto.ChatMessageDto;
import com.team2.talkking.domain.chat.entity.ChatRoom;
import com.team2.talkking.domain.chat.entity.Message;
import com.team2.talkking.domain.chat.repository.MessageRepository;
import com.team2.talkking.domain.chat.repository.ChatRoomRepository; 
import com.team2.talkking.domain.user.entity.User;
import com.team2.talkking.domain.user.repository.UserRepository;     
import com.team2.talkking.global.rabbitmq.config.RabbitConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.data.redis.core.StringRedisTemplate; 
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Slf4j
@Component
@RequiredArgsConstructor
public class ChatMessageDbConsumer {

    private final MessageRepository messageRepository;
    private final ChatRoomRepository chatRoomRepository;
    private final UserRepository userRepository;
    private final StringRedisTemplate redisTemplate; 

    private static final String REDIS_KEY_PREFIX = "chat:room:%d:user:%d:lastRead"; 

    @Transactional
    @RabbitListener(queues = RabbitConfig.DB_QUEUE_NAME)
    public void consumeMessageForDbSave(ChatMessageDto messageDto) {
        log.info("🎒 [RabbitMQ DB Consumer] 큐에서 메시지 수신: {}", messageDto.getMessage());

        try {
            if (messageDto.getType() == ChatMessageDto.MessageType.ENTER || 
                messageDto.getType() == ChatMessageDto.MessageType.QUIT ||
                messageDto.getType() == ChatMessageDto.MessageType.READ) { 
                return;
            }

            // [방어코드] 필수 파싱 데이터 검증
            if (messageDto.getRoomId() == null || messageDto.getSender() == null || 
                messageDto.getRoomId().trim().isEmpty() || messageDto.getSender().trim().isEmpty()) {
                log.error("❌ [RabbitMQ DB Consumer] 필수 파싱 데이터가 누락되었습니다. (roomId: {}, sender: {})", 
                        messageDto.getRoomId(), messageDto.getSender());
                return; 
            }

            Long roomId = Long.parseLong(messageDto.getRoomId().trim());
            Long senderId = Long.parseLong(messageDto.getSender().trim());

            ChatRoom chatRoom = chatRoomRepository.findById(roomId)
                    .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 채팅방입니다."));
            User sender = userRepository.getReferenceById(senderId);

            LocalDateTime now = LocalDateTime.now();

            // 1. 🎯 [교정] DB에 메시지를 저장함과 동시에 즉시 Flush 처리합니다.
            Message messageEntity = messageDto.toEntity(chatRoom, sender, now);
            messageRepository.saveAndFlush(messageEntity); 
            
            // 2. ChatRoom 엔티티에 최신 대화 내용과 표준 시간 동기화
            chatRoom.updateLastMessage(messageDto.getMessage(), now);
            
            // 🎯 [교정] 방 엔티티의 최신 메시지 시각(lastMessageAt) 정보도 실시간 조회를 위해 즉시 물리 DB에 Flush 반영합니다.
            chatRoomRepository.saveAndFlush(chatRoom); 
            
            // 3. 메시지를 보낸 본인의 Redis 읽음 책갈피도 저장된 최신 messageId로 즉시 전진시킵니다.
            String redisKey = String.format(REDIS_KEY_PREFIX, roomId, senderId);
            redisTemplate.opsForValue().set(redisKey, String.valueOf(messageEntity.getMessageId()));
            
            log.info("💾 [RabbitMQ DB Consumer] DB 즉시 반영(Flush), 채팅방 정렬 시각 최신화 완료! 메시지 ID: #{}", messageEntity.getMessageId());
            
        } catch (Exception e) {
            log.error("❌ [RabbitMQ DB Consumer] DB 저장 중 에러 발생: ", e);
        }
    }
}