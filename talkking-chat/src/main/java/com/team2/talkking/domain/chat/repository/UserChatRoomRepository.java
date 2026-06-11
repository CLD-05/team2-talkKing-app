package com.team2.talkking.domain.chat.repository;

import com.team2.talkking.domain.chat.entity.UserChatRoom;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface UserChatRoomRepository extends JpaRepository<UserChatRoom, Long> {

    // 💡 특정 유저가 속한 채팅방 목록을 전부 조회할 때 사용 (메인 화면용)
    List<UserChatRoom> findByUser_UserId(Long userId);

    boolean existsByUser_UserIdAndChatRoom_RoomId(Long userId, Long roomId);

    Optional<UserChatRoom> findByUser_UserIdAndChatRoom_RoomId(Long myId, Long roomId);

    List<UserChatRoom> findByChatRoom_RoomId(Long roomId);

    long countByChatRoom_RoomId(Long roomId);

    List<UserChatRoom> findAllByChatRoom_RoomId(Long roomId);
    
    // 🎯 saveAndRefreshRoom 에서 사용: 방 ID와 유저 ID로 특정 매핑 정보 단건 조회
    Optional<UserChatRoom> findByChatRoom_RoomIdAndUser_UserId(Long roomId, Long userId);

 // 🎯 [수정] lastMessageAt이 NULL인 방도 고려하고, 완벽하게 내림차순 정렬되도록 JPQL 쿼리 직접 지정
    @Query("SELECT ucr FROM UserChatRoom ucr " +
           "JOIN FETCH ucr.chatRoom cr " +
           "WHERE ucr.user.userId = :userId " +
           "ORDER BY COALESCE(cr.lastMessageAt, cr.createdAt) DESC")
    List<UserChatRoom> findByUser_UserIdOrderByChatRoom_LastMessageAtDesc(@Param("userId") Long userId);
}