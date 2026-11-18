package com.kuetcord.messaging.service;

import com.kuetcord.messaging.model.Conversation;
import com.kuetcord.messaging.model.MemberStatus;
import com.kuetcord.messaging.repository.ConversationMemberRepository;
import com.kuetcord.messaging.repository.ConversationRepository;
import com.kuetcord.support.MessagingTestUsers;
import com.kuetcord.support.MessagingTestUsers.TestUser;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Concurrency: N threads open the same DM at the same instant (both directions).
 * The partial unique index + INSERT ... ON CONFLICT DO NOTHING must yield exactly one
 * conversation with exactly two members, and every caller must get the same ID.
 * <p>
 * Not @Transactional on purpose: each thread needs its own committed transaction.
 */
@SpringBootTest
@Import(MessagingTestUsers.class)
class DirectConversationConcurrencyIntegrationTest {

    private static final int THREADS = 8;

    @Autowired
    private ConversationService conversationService;

    @Autowired
    private ConversationRepository conversationRepository;

    @Autowired
    private ConversationMemberRepository memberRepository;

    @Autowired
    private MessagingTestUsers users;

    @Test
    @DisplayName("8 simultaneous open-DM calls create exactly one DIRECT conversation")
    void shouldCreateSingleDirectConversationUnderRace() throws Exception {
        TestUser a = users.verifiedStudent("Race A");
        TestUser b = users.verifiedStudent("Race B");

        ExecutorService pool = Executors.newFixedThreadPool(THREADS);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger createdCount = new AtomicInteger();
        List<Future<Long>> results = new ArrayList<>();
        try {
            for (int i = 0; i < THREADS; i++) {
                boolean fromA = i % 2 == 0;
                results.add(pool.submit(() -> {
                    start.await();
                    ConversationService.OpenDirectResult result = fromA
                            ? conversationService.openDirect(a.id(), b.id())
                            : conversationService.openDirect(b.id(), a.id());
                    if (result.created()) {
                        createdCount.incrementAndGet();
                    }
                    return result.conversation().id();
                }));
            }
            start.countDown();

            Set<Long> ids = new HashSet<>();
            for (Future<Long> result : results) {
                ids.add(result.get(30, TimeUnit.SECONDS));
            }

            assertEquals(1, ids.size(), "Every caller must receive the same conversation");
            assertEquals(1, createdCount.get(), "Exactly one caller creates the DM");

            Conversation dm = conversationRepository.findByDirectKey(Conversation.directKeyFor(a.id(), b.id())).orElseThrow();
            assertTrue(ids.contains(dm.getId()));
            assertEquals(2, memberRepository.countByConversationIdAndActiveStatus(dm.getId(), MemberStatus.ACTIVE));
        } finally {
            pool.shutdownNow();
        }
    }
}
