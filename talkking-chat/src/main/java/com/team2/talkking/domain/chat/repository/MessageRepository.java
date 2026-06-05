package com.team2.talkking.domain.chat.repository;

import com.team2.talkking.domain.chat.entity.Message;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface MessageRepository extends JpaRepository<Message, Long> {
    // 2주차 과거 메시지 내역 조회 API용 쿼리 메서드 틀
    List<Message> findByChatRoomRoomIdOrderByCreatedAtAsc(Long roomId);
}