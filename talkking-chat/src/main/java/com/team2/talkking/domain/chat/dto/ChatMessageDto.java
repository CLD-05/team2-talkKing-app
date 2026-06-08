package com.team2.talkking.domain.chat.dto;

import com.team2.talkking.domain.chat.entity.ChatRoom;
import com.team2.talkking.domain.chat.entity.Message;
import com.team2.talkking.domain.user.entity.User;
import lombok.*;

import java.time.LocalDateTime;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatMessageDto {

    public enum MessageType {
        ENTER, TALK, QUIT
    }

    private MessageType type;    
    private String roomId;       
    private String sender;       // 🎯 여기에 유저의 고유 ID(숫자값)를 담으세요!
    private String senderNickname; // 화면 표시용 닉네임 (선택 사항)
    private String message;      
    private String createdAt;   
    
    public Message toEntity(ChatRoom chatRoom, User sender) {
        return Message.builder()
                .chatRoom(chatRoom)
                .sender(sender)
                .message(this.message)
                .createdAt(LocalDateTime.now()) // 🎯 DB 저장 시점의 서버 시간을 정확하게 기록해 줍니다.
                .build();
    }
}