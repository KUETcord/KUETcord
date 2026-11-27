package com.kuetcord.notifications.policy;

import com.kuetcord.notifications.model.NotificationMode;
import org.springframework.stereotype.Component;

@Component
public class MentionsOnlyStrategy implements NotificationPolicyStrategy {

    @Override
    public NotificationMode mode() {
        return NotificationMode.MENTIONS_ONLY;
    }

    @Override
    public boolean shouldNotify(NotificationContext context) {
        return context.mentioned();
    }
}
