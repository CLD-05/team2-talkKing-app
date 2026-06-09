package com.team2.talkking.domain.chat.controller;

import com.team2.talkking.domain.chat.repository.UserChatRoomRepository;
import com.team2.talkking.domain.chat.entity.Message;
import com.team2.talkking.domain.chat.repository.MessageRepository;
import com.team2.talkking.domain.chat.service.ChatRoomService;
import com.team2.talkking.domain.chat.service.ChatRoomService.UserSearchResponse;
import com.team2.talkking.global.jwt.JwtProvider;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/chat/rooms")
public class ChatRoomController {

    private final ChatRoomService chatRoomService;
    private final JwtProvider jwtProvider;
    private final UserChatRoomRepository userChatRoomRepository;
    private final MessageRepository messageRepository;

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
     */
    @PostMapping
    public ResponseEntity<Long> createRoom(
            @RequestHeader("Authorization") String bearerToken,
            @RequestBody CreateRoomRequest request) {
        
        String token = bearerToken.substring(7);
        Long creatorId = jwtProvider.getUserId(token);
        Long roomId = chatRoomService.createGroupChatRoom(request.getRoomTitle(), creatorId, request.getInvitedUserIds());
        
        return ResponseEntity.ok(roomId);
    }

    /**
     * 📋 현재 로그인한 유저가 참여 중인 채팅방 목록 조회 (DB 안 읽은 카운트 100% 반영)
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
                    
                    // 1. 최근 메시지 추출
                    String lastMessage = "아직 대화가 없습니다.";
                    List<com.team2.talkking.domain.chat.entity.Message> lastMsgList = 
                            messageRepository.findTopByChatRoom_RoomIdOrderByCreatedAtDesc(roomId);
                    if (lastMsgList != null && !lastMsgList.isEmpty()) {
                        lastMessage = lastMsgList.get(0).getMessage();
                    }
                    
                    // 2. 내 마지막 접속 시간 이후로 생성된 메시지 개수 카운트!
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
     * 📜 특정 채팅방의 과거 대화 내역 조회 (🎯 내 진짜 가입 시간 이후 필터링 + 시스템 메시지 분기 탑재)
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
                    // 🎯 [핵심 추가] 텍스트가 끝나는 패턴을 분석하여 메시지 타입을 동적으로 판별합니다.
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
                            type // 👈 생성자 파라미터에 type 추가 탑재
                    );
                })
                .collect(java.util.stream.Collectors.toList());
                
        return ResponseEntity.ok(response);
    }
    
    /**
     * 👥 기존 채팅방에 신규 멤버 초대 API
     */
    @PostMapping("/{roomId}/invite")
    public ResponseEntity<String> inviteUsers(
            @PathVariable("roomId") Long roomId,
            @RequestBody InviteUserRequest request) {
        
        chatRoomService.inviteUsersToExistingRoom(roomId, request.getInvitedUserIds());
        return ResponseEntity.ok("초대가 완료되었습니다.");
    }

    /**
     * 👁️‍🗨️ [신설 추가] 특정 채팅방을 확인했을 때 내 last_read_at 시간을 현재 시간으로 UPDATE 하는 API
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
     * 👥 [신설 추가] 특정 채팅방에 현재 참여 중인 유저 목록 조회 (우측 사이드바용)
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

    // 🎯 [DTO 업데이트] 프론트엔드가 ENTER/QUIT/TALK 레이아웃 분기를 태울 수 있게 type 필드 전격 확장
    public record MessageHistoryResponse(Long senderId, String senderNickname, String message, String type) {}
    public record ChatRoomResponse(Long roomId, String title, String lastMessage, long unreadCount) {}
    public record RoomParticipantResponse(Long userId, String nickname, String username) {}
}