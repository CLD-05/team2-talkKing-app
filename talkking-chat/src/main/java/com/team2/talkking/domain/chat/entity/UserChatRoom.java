package com.team2.talkking.domain.chat.entity;

import java.time.LocalDateTime;

import com.team2.talkking.domain.user.entity.User;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "user_chat_rooms")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class UserChatRoom {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "user_chat_room_id")
    private Long id;

    // 👤 어떤 유저가 방에 들어가 있는지
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    // 💬 어느 채팅방에 들어가 있는지
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "room_id", nullable = false) // 🎯 기존 ChatRoom의 room_id와 매핑
    private ChatRoom chatRoom;
    
 // UserChatRoom.java 내부
    @Column(name = "last_read_at")
    private LocalDateTime lastReadAt; // 🎯 [추가] 마지막으로 대화방을 확인한 시간
    
    @Column(name = "joined_at")
    private LocalDateTime joinedAt;

    // 엔티티 내부나 Builder 쪽에 기본값 세팅을 위해 추가하거나, 방 최초 참여시 LocalDateTime.now()가 들어가게 조율합니다.
    public void updateLastReadAt() {
        this.lastReadAt = LocalDateTime.now();
    }
    
    public void setJoinedAtNow() {
        this.joinedAt = LocalDateTime.now();
    }
}