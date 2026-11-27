package com.kuetcord.studycircles.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kuetcord.auth.ratelimit.RateLimitingFilter;
import com.kuetcord.cthub.repository.CourseOfferingRepository;
import com.kuetcord.studycircles.support.StudyCircleApi;
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

import java.util.Map;
import java.util.UUID;

import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Shared revision checklist on a study circle, against PostgreSQL 16 + Flyway (V15). */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@Import(MessagingTestUsers.class)
class RevisionItemControllerIntegrationTest {

    private static final String ITEMS = "/api/v1/study-circles/{circleId}/revision-items";
    private static final String ITEM = ITEMS + "/{itemId}";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private MessagingTestUsers users;

    @Autowired
    private RateLimitingFilter rateLimitingFilter;

    @Autowired
    private CourseOfferingRepository courseRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private StudyCircleApi api;
    private TestUser host;
    private TestUser classmate;
    private TestUser outsider;
    private long circleId;

    @BeforeEach
    void setUp() throws Exception {
        rateLimitingFilter.clearBuckets();
        api = new StudyCircleApi(mockMvc, objectMapper, courseRepository);
        host = users.verifiedStudent("Nusrat Jahan");
        classmate = users.verifiedStudent("Arif Hossain");
        outsider = users.verifiedStudent("Tanvir Ahmed");
        circleId = api.createCircle(host, "Graph Theory Sprint " + UUID.randomUUID().toString().substring(0, 6), null);
        api.join(classmate, circleId);
    }

    private long addItem(TestUser user, String title) throws Exception {
        return api.json(api.perform(user, post(ITEMS, circleId), Map.of("title", title))
                .andExpect(status().isCreated())).at("/data/id").asLong();
    }

    @Test
    @DisplayName("Members add items; the list is oldest first with the author's name, titles trimmed")
    void shouldListItemsOldestFirst() throws Exception {
        api.perform(host, post(ITEMS, circleId), Map.of("title", "  BFS and DFS traversal  "))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.title").value("BFS and DFS traversal"))
                .andExpect(jsonPath("$.data.done").value(false))
                .andExpect(jsonPath("$.data.createdBy.displayName").value("Nusrat Jahan"));
        addItem(classmate, "Dijkstra with a binary heap");

        api.perform(classmate, get(ITEMS, circleId), null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].title").value("BFS and DFS traversal"))
                .andExpect(jsonPath("$.data[1].createdBy.displayName").value("Arif Hossain"));
    }

    @Test
    @DisplayName("Ticking records who ticked it; ticking twice keeps the first tick; unticking clears it")
    void shouldTickAndUntick() throws Exception {
        long itemId = addItem(host, "Topological sort");

        api.perform(classmate, patch(ITEM, circleId, itemId), Map.of("done", true))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.done").value(true))
                .andExpect(jsonPath("$.data.completedBy.displayName").value("Arif Hossain"))
                .andExpect(jsonPath("$.data.completedAt").isNotEmpty());

        api.perform(host, patch(ITEM, circleId, itemId), Map.of("done", true))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.completedBy.displayName").value("Arif Hossain"));

        api.perform(host, patch(ITEM, circleId, itemId), Map.of("done", false))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.done").value(false))
                .andExpect(jsonPath("$.data.completedBy").value(nullValue()))
                .andExpect(jsonPath("$.data.completedAt").value(nullValue()));
    }

    @Test
    @DisplayName("IDOR: outsiders get 403, and an item id from another circle is 404 through this one")
    void shouldGuardMembershipAndCircleScope() throws Exception {
        long itemId = addItem(host, "Minimum spanning trees");
        long otherCircle = api.createCircle(outsider, "Other circle " + UUID.randomUUID().toString().substring(0, 6), null);

        api.perform(outsider, get(ITEMS, circleId), null).andExpect(status().isForbidden());
        api.perform(outsider, post(ITEMS, circleId), Map.of("title", "Intruder")).andExpect(status().isForbidden());
        api.perform(outsider, patch(ITEM, circleId, itemId), Map.of("done", true)).andExpect(status().isForbidden());
        api.perform(outsider, patch(ITEM, otherCircle, itemId), Map.of("done", true)).andExpect(status().isNotFound());
        api.perform(host, get(ITEMS, 9_999_999L), null).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Validation: blank or over-long titles and a missing done flag are 400")
    void shouldRejectInvalidInput() throws Exception {
        long itemId = addItem(host, "Floyd-Warshall");

        api.perform(host, post(ITEMS, circleId), Map.of("title", "   ")).andExpect(status().isBadRequest());
        api.perform(host, post(ITEMS, circleId), Map.of("title", "x".repeat(201))).andExpect(status().isBadRequest());
        api.perform(host, patch(ITEM, circleId, itemId), Map.of()).andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Data integrity: the database rejects a ticked row with no completion time")
    void shouldEnforceCompletionConsistencyInDatabase() {
        assertThrows(DataIntegrityViolationException.class, () -> jdbcTemplate.update(
                "INSERT INTO study_revision_items (study_circle_id, title, done) VALUES (?, 'Inconsistent', TRUE)",
                circleId));
    }
}
