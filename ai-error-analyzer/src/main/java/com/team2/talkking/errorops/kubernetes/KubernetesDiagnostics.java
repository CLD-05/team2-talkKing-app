package com.team2.talkking.errorops.kubernetes;

import java.util.List;

public record KubernetesDiagnostics(
        String podPhase,
        List<String> recentEvents,
        String recentLogs,
        String errorMessage
) {
    public static KubernetesDiagnostics unavailable(String errorMessage) {
        return new KubernetesDiagnostics("", List.of(), "", errorMessage);
    }
}
