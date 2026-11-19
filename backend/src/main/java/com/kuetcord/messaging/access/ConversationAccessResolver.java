package com.kuetcord.messaging.access;

import com.kuetcord.messaging.model.Conversation;
import com.kuetcord.messaging.model.ConversationType;

import java.util.Set;

/**
 * Type-specific read/write access rule for a conversation.
 * <p>
 * The message engine is shared, but permission rules are not: each conversation type
 * contributes its own resolver bean. Member C plugs in PLAYGROUND / STUDY_CIRCLE resolvers
 * without touching messaging code (Open/Closed). Types with no resolver are denied.
 */
public interface ConversationAccessResolver {

    Set<ConversationType> supportedTypes();

    boolean canAccess(Conversation conversation, Long userId);
}
