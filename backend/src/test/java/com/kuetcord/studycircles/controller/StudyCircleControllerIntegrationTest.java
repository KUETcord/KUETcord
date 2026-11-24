package com.kuetcord.studycircles.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kuetcord.auth.ratelimit.RateLimitingFilter;
import com.kuetcord.cthub.model.CourseOffering;
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

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Study circle directory, creation, circle page and joining against PostgreSQL 16 + Flyway (V1-V12). */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@Import(MessagingTestUsers.class)
class StudyCircleControllerIntegrationTest {

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
    private String tag;

    @BeforeEach
    void setUp() {
        rateLimitingFilter.clearBuckets();
        api = new StudyCircleApi(mockMvc, objectMapper, courseRepository);
        host = users.verifiedStudent("Nusrat Jahan");
        classmate = users.verifiedStudent("Arif Hossain");
        tag = UUID.randomUUID().toString().substring(0, 8);
    }

    @Test
    @DisplayName("Create: the creator becomes HOST and the circle is linked to the catalog course")
    void shouldCreateCircleLinkedToCourse() throws Exception {
        CourseOffering course = api.course("Algorithms");

        api.perform(host, post("/api/v1/study-circles"),
                        Map.of("name", "  DP Sprint " + tag + "  ", "courseOfferingId", course.getId()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.name").value("DP Sprint " + tag))
                .andExpect(jsonPath("$.data.course.id").value(course.getId()))
                .andExpect(jsonPath("$.data.course.courseCode").value(course.getCourseCode()))
                .andExpect(jsonPath("$.data.course.courseTitle").value("Algorithms"))
                .andExpect(jsonPath("$.data.creator.displayName").value("Nusrat Jahan"))
                .andExpect(jsonPath("$.data.myRole").value("HOST"))
                .andExpect(jsonPath("$.data.members.length()").value(1))
                .andExpect(jsonPath("$.data.members[0].role").value("HOST"))
                .andExpect(jsonPath("$.data.resourceCount").value(0))
                .andExpect(jsonPath("$.data.nextSession").doesNotExist());

        api.perform(host, post("/api/v1/study-circles"), Map.of("name", "Ghost course", "courseOfferingId", Long.MAX_VALUE))
                .andExpect(status().isNotFound());
        api.perform(host, post("/api/v1/study-circles"), Map.of("name", " "))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Join is idempotent: 201 then 200, one row, and a HOST who 'joins' stays HOST")
    void shouldJoinIdempotently() throws Exception {
        long circleId = api.createCircle(host, "Graph Theory " + tag, null);

        api.perform(classmate, post("/api/v1/study-circles/{id}/join", circleId), null)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.role").value("ATTENDEE"))
                .andExpect(jsonPath("$.data.memberCount").value(2))
                .andExpect(jsonPath("$.data.newlyJoined").value(true));
        api.perform(classmate, post("/api/v1/study-circles/{id}/join", circleId), null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.memberCount").value(2))
                .andExpect(jsonPath("$.data.newlyJoined").value(false));
        api.perform(host, post("/api/v1/study-circles/{id}/join", circleId), null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.role").value("HOST"));
        api.perform(classmate, post("/api/v1/study-circles/{id}/join", Long.MAX_VALUE), null)
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Directory: filter by course, name search and 'mine', with member count, my role and next session")
    void shouldBrowseWithFilters() throws Exception {
        CourseOffering course = api.course("Database Systems");
        long dbCircle = api.createCircle(host, "SQL Drills " + tag, course.getId());
        long osCircle = api.createCircle(classmate, "OS Scheduling " + tag, null);
        api.join(classmate, dbCircle);
        api.perform(host, post("/api/v1/study-circles/{id}/sessions", dbCircle),
                        Map.of("title", "Normal forms", "scheduledAt", Instant.now().plus(Duration.ofDays(2)).toString()))
                .andExpect(status().isCreated());

        api.perform(classmate, get("/api/v1/study-circles").param("courseOfferingId", String.valueOf(course.getId())), null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalItems").value(1))
                .andExpect(jsonPath("$.data.items[0].id").value(dbCircle))
                .andExpect(jsonPath("$.data.items[0].course.courseTitle").value("Database Systems"))
                .andExpect(jsonPath("$.data.items[0].memberCount").value(2))
                .andExpect(jsonPath("$.data.items[0].myRole").value("ATTENDEE"))
                .andExpect(jsonPath("$.data.items[0].nextSessionAt").exists());

        // Case-insensitive name search; the host never joined this circle, so myRole is absent.
        api.perform(host, get("/api/v1/study-circles").param("q", "os SCHEDULING " + tag), null)
                .andExpect(jsonPath("$.data.totalItems").value(1))
                .andExpect(jsonPath("$.data.items[0].id").value(osCircle))
                .andExpect(jsonPath("$.data.items[0].memberCount").value(1))
                .andExpect(jsonPath("$.data.items[0].myRole").doesNotExist())
                .andExpect(jsonPath("$.data.items[0].nextSessionAt").doesNotExist());

        api.perform(host, get("/api/v1/study-circles").param("mine", "true").param("q", tag), null)
                .andExpect(jsonPath("$.data.totalItems").value(1))
                .andExpect(jsonPath("$.data.items[0].id").value(dbCircle))
                .andExpect(jsonPath("$.data.items[0].myRole").value("HOST"));

        api.perform(host, get("/api/v1/study-circles").param("q", "100%_" + tag), null)
                .andExpect(jsonPath("$.data.totalItems").value(0));
    }

    @Test
    @DisplayName("Circle page: outsiders see members but not the next session's meeting details")
    void shouldHideNextSessionFromOutsiders() throws Exception {
        long circleId = api.createCircle(host, "Compiler Design " + tag, null);
        api.perform(host, post("/api/v1/study-circles/{id}/sessions", circleId),
                        Map.of("title", "LR parsing", "scheduledAt", Instant.now().plus(Duration.ofDays(1)).toString(),
                                "locationOrLink", "Library Room 2"))
                .andExpect(status().isCreated());

        api.perform(classmate, get("/api/v1/study-circles/{id}", circleId), null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.myRole").doesNotExist())
                .andExpect(jsonPath("$.data.members.length()").value(1))
                .andExpect(jsonPath("$.data.nextSession").doesNotExist());
        api.perform(host, get("/api/v1/study-circles/{id}", circleId), null)
                .andExpect(jsonPath("$.data.nextSession.title").value("LR parsing"))
                .andExpect(jsonPath("$.data.nextSession.locationOrLink").value("Library Room 2"));
        api.perform(host, get("/api/v1/study-circles/{id}", Long.MAX_VALUE), null)
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("V12 CHECK constraint rejects a member role other than HOST / ATTENDEE")
    void shouldEnforceMemberRoleConstraint() throws Exception {
        long circleId = api.createCircle(host, "Constraint Probe " + tag, null);
        assertThrows(DataIntegrityViolationException.class, () -> jdbcTemplate.update(
                "UPDATE study_circle_members SET role = 'OWNER' WHERE study_circle_id = ?", circleId));
    }
}
