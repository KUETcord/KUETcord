package com.kuetcord.playgrounds.repository;

import com.kuetcord.playgrounds.model.ProjectMilestone;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ProjectMilestoneRepository extends JpaRepository<ProjectMilestone, Long> {

    /** Timeline order; served by {@code idx_milestones_playground_due}. */
    List<ProjectMilestone> findByPlaygroundIdOrderByDueDateAscIdAsc(Long playgroundId);
}
