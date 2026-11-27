package com.kuetcord.notifications.policy;

import com.kuetcord.notifications.model.NotificationMode;

/**
 * Strategy pattern: one implementation per {@link NotificationMode}. Adding a mode means adding a
 * class, not another branch in the worker.
 */
public interface NotificationPolicyStrategy {

    NotificationMode mode();

    boolean shouldNotify(NotificationContext context);
}
