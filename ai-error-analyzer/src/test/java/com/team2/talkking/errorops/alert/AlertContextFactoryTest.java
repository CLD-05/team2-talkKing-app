package com.team2.talkking.errorops.alert;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;

class AlertContextFactoryTest {

    private final AlertContextFactory factory = new AlertContextFactory();

    @Test
    void createsContextFromAlertLabels() {
        AlertmanagerWebhookRequest.Alert alert = new AlertmanagerWebhookRequest.Alert(
                "firing",
                Map.of(
                        "alertname", "PodCrashLooping",
                        "namespace", "talkking",
                        "pod", "chat-7854fd799b-12345",
                        "container", "chat",
                        "severity", "critical"
                ),
                Map.of("summary", "Chat pod is crashing"),
                null,
                null,
                "",
                "fingerprint-1"
        );

        AlertContext context = factory.from(alert);

        assertThat(context.alertName()).isEqualTo("PodCrashLooping");
        assertThat(context.namespace()).isEqualTo("talkking");
        assertThat(context.pod()).isEqualTo("chat-7854fd799b-12345");
        assertThat(context.container()).isEqualTo("chat");
        assertThat(context.severity()).isEqualTo("critical");
        assertThat(context.summary()).isEqualTo("Chat pod is crashing");
    }
}
