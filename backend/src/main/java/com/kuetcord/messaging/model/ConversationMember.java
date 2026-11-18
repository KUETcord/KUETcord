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
 * Participation row. Rows are never deleted on leave/remove so that re-adding a user
 * reactivates the same row (the table has a unique (conversation_id, user_id) constraint).
 */
@Entity
@Table(name = "conversation_members")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConversationMember {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "conversation_id", nullable = false)
    private Long conversationId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "group_role", nullable = false, length = 30)
    private GroupRole groupRole = GroupRole.GROUP_MEMBER;

    @Column(name = "joined_at", nullable = false)
    private Instant joinedAt;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "active_status", nullable = false, length = 20)
    private MemberStatus activeStatus = MemberStatus.ACTIVE;

    @Column(name = "left_at")
    private Instant leftAt;

    @PrePersist
    protected void onCreate() {
        if (joinedAt == null) {
            joinedAt = Instant.now();
        }
    }

    public boolean isActive() {
        return activeStatus == MemberStatus.ACTIVE;
    }

    public void deactivate(MemberStatus status) {
        this.activeStatus = status;
        this.leftAt = Instant.now();
    }

    public void reactivate(GroupRole role) {
        this.activeStatus = MemberStatus.ACTIVE;
        this.groupRole = role;
        this.leftAt = null;
        this.joinedAt = Instant.now();
    }
}
