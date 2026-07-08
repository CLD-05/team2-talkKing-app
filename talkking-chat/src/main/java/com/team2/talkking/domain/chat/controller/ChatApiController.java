package com.team2.talkking.domain.chat.controller;

import org.springframework.data.domain.Slice;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.team2.talkking.domain.chat.dto.ChatMessageDto;
import com.team2.talkking.domain.chat.service.ChatService;

@RestController
public class ChatApiController {

    private final ChatService chatService;

    public ChatApiController(ChatService chatService) {
        this.chatService = chatService;
    }

    /**
     * 💬 특정 채팅방의 과거 대화 내역 페이징 조회 API
     * 프론트엔드에서 /api/chat/rooms/1/messages?page=0&size=20 형태로 호출하게 됩니다.
     */
    @GetMapping("/api/chat/rooms/{roomId}/messages")
    public ResponseEntity<Slice<ChatMessageDto>> getChatHistory(
            @PathVariable Long roomId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        
        // 🎯 서비스에서 가공해온 Slice(hasNext 정보 포함)를 그대로 JSON으로 내려줍니다.
        Slice<ChatMessageDto> history = chatService.getChatHistory(roomId, page, size);
        return ResponseEntity.ok(history);
    }
}