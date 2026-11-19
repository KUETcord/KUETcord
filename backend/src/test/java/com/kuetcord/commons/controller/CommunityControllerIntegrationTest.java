package com.kuetcord.commons.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kuetcord.auth.ratelimit.RateLimitingFilter;
import com.kuetcord.commons.model.CommunityPermission;
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
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Discord-style server RBAC against PostgreSQL 16 + Flyway (V1-V5) through the real security chain.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@Import(MessagingTestUsers.class)
class CommunityControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private MessagingTestUsers users;

    @Autowired
    private RateLimitingFilter rateLimitingFilter;

    private TestUser owner;
    private TestUser moderator;
    private TestUser student;
    private long communityId;

    @BeforeEach
    void setUp() throws Exception {
        rateLimitingFilter.clearBuckets();
        owner = users.verifiedStudent("Tashfiq Rahman");
        moderator = users.verifiedStudent("Sadia Islam");
        student = users.verifiedStudent("Rahim Ahmed");

        JsonNode created = json(perform(owner, post("/api/v1/communities"),
                Map.of("name", "KUET CSE 2k21", "description", "Batch hub"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.channels[0].name").value("general"))
                .andExpect(jsonPath("$.data.roles[0].defaultRole").value(true)));
        communityId = created.at("/data/id").asLong();

        perform(moderator, post("/api/v1/communities/{id}/join", communityId), null).andExpect(status().isOk());
        perform(student, post("/api/v1/communities/{id}/join", communityId), null).andExpect(status().isOk());

        long modRoleId = json(perform(owner, post("/api/v1/communities/{id}/roles", communityId), Map.of(
                "name", "Batch Moderator",
                "color", "#2ECC71",
                "position", 5,
                "permissions", CommunityPermission.KICK_MEMBERS.bit() | CommunityPermission.MANAGE_MESSAGES.bit()))
                .andExpect(status().isCreated()))
                .at("/data/id").asLong();
        perform(owner, post("/api/v1/communities/{id}/members/{userId}/roles/{roleId}", communityId, moderator.id(), modRoleId), null)
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("KICK: plain member gets 403; moderator kicks member; nobody can kick the owner")
    void shouldEnforceKickPermissionAndHierarchy() throws Exception {
        perform(student, delete("/api/v1/communities/{id}/members/{userId}", communityId, moderator.id()), null)
                .andExpect(status().isForbidden());

        perform(moderator, delete("/api/v1/communities/{id}/members/{userId}", communityId, owner.id()), null)
                .andExpect(status().isForbidden());

        perform(moderator, delete("/api/v1/communities/{id}/members/{userId}", communityId, student.id()), null)
                .andExpect(status().isOk());

        // Kicked student immediately loses member-only reads (permission cache was evicted).
        perform(student, get("/api/v1/communities/{id}", communityId), null)
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("BAN: moderator without BAN_MEMBERS is denied; banned user cannot rejoin until unbanned")
    void shouldBlockBannedUserFromRejoining() throws Exception {
        perform(moderator, post("/api/v1/communities/{id}/bans", communityId), Map.of("userId", student.id()))
                .andExpect(status().isForbidden());

        perform(owner, post("/api/v1/communities/{id}/bans", communityId), Map.of("userId", student.id(), "reason", "spam"))
                .andExpect(status().isCreated());

        perform(student, post("/api/v1/communities/{id}/join", communityId), null)
                .andExpect(status().isForbidden());

        perform(owner, delete("/api/v1/communities/{id}/bans/{userId}", communityId, student.id()), null)
                .andExpect(status().isOk());
        perform(student, post("/api/v1/communities/{id}/join", communityId), null)
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Roles: a MANAGE_ROLES holder cannot grant permissions they lack (no privilege escalation)")
    void shouldPreventPrivilegeEscalationViaRoles() throws Exception {
        long rolesManagerId = json(perform(owner, post("/api/v1/communities/{id}/roles", communityId), Map.of(
                "name", "Role Manager", "position", 8,
                "permissions", CommunityPermission.MANAGE_ROLES.bit() | CommunityPermission.SEND_MESSAGES.bit()))
                .andExpect(status().isCreated())).at("/data/id").asLong();
        perform(owner, post("/api/v1/communities/{id}/members/{userId}/roles/{roleId}", communityId, moderator.id(), rolesManagerId), null)
                .andExpect(status().isOk());

        perform(moderator, post("/api/v1/communities/{id}/roles", communityId), Map.of(
                "name", "Sneaky Admin", "position", 2, "permissions", CommunityPermission.BAN_MEMBERS.bit()))
                .andExpect(status().isForbidden());

        perform(moderator, post("/api/v1/communities/{id}/roles", communityId), Map.of(
                "name", "Too High", "position", 9, "permissions", CommunityPermission.SEND_MESSAGES.bit()))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Private channels are hidden from members without channel access")
    void shouldHidePrivateChannelsFromRegularMembers() throws Exception {
        perform(owner, post("/api/v1/communities/{id}/channels", communityId), Map.of("name", "CR Desk", "privateChannel", true))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.name").value("cr-desk"));

        perform(student, post("/api/v1/communities/{id}/channels", communityId), Map.of("name", "nope"))
                .andExpect(status().isForbidden());

        perform(student, get("/api/v1/communities/{id}/channels", communityId), null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].name").value("general"));

        perform(owner, get("/api/v1/communities/{id}/channels", communityId), null)
                .andExpect(jsonPath("$.data.length()").value(2));
    }

    private ResultActions perform(TestUser as,
                                  org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request,
                                  Object body) throws Exception {
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
