package com.team2.talkking.errorops.alert;

import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class AlertContextFactory {

    private static final String DEFAULT_NAMESPACE = "default";

    public AlertContext from(AlertmanagerWebhookRequest.Alert alert) {
        Map<String, String> labels = Map.copyOf(nullToEmpty(alert.labels()));
        Map<String, String> annotations = Map.copyOf(nullToEmpty(alert.annotations()));

        return new AlertContext(
                valueOr(alert.fingerprint(), "unknown"),
                valueOr(labels.get("alertname"), "UnknownAlert"),
                valueOr(labels.get("namespace"), DEFAULT_NAMESPACE),
                firstPresent(labels, "pod", "pod_name", "kubernetes_pod_name"),
                firstPresent(labels, "container", "container_name"),
                valueOr(labels.get("severity"), "warning"),
                valueOr(annotations.get("summary"), ""),
                valueOr(annotations.get("description"), ""),
                labels,
                annotations
        );
    }

    private static Map<String, String> nullToEmpty(Map<String, String> value) {
        return value == null ? Map.of() : value;
    }

    private static String firstPresent(Map<String, String> values, String... keys) {
        for (String key : keys) {
            String value = values.get(key);
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return "";
    }

    private static String valueOr(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
