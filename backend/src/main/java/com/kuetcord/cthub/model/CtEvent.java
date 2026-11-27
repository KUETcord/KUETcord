package com.kuetcord.cthub.model;

import com.kuetcord.auth.model.User;
import com.kuetcord.cthub.dto.RoomAllocationDto;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Class Test schedule entity protected by JPA {@code @Version} optimistic concurrency control,
 * supporting multi-room roll-range allocations and up to 2 combined course teachers.
 */
@Entity
@Table(name = "ct_events")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CtEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "course_offering_id", nullable = false)
    private CourseOffering courseOffering;

    @Column(name = "title", nullable = false, length = 150)
    private String title;

    @Column(name = "scheduled_at", nullable = false)
    private Instant scheduledAt;

    @Builder.Default
    @Column(name = "duration_minutes", nullable = false)
    private int durationMinutes = 45;

    @Column(name = "exam_room", length = 255)
    private String examRoom;

    @Builder.Default
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "room_allocations", columnDefinition = "jsonb", nullable = false)
    private List<RoomAllocationDto> roomAllocations = new ArrayList<>();

    @Column(name = "target_batch", length = 20)
    private String targetBatch;

    @Column(name = "covered_topics", columnDefinition = "TEXT")
    private String coveredTopics;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private CtStatus status = CtStatus.UNVERIFIED;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "submitted_by", nullable = false)
    private User submittedBy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "verified_by")
    private User verifiedBy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assigned_teacher_id")
    private User assignedTeacher;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "co_teacher_id")
    private User coTeacher;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        Instant now = Instant.now();
        if (this.createdAt == null) {
            this.createdAt = now;
        }
        if (this.updatedAt == null) {
            this.updatedAt = now;
        }
        if (this.status == null) {
            this.status = CtStatus.UNVERIFIED;
        }
        if (this.durationMinutes <= 0) {
            this.durationMinutes = 45;
        }
        if (this.roomAllocations == null) {
            this.roomAllocations = new ArrayList<>();
        }
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = Instant.now();
        if (this.roomAllocations == null) {
            this.roomAllocations = new ArrayList<>();
        }
    }
}
