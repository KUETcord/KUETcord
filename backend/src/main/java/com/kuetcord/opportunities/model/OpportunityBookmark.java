package com.kuetcord.opportunities.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * A student's saved opportunity. Rows are written with {@code INSERT ... ON CONFLICT DO NOTHING}
 * (see {@code OpportunityBookmarkRepository#insertIfAbsent}) so double clicks and retries
 * converge on one row under {@code uq_user_opportunity_bookmark}.
 */
@Entity
@Table(name = "opportunity_bookmarks")
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OpportunityBookmark {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "opportunity_id", nullable = false)
    private Long opportunityId;

    @Column(name = "bookmarked_at", nullable = false, insertable = false, updatable = false)
    private Instant bookmarkedAt;
}
