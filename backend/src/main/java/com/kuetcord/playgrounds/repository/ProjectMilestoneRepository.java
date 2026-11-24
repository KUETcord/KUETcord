package com.kuetcord.playgrounds.repository;

import com.kuetcord.playgrounds.model.ProjectMilestone;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface ProjectMilestoneRepository extends JpaRepository<ProjectMilestone, Long> {

    /** Timeline order; served by {@code idx_milestones_playground_due}. */
    List<ProjectMilestone> findByPlaygroundIdOrderByDueDateAscIdAsc(Long playgroundId);

    Optional<ProjectMilestone> findByIdAndPlaygroundId(Long id, Long playgroundId);

    /**
     * PENDING -> COMPLETED as one compare-and-set statement. When two teammates tick the same milestone at
     * once, PostgreSQL's row lock lets exactly one UPDATE match, so exactly one {@code MILESTONE_REACHED}
     * event is written.
     *
     * @return 1 if this call completed the milestone, 0 if it was already completed
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = """
            UPDATE project_milestones
            SET status = 'COMPLETED', completed_at = :completedAt, completed_by = :completedBy
            WHERE id = :id AND status = 'PENDING'
            """, nativeQuery = true)
    int markCompleted(@Param("id") Long id, @Param("completedBy") Long completedBy,
                      @Param("completedAt") Instant completedAt);

    /** COMPLETED -> PENDING; clears the completion audit. Returns 0 if it was already pending. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = """
            UPDATE project_milestones
            SET status = 'PENDING', completed_at = NULL, completed_by = NULL
            WHERE id = :id AND status = 'COMPLETED'
            """, nativeQuery = true)
    int reopen(@Param("id") Long id);
}
