package com.kuetcord.notifications.policy;

import com.kuetcord.messaging.model.ConversationType;

import java.time.Instant;

/**
 * What a strategy needs to know about one (message, recipient) pair.
 *
 * @param conversationType decides the default mode when the user never chose one
 * @param mentioned        the message {@code @mentions} this recipient
 * @param occurredAt       when the message was sent; a mute is judged at this instant, not when the
 *                         worker happens to run, so a backlog processed after unmuting is never replayed
 */
public record NotificationContext(ConversationType conversationType, boolean mentioned, Instant occurredAt) {
}
