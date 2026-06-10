package com.team2.talkking.errorops.kubernetes;

import com.team2.talkking.errorops.alert.AlertContext;
import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.Configuration;
import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.openapi.models.CoreV1EventList;
import io.kubernetes.client.openapi.models.V1Pod;
import io.kubernetes.client.util.Config;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class KubernetesDiagnosticService {

    private final int logTailLines;

    public KubernetesDiagnosticService(@Value("${errorops.kubernetes.log-tail-lines:120}") int logTailLines) {
        this.logTailLines = logTailLines;
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
        String fieldSelector = "involvedObject.name=" + context.pod();
        CoreV1EventList eventList = api.listNamespacedEvent(context.namespace())
                .fieldSelector(fieldSelector)
                .execute();

        if (eventList.getItems() == null) {
            return List.of();
        }

        return eventList.getItems().stream()
                .map(event -> "%s %s".formatted(
                        event.getReason() == null ? "UnknownReason" : event.getReason(),
                        event.getMessage() == null ? "" : event.getMessage()
                ))
                .toList();
    }
}
