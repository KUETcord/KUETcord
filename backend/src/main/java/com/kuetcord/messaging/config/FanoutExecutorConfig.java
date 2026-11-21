package com.kuetcord.messaging.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.ThreadPoolExecutor;

/**
 * Bulkhead pool for the Web-Queue-Worker fan-out stage. The inbound STOMP thread persists the
 * message and ACKs; delivery to subscribers runs here, so a burst in a large channel cannot
 * stall frame parsing, heartbeats or HTTP.
 * <p>
 * When the queue is full the task is dropped (AbortPolicy, logged by the broadcaster): the
 * message is already durable in PostgreSQL and clients recover it through history/{@code /sync}.
 */
@Configuration
public class FanoutExecutorConfig {

    public static final String FANOUT_EXECUTOR = "fanoutExecutor";

    @Bean(name = FANOUT_EXECUTOR)
    public ThreadPoolTaskExecutor fanoutExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(8);
        executor.setMaxPoolSize(32);
        executor.setQueueCapacity(5000);
        executor.setKeepAliveSeconds(60);
        executor.setThreadNamePrefix("fanout-worker-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(5);
        executor.initialize();
        return executor;
    }
}
