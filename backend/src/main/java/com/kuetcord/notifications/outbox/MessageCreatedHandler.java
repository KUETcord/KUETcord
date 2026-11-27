package com.kuetcord.notifications.outbox;

import com.kuetcord.auth.model.User;
import com.kuetcord.auth.repository.UserRepository;
import com.kuetcord.messaging.access.ConversationAccessPolicy;
import com.kuetcord.messaging.model.Conversation;
import com.kuetcord.messaging.model.ConversationMember;
import com.kuetcord.messaging.model.ConversationType;
import com.kuetcord.messaging.model.MemberStatus;
import com.kuetcord.messaging.repository.ConversationMemberRepository;
import com.kuetcord.messaging.repository.ConversationRepository;
import com.kuetcord.messaging.repository.MessageRepository;
import com.kuetcord.messaging.service.MessageService;
import com.kuetcord.notifications.model.NotificationPreference;
import com.kuetcord.notifications.policy.NotificationContext;
import com.kuetcord.notifications.policy.NotificationPolicy;
import com.kuetcord.notifications.repository.NotificationPreferenceRepository;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * {@code MESSAGE_CREATED} -> one {@code MENTION} or {@code MESSAGE} notification per eligible recipient.
 * <p>
 * Candidates are kept small on purpose:
 * <ul>
 *   <li>DMs and group DMs: every active participant;</li>
 *   <li>channels (default mentions-only): mentioned users plus users who opted in to all messages.
 *       A 2,000-member channel therefore never scans its whole membership per message.</li>
 * </ul>
 * Each candidate then goes through {@link NotificationPolicy} (Strategy pattern) with the
 * preference loaded for all candidates in one query.
 */
@Component
public class MessageCreatedHandler implements OutboxEventHandler {

    public static final String TYPE_MENTION = "MENTION";
    public static final String TYPE_MESSAGE = "MESSAGE";
    public static final String TARGET_CONVERSATION = "CONVERSATION";

    private final ConversationRepository conversationRepository;
    private final ConversationMemberRepository memberRepository;
    private final MessageRepository messageRepository;
    private final NotificationPreferenceRepository preferenceRepository;
    private final UserRepository userRepository;
    private final ConversationAccessPolicy accessPolicy;
    private final NotificationPolicy policy;

    public MessageCreatedHandler(ConversationRepository conversationRepository,
                                 ConversationMemberRepository memberRepository,
                                 MessageRepository messageRepository,
                                 NotificationPreferenceRepository preferenceRepository,
                                 UserRepository userRepository,
                                 ConversationAccessPolicy accessPolicy,
                                 NotificationPolicy policy) {
        this.conversationRepository = conversationRepository;
        this.memberRepository = memberRepository;
        this.messageRepository = messageRepository;
        this.preferenceRepository = preferenceRepository;
        this.userRepository = userRepository;
        this.accessPolicy = accessPolicy;
        this.policy = policy;
    }

    @Override
    public Set<String> eventTypes() {
        return Set.of(MessageService.MESSAGE_CREATED);
    }

    @Override
    public List<NotificationDraft> toNotifications(ClaimedOutboxEvent event) {
        Long messageId = event.requireLong("messageId");
        Long conversationId = event.requireLong("conversationId");
        Long senderId = event.requireLong("senderId");
        Set<Long> mentioned = new LinkedHashSet<>(event.optionalLongList("mentionedUserIds"));

        Conversation conversation = conversationRepository.findById(conversationId).orElse(null);
        boolean stillVisible = messageRepository.findById(messageId).map(m -> m.getDeletedAt() == null).orElse(false);
        if (conversation == null || !stillVisible) {
            return List.of();
        }

        Set<Long> participants = participantsOf(conversation);
        Set<Long> candidates = new LinkedHashSet<>(participants);
        candidates.addAll(mentioned);
        candidates.addAll(preferenceRepository.findAllMessagesSubscribers(conversationId));
        candidates.remove(senderId);
        if (candidates.isEmpty()) {
            return List.of();
        }

        Map<Long, NotificationPreference> preferences = preferenceRepository
                .findByConversationIdAndUserIdIn(conversationId, candidates).stream()
                .collect(Collectors.toMap(NotificationPreference::getUserId, Function.identity()));
        String senderName = userRepository.findById(senderId).map(User::getDisplayName).orElse("Someone");
        String place = placeName(conversation);
        String preview = event.text("preview");

        List<NotificationDraft> drafts = new ArrayList<>();
        for (Long recipientId : candidates) {
            // Access may have been lost since the message was sent (kicked, left, channel made private).
            if (!participants.contains(recipientId) && !accessPolicy.canAccess(conversation, recipientId)) {
                continue;
            }
            boolean isMention = mentioned.contains(recipientId);
            NotificationContext context = new NotificationContext(conversation.getType(), isMention, event.occurredAt());
            if (!policy.shouldNotify(preferences.get(recipientId), context)) {
                continue;
            }
            drafts.add(new NotificationDraft(recipientId,
                    isMention ? TYPE_MENTION : TYPE_MESSAGE,
                    title(conversation.getType(), isMention, senderName, place),
                    preview,
                    TARGET_CONVERSATION,
                    conversationId));
        }
        return drafts;
    }

    private Set<Long> participantsOf(Conversation conversation) {
        if (conversation.getType() == ConversationType.CHANNEL) {
            return Set.of();
        }
        return memberRepository.findByConversationIdAndActiveStatusOrderByJoinedAtAsc(conversation.getId(), MemberStatus.ACTIVE)
                .stream()
                .map(ConversationMember::getUserId)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    static String placeName(Conversation conversation) {
        String title = conversation.getTitle();
        return switch (conversation.getType()) {
            case DIRECT -> "a direct message";
            case CHANNEL -> "#" + (title == null ? "channel" : title);
            default -> title == null || title.isBlank() ? "a group chat" : title;
        };
    }

    static String title(ConversationType type, boolean mention, String sender, String place) {
        if (mention) {
            return sender + " mentioned you in " + place;
        }
        return type == ConversationType.DIRECT ? "New message from " + sender : sender + " in " + place;
    }
}
