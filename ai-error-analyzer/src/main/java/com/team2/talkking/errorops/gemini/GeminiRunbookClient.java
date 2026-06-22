package com.team2.talkking.errorops.gemini;

import com.team2.talkking.errorops.alert.AlertContext;
import com.team2.talkking.errorops.kubernetes.KubernetesDiagnostics;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.util.retry.Retry;

@Component
public class GeminiRunbookClient {

    private static final Logger logger = LoggerFactory.getLogger(GeminiRunbookClient.class);

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
            @Value("${errorops.gemini.timeout-seconds:30}") long timeoutSeconds,
            @Value("${errorops.gemini.max-retries:3}") int maxRetries,
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
            logger.warn("Gemini API key not configured, using fallback");
            return fallbackCodexTask(context, diagnostics);
        }

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
                    // 429(Too Many Requests)와 5xx 에러 처리
                    .retryWhen(Retry.backoff(maxRetries, Duration.ofMillis(initialDelayMs))
                            .filter(throwable -> {
                                if (throwable instanceof WebClientResponseException exception) {
                                    int status = exception.getStatusCode().value();
                                    // ✅ 버그 1 수정: null 안전성 추가
                                    String body = Objects.toString(
                                            exception.getResponseBodyAsString(), "");

                                    logger.warn("Gemini API error - Status: {}, Body length: {}",
                                            status, body.length());

                                    // ✅ 버그 2 수정: 고정 "Attempt: 3" 제거
                                    if (body.contains("RESOURCE_EXHAUSTED")) {
                                        logger.error("❌ 할당량 부족! 재시도 중단");
                                        return false;  // 재시도하지 말것
                                    } else if (body.contains("RATE_LIMIT")) {
                                        logger.warn("⚠️ Rate Limit 감지 - 재시도 예정");
                                        return true;   // 재시도
                                    } else if (status >= 500 && status < 600) {
                                        logger.warn("🟡 Server Error ({}) - 재시도 예정", status);
                                        return true;   // 재시도
                                    } else if (status == 429) {
                                        logger.warn("🟡 429 Rate Limit - 재시도 예정");
                                        return true;   // 재시도
                                    } else {
                                        logger.error("❌ 복구 불가능한 오류 ({}): {}", status, body);
                                        return false;  // 재시도하지 말것
                                    }
                                }
                                // 타임아웃 등 네트워크 에러도 재시도
                                if (throwable instanceof java.util.concurrent.TimeoutException) {
                                    logger.warn("⏱️ Timeout - 재시도 예정");
                                    return true;
                                }

                                logger.error("📌 기타 에러 - 타입: {}",
                                        throwable.getClass().getSimpleName());
                                return false;
                            })
                            .onRetryExhaustedThrow((retrySignal, throwable) -> {
                                // ✅ 버그 2 수정: 정확한 재시도 횟수 로깅
                                logger.error("❌ Gemini API 최종 실패 - 총 {} 회 시도",
                                        retrySignal.totalRetries());
                                return throwable;
                            })
                    )
                    .block();

            String text = response == null ? "" : response.firstText();
            if (text != null && !text.isBlank()) {
                logger.info("✅ Successfully generated runbook from Gemini API");
                return text;
            }

            logger.warn("⚠️ Gemini API returned empty response, using fallback");
            return fallbackCodexTask(context, diagnostics);

        } catch (WebClientResponseException exception) {
            int status = exception.getStatusCode().value();
            // ✅ 버그 1 수정: null 안전성 추가
            String body = Objects.toString(exception.getResponseBodyAsString(), "");

            logger.error("❌ WebClientResponseException - Status: {}, Body length: {}",
                    status, body.length());

            return fallbackCodexTask(context, diagnostics) +
                    "\n\n진단 수집 에러:\nGemini API failed with status " + status;

        } catch (java.util.concurrent.TimeoutException exception) {
            logger.error("⏱️ Gemini API request timeout after {} seconds", timeout.getSeconds());
            return fallbackCodexTask(context, diagnostics) +
                    "\n\n진단 수집 에러:\nGemini API timeout exceeded (" + timeout.getSeconds() + "s)";

        } catch (Exception exception) {
            logger.error("❌ Unexpected error calling Gemini API - Type: {}, Message: {}",
                    exception.getClass().getSimpleName(), exception.getMessage());
            return fallbackCodexTask(context, diagnostics) +
                    "\n\n진단 수집 에러:\n" + exception.getClass().getSimpleName() +
                    ": " + exception.getMessage();
        }
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
                마지막에 PR 링크, 변경 요약, 검증 결과, 남은 리스크를 한국어로 알려줘.

                The prompt must include these alert details:
                - alert name, severity, namespace, pod, container
                - Kubernetes pod phase
                - recent Kubernetes events
                - recent logs
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
                truncate(diagnostics.recentLogs(), 6000),
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
                truncate(diagnostics.recentLogs(), 6000),
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