package com.team2.talkking.errorops.alert;

import com.team2.talkking.errorops.gemini.GeminiRunbookClient;
import com.team2.talkking.errorops.history.AlertHistoryService;
import com.team2.talkking.errorops.kubernetes.KubernetesDiagnosticService;
import com.team2.talkking.errorops.kubernetes.KubernetesDiagnostics;
import com.team2.talkking.errorops.slack.SlackNotifier;
import java.util.List;
import org.springframework.stereotype.Service;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class AlertWorkflowService {

    private final AlertContextFactory alertContextFactory;
    private final KubernetesDiagnosticService kubernetesDiagnosticService;
    private final GeminiRunbookClient geminiRunbookClient;
    private final SlackNotifier slackNotifier;
    private final AlertThrottleServiceRedis alertThrottleService;
    private final AlertHistoryService alertHistoryService;

    public AlertWorkflowService(
            AlertContextFactory alertContextFactory,
            KubernetesDiagnosticService kubernetesDiagnosticService,
            GeminiRunbookClient geminiRunbookClient,
            SlackNotifier slackNotifier,
            AlertThrottleServiceRedis alertThrottleService,
            AlertHistoryService alertHistoryService
    ) {
        this.alertContextFactory = alertContextFactory;
        this.kubernetesDiagnosticService = kubernetesDiagnosticService;
        this.geminiRunbookClient = geminiRunbookClient;
        this.slackNotifier = slackNotifier;
        this.alertThrottleService = alertThrottleService;
        this.alertHistoryService = alertHistoryService;
    }

    public AlertAnalysisResponse handle(AlertmanagerWebhookRequest request) {
        List<AlertmanagerWebhookRequest.Alert> alerts = request.alerts() == null ? List.of() : request.alerts();
        List<AlertAnalysisResponse.AlertResult> results = alerts.stream()
                .filter(alert -> !"resolved".equalsIgnoreCase(alert.status()))
                .map(this::analyze)
                .toList();

        return new AlertAnalysisResponse(alerts.size(), results.size(), results);
    }

    private AlertAnalysisResponse.AlertResult analyze(AlertmanagerWebhookRequest.Alert alert) {
        AlertContext context = alertContextFactory.from(alert);
        String fingerprint = context.fingerprint();
        
        log.info("Analyzing alert - fingerprint: {}, alertname: {}, workload: {}, pod: {}", 
                fingerprint, context.alertName(), context.workload(), context.pod());
        
        // ✅ Kubernetes 진단 (실패해도 fallback)
        KubernetesDiagnostics diagnostics;
        try {
            diagnostics = kubernetesDiagnosticService.collect(context);
        } catch (Exception e) {
            log.error("Kubernetes diagnostic collection failed for alert: {}", context.alertName(), e);
            diagnostics = KubernetesDiagnostics.unavailable(
                "Kubernetes diagnostic collection failed: " + e.getMessage()
            );
        }

        // ✅ AI 서버가 뻗어도 알람은 가야 하므로 try-catch 추가
        String runbook;
        try {
            runbook = geminiRunbookClient.generateRunbook(context, diagnostics);
        } catch (Exception e) {
            log.error("Gemini AI Runbook generation failed for alert: {}", context.alertName(), e);
            runbook = "⚠️ AI 분석을 일시적으로 가져올 수 없습니다. (에러: " + e.getMessage() + ")\n" +
                    "직접 K8s 로그와 이벤트를 확인해 주세요.";
        }

        // ✅ alertName + namespace + workload + container 기반 중복 체크
        boolean shouldNotify = alertThrottleService.canNotify(
            context.alertName(), 
            context.namespace(), 
            context.workload(),
            context.container()
        );
        
        log.debug("Alert {}:{}:{}:{} shouldNotify: {}", 
            context.alertName(), context.namespace(), context.workload(), 
            context.container(), shouldNotify);

        boolean slackSent = false;

        if (shouldNotify) {
            try {
                slackSent = slackNotifier.send(context, runbook);
                
                if (slackSent) {
                    // ✅ 같은 키로 Redis에 기록
                    alertThrottleService.recordNotification(
                        context.alertName(), 
                        context.namespace(), 
                        context.workload(),
                        context.container()
                    );
                    log.info("Slack notification sent for alert: {}", fingerprint);
                } else {
                    log.warn("Failed to send Slack notification for alert: {}", fingerprint);
                }
            } catch (Exception e) {
                log.error("Error sending Slack notification for alert: {}", fingerprint, e);
            }
        } else {
            log.info("Alert {}:{}:{}:{} throttled - less than 10 minutes since last notification", 
                context.alertName(), context.namespace(), context.workload(), 
                context.container());
        }

        // ✅ 히스토리 저장 (실패해도 무시)
        try {
            alertHistoryService.save(context, diagnostics, runbook, slackSent);
        } catch (Exception e) {
            log.error("Failed to save alert history for alert: {}", context.alertName(), e);
        }

        return new AlertAnalysisResponse.AlertResult(
                fingerprint,
                context.alertName(),
                context.namespace(),
                context.pod(),
                context.severity(),
                slackSent,
                runbook
        );
    }
}