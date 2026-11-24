package com.kuetcord.playgrounds.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

/** A dated checkpoint on the project timeline ({@code project_milestones}, V3 + V9). */
@Entity
@Table(name = "project_milestones")
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProjectMilestone {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "playground_id", nullable = false, updatable = false)
    private Long playgroundId;

    @Column(name = "title", nullable = false, length = 100)
    private String title;

    @Column(name = "description")
    private String description;

    @Column(name = "due_date", nullable = false)
    private Instant dueDate;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private MilestoneStatus status = MilestoneStatus.PENDING;

    @Column(name = "created_by", updatable = false)
    private Long createdBy;

    /** Written together with status COMPLETED by a conditional UPDATE (see the repository). */
    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "completed_by")
    private Long completedBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    public boolean isOverdueAt(Instant now) {
        return status == MilestoneStatus.PENDING && dueDate.isBefore(now);
    }
}
