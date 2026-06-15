package com.team2.talkking.errorops.alert;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import java.util.concurrent.TimeUnit;

/**
 * Redis 기반 Alert 스로틀링 서비스
 * 같은 alert이 10분 내에 반복되면 Slack 알림을 차단합니다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AlertThrottleServiceRedis {

    private final StringRedisTemplate redisTemplate;
    
    private static final long THROTTLE_MINUTES = 10;
    private static final String KEY_PREFIX = "alert:throttle:";

    /**
     * Alert을 지금 보낼 수 있는지 확인 (Pod 변경 무관)
     * @param alertName alert 이름
     * @param namespace 네임스페이스
     * @return true면 알림 가능, false면 10분 내에 이미 보냈음
     */
    public boolean canNotify(String alertName, String namespace) {
        if (alertName == null || alertName.isBlank() || namespace == null || namespace.isBlank()) {
            log.warn("Empty alertName or namespace provided");
            return true;
        }
        
        String key = buildKey(alertName, namespace);
        
        try {
            Boolean hasKey = redisTemplate.hasKey(key);
            
            if (hasKey == null) {
                log.warn("Redis returned null for key: {}", key);
                return true;
            }
            
            boolean shouldNotify = !hasKey;
            log.debug("Alert {}:{} canNotify: {}", alertName, namespace, shouldNotify);
            
            return shouldNotify;
            
        } catch (Exception e) {
            log.error("Error checking alert throttle for alertName: {}, namespace: {}", alertName, namespace, e);
            return true;
        }
    }

    /**
     * Alert 알림 시간을 Redis에 기록
     * @param alertName alert 이름
     * @param namespace 네임스페이스
     */
    public void recordNotification(String alertName, String namespace) {
        if (alertName == null || alertName.isBlank() || namespace == null || namespace.isBlank()) {
            log.warn("Empty alertName or namespace provided");
            return;
        }
        
        String key = buildKey(alertName, namespace);
        
        try {
            redisTemplate.opsForValue().set(
                    key,
                    String.valueOf(System.currentTimeMillis()),
                    THROTTLE_MINUTES,
                    TimeUnit.MINUTES
            );
            
            log.info("Alert notification recorded for {}:{} (TTL: {} minutes)", 
                    alertName, namespace, THROTTLE_MINUTES);
            
        } catch (Exception e) {
            log.error("Error recording notification for alertName: {}, namespace: {}", alertName, namespace, e);
        }
    }

    private String buildKey(String alertName, String namespace) {
        return KEY_PREFIX + alertName + ":" + namespace;
    }
}