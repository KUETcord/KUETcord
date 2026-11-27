package com.kuetcord.notifications.outbox;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;

/**
 * Live delivery to {@code /user/queue/notifications}, only after the inbox rows have committed.
 * The push is best effort: an offline user simply finds the rows in the inbox later.
 */
@Component
public class NotificationPusher {

    public static final String USER_QUEUE = "/queue/notifications";

    private static final Logger log = LoggerFactory.getLogger(NotificationPusher.class);

    private final SimpMessagingTemplate messagingTemplate;

    public NotificationPusher(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    public void pushAfterCommit(List<NotificationWriter.Inserted> inserted) {
        if (inserted.isEmpty()) {
            return;
        }
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    push(inserted);
                }
            });
        } else {
            push(inserted);
        }
    }

    private void push(List<NotificationWriter.Inserted> inserted) {
        for (NotificationWriter.Inserted row : inserted) {
            try {
                // StompPrincipal.getName() is the user ID, so this resolves to every open tab of that user.
                messagingTemplate.convertAndSendToUser(String.valueOf(row.recipientId()), USER_QUEUE, row.notification());
            } catch (MessagingException ex) {
                log.warn("Live push of notification {} to user {} failed: {}",
                        row.notification().id(), row.recipientId(), ex.getMessage());
            }
        }
    }
}
