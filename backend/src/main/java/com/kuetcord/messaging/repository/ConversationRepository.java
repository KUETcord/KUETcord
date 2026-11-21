package com.kuetcord.messaging.repository;

import com.kuetcord.messaging.model.Conversation;
import com.kuetcord.messaging.model.ConversationType;
import com.kuetcord.messaging.model.MemberStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ConversationRepository extends JpaRepository<Conversation, Long> {

    Optional<Conversation> findByDirectKey(String directKey);

    /**
     * Race-safe "create DM if absent". Two students opening the same DM simultaneously both
     * hit the partial unique index {@code uq_conversations_direct_key}; the loser waits for the
     * winner's commit and then inserts nothing (returns 0) instead of failing the transaction.
     */
    @Modifying
    @Query(value = """
            INSERT INTO conversations (type, creator_id, direct_key, is_private, created_at, updated_at)
            VALUES ('DIRECT', :creatorId, :directKey, FALSE, NOW(), NOW())
            ON CONFLICT (direct_key) WHERE direct_key IS NOT NULL DO NOTHING
            """, nativeQuery = true)
    int insertDirectIfAbsent(@Param("creatorId") Long creatorId, @Param("directKey") String directKey);

    /** Bumps last-activity without loading the entity (one UPDATE per new message). */
    @Modifying
    @Query("UPDATE Conversation c SET c.updatedAt = :at WHERE c.id = :id")
    int touch(@Param("id") Long id, @Param("at") java.time.Instant at);

    List<Conversation> findByCommunityIdAndTypeOrderByIdAsc(Long communityId, ConversationType type);

    /** Non-channel conversations where the user is an active participant, most recent activity first. */
    @Query("""
            SELECT c FROM Conversation c
            WHERE c.id IN (SELECT m.conversationId FROM ConversationMember m
                           WHERE m.userId = :userId AND m.activeStatus = :status)
              AND c.type <> :excludedType
            ORDER BY c.updatedAt DESC, c.id DESC
            """)
    List<Conversation> findActiveForUser(@Param("userId") Long userId,
                                         @Param("status") MemberStatus status,
                                         @Param("excludedType") ConversationType excludedType);
}
