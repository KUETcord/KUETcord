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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Project milestones (timeline) and the architectural decision log. */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@Import(MessagingTestUsers.class)
class ProjectJournalControllerIntegrationTest {

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
        playgroundId = api.createPlayground(lead, "Robotics Club Rover", "PRIVATE");
        api.addMember(lead, playgroundId, contributor, "CONTRIBUTOR");
        api.addMember(lead, playgroundId, viewer, "VIEWER");
    }

    @Test
    @DisplayName("Milestones: listed soonest first; a past PENDING milestone is flagged overdue")
    void shouldListMilestonesOnTimeline() throws Exception {
        Instant now = Instant.now();
        String path = "/api/v1/playgrounds/{id}/milestones";
        api.perform(lead, post(path, playgroundId),
                        Map.of("title", "Final demo", "dueDate", now.plus(Duration.ofDays(14)).toString()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("PENDING"))
                .andExpect(jsonPath("$.data.overdue").value(false))
                .andExpect(jsonPath("$.data.createdBy").value(lead.id()));
        api.perform(contributor, post(path, playgroundId),
                        Map.of("title", "Proposal submitted", "description", "Abstract + team list",
                                "dueDate", now.minus(Duration.ofDays(1)).toString()))
                .andExpect(status().isCreated());
        api.perform(lead, post(path, playgroundId),
                        Map.of("title", "MVP", "dueDate", now.plus(Duration.ofDays(5)).toString()))
                .andExpect(status().isCreated());

        api.perform(viewer, get(path, playgroundId), null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(3))
                .andExpect(jsonPath("$.data[0].title").value("Proposal submitted"))
                .andExpect(jsonPath("$.data[0].overdue").value(true))
                .andExpect(jsonPath("$.data[1].title").value("MVP"))
                .andExpect(jsonPath("$.data[2].title").value("Final demo"));
    }

    @Test
    @DisplayName("Milestones: a VIEWER cannot add one and dueDate is required")
    void shouldGuardMilestoneCreation() throws Exception {
        String path = "/api/v1/playgrounds/{id}/milestones";
        api.perform(viewer, post(path, playgroundId),
                        Map.of("title", "Sneaky", "dueDate", Instant.now().plus(Duration.ofDays(3)).toString()))
                .andExpect(status().isForbidden());
        api.perform(lead, post(path, playgroundId), Map.of("title", "No date"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Decision log: newest entry first with the author's name; a VIEWER can read it but not write")
    void shouldRecordDecisionsNewestFirst() throws Exception {
        String path = "/api/v1/playgrounds/{id}/decisions";
        api.perform(lead, post(path, playgroundId),
                        Map.of("decision", "Use PostgreSQL 16", "rationale", "JSONB + strong constraints"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.author.id").value(lead.id()))
                .andExpect(jsonPath("$.data.author.displayName").value("Nusrat Jahan"));
        api.perform(contributor, post(path, playgroundId), Map.of("decision", "Adopt @dnd-kit for drag and drop"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.rationale").doesNotExist());
        api.perform(viewer, post(path, playgroundId), Map.of("decision", "Rewrite in Rust"))
                .andExpect(status().isForbidden());
        api.perform(lead, post(path, playgroundId), Map.of("decision", " "))
                .andExpect(status().isBadRequest());

        api.perform(viewer, get(path, playgroundId), null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].decision").value("Adopt @dnd-kit for drag and drop"))
                .andExpect(jsonPath("$.data[0].author.displayName").value("Arif Hossain"))
                .andExpect(jsonPath("$.data[1].decision").value("Use PostgreSQL 16"));
    }
}
