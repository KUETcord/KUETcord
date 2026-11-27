package com.kuetcord.notifications.repository;

import com.kuetcord.notifications.model.NotificationPreference;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface NotificationPreferenceRepository extends JpaRepository<NotificationPreference, Long> {

    Optional<NotificationPreference> findByUserIdAndConversationId(Long userId, Long conversationId);

    /** One query for every candidate recipient of a message (no N+1 in the worker). */
    List<NotificationPreference> findByConversationIdAndUserIdIn(Long conversationId, Collection<Long> userIds);

    /** Users who opted in to every message, e.g. on a channel whose default is mentions-only. */
    @Query("select p.userId from NotificationPreference p where p.conversationId = :conversationId "
            + "and p.mode = com.kuetcord.notifications.model.NotificationMode.ALL_MESSAGES")
    List<Long> findAllMessagesSubscribers(@Param("conversationId") Long conversationId);

    /** Race-free create-or-replace on {@code uq_user_conv_pref}. */
    @Modifying
    @Query(value = """
            INSERT INTO notification_preferences (user_id, conversation_id, mode, mute_until)
            VALUES (:userId, :conversationId, :mode, :muteUntil)
            ON CONFLICT (user_id, conversation_id)
            DO UPDATE SET mode = EXCLUDED.mode, mute_until = EXCLUDED.mute_until, updated_at = NOW()
            """, nativeQuery = true)
    void upsert(@Param("userId") Long userId,
                @Param("conversationId") Long conversationId,
                @Param("mode") String mode,
                @Param("muteUntil") Instant muteUntil);
}
