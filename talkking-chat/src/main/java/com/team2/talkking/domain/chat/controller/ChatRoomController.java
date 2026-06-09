package com.team2.talkking.domain.chat.controller;

import com.team2.talkking.domain.chat.repository.UserChatRoomRepository;
import com.team2.talkking.domain.chat.entity.Message;
import com.team2.talkking.domain.chat.repository.MessageRepository;
import com.team2.talkking.domain.chat.service.ChatRoomService;
import com.team2.talkking.domain.chat.service.ChatRoomService.UserSearchResponse;
import com.team2.talkking.domain.chat.service.ChatService;
import com.team2.talkking.global.jwt.JwtProvider;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.transaction.annotation.Transactional; // 🎯 주입 확인

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/chat/rooms")
public class ChatRoomController {

    private final ChatRoomService chatRoomService;
    private final JwtProvider jwtProvider;
    private final UserChatRoomRepository userChatRoomRepository;
    private final MessageRepository messageRepository;
    private final ChatService chatService;

    /**
     * 🔍 닉네임 또는 고유 ID로 유저 하이브리드 검색 (나 자신은 제외)
     */
    @GetMapping("/search")
    public ResponseEntity<List<UserSearchResponse>> searchUsers(
            @RequestHeader("Authorization") String bearerToken,
            @RequestParam("keyword") String keyword) {
        
        String token = bearerToken.substring(7);
        Long myId = jwtProvider.getUserId(token);
        List<UserSearchResponse> result = chatRoomService.searchUsers(keyword, myId);
        return ResponseEntity.ok(result);
    }

    /**
     * ➕ 그룹 채팅방 개설 및 다중 초대
     * 🎯 [보정] 컨트롤러 진입점에 @Transactional을 선언하여 서비스 호출과 알림 레이어를 하나의 트랜잭션 바운더리로 묶습니다.
     */
    @PostMapping
    @Transactional
    public ResponseEntity<Long> createRoom(
            @RequestHeader("Authorization") String bearerToken,
            @RequestBody CreateRoomRequest request) {
        
        String token = bearerToken.substring(7);
        Long creatorId = jwtProvider.getUserId(token);
        
        // 1. DTO 유저 ID 강제 Long 타입 래핑 및 박스갈이 처리 (무결성 최적화)
        List<Long> cleanInvitedUserIds = request.getInvitedUserIds().stream()
                .map(id -> Long.parseLong(String.valueOf(id)))
                .collect(Collectors.toList());

        // 2. 단톡방 개설 및 DB 저장 진행 (트랜잭션 유지 상태)
        Long roomId = chatRoomService.createGroupChatRoom(request.getRoomTitle(), creatorId, cleanInvitedUserIds);
        
        // 3. 이제 활성화된 트랜잭션이 감지되므로 afterCommit() 동기화 엔진이 정상 작동합니다!
        chatService.sendInviteNotification(roomId, creatorId, cleanInvitedUserIds, request.getRoomTitle());
        
        return ResponseEntity.ok(roomId);
    }

    /**
     * 📋 현재 로그인한 유저가 참여 중인 채팅방 목록 조회
     */
    @GetMapping
    public ResponseEntity<List<ChatRoomResponse>> getMyChatRooms(
            @RequestHeader("Authorization") String bearerToken) {
        
        String token = bearerToken.substring(7);
        Long myId = jwtProvider.getUserId(token);
        
        List<com.team2.talkking.domain.chat.entity.UserChatRoom> mappings = 
                userChatRoomRepository.findByUser_UserId(myId);
        
        List<ChatRoomResponse> response = mappings.stream()
                .map(mapping -> {
                    Long roomId = mapping.getChatRoom().getRoomId();
                    String title = mapping.getChatRoom().getTitle();
                    java.time.LocalDateTime lastReadAt = mapping.getLastReadAt();
                    
                    String lastMessage = "아직 대화가 없습니다.";
                    List<com.team2.talkking.domain.chat.entity.Message> lastMsgList = 
                            messageRepository.findTopByChatRoom_RoomIdOrderByCreatedAtDesc(roomId);
                    if (lastMsgList != null && !lastMsgList.isEmpty()) {
                        lastMessage = lastMsgList.get(0).getMessage();
                    }
                    
                    long unreadCount = 0;
                    if (lastReadAt != null) {
                        unreadCount = messageRepository.countByChatRoom_RoomIdAndCreatedAtAfter(roomId, lastReadAt);
                    } else {
                        unreadCount = lastMsgList != null ? lastMsgList.size() : 0; 
                    }
                    
                    return new ChatRoomResponse(roomId, title, lastMessage, unreadCount);
                })
                .collect(java.util.stream.Collectors.toList());
                
        return ResponseEntity.ok(response);
    }

    /**
     * 📜 특정 채팅방의 과거 대화 내역 조회
     */
    @GetMapping("/{roomId}/history-list") 
    public ResponseEntity<List<MessageHistoryResponse>> getChatHistories(
            @PathVariable("roomId") Long roomId,
            @RequestHeader("Authorization") String bearerToken) {
        
        String token = bearerToken.substring(7);
        Long myId = jwtProvider.getUserId(token);
        
        com.team2.talkking.domain.chat.entity.UserChatRoom myMapping = 
                userChatRoomRepository.findByUser_UserIdAndChatRoom_RoomId(myId, roomId)
                .orElseThrow(() -> new IllegalArgumentException("참여 중인 방이 아닙니다."));
                
        java.time.LocalDateTime myJoinTime = myMapping.getJoinedAt();

        List<Message> histories = 
                messageRepository.findByChatRoom_RoomIdOrderByCreatedAtAsc(roomId);
        
        List<MessageHistoryResponse> response = histories.stream()
                .filter(msg -> {
                    if (myJoinTime == null) return false; 
                    return msg.getCreatedAt().isAfter(myJoinTime) || msg.getCreatedAt().isEqual(myJoinTime);
                })
                .map(msg -> {
                    String type = "TALK";
                    if (msg.getMessage().endsWith("입장하셨습니다.")) {
                        type = "ENTER";
                    } else if (msg.getMessage().endsWith("퇴장하셨습니다.")) {
                        type = "QUIT";
                    }
                    
                    return new MessageHistoryResponse(
                            msg.getSender().getUserId(),
                            msg.getSender().getNickname(), 
                            msg.getMessage(),
                            type
                    );
                })
                .collect(java.util.stream.Collectors.toList());
                
        return ResponseEntity.ok(response);
    }
    
    /**
     * 👥 기존 채팅방에 신규 멤버 초대 API
     * 🎯 [보정] 방 생성과 마찬가지로 트랜잭션을 걸어 딜레이 타이밍 문제를 원천 차단합니다.
     */
    @PostMapping("/{roomId}/invite")
    @Transactional
    public ResponseEntity<String> inviteUsers(
            @PathVariable("roomId") Long roomId,
            @RequestHeader("Authorization") String bearerToken, 
            @RequestBody InviteUserRequest request) {
        
        String token = bearerToken.substring(7);
        Long inviterId = jwtProvider.getUserId(token);
        
        List<Long> cleanInvitedUserIds = request.getInvitedUserIds().stream()
                .map(id -> Long.parseLong(String.valueOf(id)))
                .collect(Collectors.toList());

        // 1. 기존 방에 멤버들 영속화 진행
        chatRoomService.inviteUsersToExistingRoom(roomId, cleanInvitedUserIds);
        
        // 2. 방 제목 파싱
        String roomTitle = userChatRoomRepository.findByChatRoom_RoomId(roomId)
                .stream()
                .map(mapping -> mapping.getChatRoom().getTitle())
                .findFirst()
                .orElse("그룹 채팅방");
                
        // 3. 커밋 완료 후 안전 발송 동기화 가동
        chatService.sendInviteNotification(roomId, inviterId, cleanInvitedUserIds, roomTitle);
        
        return ResponseEntity.ok("초대가 완료되었습니다.");
    }

    /**
     * 👁️‍🗨️ 특정 채팅방을 확인했을 때 내 last_read_at 시간 UPDATE
     */
    @PostMapping("/{roomId}/read")
    public ResponseEntity<String> updateLastRead(
            @PathVariable("roomId") Long roomId,
            @RequestHeader("Authorization") String bearerToken) {
        
        String token = bearerToken.substring(7);
        Long myId = jwtProvider.getUserId(token);
        
        com.team2.talkking.domain.chat.entity.UserChatRoom mapping = 
                userChatRoomRepository.findByUser_UserIdAndChatRoom_RoomId(myId, roomId)
                .orElseThrow(() -> new IllegalArgumentException("참여 중인 방이 아닙니다."));
                
        mapping.updateLastReadAt(); 
        userChatRoomRepository.save(mapping); 
        
        return ResponseEntity.ok("읽음 처리 완료");
    }

    /**
     * 👥 특정 채팅방에 현재 참여 중인 유저 목록 조회 (우측 사이드바용)
     */
    @GetMapping("/{roomId}/users")
    public ResponseEntity<List<RoomParticipantResponse>> getRoomParticipants(
            @PathVariable("roomId") Long roomId) {
        
        List<com.team2.talkking.domain.chat.entity.UserChatRoom> mappings = 
                userChatRoomRepository.findByChatRoom_RoomId(roomId);
        
        List<RoomParticipantResponse> response = mappings.stream()
                .map(mapping -> new RoomParticipantResponse(
                        mapping.getUser().getUserId(),
                        mapping.getUser().getNickname(),
                        mapping.getUser().getUsername()
                    ))
                .collect(java.util.stream.Collectors.toList());
                
        return ResponseEntity.ok(response);
    }

    /**
     * 🚪 참여 중인 채팅방 나가기 API
     */
    @DeleteMapping("/{roomId}/exit")
    public ResponseEntity<String> exitRoom(
            @PathVariable("roomId") Long roomId,
            @RequestHeader("Authorization") String bearerToken) {
        
        String token = bearerToken.substring(7);
        Long myId = jwtProvider.getUserId(token);
        
        chatRoomService.exitChatRoom(roomId, myId);
        return ResponseEntity.ok("채팅방을 성공적으로 나갔습니다.");
    }
    
    // --- DTO 묶음 정의 ---
    @Getter
    @NoArgsConstructor
    public static class CreateRoomRequest {
        private String roomTitle;
        private List<Long> invitedUserIds;
    }

    @Getter
    @NoArgsConstructor
    public static class InviteUserRequest {
        private List<Long> invitedUserIds;
    }

    public record MessageHistoryResponse(Long senderId, String senderNickname, String message, String type) {}
    public record ChatRoomResponse(Long roomId, String title, String lastMessage, long unreadCount) {}
    public record RoomParticipantResponse(Long userId, String nickname, String username) {}
}