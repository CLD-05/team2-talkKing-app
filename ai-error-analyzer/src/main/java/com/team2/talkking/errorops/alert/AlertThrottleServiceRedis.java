package com.team2.talkking.errorops.alert;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import java.util.concurrent.TimeUnit;

/**
 * Redis 기반 Alert 스로틀링 서비스 (원자적 처리)
 * setIfAbsent를 사용하여 race condition 완전 제거
 * 같은 alert이 30분 내에 반복되면 Slack 알림을 차단합니다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AlertThrottleServiceRedis {

    private final StringRedisTemplate redisTemplate;
    
    private static final long THROTTLE_MINUTES = 30;
    private static final String KEY_PREFIX = "alert:throttle:";

    /**
     * ✅ Alert을 지금 보낼 수 있는지 확인하고, 보낼 수 있다면 즉시 30분 TTL을 기록합니다.
     * (원자적 처리 - Race Condition 완전 제거)
     * 
     * @return true: 처음 발생한 알럿 (Slack 전송 O)
     *         false: 30분 내 중복 알럿 (Slack 전송 X)
     */
    public boolean checkAndRecordThrottle(String alertName, String namespace, String workload, String container) {
        if (alertName == null || alertName.isBlank()) {
            log.warn("AlertName is missing. Bypassing throttle.");
            return true; // Fail-Open
        }

        String key = buildKey(alertName, namespace, workload, container);

        try {
            // ✅ setIfAbsent (Redis SETNX): 
            // 키가 없으면 생성(true 반환) + TTL 설정,
            // 이미 있으면 무시(false 반환)
            // 이 모든 동작이 Redis 원자적으로 처리됨!
            Boolean isFirstAlert = redisTemplate.opsForValue().setIfAbsent(
                    key,
                    String.valueOf(System.currentTimeMillis()),
                    THROTTLE_MINUTES,
                    TimeUnit.MINUTES
            );

            // null 체크 (Spring Data Redis 특성상 null이 반환될 수 있음)
            if (Boolean.TRUE.equals(isFirstAlert)) {
                log.info("✅ Alert notification permitted for [{}] (TTL: {} minutes)", 
                    key, THROTTLE_MINUTES);
                return true;  // 처음 발생한 알럿이므로 알림 전송 O
            } else {
                log.info("⏸️ Alert notification throttled for [{}] (already notified within {} minutes)", 
                    key, THROTTLE_MINUTES);
                return false; // 30분 내 중복이므로 알림 전송 X
            }

        } catch (Exception e) {
            log.error("Redis connection failed. Bypassing throttle. Key: {}", key, e);
            return true; // Redis 장애 시 알림이 가도록 (Fail-Open)
        }
    }

    /**
     * Redis 키 생성
     * alertName:namespace:workload:container 형식
     */
    private String buildKey(String alertName, String namespace, String workload, String container) {
        String safeNamespace = (namespace == null || namespace.isBlank()) ? "default" : namespace;
        String safeWorkload = (workload == null || workload.isBlank()) ? "unknown" : workload;
        String safeContainer = (container == null || container.isBlank()) ? "unknown" : container;
        
        return KEY_PREFIX + alertName + ":" + safeNamespace + ":" + safeWorkload + ":" + safeContainer;
    }
}