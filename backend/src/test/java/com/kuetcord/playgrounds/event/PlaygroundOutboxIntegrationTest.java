package com.kuetcord.playgrounds.event;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kuetcord.auth.ratelimit.RateLimitingFilter;
import com.kuetcord.common.outbox.OutboxEvent;
import com.kuetcord.common.outbox.OutboxEventRepository;
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

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Transactional outbox for playgrounds: {@code TASK_ASSIGNED} and {@code MILESTONE_REACHED} rows are written
 * with the state change, only when someone actually needs to be told, and never twice for one event.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@Import(MessagingTestUsers.class)
class PlaygroundOutboxIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private MessagingTestUsers users;

    @Autowired
    private RateLimitingFilter rateLimitingFilter;

    @Autowired
    private OutboxEventRepository outboxRepository;

    private PlaygroundApi api;
    private TestUser lead;
    private TestUser contributor;
    private TestUser secondContributor;
    private TestUser viewer;
    private long playgroundId;

    @BeforeEach
    void setUp() throws Exception {
        rateLimitingFilter.clearBuckets();
        api = new PlaygroundApi(mockMvc, objectMapper);
        lead = users.verifiedStudent("Nusrat Jahan");
        contributor = users.verifiedStudent("Arif Hossain");
        secondContributor = users.verifiedStudent("Sadia Islam");
        viewer = users.verifiedStudent("Mentor Rahim");
        playgroundId = api.createPlayground(lead, "Outbox Team", "PRIVATE");
        api.addMember(lead, playgroundId, contributor, "CONTRIBUTOR");
        api.addMember(lead, playgroundId, secondContributor, "CONTRIBUTOR");
        api.addMember(lead, playgroundId, viewer, "VIEWER");
    }

    @Test
    @DisplayName("TASK_ASSIGNED is written when a card is created for, or handed to, someone else")
    void shouldEmitTaskAssignedForNewAssignee() throws Exception {
        long taskId = api.createTask(lead, playgroundId, "Write the pitch deck", "TODO", contributor.id())
                .get("id").asLong();

        List<OutboxEvent> events = taskEvents(taskId);
        assertEquals(1, events.size());
        OutboxEvent event = events.get(0);
        assertEquals("TASK_ASSIGNED", event.getEventType());
        assertEquals("PENDING", event.getStatus().name());
        Map<String, Object> payload = event.getPayload();
        assertEquals(contributor.id(), asLong(payload.get("assigneeId")));
        assertEquals(lead.id(), asLong(payload.get("assignedById")));
        assertEquals(playgroundId, asLong(payload.get("playgroundId")));
        assertEquals("Outbox Team", payload.get("playgroundTitle"));
        assertEquals("Write the pitch deck", payload.get("taskTitle"));
        assertEquals(List.of(contributor.id()), asLongs(payload.get("recipientIds")));

        // Hand it over to a second teammate: one more event, addressed to them.
        update(lead, taskId, Map.of("assigneeId", secondContributor.id(), "expectedVersion", 0));
        assertEquals(2, taskEvents(taskId).size());
        assertEquals(secondContributor.id(), asLong(taskEvents(taskId).get(1).getPayload().get("assigneeId")));
    }

    @Test
    @DisplayName("No TASK_ASSIGNED for self-assignment, re-saving the same assignee, unassigning or moving a card")
    void shouldNotEmitWhenNobodyNeedsTelling() throws Exception {
        long selfTask = api.createTask(contributor, playgroundId, "I'll take this", "TODO", contributor.id())
                .get("id").asLong();
        assertEquals(0, taskEvents(selfTask).size());

        long taskId = api.createTask(lead, playgroundId, "Unassigned card", "TODO", null).get("id").asLong();
        update(contributor, taskId, Map.of("assigneeId", contributor.id(), "expectedVersion", 0)); // self
        update(lead, taskId, Map.of("assigneeId", contributor.id(), "status", "IN_PROGRESS", "expectedVersion", 1)); // same
        update(lead, taskId, Map.of("status", "REVIEW", "expectedVersion", 2)); // move only
        update(lead, taskId, Map.of("clearAssignee", true, "expectedVersion", 3)); // unassign
        assertEquals(0, taskEvents(taskId).size());
    }

    @Test
    @DisplayName("A stale (409) assignment writes no outbox row")
    void shouldNotEmitWhenUpdateIsRejected() throws Exception {
        long taskId = api.createTask(lead, playgroundId, "Contended card", "TODO", null).get("id").asLong();
        update(lead, taskId, Map.of("priority", "LOW", "expectedVersion", 0));

        api.perform(lead, patch("/api/v1/playgrounds/{id}/tasks/{taskId}", playgroundId, taskId),
                        Map.of("assigneeId", contributor.id(), "expectedVersion", 0))
                .andExpect(status().isConflict());
        assertEquals(0, taskEvents(taskId).size());
    }

    @Test
    @DisplayName("MILESTONE_REACHED: written once on completion, to every member but the completer; idempotent; reopenable")
    void shouldEmitMilestoneReachedOnce() throws Exception {
        long milestoneId = createMilestone("MVP demo");
        String path = "/api/v1/playgrounds/{id}/milestones/{milestoneId}";

        api.perform(contributor, patch(path, playgroundId, milestoneId), Map.of("status", "COMPLETED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("COMPLETED"))
                .andExpect(jsonPath("$.data.completedBy").value(contributor.id()))
                .andExpect(jsonPath("$.data.completedAt").exists())
                .andExpect(jsonPath("$.data.overdue").value(false));

        List<OutboxEvent> events = milestoneEvents(milestoneId);
        assertEquals(1, events.size());
        assertEquals("MILESTONE_REACHED", events.get(0).getEventType());
        Map<String, Object> payload = events.get(0).getPayload();
        assertEquals("MVP demo", payload.get("milestoneTitle"));
        assertEquals(contributor.id(), asLong(payload.get("completedById")));
        assertEquals(List.of(lead.id(), secondContributor.id(), viewer.id()).stream().sorted().toList(),
                asLongs(payload.get("recipientIds")).stream().sorted().toList());

        // Ticking it again changes nothing and emits nothing.
        api.perform(lead, patch(path, playgroundId, milestoneId), Map.of("status", "COMPLETED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.completedBy").value(contributor.id()));
        assertEquals(1, milestoneEvents(milestoneId).size());

        // Reopen, then reach it again: a new, genuine event.
        api.perform(lead, patch(path, playgroundId, milestoneId), Map.of("status", "PENDING"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PENDING"))
                .andExpect(jsonPath("$.data.completedAt").doesNotExist());
        api.perform(lead, patch(path, playgroundId, milestoneId), Map.of("status", "COMPLETED"))
                .andExpect(status().isOk());
        assertEquals(2, milestoneEvents(milestoneId).size());
    }

    @Test
    @DisplayName("Milestone completion: VIEWER 403, unknown milestone 404, missing status 400")
    void shouldGuardMilestoneCompletion() throws Exception {
        long milestoneId = createMilestone("Guarded");
        String path = "/api/v1/playgrounds/{id}/milestones/{milestoneId}";
        api.perform(viewer, patch(path, playgroundId, milestoneId), Map.of("status", "COMPLETED"))
                .andExpect(status().isForbidden());
        api.perform(lead, patch(path, playgroundId, Long.MAX_VALUE), Map.of("status", "COMPLETED"))
                .andExpect(status().isNotFound());
        api.perform(lead, patch(path, playgroundId, milestoneId), Map.of())
                .andExpect(status().isBadRequest());
        assertEquals(0, milestoneEvents(milestoneId).size());
    }

    private long createMilestone(String title) throws Exception {
        JsonNode created = api.json(api.perform(lead, post("/api/v1/playgrounds/{id}/milestones", playgroundId),
                        Map.of("title", title, "dueDate", Instant.now().plus(Duration.ofDays(7)).toString()))
                .andExpect(status().isCreated()));
        return created.at("/data/id").asLong();
    }

    private void update(TestUser user, long taskId, Map<String, Object> body) throws Exception {
        api.perform(user, patch("/api/v1/playgrounds/{id}/tasks/{taskId}", playgroundId, taskId), body)
                .andExpect(status().isOk());
    }

    private List<OutboxEvent> taskEvents(long taskId) {
        return outboxRepository.findByAggregateTypeAndAggregateIdOrderByOccurredAtAsc(
                PlaygroundOutboxEmitter.TASK_AGGREGATE, String.valueOf(taskId));
    }

    private List<OutboxEvent> milestoneEvents(long milestoneId) {
        return outboxRepository.findByAggregateTypeAndAggregateIdOrderByOccurredAtAsc(
                PlaygroundOutboxEmitter.MILESTONE_AGGREGATE, String.valueOf(milestoneId));
    }

    private static Long asLong(Object value) {
        return ((Number) value).longValue();
    }

    private static List<Long> asLongs(Object value) {
        return ((List<?>) value).stream().map(PlaygroundOutboxIntegrationTest::asLong).toList();
    }
}
