package com.team2.talkking.domain.chat.service;

import com.team2.talkking.domain.chat.dto.ChatMessageDto;
import com.team2.talkking.domain.chat.entity.Message;
import com.team2.talkking.domain.chat.entity.UserChatRoom;
import com.team2.talkking.domain.chat.repository.MessageRepository;
import com.team2.talkking.domain.chat.repository.UserChatRoomRepository;
import com.team2.talkking.global.rabbitmq.config.RabbitConfig;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatService {

    private final MessageRepository messageRepository;
    // 🎯 실시간 알림 대상을 찾고 RabbitMQ로 쏘기 위해 레포지토리와 템플릿을 추가 주입합니다.
    private final UserChatRoomRepository userChatRoomRepository;
    private final RabbitTemplate rabbitTemplate;

    /**
     * 🔍 특정 채팅방의 과거 대화 내역을 페이징 처리하여 조회합니다.
     */
    @Transactional(readOnly = true)
    public List<ChatMessageDto> getChatHistory(Long roomId, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("messageId").descending());
        List<Message> messages = messageRepository.findByChatRoomRoomId(roomId, pageable);

        return messages.stream()
                .map(msg -> ChatMessageDto.builder()
                        .type(ChatMessageDto.MessageType.TALK)
                        .roomId(String.valueOf(msg.getChatRoom().getRoomId()))
                        .sender(String.valueOf(msg.getSender().getUserId())) // 프론트엔드 비교를 위해 유저 ID 바인딩
                        .senderNickname(msg.getSender().getNickname()) // 닉네임 필드 분리 바인딩
                        .message(msg.getMessage())
                        .createdAt(msg.getCreatedAt().toString())
                        .build())
                .sorted((m1, m2) -> m1.getCreatedAt().compareTo(m2.getCreatedAt())) 
                .collect(Collectors.toList());
    }

    /**
     * 🔔 [신설] 일반 대화(TALK) 발생 시, 방에 없는 다른 참여자들에게 실시간 알림을 푸시합니다.
     */
    @Transactional(readOnly = true)
    public void sendNotificationToParticipants(ChatMessageDto messageDto) {
        // 일반 대화 메시지일 때만 알림 엔진을 가동합니다.
        if (ChatMessageDto.MessageType.TALK.equals(messageDto.getType())) {
            try {
                Long roomId = Long.parseLong(messageDto.getRoomId());
                Long senderId = Long.parseLong(messageDto.getSender());

                // 1. 현재 채팅방에 속해 있는 모든 멤버 매핑 리스트를 가져옵니다.
                List<UserChatRoom> participants = userChatRoomRepository.findAllByChatRoom_RoomId(roomId);

                // 2. 방 멤버들을 순회하며 메시지를 보낸 본인을 제외하고 알림을 던집니다.
                for (UserChatRoom participant : participants) {
                    Long targetUserId = participant.getUser().getUserId();

                    // 본인에게는 알림을 보낼 필요가 없으므로 패스!
                    if (targetUserId.equals(senderId)) continue;

                    // ✉️ 알림 서버(8082)가 가로챌 알림 전용 DTO 패킷 조립
                    ChatMessageDto pushNotice = new ChatMessageDto();
                    pushNotice.setType(ChatMessageDto.MessageType.TALK); // 타입 고정
                    pushNotice.setRoomId(String.valueOf(roomId));
                    pushNotice.setSender(String.valueOf(senderId));
                    pushNotice.setSenderNickname(messageDto.getSenderNickname()); // 보낸 사람 이름
                    pushNotice.setMessage(messageDto.getMessage()); // 메시지 본문 내용

                    // 🎯 알림 서버의 큐와 연동된 라우팅 키 설정 (예: user.5)
                    String userRoutingKey = "user." + targetUserId;
                    
                    // RabbitMQ 전용 우체국(Exchange)을 통해 전송!
                    rabbitTemplate.convertAndSend(RabbitConfig.EXCHANGE_NAME, userRoutingKey, pushNotice);
                    log.info("🚀 [메시지 알림 발송] 대상 유저: {}, 방 번호: #{}", targetUserId, roomId);
                }
            } catch (Exception e) {
                log.error("❌ 실시간 메시지 알림 발송 중 오류 발생: ", e);
            }
        }
    }
}