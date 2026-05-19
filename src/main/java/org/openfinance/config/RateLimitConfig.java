package org.openfinance.config;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration for API rate limiting using Bucket4j (in-memory, single-node).
 *
 * <p>Rate limiting strategy:
 *
 * <ul>
 *   <li><strong>General endpoints</strong>: per-user token bucket: 200 requests per minute (refills
 *       every 60 seconds), with a burst capacity of 50 additional tokens.
 *   <li><strong>Sensitive endpoints</strong> (login, register, password reset, file upload):
 *       stricter per-IP bucket: 10 requests per minute with no burst. This prevents brute-force
 *       attacks and abusive file upload patterns. Requirement TASK-15.1.5.
 * </ul>
 *
 * <p>Buckets are stored in {@link ConcurrentHashMap}s keyed by username or IP. This is an in-memory
 * implementation — appropriate for single-node deployments. For clustered deployments, replace with
 * a distributed cache-backed bucket.
 *
 * @author Open Finance Team
 * @version 2.0
 * @since 2026-03-19
 */
@Configuration
public class RateLimitConfig {

    /** Maximum sustained requests per minute per principal (general endpoints). */
    public static final int REQUESTS_PER_MINUTE = 200;

    /** Burst allowance on top of the sustained rate (general endpoints). */
    public static final int BURST_CAPACITY = 50;

    /**
     * Maximum requests per minute per IP for sensitive endpoints (login, register, file upload).
     * Requirement TASK-15.1.5.
     */
    public static final int SENSITIVE_REQUESTS_PER_MINUTE = 10;

    /** Per-principal bucket store for general endpoints (username → bucket). */
    private final ConcurrentHashMap<String, Bucket> buckets = new ConcurrentHashMap<>();

    /**
     * Per-IP bucket store for sensitive endpoints (IP → bucket). Requirement TASK-15.1.5: stricter
     * rate limiting on auth/upload endpoints.
     */
    private final ConcurrentHashMap<String, Bucket> sensitiveBuckets = new ConcurrentHashMap<>();

    /**
     * Returns (or creates) a rate-limit bucket for the given principal key (general endpoints).
     *
     * <p>Each bucket uses a greedy-refill policy: {@code REQUESTS_PER_MINUTE} tokens are refilled
     * every 60 seconds, with an additional burst capacity of {@code BURST_CAPACITY} tokens
     * available at the start.
     *
     * @param principalKey username or remote IP used as the bucket key
     * @return the {@link Bucket} associated with the given key
     */
    public Bucket resolveBucket(String principalKey) {
        return buckets.computeIfAbsent(principalKey, this::newBucket);
    }

    /**
     * Returns (or creates) a stricter rate-limit bucket for the given IP key (sensitive endpoints).
     *
     * <p>Requirement TASK-15.1.5: Dedicated per-IP buckets for login, register, and file upload
     * endpoints to prevent brute-force and DoS attacks. Limit: {@value
     * #SENSITIVE_REQUESTS_PER_MINUTE} requests per minute, no burst.
     *
     * @param ipKey remote IP address used as the bucket key
     * @return the {@link Bucket} associated with the given IP
     */
    public Bucket resolveSensitiveBucket(String ipKey) {
        return sensitiveBuckets.computeIfAbsent(ipKey, this::newSensitiveBucket);
    }

    /**
     * Creates a new Bucket4j token-bucket for general endpoint rate limiting.
     *
     * @param key the principal key (unused in construction, present for {@code computeIfAbsent}
     *     compatibility)
     * @return a newly created {@link Bucket}
     */
    private Bucket newBucket(String key) {
        Bandwidth limit =
                Bandwidth.builder()
                        .capacity(REQUESTS_PER_MINUTE + BURST_CAPACITY)
                        .refillGreedy(REQUESTS_PER_MINUTE, Duration.ofMinutes(1))
                        .build();
        return Bucket.builder().addLimit(limit).build();
    }

    /**
     * Creates a new Bucket4j token-bucket for sensitive endpoint rate limiting.
     *
     * <p>Requirement TASK-15.1.5: Strict rate limiting with no burst for auth endpoints.
     *
     * @param key the IP key (unused in construction, present for {@code computeIfAbsent}
     *     compatibility)
     * @return a newly created {@link Bucket}
     */
    private Bucket newSensitiveBucket(String key) {
        Bandwidth limit =
                Bandwidth.builder()
                        .capacity(SENSITIVE_REQUESTS_PER_MINUTE)
                        .refillGreedy(SENSITIVE_REQUESTS_PER_MINUTE, Duration.ofMinutes(1))
                        .build();
        return Bucket.builder().addLimit(limit).build();
    }
}
