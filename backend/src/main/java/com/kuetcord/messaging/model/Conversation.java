package com.kuetcord.messaging.model;

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
import lombok.Setter;

import java.time.Instant;

/**
 * Unified conversation entity shared by channels, DMs, group DMs, playgrounds and study circles.
 * {@code updatedAt} doubles as "last activity" for sorting the conversation list.
 */
@Entity
@Table(name = "conversations")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Conversation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 30)
    private ConversationType type;

    @Column(name = "title", length = 150)
    private String title;

    @Column(name = "community_id")
    private Long communityId;

    @Column(name = "creator_id")
    private Long creatorId;

    /** {@code "<minUserId>:<maxUserId>"} for DIRECT conversations only; unique-indexed. */
    @Column(name = "direct_key", length = 50)
    private String directKey;

    @Builder.Default
    @Column(name = "is_private", nullable = false)
    private boolean privateChannel = false;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        Instant now = Instant.now();
        createdAt = now;
        if (updatedAt == null) {
            updatedAt = now;
        }
    }

    /** Marks new activity so the conversation rises to the top of each member's list. */
    public void touch() {
        updatedAt = Instant.now();
    }

    public static String directKeyFor(long userA, long userB) {
        return Math.min(userA, userB) + ":" + Math.max(userA, userB);
    }
}
