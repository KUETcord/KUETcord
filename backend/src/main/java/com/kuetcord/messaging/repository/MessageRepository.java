package com.kuetcord.messaging.repository;

import com.kuetcord.messaging.model.Message;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface MessageRepository extends JpaRepository<Message, Long> {

    /** Idempotent-retry lookup backed by the partial unique index {@code uq_msg_idempotency}. */
    Optional<Message> findByConversationIdAndSenderIdAndIdempotencyKey(Long conversationId, Long senderId, String idempotencyKey);
}
