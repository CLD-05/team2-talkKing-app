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
                        .sender(String.valueOf(msg.getSender().getUserId())) 
                        .senderNickname(msg.getSender().getNickname()) 
                        .message(msg.getMessage())
                        .createdAt(msg.getCreatedAt().toString())
                        .build())
                .sorted((m1, m2) -> m1.getCreatedAt().compareTo(m2.getCreatedAt())) 
                .collect(Collectors.toList());
    }

    /**
     * 🔔 1. 일반 대화(TALK) 발생 시, 나를 제외한 방 참여자들에게 실시간 알림 푸시
     */
    @Transactional(readOnly = true)
    public void sendNotificationToParticipants(ChatMessageDto messageDto) {
        if (ChatMessageDto.MessageType.TALK.equals(messageDto.getType())) {
            try {
                Long roomId = Long.parseLong(messageDto.getRoomId());
                Long senderId = Long.parseLong(messageDto.getSender()); 

                List<UserChatRoom> participants = userChatRoomRepository.findAllByChatRoom_RoomId(roomId);

                for (UserChatRoom participant : participants) {
                    Long targetUserId = participant.getUser().getUserId();

                    if (targetUserId.equals(senderId)) continue;

                    ChatMessageDto pushNotice = new ChatMessageDto();
                    pushNotice.setType(ChatMessageDto.MessageType.TALK);
                    pushNotice.setRoomId(String.valueOf(roomId));
                    pushNotice.setSender(String.valueOf(senderId));
                    pushNotice.setSenderNickname(messageDto.getSenderNickname());
                    pushNotice.setMessage(messageDto.getMessage()); 

                    String userRoutingKey = "user." + targetUserId;
                    
                    rabbitTemplate.convertAndSend(RabbitConfig.EXCHANGE_NAME, userRoutingKey, pushNotice);
                    log.info("🚀 [채팅 알림 발송] 대상 유저: {}, 보낸 사람: {}", targetUserId, senderId);
                }
            } catch (Exception e) {
                log.error("❌ 실시간 채팅 알림 발송 중 오류 발생: ", e);
            }
        }
    }

    /**
     * 🔔 2. [초대 알림 데이터 및 타이밍 최종 완성판]
     * 🎯 락을 유발하는 @Transactional과 afterCommit을 완전히 제거했습니다.
     * 별도의 비동기 데몬 쓰레드를 열어 8080의 DB 저장이 커밋될 대기 시간을 안정적으로 보장한 뒤 무조건 직송합니다.
     */
    public void sendInviteNotification(Long roomId, Long inviterId, List<Long> invitedUserIds, String roomTitle) {
        log.info("📢 [초대 알림 엔진 가동] 방 번호: #{}, 초대받은 명단 수: {}개", roomId, invitedUserIds.size());
        
        new Thread(() -> {
            try {
                // 🎯 8080 서버가 컨트롤러 요청을 끝내고 DB 물리 커밋을 완료할 시간을 벌어주는 완충재 (300ms)
                Thread.sleep(300); 
                
                for (Object target : invitedUserIds) {
                    Long targetUserId = Long.parseLong(String.valueOf(target));
                    
                    if (targetUserId.equals(inviterId)) continue; 

                    ChatMessageDto pushNotice = new ChatMessageDto();
                    pushNotice.setType(ChatMessageDto.MessageType.ENTER); 
                    pushNotice.setRoomId(String.valueOf(roomId));
                    pushNotice.setSender(String.valueOf(inviterId));
                    pushNotice.setSenderNickname("시스템"); 
                    
                    String welcomeMsg = "✉️ '" + roomTitle + "' 채팅방에 초대되었습니다!";
                    pushNotice.setMessage(welcomeMsg);

                    String userRoutingKey = "user." + targetUserId;
                    
                    // RabbitMQ 브로커로 안전 직송
                    rabbitTemplate.convertAndSend(RabbitConfig.EXCHANGE_NAME, userRoutingKey, pushNotice);
                    log.info("🚀 [초대 알림 전송 완료] 수신 대상: user.{}, 메세지: {}", targetUserId, welcomeMsg);
                }
            } catch (Exception e) {
                log.error("❌ 비동기 초대 알림 발송 중 치명적 오류 발생: ", e);
            }
        }).start();
    }
}