package com.kuetcord.messaging.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kuetcord.auth.ratelimit.RateLimitingFilter;
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

import java.util.List;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * DMs, group DMs and Group RBAC against PostgreSQL 16 + Flyway through the real JWT security chain.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@Import(MessagingTestUsers.class)
class ConversationControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private MessagingTestUsers users;

    @Autowired
    private RateLimitingFilter rateLimitingFilter;

    private TestUser alice;
    private TestUser bob;
    private TestUser carol;
    private TestUser dave;

    @BeforeEach
    void setUp() {
        rateLimitingFilter.clearBuckets();
        alice = users.verifiedStudent("Aleksandra Wiśniewska-Kowalczyk");
        bob = users.verifiedStudent("Jo");
        carol = users.verifiedStudent("Sadia Islam");
        dave = users.verifiedStudent("Rahim Ahmed");
    }

    @Test
    @DisplayName("Group DM: fewer than 3 participants is rejected with 400")
    void shouldRejectGroupWithFewerThanThreeParticipants() throws Exception {
        perform(alice, post("/api/v1/conversations/group"),
                Map.of("title", "Too small", "memberIds", List.of(bob.id(), alice.id())))
                .andExpect(status().isBadRequest());

        perform(alice, post("/api/v1/conversations/group"),
                Map.of("title", "CSE 3220 Lab Team", "memberIds", List.of(bob.id(), carol.id())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.members.length()").value(3))
                .andExpect(jsonPath("$.data.myRole").value("GROUP_OWNER"));
    }

    @Test
    @DisplayName("Group DM: a removed member immediately gets 403 on the conversation; outsiders never get in")
    void shouldRevokeAccessForRemovedMemberAndOutsiders() throws Exception {
        long groupId = createGroup(alice, bob, carol);

        perform(dave, get("/api/v1/conversations/{id}", groupId), null).andExpect(status().isForbidden());
        perform(bob, get("/api/v1/conversations/{id}", groupId), null).andExpect(status().isOk());

        // Plain members cannot remove anyone.
        perform(carol, delete("/api/v1/conversations/{id}/members/{userId}", groupId, bob.id()), null)
                .andExpect(status().isForbidden());

        perform(alice, delete("/api/v1/conversations/{id}/members/{userId}", groupId, bob.id()), null)
                .andExpect(status().isOk());
        perform(bob, get("/api/v1/conversations/{id}", groupId), null).andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Group RBAC: admin can add members but cannot remove another admin; only the owner promotes")
    void shouldEnforceGroupRoleMatrix() throws Exception {
        long groupId = createGroup(alice, bob, carol);

        perform(bob, patch("/api/v1/conversations/{id}/members/{userId}/role", groupId, carol.id()), Map.of("role", "GROUP_ADMIN"))
                .andExpect(status().isForbidden());
        perform(alice, patch("/api/v1/conversations/{id}/members/{userId}/role", groupId, bob.id()), Map.of("role", "GROUP_ADMIN"))
                .andExpect(status().isOk());
        perform(alice, patch("/api/v1/conversations/{id}/members/{userId}/role", groupId, carol.id()), Map.of("role", "GROUP_ADMIN"))
                .andExpect(status().isOk());

        perform(bob, post("/api/v1/conversations/{id}/members", groupId), Map.of("userId", dave.id()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.members.length()").value(4));
        perform(bob, patch("/api/v1/conversations/{id}", groupId), Map.of("title", "Renamed by admin"))
                .andExpect(status().isOk());

        perform(bob, delete("/api/v1/conversations/{id}/members/{userId}", groupId, carol.id()), null)
                .andExpect(status().isForbidden());
        perform(bob, delete("/api/v1/conversations/{id}/members/{userId}", groupId, alice.id()), null)
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Owner leaving hands ownership to the senior admin; leaver loses access")
    void shouldTransferOwnershipWhenOwnerLeaves() throws Exception {
        long groupId = createGroup(alice, bob, carol);
        perform(alice, patch("/api/v1/conversations/{id}/members/{userId}/role", groupId, carol.id()), Map.of("role", "GROUP_ADMIN"))
                .andExpect(status().isOk());

        perform(alice, post("/api/v1/conversations/{id}/leave", groupId), null).andExpect(status().isOk());

        perform(alice, get("/api/v1/conversations/{id}", groupId), null).andExpect(status().isForbidden());
        perform(carol, get("/api/v1/conversations/{id}", groupId), null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.myRole").value("GROUP_OWNER"));
    }

    @Test
    @DisplayName("Direct DM: reopening returns the same conversation (201 then 200); DM with self is 400")
    void shouldReuseExistingDirectConversation() throws Exception {
        long first = json(perform(alice, post("/api/v1/conversations/direct"), Map.of("userId", bob.id()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.displayTitle").value("Jo")))
                .at("/data/id").asLong();

        perform(bob, post("/api/v1/conversations/direct"), Map.of("userId", alice.id()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(first));

        perform(alice, post("/api/v1/conversations/direct"), Map.of("userId", alice.id()))
                .andExpect(status().isBadRequest());

        perform(carol, get("/api/v1/conversations/{id}", first), null).andExpect(status().isForbidden());

        perform(alice, get("/api/v1/conversations"), null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].id").value(first))
                .andExpect(jsonPath("$.data[0].peerUserId").value(bob.id()));
    }

    private long createGroup(TestUser owner, TestUser... others) throws Exception {
        List<Long> ids = java.util.Arrays.stream(others).map(TestUser::id).toList();
        return json(perform(owner, post("/api/v1/conversations/group"), Map.of("title", "Study Group", "memberIds", ids))
                .andExpect(status().isCreated()))
                .at("/data/id").asLong();
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
