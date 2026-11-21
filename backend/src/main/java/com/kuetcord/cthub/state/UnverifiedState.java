package com.kuetcord.cthub.state;

import com.kuetcord.auth.model.User;
import com.kuetcord.cthub.model.CtEvent;
import com.kuetcord.cthub.model.CtStatus;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * Initial student-submitted CT state. Can be verified, rescheduled, or cancelled by authorized roles.
 */
@Component
public class UnverifiedState implements CtState {

    @Override
    public CtStatus getStatus() {
        return CtStatus.UNVERIFIED;
    }

    @Override
    public void verify(CtEvent event, User verifier) {
        event.setStatus(CtStatus.VERIFIED);
        event.setVerifiedBy(verifier);
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
