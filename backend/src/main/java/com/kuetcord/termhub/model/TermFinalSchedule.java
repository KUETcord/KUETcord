package com.kuetcord.termhub.model;

import com.kuetcord.auth.model.User;
import com.kuetcord.cthub.dto.RoomAllocationDto;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Term Final Examination schedule header for a KUET student batch (e.g., CSE 3-2, 2k22, 2025-2026).
 * All subjects under this schedule share a single exam start/end time window.
 */
@Entity
@Table(name = "term_final_schedules")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TermFinalSchedule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "title", nullable = false, length = 180)
    private String title;

    @Column(name = "target_department", nullable = false, length = 16)
    private String targetDepartment;

    @Column(name = "year_term", nullable = false, length = 10)
    private String yearTerm;

    @Column(name = "batch", nullable = false, length = 20)
    private String batch;

    @Column(name = "session", nullable = false, length = 20)
    private String session;

    @Column(name = "exam_start_time", nullable = false)
    private LocalTime examStartTime;

    @Column(name = "exam_end_time", nullable = false)
    private LocalTime examEndTime;

    @Column(name = "exam_room", length = 255)
    private String examRoom;

    @Builder.Default
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "room_allocations", columnDefinition = "jsonb", nullable = false)
    private List<RoomAllocationDto> roomAllocations = new ArrayList<>();

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "created_by", nullable = false)
    private User createdBy;

    @Builder.Default
    @OneToMany(mappedBy = "schedule", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("examDate ASC, id ASC")
    private List<TermFinalEntry> entries = new ArrayList<>();

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public void replaceEntries(List<TermFinalEntry> newEntries) {
        this.entries.clear();
        for (TermFinalEntry entry : newEntries) {
            entry.setSchedule(this);
            this.entries.add(entry);
        }
    }

    @PrePersist
    protected void onCreate() {
        Instant now = Instant.now();
        if (this.createdAt == null) {
            this.createdAt = now;
        }
        if (this.updatedAt == null) {
            this.updatedAt = now;
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
