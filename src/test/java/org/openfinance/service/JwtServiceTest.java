package org.openfinance.service;

import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.*;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.util.Date;
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
        jwtService = new JwtService(TEST_JWT_SECRET);
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
                .isBefore(new Date(System.currentTimeMillis() + 25 * 60 * 60 * 1000)); // < 25h
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
