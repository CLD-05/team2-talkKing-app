package com.team2.talkking.errorops.history;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class AlertSafetyAuditEngine {

    public String auditAIActions(String runbook) {
        log.info("🛡️ [인프라 안심 감사 엔진 가동] AI 생성 결과물 실시간 취약점 스캔 시작...");

        if (runbook == null || runbook.isBlank()) {
            return "";
        }

        // 🎯 [핵심 패치] 프롬프트(지시문) 영역에 포함된 금지어 규칙 오탐 방지
        String targetText = runbook;
        
        // AI 분석 결과물(Summary)이 시작되는 지점을 찾아서 그 이후의 텍스트만 검사 대상으로 삼습니다.
        if (runbook.contains("[Summary]")) {
            targetText = runbook.substring(runbook.indexOf("[Summary]"));
        } else if (runbook.contains("Gemini call failed:")) {
            // 만약 이번처럼 AI가 에러를 뿜어서 분석 결과가 없는 상태라면 검사를 스킵합니다.
            log.info("✅ AI 분석 호출이 실패한 Fallback 메시지이므로 보안 검사를 스킵합니다.");
            return "";
        }

        // 이제 프롬프트가 아닌 'AI의 실제 조치 제안 본문'만 가지고 금지 키워드를 검사합니다.
        boolean destructive = targetText.contains("delete") || targetText.contains("네임스페이스") || targetText.contains("삭제");
        boolean iamRisk = targetText.contains("IAM") || targetText.contains("권한");

        if (!destructive && !iamRisk) {
            log.info("✅ AI가 안전 가이드라인을 완벽히 준수했습니다.");
            return "";
        }

        log.warn("🚨 [AI 규정 위반 징후 포착] AI가 제안한 조치 본문 내 위반 키워드 탐지됨.");
        
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