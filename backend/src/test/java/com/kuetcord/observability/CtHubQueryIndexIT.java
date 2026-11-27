package com.kuetcord.observability;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class CtHubQueryIndexIT {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("PostgreSQL schema defines required CT Hub, Year-Term, Teacher Approval, and Outbox indexes")
    void criticalDatabaseIndexesShouldExist() {
        List<String> indexes = jdbcTemplate.queryForList(
                "SELECT indexname FROM pg_indexes WHERE schemaname = 'public'",
                String.class
        );

        assertTrue(indexes.contains("idx_ct_course_schedule"), "Missing idx_ct_course_schedule");
        assertTrue(indexes.contains("idx_ct_assigned_teacher"), "Missing idx_ct_assigned_teacher");
        assertTrue(indexes.contains("idx_courses_dept_sem"), "Missing idx_courses_dept_sem");
        assertTrue(indexes.contains("idx_courses_dept_year_term"), "Missing idx_courses_dept_year_term");
        assertTrue(indexes.contains("idx_users_display_name_lower"), "Missing idx_users_display_name_lower");
        assertTrue(indexes.contains("idx_outbox_pending"), "Missing idx_outbox_pending");
    }

    @Test
    @DisplayName("PostgreSQL query planner utilizes composite indexes for CT Hub & Year-Term hot read queries")
    void postgresPlannerShouldUseCompositeIndexesOnCtQueries() {
        // Force planner to reveal index eligibility even on small CI test tables
        jdbcTemplate.execute("SET LOCAL enable_seqscan = off");

        String ctSchedulePlan = jdbcTemplate.queryForObject(
                "EXPLAIN (FORMAT JSON) SELECT id, title, scheduled_at FROM ct_events " +
                        "WHERE course_offering_id = 1 AND scheduled_at >= NOW() ORDER BY scheduled_at",
                String.class
        );
        assertTrue(ctSchedulePlan != null && ctSchedulePlan.contains("idx_ct_course_schedule"),
                "Expected idx_ct_course_schedule in EXPLAIN plan, got: " + ctSchedulePlan);

        String teacherApprovalPlan = jdbcTemplate.queryForObject(
                "EXPLAIN (FORMAT JSON) SELECT id, title FROM ct_events " +
                        "WHERE assigned_teacher_id = 1 AND status = 'UNVERIFIED'",
                String.class
        );
        assertTrue(teacherApprovalPlan != null && teacherApprovalPlan.contains("idx_ct_assigned_teacher"),
                "Expected idx_ct_assigned_teacher in EXPLAIN plan, got: " + teacherApprovalPlan);

        String yearTermPlan = jdbcTemplate.queryForObject(
                "EXPLAIN (FORMAT JSON) SELECT id, course_code FROM course_offerings " +
                        "WHERE department = 'CSE' AND year_term = '3-1'",
                String.class
        );
        assertTrue(yearTermPlan != null && yearTermPlan.contains("idx_courses_dept_year_term"),
                "Expected idx_courses_dept_year_term in EXPLAIN plan, got: " + yearTermPlan);

        String courseFilterPlan = jdbcTemplate.queryForObject(
                "EXPLAIN (FORMAT JSON) SELECT id, course_code FROM course_offerings " +
                        "WHERE department = 'CSE' AND semester = '5th'",
                String.class
        );
        assertTrue(courseFilterPlan != null &&
                        (courseFilterPlan.contains("idx_courses_dept_sem") || courseFilterPlan.contains("idx_courses_dept_year_term")),
                "Expected composite course index in EXPLAIN plan, got: " + courseFilterPlan);
    }

    @Test
    @DisplayName("Actuator /health and /prometheus endpoints return 200 UP with Micrometer JVM metrics")
    void actuatorHealthAndPrometheusEndpointsShouldBeLive() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));

        mockMvc.perform(get("/actuator/prometheus"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("jvm_memory_used_bytes")));
    }
}
