package com.kuetcord.commons.model;

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
import lombok.Setter;

import java.time.Instant;

/**
 * A server role carrying a permission bitmask. Higher {@code position} outranks lower.
 * The single {@code isDefault} role applies to every member implicitly (like Discord's @everyone).
 */
@Entity
@Table(name = "community_roles")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CommunityRole {

    public static final String DEFAULT_COLOR = "#99AAB5";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "community_id", nullable = false)
    private Long communityId;

    @Column(name = "name", nullable = false, length = 50)
    private String name;

    @Builder.Default
    @Column(name = "color", length = 10)
    private String color = DEFAULT_COLOR;

    @Builder.Default
    @Column(name = "position", nullable = false)
    private int position = 0;

    @Builder.Default
    @Column(name = "permissions", nullable = false)
    private long permissions = 0L;

    @Builder.Default
    @Column(name = "is_default", nullable = false)
    private boolean defaultRole = false;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
    }
}
