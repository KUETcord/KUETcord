package com.kuetcord.common.outbox;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;

/**
 * Persists domain events into {@code outbox_events} participating in the caller's active transaction.
 */
@Service
@RequiredArgsConstructor
public class OutboxEventPublisher {

    private final OutboxEventRepository outboxEventRepository;

    public OutboxEvent publish(String aggregateType, String aggregateId, String eventType, Map<String, Object> payload) {
        OutboxEvent event = OutboxEvent.builder()
                .aggregateType(aggregateType)
                .aggregateId(aggregateId)
                .eventType(eventType)
                .payload(payload)
                .occurredAt(Instant.now())
                .status(OutboxStatus.PENDING)
                .retryCount(0)
                .build();
        return outboxEventRepository.save(event);
    }
}
