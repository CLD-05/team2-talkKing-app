package com.team2.talkking.domain.chat.repository;

import com.team2.talkking.domain.chat.entity.UserChatRoom;
import org.springframework.data.jpa.repository.JpaRepository;
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
}