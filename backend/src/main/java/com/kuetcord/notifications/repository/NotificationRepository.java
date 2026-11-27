package com.kuetcord.notifications.repository;

import com.kuetcord.notifications.model.Notification;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

/** Inbox reads; every query is scoped by {@code recipient_id} and served by {@code idx_notif_inbox}. */
public interface NotificationRepository extends JpaRepository<Notification, Long> {

    /** Keyset page, newest first: {@code id < beforeId}. */
    @Query("select n from Notification n where n.recipientId = :recipientId and n.id < :beforeId "
            + "and (:unreadOnly = false or n.readAt is null) order by n.id desc")
    List<Notification> findInboxPage(@Param("recipientId") Long recipientId,
                                     @Param("beforeId") long beforeId,
                                     @Param("unreadOnly") boolean unreadOnly,
                                     Pageable pageable);

    long countByRecipientIdAndReadAtIsNull(Long recipientId);

    List<Notification> findByRecipientIdOrderByIdAsc(Long recipientId);

    /** Idempotent: a second read keeps the first read time. Returns 0 when the row is not the caller's. */
    @Modifying
    @Query(value = "UPDATE notifications SET read_at = COALESCE(read_at, NOW()) WHERE id = :id AND recipient_id = :recipientId",
            nativeQuery = true)
    int markRead(@Param("id") Long id, @Param("recipientId") Long recipientId);

    @Modifying
    @Query(value = "UPDATE notifications SET read_at = NOW() WHERE recipient_id = :recipientId AND read_at IS NULL",
            nativeQuery = true)
    int markAllRead(@Param("recipientId") Long recipientId);
}
