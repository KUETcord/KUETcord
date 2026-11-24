package com.kuetcord.messaging.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kuetcord.auth.ratelimit.RateLimitingFilter;
import com.kuetcord.messaging.dto.CreateGroupRequest;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Resilience: a client that was offline while messages were sent recovers exactly the gap,
 * in order, with zero duplicates, without reloading history.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@Import(MessagingTestUsers.class)
class SyncGapCatchupIntegrationTest {

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
    private RateLimitingFilter rateLimitingFilter;

    private TestUser alice;
    private TestUser bob;
    private long groupId;

    @BeforeEach
    void setUp() {
        rateLimitingFilter.clearBuckets();
        alice = users.verifiedStudent("Alice");
        bob = users.verifiedStudent("Bob");
        TestUser carol = users.verifiedStudent("Carol");
        groupId = conversationService.createGroup(alice.id(), new CreateGroupRequest("Sync group", List.of(bob.id(), carol.id()))).id();
    }

    @Test
    @DisplayName("Messages sent while offline are backfilled in order with 0 duplicates, paging through hasMore")
    void shouldBackfillMissedMessagesOnSync() throws Exception {
        long lastSeen = 0;
        for (int i = 1; i <= 5; i++) {
            lastSeen = send(alice, "before drop " + i);
        }
        // Bob's Wi-Fi drops here; 7 messages arrive while he is offline.
        List<Long> missed = new ArrayList<>();
        for (int i = 1; i <= 7; i++) {
            missed.add(send(i % 2 == 0 ? alice : bob, "during drop " + i));
        }

        LinkedHashSet<Long> recovered = new LinkedHashSet<>();
        long cursor = lastSeen;
        int calls = 0;
        boolean hasMore;
        do {
            JsonNode data = objectMapper.readTree(mockMvc.perform(get("/api/v1/conversations/{id}/sync", groupId)
                            .param("afterMessageId", String.valueOf(cursor))
                            .param("limit", "3")
                            .header(MessagingTestUsers.authHeader(), bob.bearer()))
                    .andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString()).get("data");
            for (JsonNode message : data.get("missedMessages")) {
                long id = message.get("id").asLong();
                assertEquals(true, recovered.add(id), "Duplicate " + id + " returned by sync");
                cursor = id;
            }
            hasMore = data.get("hasMore").asBoolean();
            calls++;
        } while (hasMore);

        assertEquals(missed, List.copyOf(recovered), "Exactly the gap, oldest first");
        assertEquals(3, calls, "7 messages / 3 per call = 3 calls");

        mockMvc.perform(get("/api/v1/conversations/{id}/sync", groupId)
                        .param("afterMessageId", String.valueOf(cursor))
                        .header(MessagingTestUsers.authHeader(), bob.bearer()))
                .andExpect(jsonPath("$.data.missedMessages.length()").value(0))
                .andExpect(jsonPath("$.data.hasMore").value(false));
    }

    @Test
    @DisplayName("Sync is member-only and requires afterMessageId")
    void shouldGuardSyncEndpoint() throws Exception {
        TestUser outsider = users.verifiedStudent("Outsider");
        mockMvc.perform(get("/api/v1/conversations/{id}/sync", groupId)
                        .param("afterMessageId", "0")
                        .header(MessagingTestUsers.authHeader(), outsider.bearer()))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/conversations/{id}/sync", groupId)
                        .header(MessagingTestUsers.authHeader(), bob.bearer()))
                .andExpect(status().isBadRequest());
    }

    private long send(TestUser author, String content) {
        return messageService.send(groupId, author.id(), content, UUID.randomUUID().toString()).message().id();
    }
}
