package com.team2.talkking.errorops.alert;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

public record AlertmanagerWebhookRequest(
        String receiver,
        String status,
        Map<String, String> groupLabels,
        Map<String, String> commonLabels,
        Map<String, String> commonAnnotations,
        List<Alert> alerts
) {
    public record Alert(
            String status,
            Map<String, String> labels,
            Map<String, String> annotations,
            OffsetDateTime startsAt,
            OffsetDateTime endsAt,
            String generatorURL,
            String fingerprint
    ) {
    }
}
