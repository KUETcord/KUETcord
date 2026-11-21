package com.kuetcord.cthub.state;

import com.kuetcord.auth.model.User;
import com.kuetcord.cthub.model.CtEvent;
import com.kuetcord.cthub.model.CtStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CtStatePatternTest {

    private CtStateResolver resolver;
    private User crUser;

    @BeforeEach
    void setUp() {
        resolver = new CtStateResolver(List.of(
                new UnverifiedState(),
                new VerifiedState(),
                new RescheduledState(),
                new CancelledState()
        ));
        crUser = User.builder().id(2L).email("cr@stud.kuet.ac.bd").displayName("Karim CR").build();
    }

    @Test
    @DisplayName("UNVERIFIED -> VERIFIED stamps verifiedBy and updates status")
    void shouldTransitionUnverifiedToVerified() {
        CtEvent event = CtEvent.builder().id(1L).status(CtStatus.UNVERIFIED).scheduledAt(Instant.now()).build();

        resolver.resolve(event.getStatus()).verify(event, crUser);

        assertEquals(CtStatus.VERIFIED, event.getStatus());
        assertEquals(crUser, event.getVerifiedBy());
    }

    @Test
    @DisplayName("VERIFIED -> VERIFIED throws InvalidStateTransitionException")
    void shouldRejectDoubleVerification() {
        CtEvent event = CtEvent.builder().id(1L).status(CtStatus.VERIFIED).scheduledAt(Instant.now()).build();

        assertThrows(InvalidStateTransitionException.class,
                () -> resolver.resolve(event.getStatus()).verify(event, crUser));
    }

    @Test
    @DisplayName("VERIFIED -> RESCHEDULED -> VERIFIED updates schedule and allows re-verification")
    void shouldAllowRescheduleAndReVerify() {
        Instant original = Instant.parse("2026-11-25T04:00:00Z");
        Instant shifted = Instant.parse("2026-11-28T05:00:00Z");
        CtEvent event = CtEvent.builder().id(1L).status(CtStatus.VERIFIED).scheduledAt(original).build();

        resolver.resolve(event.getStatus()).reschedule(event, shifted, "Lab clash", crUser);
        assertEquals(CtStatus.RESCHEDULED, event.getStatus());
        assertEquals(shifted, event.getScheduledAt());

        resolver.resolve(event.getStatus()).verify(event, crUser);
        assertEquals(CtStatus.VERIFIED, event.getStatus());
    }

    @Test
    @DisplayName("CANCELLED is terminal and rejects verify, reschedule, and cancel")
    void shouldRejectAllTransitionsFromCancelled() {
        CtEvent event = CtEvent.builder().id(1L).status(CtStatus.CANCELLED).scheduledAt(Instant.now()).build();

        assertThrows(InvalidStateTransitionException.class,
                () -> resolver.resolve(event.getStatus()).verify(event, crUser));
        assertThrows(InvalidStateTransitionException.class,
                () -> resolver.resolve(event.getStatus()).reschedule(event, Instant.now().plusSeconds(3600), "Retry", crUser));
        assertThrows(InvalidStateTransitionException.class,
                () -> resolver.resolve(event.getStatus()).cancel(event, "Already cancelled", crUser));
    }
}
