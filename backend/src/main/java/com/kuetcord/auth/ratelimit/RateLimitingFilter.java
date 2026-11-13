package com.kuetcord.auth.ratelimit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.kuetcord.common.dto.ErrorResponse;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;
import java.util.concurrent.TimeUnit;

/**
 * Enforces IP-scoped Token Bucket rate limits on unauthenticated authentication routes
 * to protect BCrypt threads and mail dispatch from brute-force or abuse bursts.
 */
@Component
@RequiredArgsConstructor
public class RateLimitingFilter extends OncePerRequestFilter {

    private record QuotaPolicy(String bucketPrefix, long capacity, double tokensPerMinute) {
    }

    private final ObjectMapper objectMapper;

    // Bounded cache guarantees O(1) eviction so IP rotation bursts cannot exhaust 512MB RAM
    private final Cache<String, TokenBucket> buckets = Caffeine.newBuilder()
            .expireAfterAccess(15, TimeUnit.MINUTES)
            .maximumSize(10_000)
            .build();

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {

        QuotaPolicy policy = resolvePolicy(request);
        if (policy == null) {
            filterChain.doFilter(request, response);
            return;
        }

        String clientIp = extractClientIp(request);
        String bucketKey = policy.bucketPrefix() + ":" + clientIp;
        TokenBucket bucket = buckets.get(bucketKey, k ->
                new TokenBucket(policy.capacity(), policy.tokensPerMinute() / 60.0));

        if (bucket != null && !bucket.tryConsume()) {
            writeTooManyRequests(response, request.getRequestURI());
            return;
        }

        filterChain.doFilter(request, response);
    }

    /**
     * Exposed for isolated rate-limiter unit/integration resets.
     */
    public void clearBuckets() {
        buckets.invalidateAll();
    }

    private QuotaPolicy resolvePolicy(HttpServletRequest request) {
        if (!HttpMethod.POST.matches(request.getMethod())) {
            return null;
        }
        String path = request.getRequestURI();
        return switch (path) {
            case "/api/v1/auth/login", "/api/v1/auth/google" -> new QuotaPolicy("auth-login", 10, 10.0);
            case "/api/v1/auth/register" -> new QuotaPolicy("auth-register", 5, 5.0);
            case "/api/v1/auth/resend-verification" -> new QuotaPolicy("auth-resend", 2, 2.0);
            default -> null;
        };
    }

    private String extractClientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            int commaIdx = forwarded.indexOf(',');
            return (commaIdx > 0 ? forwarded.substring(0, commaIdx) : forwarded).trim();
        }
        String remoteAddr = request.getRemoteAddr();
        return remoteAddr != null ? remoteAddr : "unknown";
    }

    private void writeTooManyRequests(HttpServletResponse response, String path) throws IOException {
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setHeader("Retry-After", "60");

        ErrorResponse body = ErrorResponse.builder()
                .status(HttpStatus.TOO_MANY_REQUESTS.value())
                .error(HttpStatus.TOO_MANY_REQUESTS.getReasonPhrase())
                .message("Too many requests. Please wait before trying again.")
                .path(path)
                .timestamp(Instant.now())
                .build();

        objectMapper.writeValue(response.getWriter(), body);
    }
}

