package com.team2.talkking.errorops.alert;

import com.team2.talkking.errorops.gemini.GeminiRunbookClient;
import com.team2.talkking.errorops.history.AlertHistoryService;
import com.team2.talkking.errorops.history.AlertSafetyAuditEngine; // ◄ 추가된 감사 엔진 임포트
import com.team2.talkking.errorops.kubernetes.KubernetesDiagnosticService;
import com.team2.talkking.errorops.kubernetes.KubernetesDiagnostics;
import com.team2.talkking.errorops.slack.SlackNotifier;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import lombok.extern.slf4j.Slf4j;
import com.team2.talkking.errorops.metrics.AiAnalyzerMetrics;

@Slf4j
@Service
public class AlertWorkflowService {

    private final AlertContextFactory alertContextFactory;
    private final KubernetesDiagnosticService kubernetesDiagnosticService;
    private final GeminiRunbookClient geminiRunbookClient;
    private final SlackNotifier slackNotifier;
    private final AlertThrottleServiceRedis alertThrottleService;
    private final AlertHistoryService alertHistoryService;
    private final AlertSafetyAuditEngine alertSafetyAuditEngine; // ◄ 1. 멤버 변수 추가
    private final AiAnalyzerMetrics metrics;

    public AlertWorkflowService(
            AlertContextFactory alertContextFactory,
            KubernetesDiagnosticService kubernetesDiagnosticService,
            GeminiRunbookClient geminiRunbookClient,
            SlackNotifier slackNotifier,
            AlertThrottleServiceRedis alertThrottleService,
            AlertHistoryService alertHistoryService,
            AlertSafetyAuditEngine alertSafetyAuditEngine, // ◄ 2. 생성자 주입 추가
            AiAnalyzerMetrics metrics
    ) {
        this.alertContextFactory = alertContextFactory;
        this.kubernetesDiagnosticService = kubernetesDiagnosticService;
        this.geminiRunbookClient = geminiRunbookClient;
        this.slackNotifier = slackNotifier;
        this.alertThrottleService = alertThrottleService;
        this.alertHistoryService = alertHistoryService;
        this.alertSafetyAuditEngine = alertSafetyAuditEngine; // ◄ 3. 필드 할당
        this.metrics = metrics;
    }

    public AlertAnalysisResponse handle(AlertmanagerWebhookRequest request) {
        metrics.recordCall();
        List<AlertmanagerWebhookRequest.Alert> alerts = request.alerts() == null ? List.of() : request.alerts();
        List<AlertAnalysisResponse.AlertResult> results = alerts.stream()
                .filter(alert -> !"resolved".equalsIgnoreCase(alert.status()))
                .filter(this::isActionableCodexAlert)
                .map(this::analyze)
                .toList();

        return new AlertAnalysisResponse(alerts.size(), results.size(), results);
    }

    private boolean isActionableCodexAlert(AlertmanagerWebhookRequest.Alert alert) {
        Map<String, String> labels = alert.labels() == null ? Map.of() : alert.labels();
        String alertName = labels.getOrDefault("alertname", "");
        String severity = labels.getOrDefault("severity", "");
        String namespace = labels.getOrDefault("namespace", "");
        String pod = firstPresent(labels, "pod", "pod_name", "kubernetes_pod_name");

        if ("Watchdog".equalsIgnoreCase(alertName) || "InfoInhibitor".equalsIgnoreCase(alertName)) {
            log.info("Skipping non-actionable platform alert: {}", alertName);
            return false;
        }

        if (!"warning".equalsIgnoreCase(severity) && !"critical".equalsIgnoreCase(severity)) {
            log.info("Skipping alert with non-actionable severity - alertname: {}, severity: {}", alertName, severity);
            return false;
        }

        if (!namespace.startsWith("talkking-")) {
            log.info("Skipping alert outside TalkKing namespaces - alertname: {}, namespace: {}", alertName, namespace);
            return false;
        }

        if (pod.isBlank()) {
            log.info("Skipping alert without pod label - alertname: {}, namespace: {}", alertName, namespace);
            return false;
        }

        return true;
    }

    private String firstPresent(Map<String, String> values, String... keys) {
        for (String key : keys) {
            String value = values.get(key);
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return "";
    }

    private AlertAnalysisResponse.AlertResult analyze(AlertmanagerWebhookRequest.Alert alert) {
        long startTime = System.currentTimeMillis();
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
        boolean aiSuccess = false;
        try {
            runbook = geminiRunbookClient.generateRunbook(context, diagnostics);
            aiSuccess = true;
        } catch (Exception e) {
            log.error("Gemini AI Runbook generation failed for alert: {}", context.alertName(), e);
            runbook = "⚠️ AI 분석을 일시적으로 가져올 수 없습니다. (에러: " + e.getMessage() + ")\n" +
                    "직접 K8s 로그와 이벤트를 확인해 주세요.";
            aiSuccess = false;
        }

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
            String alertType = AiAnalyzerMetrics.normalizeAlertType(context.alertName(), context.severity());
            String severity = AiAnalyzerMetrics.normalizeSeverity(context.severity());
            metrics.recordAlert(alertType, severity);
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

        // 🛡️ [4. 보안 보완 레이어 배치] 히스토리 저장이 끝난 후, 즉각 영속화 데이터 기반 취약점 점검 스캔 가동
        try {
            alertSafetyAuditEngine.auditAIActions();
        } catch (Exception e) {
            log.error("Failed to run safety audit engine for alert: {}", context.alertName(), e);
        }

         // 응답시간 + 성공/실패 기록
        long duration = System.currentTimeMillis() - startTime;
        metrics.recordResponseTime(duration);
        metrics.recordResult(aiSuccess);

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