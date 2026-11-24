package com.kuetcord.playgrounds.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kuetcord.auth.ratelimit.RateLimitingFilter;
import com.kuetcord.opportunities.dto.CreateOpportunityRequest;
import com.kuetcord.opportunities.model.OpportunityCategory;
import com.kuetcord.opportunities.model.OpportunityMode;
import com.kuetcord.opportunities.service.OpportunityService;
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

import java.time.Duration;
import java.time.Instant;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Contest -> team formation: {@code POST/GET /api/v1/opportunities/{id}/playgrounds}. */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@Import(MessagingTestUsers.class)
class OpportunityPlaygroundControllerIntegrationTest {

    private static final String PATH = "/api/v1/opportunities/{id}/playgrounds";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private MessagingTestUsers users;

    @Autowired
    private RateLimitingFilter rateLimitingFilter;

    @Autowired
    private OpportunityService opportunityService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private PlaygroundApi api;
    private TestUser student;
    private TestUser teammate;
    private TestUser outsider;
    private long opportunityId;

    @BeforeEach
    void setUp() {
        rateLimitingFilter.clearBuckets();
        api = new PlaygroundApi(mockMvc, objectMapper);
        student = users.verifiedStudent("Nusrat Jahan");
        teammate = users.verifiedStudent("Arif Hossain");
        outsider = users.verifiedStudent("Tanvir Ahmed");
        Instant deadline = Instant.now().plus(Duration.ofDays(10));
        opportunityId = opportunityService.submit(student.id(), new CreateOpportunityRequest(
                "KUET Hackathon 2026", OpportunityCategory.HACKATHON, "KUET Computer Club",
                "https://hackathon.kuet.ac.bd", deadline, deadline.plus(Duration.ofDays(5)),
                deadline.plus(Duration.ofDays(6)), OpportunityMode.ON_SITE, null, 3, 5,
                "48-hour build sprint addressing campus sustainability.")).id();
    }

    @Test
    @DisplayName("One click ({}): a PUBLIC '<contest> Team' playground is created, the caller is LEAD, and it shows the contest")
    void shouldCreateTeamPlaygroundInOneClick() throws Exception {
        long playgroundId = api.json(api.perform(student, post(PATH, opportunityId), Map.of())
                        .andExpect(status().isCreated())
                        .andExpect(jsonPath("$.data.opportunityTitle").value("KUET Hackathon 2026"))
                        .andExpect(jsonPath("$.data.playgroundCreated").value(true))
                        .andExpect(jsonPath("$.data.newlyLinked").value(true))
                        .andExpect(jsonPath("$.data.playground.title").value("KUET Hackathon 2026 Team"))
                        .andExpect(jsonPath("$.data.playground.problemStatement")
                                .value("48-hour build sprint addressing campus sustainability."))
                        .andExpect(jsonPath("$.data.playground.visibility").value("PUBLIC"))
                        .andExpect(jsonPath("$.data.playground.tags[0]").value("HACKATHON"))
                        .andExpect(jsonPath("$.data.playground.myRole").value("LEAD"))
                        .andExpect(jsonPath("$.data.playground.linkedOpportunities[0].id").value(opportunityId))
                        .andExpect(jsonPath("$.data.playground.linkedOpportunities[0].category").value("HACKATHON")))
                .at("/data/playground/id").asLong();

        api.perform(outsider, get(PATH, opportunityId), null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].id").value(playgroundId))
                .andExpect(jsonPath("$.data[0].memberCount").value(1));
    }

    @Test
    @DisplayName("Linking an existing playground: LEAD only, idempotent (201 then 200), hidden from outsiders when PRIVATE")
    void shouldLinkExistingPlayground() throws Exception {
        long playgroundId = api.createPlayground(student, "Secret Robotics Entry", "PRIVATE");
        api.addMember(student, playgroundId, teammate, "CONTRIBUTOR");

        api.perform(teammate, post(PATH, opportunityId), Map.of("playgroundId", playgroundId))
                .andExpect(status().isForbidden());
        api.perform(student, post(PATH, opportunityId), Map.of("playgroundId", playgroundId))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.playgroundCreated").value(false))
                .andExpect(jsonPath("$.data.newlyLinked").value(true));
        api.perform(student, post(PATH, opportunityId), Map.of("playgroundId", playgroundId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.newlyLinked").value(false));

        api.perform(outsider, get(PATH, opportunityId), null)
                .andExpect(jsonPath("$.data.length()").value(0));
        api.perform(teammate, get(PATH, opportunityId), null)
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].memberCount").value(2));

        Long linkedBy = jdbcTemplate.queryForObject(
                "SELECT linked_by FROM opportunity_playgrounds WHERE opportunity_id = ? AND playground_id = ?",
                Long.class, opportunityId, playgroundId);
        assertEquals(student.id(), linkedBy);
    }

    @Test
    @DisplayName("Custom new playground, plus 400 / 404 cases")
    void shouldValidateLinkRequests() throws Exception {
        api.perform(student, post(PATH, opportunityId),
                        Map.of("newPlayground", Map.of("title", "Team Sustainify", "visibility", "PRIVATE")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.playground.title").value("Team Sustainify"))
                .andExpect(jsonPath("$.data.playground.visibility").value("PRIVATE"));

        api.perform(student, post(PATH, opportunityId),
                        Map.of("playgroundId", 1, "newPlayground", Map.of("title", "Both")))
                .andExpect(status().isBadRequest());
        api.perform(student, post(PATH, opportunityId), Map.of("newPlayground", Map.of("title", " ")))
                .andExpect(status().isBadRequest());
        api.perform(student, post(PATH, Long.MAX_VALUE), Map.of())
                .andExpect(status().isNotFound());
        api.perform(student, post(PATH, opportunityId), Map.of("playgroundId", Long.MAX_VALUE))
                .andExpect(status().isNotFound());
        api.perform(student, get(PATH, Long.MAX_VALUE), null)
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("The join table rejects a link to a playground that does not exist (FK)")
    void shouldEnforceForeignKeys() {
        assertThrows(DataIntegrityViolationException.class, () -> jdbcTemplate.update(
                "INSERT INTO opportunity_playgrounds (opportunity_id, playground_id) VALUES (?, ?)",
                opportunityId, Long.MAX_VALUE));
    }
}
