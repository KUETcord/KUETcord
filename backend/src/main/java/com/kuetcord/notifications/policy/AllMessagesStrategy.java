package com.kuetcord.notifications.policy;

import com.kuetcord.notifications.model.NotificationMode;
import org.springframework.stereotype.Component;

@Component
public class AllMessagesStrategy implements NotificationPolicyStrategy {

    @Override
    public NotificationMode mode() {
        return NotificationMode.ALL_MESSAGES;
    }

    @Override
    public boolean shouldNotify(NotificationContext context) {
        return true;
    }
}
