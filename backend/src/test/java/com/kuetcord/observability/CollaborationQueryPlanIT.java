package com.kuetcord.observability;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kuetcord.auth.ratelimit.RateLimitingFilter;
import com.kuetcord.support.MessagingTestUsers;
import com.kuetcord.support.MessagingTestUsers.TestUser;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.PersistenceContext;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Member C, Week 5 Day 5: query performance evidence for the collaboration hot paths.
 *
 * <p>Seeds a realistic volume (100 playgrounds x 60 cards, 6,000 opportunities), refreshes planner statistics,
 * then runs {@code EXPLAIN (ANALYZE, FORMAT JSON)} on the SQL the repositories issue and asserts the planner
 * picks the intended index without disabling sequential scans. Execution times are printed with the
 * {@code [query-plan]} prefix so the CI log doubles as the performance record.
 *
 * <p>It also proves the Kanban board read has no N+1: the number of JDBC statements is the same for a
 * 3-card board and a 60-card board.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@Import(MessagingTestUsers.class)
class CollaborationQueryPlanIT {

    private static final int PLAYGROUNDS = 100;
    private static final int CARDS_PER_PLAYGROUND = 60;
    private static final int OPPORTUNITIES = 6_000;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private MessagingTestUsers users;

    @Autowired
    private RateLimitingFilter rateLimitingFilter;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    @PersistenceContext
    private EntityManager entityManager;

    private TestUser lead;
    private long firstPlaygroundId;

    @BeforeEach
    void seed() {
        rateLimitingFilter.clearBuckets();
        lead = users.verifiedStudent("Karim Rahman");

        firstPlaygroundId = jdbcTemplate.queryForObject("""
                WITH created AS (
                    INSERT INTO playgrounds (title, owner_id, status)
                    SELECT 'Load playground ' || g, ?, 'IN_PROGRESS' FROM generate_series(1, ?) g
                    RETURNING id
                )
                SELECT MIN(id) FROM created
                """, Long.class, lead.id(), PLAYGROUNDS);

        jdbcTemplate.update("""
                INSERT INTO playground_members (playground_id, user_id, role)
                SELECT id, ?, 'LEAD' FROM playgrounds WHERE id BETWEEN ? AND ?
                """, lead.id(), firstPlaygroundId, firstPlaygroundId + PLAYGROUNDS - 1);

        // Five cards per column position: card c sits in column c % 5 at position c / 5.
        jdbcTemplate.update("""
                INSERT INTO project_tasks (playground_id, title, status, priority, position, assignee_id, created_by)
                SELECT p.id, 'Card ' || c,
                       (ARRAY['BACKLOG', 'TODO', 'IN_PROGRESS', 'REVIEW', 'DONE'])[1 + c % 5],
                       (ARRAY['LOW', 'MEDIUM', 'HIGH', 'CRITICAL'])[1 + c % 4],
                       c / 5, ?, ?
                FROM playgrounds p CROSS JOIN generate_series(0, ? - 1) c
                WHERE p.id BETWEEN ? AND ?
                """, lead.id(), lead.id(), CARDS_PER_PLAYGROUND,
                firstPlaygroundId, firstPlaygroundId + PLAYGROUNDS - 1);

        jdbcTemplate.update("""
                INSERT INTO opportunities (title, category, organizer, source_url, registration_deadline,
                                           event_start, event_end, verification_status, submitted_by, created_at)
                SELECT 'Load opportunity ' || g,
                       (ARRAY['PROGRAMMING_CONTEST', 'HACKATHON', 'ROBOTICS', 'RESEARCH', 'CASE_COMPETITION'])[1 + g % 5],
                       'Organizer ' || (g % 40),
                       'https://example.org/' || g,
                       NOW() + ((g % 730) - 365) * INTERVAL '1 day',
                       NOW() + ((g % 730) - 360) * INTERVAL '1 day',
                       NOW() + ((g % 730) - 359) * INTERVAL '1 day',
                       CASE WHEN g % 2 = 0 THEN 'VERIFIED' ELSE 'UNVERIFIED' END,
                       ?, NOW() - g * INTERVAL '1 hour'
                FROM generate_series(1, ?) g
                """, lead.id(), OPPORTUNITIES);

        // Planner statistics for the freshly seeded rows (ANALYZE is allowed inside the test transaction).
        jdbcTemplate.execute("ANALYZE playgrounds, playground_members, project_tasks, opportunities");
    }

    @Test
    @DisplayName("Kanban board read, column counts and end-of-column insert all use idx_tasks_playground_status")
    void kanbanQueriesShouldUsePlaygroundStatusIndex() throws Exception {
        long pg = firstPlaygroundId + PLAYGROUNDS / 2;

        assertPlanUses("kanban board cards", "idx_tasks_playground_status", """
                SELECT t.id, t.title, t.status, t.priority, t.position, t.due_date, u.id, u.display_name, t.version
                FROM project_tasks t LEFT JOIN users u ON u.id = t.assignee_id
                WHERE t.playground_id = %d
                ORDER BY t.position, t.id
                """.formatted(pg));

        assertPlanUses("kanban column counts", "idx_tasks_playground_status", """
                SELECT t.status, COUNT(t.id) FROM project_tasks t WHERE t.playground_id = %d GROUP BY t.status
                """.formatted(pg));

        assertPlanUses("kanban end-of-column position", "idx_tasks_playground_status", """
                SELECT COALESCE(MAX(t.position), -1) FROM project_tasks t
                WHERE t.playground_id = %d AND t.status = 'REVIEW'
                """.formatted(pg));
    }

    @Test
    @DisplayName("Contest Board: deadline window, category filter and the newest-first candidate scan are index-backed")
    void opportunityQueriesShouldUseDeadlineAndCandidateIndexes() throws Exception {
        assertPlanUses("closing within 3 days", "idx_opp_deadline", """
                SELECT o.id, o.title, o.registration_deadline FROM opportunities o
                WHERE o.registration_deadline >= NOW() AND o.registration_deadline < NOW() + INTERVAL '3 days'
                ORDER BY o.registration_deadline
                """);

        assertPlanUses("verified hackathons, nearest deadline", "idx_opp_cat_status_deadline", """
                SELECT o.id, o.title, o.registration_deadline FROM opportunities o
                WHERE o.category = 'HACKATHON' AND o.verification_status = 'VERIFIED'
                  AND o.registration_deadline >= NOW()
                ORDER BY o.registration_deadline
                LIMIT 20
                """);

        assertPlanUses("ranking candidate window (newest 200)", "idx_opp_created_at", """
                SELECT o.id, o.title, o.registration_deadline, o.created_at FROM opportunities o
                ORDER BY o.created_at DESC, o.id DESC
                LIMIT 200
                """);
    }

    @Test
    @DisplayName("No N+1: GET /playgrounds/{id}/tasks issues the same number of statements for 3 cards and 60 cards")
    void boardReadShouldIssueConstantStatementCount() throws Exception {
        long fullBoard = firstPlaygroundId;
        long smallBoard = firstPlaygroundId + 1;
        jdbcTemplate.update("DELETE FROM project_tasks WHERE playground_id = ? AND position > 0", smallBoard);

        long smallCount = statementsForBoard(smallBoard, 5);
        long fullCount = statementsForBoard(fullBoard, CARDS_PER_PLAYGROUND);

        System.out.printf("[query-plan] board statements: %d cards -> %d, %d cards -> %d%n",
                5, smallCount, CARDS_PER_PLAYGROUND, fullCount);
        assertTrue(smallCount > 0, "Hibernate statistics recorded no statements; the measurement is not working");
        assertEquals(smallCount, fullCount,
                "Board read must not issue a query per card (N+1): " + smallCount + " vs " + fullCount);
    }

    private long statementsForBoard(long playgroundId, int expectedCards) throws Exception {
        Statistics stats = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        entityManager.flush();
        entityManager.clear();
        boolean wasEnabled = stats.isStatisticsEnabled();
        stats.setStatisticsEnabled(true);
        stats.clear();
        try {
            mockMvc.perform(get("/api/v1/playgrounds/{id}/tasks", playgroundId)
                            .header(HttpHeaders.AUTHORIZATION, lead.bearer()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.totalTasks").value(expectedCards));
            return stats.getPrepareStatementCount();
        } finally {
            stats.setStatisticsEnabled(wasEnabled);
        }
    }

    private void assertPlanUses(String label, String indexName, String sql) throws Exception {
        String json = jdbcTemplate.queryForObject("EXPLAIN (ANALYZE, BUFFERS, FORMAT JSON) " + sql, String.class);
        JsonNode root = objectMapper.readTree(json).get(0);
        List<String> indexes = new ArrayList<>();
        collectIndexNames(root.get("Plan"), indexes);

        System.out.printf("[query-plan] %-40s index=%s nodes=%s execution=%.3f ms%n",
                label, indexes, root.at("/Plan/Node Type").asText(), root.get("Execution Time").asDouble());
        assertTrue(indexes.contains(indexName),
                "Expected " + indexName + " in the plan for '" + label + "', got " + indexes + ":\n" + json);
    }

    private static void collectIndexNames(JsonNode plan, List<String> out) {
        if (plan == null) {
            return;
        }
        if (plan.hasNonNull("Index Name")) {
            out.add(plan.get("Index Name").asText());
        }
        JsonNode children = plan.get("Plans");
        if (children != null) {
            children.forEach(child -> collectIndexNames(child, out));
        }
    }
}
