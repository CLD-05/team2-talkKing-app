package com.team2.talkking.domain.chat.repository;

import com.team2.talkking.domain.chat.entity.Message;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional; // 🎯 추가

public interface MessageRepository extends JpaRepository<Message, Long> {
    
    List<Message> findByChatRoomRoomIdOrderByCreatedAtAsc(Long roomId);

    List<Message> findByChatRoomRoomId(Long roomId, Pageable pageable);

    List<Message> findByChatRoom_RoomIdOrderByCreatedAtAsc(Long roomId);

    // 🎯 [교정] 반환 타입을 Optional<Message>로 명확히 고정하고, 성능 신뢰도가 높은 messageId 순으로 정렬합니다.
    Optional<Message> findTopByChatRoom_RoomIdOrderByMessageIdDesc(Long roomId);

    long countByChatRoom_RoomIdAndCreatedAtAfter(Long roomId, LocalDateTime lastReadAt);

    void deleteByChatRoom_RoomId(Long roomId);
    
    Slice<Message> findByChatRoom_RoomId(Long roomId, Pageable pageable);
    
    @Query("SELECT COUNT(m) FROM Message m WHERE m.chatRoom.roomId = :roomId AND m.messageId > :lastReadId")
    long countUnreadMessages(@Param("roomId") Long roomId, @Param("lastReadId") Long lastReadId);

    long countByChatRoom_RoomId(Long roomId);
}