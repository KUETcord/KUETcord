package com.kuetcord.cthub.event;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Post-commit domain observer for CT lifecycle transitions.
 * Persistent delivery is guaranteed atomically via {@code outbox_events}; this observer
 * provides decoupled post-commit telemetry and logging without coupling CT Hub to Notification delivery.
 */
@Slf4j
@Component
public class CtLifecycleAuditObserver {

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onCtStatusChanged(CtStatusChangedDomainEvent event) {
        log.info("CT event {} ({}) transitioned {} -> {} by user {} (scheduledAt={})",
                event.ctEventId(),
                event.courseCode(),
                event.previousStatus(),
                event.newStatus(),
                event.actorId(),
                event.scheduledAt());
    }
}
