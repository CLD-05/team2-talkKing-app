package com.team2.talkking.errorops.alert;

import java.util.Map;
import java.util.Arrays;
import org.springframework.stereotype.Component;

@Component
public class AlertContextFactory {

    private static final String DEFAULT_NAMESPACE = "default";

    public AlertContext from(AlertmanagerWebhookRequest.Alert alert) {
        Map<String, String> labels = Map.copyOf(nullToEmpty(alert.labels()));
        Map<String, String> annotations = Map.copyOf(nullToEmpty(alert.annotations()));
        
        String pod = firstPresent(labels, "pod", "pod_name", "kubernetes_pod_name");
        String workload = extractWorkload(pod);

        return new AlertContext(
                valueOr(alert.fingerprint(), "unknown"),
                valueOr(labels.get("alertname"), "UnknownAlert"),
                valueOr(labels.get("namespace"), DEFAULT_NAMESPACE),
                pod,
                workload,  // ← 추가!
                firstPresent(labels, "container", "container_name"),
                valueOr(labels.get("severity"), "warning"),
                valueOr(annotations.get("summary"), ""),
                valueOr(annotations.get("description"), ""),
                labels,
                annotations
        );
    }

    /**
     * Pod 이름에서 workload 추출
     * chat-service-777fb46bd4-6p9x4 → chat-service
     */
    private static String extractWorkload(String pod) {
        if (pod == null || pod.isBlank()) {
            return "";
        }

        String[] parts = pod.split("-");
        
        if (parts.length > 1) {
            String lastPart = parts[parts.length - 1];
            if (lastPart.length() == 5 && lastPart.matches("[a-z0-9]{5}")) {
                if (parts.length > 2) {
                    String secondLast = parts[parts.length - 2];
                    if (secondLast.length() >= 8) {
                        return String.join("-", Arrays.copyOf(parts, parts.length - 2));
                    }
                }
                return String.join("-", Arrays.copyOf(parts, parts.length - 1));
            }
        }
        
        return pod;
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