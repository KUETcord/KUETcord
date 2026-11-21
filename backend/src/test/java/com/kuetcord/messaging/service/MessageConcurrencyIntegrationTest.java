package com.kuetcord.messaging.service;

import com.kuetcord.messaging.dto.CreateGroupRequest;
import com.kuetcord.messaging.repository.MessageRepository;
import com.kuetcord.support.MessagingTestUsers;
import com.kuetcord.support.MessagingTestUsers.TestUser;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Concurrency: a flaky-4G client fires the same message (same idempotency key) from 10 threads
 * at once. Exactly one row may exist, exactly one call reports "created", all return its ID.
 * Not @Transactional: every thread must commit independently against PostgreSQL.
 */
@SpringBootTest
@Import(MessagingTestUsers.class)
class MessageConcurrencyIntegrationTest {

    private static final int THREADS = 10;

    @Autowired
    private MessageService messageService;

    @Autowired
    private ConversationService conversationService;

    @Autowired
    private MessageRepository messageRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private MessagingTestUsers users;

    @Test
    @DisplayName("10 concurrent retries with one idempotency key persist exactly one message")
    void shouldPersistExactlyOneMessageUnderConcurrentRetries() throws Exception {
        TestUser sender = users.verifiedStudent("Retry Sender");
        TestUser b = users.verifiedStudent("Member B");
        TestUser c = users.verifiedStudent("Member C");
        long conversationId = conversationService.createGroup(sender.id(),
                new CreateGroupRequest("Flaky 4G group", List.of(b.id(), c.id()))).id();
        String key = UUID.randomUUID().toString();

        ExecutorService pool = Executors.newFixedThreadPool(THREADS);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger created = new AtomicInteger();
        List<Future<Long>> futures = new ArrayList<>();
        try {
            for (int i = 0; i < THREADS; i++) {
                futures.add(pool.submit(() -> {
                    start.await();
                    MessageService.SendResult result = messageService.send(conversationId, sender.id(), "CT 2 notes are ready!", key);
                    if (result.created()) {
                        created.incrementAndGet();
                    }
                    return result.message().id();
                }));
            }
            start.countDown();

            Set<Long> ids = new HashSet<>();
            for (Future<Long> future : futures) {
                ids.add(future.get(30, TimeUnit.SECONDS));
            }

            Integer rows = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM messages WHERE conversation_id = ? AND idempotency_key = ?",
                    Integer.class, conversationId, key);
            assertEquals(1, rows, "Duplicate rows means idempotency failed");
            assertEquals(1, created.get(), "Exactly one call must report creation (only it is broadcast)");
            assertEquals(1, ids.size(), "Every retry must be answered with the same message ID");
            assertEquals(ids.iterator().next(),
                    messageRepository.findByConversationIdAndSenderIdAndIdempotencyKey(conversationId, sender.id(), key)
                            .orElseThrow().getId());
        } finally {
            pool.shutdownNow();
        }
    }
}
