package com.team2.talkking.domain.chat.service;

import com.team2.talkking.domain.chat.dto.ChatMessageDto;
import com.team2.talkking.domain.chat.dto.ChatRoomListResponseDto;
import com.team2.talkking.domain.chat.entity.ChatRoom;
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
import org.springframework.data.domain.Slice;
import org.springframework.data.domain.SliceImpl;
import org.springframework.data.domain.Sort;
import org.springframework.data.redis.core.StringRedisTemplate; 
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatService {

    private final MessageRepository messageRepository;
    private final UserChatRoomRepository userChatRoomRepository;
    private final RabbitTemplate rabbitTemplate;
    private final StringRedisTemplate redisTemplate; 

    private static final String REDIS_KEY_PREFIX = "chat:room:%d:user:%d:lastRead";

    /**
     * 🎯 [2단계 완성판] 유저가 채팅방에 진입했을 때, 데이터베이스 기준 방의 가장 최신 메시지 ID를 찾아 Redis 책갈피를 전진시킵니다.
     * (이 타이밍 제어를 통해 비동기 큐 저장 속도 차이로 인한 좀비 배지 버그를 근본적으로 차단합니다.)
     */
    @Transactional
    public void updateLastReadMessage(Long roomId, Long userId, Long lastMessageId) {
        Long targetId = lastMessageId;

        // 🎯 레포지토리에서 교정한 Optional 구조를 안정적으로 꺼내 씁니다.
        Optional<Message> latestMsgOpt = messageRepository.findTopByChatRoom_RoomIdOrderByMessageIdDesc(roomId);
        if (latestMsgOpt.isPresent()) {
            targetId = latestMsgOpt.get().getMessageId();
        }

        String redisKey = String.format(REDIS_KEY_PREFIX, roomId, userId);
        redisTemplate.opsForValue().set(redisKey, String.valueOf(targetId));
        
        log.info("📌 [읽음 처리 완전 동기화] 방: #{}, 유저: #{}, 확정된 마지막 읽은 ID: {}", roomId, userId, targetId);
    }

    /**
     * 🔍 [1, 2단계 목록 통합 조회] 내가 참여 중인 채팅방 목록을 최신 메시지 순으로 정렬하고, '안 읽은 개수'를 포함하여 조회합니다.
     */
    @Transactional(readOnly = true)
    public List<ChatRoomListResponseDto> getMyChatRoomList(Long userId) {
        // userChatRoomRepository에 보완해 둔 COALESCE 정렬 JPQL 쿼리가 정상 실행됩니다.
        List<UserChatRoom> myRooms = userChatRoomRepository.findByUser_UserIdOrderByChatRoom_LastMessageAtDesc(userId);

        return myRooms.stream().map(userChatRoom -> {
            ChatRoom room = userChatRoom.getChatRoom();
            Long roomId = room.getRoomId();
            
            String redisKey = String.format(REDIS_KEY_PREFIX, roomId, userId);
            String lastReadIdRaw = redisTemplate.opsForValue().get(redisKey);

            long unreadCount;
            
            // 🎯 [완벽 방어] null 객체뿐만 아니라 공백문자, 혹은 문자열 텍스트 "null" 케이스까지 전방위 필터링
            if (lastReadIdRaw == null || lastReadIdRaw.trim().isEmpty() || "null".equals(lastReadIdRaw.trim())) {
                unreadCount = messageRepository.countByChatRoom_RoomId(roomId);
            } else {
                Long lastReadId = Long.parseLong(lastReadIdRaw.trim());
                unreadCount = messageRepository.countUnreadMessages(roomId, lastReadId);
            }

            return ChatRoomListResponseDto.builder()
                    .roomId(roomId)
                    .title(room.getTitle())
                    .isGroup(room.getIsGroup())
                    .lastMessage(room.getLastMessageContent())
                    .lastMessageAt(room.getLastMessageAt() != null ? room.getLastMessageAt().toString() : null)
                    .unreadCount(unreadCount) 
                    .build();
        }).collect(Collectors.toList());
    }

    /**
     * 🔍 [무한 스크롤 엔진 연동] 특정 채팅방의 과거 대화 내역을 페이징 처리하여 조회합니다.
     */
    @Transactional(readOnly = true)
    public Slice<ChatMessageDto> getChatHistory(Long roomId, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("messageId").descending());
        Slice<Message> messageSlice = messageRepository.findByChatRoom_RoomId(roomId, pageable);

        List<ChatMessageDto> dtoList = messageSlice.getContent().stream()
                .map(msg -> ChatMessageDto.builder()
                        .messageId(msg.getMessageId()) 
                        .type(ChatMessageDto.MessageType.TALK)
                        .roomId(String.valueOf(msg.getChatRoom().getRoomId()))
                        .sender(String.valueOf(msg.getSender().getUserId())) 
                        .senderNickname(msg.getSender().getNickname()) 
                        .message(msg.getMessage())
                        .createdAt(msg.getCreatedAt().toString())
                        .build())
                .sorted((m1, m2) -> m1.getCreatedAt().compareTo(m2.getCreatedAt())) 
                .collect(Collectors.toList());

        return new SliceImpl<>(dtoList, pageable, messageSlice.hasNext());
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
     * 🔔 2. [비동기] 초대 알림 데이터 및 타이밍 기동 엔진
     */
    public void sendInviteNotification(Long roomId, Long inviterId, List<Long> invitedUserIds, String roomTitle) {
        log.info("📢 [초대 알림 엔진 가동] 방 번호: #{}, 초대받은 명단 수: {}개", roomId, invitedUserIds.size());
        
        new Thread(() -> {
            try {
                Thread.sleep(300); 
                
                for (Object target : invitedUserIds) {
                    Long targetUserId = Long.parseLong(String.valueOf(target));
                    
                    if (targetUserId.equals(inviterId)) continue; 

                    ChatMessageDto pushNotice = new ChatMessageDto();
                    pushNotice.setType(ChatMessageDto.MessageType.ENTER); 
                    pushNotice.setRoomId(String.valueOf(roomId));
                    pushNotice.setSender(String.valueOf(inviterId));
                    pushNotice.setSenderNickname("SYSTEM"); 
                    
                    String welcomeMsg = "✉️ '" + roomTitle + "' 채팅방에 초대되었습니다!";
                    pushNotice.setMessage(welcomeMsg);

                    String userRoutingKey = "user." + targetUserId;
                    
                    rabbitTemplate.convertAndSend(RabbitConfig.EXCHANGE_NAME, userRoutingKey, pushNotice);
                    log.info("🚀 [초대 알림 전송 완료] 수신 대상: user.{}, 메세지: {}", targetUserId, welcomeMsg);
                }
            } catch (Exception e) {
                log.error("❌ 비동기 초대 알림 발송 중 치명적 오류 발생: ", e);
            }
        }).start();
    }
}