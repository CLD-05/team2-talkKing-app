package com.team2.talkking.domain.user.service;

import com.team2.talkking.domain.user.entity.User;
import com.team2.talkking.domain.user.repository.UserRepository;
import com.team2.talkking.global.jwt.JwtProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtProvider jwtProvider;

    /**
     * 📝 회원가입 비즈니스 로직
     */
    @Transactional
    public void signUp(String username, String password, String nickname) {
        // 1. 아이디 중복 체크
        if (userRepository.existsByUsername(username)) {
            throw new IllegalArgumentException("이미 존재하는 아이디입니다.");
        }

        // 2. 비밀번호 해시 암호화 진행 (BCrypt 적용)
        String encodedPassword = passwordEncoder.encode(password);

        // 3. 빌더 패턴으로 유저 엔티티 생성 후 DB 세이브
        User user = User.builder()
                .username(username)
                .password(encodedPassword)
                .nickname(nickname)
                .build();

        userRepository.save(user);
    }

    /**
     * 🔐 로그인 비즈니스 로직 (성공 시 JWT 토큰 반환)
     */
    @Transactional(readOnly = true)
    public String login(String username, String password) {
        // 1. 아이디 존재 여부 확인
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 아이디입니다."));

        // 2. 암호화된 비밀번호 대조 (matches 메서드 필수!)
        if (!passwordEncoder.matches(password, user.getPassword())) {
            throw new IllegalArgumentException("비밀번호가 일치하지 않습니다.");
        }

        // 3. 로그인 성공! 유저 고유 ID(PK)와 닉네임을 품은 JWT 토큰 발행
        return jwtProvider.createAccessToken(user.getUserId(), user.getUsername(), user.getNickname());
    }
}