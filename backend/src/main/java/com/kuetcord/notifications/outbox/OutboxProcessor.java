package com.kuetcord.notifications.outbox;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * One drain cycle of the transactional outbox: claim a batch with {@code SKIP LOCKED}, then process
 * each event in its own transaction so one bad event cannot roll back the rest of the batch.
 * <pre>
 *   PENDING --claim--> PROCESSING --ok--> PROCESSED
 *                          |  \--lease expired--> claimable again
 *                          \--error--> PENDING (retry_count+1, backoff) ... --> DEAD_LETTER
 * </pre>
 * Safe to run from any number of threads or instances at once: {@code SKIP LOCKED} hands every row
 * to exactly one claimer.
 */
@Component
public class OutboxProcessor {

    private static final Logger log = LoggerFactory.getLogger(OutboxProcessor.class);
    private static final int MAX_ERROR_CODE = 100;

    private final OutboxClaimRepository claimRepository;
    private final NotificationWriter writer;
    private final NotificationPusher pusher;
    private final TransactionTemplate perEventTransaction;
    private final Map<String, OutboxEventHandler> handlers = new HashMap<>();
    private final int batchSize;
    private final int leaseSeconds;
    private final int maxAttempts;

    public OutboxProcessor(OutboxClaimRepository claimRepository,
                           NotificationWriter writer,
                           NotificationPusher pusher,
                           PlatformTransactionManager transactionManager,
                           List<OutboxEventHandler> handlerBeans,
                           @Value("${kuetcord.outbox.worker.batch-size:50}") int batchSize,
                           @Value("${kuetcord.outbox.worker.lease-seconds:30}") int leaseSeconds,
                           @Value("${kuetcord.outbox.worker.max-attempts:5}") int maxAttempts) {
        this.claimRepository = claimRepository;
        this.writer = writer;
        this.pusher = pusher;
        this.perEventTransaction = new TransactionTemplate(transactionManager);
        this.perEventTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.batchSize = batchSize;
        this.leaseSeconds = leaseSeconds;
        this.maxAttempts = maxAttempts;
        for (OutboxEventHandler handler : handlerBeans) {
            for (String type : handler.eventTypes()) {
                OutboxEventHandler previous = handlers.putIfAbsent(type, handler);
                if (previous != null) {
                    throw new IllegalStateException("Duplicate outbox handlers for " + type + ": "
                            + previous.getClass().getSimpleName() + ", " + handler.getClass().getSimpleName());
                }
            }
        }
    }

    public Set<String> handledEventTypes() {
        return Set.copyOf(handlers.keySet());
    }

    /** Claims and processes at most one batch. @return IDs of the events this call claimed. */
    public List<Long> drainOnce() {
        List<ClaimedOutboxEvent> batch = claimRepository.claim(handlers.keySet(), batchSize, leaseSeconds);
        for (ClaimedOutboxEvent event : batch) {
            process(event);
        }
        return batch.stream().map(ClaimedOutboxEvent::id).toList();
    }

    /** Drains until nothing claimable is left (tests and shutdown). @return total events claimed. */
    public int drainAll() {
        int total = 0;
        List<Long> claimed;
        do {
            claimed = drainOnce();
            total += claimed.size();
        } while (!claimed.isEmpty());
        return total;
    }

    private void process(ClaimedOutboxEvent event) {
        try {
            perEventTransaction.executeWithoutResult(status -> {
                List<NotificationDraft> drafts = handlers.get(event.eventType()).toNotifications(event);
                List<NotificationWriter.Inserted> inserted = writer.insertAll(drafts, event.sourceEventId());
                if (!claimRepository.markProcessed(event.id())) {
                    log.info("Outbox event {} was re-claimed after its lease expired; dedup kept it exactly-once", event.id());
                }
                pusher.pushAfterCommit(inserted);
            });
        } catch (RuntimeException ex) {
            int attempts = event.retryCount() + 1;
            log.warn("Outbox event {} ({}) failed on attempt {}/{}: {}",
                    event.id(), event.eventType(), attempts, maxAttempts, ex.toString());
            claimRepository.markFailed(event.id(), attempts, maxAttempts, errorCode(ex));
        }
    }

    private static String errorCode(RuntimeException ex) {
        String code = ex.getClass().getSimpleName();
        return code.length() > MAX_ERROR_CODE ? code.substring(0, MAX_ERROR_CODE) : code;
    }
}
