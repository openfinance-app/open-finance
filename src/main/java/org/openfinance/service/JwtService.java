package org.openfinance.service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.UnsupportedJwtException;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.SignatureException;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;
import javax.crypto.SecretKey;
import lombok.extern.slf4j.Slf4j;
import org.openfinance.entity.User;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Service for JWT token generation and validation.
 *
 * <p>Provides methods to create JWTs with user claims, validate token signatures, and extract user
 * information from tokens. Tokens expire after 24 hours.
 *
 * <p>Requirement REQ-2.1.3: JWT-based authentication with 24-hour token expiration
 *
 * @author Open-Finance Development Team
 * @version 1.0
 * @since 2026-01-30
 */
@Slf4j
@Service
public class JwtService {

    private static final long JWT_EXPIRATION_MS = 24 * 60 * 60 * 1000; // 24 hours

    private final SecretKey signingKey;

    /**
     * Constructs JwtService with signing key from configuration.
     *
     * <p>The configured property {@code jwt.secret} must provide sufficient entropy (>= 256 bits)
     * for the HS256 algorithm. The value is used as raw bytes; if you store the secret as
     * Base64-encoded string, decode it before assigning to the property.
     *
     * @param jwtSecret secret key string (raw, not automatically Base64-decoded)
     */
    public JwtService(@Value("${jwt.secret}") String jwtSecret) {
        this.signingKey = Keys.hmacShaKeyFor(jwtSecret.getBytes());
        // log at debug to avoid logging environment sensitive initialization in production logs
        log.debug("JwtService initialized with HS256 algorithm");
    }

    /**
     * Generates a JWT token for the given user.
     *
     * <p>Token includes claims: - sub (subject): username - userId: user's database ID - iat
     * (issued at): current timestamp - exp (expiration): current timestamp + 24 hours
     *
     * @param user User entity to generate token for
     * @return JWT token string
     * @throws IllegalArgumentException if user is null or has null username/id
     */
    public String generateToken(User user) {
        if (user == null) {
            throw new IllegalArgumentException("User cannot be null");
        }
        if (user.getUsername() == null || user.getUsername().isBlank()) {
            throw new IllegalArgumentException("User username cannot be null or blank");
        }
        if (user.getId() == null) {
            throw new IllegalArgumentException("User ID cannot be null");
        }

        Map<String, Object> claims = new HashMap<>();
        claims.put("userId", user.getId());

        Date now = new Date();
        Date expiration = new Date(now.getTime() + JWT_EXPIRATION_MS);

        String token =
                Jwts.builder()
                        .claims(claims)
                        .subject(user.getUsername())
                        .issuedAt(now)
                        .expiration(expiration)
                        .signWith(signingKey)
                        .compact();

        log.debug("Generated JWT token for user: {}", user.getUsername());
        return token;
    }

    /**
     * Validates JWT token signature and expiration.
     *
     * @param token JWT token string
     * @return true if token is valid, false otherwise
     */
    public boolean validateToken(String token) {
        if (token == null || token.isBlank()) {
            log.debug("Token validation failed: token is null or blank");
            return false;
        }

        try {
            Jwts.parser().verifyWith(signingKey).build().parseSignedClaims(token);
            log.debug("Token validation successful");
            return true;
        } catch (SignatureException e) {
            // Signature failures are expected for invalid tokens; log at debug to avoid noise
            log.debug("Invalid JWT signature", e);
        } catch (MalformedJwtException e) {
            log.debug("Invalid JWT token format", e);
        } catch (ExpiredJwtException e) {
            log.debug("JWT token expired", e);
        } catch (UnsupportedJwtException e) {
            log.debug("Unsupported JWT token", e);
        } catch (IllegalArgumentException e) {
            log.debug("JWT claims string is empty or token is invalid", e);
        }

        return false;
    }

    /**
     * Extracts username (subject) from JWT token.
     *
     * @param token JWT token string
     * @return Username from token subject claim
     * @throws IllegalArgumentException if token is invalid
     */
    public String extractUsername(String token) {
        return extractClaim(token, Claims::getSubject);
    }

    /**
     * Extracts user ID from JWT token.
     *
     * @param token JWT token string
     * @return User ID from token claims
     * @throws IllegalArgumentException if token is invalid
     */
    public Long extractUserId(String token) {
        // JWT libraries may parse numeric claims as Integer or Long depending on the value.
        // Read as Number and convert to Long to be robust.
        return extractClaim(
                token,
                claims -> {
                    Object raw = claims.get("userId");
                    if (raw == null) {
                        return null;
                    }
                    if (raw instanceof Number) {
                        return ((Number) raw).longValue();
                    }
                    // Fallback: try parsing as string
                    try {
                        return Long.parseLong(raw.toString());
                    } catch (NumberFormatException e) {
                        throw new IllegalArgumentException("Invalid userId claim type", e);
                    }
                });
    }

    /**
     * Extracts expiration date from JWT token.
     *
     * @param token JWT token string
     * @return Expiration date
     * @throws IllegalArgumentException if token is invalid
     */
    public Date extractExpiration(String token) {
        return extractClaim(token, Claims::getExpiration);
    }

    /**
     * Checks if JWT token is expired.
     *
     * @param token JWT token string
     * @return true if token is expired, false otherwise
     */
    public boolean isTokenExpired(String token) {
        try {
            Date expiration = extractExpiration(token);
            return expiration.before(new Date());
        } catch (Exception e) {
            log.debug("Error checking token expiration: {}", e.getMessage());
            return true; // Treat invalid tokens as expired
        }
    }

    /**
     * Extracts a specific claim from JWT token using the provided resolver function.
     *
     * @param <T> Type of the claim value
     * @param token JWT token string
     * @param claimsResolver Function to extract specific claim
     * @return Extracted claim value
     * @throws IllegalArgumentException if token is invalid
     */
    private <T> T extractClaim(String token, Function<Claims, T> claimsResolver) {
        if (token == null || token.isBlank()) {
            throw new IllegalArgumentException("Token cannot be null or blank");
        }

        try {
            Claims claims =
                    Jwts.parser()
                            .verifyWith(signingKey)
                            .build()
                            .parseSignedClaims(token)
                            .getPayload();
            return claimsResolver.apply(claims);
        } catch (Exception e) {
            // Keep detailed stack traces at debug level to avoid leaking token internals in logs
            log.debug("Error extracting claim from token", e);
            throw new IllegalArgumentException("Invalid JWT token", e);
        }
    }
}
