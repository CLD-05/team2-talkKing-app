package com.team2.talkking.errorops.history;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AlertHistoryRepository extends JpaRepository<AlertHistory, AlertHistory.AlertHistoryId> {

    List<AlertHistory> findTop20ByOrderByCreatedAtDesc();

    List<AlertHistory> findByAlertName(String alertName);

    List<AlertHistory> findByNamespace(String namespace);
}