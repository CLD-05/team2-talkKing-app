package com.team2.talkking.errorops.history;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 🛡️ AI Error Ops 보안 감사 엔진 (Safety Audit Engine)
 * 역할: AI가 생성한 결과물(Runbook/PR 요약)을 실시간 분석하여 금지된 행동을 수행했는지 검증하고 위험 징후를 추적.
 */
@Slf4j
@Component
public class AlertSafetyAuditEngine {

    /**
     * AI가 생성한 런북 본문을 실시간으로 파싱하여
     * 금지된 파괴적 작업 패턴이 보일 경우 슬랙 하단에 부착할 경고 메시지를 생성합니다.
     */
    public String auditAIActions(String runbook) {
        log.info("🛡️ [인프라 안심 감사 엔진 가동] AI 생성 결과물 실시간 취약점 스캔 시작...");

        if (runbook == null || runbook.isBlank()) {
            return "";
        }

        // 금지 키워드 포함 여부 검사
        boolean destructive = runbook.contains("delete") || runbook.contains("네임스페이스") || runbook.contains("삭제");
        boolean iamRisk = runbook.contains("IAM") || runbook.contains("권한");

        if (!destructive && !iamRisk) {
            log.info("✅ AI가 안전 가이드라인을 완벽히 준수했습니다.");
            return ""; // 안전하다면 빈 문자열 반환
        }

        log.warn("🚨 [AI 규정 위반 징후 포착] 실시간 런북 내 가이드라인 위반 키워드 탐지됨.");
        
        // 슬랙 마크다운 포맷에 맞춘 경고 문구 조합
        StringBuilder warningMessage = new StringBuilder();
        warningMessage.append("\n\n--------------------------------------------------");
        warningMessage.append("\n*🚨 [Safety Audit Engine 보안 감사 경고]*");
        warningMessage.append("\n> ⚠️ *해당 조치 제안서는 AI의 규정 위반 위험 요소를 포함하고 있을 수 있습니다.*");
        
        if (destructive) {
            warningMessage.append("\n• ⚠️ *파괴적 작업(삭제/리셋 등)* 관련 행위가 감지되었습니다. 절대 무지성 승인을 금하며, 조치 코드를 반드시 수동 검토하십시오.");
        }
        if (iamRisk) {
            warningMessage.append("\n• ⚠️ *IAM 및 인프라 권한 변경* 시도 징후가 포착되었습니다. 자동 PR 승인 요건 처리를 보류하고 인프라 관리자 승인을 받으세요.");
        }
        warningMessage.append("\n--------------------------------------------------");

        return warningMessage.toString();
    }
}