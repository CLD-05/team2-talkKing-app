package com.team2.talkking.errorops.history;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AlertHistoryRepository
        extends JpaRepository<AlertHistory, Long> {

    List<AlertHistory> findTop20ByOrderByCreatedAtDesc();

    List<AlertHistory> findByAlertName(String alertName);

    List<AlertHistory> findByNamespace(String namespace);
}