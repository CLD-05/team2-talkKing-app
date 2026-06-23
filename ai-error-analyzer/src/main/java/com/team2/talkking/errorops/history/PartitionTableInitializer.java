package com.team2.talkking.errorops.history;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

@Slf4j
@Component
@RequiredArgsConstructor
public class PartitionTableInitializer implements CommandLineRunner {

    private final JdbcTemplate jdbcTemplate;

    @Override
    public void run(String... args) throws Exception {
        log.info("🚀 PostgreSQL 파티션 테이블 자동 생성 및 검증 시작...");
        try {
            // 1. 마스터 파티션 테이블 생성 (없을 때만)
            jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS alert_history (
                    id BIGSERIAL,
                    fingerprint VARCHAR(255),
                    alert_name VARCHAR(255),
                    namespace VARCHAR(255),
                    pod VARCHAR(255),
                    workload VARCHAR(255),
                    container VARCHAR(255),
                    severity VARCHAR(50),
                    summary VARCHAR(1000),
                    description TEXT,
                    pod_phase VARCHAR(50),
                    recent_events TEXT,
                    recent_logs TEXT,
                    k8s_error_message TEXT,
                    slack_sent BOOLEAN,
                    runbook TEXT,
                    created_at TIMESTAMP WITHOUT TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,
                    PRIMARY KEY (id, created_at)
                ) PARTITION BY RANGE (created_at);
            """);

            // 2. 고속 검색을 위한 B-Tree 인덱스 생성 (없을 때만)
            jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_alert_history_created_at ON alert_history (created_at DESC);");
            jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_alert_history_namespace ON alert_history (namespace);");
            jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_alert_history_alert_name ON alert_history (alert_name);");

            // 3. 현재 달(6월) 및 다음 달(7월) 서브 파티션 방 동적 자동 생성
            createPartitionForMonth(LocalDate.now());         // 이번 달 파티션 생성
            createPartitionForMonth(LocalDate.now().plusMonths(1)); // 다음 달 파티션 미리 생성

            log.info("✅ PostgreSQL 파티션 테이블 및 인덱스 자동 초기화 완료!");
        } catch (Exception e) {
            log.error("❌ 파티션 테이블 자동 생성 중 실패 (인프라 권한 및 DDL 문법 확인 필요)", e);
        }
    }

    private void createPartitionForMonth(LocalDate date) {
        // 2026-06-23 기준 -> "2026_06" 포맷팅
        String suffix = date.format(DateTimeFormatter.ofPattern("yyyy_MM"));
        String startRange = date.withDayOfMonth(1).format(DateTimeFormatter.ofPattern("yyyy-MM-01 00:00:00"));
        String endRange = date.withDayOfMonth(1).plusMonths(1).format(DateTimeFormatter.ofPattern("yyyy-MM-01 00:00:00"));

        String tableName = "alert_history_" + suffix;
        
        String sql = """
            CREATE TABLE IF NOT EXISTS %s PARTITION OF alert_history
            FOR VALUES FROM ('%s') TO ('%s');
            """.formatted(tableName, startRange, endRange);

        jdbcTemplate.execute(sql);
        log.info("📂 서브 파티션 테이블 검증 완료: {} [{} ~ {}]", tableName, startRange, endRange);
    }
}