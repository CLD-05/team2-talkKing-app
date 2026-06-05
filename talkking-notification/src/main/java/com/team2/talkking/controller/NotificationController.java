package com.team2.talkking.controller;

import com.team2.talkking.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    /**
     * 🔔 유저가 실시간 푸시 알림을 받기 위해 SSE(Server-Sent Events) 연결을 맺는 진입점
     * (3주차 카프카/SSE 파트에서 로직 구현 예정)
     */
    @GetMapping(value = "/subscribe/{userId}", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter subscribe(@PathVariable Long userId) {
        // TODO: SSE emitter 생성 및 맵 관리 로직 구현
        return null;
    }
}