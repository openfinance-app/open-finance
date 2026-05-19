package org.openfinance.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Spring MVC configuration for Open-Finance.
 *
 * <p>Registers application-level MVC interceptors, including the {@link RateLimitInterceptor} which
 * enforces per-principal API rate limits on all {@code /api/**} endpoints.
 *
 * <p>Excluded paths (rate limiting not applied):
 *
 * <ul>
 *   <li>{@code /api/v1/auth/login} — unauthenticated login is rate-limited only by IP via the
 *       interceptor's anonymous fallback
 *   <li>{@code /api/v1/auth/register} — same as above
 *   <li>{@code /api/v1/health/**} — health-check probes must never be throttled
 * </ul>
 *
 * @author Open Finance Team
 * @version 1.0
 * @since 2026-03-19
 * @see RateLimitInterceptor
 */
@Configuration
@RequiredArgsConstructor
@org.springframework.context.annotation.Profile("!test")
public class WebMvcConfig implements WebMvcConfigurer {

    private final RateLimitInterceptor rateLimitInterceptor;

    /**
     * Registers the {@link RateLimitInterceptor} for all {@code /api/**} paths, excluding
     * health-check endpoints.
     *
     * @param registry the interceptor registry provided by Spring MVC
     */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(rateLimitInterceptor)
                .addPathPatterns("/api/**")
                .excludePathPatterns("/api/v1/health/**");
    }
}
