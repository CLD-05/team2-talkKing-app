package com.team2.talkking.errorops.history;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.*;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Table(
	    name = "alert_history",
	    indexes = {
	        @Index(name = "idx_alert_history_created_at", columnList = "createdAt"),
	        @Index(name = "idx_alert_history_namespace", columnList = "namespace"),
	        @Index(name = "idx_alert_history_alert_name", columnList = "alertName")
	    }
	)
public class AlertHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String fingerprint;

    private String alertName;

    private String namespace;

    private String pod;

    private String workload;

    private String container;

    private String severity;

    @Column(length = 1000)
    private String summary;

    // 🚀 에러 로그 및 스택 트레이스가 길어질 것을 대비해 TEXT 타입으로 변경하여 에러 방지
    @Column(columnDefinition = "TEXT")
    private String description;

    private String podPhase;

    private Boolean slackSent;

    @Column(columnDefinition = "TEXT")
    private String runbook;

    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        this.createdAt = LocalDateTime.now();
    }
}