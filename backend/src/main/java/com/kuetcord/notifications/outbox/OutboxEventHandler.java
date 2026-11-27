package com.kuetcord.notifications.outbox;

import java.util.List;
import java.util.Set;

/**
 * Turns one outbox event type into inbox notifications. The worker only claims event types that some
 * handler declares, so rows meant for other consumers (e.g. CT Hub e-mails) are never touched.
 * <p>
 * Throwing marks the event for retry with backoff, and after the last attempt as {@code DEAD_LETTER}.
 */
public interface OutboxEventHandler {

    Set<String> eventTypes();

    List<NotificationDraft> toNotifications(ClaimedOutboxEvent event);
}
