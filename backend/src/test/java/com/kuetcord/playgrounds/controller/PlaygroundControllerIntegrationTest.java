package com.kuetcord.playgrounds.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kuetcord.auth.ratelimit.RateLimitingFilter;
import com.kuetcord.playgrounds.support.PlaygroundApi;
import com.kuetcord.support.MessagingTestUsers;
import com.kuetcord.support.MessagingTestUsers.TestUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Project Playground workspaces against PostgreSQL 16 + Flyway (V1-V9) through the real JWT chain.
 * Each test runs in a rolled-back transaction.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@Import(MessagingTestUsers.class)
class PlaygroundControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private MessagingTestUsers users;

    @Autowired
    private RateLimitingFilter rateLimitingFilter;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private PlaygroundApi api;
    private TestUser lead;
    private TestUser teammate;
    private TestUser outsider;

    @BeforeEach
    void setUp() {
        rateLimitingFilter.clearBuckets();
        api = new PlaygroundApi(mockMvc, objectMapper);
        lead = users.verifiedStudent("Nusrat Jahan");
        teammate = users.verifiedStudent("Arif Hossain");
        outsider = users.verifiedStudent("Tanvir Ahmed");
    }

    @Test
    @DisplayName("Create: the creator becomes LEAD, the board starts empty and the playground shows in 'my playgrounds'")
    void shouldCreatePlaygroundWithCreatorAsLead() throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("title", "  Team KUETcord  ");
        body.put("problemStatement", "Unify CT schedules, contests and peer collaboration.");
        body.put("tags", List.of("Spring Boot", "React", "spring boot"));
        body.put("repoUrl", "https://github.com/kuetcord/kuetcord");

        long id = api.json(api.perform(lead, post("/api/v1/playgrounds"), body)
                        .andExpect(status().isCreated())
                        .andExpect(jsonPath("$.data.title").value("Team KUETcord"))
                        .andExpect(jsonPath("$.data.visibility").value("PUBLIC"))
                        .andExpect(jsonPath("$.data.status").value("PLANNING"))
                        .andExpect(jsonPath("$.data.myRole").value("LEAD"))
                        .andExpect(jsonPath("$.data.version").value(0))
                        .andExpect(jsonPath("$.data.tags.length()").value(2))
                        .andExpect(jsonPath("$.data.members.length()").value(1))
                        .andExpect(jsonPath("$.data.members[0].userId").value(lead.id()))
                        .andExpect(jsonPath("$.data.members[0].role").value("LEAD"))
                        .andExpect(jsonPath("$.data.taskCounts.BACKLOG").value(0))
                        .andExpect(jsonPath("$.data.taskCounts.DONE").value(0)))
                .at("/data/id").asLong();

        api.perform(lead, get("/api/v1/playgrounds"), null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].id").value(id))
                .andExpect(jsonPath("$.data[0].myRole").value("LEAD"))
                .andExpect(jsonPath("$.data[0].memberCount").value(1));
    }

    @Test
    @DisplayName("Visibility: a PUBLIC playground is readable by outsiders, a PRIVATE one is a 404 to them")
    void shouldHidePrivatePlaygroundsFromNonMembers() throws Exception {
        long publicId = api.createPlayground(lead, "Open Robotics Team", "PUBLIC");
        long privateId = api.createPlayground(lead, "Secret Hackathon Entry", "PRIVATE");

        api.perform(outsider, get("/api/v1/playgrounds/{id}", publicId), null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.myRole").doesNotExist());
        api.perform(outsider, get("/api/v1/playgrounds/{id}", privateId), null)
                .andExpect(status().isNotFound());
        api.perform(outsider, get("/api/v1/playgrounds/{id}/tasks", privateId), null)
                .andExpect(status().isNotFound());
        api.perform(outsider, post("/api/v1/playgrounds/{id}/tasks", publicId), Map.of("title", "Drive-by task"))
                .andExpect(status().isForbidden());
        api.perform(outsider, get("/api/v1/playgrounds"), null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[*].id", not(hasItem((int) publicId))));
    }

    @Test
    @DisplayName("Members: only a LEAD invites; inviting twice is 409 and an unknown user is 404")
    void shouldInviteMembersAsLead() throws Exception {
        long id = api.createPlayground(lead, "ICPC Prep Squad", "PRIVATE");

        api.perform(lead, post("/api/v1/playgrounds/{id}/members", id),
                        Map.of("userId", teammate.id(), "role", "CONTRIBUTOR"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[1].displayName").value("Arif Hossain"))
                .andExpect(jsonPath("$.data[1].role").value("CONTRIBUTOR"));

        api.perform(lead, post("/api/v1/playgrounds/{id}/members", id), Map.of("userId", teammate.id()))
                .andExpect(status().isConflict());
        api.perform(teammate, post("/api/v1/playgrounds/{id}/members", id), Map.of("userId", outsider.id()))
                .andExpect(status().isForbidden());
        api.perform(lead, post("/api/v1/playgrounds/{id}/members", id), Map.of("userId", Long.MAX_VALUE))
                .andExpect(status().isNotFound());

        api.perform(teammate, get("/api/v1/playgrounds"), null)
                .andExpect(jsonPath("$.data[0].id").value(id))
                .andExpect(jsonPath("$.data[0].myRole").value("CONTRIBUTOR"))
                .andExpect(jsonPath("$.data[0].memberCount").value(2));
    }

    @Test
    @DisplayName("PATCH metadata: version moves 0 -> 1; replaying the stale version is 409 and changes nothing")
    void shouldRejectStaleMetadataEdit() throws Exception {
        long id = api.createPlayground(lead, "Campus Pulse", "PUBLIC");
        api.addMember(lead, id, teammate, "CONTRIBUTOR");

        api.perform(lead, patch("/api/v1/playgrounds/{id}", id),
                        Map.of("title", "Campus Pulse v2", "status", "IN_PROGRESS", "repoUrl", "https://github.com/x/y",
                                "expectedVersion", 0))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.title").value("Campus Pulse v2"))
                .andExpect(jsonPath("$.data.status").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.data.version").value(1));

        api.perform(lead, patch("/api/v1/playgrounds/{id}", id), Map.of("title", "Lost update", "expectedVersion", 0))
                .andExpect(status().isConflict());
        api.perform(teammate, patch("/api/v1/playgrounds/{id}", id), Map.of("title", "Hijack", "expectedVersion", 1))
                .andExpect(status().isForbidden());
        api.perform(lead, patch("/api/v1/playgrounds/{id}", id), Map.of("title", "No version"))
                .andExpect(status().isBadRequest());

        api.perform(lead, patch("/api/v1/playgrounds/{id}", id), Map.of("repoUrl", "", "expectedVersion", 1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.title").value("Campus Pulse v2"))
                .andExpect(jsonPath("$.data.repoUrl").doesNotExist())
                .andExpect(jsonPath("$.data.version").value(2));
    }

    @Test
    @DisplayName("Archive: an ARCHIVED playground rejects board writes with 409 until a lead reopens it")
    void shouldFreezeArchivedPlayground() throws Exception {
        long id = api.createPlayground(lead, "Finished Project", "PUBLIC");

        api.perform(lead, patch("/api/v1/playgrounds/{id}", id), Map.of("status", "ARCHIVED", "expectedVersion", 0))
                .andExpect(status().isOk());
        api.perform(lead, post("/api/v1/playgrounds/{id}/tasks", id), Map.of("title", "One more thing"))
                .andExpect(status().isConflict());
        api.perform(lead, post("/api/v1/playgrounds/{id}/decisions", id), Map.of("decision", "Ship it"))
                .andExpect(status().isConflict());
        api.perform(lead, get("/api/v1/playgrounds/{id}/tasks", id), null)
                .andExpect(status().isOk());

        api.perform(lead, patch("/api/v1/playgrounds/{id}", id), Map.of("status", "IN_PROGRESS", "expectedVersion", 1))
                .andExpect(status().isOk());
        api.perform(lead, post("/api/v1/playgrounds/{id}/tasks", id), Map.of("title", "One more thing"))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("Validation: blank title and non-http links are 400")
    void shouldValidateCreateRequest() throws Exception {
        api.perform(lead, post("/api/v1/playgrounds"), Map.of("title", " "))
                .andExpect(status().isBadRequest());
        api.perform(lead, post("/api/v1/playgrounds"), Map.of("title", "X", "demoUrl", "javascript:alert(1)"))
                .andExpect(status().isBadRequest());
        api.perform(lead, post("/api/v1/playgrounds"), Map.of("title", "X", "visibility", "SECRET"))
                .andExpect(status().isBadRequest());
    }

    // One violation per test: PostgreSQL aborts the surrounding transaction after the first failed statement.

    @Test
    @DisplayName("V9 CHECK constraint rejects an unknown playground status written past the API")
    void shouldEnforcePlaygroundStatusConstraint() throws Exception {
        long id = api.createPlayground(lead, "Constraint Probe", "PUBLIC");
        assertThrows(DataIntegrityViolationException.class,
                () -> jdbcTemplate.update("UPDATE playgrounds SET status = 'DELETED' WHERE id = ?", id));
    }

    @Test
    @DisplayName("V9 CHECK constraint rejects a Kanban status that is not one of the five columns")
    void shouldEnforceTaskStatusConstraint() throws Exception {
        long id = api.createPlayground(lead, "Constraint Probe", "PUBLIC");
        long taskId = api.createTask(lead, id, "Probe", "TODO", null).get("id").asLong();
        assertThrows(DataIntegrityViolationException.class,
                () -> jdbcTemplate.update("UPDATE project_tasks SET status = 'BLOCKED' WHERE id = ?", taskId));
    }
}
