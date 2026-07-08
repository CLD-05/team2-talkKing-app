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
        String alertName = valueOr(labels.get("alertname"), "UnknownAlert"); // ✅ alertName 먼저 추출
        
        // ✅ fallback으로 pod이 아닌 'workload'를 넘겨야 컨테이너 이름과 일치합니다!
        String container = getContainerOrFallback(
            alertName,
            firstPresent(labels, "container", "container_name"),
            workload 
        );

        return new AlertContext(
                valueOr(alert.fingerprint(), "unknown"),
                alertName,
                valueOr(labels.get("namespace"), DEFAULT_NAMESPACE),
                pod,
                workload,
                container,
                valueOr(labels.get("severity"), "warning"),
                valueOr(annotations.get("summary"), ""),
                valueOr(annotations.get("description"), ""),
                labels,
                annotations
        );
    }

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

    // ✅ 세 번째 인자를 fallbackName(workload)으로 받습니다.
    private static String getContainerOrFallback(String alertName, String container, String fallbackName) {
        // Pod-level alert는 무조건 fallback(workload)으로
        if (alertName.contains("PodNotReady") || 
            alertName.contains("CrashLooping") || 
            alertName.contains("RestartRate")) {
            return fallbackName;
        }
        
        // 값이 없거나 쓰레기값(kube-state-metrics)이면 fallback(workload)으로
        if (container == null || 
            container.isBlank() || 
            "kube-state-metrics".equals(container)) {
            return fallbackName;
        }
        
        return container;
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