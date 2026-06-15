package com.team2.talkking.errorops.alert;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/alerts")
public class AlertWebhookController {

    private final AlertWorkflowService alertWorkflowService;

    public AlertWebhookController(AlertWorkflowService alertWorkflowService) {
        this.alertWorkflowService = alertWorkflowService;
    }

    @PostMapping
    public ResponseEntity<AlertAnalysisResponse> receive(@RequestBody AlertmanagerWebhookRequest request) {
        return ResponseEntity.ok(alertWorkflowService.handle(request));
    }
}
