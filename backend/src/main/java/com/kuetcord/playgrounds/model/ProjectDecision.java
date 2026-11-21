package com.kuetcord.playgrounds.model;

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

/**
 * One entry in a playground's architectural / project decision log ({@code project_decisions}).
 * Entries are append-only: a reversed decision is recorded as a new entry, which keeps the history honest.
 */
@Entity
@Table(name = "project_decisions")
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProjectDecision {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "playground_id", nullable = false, updatable = false)
    private Long playgroundId;

    @Column(name = "decision", nullable = false, updatable = false)
    private String decision;

    @Column(name = "rationale", updatable = false)
    private String rationale;

    @Column(name = "author_id", nullable = false, updatable = false)
    private Long authorId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}
