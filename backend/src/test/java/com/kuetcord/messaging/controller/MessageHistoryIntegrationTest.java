package com.kuetcord.messaging.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kuetcord.auth.ratelimit.RateLimitingFilter;
import com.kuetcord.messaging.dto.CreateGroupRequest;
import com.kuetcord.messaging.repository.MessageMentionRepository;
import com.kuetcord.messaging.service.ConversationService;
import com.kuetcord.messaging.service.MessageService;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Keyset history pagination, read-state and unread counts against PostgreSQL 16.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@Import(MessagingTestUsers.class)
class MessageHistoryIntegrationTest {

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
    private MessageMentionRepository mentionRepository;

    @Autowired
    private RateLimitingFilter rateLimitingFilter;

    private TestUser alice;
    private TestUser bob;
    private TestUser carol;
    private long groupId;

    @BeforeEach
    void setUp() {
        rateLimitingFilter.clearBuckets();
        alice = users.verifiedStudent("Alice");
        bob = users.verifiedStudent("Bob");
        carol = users.verifiedStudent("Carol");
        groupId = conversationService.createGroup(alice.id(), new CreateGroupRequest("History group", List.of(bob.id(), carol.id()))).id();
    }

    @Test
    @DisplayName("Cursor pagination walks 75 messages in pages of 30 with no gaps or duplicates")
    void shouldPaginateChatHistoryWithCursor() throws Exception {
        List<Long> sent = new ArrayList<>();
        for (int i = 1; i <= 75; i++) {
            TestUser author = i % 2 == 0 ? alice : bob;
            sent.add(messageService.send(groupId, author.id(), "msg " + i, UUID.randomUUID().toString()).message().id());
        }

        Set<Long> seen = new HashSet<>();
        List<Long> ordered = new ArrayList<>();
        String cursor = null;
        int pages = 0;
        do {
            MockHttpServletRequestBuilder request = get("/api/v1/conversations/{id}/messages", groupId).param("limit", "30");
            if (cursor != null) {
                request.param("cursor", cursor);
            }
            JsonNode data = json(perform(carol, request, null).andExpect(status().isOk())).get("data");
            List<Long> pageIds = new ArrayList<>();
            data.get("messages").forEach(m -> pageIds.add(m.get("id").asLong()));
            assertTrue(pageIds.size() <= 30);
            for (int i = 1; i < pageIds.size(); i++) {
                assertTrue(pageIds.get(i - 1) < pageIds.get(i), "Each page must be chronological");
            }
            pageIds.forEach(id -> assertTrue(seen.add(id), "Duplicate message " + id + " across pages"));
            ordered.addAll(0, pageIds);
            cursor = data.get("hasMore").asBoolean() ? data.get("nextCursor").asText() : null;
            pages++;
        } while (cursor != null);

        assertEquals(3, pages, "75 messages / 30 per page = 3 pages");
        assertEquals(sent, ordered, "Concatenated pages must equal the full history in order");
    }

    @Test
    @DisplayName("Read marker is monotonic and drives unread counts; outsiders cannot read history")
    void shouldTrackReadStateAndUnreadCounts() throws Exception {
        long m1 = messageService.send(groupId, alice.id(), "one", UUID.randomUUID().toString()).message().id();
        long m2 = messageService.send(groupId, alice.id(), "two", UUID.randomUUID().toString()).message().id();
        long m3 = messageService.send(groupId, alice.id(), "three", UUID.randomUUID().toString()).message().id();

        perform(bob, get("/api/v1/conversations"), null)
                .andExpect(jsonPath("$.data[0].id").value(groupId))
                .andExpect(jsonPath("$.data[0].unreadCount").value(3));
        perform(alice, get("/api/v1/conversations"), null)
                .andExpect(jsonPath("$.data[0].unreadCount").value(0));

        perform(bob, post("/api/v1/conversations/{id}/read-state", groupId), Map.of("lastReadMessageId", m2))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.lastReadMessageId").value(m2))
                .andExpect(jsonPath("$.data.unreadCount").value(1));

        // A stale tab reporting an older position must not move the marker backwards.
        perform(bob, post("/api/v1/conversations/{id}/read-state", groupId), Map.of("lastReadMessageId", m1))
                .andExpect(jsonPath("$.data.lastReadMessageId").value(m2));

        perform(bob, post("/api/v1/conversations/{id}/read-state", groupId), Map.of("lastReadMessageId", m3))
                .andExpect(jsonPath("$.data.unreadCount").value(0));

        TestUser outsider = users.verifiedStudent("Outsider");
        perform(outsider, get("/api/v1/conversations/{id}/messages", groupId), null).andExpect(status().isForbidden());
        perform(outsider, post("/api/v1/conversations/{id}/read-state", groupId), Map.of("lastReadMessageId", m1))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("@mentions are stored only for users who can read the conversation")
    void shouldStoreMentionsOnlyForParticipants() throws Exception {
        TestUser outsider = users.verifiedStudent("Outsider");
        String bobUsername = username(bob);
        String outsiderUsername = username(outsider);

        long messageId = messageService.send(groupId, alice.id(),
                "@" + bobUsername + " please review; also @" + outsiderUsername + " and @" + username(alice),
                UUID.randomUUID().toString()).message().id();

        List<Long> mentioned = mentionRepository.findByMessageId(messageId).stream().map(m -> m.getMentionedUserId()).toList();
        assertEquals(List.of(bob.id()), mentioned, "Outsiders and the sender themselves are never recorded");
        assertFalse(mentioned.contains(outsider.id()));
    }

    private String username(TestUser user) throws Exception {
        return json(perform(user, get("/api/v1/me"), null)).at("/data/username").asText();
    }

    private ResultActions perform(TestUser as, MockHttpServletRequestBuilder request, Object body) throws Exception {
        request.header(MessagingTestUsers.authHeader(), as.bearer());
        if (body != null) {
            request.contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(body));
        }
        return mockMvc.perform(request);
    }

    private JsonNode json(ResultActions actions) throws Exception {
        return objectMapper.readTree(actions.andReturn().getResponse().getContentAsString());
    }
}
