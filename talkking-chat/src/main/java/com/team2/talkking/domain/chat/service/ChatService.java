package com.team2.talkking.domain.chat.service;

import com.team2.talkking.domain.chat.repository.ChatRoomRepository;
import com.team2.talkking.domain.chat.repository.MessageRepository;
import com.team2.talkking.domain.chat.repository.RoomMemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ChatService {

    private final ChatRoomRepository chatRoomRepository;
    private final RoomMemberRepository roomMemberRepository;
    private final MessageRepository messageRepository;

    /**
     * 채팅방 생성 틀 (2주차 구현 예정)
     */
    @Transactional
    public Long createChatRoom() {
        // TODO: 채팅방 생성 비즈니스 로직
        return null;
    }

    /**
     * 메시지 송신 및 저장 틀 (2주차 구현 예정)
     */
    @Transactional
    public void saveMessage() {
        // TODO: 웹소켓으로 수신한 메시지 DB 저장 및 카프카 이벤트 발행 연동
    }
}