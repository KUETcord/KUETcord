package com.kuetcord.messaging.repository;

import com.kuetcord.messaging.model.Message;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface MessageRepository extends JpaRepository<Message, Long> {

    /** Idempotent-retry lookup backed by the partial unique index {@code uq_msg_idempotency}. */
    Optional<Message> findByConversationIdAndSenderIdAndIdempotencyKey(Long conversationId, Long senderId, String idempotencyKey);

    /**
     * Race-safe idempotent insert. A retry carrying the same key (even one racing the original
     * on another thread) inserts nothing and returns 0 instead of aborting the transaction, which
     * a caught DataIntegrityViolationException would do on PostgreSQL.
     */
    @Modifying
    @Query(value = """
            INSERT INTO messages (conversation_id, sender_id, content, idempotency_key, created_at)
            VALUES (:conversationId, :senderId, :content, :idempotencyKey, NOW())
            ON CONFLICT (conversation_id, sender_id, idempotency_key) WHERE idempotency_key IS NOT NULL DO NOTHING
            """, nativeQuery = true)
    int insertIfAbsent(@Param("conversationId") Long conversationId,
                       @Param("senderId") Long senderId,
                       @Param("content") String content,
                       @Param("idempotencyKey") String idempotencyKey);

    /**
     * Keyset (seek) pagination: newest first, strictly older than {@code beforeId}.
     * O(log N) seek on idx_messages_cursor (conversation_id, id DESC) regardless of history depth.
     */
    @Query("SELECT m FROM Message m WHERE m.conversationId = :conversationId AND m.id < :beforeId ORDER BY m.id DESC")
    List<Message> findPageBefore(@Param("conversationId") Long conversationId,
                                 @Param("beforeId") Long beforeId,
                                 Pageable pageable);

    boolean existsByIdAndConversationId(Long id, Long conversationId);
}
