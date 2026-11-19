package com.kuetcord.messaging.access;

import com.kuetcord.common.exception.ForbiddenException;
import com.kuetcord.messaging.model.Conversation;
import com.kuetcord.messaging.model.ConversationType;
import com.kuetcord.messaging.repository.ConversationRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Single authorization gate for every conversation read, send and subscribe
 * (REST now; STOMP SEND/SUBSCRIBE interceptors in Phase 2).
 * <p>
 * Fail-closed: unknown conversation IDs and types without a registered resolver are denied,
 * and both cases look identical to the caller so IDs cannot be probed (IDOR defence).
 */
@Component
@Transactional(readOnly = true)
public class ConversationAccessPolicy {

    public static final String DENIED_MESSAGE = "You do not have access to this conversation";

    private final ConversationRepository conversationRepository;
    private final Map<ConversationType, ConversationAccessResolver> resolvers = new EnumMap<>(ConversationType.class);

    public ConversationAccessPolicy(ConversationRepository conversationRepository,
                                    List<ConversationAccessResolver> resolverBeans) {
        this.conversationRepository = conversationRepository;
        for (ConversationAccessResolver resolver : resolverBeans) {
            for (ConversationType type : resolver.supportedTypes()) {
                ConversationAccessResolver previous = resolvers.putIfAbsent(type, resolver);
                if (previous != null) {
                    throw new IllegalStateException("Duplicate access resolvers for " + type + ": "
                            + previous.getClass().getSimpleName() + ", " + resolver.getClass().getSimpleName());
                }
            }
        }
    }

    public boolean canAccess(Long conversationId, Long userId) {
        if (conversationId == null || userId == null) {
            return false;
        }
        return conversationRepository.findById(conversationId)
                .map(conversation -> canAccess(conversation, userId))
                .orElse(false);
    }

    public boolean canAccess(Conversation conversation, Long userId) {
        ConversationAccessResolver resolver = resolvers.get(conversation.getType());
        return resolver != null && resolver.canAccess(conversation, userId);
    }

    /** Loads the conversation or throws 403 for both "missing" and "not allowed". */
    public Conversation requireAccess(Long conversationId, Long userId) {
        return conversationRepository.findById(conversationId)
                .filter(conversation -> canAccess(conversation, userId))
                .orElseThrow(() -> new ForbiddenException(DENIED_MESSAGE));
    }
}
