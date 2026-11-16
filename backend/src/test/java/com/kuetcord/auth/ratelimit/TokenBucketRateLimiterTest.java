package com.kuetcord.auth.ratelimit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TokenBucketRateLimiterTest {

    @Test
    @DisplayName("TokenBucket should allow initial burst, throttle when empty, and lazily refill over time")
    void shouldEnforceCapacityAndLazyRefill() {
        AtomicLong fakeClockNanos = new AtomicLong(1_000_000_000L);
        // Capacity 2, refill 1 token per second
        TokenBucket bucket = new TokenBucket(2, 1.0, fakeClockNanos::get);

        assertTrue(bucket.tryConsume(), "1st token should be available");
        assertTrue(bucket.tryConsume(), "2nd token should be available");
        assertFalse(bucket.tryConsume(), "3rd immediate request must be throttled");

        // Advance clock by 0.5s -> still < 1.0 token
        fakeClockNanos.addAndGet(500_000_000L);
        assertFalse(bucket.tryConsume(), "Half token should not satisfy consume");

        // Advance clock by another 0.6s -> 1.1 tokens total
        fakeClockNanos.addAndGet(600_000_000L);
        assertTrue(bucket.tryConsume(), "Token should be replenished after 1.1s elapsed");
        assertFalse(bucket.tryConsume(), "Bucket should be empty again");
    }

    @Test
    @DisplayName("RateLimitingFilter should allow 10 login attempts and throttle 11th with HTTP 429 and Retry-After")
    void filterShouldThrottle11thLoginAttemptWith429() throws Exception {
        ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());
        RateLimitingFilter filter = new RateLimitingFilter(mapper);

        for (int i = 1; i <= 10; i++) {
            MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/v1/auth/login");
            req.setRemoteAddr("10.0.0.55");
            MockHttpServletResponse res = new MockHttpServletResponse();
            filter.doFilter(req, res, new MockFilterChain());
            assertEquals(HttpStatus.OK.value(), res.getStatus(), "Request #" + i + " should pass");
        }

        MockHttpServletRequest throttledReq = new MockHttpServletRequest("POST", "/api/v1/auth/login");
        throttledReq.setRemoteAddr("10.0.0.55");
        MockHttpServletResponse throttledRes = new MockHttpServletResponse();

        filter.doFilter(throttledReq, throttledRes, new MockFilterChain());

        assertEquals(HttpStatus.TOO_MANY_REQUESTS.value(), throttledRes.getStatus());
        assertEquals("60", throttledRes.getHeader("Retry-After"));
        assertTrue(throttledRes.getContentAsString().contains("Too many requests"));
    }
}

