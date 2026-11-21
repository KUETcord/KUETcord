package com.kuetcord.messaging.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Per-user last-read marker. Written only through the monotonic upsert in
 * {@code ConversationReadStateRepository} so concurrent tabs can never move it backwards.
 */
@Entity
@Table(name = "conversation_read_states")
@Getter
@NoArgsConstructor
public class ConversationReadState {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "conversation_id", nullable = false)
    private Long conversationId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "last_read_message_id", nullable = false)
    private Long lastReadMessageId;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
