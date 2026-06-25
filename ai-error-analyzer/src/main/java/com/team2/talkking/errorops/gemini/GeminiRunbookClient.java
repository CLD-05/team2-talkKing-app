package com.team2.talkking.errorops.gemini;

import com.team2.talkking.errorops.alert.AlertContext;
import com.team2.talkking.errorops.kubernetes.KubernetesDiagnostics;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

@Component
public class GeminiRunbookClient {

    private final WebClient webClient;
    private final String apiKey;
    private final String model;
    private final Duration timeout;
    private final int maxRetries;
    private final long initialDelayMs;

    public GeminiRunbookClient(
            WebClient.Builder webClientBuilder,
            @Value("${errorops.gemini.api-key:}") String apiKey,
            @Value("${errorops.gemini.model:gemini-flash-latest}") String model,
            @Value("${errorops.gemini.timeout-seconds:60}") long timeoutSeconds,
            @Value("${errorops.gemini.max-retries:2}") int maxRetries,
            @Value("${errorops.gemini.initial-delay-ms:1000}") long initialDelayMs
    ) {
        this.webClient = webClientBuilder.baseUrl("https://generativelanguage.googleapis.com").build();
        this.apiKey = apiKey;
        this.model = model;
        this.timeout = Duration.ofSeconds(timeoutSeconds);
        this.maxRetries = maxRetries;
        this.initialDelayMs = initialDelayMs;
    }

    public String generateRunbook(AlertContext context, KubernetesDiagnostics diagnostics) {
        String prompt = buildPrompt(context, diagnostics);
        if (apiKey == null || apiKey.isBlank()) {
            return fallbackCodexTask(context, diagnostics);
        }

        try {
            String text = webClient.post()
                    .uri("/v1beta/models/{model}:generateContent?key={apiKey}", model, apiKey)
                    .bodyValue(Map.of(
                            "contents", List.of(Map.of(
                                    "parts", List.of(Map.of("text", prompt))
                            ))
                    ))
                    .retrieve()
                    .bodyToMono(GeminiResponse.class)
                    .retryWhen(Retry.backoff(maxRetries, Duration.ofMillis(initialDelayMs))
                            .filter(throwable -> isRetryable(throwable))
                            .maxBackoff(Duration.ofMillis(initialDelayMs * maxRetries))
                    )
                    .timeout(timeout)
                    .map(response -> response == null ? "" : (response.firstText() == null ? "" : response.firstText()))
                    .onErrorResume(throwable -> {
                        String errorMsg = throwable.getMessage();
                        return Mono.just(
                            fallbackCodexTask(context, diagnostics) + "\n\nGemini call failed: " + 
                            (errorMsg != null ? errorMsg : "Unknown error")
                        );
                    })
                    .block();
            
            return (text != null && !text.isBlank()) ? text : fallbackCodexTask(context, diagnostics);

        } catch (Exception exception) {
            String errorMsg = exception.getMessage();
            return fallbackCodexTask(context, diagnostics) + "\n\nGemini call failed: " + 
                (errorMsg != null ? errorMsg : "Unknown error");
        }
    }

    // ✅ HTTP 상태 코드 기반 재시도 판단
    private boolean isRetryable(Throwable throwable) {
        if (throwable instanceof WebClientResponseException) {
            WebClientResponseException ex = (WebClientResponseException) throwable;
            int statusCode = ex.getStatusCode().value();
            return statusCode == 503 || statusCode == 504 || statusCode == 429;
        }
        
        // Timeout 예외도 재시도 가능
        if (throwable instanceof java.util.concurrent.TimeoutException) {
            return true;
        }
        
        // 네트워크 에러 재시도 가능
        if (throwable.getCause() instanceof java.io.IOException) {
            return true;
        }
        
        return false;
    }

    private String buildPrompt(AlertContext context, KubernetesDiagnostics diagnostics) {
        return """
                You are an ErrorOps automation planner.
                Create a task message that will be consumed by a local Codex runner.
                Output ONLY the task message. Do not wrap it in Markdown fences.
                Write the prompt section and your analysis in Korean.
                
                CRITICAL INSTRUCTION:
                Your entire response MUST start exactly with "[CODEX_TASK]".
                Do not output any conversational greetings, introductions, or explanations.
                Do not fabricate or hallucinate logs; use ONLY the provided INPUT_LOGS verbatim.
                DO NOT include any logs in the middle of your [CODEX_TASK] output.
                DO NOT include logs in the prompt section.
                You MUST put all logs ONLY in a [RAW LOGS] section at the absolute end of your entire output.
                
                === REQUIRED OUTPUT FORMAT START ===
                [CODEX_TASK]
                id: alert-<alert_id>
                title: <short Korean title describing the issue>
                severity: <severity>
                
                prompt:
                너는 TalkKing 프로젝트의 로컬 Codex야.
                우선 C:\\CE\\talkKing 아래의 team2-talkKing-app, team2-talkKing-config, team2-talkKing-infra 레포를 각각 dev 브랜치 최신 상태로 pull 해줘.
                아래 장애 정보를 바탕으로 필요한 수정 후보를 찾고, app/config/infra 중 필요한 파일만 최소 변경해줘.
                변경 전후로 관련 파일을 확인하고, 가능한 검증 명령을 실행해줘.
                dev 브랜치에 직접 push하지 말고 새 브랜치 chore/errorops-<alert_id>로 커밋하고 push한 뒤 draft가 아닌 일반 pull request까지 생성해줘.
                파괴적인 인프라 작업, 네임스페이스 삭제, 강제 리셋, dev 브랜치 직접 push, IAM 변경 작업은 하지 마.
                마지막에 PR 링크, 변경 요약, 검증 결과, 남은 리스크를 한국어로 알려줘.
                
                [Alert Details]
                alert name: <alert_name>
                severity: <severity>
                namespace: <namespace>
                pod: <pod>
                container: <container>
                
                [Kubernetes Diagnostic Details]
                pod phase: <pod_phase>
                recent Kubernetes events: <analyze and summarize the root cause from events>
                
                [RAW LOGS]
                <paste INPUT_LOGS here exactly as provided, no modifications>
                
                [Summary]
                <provide brief error analysis and suggested next steps based on the logs and events>
                
                === REQUIRED OUTPUT FORMAT END ===
                
                Input Data (fill the template above using ONLY this data):
                INPUT_ALERT_ID: %s
                INPUT_ALERT_NAME: %s
                INPUT_SEVERITY: %s
                INPUT_NAMESPACE: %s
                INPUT_POD: %s
                INPUT_CONTAINER: %s
                INPUT_POD_PHASE: %s
                INPUT_SUMMARY_DESC: %s
                INPUT_EVENTS:
                %s
                INPUT_LOGS:
                %s
                === END OF INPUT DATA ===
                """.formatted(
                safeId(context.fingerprint()),
                context.alertName(),
                context.severity(),
                context.namespace(),
                context.pod(),
                context.container(),
                diagnostics.podPhase(),
                context.summary() + (context.description() != null ? " - " + context.description() : ""),
                formatEvents(diagnostics.recentEvents()),
                truncate(diagnostics.recentLogs(), 50000)
        );
    }

    private String fallbackCodexTask(AlertContext context, KubernetesDiagnostics diagnostics) {
        return """
                [CODEX_TASK]
                id: alert-%s
                title: %s
                severity: %s

                prompt:
                너는 TalkKing 프로젝트의 로컬 Codex야.
                우선 C:\\CE\\talkKing 아래의 team2-talkKing-app, team2-talkKing-config, team2-talkKing-infra 레포를 각각 dev 브랜치 최신 상태로 pull 해줘.
                아래 장애 정보를 바탕으로 필요한 수정 후보를 찾고, app/config/infra 중 필요한 파일만 최소 변경해줘.
                변경 전후로 관련 파일을 확인하고, 가능한 검증 명령을 실행해줘.
                dev 브랜치에 직접 push하지 말고 새 브랜치 chore/errorops-%s 로 커밋하고 push한 뒤 draft가 아닌 일반 pull request까지 생성해줘.
                파괴적인 인프라 작업, 네임스페이스 삭제, 강제 리셋, dev 브랜치 직접 push, IAM 변경 작업은 하지 마.
                마지막에 PR 링크, 변경 요약, 검증 결과, 남은 리스크를 한국어로 알려줘.

                [Alert Details]
                alert name: %s
                severity: %s
                namespace: %s
                pod: %s
                container: %s

                [Kubernetes Diagnostic Details]
                pod phase: %s
                recent Kubernetes events:
                %s

                [RAW LOGS]
                %s

                [Summary]
                %s
                """.formatted(
                safeId(context.fingerprint()),
                context.alertName() + " 장애 원인 분석 및 수정 후보 작성",
                context.severity(),
                safeId(context.fingerprint()),
                context.alertName(),
                context.severity(),
                context.namespace(),
                context.pod(),
                context.container(),
                diagnostics.podPhase(),
                formatEvents(diagnostics.recentEvents()),
                truncate(diagnostics.recentLogs(), 50000),
                diagnostics.errorMessage() == null || diagnostics.errorMessage().isBlank() ? 
                    "Gemini API 호출 실패로 자동 생성된 기본 진단" : 
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
        // ✅ StringBuilder 기반 효율적인 문자열 병합
        return events.stream()
                .map(event -> "- " + event)
                .collect(Collectors.joining("\n"));
    }

    private String safeId(String value) {
        if (value == null || value.isBlank()) {
            return "unknown";
        }
        return value.replaceAll("[^a-zA-Z0-9._-]", "-");
    }
}