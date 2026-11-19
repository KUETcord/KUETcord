package com.kuetcord.commons.model;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;
import java.time.Instant;

/**
 * Explicit role assignment (the default role is never stored here; it applies implicitly).
 */
@Entity
@Table(name = "community_member_roles")
@Getter
@Setter
@NoArgsConstructor
public class CommunityMemberRole {

    @EmbeddedId
    private Key id;

    @Column(name = "assigned_at", nullable = false, updatable = false)
    private Instant assignedAt;

    public CommunityMemberRole(Long communityId, Long userId, Long roleId) {
        this.id = new Key(communityId, userId, roleId);
    }

    @PrePersist
    protected void onCreate() {
        assignedAt = Instant.now();
    }

    @Embeddable
    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @EqualsAndHashCode
    public static class Key implements Serializable {

        @Column(name = "community_id")
        private Long communityId;

        @Column(name = "user_id")
        private Long userId;

        @Column(name = "role_id")
        private Long roleId;
    }
}
