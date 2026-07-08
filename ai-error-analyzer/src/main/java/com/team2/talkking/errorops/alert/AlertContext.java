package com.team2.talkking.errorops.alert;

import java.util.Map;

public record AlertContext(
        String fingerprint,
        String alertName,
        String namespace,
        String pod,
        String workload,
        String container,
        String severity,
        String summary,
        String description,
        Map<String, String> labels,
        Map<String, String> annotations
) {
}
