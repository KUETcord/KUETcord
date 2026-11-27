package com.kuetcord.notifications.service;

import com.kuetcord.common.exception.BadRequestException;
import com.kuetcord.messaging.access.ConversationAccessPolicy;
import com.kuetcord.messaging.model.Conversation;
import com.kuetcord.notifications.dto.NotificationPreferenceDto;
import com.kuetcord.notifications.dto.UpdateNotificationPreferenceRequest;
import com.kuetcord.notifications.model.NotificationMode;
import com.kuetcord.notifications.model.NotificationPreference;
import com.kuetcord.notifications.policy.NotificationPolicy;
import com.kuetcord.notifications.repository.NotificationPreferenceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;

/** Per-conversation Notify/Mute settings. Only participants may read or change their own. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class NotificationPreferenceService {

    /** A timed mute longer than a year is a typo; "until I change it" is {@code muteUntil = null}. */
    static final Duration MAX_MUTE = Duration.ofDays(366);

    private final NotificationPreferenceRepository repository;
    private final ConversationAccessPolicy accessPolicy;

    public NotificationPreferenceDto get(Long conversationId, Long userId) {
        Conversation conversation = accessPolicy.requireAccess(conversationId, userId);
        return toDto(conversation, repository.findByUserIdAndConversationId(userId, conversationId).orElse(null), Instant.now());
    }

    @Transactional
    public NotificationPreferenceDto update(Long conversationId, Long userId, UpdateNotificationPreferenceRequest request) {
        Conversation conversation = accessPolicy.requireAccess(conversationId, userId);
        Instant now = Instant.now();
        validate(request, now);
        repository.upsert(userId, conversationId, request.mode().name(), request.muteUntil());
        NotificationPreference saved = NotificationPreference.builder()
                .userId(userId)
                .conversationId(conversationId)
                .mode(request.mode())
                .muteUntil(request.muteUntil())
                .build();
        return toDto(conversation, saved, now);
    }

    static void validate(UpdateNotificationPreferenceRequest request, Instant now) {
        if (request.muteUntil() == null) {
            return;
        }
        if (request.mode() != NotificationMode.MUTED) {
            throw new BadRequestException("muteUntil is only allowed when mode is MUTED");
        }
        if (!request.muteUntil().isAfter(now)) {
            throw new BadRequestException("muteUntil must be in the future");
        }
        if (request.muteUntil().isAfter(now.plus(MAX_MUTE))) {
            throw new BadRequestException("muteUntil can be at most one year ahead; omit it to mute until changed");
        }
    }

    private static NotificationPreferenceDto toDto(Conversation conversation, NotificationPreference preference, Instant now) {
        NotificationMode defaultMode = NotificationPolicy.defaultMode(conversation.getType());
        NotificationMode effective = NotificationPolicy.effectiveMode(preference, conversation.getType(), now);
        if (preference == null) {
            return new NotificationPreferenceDto(conversation.getId(), defaultMode, null, effective, defaultMode, false);
        }
        return new NotificationPreferenceDto(conversation.getId(), preference.getMode(), preference.getMuteUntil(),
                effective, defaultMode, true);
    }
}
