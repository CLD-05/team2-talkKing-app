package com.team2.talkking.domain.chat.repository;

import com.team2.talkking.domain.chat.entity.RoomMember;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface RoomMemberRepository extends JpaRepository<RoomMember, Long> {
    // 특정 채팅방에 속한 멤버 목록을 조회하기 위한 틀
    List<RoomMember> findByChatRoomRoomId(Long roomId);
}