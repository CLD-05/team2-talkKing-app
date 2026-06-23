package com.team2.talkking.errorops.history;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * 🎯 100% 자가 치유형 파티션 로테이터 (Partition Rotator)
 * 역할: 매월 말일 작동하여 다음 달 파티션을 대기시키고, 3개월이 지난 과거 파티션은 영구 삭제하여 용량 확보.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PartitionTableRotator {

    private final JdbcTemplate jdbcTemplate;

    // 🚀 보존 기한 설정 (3개월 뒤 자동으로 테이블을 파괴하도록 제어)
    private static final int RETENTION_MONTHS = 3; 

    /**
     * ⏰ 매월 25일 새벽 3시에 자동으로 실행되는 스케줄러 
     * (cron = "0 0 3 25 * *")
     */
    @Scheduled(cron = "0 0 3 25 * *")
    public void rotatePartitions() {
        log.info("⏰ [자동 파티션 로테이션 정기 스케줄러 가동] 미래 파티션 예약 및 과거 데이터 정리 시작...");
        
        try {
            // 1. 다음 달 파티션 미리 예약 생성 (다음 달 방 대기)
            LocalDate nextMonth = LocalDate.now().plusMonths(1);
            createFuturePartition(nextMonth);

            // 2. 보존 기한이 지난 과거 파티션 테이블 영구 삭제 (디스크 용량 회수)
            LocalDate expiredMonth = LocalDate.now().minusMonths(RETENTION_MONTHS);
            dropExpiredPartition(expiredMonth);

            log.info("✅ [자동 파티션 로테이션 완료] 인프라 자가 최적화 성공.");
        } catch (Exception e) {
            log.error("❌ [파티션 로테이션 실패] 인프라 스케줄러 실행 중 예외 발생", e);
        }
    }

    /**
     * 미래의 파티션 방을 동적으로 선점 생성
     */
    private void createFuturePartition(LocalDate date) {
        String suffix = date.format(DateTimeFormatter.ofPattern("yyyy_MM"));
        String startRange = date.withDayOfMonth(1).format(DateTimeFormatter.ofPattern("yyyy-MM-01 00:00:00"));
        String endRange = date.withDayOfMonth(1).plusMonths(1).format(DateTimeFormatter.ofPattern("yyyy-MM-01 00:00:00"));
        String tableName = "alert_history_" + suffix;

        String sql = """
            CREATE TABLE IF NOT EXISTS %s PARTITION OF alert_history
            FOR VALUES FROM ('%s') TO ('%s');
            """.formatted(tableName, startRange, endRange);

        jdbcTemplate.execute(sql);
        log.info("📂 [미래 파티션 예약 생성 성공] 테이블명: {} (범위: {} ~ {})", tableName, startRange, endRange);
    }

    /**
     * 보존 기한이 지난 서브 테이블을 DROP 문으로 리소스 부하 없이 통째로 증발시킴
     */
    private void dropExpiredPartition(LocalDate date) {
        String suffix = date.format(DateTimeFormatter.ofPattern("yyyy_MM"));
        String tableName = "alert_history_" + suffix;

        // 🚀 핵심 포인트: DELETE를 쓰지 않고 DROP TABLE을 써서 Dead Tuple(찌꺼기) 없이 디스크 용량을 즉시 완전 회수
        String sql = "DROP TABLE IF EXISTS " + tableName;

        jdbcTemplate.execute(sql);
        log.warn("🔥 [과거 데이터 파티션 폭파 완료] 보존 기한 만료 테이블 삭제 성공: {}", tableName);
    }
}