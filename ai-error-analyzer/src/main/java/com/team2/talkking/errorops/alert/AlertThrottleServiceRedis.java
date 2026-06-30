package com.team2.talkking.errorops.alert;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import java.util.concurrent.TimeUnit;

/**
 * Redis 기반 Alert 스로틀링 서비스
 * 같은 alert이 30분 내에 반복되면 Slack 알림을 차단합니다.
 * alertName + namespace + workload + container 기반으로 중복 제거하므로
 * Pod이 바뀌어도 같은 workload 장애로 인식합니다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AlertThrottleServiceRedis {

    private final StringRedisTemplate redisTemplate;
    
    private static final long THROTTLE_MINUTES = 30;
    private static final String KEY_PREFIX = "alert:throttle:";

    /**
     * Alert을 지금 보낼 수 있는지 확인
     * alertName만 필수, 나머지는 기본값으로 처리
     */
    public boolean canNotify(String alertName, String namespace, String workload, String container) {
        // alertName이 없으면 스로틀링 불가능 (Fail-Open)
        if (alertName == null || alertName.isBlank()) {
            log.warn("AlertName is missing. Bypassing throttle.");
            return true;
        }
        
        String key = buildKey(alertName, namespace, workload, container);
        
        try {
            // Redis에 키가 없으면(=처음이거나 TTL 만료) true 반환
            return !Boolean.TRUE.equals(redisTemplate.hasKey(key));
        } catch (Exception e) {
            log.error("Redis connection failed. Bypassing throttle. Key: {}", key, e);
            return true; // Redis 장애 시 알림이 가도록 (Fail-Open)
        }
    }

    /**
     * Alert 알림 시간을 Redis에 기록
     */
    public void recordNotification(String alertName, String namespace, String workload, String container) {
        if (alertName == null || alertName.isBlank()) {
            log.warn("AlertName is missing. Cannot record notification.");
            return;
        }
        
        String key = buildKey(alertName, namespace, workload, container);
        
        try {
            redisTemplate.opsForValue().set(
                    key,
                    String.valueOf(System.currentTimeMillis()),
                    THROTTLE_MINUTES,
                    TimeUnit.MINUTES
            );
            
            log.info("Alert notification recorded for [{}] (TTL: {} minutes)", key, THROTTLE_MINUTES);
            
        } catch (Exception e) {
            log.error("Failed to record throttle in Redis for key: {}", key, e);
        }
    }

    /**
     * Redis 키 생성
     * namespace, workload, container가 없으면 기본값 사용
     */
    private String buildKey(String alertName, String namespace, String workload, String container) {
        String safeNamespace = (namespace == null || namespace.isBlank()) ? "default" : namespace;
        String safeWorkload = (workload == null || workload.isBlank()) ? "unknown" : workload;
        String safeContainer = (container == null || container.isBlank()) ? "unknown" : container;
        
        return KEY_PREFIX + alertName + ":" + safeNamespace + ":" + safeWorkload + ":" + safeContainer;
    }
}