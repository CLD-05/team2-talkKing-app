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
import org.springframework.transaction.annotation.Transactional;

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
     */
    @PostMapping
    @Transactional
    public ResponseEntity<Long> createRoom(
            @RequestHeader("Authorization") String bearerToken,
            @RequestBody CreateRoomRequest request) {
        
        String token = bearerToken.substring(7);
        Long creatorId = jwtProvider.getUserId(token);
        
        List<Long> cleanInvitedUserIds = request.getInvitedUserIds().stream()
                .map(id -> Long.parseLong(String.valueOf(id)))
                .collect(Collectors.toList());

        Long roomId = chatRoomService.createGroupChatRoom(request.getRoomTitle(), creatorId, cleanInvitedUserIds);
        
        chatService.sendInviteNotification(roomId, creatorId, cleanInvitedUserIds, request.getRoomTitle());
        
        return ResponseEntity.ok(roomId);
    }

    /**
     * 📋 현재 로그인한 유저가 참여 중인 채팅방 목록 조회
     * 🎯 [대수술 완료] 컴파일 에러를 유발하고 좀비 배지 버그를 만들던 레거시 로직 전면 폐쇄!
     * 우리가 완벽하게 설계한 chatService의 Redis 최적화 리스트 출력 엔진과 파이프를 다이렉트로 연결합니다.
     */
    @GetMapping
    public ResponseEntity<?> getMyChatRooms(
            @RequestHeader("Authorization") String bearerToken) {
        
        String token = bearerToken.substring(7);
        Long myId = jwtProvider.getUserId(token);
        
        // 🚀 수동 카운팅 뜯어내고 최신 정렬 + Redis 읽음 카운팅 엔진 호출!
        return ResponseEntity.ok(chatService.getMyChatRoomList(myId));
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

        chatRoomService.inviteUsersToExistingRoom(roomId, cleanInvitedUserIds);
        
        String roomTitle = userChatRoomRepository.findByChatRoom_RoomId(roomId)
                .stream()
                .map(mapping -> mapping.getChatRoom().getTitle())
                .findFirst()
                .orElse("그룹 채팅방");
                
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
    public record RoomParticipantResponse(Long userId, String nickname, String username) {}
}