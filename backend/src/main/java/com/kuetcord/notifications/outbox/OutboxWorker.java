package com.kuetcord.notifications.outbox;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.SmartLifecycle;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.ScheduledFuture;

/**
 * Bulkhead for the outbox: runs {@link OutboxProcessor#drainOnce()} on a private {@code outbox-worker-}
 * scheduler with a fixed delay.
 * <p>
 * The scheduler is deliberately not a Spring bean. Enabling the STOMP broker registers
 * {@code messageBrokerTaskScheduler} (heartbeats); a second {@code TaskScheduler} bean would change
 * which scheduler {@code @Scheduled} methods elsewhere pick, and sharing one would let a slow drain
 * delay heartbeats. Disabled in tests ({@code kuetcord.outbox.worker.enabled=false}), which call the
 * processor directly.
 */
@Component
public class OutboxWorker implements SmartLifecycle {

    private static final Logger log = LoggerFactory.getLogger(OutboxWorker.class);

    private final OutboxProcessor processor;
    private final boolean enabled;
    private final Duration pollInterval;
    private ThreadPoolTaskScheduler scheduler;
    private volatile ScheduledFuture<?> task;

    public OutboxWorker(OutboxProcessor processor,
                        @Value("${kuetcord.outbox.worker.enabled:true}") boolean enabled,
                        @Value("${kuetcord.outbox.worker.poll-interval-ms:1000}") long pollIntervalMs) {
        this.processor = processor;
        this.enabled = enabled;
        this.pollInterval = Duration.ofMillis(pollIntervalMs);
    }

    @Override
    public synchronized void start() {
        if (!enabled || task != null) {
            return;
        }
        scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(1);
        scheduler.setThreadNamePrefix("outbox-worker-");
        scheduler.setWaitForTasksToCompleteOnShutdown(true);
        scheduler.setAwaitTerminationSeconds(10);
        scheduler.initialize();
        task = scheduler.scheduleWithFixedDelay(this::tick, pollInterval);
        log.info("Outbox notification worker started (every {} ms, event types {})",
                pollInterval.toMillis(), processor.handledEventTypes());
    }

    private void tick() {
        try {
            // Keep draining while batches come back, so a burst is cleared without waiting an interval per batch.
            boolean more;
            do {
                more = !processor.drainOnce().isEmpty();
            } while (more && isRunning());
        } catch (RuntimeException ex) {
            // Never let an exception cancel the fixed-delay schedule (e.g. database briefly unavailable).
            log.warn("Outbox drain cycle failed: {}", ex.toString());
        }
    }

    @Override
    public synchronized void stop() {
        if (task != null) {
            task.cancel(false);
            task = null;
        }
        if (scheduler != null) {
            scheduler.shutdown();
            scheduler = null;
        }
    }

    @Override
    public boolean isRunning() {
        return task != null;
    }
}
