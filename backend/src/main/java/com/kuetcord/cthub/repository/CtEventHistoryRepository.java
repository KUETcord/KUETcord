package com.kuetcord.cthub.repository;

import com.kuetcord.cthub.model.CtEventHistory;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CtEventHistoryRepository extends JpaRepository<CtEventHistory, Long> {

    @EntityGraph(attributePaths = {"changedBy"})
    List<CtEventHistory> findByCtEventIdOrderByChangedAtDesc(Long ctEventId);
}
