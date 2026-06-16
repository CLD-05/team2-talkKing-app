package com.team2.talkking.errorops.kubernetes;

import com.team2.talkking.errorops.alert.AlertContext;
import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.Configuration;
import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.openapi.models.CoreV1EventList;
import io.kubernetes.client.openapi.models.V1Pod;
import io.kubernetes.client.util.Config;
import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class KubernetesDiagnosticService {

    private final int logTailLines;
    private final int eventLimit;

    public KubernetesDiagnosticService(
            @Value("${errorops.kubernetes.log-tail-lines:120}") int logTailLines,
            @Value("${errorops.kubernetes.event-limit:20}") int eventLimit
    ) {
        this.logTailLines = logTailLines;
        this.eventLimit = eventLimit;
    }

    public KubernetesDiagnostics collect(AlertContext context) {
        if (context.pod() == null || context.pod().isBlank()) {
            return KubernetesDiagnostics.unavailable("Alert payload does not include a pod label.");
        }

        try {
            CoreV1Api api = coreV1Api();
            V1Pod pod = api.readNamespacedPod(context.pod(), context.namespace()).execute();
            String phase = pod.getStatus() == null ? "" : String.valueOf(pod.getStatus().getPhase());
            String logs = readLogs(api, context);
            List<String> events = readEvents(api, context);

            return new KubernetesDiagnostics(phase, events, logs, "");
        } catch (Exception exception) {
            return KubernetesDiagnostics.unavailable(exception.getMessage());
        }
    }

    private CoreV1Api coreV1Api() throws Exception {
        ApiClient client = Config.defaultClient();
        Configuration.setDefaultApiClient(client);
        return new CoreV1Api(client);
    }

    private String readLogs(CoreV1Api api, AlertContext context) throws Exception {
        CoreV1Api.APIreadNamespacedPodLogRequest request =
                api.readNamespacedPodLog(context.pod(), context.namespace())
                        .tailLines(logTailLines)
                        .timestamps(true);

        if (context.container() != null && !context.container().isBlank()) {
            request.container(context.container());
        }

        return request.execute();
    }

    private List<String> readEvents(CoreV1Api api, AlertContext context) throws Exception {
        String fieldSelector = "involvedObject.kind=Pod,involvedObject.name=" + context.pod();
        CoreV1EventList eventList = api.listNamespacedEvent(context.namespace())
                .fieldSelector(fieldSelector)
                .execute();

        if (eventList.getItems() == null) {
            return List.of();
        }

        return eventList.getItems().stream()
                .sorted(Comparator.comparing(
                        event -> eventTime(event),
                        Comparator.nullsLast(Comparator.reverseOrder())
                ))
                .limit(eventLimit)
                .map(event -> "%s %s %s count=%s lastSeen=%s - %s".formatted(
                        event.getType() == null ? "UnknownType" : event.getType(),
                        event.getReason() == null ? "UnknownReason" : event.getReason(),
                        event.getInvolvedObject() == null || event.getInvolvedObject().getName() == null
                                ? context.pod()
                                : event.getInvolvedObject().getName(),
                        event.getCount() == null ? 1 : event.getCount(),
                        eventTime(event) == null ? "unknown" : eventTime(event),
                        event.getMessage() == null ? "" : event.getMessage()
                ))
                .toList();
    }

    private OffsetDateTime eventTime(io.kubernetes.client.openapi.models.CoreV1Event event) {
        if (event.getLastTimestamp() != null) {
            return event.getLastTimestamp();
        }
        if (event.getEventTime() != null) {
            return event.getEventTime();
        }
        if (event.getFirstTimestamp() != null) {
            return event.getFirstTimestamp();
        }
        return event.getMetadata() == null ? null : event.getMetadata().getCreationTimestamp();
    }
}
