package com.team2.talkking.errorops.history;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 🛡️ AI Error Ops 보안 감사 엔진 (Safety Audit Engine)
 * 역할: AI가 생성한 결과물(Runbook/PR 요약)을 사후 분석하여 금지된 행동을 수행했는지 검증하고 위험 징후를 추적.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AlertSafetyAuditEngine {

    private final JdbcTemplate jdbcTemplate;

    /**
     * PostgreSQL의 Full-Text 검색 및 패턴 매칭을 활용해
     * AI가 금지된 파괴적 작업(IAM 변경, 네임스페이스 삭제 등)을 수행했는지 본문을 감사 시놉시스화 합니다.
     */
    public void auditAIActions() {
        log.info("🛡️ [PostgreSQL 인프라 안심 감사 엔진 가동] AI 변경 내역 취약점 스캔 시작...");
        
        try {
            // [성능 최적화 튜닝] 파티션 테이블의 인덱스(idx_alert_history_created_at)를 타도록 
            // WHERE 조건절의 created_at 필터링 위치와 조건 방식을 최적화했습니다.
            String sql = """
                SELECT 
                    id, 
                    alert_name, 
                    workload,
                    (runbook LIKE '%delete%' OR runbook LIKE '%네임스페이스%' OR runbook LIKE '%삭제%') as has_destructive_act,
                    (runbook LIKE '%IAM%' OR runbook LIKE '%권한%') as has_iam_risk
                FROM alert_history
                WHERE created_at >= NOW() - INTERVAL '1 hour'
                  AND (
                       runbook LIKE '%delete%' 
                    OR runbook LIKE '%삭제%' 
                    OR runbook LIKE '%네임스페이스%' 
                    OR runbook LIKE '%IAM%' 
                    OR runbook LIKE '%권한%'
                  );
                """;

            List<Map<String, Object>> riskyRows = jdbcTemplate.queryForList(sql);

            if (riskyRows.isEmpty()) {
                log.info("✅ 최근 1시간 동안 AI가 안전 가이드라인을 완벽히 준수했습니다.");
                return;
            }

            for (Map<String, Object> row : riskyRows) {
                // 파티션 복합키 id 구조를 고려하여 안전하게 Long 타입 변환
                Long id = ((Number) row.get("id")).longValue();
                String alertName = (String) row.get("alert_name");
                boolean destructive = (Boolean) row.get("has_destructive_act");
                boolean iamRisk = (Boolean) row.get("has_iam_risk");

                log.warn("🚨 [AI 규정 위반 징후 포착] History ID: {}, 알림명: {}", id, alertName);
                if (destructive) {
                    log.warn("   ↳ ⚠️ 파괴적 작업(삭제/리셋 등) 관련 키워드가 런북에서 탐지되었습니다. 즉시 PR을 수동 검토하십시오.");
                }
                if (iamRisk) {
                    log.warn("   ↳ ⚠️ IAM 또는 권한 변경 시도 징후가 포착되었습니다. 자동 승인을 보류하십시오.");
                }
            }
        } catch (Exception e) {
            log.error("❌ 안전 감사 엔진 실행 중 예외 발생", e);
        }
    }
}