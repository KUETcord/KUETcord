package com.kuetcord.cthub.state;

import com.kuetcord.auth.model.User;
import com.kuetcord.cthub.model.CtEvent;
import com.kuetcord.cthub.model.CtStatus;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * Verified CT state confirmed by a CR, Moderator, or Admin.
 * Cannot be verified twice; may be rescheduled or cancelled.
 */
@Component
public class VerifiedState implements CtState {

    @Override
    public CtStatus getStatus() {
        return CtStatus.VERIFIED;
    }

    @Override
    public void verify(CtEvent event, User verifier) {
        throw new InvalidStateTransitionException("CT event is already VERIFIED");
    }

    @Override
    public void reschedule(CtEvent event, Instant newScheduledAt, String reason, User actor) {
        event.setScheduledAt(newScheduledAt);
        event.setStatus(CtStatus.RESCHEDULED);
        event.setVerifiedBy(actor);
    }

    @Override
    public void cancel(CtEvent event, String reason, User actor) {
        event.setStatus(CtStatus.CANCELLED);
    }
}
