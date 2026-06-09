package com.team2.talkking.domain.chat.service;

import com.team2.talkking.domain.chat.dto.ChatMessageDto;
import com.team2.talkking.domain.chat.entity.ChatRoom;
import com.team2.talkking.domain.chat.entity.Message;
import com.team2.talkking.domain.chat.entity.UserChatRoom;
import com.team2.talkking.domain.chat.repository.ChatRoomRepository;
import com.team2.talkking.domain.chat.repository.MessageRepository;
import com.team2.talkking.domain.chat.repository.UserChatRoomRepository;
import com.team2.talkking.domain.user.entity.User;
import com.team2.talkking.domain.user.repository.UserRepository;
import com.team2.talkking.global.rabbitmq.config.RabbitConfig;
import lombok.extern.slf4j.Slf4j;

import lombok.RequiredArgsConstructor;

import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatRoomService {

    private final ChatRoomRepository chatRoomRepository;
    private final UserChatRoomRepository userChatRoomRepository;
    private final UserRepository userRepository;
    private final RabbitTemplate rabbitTemplate;
    private final MessageRepository messageRepository;

    /**
     * 🔍 1. 유저 검색 (고유 ID 또는 닉네임 하이브리드 검색)
     */
    @Transactional(readOnly = true)
    public List<UserSearchResponse> searchUsers(String keyword, Long myId) {
        List<User> users = userRepository.searchUsersExceptMe(keyword, myId);
        
        return users.stream()
                .map(user -> new UserSearchResponse(user.getUserId(), user.getUsername(), user.getNickname()))
                .collect(Collectors.toList());
    }

    /**
     * ➕ 2. 그룹 채팅방 개설 및 다중 유저 초대
     */
    @Transactional
    public Long createGroupChatRoom(String roomTitle, Long creatorId, List<Long> invitedUserIds) {
        ChatRoom chatRoom = ChatRoom.builder()
                .title(roomTitle)
                .isGroup(true) 
                .build();
        chatRoomRepository.save(chatRoom);

        // 2. 방 개설자(나)를 먼저 방 멤버로 추가
        User creator = userRepository.findById(creatorId)
                .orElseThrow(() -> new IllegalArgumentException("방 개설자 정보가 올바르지 않습니다."));
        
        UserChatRoom creatorMapping = UserChatRoom.builder()
                .user(creator)
                .chatRoom(chatRoom)
                .build();
        creatorMapping.setJoinedAtNow();
        creatorMapping.updateLastReadAt();
        userChatRoomRepository.save(creatorMapping);

        // 3. 초대된 유저들을 반복문 돌며 매핑 테이블에 일괄 등록
        for (Long userId : invitedUserIds) {
            if (userId.equals(creatorId)) continue; // 本인 중복 초대 방지

            User invitedUser = userRepository.findById(userId)
                    .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 유저가 초대 목록에 포함되어 있습니다."));

            UserChatRoom userChatRoom = UserChatRoom.builder()
                    .user(invitedUser)
                    .chatRoom(chatRoom)
                    .build();
            userChatRoom.setJoinedAtNow();
            userChatRoom.updateLastReadAt();
            userChatRoomRepository.save(userChatRoom);
            
            // 🎯 [방 개설 시에도 알림 추가] 개설 시점에 초대받은 유저들의 '개인 알림 큐'로도 신호를 쏩니다.
            ChatMessageDto inviteNotice = new ChatMessageDto();
            inviteNotice.setType(ChatMessageDto.MessageType.ENTER);
            inviteNotice.setRoomId(String.valueOf(chatRoom.getRoomId()));
            inviteNotice.setSender(String.valueOf(invitedUser.getUserId()));
            inviteNotice.setMessage("✉️ '" + chatRoom.getTitle() + "' 채팅방에 초대되었습니다!");
            
            String userRoutingKey = "user." + invitedUser.getUserId();
            rabbitTemplate.convertAndSend(RabbitConfig.EXCHANGE_NAME, userRoutingKey, inviteNotice);
        }

        return chatRoom.getRoomId(); 
    }

    /**
     * 👥 3. 기존 채팅방에 새로운 유저들을 추가로 초대 (DB 저장 및 분산 알림 전송)
     */
    @Transactional
    public void inviteUsersToExistingRoom(Long roomId, List<Long> invitedUserIds) {
        ChatRoom chatRoom = chatRoomRepository.findById(roomId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 채팅방입니다."));

        if (invitedUserIds != null && !invitedUserIds.isEmpty()) {
            for (Long userId : invitedUserIds) {
                boolean isAlreadyParticipant = userChatRoomRepository.existsByUser_UserIdAndChatRoom_RoomId(userId, roomId);
                if (isAlreadyParticipant) continue; 

                User invitedUser = userRepository.findById(userId)
                        .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 유저가 포함되어 있습니다."));

                UserChatRoom userChatRoom = UserChatRoom.builder()
                        .user(invitedUser)
                        .chatRoom(chatRoom)
                        .build();
                userChatRoom.setJoinedAtNow();
                userChatRoom.updateLastReadAt();
                userChatRoomRepository.save(userChatRoom);

                // 📝 "입장 멘트"를 DB에 일반 메시지로 저장
                String noticeText = invitedUser.getNickname() + "님이 입장하셨습니다.";
                Message dbMessage = Message.builder()
                                .chatRoom(chatRoom)
                                .sender(invitedUser) 
                                .message(noticeText)
                                .build();
                messageRepository.save(dbMessage); 

                // 💬 [통로 1] 해당 채팅방 안에 이미 들어와 있는 사람들을 위한 실시간 소켓 알림 (기존 유지)
                ChatMessageDto systemMessage = new ChatMessageDto();
                systemMessage.setType(ChatMessageDto.MessageType.ENTER); 
                systemMessage.setRoomId(String.valueOf(roomId));
                systemMessage.setSender(String.valueOf(invitedUser.getUserId()));
                systemMessage.setSenderNickname(invitedUser.getNickname());
                systemMessage.setMessage(noticeText); 

                String routingKey = "room." + roomId;
                rabbitTemplate.convertAndSend(RabbitConfig.EXCHANGE_NAME, routingKey, systemMessage);

                // 🔔 [통로 2 - 핵심 신설] 알림 전용 서버가 낚아챌 수 있도록 "개인 고유 알림 채널"로 초대장 푸시!
                // 알림 서버의 RabbitMQ가 user.# 패턴을 읽어서 처리하게 됩니다.
                ChatMessageDto inviteNotice = new ChatMessageDto();
                inviteNotice.setType(ChatMessageDto.MessageType.ENTER);
                inviteNotice.setRoomId(String.valueOf(roomId));
                inviteNotice.setSender(String.valueOf(invitedUser.getUserId()));
                inviteNotice.setMessage("✉️ '" + chatRoom.getTitle() + "' 채팅방에 초대되었습니다!");

                String userRoutingKey = "user." + invitedUser.getUserId(); // 예: user.5
                rabbitTemplate.convertAndSend(RabbitConfig.EXCHANGE_NAME, userRoutingKey, inviteNotice);
            }
        }
    }

    /**
     * 🚪 4. 참여 중인 채팅방에서 나가기
     */
    @Transactional
    public void exitChatRoom(Long roomId, Long userId) {
        UserChatRoom userChatRoom = userChatRoomRepository.findByUser_UserIdAndChatRoom_RoomId(userId, roomId)
                .orElseThrow(() -> new IllegalArgumentException("참여하고 있지 않은 채팅방입니다."));

        User leavingUser = userChatRoom.getUser();
        ChatRoom chatRoom = userChatRoom.getChatRoom();

        // 1. 퇴장 메세지 생성 및 DB 저장
        String quitText = leavingUser.getNickname() + "님이 퇴장하셨습니다.";
        Message dbQuitMessage = Message.builder()
                        .chatRoom(chatRoom)
                        .sender(leavingUser)
                        .message(quitText)
                        .build();
        messageRepository.save(dbQuitMessage); 

        // 2. 다른 소켓 서버(파드)들이 알 수 있도록 실시간 퇴장 신호 브로드캐스팅
        ChatMessageDto systemMessage = new ChatMessageDto();
        systemMessage.setType(ChatMessageDto.MessageType.QUIT); 
        systemMessage.setRoomId(String.valueOf(roomId));
        systemMessage.setSender(String.valueOf(leavingUser.getUserId()));
        systemMessage.setSenderNickname(leavingUser.getNickname());
        systemMessage.setMessage(quitText);

        String routingKey = "room." + roomId;
        rabbitTemplate.convertAndSend(RabbitConfig.EXCHANGE_NAME, routingKey, systemMessage);

        // 3. 내 참여 정보 매핑 테이블에서 먼저 제거
        userChatRoomRepository.delete(userChatRoom);

        // 4. 🎯 [정밀 타격] 현재 방에 남은 유저 수를 카운트합니다.
        long remainingUsers = userChatRoomRepository.countByChatRoom_RoomId(roomId);
        
        if (remainingUsers == 0) {
            log.info("🧹 방 번호 #{} 에 더 이상 참여자가 없으므로 데이터를 청소합니다.", roomId);
            
            // 🎯 [핵심 보정] 외래키 제약조건을 피하기 위해, 방을 지우기 전 해당 방의 메시지 이력을 전부 선제 삭제합니다!
            // (만약 ChatRoom 엔티티 내부 List<Message> 필드에 cascade = CascadeType.ALL, orphanRemoval = true 가 설정되어 있다면 생략 가능하지만, 
            // 안전하게 레포지토리 명시적 삭제가 확실합니다.)
            messageRepository.deleteByChatRoom_RoomId(roomId); 
            
            // 이제 족쇄가 풀렸으므로 방을 안전하게 폭파합니다.
            chatRoomRepository.delete(chatRoom);
        }
    }
    
    public record UserSearchResponse(Long userId, String username, String nickname) {}
}