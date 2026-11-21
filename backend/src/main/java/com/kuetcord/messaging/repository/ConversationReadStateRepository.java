package com.kuetcord.messaging.repository;

import com.kuetcord.messaging.model.ConversationReadState;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ConversationReadStateRepository extends JpaRepository<ConversationReadState, Long> {

    Optional<ConversationReadState> findByConversationIdAndUserId(Long conversationId, Long userId);

    /**
     * Monotonic upsert: the marker only ever moves forward, so an older tab reporting a stale
     * read position cannot resurrect unread badges that a newer tab already cleared.
     */
    @Modifying
    @Query(value = """
            INSERT INTO conversation_read_states (conversation_id, user_id, last_read_message_id, updated_at)
            VALUES (:conversationId, :userId, :messageId, NOW())
            ON CONFLICT (conversation_id, user_id) DO UPDATE
               SET last_read_message_id = GREATEST(conversation_read_states.last_read_message_id, EXCLUDED.last_read_message_id),
                   updated_at = NOW()
            """, nativeQuery = true)
    int upsertMonotonic(@Param("conversationId") Long conversationId,
                        @Param("userId") Long userId,
                        @Param("messageId") Long messageId);

    /**
     * Rows of [conversationId, unreadCount] for one user across many conversations in a single
     * query: messages from others newer than the user's marker. Served by idx_messages_cursor.
     */
    @Query(value = """
            SELECT m.conversation_id, COUNT(*)
            FROM messages m
            LEFT JOIN conversation_read_states r
                   ON r.conversation_id = m.conversation_id AND r.user_id = :userId
            WHERE m.conversation_id IN (:conversationIds)
              AND m.sender_id <> :userId
              AND m.deleted_at IS NULL
              AND m.id > COALESCE(r.last_read_message_id, 0)
            GROUP BY m.conversation_id
            """, nativeQuery = true)
    List<Object[]> countUnread(@Param("userId") Long userId,
                               @Param("conversationIds") Collection<Long> conversationIds);
}
