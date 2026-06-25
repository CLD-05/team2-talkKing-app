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
// 1. 엔티티 클래스 위에 시퀀스 제네레이터를 선언해 줍니다.
@SequenceGenerator(
    name = "alert_history_seq_gen",
    sequenceName = "alert_history_id_seq", // DB에 만들어둔 시퀀스 이름과 매칭
    allocationSize = 1
)
public class AlertHistory {

    @Id
    // 2. id 컬럼 위에 시퀀스 전략을 지정해 줍니다. 이렇게 하면 null 대신 시퀀스 번호가 박혀서 나갑니다.
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "alert_history_seq_gen")
    private Long id; 

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