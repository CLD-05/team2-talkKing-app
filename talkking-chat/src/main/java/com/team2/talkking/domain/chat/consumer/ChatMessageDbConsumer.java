package com.team2.talkking.domain.chat.consumer;

import com.team2.talkking.domain.chat.dto.ChatMessageDto;
import com.team2.talkking.domain.chat.entity.ChatRoom;
import com.team2.talkking.domain.chat.entity.Message;
import com.team2.talkking.domain.chat.repository.MessageRepository;
import com.team2.talkking.domain.chat.repository.ChatRoomRepository; // 🎯 본인 프로젝트의 방 레포지토리 임포트
import com.team2.talkking.domain.user.entity.User;
import com.team2.talkking.domain.user.repository.UserRepository;     // 🎯 본인 프로젝트의 유저 레포지토리 임포트
import com.team2.talkking.global.rabbitmq.config.RabbitConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@RequiredArgsConstructor
public class ChatMessageDbConsumer {

    private final MessageRepository messageRepository;
    
    // 🎯 [수정] 실제 프로젝트의 Room, User 레포지토리를 주입받습니다.
    private final ChatRoomRepository chatRoomRepository;
    private final UserRepository userRepository;

    @Transactional
    @RabbitListener(queues = RabbitConfig.DB_QUEUE_NAME)
    public void consumeMessageForDbSave(ChatMessageDto messageDto) {
        log.info("🎒 [RabbitMQ DB Consumer] 큐에서 메시지 수신: {}", messageDto.getMessage());

        try {
            if (messageDto.getType() == ChatMessageDto.MessageType.ENTER || 
                messageDto.getType() == ChatMessageDto.MessageType.QUIT) {
                return;
            }

            // 🎯 [방어코드 추가] roomId나 sender가 정상적으로 파싱 가능한 상태인지 먼저 확인합니다.
            if (messageDto.getRoomId() == null || messageDto.getSender() == null || 
                messageDto.getRoomId().trim().isEmpty() || messageDto.getSender().trim().isEmpty()) {
                log.error("❌ [RabbitMQ DB Consumer] 필수 파싱 데이터가 누락되었습니다. (roomId: {}, sender: {})", 
                        messageDto.getRoomId(), messageDto.getSender());
                return; // 저장 로직을 태우지 않고 우아하게 종료
            }

            Long roomId = Long.parseLong(messageDto.getRoomId().trim());
            Long senderId = Long.parseLong(messageDto.getSender().trim());

            ChatRoom chatRoom = chatRoomRepository.getReferenceById(roomId);
            User sender = userRepository.getReferenceById(senderId);

            Message messageEntity = messageDto.toEntity(chatRoom, sender);
            messageRepository.save(messageEntity);
            
            log.info("💾 [RabbitMQ DB Consumer] DB 비동기 저장 완료!");
            
        } catch (Exception e) {
            log.error("❌ [RabbitMQ DB Consumer] DB 저장 중 에러 발생: ", e);
        }
    }
}