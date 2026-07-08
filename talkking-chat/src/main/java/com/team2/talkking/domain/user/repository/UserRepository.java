package com.team2.talkking.domain.user.repository;

import com.team2.talkking.domain.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByUsername(String username);
    boolean existsByUsername(String username);

    // 🎯 [수정] 내 유저 ID(creatorId)를 제외하고, 아이디나 닉네임에 검색어가 포함된 사람만 찾습니다.
    // JPA가 메서드 이름만 보고 복잡한 괄호 지정을 헷갈려할 수 있으므로, 명쾌하게 @Query를 붙여 가독성을 높였습니다.
    @Query("SELECT u FROM User u WHERE (u.username LIKE %:keyword% OR u.nickname LIKE %:keyword%) AND u.userId != :myId")
    List<User> searchUsersExceptMe(@Param("keyword") String keyword, @Param("myId") Long myId);
}