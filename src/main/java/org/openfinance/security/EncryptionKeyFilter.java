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
import org.openfinance.util.EncryptionUtil;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Servlet filter that resolves the encryption key for the current request.
 *
 * <p>
 * The client sends an opaque session token (obtained at login) in the
 * {@code X-Encryption-Session} header. This filter looks up the real AES key
 * from {@link EncryptionKeyCache} and stores it in {@link EncryptionContext}
 * for the duration of the request. The actual encryption key never transits
 * over the wire after login.
 *
 * <p>
 * Registered in the filter chain after {@link JwtAuthenticationFilter} so
 * that the authenticated user ID is available.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EncryptionKeyFilter extends OncePerRequestFilter {

    private static final String SESSION_HEADER = "X-Encryption-Session";

    private final EncryptionKeyCache encryptionKeyCache;

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain)
            throws ServletException, IOException {

        String sessionToken = request.getHeader(SESSION_HEADER);

        if (sessionToken != null && !sessionToken.isBlank()) {
            Optional<SecretKey> keyOpt = encryptionKeyCache.getKeyBySessionToken(sessionToken);
            if (keyOpt.isPresent()) {
                EncryptionContext.setKey(keyOpt.get());
            } else if (looksLikeLegacyRawKey(sessionToken)) {
                SecretKey legacyKey = EncryptionUtil.decodeEncryptionKey(sessionToken);
                EncryptionContext.setKey(legacyKey);

                Object userIdAttr = request.getAttribute("userId");
                if (userIdAttr instanceof Long userId) {
                    encryptionKeyCache.cacheKey(userId, legacyKey);
                }

                log.debug("Accepted legacy raw encryption key header for backward compatibility");
            } else {
                log.warn("Invalid or expired encryption session token");
            }
        }

        try {
            filterChain.doFilter(request, response);
        } finally {
            EncryptionContext.clear();
        }
    }

    private boolean looksLikeLegacyRawKey(String headerValue) {
        return headerValue.indexOf('=') >= 0
                || headerValue.indexOf('+') >= 0
                || headerValue.indexOf('/') >= 0;
    }
}
