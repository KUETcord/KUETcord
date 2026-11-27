package com.kuetcord.notifications.outbox;

import com.kuetcord.support.MessagingTestUsers;
import com.kuetcord.support.MessagingTestUsers.TestUser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The outbox worker against real PostgreSQL 16 row locks: competing workers, a held lock,
 * an expired lease, bounded retries and event types that belong to other consumers.
 * Not {@code @Transactional}: every worker needs its own connection and its own commits.
 */
@SpringBootTest
@Import(MessagingTestUsers.class)
class OutboxSkipLockedIntegrationTest {

    private static final String TEST_AGGREGATE = "OUTBOX_TEST";

    @Autowired
    private OutboxProcessor processor;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private MessagingTestUsers users;

    private TestUser recipient;
    private String run;

    @BeforeEach
    void setUp() {
        recipient = users.verifiedStudent("Outbox Recipient");
        run = UUID.randomUUID().toString().substring(0, 8);
        processor.drainAll(); // start from a clean queue, whatever earlier suites left behind
    }

    @AfterEach
    void tearDown() {
        jdbcTemplate.update("DELETE FROM outbox_events WHERE aggregate_type = ?", TEST_AGGREGATE);
    }

    @Test
    @DisplayName("4 workers drain 120 events concurrently: every event claimed exactly once, 120 notifications, 0 duplicates")
    void competingWorkersNeverClaimTheSameRow() throws Exception {
        List<Long> ids = new ArrayList<>();
        for (int i = 0; i < 120; i++) {
            ids.add(insertTaskAssigned("{\"recipientIds\":[" + recipient.id() + "],\"playgroundId\":7,"
                    + "\"playgroundTitle\":\"KUETcord\",\"taskTitle\":\"Card " + i + "\"}"));
        }

        Map<Long, AtomicInteger> claims = new ConcurrentHashMap<>();
        Map<Integer, Integer> perWorker = new ConcurrentHashMap<>();
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(4);
        List<Future<?>> workers = new ArrayList<>();
        for (int w = 0; w < 4; w++) {
            int worker = w;
            workers.add(pool.submit(() -> {
                start.await();
                List<Long> claimed;
                do {
                    claimed = processor.drainOnce();
                    claimed.forEach(id -> claims.computeIfAbsent(id, k -> new AtomicInteger()).incrementAndGet());
                    perWorker.merge(worker, claimed.size(), Integer::sum);
                } while (!claimed.isEmpty());
                return null;
            }));
        }
        start.countDown();
        for (Future<?> f : workers) {
            f.get(60, TimeUnit.SECONDS);
        }
        pool.shutdown();

        for (Long id : ids) {
            assertEquals(1, claims.getOrDefault(id, new AtomicInteger()).get(), "event " + id + " claimed exactly once");
            assertEquals("PROCESSED", statusOf(id));
        }
        assertEquals(120, count("SELECT COUNT(*) FROM notifications WHERE recipient_id = ?", recipient.id()));
        assertEquals(120, count("SELECT COUNT(DISTINCT source_event_id) FROM notifications WHERE recipient_id = ?", recipient.id()));
        System.out.println("[outbox] events claimed per worker: " + perWorker);
    }

    @Test
    @DisplayName("A row locked by another transaction is skipped without blocking; the worker takes the next row")
    void lockedRowIsSkippedNotWaitedOn() throws Exception {
        long locked = insertTaskAssigned(payloadFor("Locked card"));
        long free = insertTaskAssigned(payloadFor("Free card"));

        CountDownLatch lockHeld = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        ExecutorService pool = Executors.newSingleThreadExecutor();
        Future<?> holder = pool.submit(() -> new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            jdbcTemplate.queryForObject("SELECT id FROM outbox_events WHERE id = ? FOR UPDATE", Long.class, locked);
            lockHeld.countDown();
            try {
                release.await(30, TimeUnit.SECONDS);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            }
        }));
        assertTrue(lockHeld.await(10, TimeUnit.SECONDS));

        long startedAt = System.nanoTime();
        List<Long> claimed = processor.drainOnce();
        long tookMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);

        assertTrue(claimed.contains(free), "the unlocked row is processed");
        assertFalse(claimed.contains(locked), "the locked row is skipped, not stolen");
        assertTrue(tookMs < 5_000, "SKIP LOCKED must not wait for the other transaction (took " + tookMs + " ms)");
        assertEquals("PENDING", statusOf(locked));

        release.countDown();
        holder.get(10, TimeUnit.SECONDS);
        pool.shutdown();
        processor.drainAll();
        assertEquals("PROCESSED", statusOf(locked), "once the lock is gone the row is picked up normally");
    }

    @Test
    @DisplayName("A crashed worker's expired lease is re-claimed; a live lease is left alone")
    void expiredLeaseIsReclaimed() {
        long abandoned = insertTaskAssigned(payloadFor("Abandoned"));
        long inFlight = insertTaskAssigned(payloadFor("In flight"));
        jdbcTemplate.update("UPDATE outbox_events SET status = 'PROCESSING', next_attempt_at = NOW() - INTERVAL '1 second' WHERE id = ?", abandoned);
        jdbcTemplate.update("UPDATE outbox_events SET status = 'PROCESSING', next_attempt_at = NOW() + INTERVAL '1 minute' WHERE id = ?", inFlight);

        processor.drainAll();

        assertEquals("PROCESSED", statusOf(abandoned));
        assertEquals("PROCESSING", statusOf(inFlight));
        assertEquals(1, count("SELECT COUNT(*) FROM notifications WHERE source_event_id = ?", "outbox:" + abandoned));
    }

    @Test
    @DisplayName("A failing event is retried with backoff and dead-lettered after 5 attempts; it never blocks the batch")
    void failingEventBacksOffThenDeadLetters() {
        long broken = insertTaskAssigned("{\"playgroundId\":7,\"taskTitle\":\"no recipients\"}");
        long healthy = insertTaskAssigned(payloadFor("Healthy"));

        processor.drainAll();

        assertEquals("PROCESSED", statusOf(healthy), "one bad event must not roll back the others");
        Map<String, Object> row = jdbcTemplate.queryForMap(
                "SELECT status, retry_count, last_error_code, next_attempt_at > NOW() AS backed_off FROM outbox_events WHERE id = ?", broken);
        assertEquals("PENDING", row.get("status"));
        assertEquals(1, ((Number) row.get("retry_count")).intValue());
        assertEquals("IllegalArgumentException", row.get("last_error_code"));
        assertEquals(Boolean.TRUE, row.get("backed_off"), "next attempt is scheduled in the future");

        jdbcTemplate.update("UPDATE outbox_events SET retry_count = 4, next_attempt_at = NOW() - INTERVAL '1 second' WHERE id = ?", broken);
        processor.drainAll();

        assertEquals("DEAD_LETTER", statusOf(broken));
        assertEquals(5, count("SELECT retry_count FROM outbox_events WHERE id = ?", broken));
    }

    @Test
    @DisplayName("Event types without a handler (e.g. CT Hub e-mail events) are never claimed")
    void foreignEventTypesAreLeftForTheirOwnConsumer() {
        long foreign = insert("CT_APPROVAL_REQUESTED", "{\"teacherId\":1}");

        processor.drainAll();

        assertEquals("PENDING", statusOf(foreign));
        assertFalse(processor.handledEventTypes().contains("CT_APPROVAL_REQUESTED"));
    }

    // ----------------------------------------------------------------- helpers

    private String payloadFor(String title) {
        return "{\"recipientIds\":[" + recipient.id() + "],\"playgroundId\":7,\"playgroundTitle\":\"KUETcord\",\"taskTitle\":\"" + title + "\"}";
    }

    private long insertTaskAssigned(String payloadJson) {
        return insert("TASK_ASSIGNED", payloadJson);
    }

    private long insert(String eventType, String payloadJson) {
        return jdbcTemplate.queryForObject(
                "INSERT INTO outbox_events (aggregate_type, aggregate_id, event_type, payload) VALUES (?, ?, ?, ?::jsonb) RETURNING id",
                Long.class, TEST_AGGREGATE, run, eventType, payloadJson);
    }

    private String statusOf(long id) {
        return jdbcTemplate.queryForObject("SELECT status FROM outbox_events WHERE id = ?", String.class, id);
    }

    private int count(String sql, Object... args) {
        Integer value = jdbcTemplate.queryForObject(sql, Integer.class, args);
        return value == null ? 0 : value;
    }
}
