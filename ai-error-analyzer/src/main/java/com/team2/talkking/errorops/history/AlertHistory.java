package com.team2.talkking.errorops.history;

import jakarta.persistence.*;
import java.io.Serializable;
import java.time.LocalDateTime;
import lombok.*;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Table(name = "alert_history")
@IdClass(AlertHistory.AlertHistoryId.class)
public class AlertHistory {

    @Id
    private Long id; // 🚀 DB의 SERIAL이 자동으로 매핑되므로 @GeneratedValue를 제거하여 파티션 복합키 충돌 방지

    @Id
    private LocalDateTime createdAt;

    private String fingerprint;
    private String alertName;
    private String namespace;
    private String pod;
    private String workload;
    private String container;
    private String severity;

    @Column(length = 1000)
    private String summary;

    @Column(columnDefinition = "TEXT")
    private String description;

    private String podPhase;

    @Column(columnDefinition = "TEXT")
    private String recentEvents;

    @Column(columnDefinition = "TEXT")
    private String recentLogs;

    @Column(columnDefinition = "TEXT")
    private String k8sErrorMessage;

    private Boolean slackSent;

    @Column(columnDefinition = "TEXT")
    private String runbook;

    @PrePersist
    public void prePersist() {
        if (this.createdAt == null) {
            this.createdAt = LocalDateTime.now();
        }
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AlertHistoryId implements Serializable {
        private Long id;
        private LocalDateTime createdAt;
    }
}