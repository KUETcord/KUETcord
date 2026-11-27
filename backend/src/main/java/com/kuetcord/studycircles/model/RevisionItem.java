package com.kuetcord.studycircles.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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

/** One topic on a circle's shared revision checklist, e.g. "Dijkstra with a binary heap". */
@Entity
@Table(name = "study_revision_items")
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RevisionItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "study_circle_id", nullable = false, updatable = false)
    private Long studyCircleId;

    @Column(name = "title", nullable = false, length = 200)
    private String title;

    @Column(name = "done", nullable = false)
    private boolean done;

    @Column(name = "created_by", updatable = false)
    private Long createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "completed_by")
    private Long completedBy;

    @Column(name = "completed_at")
    private Instant completedAt;

    /**
     * Ticks or unticks the item. Asking for the state it is already in changes nothing, so a double click
     * or a retried request keeps the original "ticked by" and "ticked at".
     *
     * @return true if the state changed
     */
    public boolean markDone(boolean value, Long userId, Instant now) {
        if (done == value) {
            return false;
        }
        done = value;
        completedBy = value ? userId : null;
        completedAt = value ? now : null;
        return true;
    }

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}
