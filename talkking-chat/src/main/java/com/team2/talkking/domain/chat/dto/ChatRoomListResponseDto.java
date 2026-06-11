package com.team2.talkking.domain.chat.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatRoomListResponseDto {
    
    private Long roomId;
    private String title;
    private Boolean isGroup;
    private String lastMessage;
    private String lastMessageAt;
    
    // 🎯 2단계 읽음 확인 기능에서 실시간으로 채워줄 안 읽은 메시지 개수
    private Long unreadCount; 
}