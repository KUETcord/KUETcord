package com.kuetcord.auth.ratelimit;

import java.util.concurrent.atomic.AtomicReference;
import java.util.function.LongSupplier;

/**
 * Lock-free token bucket implementing lazy O(1) mathematical replenishment using atomic CAS updates.
 * Avoids background timer threads to conserve memory and CPU on constrained containers.
 */
public class TokenBucket {

    private record BucketState(double tokens, long lastRefillNanos) {
    }

    private final long capacity;
    private final double refillTokensPerNano;
    private final LongSupplier nanoTimeSource;
    private final AtomicReference<BucketState> state;

    public TokenBucket(long capacity, double refillTokensPerSecond) {
        this(capacity, refillTokensPerSecond, System::nanoTime);
    }

    public TokenBucket(long capacity, double refillTokensPerSecond, LongSupplier nanoTimeSource) {
        if (capacity <= 0) {
            throw new IllegalArgumentException("Capacity must be positive");
        }
        if (refillTokensPerSecond < 0) {
            throw new IllegalArgumentException("Refill rate must be non-negative");
        }
        this.capacity = capacity;
        this.refillTokensPerNano = refillTokensPerSecond / 1_000_000_000.0;
        this.nanoTimeSource = nanoTimeSource;
        this.state = new AtomicReference<>(new BucketState(capacity, nanoTimeSource.getAsLong()));
    }

    /**
     * Attempts to consume a single token from the bucket without blocking.
     *
     * @return true if a token was available and consumed; false if rate limit exceeded.
     */
    public boolean tryConsume() {
        while (true) {
            BucketState current = state.get();
            long now = nanoTimeSource.getAsLong();
            long elapsedNanos = Math.max(0L, now - current.lastRefillNanos());
            double replenished = current.tokens() + (elapsedNanos * refillTokensPerNano);
            double available = Math.min(capacity, replenished);

            if (available < 1.0) {
                // Only advance timestamp/tokens if refill actually accrued fractional progress
                if (available > current.tokens()) {
                    BucketState updated = new BucketState(available, now);
                    if (state.compareAndSet(current, updated)) {
                        return false;
                    }
                    continue;
                }
                return false;
            }

            BucketState next = new BucketState(available - 1.0, now);
            if (state.compareAndSet(current, next)) {
                return true;
            }
        }
    }
}

