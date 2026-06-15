package com.team2.talkking.errorops.alert;

import com.team2.talkking.errorops.gemini.GeminiRunbookClient;
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

    public AlertWorkflowService(
            AlertContextFactory alertContextFactory,
            KubernetesDiagnosticService kubernetesDiagnosticService,
            GeminiRunbookClient geminiRunbookClient,
            SlackNotifier slackNotifier,
            AlertThrottleServiceRedis alertThrottleService
    ) {
        this.alertContextFactory = alertContextFactory;
        this.kubernetesDiagnosticService = kubernetesDiagnosticService;
        this.geminiRunbookClient = geminiRunbookClient;
        this.slackNotifier = slackNotifier;
        this.alertThrottleService = alertThrottleService;
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
        
        log.info("Analyzing alert - fingerprint: {}, alertname: {}, pod: {}", 
                fingerprint, context.alertName(), context.pod());
        
        KubernetesDiagnostics diagnostics = kubernetesDiagnosticService.collect(context);
        String runbook = geminiRunbookClient.generateRunbook(context, diagnostics);
        
        // ✅ 수정: alertName과 namespace로 중복 체크 (Pod 무관)
        boolean shouldNotify = alertThrottleService.canNotify(context.alertName(), context.namespace());
        log.debug("Alert {}:{} shouldNotify: {}", context.alertName(), context.namespace(), shouldNotify);
        
        boolean slackSent = false;
        
        if (shouldNotify) {
            try {
                slackSent = slackNotifier.send(context, runbook);
                
                if (slackSent) {
                    // ✅ 수정: alertName과 namespace로 기록
                    alertThrottleService.recordNotification(context.alertName(), context.namespace());
                    log.info("Slack notification sent for alert: {}", fingerprint);
                } else {
                    log.warn("Failed to send Slack notification for alert: {}", fingerprint);
                }
            } catch (Exception e) {
                log.error("Error sending Slack notification for alert: {}", fingerprint, e);
            }
        } else {
            log.debug("Alert {}:{} throttled - less than 10 minutes since last notification", context.alertName(), context.namespace());
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