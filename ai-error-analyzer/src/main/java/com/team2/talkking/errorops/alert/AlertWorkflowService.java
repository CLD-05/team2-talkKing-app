package com.team2.talkking.errorops.alert;

import com.team2.talkking.errorops.gemini.GeminiRunbookClient;
import com.team2.talkking.errorops.kubernetes.KubernetesDiagnosticService;
import com.team2.talkking.errorops.kubernetes.KubernetesDiagnostics;
import com.team2.talkking.errorops.slack.SlackNotifier;
import java.util.List;
import org.springframework.stereotype.Service;

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
        String fingerprint = context.fingerprint();  // ✅ 추가
        
        log.info("Analyzing alert - fingerprint: {}, alertname: {}, pod: {}", 
                fingerprint, context.alertName(), context.pod());
        
        KubernetesDiagnostics diagnostics = kubernetesDiagnosticService.collect(context);
        String runbook = geminiRunbookClient.generateRunbook(context, diagnostics);
        
        // ✅ 추가: Redis에서 중복 체크
        boolean shouldNotify = alertThrottleService.canNotify(fingerprint);
        log.debug("Alert {} shouldNotify: {}", fingerprint, shouldNotify);
        
        boolean slackSent = false;
        
        // ✅ 추가: shouldNotify가 true일 때만 알림
        if (shouldNotify) {
            try {
                slackSent = slackNotifier.send(context, runbook);
                
                if (slackSent) {
                    alertThrottleService.recordNotification(fingerprint);  // ✅ 기록
                    log.info("Slack notification sent for alert: {}", fingerprint);
                } else {
                    log.warn("Failed to send Slack notification for alert: {}", fingerprint);
                }
            } catch (Exception e) {
                log.error("Error sending Slack notification for alert: {}", fingerprint, e);
            }
        } else {
            log.debug("Alert {} throttled - less than 10 minutes since last notification", fingerprint);
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
