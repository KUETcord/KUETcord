package com.kuetcord.notifications.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kuetcord.auth.ratelimit.RateLimitingFilter;
import com.kuetcord.messaging.dto.CreateGroupRequest;
import com.kuetcord.messaging.service.ConversationService;
import com.kuetcord.messaging.service.MessageService;
import com.kuetcord.notifications.outbox.OutboxProcessor;
import com.kuetcord.support.MessagingTestUsers;
import com.kuetcord.support.MessagingTestUsers.TestUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Notification Center end to end: message -> outbox -> worker -> strategy -> inbox, plus the REST API.
 * Not {@code @Transactional}: the worker processes each event in its own committed transaction.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(MessagingTestUsers.class)
class NotificationIsolationIntegrationTest {

    private static final String TITLE = "CSE 3220 Lab Team";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private MessagingTestUsers users;

    @Autowired
    private ConversationService conversationService;

    @Autowired
    private MessageService messageService;

    @Autowired
    private OutboxProcessor processor;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private RateLimitingFilter rateLimitingFilter;

    private TestUser alice;
    private TestUser bob;
    private TestUser carol;
    private TestUser dave;
    private long groupId;

    @BeforeEach
    void setUp() {
        rateLimitingFilter.clearBuckets();
        alice = users.verifiedStudent("Alice");
        bob = users.verifiedStudent("Bob");
        carol = users.verifiedStudent("Carol");
        dave = users.verifiedStudent("Dave Outsider");
        groupId = conversationService.createGroup(alice.id(), new CreateGroupRequest(TITLE, List.of(bob.id(), carol.id()))).id();
        processor.drainAll();
    }

    @Test
    @DisplayName("Bob mutes, Carol does not: only Carol is notified, and Bob's muted messages are never replayed on unmute")
    void mutedUserIsIsolatedAndNothingIsReplayed() throws Exception {
        setPreference(bob, Map.of("mode", "MUTED")).andExpect(status().isOk());

        send(alice, "Lab report draft is up");
        processor.drainAll();
        assertEquals(0, notifications(bob), "muted: nothing for Bob");
        assertEquals(1, notifications(carol), "Carol keeps the default (all messages in a group DM)");

        setPreference(bob, Map.of("mode", "ALL_MESSAGES")).andExpect(status().isOk());
        processor.drainAll();
        assertEquals(0, notifications(bob), "unmuting must not replay what was suppressed");

        send(alice, "Meeting at 4");
        processor.drainAll();
        assertEquals(1, notifications(bob), "new messages after unmuting do notify");
        assertEquals(2, notifications(carol));
    }

    @Test
    @DisplayName("A timed mute is judged at send time: a backlog processed after expiry stays silent; later messages notify")
    void timedMuteIsJudgedAtSendTimeNotAtProcessingTime() throws Exception {
        setPreference(bob, Map.of("mode", "MUTED", "muteUntil", Instant.now().plus(1, ChronoUnit.HOURS).toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.effectiveMode").value("MUTED"));

        long messageId = send(alice, "sent while Bob is muted");
        // The mute expires 1 ms after the message, before the worker gets to it (worker lag / downtime).
        jdbcTemplate.update("""
                UPDATE notification_preferences SET mute_until = (
                    SELECT occurred_at + INTERVAL '1 millisecond' FROM outbox_events
                     WHERE event_type = 'MESSAGE_CREATED' AND aggregate_id = ?)
                 WHERE user_id = ? AND conversation_id = ?""", String.valueOf(messageId), bob.id(), groupId);
        processor.drainAll();
        assertEquals(0, notifications(bob), "the message was sent inside the mute window");

        perform(bob, get("/api/v1/conversations/{id}/notification-preference", groupId), null)
                .andExpect(jsonPath("$.data.mode").value("MUTED"))
                .andExpect(jsonPath("$.data.effectiveMode").value("ALL_MESSAGES"));

        send(alice, "sent after the mute expired");
        processor.drainAll();
        assertEquals(1, notifications(bob));
    }

    @Test
    @DisplayName("MENTIONS_ONLY: plain messages are silent, an @mention notifies as MENTION; others get a MESSAGE")
    void mentionsOnlyNotifiesOnlyOnMention() throws Exception {
        setPreference(carol, Map.of("mode", "MENTIONS_ONLY"))
                .andExpect(jsonPath("$.data.mode").value("MENTIONS_ONLY"))
                .andExpect(jsonPath("$.data.explicit").value(true));

        send(alice, "plain update");
        processor.drainAll();
        assertEquals(0, notifications(carol));

        send(alice, "@" + username(carol) + " can you review the PR?");
        processor.drainAll();
        assertEquals(1, notifications(carol));
        Map<String, Object> mention = jdbcTemplate.queryForMap(
                "SELECT type, title, preview, target_type, target_id FROM notifications WHERE recipient_id = ?", carol.id());
        assertEquals("MENTION", mention.get("type"));
        assertEquals("Alice mentioned you in " + TITLE, mention.get("title"));
        assertEquals("CONVERSATION", mention.get("target_type"));
        assertEquals(groupId, ((Number) mention.get("target_id")).longValue());

        assertEquals(2, notifications(bob), "Bob (default) is notified of both, as MESSAGE");
        assertEquals(0, notifications(alice), "never about your own messages");
    }

    @Test
    @DisplayName("Inbox: keyset pages newest-first, unread badge, mark one / mark all, someone else's ID is 404")
    void inboxPaginationAndReadState() throws Exception {
        for (int i = 1; i <= 3; i++) {
            send(alice, "update " + i);
        }
        processor.drainAll();

        JsonNode first = json(perform(bob, get("/api/v1/notifications").param("limit", "2"), null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.notifications.length()").value(2))
                .andExpect(jsonPath("$.data.hasMore").value(true))
                .andExpect(jsonPath("$.data.unreadCount").value(3))
                .andExpect(jsonPath("$.data.notifications[0].preview").value("update 3")));
        long newestId = first.at("/data/notifications/0/id").asLong();
        String cursor = first.at("/data/nextCursor").asText();

        perform(bob, get("/api/v1/notifications").param("limit", "2").param("cursor", cursor), null)
                .andExpect(jsonPath("$.data.notifications.length()").value(1))
                .andExpect(jsonPath("$.data.notifications[0].preview").value("update 1"))
                .andExpect(jsonPath("$.data.hasMore").value(false));

        perform(carol, post("/api/v1/notifications/{id}/read", newestId), null).andExpect(status().isNotFound());
        perform(bob, post("/api/v1/notifications/{id}/read", newestId), null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.unreadCount").value(2));
        perform(bob, post("/api/v1/notifications/{id}/read", newestId), null)
                .andExpect(jsonPath("$.data.unreadCount").value(2)); // idempotent

        perform(bob, get("/api/v1/notifications").param("unreadOnly", "true"), null)
                .andExpect(jsonPath("$.data.notifications.length()").value(2));

        perform(bob, post("/api/v1/notifications/read-all"), null)
                .andExpect(jsonPath("$.data.updated").value(2))
                .andExpect(jsonPath("$.data.unreadCount").value(0));
        perform(bob, get("/api/v1/notifications/unread-count"), null)
                .andExpect(jsonPath("$.data.unreadCount").value(0));
        perform(carol, get("/api/v1/notifications/unread-count"), null)
                .andExpect(jsonPath("$.data.unreadCount").value(3)); // Bob's read-all never touches Carol's inbox
    }

    @Test
    @DisplayName("Preference API: defaults, validation of muteUntil, and 403 for non-participants")
    void preferenceApiValidatesInput() throws Exception {
        perform(bob, get("/api/v1/conversations/{id}/notification-preference", groupId), null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.mode").value("ALL_MESSAGES"))
                .andExpect(jsonPath("$.data.defaultMode").value("ALL_MESSAGES"))
                .andExpect(jsonPath("$.data.explicit").value(false));

        String inAnHour = Instant.now().plus(1, ChronoUnit.HOURS).toString();
        setPreference(bob, Map.of("mode", "ALL_MESSAGES", "muteUntil", inAnHour)).andExpect(status().isBadRequest());
        setPreference(bob, Map.of("mode", "MUTED", "muteUntil", Instant.now().minusSeconds(60).toString())).andExpect(status().isBadRequest());
        setPreference(bob, Map.of("mode", "MUTED", "muteUntil", Instant.now().plus(400, ChronoUnit.DAYS).toString())).andExpect(status().isBadRequest());
        setPreference(bob, Map.of("muteUntil", inAnHour)).andExpect(status().isBadRequest());
        setPreference(bob, Map.of("mode", "LOUD")).andExpect(status().isBadRequest());

        perform(dave, get("/api/v1/conversations/{id}/notification-preference", groupId), null).andExpect(status().isForbidden());
        setPreference(dave, Map.of("mode", "MUTED")).andExpect(status().isForbidden());

        setPreference(bob, Map.of("mode", "MUTED", "muteUntil", inAnHour))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.mode").value("MUTED"))
                .andExpect(jsonPath("$.data.effectiveMode").value("MUTED"))
                .andExpect(jsonPath("$.data.explicit").value(true));
        // Switching back clears the timer.
        setPreference(bob, Map.of("mode", "MENTIONS_ONLY"))
                .andExpect(jsonPath("$.data.muteUntil").doesNotExist());
    }

    // ----------------------------------------------------------------- helpers

    private long send(TestUser author, String content) {
        return messageService.send(groupId, author.id(), content, UUID.randomUUID().toString()).message().id();
    }

    private int notifications(TestUser user) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM notifications WHERE recipient_id = ? AND target_type = 'CONVERSATION' AND target_id = ?",
                Integer.class, user.id(), groupId);
        return count == null ? 0 : count;
    }

    private String username(TestUser user) {
        return jdbcTemplate.queryForObject("SELECT username FROM users WHERE id = ?", String.class, user.id());
    }

    private ResultActions setPreference(TestUser user, Map<String, Object> body) throws Exception {
        return perform(user, put("/api/v1/conversations/{id}/notification-preference", groupId), new HashMap<>(body));
    }

    private ResultActions perform(TestUser user, MockHttpServletRequestBuilder request, Object body) throws Exception {
        request.header(MessagingTestUsers.authHeader(), user.bearer());
        if (body != null) {
            request.contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(body));
        }
        return mockMvc.perform(request);
    }

    private JsonNode json(ResultActions actions) throws Exception {
        return objectMapper.readTree(actions.andReturn().getResponse().getContentAsString());
    }
}
