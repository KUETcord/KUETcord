package com.kuetcord.playgrounds.service;

import com.kuetcord.playgrounds.dto.CreatePlaygroundRequest;
import com.kuetcord.playgrounds.dto.CreateTaskRequest;
import com.kuetcord.playgrounds.dto.TaskDto;
import com.kuetcord.playgrounds.dto.UpdateTaskRequest;
import com.kuetcord.playgrounds.model.TaskStatus;
import com.kuetcord.support.MessagingTestUsers;
import com.kuetcord.support.MessagingTestUsers.TestUser;
import jakarta.persistence.OptimisticLockException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Concurrency: several teammates drag the same card at the same instant, all from version 0.
 * Exactly one move may win; every other request must fail with an optimistic-lock error (HTTP 409),
 * never silently overwrite the winner.
 * <p>
 * Not @Transactional on purpose: each thread needs its own committed transaction.
 */
@SpringBootTest
@Import(MessagingTestUsers.class)
class ProjectTaskConcurrencyIntegrationTest {

    private static final int THREADS = 6;

    @Autowired
    private PlaygroundService playgroundService;

    @Autowired
    private ProjectTaskService taskService;

    @Autowired
    private MessagingTestUsers users;

    @Test
    @DisplayName("6 simultaneous moves of one card from version 0: exactly one wins, the rest get optimistic-lock errors")
    void shouldLetExactlyOneConcurrentMoveWin() throws Exception {
        TestUser lead = users.verifiedStudent("Race Lead");
        long playgroundId = playgroundService.create(lead.id(),
                new CreatePlaygroundRequest("Race Team", null, null, null, null, null)).id();
        long taskId = taskService.create(playgroundId, lead.id(),
                new CreateTaskRequest("Contended card", null, TaskStatus.TODO, null, null, null)).id();

        TaskStatus[] targets = {TaskStatus.IN_PROGRESS, TaskStatus.REVIEW, TaskStatus.DONE};
        ExecutorService pool = Executors.newFixedThreadPool(THREADS);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<TaskDto>> results = new ArrayList<>();
        try {
            for (int i = 0; i < THREADS; i++) {
                TaskStatus target = targets[i % targets.length];
                results.add(pool.submit(() -> {
                    start.await();
                    return taskService.update(playgroundId, taskId, lead.id(),
                            new UpdateTaskRequest(null, null, target, null, null, null, null, null, 0L));
                }));
            }
            start.countDown();

            int wins = 0;
            TaskStatus winningStatus = null;
            for (Future<TaskDto> result : results) {
                try {
                    TaskDto moved = result.get(30, TimeUnit.SECONDS);
                    wins++;
                    winningStatus = moved.status();
                    assertEquals(1L, moved.version());
                } catch (ExecutionException e) {
                    Throwable cause = e.getCause();
                    assertTrue(cause instanceof OptimisticLockException
                                    || cause instanceof ObjectOptimisticLockingFailureException,
                            "Losers must fail with an optimistic-lock error, got " + cause);
                }
            }

            assertEquals(1, wins, "Exactly one concurrent move may succeed");
            TaskDto stored = taskService.get(playgroundId, taskId, lead.id());
            assertEquals(1L, stored.version());
            assertEquals(winningStatus, stored.status());
        } finally {
            pool.shutdownNow();
        }
    }
}
