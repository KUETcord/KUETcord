package com.kuetcord.common.concurrency;

import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * SingleFlight request coalescer collapsing concurrent identical read queries into a single
 * upstream execution to suppress thundering-herd database spikes.
 */
@Component
public class RequestCoalescer {

    private final ConcurrentHashMap<String, CompletableFuture<Object>> inFlight = new ConcurrentHashMap<>();

    @SuppressWarnings("unchecked")
    public <T> T coalesce(String key, Supplier<T> supplier) {
        CompletableFuture<Object> promise = new CompletableFuture<>();
        CompletableFuture<Object> existing = inFlight.putIfAbsent(key, promise);

        if (existing != null) {
            try {
                return (T) existing.join();
            } catch (CompletionException ex) {
                if (ex.getCause() instanceof RuntimeException runtime) {
                    throw runtime;
                }
                throw ex;
            }
        }

        try {
            T result = supplier.get();
            promise.complete(result);
            return result;
        } catch (Throwable t) {
            promise.completeExceptionally(t);
            throw t;
        } finally {
            inFlight.remove(key, promise);
        }
    }
}
