package com.kuetcord.notifications.policy;

import com.kuetcord.messaging.model.ConversationType;
import com.kuetcord.notifications.model.NotificationMode;
import com.kuetcord.notifications.model.NotificationPreference;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Strategy pattern unit tests: every mode, defaults per conversation type and timed mutes. */
class NotificationPolicyStrategyTest {

    private static final Instant SENT_AT = Instant.parse("2026-11-27T10:00:00Z");

    private final NotificationPolicy policy = new NotificationPolicy(
            List.of(new AllMessagesStrategy(), new MentionsOnlyStrategy(), new MutedStrategy()));

    private static NotificationPreference pref(NotificationMode mode, Instant muteUntil) {
        return NotificationPreference.builder().userId(1L).conversationId(9L).mode(mode).muteUntil(muteUntil).build();
    }

    private static NotificationContext plain(ConversationType type) {
        return new NotificationContext(type, false, SENT_AT);
    }

    private static NotificationContext mention(ConversationType type) {
        return new NotificationContext(type, true, SENT_AT);
    }

    @Test
    @DisplayName("ALL_MESSAGES notifies on everything; MENTIONS_ONLY only on mentions; MUTED on nothing, mentions included")
    void eachStrategyMatchesItsMode() {
        assertTrue(policy.shouldNotify(pref(NotificationMode.ALL_MESSAGES, null), plain(ConversationType.CHANNEL)));
        assertTrue(policy.shouldNotify(pref(NotificationMode.ALL_MESSAGES, null), mention(ConversationType.CHANNEL)));

        assertFalse(policy.shouldNotify(pref(NotificationMode.MENTIONS_ONLY, null), plain(ConversationType.GROUP_DM)));
        assertTrue(policy.shouldNotify(pref(NotificationMode.MENTIONS_ONLY, null), mention(ConversationType.GROUP_DM)));

        assertFalse(policy.shouldNotify(pref(NotificationMode.MUTED, null), plain(ConversationType.DIRECT)));
        assertFalse(policy.shouldNotify(pref(NotificationMode.MUTED, null), mention(ConversationType.DIRECT)));
    }

    @Test
    @DisplayName("No saved preference: DMs, group DMs and playgrounds notify on all; channels and study circles on mentions")
    void defaultsDependOnConversationType() {
        assertTrue(policy.shouldNotify(null, plain(ConversationType.DIRECT)));
        assertTrue(policy.shouldNotify(null, plain(ConversationType.GROUP_DM)));
        assertTrue(policy.shouldNotify(null, plain(ConversationType.PLAYGROUND)));

        assertFalse(policy.shouldNotify(null, plain(ConversationType.CHANNEL)));
        assertTrue(policy.shouldNotify(null, mention(ConversationType.CHANNEL)));
        assertFalse(policy.shouldNotify(null, plain(ConversationType.STUDY_CIRCLE)));
    }

    @Test
    @DisplayName("A timed mute is judged at send time: active before muteUntil, default mode from muteUntil on")
    void timedMuteIsEvaluatedAtTheMessageTimestamp() {
        NotificationPreference mutedForAnHour = pref(NotificationMode.MUTED, SENT_AT.plus(Duration.ofHours(1)));
        assertFalse(policy.shouldNotify(mutedForAnHour, plain(ConversationType.GROUP_DM)));
        assertEquals(NotificationMode.MUTED,
                NotificationPolicy.effectiveMode(mutedForAnHour, ConversationType.GROUP_DM, SENT_AT));

        NotificationPreference expired = pref(NotificationMode.MUTED, SENT_AT);
        assertTrue(policy.shouldNotify(expired, plain(ConversationType.GROUP_DM)), "muteUntil is exclusive");
        assertEquals(NotificationMode.MENTIONS_ONLY,
                NotificationPolicy.effectiveMode(expired, ConversationType.CHANNEL, SENT_AT),
                "an expired mute falls back to the type default, not to ALL_MESSAGES");
        assertFalse(policy.shouldNotify(expired, plain(ConversationType.CHANNEL)));
    }

    @Test
    @DisplayName("The policy refuses to start with a missing or duplicate strategy (fail fast at boot)")
    void registryIsCompleteAndUnique() {
        assertThrows(IllegalStateException.class,
                () -> new NotificationPolicy(List.of(new AllMessagesStrategy(), new MutedStrategy())));
        assertThrows(IllegalStateException.class,
                () -> new NotificationPolicy(List.of(new AllMessagesStrategy(), new MentionsOnlyStrategy(),
                        new MutedStrategy(), new MutedStrategy())));
    }
}
