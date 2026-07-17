// filepath: src/main/java/org/openfinance/security/EncryptionKeyFilter.java

package org.openfinance.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Optional;
import javax.crypto.SecretKey;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.openfinance.config.EncryptionProperties;
import org.springframework.lang.NonNull;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Servlet filter that resolves the encryption key for the current request.
 *
 * <p>The client sends an opaque session token (obtained at login) in the {@code
 * X-Encryption-Session} header. This filter looks up the real AES key from {@link
 * EncryptionKeyCache} and stores it in {@link EncryptionContext} for the duration of the request.
 * The actual encryption key never transits over the wire after login.
 *
 * <p>Registered in the filter chain after {@link JwtAuthenticationFilter} so that the authenticated
 * user ID is available.
 */
@Slf4j
@RequiredArgsConstructor
public class EncryptionKeyFilter extends OncePerRequestFilter {

    private static final String SESSION_HEADER = "X-Encryption-Session";

    private final EncryptionKeyCache encryptionKeyCache;

    private final EncryptionProperties encryptionProperties;

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain)
            throws ServletException, IOException {

        EncryptionContext.clear();

        try {
            if (!encryptionProperties.isEnabled()) {
                filterChain.doFilter(request, response);
                return;
            }

            if (isPublicEndpoint(request)) {
                filterChain.doFilter(request, response);
                return;
            }

            Long requestUserId = resolveRequestUserId(request);
            if (requestUserId == null) {
                filterChain.doFilter(request, response);
                return;
            }

            String sessionToken = request.getHeader(SESSION_HEADER);
            if (sessionToken == null || sessionToken.isBlank()) {
                rejectMissingSession(response);
                return;
            }

            Optional<SecretKey> keyOpt = encryptionKeyCache.getKeyBySessionToken(sessionToken);
            if (keyOpt.isPresent()) {
                Optional<Long> sessionUserId =
                        encryptionKeyCache.getUserIdBySessionToken(sessionToken);
                if (sessionUserId.isEmpty() || !sessionUserId.get().equals(requestUserId)) {
                    log.warn("Encryption session token does not belong to authenticated user");
                    rejectInvalidSession(response);
                    return;
                }
                EncryptionContext.setKey(keyOpt.get());
            } else {
                log.warn("Invalid or expired encryption session token");
                rejectInvalidSession(response);
                return;
            }

            filterChain.doFilter(request, response);
        } finally {
            EncryptionContext.clear();
        }
    }

    private Long resolveRequestUserId(HttpServletRequest request) {
        Object userIdAttr = request.getAttribute("userId");
        return userIdAttr instanceof Long userId ? userId : null;
    }

    private boolean isPublicEndpoint(HttpServletRequest request) {
        String path = request.getServletPath();
        if (path == null || path.isBlank()) {
            path = request.getRequestURI();
        }
        return "/api/v1/auth/register".equals(path)
                || "/api/v1/auth/login".equals(path)
                || "/api/v1/config/security".equals(path)
                || "/api/v1/ai/health".equals(path)
                || "/api/v1/health".equals(path)
                || path.startsWith("/api/v1/health/")
                || "/actuator/health".equals(path)
                || "/actuator/info".equals(path)
                || "/swagger-ui.html".equals(path)
                || path.startsWith("/swagger-ui/")
                || "/v3/api-docs".equals(path)
                || path.startsWith("/v3/api-docs/")
                || path.startsWith("/swagger-resources/")
                || path.startsWith("/webjars/");
    }

    private void rejectMissingSession(HttpServletResponse response) throws IOException {
        response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Encryption session required");
    }

    private void rejectInvalidSession(HttpServletResponse response) throws IOException {
        response.sendError(
                HttpServletResponse.SC_UNAUTHORIZED, "Invalid or expired encryption session");
    }
}
