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
            // 1. 마스터와 자식 파티션이 공유할 전용 시퀀스 생성 (BIGSERIAL의 JPA null 이슈 해결용)
            jdbcTemplate.execute("CREATE SEQUENCE IF NOT EXISTS alert_history_id_seq;");

            // 2. 마스터 파티션 테이블 생성 (id 설정을 시퀀스 nextval 기본값으로 래핑)
            jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS alert_history (
                    id BIGINT DEFAULT nextval('alert_history_id_seq'),
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

            // 3. 고속 검색을 위한 B-Tree 인덱스 생성
            jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_alert_history_created_at ON alert_history (created_at DESC);");
            jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_alert_history_namespace ON alert_history (namespace);");
            jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_alert_history_alert_name ON alert_history (alert_name);");

            // 4. 이번 달 및 다음 달 서브 파티션 방 동적 자동 생성
            createPartitionForMonth(LocalDate.now());           // 이번 달 파티션 생성
            createPartitionForMonth(LocalDate.now().plusMonths(1)); // 다음 달 파티션 미리 생성

            log.info("✅ PostgreSQL 파티션 테이블 및 인덱스 자동 초기화 완료!");
        } catch (Exception e) {
            log.error("❌ 파티션 테이블 자동 생성 중 실패 (인프라 권한 및 DDL 문법 확인 필요)", e);
        }
    }

    private void createPartitionForMonth(LocalDate date) {
        String suffix = date.format(DateTimeFormatter.ofPattern("yyyy_MM"));
        
        // yyyy-MM-dd HH:mm:ss 포맷터 형식에 맞춰 안전하게 일(day)과 시/분/초 설정
        String startRange = date.withDayOfMonth(1).atStartOfDay().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        String endRange = date.withDayOfMonth(1).plusMonths(1).atStartOfDay().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));

        String tableName = "alert_history_" + suffix;
        
        // 자식 테이블을 생성하고, 자식 테이블의 id 컬럼에도 공유 시퀀스 기본값을 바인딩
        String sql = """
            CREATE TABLE IF NOT EXISTS %s PARTITION OF alert_history
            FOR VALUES FROM ('%s') TO ('%s');
            
            ALTER TABLE %s ALTER COLUMN id SET DEFAULT nextval('alert_history_id_seq');
            """.formatted(tableName, startRange, endRange, tableName);

        jdbcTemplate.execute(sql);
        log.info("📂 서브 파티션 테이블 검증 완료: {} [{} ~ {}]", tableName, startRange, endRange);
    }
}