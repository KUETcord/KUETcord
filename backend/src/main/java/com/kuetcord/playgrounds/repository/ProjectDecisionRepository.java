package com.kuetcord.playgrounds.repository;

import com.kuetcord.playgrounds.dto.DecisionDto;
import com.kuetcord.playgrounds.model.ProjectDecision;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ProjectDecisionRepository extends JpaRepository<ProjectDecision, Long> {

    /** Newest decision first, author name joined in; served by {@code idx_decisions_playground_created}. */
    @Query("""
            SELECT new com.kuetcord.playgrounds.dto.DecisionDto(
                d.id, d.decision, d.rationale, u.id, u.displayName, d.createdAt)
            FROM ProjectDecision d
            LEFT JOIN User u ON u.id = d.authorId
            WHERE d.playgroundId = :playgroundId
            ORDER BY d.createdAt DESC, d.id DESC
            """)
    List<DecisionDto> findDecisionLog(@Param("playgroundId") Long playgroundId);

    @Query("""
            SELECT new com.kuetcord.playgrounds.dto.DecisionDto(
                d.id, d.decision, d.rationale, u.id, u.displayName, d.createdAt)
            FROM ProjectDecision d
            LEFT JOIN User u ON u.id = d.authorId
            WHERE d.id = :id
            """)
    Optional<DecisionDto> findDecisionDto(@Param("id") Long id);
}
