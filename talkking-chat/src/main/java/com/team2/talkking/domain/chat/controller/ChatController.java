package com.team2.talkking.domain.chat.controller;

import com.team2.talkking.domain.chat.dto.ChatMessageDto;
import com.team2.talkking.domain.chat.service.ChatService; // 🎯 ChatService 임포트 추가
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
    private final ChatService chatService; // 🎯 [핵심 추가] 알림 엔진을 쓰기 위해 ChatService 주입!

    /**
     * 클라이언트가 /pub/chat/message 주소로 보낸 메시지를 수신합니다.
     */
    @MessageMapping("/chat/message")
    public void message(ChatMessageDto message) {
        String roomId = message.getRoomId();
        
        try {
            String senderIdStr = message.getSender(); 
            Long userId = Long.parseLong(senderIdStr); 
            
            User user = userRepository.findById(userId)
                    .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 유저입니다."));
            
            message.setSender(String.valueOf(userId));
            message.setSenderNickname(user.getNickname()); 
            
        } catch (Exception e) {
            log.error("❌ [ChatController] 웹소켓 메시지 유저 검증 실패: {}", e.getMessage());
            return; 
        }

        if (ChatMessageDto.MessageType.ENTER.equals(message.getType())) {
            message.setMessage(message.getSenderNickname() + "님이 입장하셨습니다.");
        }

        // 1. [기존 유지] 채팅방 내부에 연결된 사람들을 위해 RabbitMQ Exchange로 대화 토스!
        String routingKey = "room." + roomId;
        rabbitTemplate.convertAndSend(RabbitConfig.EXCHANGE_NAME, routingKey, message);

        // ===================================================================
        // 🎯 2. [마지막 스위치 ON!] 방 밖에 있는 참여자들을 찾아 실시간 메시지 푸시 알림을 쏏니다.
        // ===================================================================
        chatService.sendNotificationToParticipants(message);
    }
}