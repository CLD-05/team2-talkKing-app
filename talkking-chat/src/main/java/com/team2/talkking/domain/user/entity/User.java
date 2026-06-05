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
    private Long userId; // 유저 고유 식별자

    @Column(name = "email", nullable = false, unique = true, length = 100)
    private String email; // 로그인 아이디 및 이메일 알림용

    @Column(name = "password", nullable = false)
    private String password; // 암호화된 비밀번호

    @Column(name = "nickname", nullable = false, length = 50)
    private String nickname; // 메신저 내 표시될 이름

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now(); // 가입 일시
}