# ai-error-analyzer

Alertmanager webhook receiver for TalkKing error operations.

## Flow

1. Receive Alertmanager webhook at `POST /api/v1/alerts`
2. Extract alert labels such as `namespace`, `pod`, `container`, and `severity`
3. Read Kubernetes pod status, recent events, and recent logs
4. Generate a runbook with Gemini
5. Send the runbook to Slack

## Runtime Configuration

```properties
GEMINI_API_KEY=
SLACK_WEBHOOK_URL=
```

Optional application properties:

```properties
errorops.kubernetes.log-tail-lines=120
errorops.gemini.model=gemini-1.5-flash
```

If `GEMINI_API_KEY` is empty, the app returns a fallback runbook.
If `SLACK_WEBHOOK_URL` is empty, Slack sending is skipped.

## Local Endpoints

```text
GET  /actuator/health
POST /api/v1/alerts
```
