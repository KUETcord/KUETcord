package com.kuetcord.playgrounds.controller;

import com.fasterxml.jackson.databind.JsonNode;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Kanban board end to end: column grouping, card moves, assignment rules and the optimistic-locking
 * contract (stale {@code expectedVersion} -> 409, nothing overwritten).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@Import(MessagingTestUsers.class)
class ProjectTaskControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private MessagingTestUsers users;

    @Autowired
    private RateLimitingFilter rateLimitingFilter;

    private PlaygroundApi api;
    private TestUser lead;
    private TestUser contributor;
    private TestUser viewer;
    private long playgroundId;

    @BeforeEach
    void setUp() throws Exception {
        rateLimitingFilter.clearBuckets();
        api = new PlaygroundApi(mockMvc, objectMapper);
        lead = users.verifiedStudent("Nusrat Jahan");
        contributor = users.verifiedStudent("Arif Hossain");
        viewer = users.verifiedStudent("Mentor Rahim");
        playgroundId = api.createPlayground(lead, "KUET Hackathon Team", "PRIVATE");
        api.addMember(lead, playgroundId, contributor, "CONTRIBUTOR");
        api.addMember(lead, playgroundId, viewer, "VIEWER");
    }

    @Test
    @DisplayName("Board: five columns in Kanban order, cards stacked by position, assignee name joined in")
    void shouldGroupBoardIntoColumns() throws Exception {
        api.createTask(lead, playgroundId, "Design schema", null, null);
        api.createTask(lead, playgroundId, "Write ADR", null, contributor.id());
        api.createTask(contributor, playgroundId, "Set up CI", "TODO", null);

        api.perform(viewer, get("/api/v1/playgrounds/{id}/tasks", playgroundId), null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalTasks").value(3))
                .andExpect(jsonPath("$.data.columns.length()").value(5))
                .andExpect(jsonPath("$.data.columns[0].status").value("BACKLOG"))
                .andExpect(jsonPath("$.data.columns[4].status").value("DONE"))
                .andExpect(jsonPath("$.data.columns[0].tasks.length()").value(2))
                .andExpect(jsonPath("$.data.columns[0].tasks[0].title").value("Design schema"))
                .andExpect(jsonPath("$.data.columns[0].tasks[0].position").value(0))
                .andExpect(jsonPath("$.data.columns[0].tasks[1].position").value(1))
                .andExpect(jsonPath("$.data.columns[0].tasks[1].assignee.displayName").value("Arif Hossain"))
                .andExpect(jsonPath("$.data.columns[0].tasks[1].version").value(0))
                .andExpect(jsonPath("$.data.columns[1].tasks[0].title").value("Set up CI"))
                .andExpect(jsonPath("$.data.columns[3].tasks.length()").value(0));

        api.perform(lead, get("/api/v1/playgrounds/{id}", playgroundId), null)
                .andExpect(jsonPath("$.data.taskCounts.BACKLOG").value(2))
                .andExpect(jsonPath("$.data.taskCounts.TODO").value(1));
    }

    @Test
    @DisplayName("Move + assign with the current version: card lands at the end of the new column, version 0 -> 1")
    void shouldMoveAndAssignTask() throws Exception {
        api.createTask(lead, playgroundId, "Already in progress", "IN_PROGRESS", null);
        JsonNode task = api.createTask(lead, playgroundId, "Implement STOMP reconnect", "TODO", null);

        api.perform(contributor, patch("/api/v1/playgrounds/{id}/tasks/{taskId}", playgroundId, task.get("id").asLong()),
                        Map.of("status", "IN_PROGRESS", "assigneeId", contributor.id(), "expectedVersion", 0))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.data.position").value(1))
                .andExpect(jsonPath("$.data.assignee.id").value(contributor.id()))
                .andExpect(jsonPath("$.data.assignee.displayName").value("Arif Hossain"))
                .andExpect(jsonPath("$.data.priority").value("HIGH"))
                .andExpect(jsonPath("$.data.version").value(1));
    }

    @Test
    @DisplayName("Lost update prevented: the second teammate's stale write is 409 and the first write survives")
    void shouldRejectStaleTaskUpdate() throws Exception {
        long taskId = api.createTask(lead, playgroundId, "Integrate @Version on cards", "TODO", null).get("id").asLong();

        // Both teammates loaded the board at version 0.
        api.perform(contributor, patch("/api/v1/playgrounds/{id}/tasks/{taskId}", playgroundId, taskId),
                        Map.of("status", "IN_PROGRESS", "assigneeId", contributor.id(), "expectedVersion", 0))
                .andExpect(status().isOk());
        api.perform(lead, patch("/api/v1/playgrounds/{id}/tasks/{taskId}", playgroundId, taskId),
                        Map.of("status", "REVIEW", "clearAssignee", true, "expectedVersion", 0))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Resource was modified by another concurrent action. Please reload."));

        api.perform(lead, get("/api/v1/playgrounds/{id}/tasks/{taskId}", playgroundId, taskId), null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.data.assignee.id").value(contributor.id()))
                .andExpect(jsonPath("$.data.version").value(1));

        // After reloading, the lead retries with the fresh version and succeeds.
        api.perform(lead, patch("/api/v1/playgrounds/{id}/tasks/{taskId}", playgroundId, taskId),
                        Map.of("status", "REVIEW", "expectedVersion", 1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("REVIEW"))
                .andExpect(jsonPath("$.data.assignee.id").value(contributor.id()))
                .andExpect(jsonPath("$.data.version").value(2));
    }

    @Test
    @DisplayName("Assignment: only LEAD/CONTRIBUTOR members can own a card; clearAssignee removes the owner")
    void shouldValidateAssignee() throws Exception {
        TestUser outsider = users.verifiedStudent("Outsider");
        long taskId = api.createTask(lead, playgroundId, "Pick an owner", "TODO", lead.id()).get("id").asLong();

        String path = "/api/v1/playgrounds/{id}/tasks/{taskId}";
        api.perform(lead, patch(path, playgroundId, taskId), Map.of("assigneeId", viewer.id(), "expectedVersion", 0))
                .andExpect(status().isBadRequest());
        api.perform(lead, patch(path, playgroundId, taskId), Map.of("assigneeId", outsider.id(), "expectedVersion", 0))
                .andExpect(status().isBadRequest());
        api.perform(lead, patch(path, playgroundId, taskId),
                        Map.of("assigneeId", lead.id(), "clearAssignee", true, "expectedVersion", 0))
                .andExpect(status().isBadRequest());
        api.perform(lead, patch(path, playgroundId, taskId), Map.of("clearAssignee", true, "expectedVersion", 0))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.assignee").doesNotExist())
                .andExpect(jsonPath("$.data.version").value(1));
    }

    @Test
    @DisplayName("Roles and scoping: a VIEWER reads but cannot write; a task id from another playground is 404")
    void shouldEnforceRolesAndScoping() throws Exception {
        long taskId = api.createTask(lead, playgroundId, "Scoped task", "TODO", null).get("id").asLong();
        long otherPlayground = api.createPlayground(lead, "Another Team", "PUBLIC");

        api.perform(viewer, post("/api/v1/playgrounds/{id}/tasks", playgroundId), Map.of("title", "Viewer task"))
                .andExpect(status().isForbidden());
        api.perform(viewer, patch("/api/v1/playgrounds/{id}/tasks/{taskId}", playgroundId, taskId),
                        Map.of("status", "DONE", "expectedVersion", 0))
                .andExpect(status().isForbidden());
        api.perform(viewer, get("/api/v1/playgrounds/{id}/tasks/{taskId}", playgroundId, taskId), null)
                .andExpect(status().isOk());
        api.perform(lead, get("/api/v1/playgrounds/{id}/tasks/{taskId}", otherPlayground, taskId), null)
                .andExpect(status().isNotFound());
        api.perform(lead, patch("/api/v1/playgrounds/{id}/tasks/{taskId}", playgroundId, taskId),
                        Map.of("status", "DONE"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Delete: a stale version is 409; the current version deletes the card (204)")
    void shouldDeleteWithVersionCheck() throws Exception {
        long taskId = api.createTask(lead, playgroundId, "Obsolete spike", null, null).get("id").asLong();
        api.perform(contributor, patch("/api/v1/playgrounds/{id}/tasks/{taskId}", playgroundId, taskId),
                        Map.of("priority", "LOW", "expectedVersion", 0))
                .andExpect(status().isOk());

        api.perform(lead, delete("/api/v1/playgrounds/{id}/tasks/{taskId}", playgroundId, taskId)
                        .param("expectedVersion", "0"), null)
                .andExpect(status().isConflict());
        api.perform(lead, delete("/api/v1/playgrounds/{id}/tasks/{taskId}", playgroundId, taskId)
                        .param("expectedVersion", "1"), null)
                .andExpect(status().isNoContent());
        api.perform(lead, get("/api/v1/playgrounds/{id}/tasks/{taskId}", playgroundId, taskId), null)
                .andExpect(status().isNotFound());
        api.perform(lead, delete("/api/v1/playgrounds/{id}/tasks/{taskId}", playgroundId, taskId), null)
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Create validation: blank title and unknown status are 400")
    void shouldValidateCreateTask() throws Exception {
        api.perform(lead, post("/api/v1/playgrounds/{id}/tasks", playgroundId), Map.of("title", ""))
                .andExpect(status().isBadRequest());
        Map<String, Object> badStatus = new HashMap<>();
        badStatus.put("title", "Task");
        badStatus.put("status", "BLOCKED");
        api.perform(lead, post("/api/v1/playgrounds/{id}/tasks", playgroundId), badStatus)
                .andExpect(status().isBadRequest());
    }
}
