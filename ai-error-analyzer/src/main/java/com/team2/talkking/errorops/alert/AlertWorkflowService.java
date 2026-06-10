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

    public AlertWorkflowService(
            AlertContextFactory alertContextFactory,
            KubernetesDiagnosticService kubernetesDiagnosticService,
            GeminiRunbookClient geminiRunbookClient,
            SlackNotifier slackNotifier
    ) {
        this.alertContextFactory = alertContextFactory;
        this.kubernetesDiagnosticService = kubernetesDiagnosticService;
        this.geminiRunbookClient = geminiRunbookClient;
        this.slackNotifier = slackNotifier;
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
        KubernetesDiagnostics diagnostics = kubernetesDiagnosticService.collect(context);
        String runbook = geminiRunbookClient.generateRunbook(context, diagnostics);
        boolean slackSent = slackNotifier.send(context, runbook);

        return new AlertAnalysisResponse.AlertResult(
                context.fingerprint(),
                context.alertName(),
                context.namespace(),
                context.pod(),
                context.severity(),
                slackSent,
                runbook
        );
    }
}
