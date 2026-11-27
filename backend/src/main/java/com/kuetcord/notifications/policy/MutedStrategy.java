package com.kuetcord.notifications.policy;

import com.kuetcord.notifications.model.NotificationMode;
import com.kuetcord.notifications.model.NotificationPreference;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * Suppresses everything, mentions included. Expiry ({@code muteUntil}) is checked by
 * {@link NotificationPolicy#effectiveMode} before this strategy is chosen.
 */
@Component
public class MutedStrategy implements NotificationPolicyStrategy {

    @Override
    public NotificationMode mode() {
        return NotificationMode.MUTED;
    }

    @Override
    public boolean shouldNotify(NotificationContext context) {
        return false;
    }

    /** A mute without an end date lasts until changed; a timed mute covers {@code [set, muteUntil)}. */
    public static boolean isMutedAt(NotificationPreference preference, Instant at) {
        if (preference == null || preference.getMode() != NotificationMode.MUTED) {
            return false;
        }
        return preference.getMuteUntil() == null || at.isBefore(preference.getMuteUntil());
    }
}
