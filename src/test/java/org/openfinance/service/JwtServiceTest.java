package org.openfinance.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.openfinance.entity.User;

/**
 * Unit tests for JwtService.
 *
 * <p>Tests JWT token generation, validation, and claim extraction.
 *
 * @author Open-Finance Development Team
 * @since 2026-01-30
 */
@DisplayName("JwtService Unit Tests")
class JwtServiceTest {

    private JwtService jwtService;
    private static final String TEST_JWT_SECRET =
            "test-secret-key-for-jwt-must-be-at-least-256-bits-long-for-hs256";

    @BeforeEach
    void setUp() {
        jwtService = new JwtService(TEST_JWT_SECRET, Duration.ofDays(31).toMillis());
    }

    @Test
    @DisplayName("Activity extends expiry by 31 days while preserving the login session")
    void activityExtendsSession() {
        Instant loginTime = Instant.parse("2026-11-11T12:00:00Z");
        JwtService login = serviceAt(loginTime);
        String original = login.generateToken(createTestUser(1L, "john_doe"));
        assertThat(login.extractExpiration(original).toInstant())
                .isEqualTo(loginTime.plus(Duration.ofDays(31)));

        JwtService active = serviceAt(loginTime.plus(Duration.ofDays(30)));
        String renewed = active.renewToken(original);
        assertThat(active.extractExpiration(renewed).toInstant())
                .isEqualTo(loginTime.plus(Duration.ofDays(61)));
        assertThat(active.extractSessionId(renewed)).isEqualTo(active.extractSessionId(original));
        assertThat(active.extractUsername(renewed)).isEqualTo("john_doe");
        assertThat(active.extractUserId(renewed)).isEqualTo(1L);
        assertThat(active.extractTokenVersion(renewed))
                .isEqualTo(active.extractTokenVersion(original));
        assertThat(active.sessionRevocationExpiresAt(original))
                .isEqualTo(
                        active.extractExpiration(renewed).getTime()
                                + Duration.ofDays(31).toMillis());

        JwtService later = serviceAt(loginTime.plus(Duration.ofDays(32)));
        assertThat(later.validateToken(original)).isFalse();
        assertThat(later.validateToken(renewed)).isTrue();
        assertThatThrownBy(() -> later.renewToken(original))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(serviceAt(loginTime.plus(Duration.ofDays(62))).validateToken(renewed)).isFalse();
    }

    private JwtService serviceAt(Instant instant) {
        return JwtService.withClock(
                TEST_JWT_SECRET,
                Duration.ofDays(31).toMillis(),
                Clock.fixed(instant, ZoneOffset.UTC));
    }

    @Test
    @DisplayName("Separate logins issue distinct tokens even within the same second")
    void distinctTokensForIndependentLogins() {
        User user = createTestUser(1L, "john_doe");
        String first = jwtService.generateToken(user);
        String second = jwtService.generateToken(user);
        assertThat(first).isNotEqualTo(second);
        assertThat(jwtService.extractSessionId(first))
                .isNotEqualTo(jwtService.extractSessionId(second));
        user.setTokenVersion(7);
        assertThat(jwtService.extractTokenVersion(jwtService.generateToken(user))).isEqualTo(7);
    }

    @Test
    @DisplayName("Should honor the configured jwt.expiration for token lifetime")
    void shouldHonorConfiguredExpiration() {
        // Given a service configured with a non-default expiration
        long customExpirationMs = 60 * 60 * 1000L; // 1 hour
        JwtService service = new JwtService(TEST_JWT_SECRET, customExpirationMs);
        User user = createTestUser(1L, "john_doe");

        // When
        String token = service.generateToken(user);

        // Then the token's (exp - iat) equals the configured expiration, proving the
        // application.yml value drives lifetime instead of a hardcoded constant.
        SecretKey key = Keys.hmacShaKeyFor(TEST_JWT_SECRET.getBytes());
        io.jsonwebtoken.Claims claims =
                Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
        long lifetimeMs = claims.getExpiration().getTime() - claims.getIssuedAt().getTime();
        assertThat(lifetimeMs).isEqualTo(customExpirationMs);
    }

    @Test
    @DisplayName("Should generate valid JWT token for user")
    void shouldGenerateValidTokenForUser() {
        // Given
        User user = createTestUser(1L, "john_doe");

        // When
        String token = jwtService.generateToken(user);

        // Then
        assertThat(token).isNotNull().isNotEmpty();
        assertThat(token.split("\\.")).hasSize(3); // JWT has 3 parts: header.payload.signature
        assertThat(jwtService.validateToken(token)).isTrue();
    }

    @Test
    @DisplayName("Should extract username from token")
    void shouldExtractUsernameFromToken() {
        // Given
        User user = createTestUser(1L, "john_doe");
        String token = jwtService.generateToken(user);

        // When
        String username = jwtService.extractUsername(token);

        // Then
        assertThat(username).isEqualTo("john_doe");
    }

    @Test
    @DisplayName("Should extract user ID from token")
    void shouldExtractUserIdFromToken() {
        // Given
        User user = createTestUser(42L, "john_doe");
        String token = jwtService.generateToken(user);

        // When
        Long userId = jwtService.extractUserId(token);

        // Then
        assertThat(userId).isEqualTo(42L);
    }

    @Test
    @DisplayName("Should extract expiration date from token")
    void shouldExtractExpirationFromToken() {
        // Given
        User user = createTestUser(1L, "john_doe");
        String token = jwtService.generateToken(user);

        // When
        Date expiration = jwtService.extractExpiration(token);

        // Then
        assertThat(expiration).isNotNull();
        assertThat(expiration).isAfter(new Date());
        assertThat(expiration)
                .isBefore(new Date(System.currentTimeMillis() + Duration.ofDays(32).toMillis()));
    }

    @Test
    @DisplayName("Should return false for expired token")
    void shouldReturnFalseForExpiredToken() throws InterruptedException {
        // Given: Create a token that expires immediately
        User user = createTestUser(1L, "john_doe");
        SecretKey key = Keys.hmacShaKeyFor(TEST_JWT_SECRET.getBytes());

        String expiredToken =
                Jwts.builder()
                        .subject(user.getUsername())
                        .claim("userId", user.getId())
                        .issuedAt(new Date(System.currentTimeMillis() - 2000))
                        .expiration(new Date(System.currentTimeMillis() - 1000)) // Expired 1s ago
                        .signWith(key)
                        .compact();

        // When
        boolean isExpired = jwtService.isTokenExpired(expiredToken);

        // Then
        assertThat(isExpired).isTrue();
    }

    @Test
    @DisplayName("Should return false for token with invalid signature")
    void shouldReturnFalseForInvalidSignature() {
        // Given: Token signed with different secret
        User user = createTestUser(1L, "john_doe");
        SecretKey wrongKey =
                Keys.hmacShaKeyFor(
                        "different-secret-key-for-jwt-must-be-at-least-256-bits-long".getBytes());

        String invalidToken =
                Jwts.builder()
                        .subject(user.getUsername())
                        .claim("userId", user.getId())
                        .issuedAt(new Date())
                        .expiration(new Date(System.currentTimeMillis() + 24 * 60 * 60 * 1000))
                        .signWith(wrongKey)
                        .compact();

        // When & Then
        assertThat(jwtService.validateToken(invalidToken)).isFalse();
    }

    @Test
    @DisplayName("Should return false for malformed token")
    void shouldReturnFalseForMalformedToken() {
        // Given
        String malformedToken = "not.a.valid.jwt.token";

        // When & Then
        assertThat(jwtService.validateToken(malformedToken)).isFalse();
    }

    @Test
    @DisplayName("Should return false for null token")
    void shouldReturnFalseForNullToken() {
        // When & Then
        assertThat(jwtService.validateToken(null)).isFalse();
    }

    @Test
    @DisplayName("Should return false for empty token")
    void shouldReturnFalseForEmptyToken() {
        // When & Then
        assertThat(jwtService.validateToken("")).isFalse();
        assertThat(jwtService.validateToken("   ")).isFalse();
    }

    @Test
    @DisplayName("Should throw exception when generating token for null user")
    void shouldThrowExceptionForNullUser() {
        // When & Then
        assertThatThrownBy(() -> jwtService.generateToken(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("User cannot be null");
    }

    @Test
    @DisplayName("Should throw exception when generating token for user with null username")
    void shouldThrowExceptionForNullUsername() {
        // Given
        User user = createTestUser(1L, null);

        // When & Then
        assertThatThrownBy(() -> jwtService.generateToken(user))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("username cannot be null");
    }

    @Test
    @DisplayName("Should throw exception when generating token for user with null ID")
    void shouldThrowExceptionForNullUserId() {
        // Given
        User user = createTestUser(null, "john_doe");

        // When & Then
        assertThatThrownBy(() -> jwtService.generateToken(user))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ID cannot be null");
    }

    @Test
    @DisplayName("Should throw exception when extracting username from null token")
    void shouldThrowExceptionWhenExtractingUsernameFromNullToken() {
        // When & Then
        assertThatThrownBy(() -> jwtService.extractUsername(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cannot be null or blank");
    }

    @Test
    @DisplayName("Should throw exception when extracting username from invalid token")
    void shouldThrowExceptionWhenExtractingUsernameFromInvalidToken() {
        // When & Then
        assertThatThrownBy(() -> jwtService.extractUsername("invalid.token"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid JWT token");
    }

    @Test
    @DisplayName("Legacy tokens without a sid claim get a stable SHA-256 session id")
    void legacyTokenSessionIdFallsBackToTokenHash() throws Exception {
        String legacy = craftToken(Map.of("userId", 7L), null);

        String expected =
                HexFormat.of()
                        .formatHex(
                                MessageDigest.getInstance("SHA-256")
                                        .digest(legacy.getBytes(StandardCharsets.UTF_8)));

        assertThat(jwtService.extractSessionId(legacy)).isEqualTo(expected);
        // Stable across calls and distinct from another token's id
        assertThat(jwtService.extractSessionId(legacy)).isEqualTo(expected);
        String other = craftToken(Map.of("userId", 8L, "jti", UUID.randomUUID().toString()), null);
        assertThat(jwtService.extractSessionId(other)).isNotEqualTo(expected);
    }

    @Test
    @DisplayName("Blank sid claim falls back to the token hash and renewal preserves it")
    void blankSidFallsBackAndSurvivesRenewal() {
        String blankSid = craftToken(Map.of("userId", 7L, "sid", "  "), null);
        String sessionId = jwtService.extractSessionId(blankSid);
        assertThat(sessionId).matches("[0-9a-f]{64}");
        assertThat(jwtService.extractSessionId(jwtService.renewToken(blankSid)))
                .isEqualTo(sessionId);
    }

    @Test
    @DisplayName("Renewing a legacy token embeds its hash session id as a real sid claim")
    void renewalMigratesLegacySessionId() {
        String legacy = craftToken(Map.of("userId", 7L), null);
        String renewed = jwtService.renewToken(legacy);
        assertThat(jwtService.extractSessionId(renewed))
                .isEqualTo(jwtService.extractSessionId(legacy));
    }

    @Test
    @DisplayName("Extracts userId stored as a numeric string claim")
    void extractUserIdFromStringClaim() {
        String token = craftToken(Map.of("userId", "42"), null);
        assertThat(jwtService.extractUserId(token)).isEqualTo(42L);
    }

    @Test
    @DisplayName("Returns null userId when the claim is absent")
    void extractUserIdMissingClaim() {
        String token = craftToken(Map.of("other", "x"), null);
        assertThat(jwtService.extractUserId(token)).isNull();
    }

    @Test
    @DisplayName("Rejects a non-numeric userId claim instead of coercing it")
    void extractUserIdNonNumericClaim() {
        String token = craftToken(Map.of("userId", "not-a-number"), null);
        assertThatThrownBy(() -> jwtService.extractUserId(token))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Tokens issued before versioning report token version 0")
    void tokenVersionDefaultsToZero() {
        String token = craftToken(Map.of("userId", 7L), null);
        assertThat(jwtService.extractTokenVersion(token)).isZero();
    }

    @Test
    @DisplayName("isTokenExpired treats fresh tokens as live and any invalid token as expired")
    void isTokenExpiredSemantics() {
        String fresh = jwtService.generateToken(createTestUser(1L, "john_doe"));
        assertThat(jwtService.isTokenExpired(fresh)).isFalse();
        assertThat(jwtService.isTokenExpired("not.a.token")).isTrue();
        assertThat(jwtService.isTokenExpired(null)).isTrue();
        assertThat(jwtService.isTokenExpired("")).isTrue();

        JwtService later = serviceAt(Instant.now().plus(Duration.ofDays(40)));
        assertThat(later.isTokenExpired(fresh)).isTrue();
    }

    @Test
    @DisplayName("validateToken rejects tampered payloads and tokens signed with another secret")
    void validateTokenRejectsTampering() {
        String token = jwtService.generateToken(createTestUser(1L, "john_doe"));
        String[] parts = token.split("\\.");
        String tamperedPayload =
                parts[0] + "." + parts[1].substring(0, parts[1].length() - 2) + "AA." + parts[2];
        assertThat(jwtService.validateToken(tamperedPayload)).isFalse();

        JwtService otherSecret =
                new JwtService(
                        "another-secret-key-that-is-also-at-least-256-bits-long-for-hs256",
                        Duration.ofDays(31).toMillis());
        assertThat(
                        jwtService.validateToken(
                                otherSecret.generateToken(createTestUser(1L, "john_doe"))))
                .isFalse();
        assertThat(jwtService.validateToken("   ")).isFalse();
    }

    /** Builds a correctly-signed token with raw claims, bypassing generateToken's claim set. */
    private String craftToken(Map<String, Object> claims, String subject) {
        SecretKey key = Keys.hmacShaKeyFor(TEST_JWT_SECRET.getBytes(StandardCharsets.UTF_8));
        return Jwts.builder()
                .claims(claims)
                .subject(subject == null ? "john_doe" : subject)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + Duration.ofDays(1).toMillis()))
                .signWith(key)
                .compact();
    }

    // Helper methods

    private User createTestUser(Long id, String username) {
        return User.builder()
                .id(id)
                .username(username)
                .email("test@example.com")
                .passwordHash("hashedPassword")
                .masterPasswordSalt("saltBase64")
                .build();
    }
}
