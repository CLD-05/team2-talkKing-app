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
        ENTER, TALK, QUIT, READ 
    }

    // 🎯 [추가] 읽음 책갈피 기준선 처리를 위해 메시지의 고유 PK ID를 필드로 추가합니다.
    private Long messageId; 

    private MessageType type;    
    private String roomId;       
    private String sender;       
    private String senderNickname; 
    private String message;      
    private String createdAt;   
    
    public Message toEntity(ChatRoom chatRoom, User sender, LocalDateTime now) {
        return Message.builder()
                .chatRoom(chatRoom)
                .sender(sender)
                .message(this.message)
                .createdAt(now)
                .build();
    }
}