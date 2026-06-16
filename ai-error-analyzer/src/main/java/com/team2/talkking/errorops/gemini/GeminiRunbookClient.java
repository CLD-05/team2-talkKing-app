package com.team2.talkking.errorops.gemini;

import com.team2.talkking.errorops.alert.AlertContext;
import com.team2.talkking.errorops.kubernetes.KubernetesDiagnostics;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

@Component
public class GeminiRunbookClient {

    private final WebClient webClient;
    private final String apiKey;
    private final String model;

    public GeminiRunbookClient(
            WebClient.Builder webClientBuilder,
            @Value("${errorops.gemini.api-key:}") String apiKey,
            @Value("${errorops.gemini.model:gemini-1.5-flash}") String model
    ) {
        this.webClient = webClientBuilder.baseUrl("https://generativelanguage.googleapis.com").build();
        this.apiKey = apiKey;
        this.model = model;
    }

    public String generateRunbook(AlertContext context, KubernetesDiagnostics diagnostics) {
        String prompt = buildPrompt(context, diagnostics);
        if (apiKey == null || apiKey.isBlank()) {
            return fallbackRunbook(context, diagnostics);
        }

        try {
            GeminiResponse response = webClient.post()
                    .uri("/v1beta/models/{model}:generateContent?key={apiKey}", model, apiKey)
                    .bodyValue(Map.of(
                            "contents", List.of(Map.of(
                                    "parts", List.of(Map.of("text", prompt))
                            ))
                    ))
                    .retrieve()
                    .bodyToMono(GeminiResponse.class)
                    .timeout(Duration.ofSeconds(20))
                    .block();

            String text = response == null ? "" : response.firstText();
            return text == null || text.isBlank() ? fallbackRunbook(context, diagnostics) : text;
        } catch (Exception exception) {
            return fallbackRunbook(context, diagnostics) + "\n\nGemini call failed: " + exception.getMessage();
        }
    }

    private String buildPrompt(AlertContext context, KubernetesDiagnostics diagnostics) {
        return """
                You are an SRE assistant. Write a concise English runbook for the Kubernetes alert.

                Alert:
                - name: %s
                - severity: %s
                - namespace: %s
                - pod: %s
                - container: %s
                - summary: %s
                - description: %s

                Kubernetes diagnostics:
                - pod phase: %s
                - recent events:
                %s
                - logs:
                %s

                Output sections:
                1. Likely cause
                2. Impact
                3. Commands to check now
                4. Recommended actions
                """.formatted(
                context.alertName(),
                context.severity(),
                context.namespace(),
                context.pod(),
                context.container(),
                context.summary(),
                context.description(),
                diagnostics.podPhase(),
                formatEvents(diagnostics.recentEvents()),
                truncate(diagnostics.recentLogs(), 6000)
        );
    }

    private String fallbackRunbook(AlertContext context, KubernetesDiagnostics diagnostics) {
        return """
                Likely cause: Alert %s is firing. Check the pod status, recent events, and recent container logs first.
                Impact: namespace=%s, pod=%s, severity=%s
                Recent Kubernetes events:
                %s
                Commands to check now:
                - kubectl describe pod %s -n %s
                - kubectl logs %s -n %s --tail=120
                Recommended actions: Use the events and logs to check for crash loops, image pull errors, bad configuration, failed probes, or resource pressure.
                Diagnostic error: %s
                """.formatted(
                context.alertName(),
                context.namespace(),
                context.pod(),
                context.severity(),
                formatEvents(diagnostics.recentEvents()),
                context.pod(),
                context.namespace(),
                context.pod(),
                context.namespace(),
                diagnostics.errorMessage()
        );
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value == null ? "" : value;
        }
        return value.substring(value.length() - maxLength);
    }

    private String formatEvents(List<String> events) {
        if (events == null || events.isEmpty()) {
            return "- No recent pod events found.";
        }
        return events.stream()
                .map(event -> "- " + event)
                .reduce((left, right) -> left + "\n" + right)
                .orElse("- No recent pod events found.");
    }
}
