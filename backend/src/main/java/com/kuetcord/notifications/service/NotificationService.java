package com.kuetcord.notifications.service;

import com.kuetcord.common.exception.ResourceNotFoundException;
import com.kuetcord.notifications.dto.NotificationDto;
import com.kuetcord.notifications.dto.NotificationPageDto;
import com.kuetcord.notifications.dto.UnreadCountDto;
import com.kuetcord.notifications.model.Notification;
import com.kuetcord.notifications.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * The caller's own inbox. Every query is scoped by recipient, and someone else's notification ID
 * answers 404 exactly like a missing one, so IDs cannot be probed.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class NotificationService {

    public static final int DEFAULT_PAGE_SIZE = 20;
    public static final int MAX_PAGE_SIZE = 50;

    private final NotificationRepository repository;

    public NotificationPageDto inbox(Long recipientId, Long cursor, Integer limit, boolean unreadOnly) {
        int pageSize = limit == null ? DEFAULT_PAGE_SIZE : Math.min(Math.max(limit, 1), MAX_PAGE_SIZE);
        long beforeId = cursor == null ? Long.MAX_VALUE : cursor;

        List<Notification> found = repository.findInboxPage(recipientId, beforeId, unreadOnly, PageRequest.of(0, pageSize + 1));
        boolean hasMore = found.size() > pageSize;
        List<Notification> page = hasMore ? found.subList(0, pageSize) : found;
        Long nextCursor = hasMore ? page.get(page.size() - 1).getId() : null;
        return new NotificationPageDto(page.stream().map(NotificationDto::from).toList(), nextCursor, hasMore,
                repository.countByRecipientIdAndReadAtIsNull(recipientId));
    }

    public UnreadCountDto unreadCount(Long recipientId) {
        return new UnreadCountDto(repository.countByRecipientIdAndReadAtIsNull(recipientId), 0);
    }

    @Transactional
    public UnreadCountDto markRead(Long notificationId, Long recipientId) {
        int updated = repository.markRead(notificationId, recipientId);
        if (updated == 0) {
            throw new ResourceNotFoundException("Notification", "id", notificationId);
        }
        return new UnreadCountDto(repository.countByRecipientIdAndReadAtIsNull(recipientId), updated);
    }

    @Transactional
    public UnreadCountDto markAllRead(Long recipientId) {
        int updated = repository.markAllRead(recipientId);
        return new UnreadCountDto(0, updated);
    }
}
