package com.team2.talkking.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    /**
     * 💬 채팅 발생 이벤트를 수신해서 처리하는 핵심 로직 틀 (3주차 구현)
     */
    public void sendChatNotification(Long roomId, Long senderId, String messageText) {
        log.info("알림 이벤트를 처리합니다. 방번호: {}, 발신자: {}, 메시지: {}", roomId, senderId, messageText);
        // TODO: 특정 방에 속한 멤버들을 찾아서 SSE 푸시 알림 및 이메일 발송 연동
    }
}