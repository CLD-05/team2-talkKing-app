package com.team2.talkking.domain.chat.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@NoArgsConstructor
@ToString
public class ChatMessageDto {

    // 🎯 메시지 성격을 규정하는 열거형(Enum)
    private MessageType type;      // ENTER, QUIT, TALK 등
    private String roomId;         // 알림이 발생한 채팅방 번호
    private String sender;         // 보낸 사람 (알림 서버에서는 타겟 유저 ID 숫자가 담김)
    private String senderNickname; // 보낸 사람의 닉네임 (예: 홍길동)
    private String message;        // 🔔 화면에 뿌려질 실제 알림 문구 (예: 'XX방'에 초대되었습니다!)

    /**
     * 🏷️ 채팅 서버와 100% 싱크를 맞춘 메시지 타입 이넘(Enum)
     */
    public enum MessageType {
        ENTER,  // 입장 및 초대 알림
        QUIT,  // 퇴장 알림
        TALK   // 일반 메시지 알림
    }
}