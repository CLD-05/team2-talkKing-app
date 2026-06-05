package com.team2.talkking.domain.user.service;

import com.team2.talkking.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserService {

    private final UserRepository userRepository;

    /**
     * 회원가입 기능 틀 (2주차 구현 예정)
     */
    @Transactional
    public Long register() {
        // TODO: 회원가입 및 비밀번호 암호화 로직 구현
        return null;
    }
}