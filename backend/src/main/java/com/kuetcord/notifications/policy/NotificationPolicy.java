package com.kuetcord.notifications.policy;

import com.kuetcord.messaging.model.ConversationType;
import com.kuetcord.notifications.model.NotificationMode;
import com.kuetcord.notifications.model.NotificationPreference;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Context of the Strategy pattern: resolves the effective mode for a recipient and delegates the
 * decision to the matching {@link NotificationPolicyStrategy}.
 * <pre>
 *   explicit preference?  no  -> default for the conversation type
 *   MUTED but expired?    yes -> default for the conversation type
 *   otherwise                 -> the stored mode
 * </pre>
 * Defaults follow chat convention: small private spaces notify on everything, large or public
 * spaces only on mentions.
 */
@Component
public class NotificationPolicy {

    private final Map<NotificationMode, NotificationPolicyStrategy> strategies = new EnumMap<>(NotificationMode.class);

    public NotificationPolicy(List<NotificationPolicyStrategy> strategyBeans) {
        for (NotificationPolicyStrategy strategy : strategyBeans) {
            if (strategies.putIfAbsent(strategy.mode(), strategy) != null) {
                throw new IllegalStateException("Duplicate notification strategy for " + strategy.mode());
            }
        }
        for (NotificationMode mode : NotificationMode.values()) {
            if (!strategies.containsKey(mode)) {
                throw new IllegalStateException("No notification strategy registered for " + mode);
            }
        }
    }

    public static NotificationMode defaultMode(ConversationType type) {
        return switch (type) {
            case DIRECT, GROUP_DM, PLAYGROUND -> NotificationMode.ALL_MESSAGES;
            case CHANNEL, STUDY_CIRCLE -> NotificationMode.MENTIONS_ONLY;
        };
    }

    public static NotificationMode effectiveMode(NotificationPreference preference, ConversationType type, Instant at) {
        if (preference == null) {
            return defaultMode(type);
        }
        if (preference.getMode() == NotificationMode.MUTED && !MutedStrategy.isMutedAt(preference, at)) {
            return defaultMode(type);
        }
        return preference.getMode();
    }

    public boolean shouldNotify(NotificationPreference preference, NotificationContext context) {
        NotificationMode mode = effectiveMode(preference, context.conversationType(), context.occurredAt());
        return strategies.get(mode).shouldNotify(context);
    }
}
