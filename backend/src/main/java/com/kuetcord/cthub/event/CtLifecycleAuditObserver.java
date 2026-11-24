package com.kuetcord.cthub.event;

import com.kuetcord.auth.service.EmailDispatchService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Post-commit domain observer for CT lifecycle transitions and teacher email notifications.
 * Persistent delivery is guaranteed atomically via {@code outbox_events}; this observer
 * dispatches email notifications and audit logs cleanly after ACID commit.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CtLifecycleAuditObserver {

    private final EmailDispatchService emailDispatchService;

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

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onTeacherNotificationRequested(CtTeacherNotificationDomainEvent event) {
        emailDispatchService.sendTeacherCtNotificationEmail(
                event.teacherEmail(),
                event.teacherName(),
                event.subject(),
                event.details()
        );
    }
}
