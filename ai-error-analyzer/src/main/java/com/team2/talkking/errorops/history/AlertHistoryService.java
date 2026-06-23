package com.team2.talkking.errorops.history;

import com.team2.talkking.errorops.alert.AlertContext;
import com.team2.talkking.errorops.kubernetes.KubernetesDiagnostics;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AlertHistoryService {

    private final AlertHistoryRepository repository;

    /**
     * 🚀 비즈니스 가드레일 반영
     * 1. propagation = Propagation.REQUIRES_NEW: 독립 트랜잭션을 실행하여 DB 예외가 상위 알림 워크플로우를 망치지 않게 격리합니다.
     * 2. 내부 전범위 try-catch: 예외가 터져도 상위 오케스트레이터로 전파되지 않고 가상 격벽 내에서 소멸됩니다.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void save(
            AlertContext context,
            KubernetesDiagnostics diagnostics,
            String runbook,
            boolean slackSent
    ) {
        try {
            // List<String> 이벤트를 줄바꿈(\n) 단위 텍스트로 치환하여 파싱 처리
            String formattedEvents = (diagnostics.recentEvents() == null || diagnostics.recentEvents().isEmpty())
                    ? "No recent pod events found."
                    : String.join("\n", diagnostics.recentEvents());

            AlertHistory history = AlertHistory.builder()
                    .fingerprint(context.fingerprint())
                    .alertName(context.alertName())
                    .namespace(context.namespace())
                    .pod(context.pod())
                    .workload(context.workload())
                    .container(context.container())
                    .severity(context.severity())
                    .summary(context.summary())
                    .description(context.description())
                    .podPhase(diagnostics.podPhase())
                    .recentEvents(formattedEvents)
                    .recentLogs(diagnostics.recentLogs())
                    .k8sErrorMessage(diagnostics.errorMessage())
                    .runbook(runbook)
                    .slackSent(slackSent)
                    .build();

            repository.save(history);
            log.info("💾 [PostgreSQL 영속화 완료] Fingerprint: {}", context.fingerprint());

        } catch (Exception e) {
            log.error("❌ [History 격벽 내부 예외 방어] AlertHistory 저장 중 에러 발생: {}", context.alertName(), e);
        }
    }
}