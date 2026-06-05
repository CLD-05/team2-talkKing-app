package com.team2.talkking.domain.chat.dto;

import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatMessageDto {

    // 💡 메시지 타입: 입장, 채팅, 퇴장
    public enum MessageType {
        ENTER, TALK, QUIT
    }

    private MessageType type;    // 메시지 타입
    private String roomId;       // 방 번호
    private String sender;       // 보내는 사람 (닉네임 또는 유저 ID)
    private String message;      // 메시지 내용
    private String createdAt;    // 생성 시간 (예: yyyy-MM-dd HH:mm:ss)
}