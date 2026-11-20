package com.kuetcord.opportunities.service;

import com.kuetcord.opportunities.dto.CreateOpportunityRequest;
import com.kuetcord.opportunities.dto.BookmarkStatusDto;
import com.kuetcord.opportunities.model.OpportunityCategory;
import com.kuetcord.opportunities.model.OpportunityMode;
import com.kuetcord.opportunities.repository.OpportunityBookmarkRepository;
import com.kuetcord.support.MessagingTestUsers;
import com.kuetcord.support.MessagingTestUsers.TestUser;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Concurrency: a student double/triple-clicks "Bookmark" and the requests race.
 * {@code INSERT ... ON CONFLICT DO NOTHING} on {@code uq_user_opportunity_bookmark} must leave exactly
 * one row and every request must succeed (no 500 from a unique violation).
 * <p>
 * Not @Transactional on purpose: each thread needs its own committed transaction.
 */
@SpringBootTest
@Import(MessagingTestUsers.class)
class OpportunityBookmarkConcurrencyIntegrationTest {

    private static final int THREADS = 8;

    @Autowired
    private OpportunityService opportunityService;

    @Autowired
    private OpportunityBookmarkRepository bookmarkRepository;

    @Autowired
    private MessagingTestUsers users;

    @Test
    @DisplayName("8 simultaneous bookmark calls from one student store exactly one bookmark")
    void shouldStoreSingleBookmarkUnderRace() throws Exception {
        TestUser student = users.verifiedStudent("Race Bookmarker");
        Instant deadline = Instant.now().plus(Duration.ofDays(10));
        long opportunityId = opportunityService.submit(student.id(), new CreateOpportunityRequest(
                "Race Hackathon", OpportunityCategory.HACKATHON, "Race Club", "https://race.example.org",
                deadline, deadline.plus(Duration.ofDays(1)), deadline.plus(Duration.ofDays(2)),
                OpportunityMode.ONLINE, null, 1, 4, null)).id();

        ExecutorService pool = Executors.newFixedThreadPool(THREADS);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<BookmarkStatusDto>> results = new ArrayList<>();
        try {
            for (int i = 0; i < THREADS; i++) {
                results.add(pool.submit(() -> {
                    start.await();
                    return opportunityService.bookmark(student.id(), opportunityId);
                }));
            }
            start.countDown();

            for (Future<BookmarkStatusDto> result : results) {
                assertTrue(result.get(30, TimeUnit.SECONDS).bookmarked());
            }
            assertEquals(1, bookmarkRepository.countByOpportunityId(opportunityId));
        } finally {
            pool.shutdownNow();
        }
    }
}
