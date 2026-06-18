package com.team2.talkking.errorops.slack;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.team2.talkking.errorops.alert.AlertContext;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.client.WebClient;

@Component
public class SlackNotifier {

    private final WebClient webClient;
    private final String generalWebhookUrl;
    private final String criticalWebhookUrl;
    private final String warningWebhookUrl;
    private final String infoWebhookUrl;
    private final String codexQueueWebhookUrl;
    private final String codexBotToken;
    private final String codexChannelId;

    public SlackNotifier(
            WebClient.Builder webClientBuilder,
            @Value("${errorops.slack.general-webhook-url:}") String generalWebhookUrl,
            @Value("${errorops.slack.critical-webhook-url:}") String criticalWebhookUrl,
            @Value("${errorops.slack.warning-webhook-url:}") String warningWebhookUrl,
            @Value("${errorops.slack.info-webhook-url:}") String infoWebhookUrl,
            @Value("${errorops.slack.codex-queue-webhook-url:}") String codexQueueWebhookUrl,
            @Value("${errorops.slack.codex-bot-token:}") String codexBotToken,
            @Value("${errorops.slack.codex-channel-id:}") String codexChannelId
    ) {
        this.webClient = webClientBuilder.build();
        this.generalWebhookUrl = generalWebhookUrl;
        this.criticalWebhookUrl = criticalWebhookUrl;
        this.warningWebhookUrl = warningWebhookUrl;
        this.infoWebhookUrl = infoWebhookUrl;
        this.codexQueueWebhookUrl = codexQueueWebhookUrl;
        this.codexBotToken = codexBotToken;
        this.codexChannelId = codexChannelId;
    }

    public boolean send(AlertContext context, String runbook) {
        if (isCodexTask(runbook) && hasCodexFileUploadConfig()) {
            return sendCodexTaskFile(context, runbook);
        }

        String selectedWebhookUrl = selectWebhookUrl(context);
        if (selectedWebhookUrl == null || selectedWebhookUrl.isBlank()) {
            return false;
        }

        String message = isCodexQueueSelected(selectedWebhookUrl)
                ? runbook
                : """
                        *[%s] %s*
                        namespace: `%s`
                        pod: `%s`

                        %s
                        """.formatted(
                        context.severity(),
                        context.alertName(),
                        context.namespace(),
                        context.pod(),
                        runbook
                );

        try {
            webClient.post()
                    .uri(selectedWebhookUrl)
                    .bodyValue(Map.of("text", message))
                    .retrieve()
                    .toBodilessEntity()
                    .timeout(Duration.ofSeconds(10))
                    .block();
            return true;
        } catch (Exception exception) {
            return false;
        }
    }

    private boolean sendCodexTaskFile(AlertContext context, String prompt) {
        String taskId = safeId(context.fingerprint());
        String filename = "alert-%s-prompt.txt".formatted(taskId);
        byte[] bytes = prompt.getBytes(StandardCharsets.UTF_8);

        try {
            UploadUrlResponse uploadUrlResponse = requestUploadUrl(filename, bytes.length);
            if (uploadUrlResponse == null || !uploadUrlResponse.ok() || uploadUrlResponse.uploadUrl() == null) {
                return false;
            }

            uploadPromptBytes(uploadUrlResponse.uploadUrl(), bytes);

            CompleteUploadResponse completeUploadResponse = completeUpload(
                    uploadUrlResponse.fileId(),
                    filename,
                    buildCodexTaskMessage(context, filename)
            );
            return completeUploadResponse != null && completeUploadResponse.ok();
        } catch (Exception exception) {
            return false;
        }
    }

    private UploadUrlResponse requestUploadUrl(String filename, int length) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("filename", filename);
        form.add("length", String.valueOf(length));
        form.add("snippet_type", "text");

        return webClient.post()
                .uri("https://slack.com/api/files.getUploadURLExternal")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + codexBotToken)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .bodyValue(form)
                .retrieve()
                .bodyToMono(UploadUrlResponse.class)
                .timeout(Duration.ofSeconds(10))
                .block();
    }

    private void uploadPromptBytes(String uploadUrl, byte[] bytes) {
        webClient.post()
                .uri(uploadUrl)
                .contentType(MediaType.TEXT_PLAIN)
                .bodyValue(bytes)
                .retrieve()
                .toBodilessEntity()
                .timeout(Duration.ofSeconds(20))
                .block();
    }

    private CompleteUploadResponse completeUpload(String fileId, String title, String initialComment) {
        return webClient.post()
                .uri("https://slack.com/api/files.completeUploadExternal")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + codexBotToken)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of(
                        "channel_id", codexChannelId,
                        "initial_comment", initialComment,
                        "files", List.of(Map.of(
                                "id", fileId,
                                "title", title
                        ))
                ))
                .retrieve()
                .bodyToMono(CompleteUploadResponse.class)
                .timeout(Duration.ofSeconds(10))
                .block();
    }

    private String buildCodexTaskMessage(AlertContext context, String filename) {
        return """
                [CODEX_TASK]
                id: alert-%s
                title: %s 장애 원인 분석 및 수정 후보 작성
                severity: %s
                prompt_file: %s

                승인: :white_check_mark: 2명
                거절: :x:
                """.formatted(
                safeId(context.fingerprint()),
                context.alertName(),
                context.severity(),
                filename
        );
    }

    private String selectWebhookUrl(AlertContext context) {
        if (codexQueueWebhookUrl != null && !codexQueueWebhookUrl.isBlank()) {
            return codexQueueWebhookUrl;
        }

        String severity = context.severity() == null ? "" : context.severity().toLowerCase();

        return switch (severity) {
            case "critical" -> firstNonBlank(criticalWebhookUrl, generalWebhookUrl);
            case "warning" -> firstNonBlank(warningWebhookUrl, generalWebhookUrl);
            case "info" -> firstNonBlank(infoWebhookUrl, generalWebhookUrl);
            default -> generalWebhookUrl;
        };
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return "";
    }

    private boolean isCodexQueueSelected(String selectedWebhookUrl) {
        return codexQueueWebhookUrl != null
                && !codexQueueWebhookUrl.isBlank()
                && codexQueueWebhookUrl.equals(selectedWebhookUrl);
    }

    private boolean isCodexTask(String value) {
        return value != null && value.contains("[CODEX_TASK]");
    }

    private boolean hasCodexFileUploadConfig() {
        return codexBotToken != null && !codexBotToken.isBlank()
                && codexChannelId != null && !codexChannelId.isBlank();
    }

    private String safeId(String value) {
        if (value == null || value.isBlank()) {
            return "unknown";
        }
        return value.replaceAll("[^a-zA-Z0-9._-]", "-");
    }

    private record UploadUrlResponse(
            boolean ok,
            @JsonProperty("upload_url") String uploadUrl,
            @JsonProperty("file_id") String fileId,
            String error
    ) {
    }

    private record CompleteUploadResponse(
            boolean ok,
            String error
    ) {
    }
}
