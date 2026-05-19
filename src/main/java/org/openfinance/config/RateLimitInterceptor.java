package org.openfinance.config;

import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Arrays;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Spring MVC interceptor that enforces per-principal API rate limits via Bucket4j.
 *
 * <p>Two tiers of rate limiting are applied (Requirement TASK-15.1.5):
 *
 * <ol>
 *   <li><strong>Sensitive endpoints</strong> ({@code /api/v1/auth/**}, {@code /api/v1/files/**}):
 *       stricter per-IP bucket of {@value RateLimitConfig#SENSITIVE_REQUESTS_PER_MINUTE}
 *       requests/min with no burst, keyed solely by IP to prevent brute-force attacks before
 *       authentication.
 *   <li><strong>General endpoints</strong>: per-principal bucket of {@value
 *       RateLimitConfig#REQUESTS_PER_MINUTE} requests/min + burst, keyed by username for
 *       authenticated callers or by IP for anonymous callers.
 * </ol>
 *
 * <p>When a bucket is exhausted the interceptor immediately responds with HTTP 429 Too Many
 * Requests and adds the standard {@code X-Rate-Limit-*} headers so clients can back off
 * appropriately.
 *
 * <p>Standard response headers set on every request:
 *
 * <ul>
 *   <li>{@code X-Rate-Limit-Limit} — maximum tokens per window
 *   <li>{@code X-Rate-Limit-Remaining} — tokens remaining after this request
 *   <li>{@code X-Rate-Limit-Retry-After-Seconds} — seconds until next token is available (only
 *       present on 429 responses)
 * </ul>
 *
 * @author Open Finance Team
 * @version 2.0
 * @since 2026-03-19
 * @see RateLimitConfig
 * @see WebMvcConfig
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RateLimitInterceptor implements HandlerInterceptor {

    /** URI prefixes that receive the stricter sensitive-endpoint rate limit. */
    private static final String[] SENSITIVE_PREFIXES = {"/api/v1/auth/", "/api/v1/files/"};

    private final RateLimitConfig rateLimitConfig;

    /**
     * Intercepts each request, resolves the appropriate bucket for the calling principal, and
     * either allows the request through or rejects it with HTTP 429.
     *
     * <p>Sensitive paths ({@code /api/v1/auth/**}, {@code /api/v1/files/**}) use a stricter per-IP
     * bucket. All other paths use the general per-principal bucket. Requirement TASK-15.1.5.
     *
     * @param request the current HTTP request
     * @param response the current HTTP response
     * @param handler the chosen handler (unused)
     * @return {@code true} if the request is within the rate limit; {@code false} if the request
     *     has been rejected
     */
    @Override
    public boolean preHandle(
            HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {

        String uri = request.getRequestURI();
        boolean isSensitive = isSensitivePath(uri);

        Bucket bucket;
        int limitHeaderValue;
        if (isSensitive) {
            // Requirement TASK-15.1.5: always key sensitive endpoints by IP to block
            // brute-force before any authentication occurs.
            String ipKey = resolveIpKey(request);
            bucket = rateLimitConfig.resolveSensitiveBucket(ipKey);
            limitHeaderValue = RateLimitConfig.SENSITIVE_REQUESTS_PER_MINUTE;
        } else {
            String principalKey = resolvePrincipalKey(request);
            bucket = rateLimitConfig.resolveBucket(principalKey);
            limitHeaderValue = RateLimitConfig.REQUESTS_PER_MINUTE + RateLimitConfig.BURST_CAPACITY;
        }

        ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);

        response.addHeader("X-Rate-Limit-Limit", String.valueOf(limitHeaderValue));
        response.addHeader("X-Rate-Limit-Remaining", String.valueOf(probe.getRemainingTokens()));

        if (probe.isConsumed()) {
            return true;
        }

        long waitSeconds = probe.getNanosToWaitForRefill() / 1_000_000_000L;
        response.addHeader("X-Rate-Limit-Retry-After-Seconds", String.valueOf(waitSeconds));
        response.sendError(
                HttpStatus.TOO_MANY_REQUESTS.value(),
                "Too many requests — please retry after " + waitSeconds + " second(s).");

        log.warn(
                "Rate limit exceeded [{}] on {} {} — retry after {}s",
                isSensitive ? "sensitive" : "general",
                request.getMethod(),
                uri,
                waitSeconds);

        return false;
    }

    /**
     * Returns {@code true} when the request URI matches one of the sensitive path prefixes that
     * warrant stricter rate limiting (Requirement TASK-15.1.5).
     *
     * @param uri the request URI
     * @return {@code true} if the path is considered sensitive
     */
    private boolean isSensitivePath(String uri) {
        return Arrays.stream(SENSITIVE_PREFIXES).anyMatch(uri::startsWith);
    }

    /**
     * Resolves the remote IP address from the request, honouring the {@code X-Forwarded-For} header
     * when present (first hop).
     *
     * @param request the current HTTP request
     * @return a non-null IP string
     */
    private String resolveIpKey(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        return "ip:" + (xff != null ? xff.split(",")[0].trim() : request.getRemoteAddr());
    }

    /**
     * Resolves the principal key used for general bucket lookup.
     *
     * <p>Returns the authenticated username when a valid {@link Authentication} is present in the
     * {@link SecurityContextHolder}; otherwise falls back to the remote IP address.
     *
     * @param request the current HTTP request
     * @return a non-null string that uniquely identifies the calling principal
     */
    private String resolvePrincipalKey(HttpServletRequest request) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null
                && authentication.isAuthenticated()
                && !"anonymousUser".equals(authentication.getPrincipal())) {
            return "user:" + authentication.getName();
        }
        return resolveIpKey(request);
    }
}
