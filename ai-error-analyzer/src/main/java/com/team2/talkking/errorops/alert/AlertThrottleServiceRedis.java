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
     * Alert을 지금 보낼 수 있는지 확인
     * @param fingerprint alert의 고유 ID
     * @return true면 알림 가능, false면 10분 내에 이미 보냈음
     */
    public boolean canNotify(String fingerprint) {
        if (fingerprint == null || fingerprint.isBlank()) {
            log.warn("Empty fingerprint provided");
            return true;
        }
        
        String key = buildKey(fingerprint);
        
        try {
            Boolean hasKey = redisTemplate.hasKey(key);
            
            if (hasKey == null) {
                log.warn("Redis returned null for key: {}", key);
                return true;
            }
            
            boolean shouldNotify = !hasKey;
            log.debug("Alert {} canNotify: {}", fingerprint, shouldNotify);
            
            return shouldNotify;
            
        } catch (Exception e) {
            log.error("Error checking alert throttle for fingerprint: {}", fingerprint, e);
            return true;  // Redis 오류 시 안전하게 알림 보냄
        }
    }

    /**
     * Alert 알림 시간을 Redis에 기록
     * @param fingerprint alert의 고유 ID
     */
    public void recordNotification(String fingerprint) {
        if (fingerprint == null || fingerprint.isBlank()) {
            log.warn("Empty fingerprint provided");
            return;
        }
        
        String key = buildKey(fingerprint);
        
        try {
            redisTemplate.opsForValue().set(
                    key,
                    String.valueOf(System.currentTimeMillis()),
                    THROTTLE_MINUTES,
                    TimeUnit.MINUTES
            );
            
            log.info("Alert notification recorded for fingerprint: {} (TTL: {} minutes)", 
                    fingerprint, THROTTLE_MINUTES);
            
        } catch (Exception e) {
            log.error("Error recording notification for fingerprint: {}", fingerprint, e);
        }
    }

    private String buildKey(String fingerprint) {
        return KEY_PREFIX + fingerprint;
    }
}