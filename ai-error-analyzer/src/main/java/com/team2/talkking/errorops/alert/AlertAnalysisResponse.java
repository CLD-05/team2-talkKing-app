package com.team2.talkking.errorops.alert;

import java.util.List;

public record AlertAnalysisResponse(
        int receivedAlerts,
        int analyzedAlerts,
        List<AlertResult> results
) {
    public record AlertResult(
            String fingerprint,
            String alertName,
            String namespace,
            String pod,
            String severity,
            boolean slackSent,
            String runbook
    ) {
    }
}
