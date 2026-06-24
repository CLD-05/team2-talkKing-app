package com.team2.talkking.errorops.gemini;

import com.team2.talkking.errorops.alert.AlertContext;
import com.team2.talkking.errorops.kubernetes.KubernetesDiagnostics;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

@Component
public class GeminiRunbookClient {

    private final WebClient webClient;
    private final String apiKey;
    private final String model;
    private final Duration timeout;

    public GeminiRunbookClient(
            WebClient.Builder webClientBuilder,
            @Value("${errorops.gemini.api-key:}") String apiKey,
            @Value("${errorops.gemini.model:gemini-flash-latest}") String model,
            @Value("${errorops.gemini.timeout-seconds:60}") long timeoutSeconds
    ) {
        this.webClient = webClientBuilder.baseUrl("https://generativelanguage.googleapis.com").build();
        this.apiKey = apiKey;
        this.model = model;
        this.timeout = Duration.ofSeconds(timeoutSeconds);
    }

    public String generateRunbook(AlertContext context, KubernetesDiagnostics diagnostics) {
        String prompt = buildPrompt(context, diagnostics);
        if (apiKey == null || apiKey.isBlank()) {
            return fallbackCodexTask(context, diagnostics);
        }

        // ✅ 재시도 로직 추가
        int maxRetries = 2;
        long initialDelayMs = 1000;

        for (int attempt = 1; attempt <= maxRetries + 1; attempt++) {
            try {
                GeminiResponse response = webClient.post()
                        .uri("/v1beta/models/{model}:generateContent?key={apiKey}", model, apiKey)
                        .bodyValue(Map.of(
                                "contents", List.of(Map.of(
                                        "parts", List.of(Map.of("text", prompt))
                                ))
                        ))
                        .retrieve()
                        .bodyToMono(GeminiResponse.class)
                        .timeout(timeout)
                        .block();

                String text = response == null ? "" : response.firstText();
                if (text != null && !text.isBlank()) {
                    return text;
                }
                return fallbackCodexTask(context, diagnostics);

            } catch (Exception exception) {
                String errorMsg = exception.getMessage();
                
                // 재시도 가능한 상황인지 확인 (503, Timeout)
                if (attempt < maxRetries + 1 && (errorMsg.contains("503") || errorMsg.contains("Timeout"))) {
                    long delayMs = initialDelayMs * attempt;
                    try {
                        Thread.sleep(delayMs);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                    }
                } else {
                    // 마지막 시도 또는 재시도 불가능한 에러
                    return fallbackCodexTask(context, diagnostics) + "\n\nGemini call failed: " + errorMsg;
                }
            }
        }

        return fallbackCodexTask(context, diagnostics) + "\n\nGemini call failed: Max retries exceeded";
    }

    private String buildPrompt(AlertContext context, KubernetesDiagnostics diagnostics) {
        return """
                You are an ErrorOps automation planner.
                Create a Slack message that will be consumed by a local Codex runner.

                Output only the task message. Do not wrap it in Markdown fences.
                The output must start with [CODEX_TASK].
                Write the prompt section in Korean because the local operator works in Korean.

                Alert:
                - name: %s
                - severity: %s
                - namespace: %s
                - pod: %s
                - container: %s
                - summary: %s
                - description: %s

                Kubernetes diagnostics:
                - pod phase: %s
                - recent events:
                %s
                - logs:
                %s

                Required output format:

                [CODEX_TASK]
                id: alert-%s
                title: <short Korean title>
                severity: %s

                prompt:
                너는 TalkKing 프로젝트의 로컬 Codex야.
                우선 C:\\CE\\talkKing 아래의 team2-talkKing-app, team2-talkKing-config, team2-talkKing-infra 레포를 각각 dev 브랜치 최신 상태로 pull 해줘.
                아래 장애 정보를 바탕으로 필요한 수정 후보를 찾고, app/config/infra 중 필요한 파일만 최소 변경해줘.
                변경 전후로 관련 파일을 확인하고, 가능한 검증 명령을 실행해줘.
                dev 브랜치에 직접 push하지 말고 새 브랜치 chore/errorops-<alert id>로 커밋하고 push한 뒤 draft가 아닌 일반 pull request까지 생성해줘.
                파괴적인 인프라 작업, 네임스페이스 삭제, 강제 리셋, dev 브랜치 직접 push, IAM 변경 작업은 하지 마.
                마지막에 PR 링크, 변경 요약, 검증 결과, 남은 리스크를 한국어로 알려주고,
                슬랙 메시지 중간에는 로그를 포함하지 마. 대신 맨 마지막에 반드시 [RAW LOGS] 섹션을 만들어 네가 전달받은 전체 로그 원본을 그대로 덧붙여줘.
                
                The prompt must include these alert details:
                - alert name, severity, namespace, pod, container
                - Kubernetes pod phase
                - recent Kubernetes events
                """.formatted(
                context.alertName(),
                context.severity(),
                context.namespace(),
                context.pod(),
                context.container(),
                context.summary(),
                context.description(),
                diagnostics.podPhase(),
                formatEvents(diagnostics.recentEvents()),
                truncate(diagnostics.recentLogs(), 50000),
                safeId(context.fingerprint()),
                context.severity()
        );
    }

    private String fallbackCodexTask(AlertContext context, KubernetesDiagnostics diagnostics) {
        String taskId = safeId(context.fingerprint());
        return """
                [CODEX_TASK]
                id: alert-%s
                title: %s 장애 원인 분석 및 수정 후보 작성
                severity: %s

                prompt:
                너는 TalkKing 프로젝트의 로컬 Codex야.
                우선 C:\\CE\\talkKing 아래의 team2-talkKing-app, team2-talkKing-config, team2-talkKing-infra 레포를 각각 dev 브랜치 최신 상태로 pull 해줘.
                아래 Kubernetes alert를 기준으로 app/config/infra 중 수정이 필요한 부분을 찾아줘.
                필요한 파일만 최소 변경하고, dev 브랜치에 직접 push하지 말고 새 브랜치 chore/errorops-%s 로 커밋하고 push한 뒤 draft가 아닌 일반 pull request까지 생성해줘.
                파괴적인 인프라 작업, 네임스페이스 삭제, 강제 리셋, dev 브랜치 직접 push, IAM 변경 작업은 하지 마.
                마지막에 PR 링크, 변경 요약, 검증 결과, 남은 리스크를 한국어로 알려줘.

                장애 정보:
                - alert: %s
                - severity: %s
                - namespace: %s
                - pod: %s
                - container: %s
                - summary: %s
                - description: %s
                - pod phase: %s

                최근 Kubernetes Events:
                %s

                최근 로그:
                %s

                진단 수집 에러:
                %s
                """.formatted(
                taskId,
                context.alertName(),
                context.severity(),
                taskId,
                context.alertName(),
                context.severity(),
                context.namespace(),
                context.pod(),
                context.container(),
                context.summary(),
                context.description(),
                diagnostics.podPhase(),
                formatEvents(diagnostics.recentEvents()),
                truncate(diagnostics.recentLogs(), 50000),
                diagnostics.errorMessage()
        );
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value == null ? "" : value;
        }
        return value.substring(value.length() - maxLength);
    }

    private String formatEvents(List<String> events) {
        if (events == null || events.isEmpty()) {
            return "- No recent pod events found.";
        }
        return events.stream()
                .map(event -> "- " + event)
                .reduce((left, right) -> left + "\n" + right)
                .orElse("- No recent pod events found.");
    }

    private String safeId(String value) {
        if (value == null || value.isBlank()) {
            return "unknown";
        }
        return value.replaceAll("[^a-zA-Z0-9._-]", "-");
    }
}