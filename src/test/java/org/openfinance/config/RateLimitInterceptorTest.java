package org.openfinance.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

@ExtendWith(MockitoExtension.class)
class RateLimitInterceptorTest {

    @Mock private RateLimitConfig rateLimitConfig;

    @Mock private HttpServletRequest request;

    @Mock private HttpServletResponse response;

    @Mock private Bucket bucket;

    @Mock private ConsumptionProbe probe;

    @Mock private SecurityContext securityContext;

    @Mock private Authentication authentication;

    @InjectMocks private RateLimitInterceptor rateLimitInterceptor;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.setContext(securityContext);
    }

    @Test
    @DisplayName("should allow request when within rate limit")
    void shouldAllowRequestWhenWithinRateLimit() throws Exception {
        // Given
        when(request.getRequestURI()).thenReturn("/api/v1/assets");
        when(securityContext.getAuthentication()).thenReturn(authentication);
        when(authentication.isAuthenticated()).thenReturn(true);
        when(authentication.getPrincipal()).thenReturn("user");
        when(authentication.getName()).thenReturn("testuser");

        when(rateLimitConfig.resolveBucket("user:testuser")).thenReturn(bucket);
        when(bucket.tryConsumeAndReturnRemaining(1)).thenReturn(probe);
        when(probe.isConsumed()).thenReturn(true);
        when(probe.getRemainingTokens()).thenReturn(10L);

        // When
        boolean result = rateLimitInterceptor.preHandle(request, response, new Object());

        // Then
        assertThat(result).isTrue();
        verify(response)
                .addHeader(
                        "X-Rate-Limit-Limit",
                        String.valueOf(
                                RateLimitConfig.REQUESTS_PER_MINUTE
                                        + RateLimitConfig.BURST_CAPACITY));
        verify(response).addHeader("X-Rate-Limit-Remaining", "10");
        verify(response, never()).sendError(anyInt(), anyString());
    }

    @Test
    @DisplayName("should reject request when exceeding rate limit")
    void shouldRejectRequestWhenExceedingRateLimit() throws Exception {
        // Given
        when(request.getRequestURI()).thenReturn("/api/v1/assets");
        when(securityContext.getAuthentication()).thenReturn(null);
        when(request.getRemoteAddr()).thenReturn("127.0.0.1");

        when(rateLimitConfig.resolveBucket("ip:127.0.0.1")).thenReturn(bucket);
        when(bucket.tryConsumeAndReturnRemaining(1)).thenReturn(probe);
        when(probe.isConsumed()).thenReturn(false);
        when(probe.getRemainingTokens()).thenReturn(0L);
        when(probe.getNanosToWaitForRefill()).thenReturn(5_000_000_000L); // 5 seconds

        // When
        boolean result = rateLimitInterceptor.preHandle(request, response, new Object());

        // Then
        assertThat(result).isFalse();
        verify(response).addHeader("X-Rate-Limit-Remaining", "0");
        verify(response).addHeader("X-Rate-Limit-Retry-After-Seconds", "5");
        verify(response).sendError(eq(HttpStatus.TOO_MANY_REQUESTS.value()), anyString());
    }

    @Test
    @DisplayName("should use sensitive bucket for auth endpoints")
    void shouldUseSensitiveBucketForAuthEndpoints() throws Exception {
        // Given
        when(request.getRequestURI()).thenReturn("/api/v1/auth/login");
        when(request.getRemoteAddr()).thenReturn("127.0.0.1");

        when(rateLimitConfig.resolveSensitiveBucket("ip:127.0.0.1")).thenReturn(bucket);
        when(bucket.tryConsumeAndReturnRemaining(1)).thenReturn(probe);
        when(probe.isConsumed()).thenReturn(true);
        when(probe.getRemainingTokens()).thenReturn(9L);

        // When
        boolean result = rateLimitInterceptor.preHandle(request, response, new Object());

        // Then
        assertThat(result).isTrue();
        verify(rateLimitConfig).resolveSensitiveBucket("ip:127.0.0.1");
        verify(response)
                .addHeader(
                        "X-Rate-Limit-Limit",
                        String.valueOf(RateLimitConfig.SENSITIVE_REQUESTS_PER_MINUTE));
    }

    @Test
    @DisplayName("should use sensitive bucket for file endpoints")
    void shouldUseSensitiveBucketForFileEndpoints() throws Exception {
        // Given
        when(request.getRequestURI()).thenReturn("/api/v1/files/upload");
        when(request.getRemoteAddr()).thenReturn("127.0.0.1");

        when(rateLimitConfig.resolveSensitiveBucket("ip:127.0.0.1")).thenReturn(bucket);
        when(bucket.tryConsumeAndReturnRemaining(1)).thenReturn(probe);
        when(probe.isConsumed()).thenReturn(true);

        // When
        rateLimitInterceptor.preHandle(request, response, new Object());

        // Then
        verify(rateLimitConfig).resolveSensitiveBucket("ip:127.0.0.1");
    }

    @Test
    @DisplayName("should handle X-Forwarded-For header")
    void shouldHandleXForwardedForHeader() throws Exception {
        // Given
        when(request.getRequestURI()).thenReturn("/api/v1/assets");
        when(securityContext.getAuthentication()).thenReturn(null);
        when(request.getHeader("X-Forwarded-For")).thenReturn("10.0.0.1, 192.168.1.1");

        when(rateLimitConfig.resolveBucket("ip:10.0.0.1")).thenReturn(bucket);
        when(bucket.tryConsumeAndReturnRemaining(1)).thenReturn(probe);
        when(probe.isConsumed()).thenReturn(true);

        // When
        rateLimitInterceptor.preHandle(request, response, new Object());

        // Then
        verify(rateLimitConfig).resolveBucket("ip:10.0.0.1");
    }
}
