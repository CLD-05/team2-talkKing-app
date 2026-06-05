package com.team2.talkking.domain.user.repository;

import com.team2.talkking.domain.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    // 2주차 JWT 로그인 및 회원가입 시 중복 검사를 위한 쿼리 메서드 틀
    Optional<User> findByEmail(String email);
    boolean existsByEmail(String email);
}