package com.team2.talkking.errorops.slack;

import com.team2.talkking.errorops.alert.AlertContext;
import java.time.Duration;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

@Component
public class SlackNotifier {

    private final WebClient webClient;
    private final String criticalWebhookUrl;
    private final String warningWebhookUrl;
    private final String infoWebhookUrl;

    public SlackNotifier(
            WebClient.Builder webClientBuilder,
            @Value("${errorops.slack.critical-webhook-url:}") String criticalWebhookUrl,
            @Value("${errorops.slack.warning-webhook-url:}") String warningWebhookUrl,
            @Value("${errorops.slack.info-webhook-url:}") String infoWebhookUrl
    ) {
        this.webClient = webClientBuilder.build();
        this.criticalWebhookUrl = criticalWebhookUrl;
        this.warningWebhookUrl = warningWebhookUrl;
        this.infoWebhookUrl = infoWebhookUrl;
    }

    public boolean send(AlertContext context, String runbook) {
        String selectedWebhookUrl = selectWebhookUrl(context);
        if (selectedWebhookUrl == null || selectedWebhookUrl.isBlank()) {
            return false;
        }

        String message = """
                *[%s] %s*
                namespace: `%s`
                pod: `%s`

                %s
                """.formatted(
                context.severity(),
                context.alertName(),
                context.namespace(),
                context.pod(),
                runbook
        );

        try {
            webClient.post()
                    .uri(selectedWebhookUrl)
                    .bodyValue(Map.of("text", message))
                    .retrieve()
                    .toBodilessEntity()
                    .timeout(Duration.ofSeconds(10))
                    .block();
            return true;
        } catch (Exception exception) {
            return false;
        }
    }

    private String selectWebhookUrl(AlertContext context) {
        String severity = context.severity() == null ? "" : context.severity().toLowerCase();

        return switch (severity) {
            case "warning" -> firstNonBlank(warningWebhookUrl, criticalWebhookUrl);
            case "info" -> firstNonBlank(infoWebhookUrl, criticalWebhookUrl);
            case "critical" -> criticalWebhookUrl;
            default -> criticalWebhookUrl;
        };
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return "";
    }
}
