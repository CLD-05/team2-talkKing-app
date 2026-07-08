package com.team2.talkking.domain.chat.controller;

import com.team2.talkking.domain.chat.dto.ChatMessageDto;
import com.team2.talkking.domain.chat.service.ChatService;
import com.team2.talkking.domain.user.entity.User; 
import com.team2.talkking.domain.user.repository.UserRepository;
import com.team2.talkking.global.jwt.JwtProvider; 
import com.team2.talkking.global.rabbitmq.config.RabbitConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.stereotype.Controller; 

@Slf4j
@Controller 
@RequiredArgsConstructor
public class ChatController {

    private final RabbitTemplate rabbitTemplate;
    private final JwtProvider jwtProvider; 
    private final UserRepository userRepository; 
    private final ChatService chatService;

    /**
     * 클라이언트가 /pub/chat/message 주소로 보낸 메시지를 수신합니다.
     */
    @MessageMapping("/chat/message")
    public void message(ChatMessageDto message) {
        String roomId = message.getRoomId();
        Long userId;
        
        try {
            String senderIdStr = message.getSender(); 
            userId = Long.parseLong(senderIdStr); 
            
            User user = userRepository.findById(userId)
                    .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 유저입니다."));
            
            message.setSender(String.valueOf(userId));
            message.setSenderNickname(user.getNickname()); 
            
        } catch (Exception e) {
            log.error("❌ [ChatController] 웹소켓 메시지 유저 검증 실패: {}", e.getMessage());
            return; 
        }

        // -------------------------------------------------------------------
        // 🎯 [시점 ① / ②] 읽음 처리 및 메시지 저장 로직 분기 분선
        // -------------------------------------------------------------------
        
        // ケース A: 사용자가 채팅방에 입장하여 단순히 "여기까지 읽었음"을 보냈을 때 (시점 ①)
        if (ChatMessageDto.MessageType.READ.equals(message.getType())) {
            // 프론트엔드가 messageDto.getMessage()에 마지막 메시지 ID 번호를 담아 보낸다고 가정합니다.
            Long lastMessageId = Long.parseLong(message.getMessage());
            chatService.updateLastReadMessage(Long.parseLong(roomId), userId, lastMessageId);
            
            // 읽음 확인 이벤트는 굳이 다른 사람에게 브로드캐스팅(알림)할 필요가 없으므로 여기서 종료합니다.
            return;
        }

        // ケース B: 기존 입장 메시지 처리
        if (ChatMessageDto.MessageType.ENTER.equals(message.getType())) {
            message.setMessage(message.getSenderNickname() + "님이 입장하셨습니다.");
        }

        // 🎯 [1단계 연동] 일반 대화(TALK)인 경우, 전송 시점에 DB 저장 및 채팅방 타임스탬프 동기화를 수행합니다. (시점 ②)
        // 이 안에서 메시지를 보낸 본인의 Redis 책갈피 위치도 최신으로 자동 자동 갱신됩니다.
		/*
		 * if (ChatMessageDto.MessageType.TALK.equals(message.getType())) { message =
		 * chatService.saveAndRefreshRoom(message); }
		 */

        // 1. [기존 유지] 채팅방 내부에 연결된 사람들을 위해 RabbitMQ Exchange로 대화 토스!
        String routingKey = "room." + roomId;
        rabbitTemplate.convertAndSend(RabbitConfig.EXCHANGE_NAME, routingKey, message);

        // 2. 방 밖에 있는 참여자들을 찾아 실시간 메시지 푸시 알림을 쏩니다.
        chatService.sendNotificationToParticipants(message);
    }
}