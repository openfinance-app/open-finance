package org.openfinance.service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.UnsupportedJwtException;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.SignatureException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.util.Date;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import javax.crypto.SecretKey;
import lombok.extern.slf4j.Slf4j;
import org.openfinance.entity.User;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Service for JWT token generation and validation.
 *
 * <p>Provides methods to create JWTs with user claims, validate token signatures, and extract user
 * information from tokens. The sliding session lifetime is configurable via {@code jwt.expiration}
 * (default 31 days). Authenticated requests receive a renewed token for the same login session.
 *
 * <p>Requirement REQ-2.1.3: JWT-based authentication with configurable token expiration
 *
 * @author Open-Finance Development Team
 * @version 1.0
 * @since 2026-01-30
 */
@Slf4j
@Service
public class JwtService {

    private final SecretKey signingKey;
    private final long jwtExpirationMs;
    private final Clock clock;

    /**
     * Constructs JwtService with signing key and token lifetime from configuration.
     *
     * <p>The configured property {@code jwt.secret} must provide sufficient entropy (>= 256 bits)
     * for the HS256 algorithm. The value is used as raw bytes; if you store the secret as
     * Base64-encoded string, decode it before assigning to the property.
     *
     * @param jwtSecret secret key string (raw, not automatically Base64-decoded)
     * @param jwtExpirationMs token lifetime in milliseconds, from {@code jwt.expiration} (defaults
     *     to 31 days if the property is absent)
     */
    @Autowired
    public JwtService(
            @Value("${jwt.secret}") String jwtSecret,
            @Value("${jwt.expiration:2678400000}") long jwtExpirationMs) {
        this(
                Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8)),
                jwtExpirationMs,
                Clock.systemUTC());
    }

    static JwtService withClock(String jwtSecret, long jwtExpirationMs, Clock clock) {
        return new JwtService(
                Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8)),
                jwtExpirationMs,
                clock);
    }

    private JwtService(SecretKey signingKey, long jwtExpirationMs, Clock clock) {
        this.signingKey = signingKey;
        this.jwtExpirationMs = jwtExpirationMs;
        this.clock = clock;
        // log at debug to avoid logging environment sensitive initialization in production logs
        log.debug("JwtService initialized with HS256 algorithm, expiration={}ms", jwtExpirationMs);
    }

    /**
     * Generates a JWT token for the given user.
     *
     * <p>Token includes claims: - sub (subject): username - userId: user's database ID - iat
     * (issued at): current timestamp - exp (expiration): current timestamp + configured lifetime -
     * sid: stable login session identifier, retained across renewals
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
        claims.put("tokenVersion", user.getTokenVersion());
        claims.put("sid", UUID.randomUUID().toString());

        return issueToken(claims, user.getUsername());
    }

    /** Renew only a still-valid token, retaining its identity and credential version. */
    public String renewToken(String token) {
        Claims claims = extractClaim(token, Function.identity());
        Map<String, Object> renewedClaims = new HashMap<>(claims);
        renewedClaims.put("sid", sessionId(token, claims));
        return issueToken(renewedClaims, claims.getSubject());
    }

    private String issueToken(Map<String, Object> claims, String username) {
        Date now = Date.from(clock.instant());
        Date expiration = new Date(now.getTime() + jwtExpirationMs);

        String token =
                Jwts.builder()
                        .claims(claims)
                        .id(UUID.randomUUID().toString())
                        .subject(username)
                        .issuedAt(now)
                        .expiration(expiration)
                        .signWith(signingKey)
                        .compact();

        log.debug("Generated JWT token for user: {}", username);
        return token;
    }

    /** All renewed tokens from one login share a revocation key. */
    public String extractSessionId(String token) {
        return extractClaim(token, claims -> sessionId(token, claims));
    }

    private String sessionId(String token, Claims claims) {
        String sessionId = claims.get("sid", String.class);
        if (sessionId != null && !sessionId.isBlank()) return sessionId;
        // Preserve logout revocations for tokens issued before sliding sessions existed.
        try {
            return HexFormat.of()
                    .formatHex(
                            MessageDigest.getInstance("SHA-256")
                                    .digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is required by the JVM", ex);
        }
    }

    /**
     * An older token can log out a session with newer tokens. Retain revocation for two lifetimes:
     * an in-flight request may have validated just before logout and finish renewal afterwards.
     */
    public long sessionRevocationExpiresAt(String token) {
        return Math.max(extractExpiration(token).getTime(), clock.millis() + jwtExpirationMs)
                + jwtExpirationMs;
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
            Jwts.parser()
                    .verifyWith(signingKey)
                    .clock(() -> Date.from(clock.instant()))
                    .build()
                    .parseSignedClaims(token);
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

    /** Tokens issued before versioning belong to the initial credential version. */
    public long extractTokenVersion(String token) {
        return extractClaim(
                token,
                claims -> {
                    Number version = claims.get("tokenVersion", Number.class);
                    return version == null ? 0L : version.longValue();
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
            return !expiration.after(Date.from(clock.instant()));
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
                            .clock(() -> Date.from(clock.instant()))
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
