package com.team2.talkking.domain.chat.controller;

import com.team2.talkking.domain.chat.dto.ChatMessageDto;
import com.team2.talkking.domain.chat.service.ChatService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/chat")
public class ChatApiController {

    private final ChatService chatService;

    /**
     * 📅 특정 채팅방의 과거 대화 내역 조회 API
     * 예시 URL: /api/chat/rooms/1/messages?page=0&size=30
     */
    @GetMapping("/rooms/{roomId}/messages")
    public ResponseEntity<List<ChatMessageDto>> getChatHistory(
            @PathVariable("roomId") Long roomId,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "30") int size) {
        
        List<ChatMessageDto> history = chatService.getChatHistory(roomId, page, size);
        return ResponseEntity.ok(history);
    }
}