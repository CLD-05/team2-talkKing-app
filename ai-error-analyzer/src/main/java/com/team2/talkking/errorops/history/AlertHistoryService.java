package com.team2.talkking.errorops.history;

import com.team2.talkking.errorops.alert.AlertContext;
import com.team2.talkking.errorops.kubernetes.KubernetesDiagnostics;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional; // 🚀 추가

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true) // 🚀 기본적으로 읽기 전용으로 설정하여 성능 최적화
public class AlertHistoryService {

    private final AlertHistoryRepository repository;

    @Transactional // 🚀 저장(CUD) 로직에는 쓰기 트랜잭션 보장
    public void save(
            AlertContext context,
            KubernetesDiagnostics diagnostics,
            String runbook,
            boolean slackSent
    ) {

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
                .runbook(runbook)
                .slackSent(slackSent)
                .build();

        repository.save(history);
    }
}