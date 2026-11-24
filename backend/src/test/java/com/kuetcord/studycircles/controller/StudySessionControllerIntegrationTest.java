package com.kuetcord.studycircles.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kuetcord.auth.ratelimit.RateLimitingFilter;
import com.kuetcord.common.outbox.OutboxEvent;
import com.kuetcord.common.outbox.OutboxEventRepository;
import com.kuetcord.cthub.model.CourseOffering;
import com.kuetcord.cthub.repository.CourseOfferingRepository;
import com.kuetcord.studycircles.event.StudyCircleOutboxEmitter;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Session scheduler (UTC, overlap guard, outbox event) and shared resources. */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@Import(MessagingTestUsers.class)
class StudySessionControllerIntegrationTest {

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
    private OutboxEventRepository outboxRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private StudyCircleApi api;
    private TestUser host;
    private TestUser attendee;
    private TestUser outsider;
    private CourseOffering course;
    private long circleId;
    private Instant tomorrow;

    @BeforeEach
    void setUp() throws Exception {
        rateLimitingFilter.clearBuckets();
        api = new StudyCircleApi(mockMvc, objectMapper, courseRepository);
        host = users.verifiedStudent("Nusrat Jahan");
        attendee = users.verifiedStudent("Arif Hossain");
        outsider = users.verifiedStudent("Tanvir Ahmed");
        course = api.course("Algorithm Engineering");
        circleId = api.createCircle(host, "DP Problem Sprint", course.getId());
        api.join(attendee, circleId);
        tomorrow = Instant.now().plus(Duration.ofDays(1)).truncatedTo(ChronoUnit.MINUTES);
    }

    @Test
    @DisplayName("Schedule: plan payload accepted, times are UTC, endsAt derived, STUDY_SESSION_SCHEDULED written for the others")
    void shouldScheduleSessionAndEmitEvent() throws Exception {
        long sessionId = api.json(api.perform(host, post(sessionsPath(), circleId), Map.of(
                        "title", "CSE 3103: Dynamic Programming Problem Sprint",
                        "scheduledAt", "2099-11-22T20:00:00+06:00",
                        "durationMinutes", 90,
                        "agenda", "Solve 0/1 Knapsack and Matrix Chain Multiplication",
                        "locationOrLink", "Library 2nd Floor Study Room / Jitsi"))
                        .andExpect(status().isCreated())
                        .andExpect(jsonPath("$.data.scheduledAt").value("2099-11-22T14:00:00Z"))
                        .andExpect(jsonPath("$.data.endsAt").value("2099-11-22T15:30:00Z"))
                        .andExpect(jsonPath("$.data.durationMinutes").value(90))
                        .andExpect(jsonPath("$.data.scheduledBy.displayName").value("Nusrat Jahan")))
                .at("/data/id").asLong();

        List<OutboxEvent> events = outboxRepository.findByAggregateTypeAndAggregateIdOrderByOccurredAtAsc(
                StudyCircleOutboxEmitter.SESSION_AGGREGATE, String.valueOf(sessionId));
        assertEquals(1, events.size());
        assertEquals("STUDY_SESSION_SCHEDULED", events.get(0).getEventType());
        Map<String, Object> payload = events.get(0).getPayload();
        assertEquals("DP Problem Sprint", payload.get("studyCircleName"));
        assertEquals(course.getCourseCode(), payload.get("courseCode"));
        assertEquals("2099-11-22T14:00:00Z", payload.get("scheduledAt"));
        assertEquals(List.of(attendee.id()),
                ((List<?>) payload.get("recipientIds")).stream().map(id -> ((Number) id).longValue()).toList());
    }

    @Test
    @DisplayName("Overlap guard: a clashing session is 409; one starting exactly when the previous ends is fine")
    void shouldRejectOverlappingSessions() throws Exception {
        schedule(host, "First", tomorrow, 60);

        api.perform(attendee, post(sessionsPath(), circleId), session("Clash", tomorrow.plus(Duration.ofMinutes(30)), 60))
                .andExpect(status().isConflict());
        api.perform(attendee, post(sessionsPath(), circleId), session("Wraps", tomorrow.minus(Duration.ofMinutes(30)), 120))
                .andExpect(status().isConflict());
        api.perform(attendee, post(sessionsPath(), circleId), session("Back to back", tomorrow.plus(Duration.ofMinutes(60)), 45))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("Validation: past start, too short / too long sessions and a missing time are 400; duration defaults to 60")
    void shouldValidateSessionRequests() throws Exception {
        api.perform(host, post(sessionsPath(), circleId), session("Past", Instant.now().minus(Duration.ofHours(1)), 60))
                .andExpect(status().isBadRequest());
        api.perform(host, post(sessionsPath(), circleId), session("Blink", tomorrow, 10))
                .andExpect(status().isBadRequest());
        api.perform(host, post(sessionsPath(), circleId), session("Marathon", tomorrow, 600))
                .andExpect(status().isBadRequest());
        api.perform(host, post(sessionsPath(), circleId), Map.of("title", "No time"))
                .andExpect(status().isBadRequest());
        api.perform(host, post(sessionsPath(), circleId), Map.of("title", "Default length", "scheduledAt", tomorrow.toString()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.durationMinutes").value(60));
    }

    @Test
    @DisplayName("Listing: upcoming by default (an in-progress session counts), full history with includePast; members only")
    void shouldListUpcomingSessionsForMembers() throws Exception {
        insertSession("Last week", Instant.now().minus(Duration.ofDays(7)), 60);
        insertSession("Happening now", Instant.now().minus(Duration.ofMinutes(30)), 90);
        schedule(host, "Tomorrow", tomorrow, 60);

        api.perform(attendee, get(sessionsPath(), circleId), null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].title").value("Happening now"))
                .andExpect(jsonPath("$.data[1].title").value("Tomorrow"));
        api.perform(attendee, get(sessionsPath(), circleId).param("includePast", "true"), null)
                .andExpect(jsonPath("$.data.length()").value(3))
                .andExpect(jsonPath("$.data[0].title").value("Last week"));

        api.perform(outsider, get(sessionsPath(), circleId), null).andExpect(status().isForbidden());
        api.perform(outsider, post(sessionsPath(), circleId), session("Intruder", tomorrow.plus(Duration.ofDays(3)), 60))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Resources: members share http(s) links, listed newest first with the contributor; outsiders are refused")
    void shouldShareResources() throws Exception {
        String path = "/api/v1/study-circles/{id}/resources";
        api.perform(host, post(path, circleId),
                        Map.of("title", "CLRS ch. 15 notes", "url", "https://notes.example.org/dp", "category", "NOTES"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.contributor.displayName").value("Nusrat Jahan"));
        api.perform(attendee, post(path, circleId),
                        Map.of("title", "Past CT questions", "url", "https://drive.example.org/qb", "category", "QUESTION_BANK"))
                .andExpect(status().isCreated());

        api.perform(attendee, get(path, circleId), null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].title").value("Past CT questions"))
                .andExpect(jsonPath("$.data[0].contributor.displayName").value("Arif Hossain"))
                .andExpect(jsonPath("$.data[1].category").value("NOTES"));
        api.perform(host, get("/api/v1/study-circles/{id}", circleId), null)
                .andExpect(jsonPath("$.data.resourceCount").value(2));

        api.perform(host, post(path, circleId), Map.of("title", "Bad", "url", "javascript:alert(1)", "category", "NOTES"))
                .andExpect(status().isBadRequest());
        api.perform(host, post(path, circleId), Map.of("title", "Bad", "url", "https://x.org", "category", "MEMES"))
                .andExpect(status().isBadRequest());
        api.perform(outsider, get(path, circleId), null).andExpect(status().isForbidden());
    }

    private String sessionsPath() {
        return "/api/v1/study-circles/{id}/sessions";
    }

    private Map<String, Object> session(String title, Instant start, int minutes) {
        Map<String, Object> body = new HashMap<>();
        body.put("title", title);
        body.put("scheduledAt", start.toString());
        body.put("durationMinutes", minutes);
        return body;
    }

    private void schedule(TestUser user, String title, Instant start, int minutes) throws Exception {
        api.perform(user, post(sessionsPath(), circleId), session(title, start, minutes)).andExpect(status().isCreated());
    }

    /** The API refuses past starts, so history is seeded directly. */
    private void insertSession(String title, Instant start, int minutes) {
        jdbcTemplate.update("INSERT INTO study_sessions (study_circle_id, title, scheduled_start, duration_minutes, created_by)"
                + " VALUES (?, ?, ?, ?, ?)", circleId, title, Timestamp.from(start), minutes, host.id());
    }
}
