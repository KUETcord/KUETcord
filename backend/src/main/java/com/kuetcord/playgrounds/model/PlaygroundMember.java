package com.kuetcord.playgrounds.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
 * Membership row ({@code playground_members}). Rows are written with a native
 * {@code INSERT ... ON CONFLICT DO NOTHING} so this entity is read-only from JPA's point of view.
 */
@Entity
@Table(name = "playground_members")
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlaygroundMember {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "playground_id", nullable = false, updatable = false)
    private Long playgroundId;

    @Column(name = "user_id", nullable = false, updatable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 30)
    private PlaygroundRole role;

    @Column(name = "joined_at", nullable = false, insertable = false, updatable = false)
    private Instant joinedAt;
}
