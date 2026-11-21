package com.kuetcord.common.concurrency;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RequestCoalescerUnitTest {

    @Test
    @DisplayName("SingleFlight collapses 12 concurrent identical reads into 1 single supplier invocation")
    void shouldCollapseConcurrentIdenticalReads() throws Exception {
        RequestCoalescer coalescer = new RequestCoalescer();
        AtomicInteger dbQueryCount = new AtomicInteger(0);
        int threads = 12;

        CountDownLatch startGate = new CountDownLatch(1);
        CountDownLatch enteredSupplier = new CountDownLatch(1);
        CountDownLatch releaseSupplier = new CountDownLatch(1);

        ExecutorService pool = Executors.newFixedThreadPool(threads);
        List<Future<String>> futures = new ArrayList<>();

        for (int i = 0; i < threads; i++) {
            futures.add(pool.submit(() -> {
                startGate.await(5, TimeUnit.SECONDS);
                return coalescer.coalesce("ct:event:42", () -> {
                    int call = dbQueryCount.incrementAndGet();
                    enteredSupplier.countDown();
                    try {
                        releaseSupplier.await(5, TimeUnit.SECONDS);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                    return "CT-PAYLOAD-" + call;
                });
            }));
        }

        startGate.countDown();
        assertTrue(enteredSupplier.await(5, TimeUnit.SECONDS));
        // Let remaining threads join the in-flight promise before releasing
        Thread.sleep(50);
        releaseSupplier.countDown();

        for (Future<String> future : futures) {
            assertEquals("CT-PAYLOAD-1", future.get(5, TimeUnit.SECONDS));
        }

        assertEquals(1, dbQueryCount.get(), "Exactly 1 DB query must execute for 12 concurrent callers");

        // Next call after completion should execute a fresh query cleanly
        String secondWave = coalescer.coalesce("ct:event:42", () -> "CT-PAYLOAD-" + dbQueryCount.incrementAndGet());
        assertEquals("CT-PAYLOAD-2", secondWave);
        pool.shutdownNow();
    }
}
