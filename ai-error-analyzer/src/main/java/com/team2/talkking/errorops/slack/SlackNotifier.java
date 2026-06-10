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
    private final String webhookUrl;

    public SlackNotifier(
            WebClient.Builder webClientBuilder,
            @Value("${errorops.slack.webhook-url:}") String webhookUrl
    ) {
        this.webClient = webClientBuilder.build();
        this.webhookUrl = webhookUrl;
    }

    public boolean send(AlertContext context, String runbook) {
        if (webhookUrl == null || webhookUrl.isBlank()) {
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
                    .uri(webhookUrl)
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
}
