package com.kuetcord.cthub.state;

import com.kuetcord.auth.model.User;
import com.kuetcord.cthub.model.CtEvent;
import com.kuetcord.cthub.model.CtStatus;

import java.time.Instant;

/**
 * State Pattern contract encapsulating legal lifecycle transitions for a {@link CtEvent}.
 */
public interface CtState {

    CtStatus getStatus();

    void verify(CtEvent event, User verifier);

    void reschedule(CtEvent event, Instant newScheduledAt, String reason, User actor);

    void cancel(CtEvent event, String reason, User actor);
}
