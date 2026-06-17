package com.team2.talkking.errorops.metrics;

import io.micrometer.core.instrument.*;
import org.springframework.stereotype.Component;
import lombok.extern.slf4j.Slf4j;

/**
 * 🆕 AI Error Analyzer 메트릭 클래스
 * 
 * 위치: src/main/java/com/team2/talkking/errorops/metrics/AiAnalyzerMetrics.java
 * 
 * 역할:
 * 1. 메트릭 초기화 (@Component로 자동 등록)
 * 2. Alert 처리 메트릭 기록:
 *    - ai_analyzer_calls_total: 호출 건수
 *    - ai_analyzer_alerts_by_type: Alert 종류별 (critical/warning/info)
 *    - ai_analyzer_severity_count: 심각도별 (high/medium/low)
 *    - ai_analyzer_request_duration_seconds: 응답시간
 *    - ai_analyzer_request_total: 성공/실패
 */
@Slf4j
@Component
public class AiAnalyzerMetrics {

    private final MeterRegistry meterRegistry;

    public AiAnalyzerMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
        initializeMetrics();
        log.info("✅ AiAnalyzerMetrics 초기화 완료");
    }

    /**
     * 모든 메트릭 초기화
     * Spring Boot 시작시 자동으로 호출됨
     */
    private void initializeMetrics() {
        // 1️⃣ 호출 건수 (총합)
        Counter.builder("ai_analyzer_calls_total")
            .description("AI Analyzer 총 호출 건수")
            .register(meterRegistry);

        // 2️⃣ Alert 종류별 (critical, warning, info)
        for (String alertType : new String[]{"critical", "warning", "info"}) {
            Counter.builder("ai_analyzer_alerts_by_type")
                .description("Alert 종류별 개수")
                .tag("type", alertType)
                .register(meterRegistry);
        }

        // 3️⃣ 심각도별 (high, medium, low)
        for (String severity : new String[]{"high", "medium", "low"}) {
            Counter.builder("ai_analyzer_severity_count")
                .description("심각도별 개수")
                .tag("severity", severity)
                .register(meterRegistry);
        }

        // 4️⃣ 성공/실패 (success, failure)
        for (String status : new String[]{"success", "failure"}) {
            Counter.builder("ai_analyzer_request_total")
                .description("요청 성공/실패 개수")
                .tag("status", status)
                .register(meterRegistry);
        }

        // 5️⃣ 응답시간 (분포, P50/P95/P99 자동 계산)
        Timer.builder("ai_analyzer_request_duration_seconds")
            .description("AI Analyzer 응답시간 (초)")
            .publishPercentiles(0.5, 0.95, 0.99)
            .baseUnit("seconds")
            .register(meterRegistry);

        log.info("🎯 메트릭 초기화 완료");
    }

    // =====================================================
    // 📊 메트릭 기록 메서드
    // =====================================================

    /**
     * 호출 건수 증가 (AlertWorkflowService.handle()에서 호출)
     */
    public void recordCall() {
        Counter counter = meterRegistry.find("ai_analyzer_calls_total").counter();
        if (counter != null) {
            counter.increment();
        }
    }

    /**
     * Alert 기록 (AlertWorkflowService.analyze()에서 호출)
     * @param alertType "critical", "warning", "info" (Alert 종류)
     * @param severity "high", "medium", "low" (심각도)
     */
    public void recordAlert(String alertType, String severity) {
        meterRegistry.counter("ai_analyzer_alerts_by_type", "type", alertType).increment();
        meterRegistry.counter("ai_analyzer_severity_count", "severity", severity).increment();
        log.debug("📊 Alert 기록: type={}, severity={}", alertType, severity);
    }

    /**
     * 성공/실패 기록 (AlertWorkflowService.analyze()에서 호출)
     * @param success true면 성공, false면 실패
     */
    public void recordResult(boolean success) {
        String status = success ? "success" : "failure";
        meterRegistry.counter("ai_analyzer_request_total", "status", status).increment();
    }

    /**
     * 응답시간 기록 (AlertWorkflowService.analyze()에서 호출)
     * @param durationMs 응답시간 (밀리초)
     */
    public void recordResponseTime(long durationMs) {
        Timer timer = meterRegistry.find("ai_analyzer_request_duration_seconds").timer();
        if (timer != null) {
            timer.record(java.time.Duration.ofMillis(durationMs));
        }
    }

    // =====================================================
    // 🔧 정규화 유틸 (AlertWorkflowService에서 사용)
    // =====================================================

    /**
     * Alert 타입 정규화
     * alertName과 severity로부터 "critical", "warning", "info" 중 하나로 변환
     * 
     * @param alertName Alert 이름 (예: "KubernetesError", "HighMemory")
     * @param severity Alert severity (예: "critical", "warning")
     * @return "critical", "warning", "info" 중 하나
     */
    public static String normalizeAlertType(String alertName, String severity) {
        if (alertName == null && severity == null) {
            return "info";
        }

        String combined = ((alertName != null ? alertName : "") + " " + 
                          (severity != null ? severity : "")).toLowerCase();

        // Critical로 분류
        if (combined.contains("critical") || combined.contains("error") || 
            combined.contains("crash") || combined.contains("down") || 
            combined.contains("fatal") || combined.contains("panic")) {
            return "critical";
        }

        // Warning으로 분류
        if (combined.contains("warning") || combined.contains("slow") || 
            combined.contains("high") || combined.contains("watch")) {
            return "warning";
        }

        // 기본값: info
        return "info";
    }

    /**
     * 심각도 정규화
     * severity를 "high", "medium", "low" 중 하나로 변환
     * 
     * @param severity 원본 severity (예: "critical", "warning", "info")
     * @return "high", "medium", "low" 중 하나
     */
    public static String normalizeSeverity(String severity) {
        if (severity == null) {
            return "medium";
        }

        String lower = severity.toLowerCase();

        // High 심각도
        if (lower.contains("critical") || lower.contains("high") || 
            lower.contains("severe") || lower.contains("fatal")) {
            return "high";
        } 
        // Medium 심각도
        else if (lower.contains("warning") || lower.contains("medium")) {
            return "medium";
        } 
        // Low 심각도
        else {
            return "low";
        }
    }
}