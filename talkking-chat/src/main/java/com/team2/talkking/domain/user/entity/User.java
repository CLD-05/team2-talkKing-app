package com.team2.talkking.domain.user.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "users")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "user_id")
    private Long userId;

    @Column(nullable = false, unique = true, length = 50)
    private String username; // 로그인에 사용할 아이디 (예: email 또는 id)

    @Column(nullable = false, length = 100)
    private String password; // 암호화되어 저장될 비밀번호

    @Column(nullable = false, length = 50)
    private String nickname; // 채팅방에 표시될 닉네임

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();
}