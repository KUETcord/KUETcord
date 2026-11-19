package com.kuetcord.opportunities.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kuetcord.auth.model.RoleName;
import com.kuetcord.auth.model.User;
import com.kuetcord.auth.ratelimit.RateLimitingFilter;
import com.kuetcord.auth.repository.RoleRepository;
import com.kuetcord.auth.repository.UserRepository;
import com.kuetcord.auth.service.JwtTokenService;
import com.kuetcord.opportunities.repository.OpportunityBookmarkRepository;
import com.kuetcord.support.MessagingTestUsers;
import com.kuetcord.support.MessagingTestUsers.TestUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import jakarta.persistence.EntityManager;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Contest &amp; Challenge Board against PostgreSQL 16 + Flyway (V1-V6) through the real JWT security chain.
 * <p>
 * Every listing created here carries a per-test organizer tag so assertions can filter with {@code q=}
 * and stay independent of rows left behind by other test classes.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@Import(MessagingTestUsers.class)
class OpportunityControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private MessagingTestUsers users;

    @Autowired
    private RateLimitingFilter rateLimitingFilter;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private JwtTokenService jwtTokenService;

    @Autowired
    private OpportunityBookmarkRepository bookmarkRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private EntityManager entityManager;

    private TestUser student;
    private TestUser teammate;
    private String tag;

    @BeforeEach
    void setUp() {
        rateLimitingFilter.clearBuckets();
        student = users.verifiedStudent("Nusrat Jahan");
        teammate = users.verifiedStudent("Arif Hossain");
        tag = "org-" + UUID.randomUUID().toString().substring(0, 8);
    }

    @Test
    @DisplayName("Submit: student listing is stored UNVERIFIED with the submitter attached")
    void shouldSubmitOpportunityAsUnverified() throws Exception {
        perform(student, post("/api/v1/opportunities"), payload("KUET Hackathon 2026", Duration.ofDays(10)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("UNVERIFIED"))
                .andExpect(jsonPath("$.data.category").value("HACKATHON"))
                .andExpect(jsonPath("$.data.mode").value("ON_SITE"))
                .andExpect(jsonPath("$.data.teamSizeMin").value(3))
                .andExpect(jsonPath("$.data.submittedBy.id").value(student.id()))
                .andExpect(jsonPath("$.data.submittedBy.displayName").value("Nusrat Jahan"))
                .andExpect(jsonPath("$.data.bookmarked").value(false))
                .andExpect(jsonPath("$.data.expired").value(false));
    }

    @Test
    @DisplayName("Submit: invalid payloads, unknown enums and anonymous callers are rejected")
    void shouldRejectInvalidSubmissions() throws Exception {
        Map<String, Object> badWindow = payload("Backwards event", Duration.ofDays(5));
        badWindow.put("endDate", Instant.now().plus(Duration.ofDays(1)).toString());
        perform(student, post("/api/v1/opportunities"), badWindow)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.validationErrors.eventWindowValid").exists());

        Map<String, Object> badCategory = payload("CT 2 Algorithms", Duration.ofDays(5));
        badCategory.put("category", "CLASS_TEST");
        perform(student, post("/api/v1/opportunities"), badCategory)
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/v1/opportunities")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload("Anonymous", Duration.ofDays(5)))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Strategy pattern: toggling deadline vs recent reorders the same listings")
    void shouldToggleRankingStrategy() throws Exception {
        long publishedFirst = create(student, "Robotics Olympiad", Duration.ofDays(20));
        long publishedSecond = create(student, "ICPC Preliminary", Duration.ofDays(3));
        long expired = create(student, "Closed Datathon", Duration.ofDays(4));
        backdate(publishedFirst, Duration.ofDays(3));
        backdate(publishedSecond, Duration.ofDays(2));
        expireDeadline(expired);

        assertEquals(List.of(publishedSecond, publishedFirst, expired), browseIds("deadline"));
        assertEquals(List.of(expired, publishedSecond, publishedFirst), browseIds("recent"));

        perform(student, get("/api/v1/opportunities").param("q", tag), null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.strategy").value("deadline"))
                .andExpect(jsonPath("$.data.items[2].expired").value(true));

        perform(student, get("/api/v1/opportunities").param("strategy", "popular"), null)
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Facets: category, mode, status and deadlineBefore narrow the result set")
    void shouldFilterByFacets() throws Exception {
        long hackathon = create(student, "Green Campus Hackathon", Duration.ofDays(10));
        Map<String, Object> contestPayload = payload("IUPC Online", Duration.ofDays(40));
        contestPayload.put("category", "PROGRAMMING_CONTEST");
        contestPayload.put("mode", "ONLINE");
        long contest = idOf(perform(student, post("/api/v1/opportunities"), contestPayload)
                .andExpect(status().isCreated()));

        assertEquals(List.of(hackathon), browseIds(Map.of("category", "HACKATHON")));
        assertEquals(List.of(contest), browseIds(Map.of("mode", "ONLINE")));
        assertEquals(List.of(hackathon),
                browseIds(Map.of("deadlineBefore", Instant.now().plus(Duration.ofDays(20)).toString())));
        assertEquals(List.of(), browseIds(Map.of("status", "VERIFIED")));

        perform(student, get("/api/v1/opportunities").param("category", "CLASS_TEST"), null)
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Bookmark: idempotent add/remove, per-user flag and bookmarkedOnly filter")
    void shouldPreventDuplicateOpportunityBookmarks() throws Exception {
        long saved = create(student, "SUST CSE Carnival", Duration.ofDays(6));
        long other = create(student, "BUET Robo Fest", Duration.ofDays(9));

        perform(student, post("/api/v1/opportunities/{id}/bookmark", saved), null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.bookmarked").value(true))
                .andExpect(jsonPath("$.data.bookmarkCount").value(1));
        perform(student, post("/api/v1/opportunities/{id}/bookmark", saved), null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.bookmarkCount").value(1));
        perform(teammate, post("/api/v1/opportunities/{id}/bookmark", saved), null)
                .andExpect(jsonPath("$.data.bookmarkCount").value(2));

        assertEquals(2, bookmarkRepository.countByOpportunityId(saved));
        assertEquals(List.of(saved), browseIds(Map.of("bookmarkedOnly", "true")));

        perform(student, get("/api/v1/opportunities/{id}", saved), null)
                .andExpect(jsonPath("$.data.bookmarked").value(true))
                .andExpect(jsonPath("$.data.bookmarkCount").value(2));
        perform(student, get("/api/v1/opportunities/{id}", other), null)
                .andExpect(jsonPath("$.data.bookmarked").value(false));

        perform(student, delete("/api/v1/opportunities/{id}/bookmark", saved), null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.bookmarked").value(false))
                .andExpect(jsonPath("$.data.bookmarkCount").value(1));
        perform(student, delete("/api/v1/opportunities/{id}/bookmark", saved), null)
                .andExpect(status().isOk());

        assertEquals(List.of(), browseIds(Map.of("bookmarkedOnly", "true")));
        perform(student, post("/api/v1/opportunities/{id}/bookmark", Long.MAX_VALUE), null)
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Verify: students get 403; moderators verify once and keep the original audit trail")
    void shouldOnlyLetModeratorsVerify() throws Exception {
        long listing = create(student, "NASA Space Apps Khulna", Duration.ofDays(12));

        perform(student, post("/api/v1/opportunities/{id}/verify", listing), null)
                .andExpect(status().isForbidden());

        TestUser moderator = withRole(users.verifiedStudent("Moderator Mitu"), RoleName.ROLE_MODERATOR);
        perform(moderator, post("/api/v1/opportunities/{id}/verify", listing), null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("VERIFIED"))
                .andExpect(jsonPath("$.data.verifiedBy").value(moderator.id()))
                .andExpect(jsonPath("$.data.verifiedAt").exists());

        TestUser admin = withRole(users.verifiedStudent("Admin Anik"), RoleName.ROLE_ADMIN);
        perform(admin, post("/api/v1/opportunities/{id}/verify", listing), null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.verifiedBy").value(moderator.id()));

        assertEquals(List.of(listing), browseIds(Map.of("status", "VERIFIED")));
        perform(moderator, post("/api/v1/opportunities/{id}/verify", Long.MAX_VALUE), null)
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Detail and bookmark routes answer 404 for unknown ids")
    void shouldReturnNotFoundForUnknownOpportunity() throws Exception {
        perform(student, get("/api/v1/opportunities/{id}", Long.MAX_VALUE), null)
                .andExpect(status().isNotFound());
        perform(student, delete("/api/v1/opportunities/{id}/bookmark", Long.MAX_VALUE), null)
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Schema: V6 CHECK constraints reject rows that bypass DTO validation")
    void shouldEnforceSchemaConstraints() {
        long id = create(student, "Constraint Probe", Duration.ofDays(5));
        assertThrows(DataIntegrityViolationException.class, () -> jdbcTemplate.update(
                "UPDATE opportunities SET team_size_min = 6, team_size_max = 2 WHERE id = ?", id));
    }

    // ----------------------------------------------------------------- helpers

    private Map<String, Object> payload(String title, Duration deadlineFromNow) {
        Instant deadline = Instant.now().plus(deadlineFromNow);
        Map<String, Object> body = new HashMap<>();
        body.put("title", title);
        body.put("category", "HACKATHON");
        body.put("organizer", tag);
        body.put("sourceUrl", "https://hackathon.kuet.ac.bd");
        body.put("registrationDeadline", deadline.toString());
        body.put("startDate", deadline.plus(Duration.ofDays(7)).toString());
        body.put("endDate", deadline.plus(Duration.ofDays(8)).toString());
        body.put("mode", "ON_SITE");
        body.put("eligibility", "Undergraduate students, all departments");
        body.put("teamSizeMin", 3);
        body.put("teamSizeMax", 5);
        body.put("description", "48-hour build sprint addressing campus sustainability.");
        return body;
    }

    private long create(TestUser user, String title, Duration deadlineFromNow) {
        try {
            return idOf(perform(user, post("/api/v1/opportunities"), payload(title, deadlineFromNow))
                    .andExpect(status().isCreated()));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private List<Long> browseIds(String strategy) throws Exception {
        return browseIds(Map.of("strategy", strategy));
    }

    private List<Long> browseIds(Map<String, String> params) throws Exception {
        MockHttpServletRequestBuilder request = get("/api/v1/opportunities").param("q", tag);
        params.forEach(request::param);
        JsonNode items = json(perform(student, request, null).andExpect(status().isOk())).at("/data/items");
        List<Long> ids = new ArrayList<>();
        items.forEach(item -> ids.add(item.get("id").asLong()));
        return ids;
    }

    private void backdate(long opportunityId, Duration age) {
        jdbcTemplate.update("UPDATE opportunities SET created_at = ? WHERE id = ?",
                java.sql.Timestamp.from(Instant.now().minus(age)), opportunityId);
        evictPersistenceContext();
    }

    /** Moves the deadline into the past while keeping the event window consistent. */
    private void expireDeadline(long opportunityId) {
        Instant past = Instant.now().minus(Duration.ofDays(1));
        jdbcTemplate.update("""
                        UPDATE opportunities
                        SET registration_deadline = ?, event_start = ?, event_end = ?
                        WHERE id = ?""",
                java.sql.Timestamp.from(past), java.sql.Timestamp.from(past.plus(Duration.ofHours(1))),
                java.sql.Timestamp.from(past.plus(Duration.ofHours(2))), opportunityId);
        evictPersistenceContext();
    }

    /** The test transaction shares one persistence context; drop it so raw SQL edits are re-read. */
    private void evictPersistenceContext() {
        entityManager.flush();
        entityManager.clear();
    }

    private TestUser withRole(TestUser testUser, RoleName roleName) {
        User user = userRepository.findById(testUser.id()).orElseThrow();
        user.getRoles().add(roleRepository.findByName(roleName).orElseThrow());
        userRepository.save(user);
        return new TestUser(user.getId(), user.getDisplayName(), "Bearer " + jwtTokenService.generateToken(user));
    }

    private ResultActions perform(TestUser user, MockHttpServletRequestBuilder request, Object body) throws Exception {
        request.header(HttpHeaders.AUTHORIZATION, user.bearer());
        if (body != null) {
            request.contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(body));
        }
        return mockMvc.perform(request);
    }

    private long idOf(ResultActions result) throws Exception {
        return json(result).at("/data/id").asLong();
    }

    private JsonNode json(ResultActions result) throws Exception {
        return objectMapper.readTree(result.andReturn().getResponse().getContentAsString());
    }
}
