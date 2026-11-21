package com.kuetcord.playgrounds.repository;

import com.kuetcord.playgrounds.dto.TaskCardDto;
import com.kuetcord.playgrounds.model.ProjectTask;
import com.kuetcord.playgrounds.model.TaskStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ProjectTaskRepository extends JpaRepository<ProjectTask, Long> {

    /** Scoped lookup: a task id from another playground is reported as not found. */
    Optional<ProjectTask> findByIdAndPlaygroundId(Long id, Long playgroundId);

    /**
     * Whole board in one query: cards plus assignee name through a LEFT JOIN, projected straight into
     * records (no entity proxies, no dirty-checking snapshots). Column grouping happens in the service
     * because the status column is a string and would sort alphabetically, not in board order.
     */
    @Query("""
            SELECT new com.kuetcord.playgrounds.dto.TaskCardDto(
                t.id, t.title, t.status, t.priority, t.position, t.dueDate,
                u.id, u.displayName, t.version)
            FROM ProjectTask t
            LEFT JOIN User u ON u.id = t.assigneeId
            WHERE t.playgroundId = :playgroundId
            ORDER BY t.position ASC, t.id ASC
            """)
    List<TaskCardDto> findBoardCards(@Param("playgroundId") Long playgroundId);

    /** Highest card position in a column, or -1 when the column is empty. */
    @Query("""
            SELECT COALESCE(MAX(t.position), -1)
            FROM ProjectTask t
            WHERE t.playgroundId = :playgroundId AND t.status = :status
            """)
    Integer findMaxPosition(@Param("playgroundId") Long playgroundId, @Param("status") TaskStatus status);

    /** Rows of {@code [TaskStatus, Long]}: card count per column. */
    @Query("""
            SELECT t.status, COUNT(t)
            FROM ProjectTask t
            WHERE t.playgroundId = :playgroundId
            GROUP BY t.status
            """)
    List<Object[]> countByStatus(@Param("playgroundId") Long playgroundId);
}
