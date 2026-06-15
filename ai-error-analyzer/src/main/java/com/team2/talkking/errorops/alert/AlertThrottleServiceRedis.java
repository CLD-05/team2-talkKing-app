package com.team2.talkking.errorops.alert;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import java.util.concurrent.TimeUnit;

/**
 * Redis 기반 Alert 스로틀링 서비스
 * 같은 alert이 10분 내에 반복되면 Slack 알림을 차단합니다.
 * workload 기반으로 중복 제거하므로 Pod이 바뀌어도 같은 서비스 장애로 인식합니다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AlertThrottleServiceRedis {

    private final StringRedisTemplate redisTemplate;
    
    private static final long THROTTLE_MINUTES = 10;
    private static final String KEY_PREFIX = "alert:throttle:";

    /**
     * Alert을 지금 보낼 수 있는지 확인 (Workload 기반)
     * @param alertName alert 이름
     * @param namespace 네임스페이스
     * @param workload workload 이름 (deployment/statefulset 등)
     * @param container 컨테이너 이름
     * @return true면 알림 가능, false면 10분 내에 이미 보냈음
     */
    public boolean canNotify(String alertName, String namespace, String workload, String container) {
        if (alertName == null || alertName.isBlank() || 
            namespace == null || namespace.isBlank() ||
            workload == null || workload.isBlank() ||
            container == null || container.isBlank()) {
            log.warn("Empty alertName, namespace, workload, or container provided");
            return true;
        }
        
        String key = buildKey(alertName, namespace, workload, container);
        
        try {
            Boolean hasKey = redisTemplate.hasKey(key);
            
            if (hasKey == null) {
                log.warn("Redis returned null for key: {}", key);
                return true;
            }
            
            boolean shouldNotify = !hasKey;
            log.debug("Alert {}:{}:{}:{} canNotify: {}", alertName, namespace, workload, container, shouldNotify);
            
            return shouldNotify;
            
        } catch (Exception e) {
            log.error("Error checking alert throttle for alertName: {}, namespace: {}, workload: {}, container: {}", 
                alertName, namespace, workload, container, e);
            return true;
        }
    }

    /**
     * Alert 알림 시간을 Redis에 기록
     * @param alertName alert 이름
     * @param namespace 네임스페이스
     * @param workload workload 이름
     * @param container 컨테이너 이름
     */
    public void recordNotification(String alertName, String namespace, String workload, String container) {
        if (alertName == null || alertName.isBlank() || 
            namespace == null || namespace.isBlank() ||
            workload == null || workload.isBlank() ||
            container == null || container.isBlank()) {
            log.warn("Empty alertName, namespace, workload, or container provided");
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
            
            log.info("Alert notification recorded for {}:{}:{}:{} (TTL: {} minutes)", 
                    alertName, namespace, workload, container, THROTTLE_MINUTES);
            
        } catch (Exception e) {
            log.error("Error recording notification for alertName: {}, namespace: {}, workload: {}, container: {}", 
                alertName, namespace, workload, container, e);
        }
    }

    private String buildKey(String alertName, String namespace, String workload, String container) {
        return KEY_PREFIX + alertName + ":" + namespace + ":" + workload + ":" + container;
    }
}