package com.kuetcord.messaging.service;

import com.kuetcord.auth.model.User;
import com.kuetcord.auth.repository.UserRepository;
import com.kuetcord.common.exception.BadRequestException;
import com.kuetcord.messaging.access.ConversationAccessPolicy;
import com.kuetcord.messaging.dto.ChatMessageDto;
import com.kuetcord.messaging.dto.MessagePageDto;
import com.kuetcord.messaging.dto.ReadStateDto;
import com.kuetcord.messaging.dto.SyncResponseDto;
import com.kuetcord.messaging.model.Message;
import com.kuetcord.messaging.model.MessageMention;
import com.kuetcord.messaging.repository.ConversationReadStateRepository;
import com.kuetcord.messaging.repository.ConversationRepository;
import com.kuetcord.messaging.repository.MessageMentionRepository;
import com.kuetcord.messaging.repository.MessageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Message persistence for every conversation type: idempotent send, keyset history and read state.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MessageService {

    public static final int MAX_IDEMPOTENCY_KEY_LENGTH = 64;
    public static final int DEFAULT_PAGE_SIZE = 30;
    public static final int MAX_PAGE_SIZE = 100;
    public static final int DEFAULT_SYNC_SIZE = 50;
    public static final int MAX_SYNC_SIZE = 200;

    private final MessageRepository messageRepository;
    private final MessageMentionRepository mentionRepository;
    private final ConversationRepository conversationRepository;
    private final ConversationReadStateRepository readStateRepository;
    private final ConversationAccessPolicy accessPolicy;
    private final UserRepository userRepository;

    /** {@code created == false} means this was a retry of an already-persisted message. */
    public record SendResult(ChatMessageDto message, boolean created) {
    }

    /**
     * Validates, authorizes and persists a message exactly once per idempotency key.
     * Commits before returning, so callers may ACK/broadcast only after durability.
     */
    @Transactional
    public SendResult send(Long conversationId, Long senderId, String content, String idempotencyKey) {
        validateContent(content);
        validateIdempotencyKey(idempotencyKey);
        accessPolicy.requireSend(conversationId, senderId);

        // Fast path for retries: no insert attempt, no extra index contention.
        var existing = messageRepository.findByConversationIdAndSenderIdAndIdempotencyKey(conversationId, senderId, idempotencyKey);
        if (existing.isPresent()) {
            return new SendResult(toDto(existing.get(), senderName(senderId)), false);
        }

        boolean created = messageRepository.insertIfAbsent(conversationId, senderId, content, idempotencyKey) == 1;
        Message message = messageRepository.findByConversationIdAndSenderIdAndIdempotencyKey(conversationId, senderId, idempotencyKey)
                .orElseThrow(() -> new IllegalStateException("Message vanished after idempotent insert"));

        if (created) {
            saveMentions(message, conversationId, senderId);
            conversationRepository.touch(conversationId, Instant.now());
        }
        return new SendResult(toDto(message, senderName(senderId)), created);
    }

    /**
     * Keyset page of history, returned oldest-first. {@code cursor} is the smallest message ID
     * the client already has (null for the newest page).
     */
    public MessagePageDto history(Long conversationId, Long userId, Long cursor, Integer limit) {
        accessPolicy.requireAccess(conversationId, userId);
        int pageSize = limit == null ? DEFAULT_PAGE_SIZE : Math.min(Math.max(limit, 1), MAX_PAGE_SIZE);
        long beforeId = cursor == null ? Long.MAX_VALUE : cursor;

        List<Message> newestFirst = messageRepository.findPageBefore(conversationId, beforeId, PageRequest.of(0, pageSize + 1));
        boolean hasMore = newestFirst.size() > pageSize;
        List<Message> page = new ArrayList<>(hasMore ? newestFirst.subList(0, pageSize) : newestFirst);
        Collections.reverse(page);

        Map<Long, String> names = senderNames(page.stream().map(Message::getSenderId).collect(Collectors.toSet()));
        List<ChatMessageDto> dtos = page.stream().map(m -> toDto(m, names.get(m.getSenderId()))).toList();
        Long nextCursor = hasMore && !page.isEmpty() ? page.get(0).getId() : null;
        return new MessagePageDto(conversationId, dtos, nextCursor, hasMore);
    }

    /**
     * Gap catch-up after a reconnect: messages strictly newer than {@code afterMessageId}, oldest first.
     * The client keeps its DOM and scroll position and simply appends what it missed.
     */
    public SyncResponseDto sync(Long conversationId, Long userId, Long afterMessageId, Integer limit) {
        accessPolicy.requireAccess(conversationId, userId);
        if (afterMessageId == null || afterMessageId < 0) {
            throw new BadRequestException("afterMessageId must be zero or a positive message id");
        }
        int pageSize = limit == null ? DEFAULT_SYNC_SIZE : Math.min(Math.max(limit, 1), MAX_SYNC_SIZE);

        List<Message> found = messageRepository.findPageAfter(conversationId, afterMessageId, PageRequest.of(0, pageSize + 1));
        boolean hasMore = found.size() > pageSize;
        List<Message> page = hasMore ? found.subList(0, pageSize) : found;

        Map<Long, String> names = senderNames(page.stream().map(Message::getSenderId).collect(Collectors.toSet()));
        return new SyncResponseDto(conversationId,
                page.stream().map(m -> toDto(m, names.get(m.getSenderId()))).toList(),
                hasMore);
    }

    @Transactional
    public ReadStateDto markRead(Long conversationId, Long userId, Long lastReadMessageId) {
        accessPolicy.requireAccess(conversationId, userId);
        if (!messageRepository.existsByIdAndConversationId(lastReadMessageId, conversationId)) {
            throw new BadRequestException("Message " + lastReadMessageId + " does not belong to this conversation");
        }
        readStateRepository.upsertMonotonic(conversationId, userId, lastReadMessageId);
        Long effective = readStateRepository.findByConversationIdAndUserId(conversationId, userId)
                .map(state -> state.getLastReadMessageId())
                .orElse(lastReadMessageId);
        return new ReadStateDto(conversationId, effective, unreadCounts(userId, List.of(conversationId)).getOrDefault(conversationId, 0L));
    }

    /** conversationId -> unread messages from others, for many conversations in one query. */
    public Map<Long, Long> unreadCounts(Long userId, Collection<Long> conversationIds) {
        if (conversationIds.isEmpty()) {
            return Map.of();
        }
        return readStateRepository.countUnread(userId, conversationIds).stream()
                .collect(Collectors.toMap(row -> ((Number) row[0]).longValue(), row -> ((Number) row[1]).longValue()));
    }

    // ----------------------------------------------------------------- helpers

    /** Only users who can actually read this conversation are recorded as mentioned. */
    private void saveMentions(Message message, Long conversationId, Long senderId) {
        Set<String> usernames = MentionParser.parse(message.getContent());
        if (usernames.isEmpty()) {
            return;
        }
        List<MessageMention> mentions = mentionRepository.resolveUsernames(usernames).stream()
                .map(row -> (Long) row[0])
                .filter(userId -> !userId.equals(senderId))
                .distinct()
                .filter(userId -> accessPolicy.canAccess(conversationId, userId))
                .map(userId -> MessageMention.builder().messageId(message.getId()).mentionedUserId(userId).build())
                .toList();
        mentionRepository.saveAll(mentions);
    }

    static void validateContent(String content) {
        if (content == null || content.isBlank()) {
            throw new BadRequestException("Message content cannot be empty");
        }
        if (content.length() > Message.MAX_CONTENT_LENGTH) {
            throw new BadRequestException("Message content must be at most " + Message.MAX_CONTENT_LENGTH + " characters");
        }
    }

    static void validateIdempotencyKey(String key) {
        if (key == null || key.isBlank()) {
            throw new BadRequestException("idempotencyKey is required");
        }
        if (key.length() > MAX_IDEMPOTENCY_KEY_LENGTH) {
            throw new BadRequestException("idempotencyKey must be at most " + MAX_IDEMPOTENCY_KEY_LENGTH + " characters");
        }
    }

    private String senderName(Long senderId) {
        return userRepository.findById(senderId).map(User::getDisplayName).orElse(null);
    }

    private Map<Long, String> senderNames(Set<Long> ids) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        return userRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(User::getId, User::getDisplayName, (a, b) -> a));
    }

    static ChatMessageDto toDto(Message message, String senderName) {
        boolean deleted = message.getDeletedAt() != null;
        return new ChatMessageDto(message.getId(), message.getConversationId(), message.getSenderId(), senderName,
                deleted ? null : message.getContent(), message.getIdempotencyKey(),
                message.getCreatedAt(), message.getEditedAt(), deleted);
    }
}
