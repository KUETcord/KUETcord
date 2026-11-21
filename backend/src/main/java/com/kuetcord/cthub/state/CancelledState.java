package com.kuetcord.cthub.state;

import com.kuetcord.auth.model.User;
import com.kuetcord.cthub.model.CtEvent;
import com.kuetcord.cthub.model.CtStatus;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * Terminal CANCELLED CT state. Rejects all subsequent lifecycle transitions.
 */
@Component
public class CancelledState implements CtState {

    @Override
    public CtStatus getStatus() {
        return CtStatus.CANCELLED;
    }

    @Override
    public void verify(CtEvent event, User verifier) {
        throw new InvalidStateTransitionException("Cannot verify a CANCELLED CT event");
    }

    @Override
    public void reschedule(CtEvent event, Instant newScheduledAt, String reason, User actor) {
        throw new InvalidStateTransitionException("Cannot reschedule a CANCELLED CT event");
    }

    @Override
    public void cancel(CtEvent event, String reason, User actor) {
        throw new InvalidStateTransitionException("CT event is already CANCELLED");
    }
}
