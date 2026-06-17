# ai-error-analyzer

Alertmanager webhook receiver for TalkKing error operations.

## Flow

1. Receive Alertmanager webhook at `POST /api/v1/alerts`
2. Extract alert labels such as `namespace`, `pod`, `container`, and `severity`
3. Read Kubernetes pod status, recent events, and recent logs
4. Generate a Codex task prompt with Gemini
5. Send the task to Slack for local Codex runner approval

## Runtime Configuration

```properties
GEMINI_API_KEY=
SLACK_GENERAL_WEBHOOK_URL=
SLACK_CRITICAL_WEBHOOK_URL=
SLACK_WARNING_WEBHOOK_URL=
SLACK_INFO_WEBHOOK_URL=
SLACK_CODEX_QUEUE_WEBHOOK_URL=
```

Optional application properties:

```properties
errorops.kubernetes.log-tail-lines=120
errorops.kubernetes.event-limit=20
errorops.gemini.model=gemini-1.5-flash
```

If `GEMINI_API_KEY` is empty, the app returns a fallback `[CODEX_TASK]` prompt.
If `SLACK_CODEX_QUEUE_WEBHOOK_URL` is set, Codex tasks are sent to that queue channel.
If the Codex queue webhook is empty, alerts with `critical`, `warning`, and `info` severity use their matching webhook first.
Other severities are sent to `SLACK_GENERAL_WEBHOOK_URL`.
If a severity-specific webhook is empty, the alert falls back to `SLACK_GENERAL_WEBHOOK_URL`.
If the selected webhook is empty, Slack sending is skipped.

## Local Endpoints

```text
GET  /actuator/health
POST /api/v1/alerts
```
