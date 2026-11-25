package com.kuetcord.playgrounds.service;

import com.kuetcord.common.outbox.OutboxEventRepository;
import com.kuetcord.playgrounds.dto.CreateMilestoneRequest;
import com.kuetcord.playgrounds.dto.CreatePlaygroundRequest;
import com.kuetcord.playgrounds.dto.MilestoneDto;
import com.kuetcord.playgrounds.dto.UpdateMilestoneRequest;
import com.kuetcord.playgrounds.event.PlaygroundOutboxEmitter;
import com.kuetcord.playgrounds.model.MilestoneStatus;
import com.kuetcord.playgrounds.model.Playground;
import com.kuetcord.playgrounds.model.ProjectTask;
import com.kuetcord.support.MessagingTestUsers;
import com.kuetcord.support.MessagingTestUsers.TestUser;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.IllegalTransactionStateException;

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
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Concurrency and atomicity of playground outbox events. Not @Transactional on purpose: each thread needs
 * its own committed transaction.
 */
@SpringBootTest
@Import(MessagingTestUsers.class)
class MilestoneCompletionConcurrencyIntegrationTest {

    private static final int THREADS = 6;

    @Autowired
    private PlaygroundService playgroundService;

    @Autowired
    private ProjectMilestoneService milestoneService;

    @Autowired
    private OutboxEventRepository outboxRepository;

    @Autowired
    private PlaygroundOutboxEmitter outboxEmitter;

    @Autowired
    private MessagingTestUsers users;

    @Test
    @DisplayName("6 teammates complete the same milestone at once: every call succeeds, exactly one MILESTONE_REACHED")
    void shouldEmitExactlyOneEventUnderRace() throws Exception {
        TestUser lead = users.verifiedStudent("Race Lead");
        long playgroundId = playgroundService.create(lead.id(),
                new CreatePlaygroundRequest("Milestone Race", null, null, null, null, null)).id();
        long milestoneId = milestoneService.create(playgroundId, lead.id(),
                new CreateMilestoneRequest("Prototype", null, Instant.now().plus(Duration.ofDays(3)))).id();

        ExecutorService pool = Executors.newFixedThreadPool(THREADS);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<MilestoneDto>> results = new ArrayList<>();
        try {
            for (int i = 0; i < THREADS; i++) {
                results.add(pool.submit(() -> {
                    start.await();
                    return milestoneService.updateStatus(playgroundId, milestoneId, lead.id(),
                            new UpdateMilestoneRequest(MilestoneStatus.COMPLETED));
                }));
            }
            start.countDown();
            for (Future<MilestoneDto> result : results) {
                assertEquals(MilestoneStatus.COMPLETED, result.get(30, TimeUnit.SECONDS).status());
            }
        } finally {
            pool.shutdownNow();
        }

        assertEquals(1, outboxRepository.findByAggregateTypeAndAggregateIdOrderByOccurredAtAsc(
                PlaygroundOutboxEmitter.MILESTONE_AGGREGATE, String.valueOf(milestoneId)).size());
    }

    @Test
    @DisplayName("The emitter refuses to run outside a transaction, so an event can never outlive a rolled-back change")
    void shouldRequireSurroundingTransaction() {
        Playground playground = Playground.builder().id(1L).title("Detached").build();
        ProjectTask task = ProjectTask.builder().id(1L).title("Detached").assigneeId(2L).build();
        assertThrows(IllegalTransactionStateException.class,
                () -> outboxEmitter.taskAssigned(playground, task, 3L));
    }
}
