package org.openfinance.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Date;
import org.junit.jupiter.api.Test;
import org.openfinance.security.JwtAuthenticationFilter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** Logout and both password-change routes must revoke real authenticated access. */
@TestPropertySource(properties = "jwt.expiration=2678400000")
class AuditSessionRevocationIntegrationTest extends AuditApiTestSupport {
    @Autowired private JwtService jwtService;

    @Value("${jwt.secret}")
    private String jwtSecret;

    @Test
    void logoutRevokesTheWholeLoginSessionIncludingRenewals() throws Exception {
        Auth second = login("LoginPassword123!");
        assertThat(second.token()).isNotEqualTo(owner.token());
        Auth aging = agedToken(Duration.ofDays(30), Duration.ofDays(1));
        Auth renewed = renew(aging);
        json("POST", "/auth/logout", null, aging, 204);
        // Purging at the old token's expiry must not resurrect its longer-lived renewal.
        jdbc.update(
                "DELETE FROM revoked_tokens WHERE expires_at <= ?",
                jwtService.extractExpiration(aging.token()).getTime());
        assertRevoked(aging);
        assertRevoked(renewed);
        json("GET", "/users/me", null, second, 200);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM revoked_tokens", Long.class))
                .isPositive();
    }

    @Test
    void profilePasswordChangeRevokesExistingLoginsAndAllowsNewCredentials() throws Exception {
        Auth second = login("LoginPassword123!");
        Auth renewed = renew(owner);
        json(
                "PUT",
                "/auth/profile",
                data("currentPassword", "LoginPassword123!", "newPassword", "ChangedPassword456!"),
                second,
                200);
        assertRevoked(owner);
        assertRevoked(second);
        assertRevoked(renewed);
        json("GET", "/users/me", null, login("ChangedPassword456!"), 200);
    }

    @Test
    void loginAndActivityGrant31Days() throws Exception {
        Claims initial = claims(owner.token());
        assertThat(initial.getExpiration().getTime() - initial.getIssuedAt().getTime())
                .isEqualTo(Duration.ofDays(31).toMillis());
        Auth aging = agedToken(Duration.ofDays(30), Duration.ofDays(1));
        long before = System.currentTimeMillis();
        Auth renewed = renew(aging);
        assertThat(jwtService.extractExpiration(renewed.token()).getTime())
                .isBetween(
                        before + Duration.ofDays(31).toMillis() - 1000,
                        System.currentTimeMillis() + Duration.ofDays(31).toMillis());
        assertThat(jwtService.extractSessionId(renewed.token()))
                .isEqualTo(jwtService.extractSessionId(aging.token()));
        json("GET", "/users/me/settings", null, renewed, 200);
    }

    @Test
    void expiredSessionCannotRenewItself() throws Exception {
        Auth expired = agedToken(Duration.ofDays(32), Duration.ofSeconds(-5));
        mvc.perform(settingsRequest(expired))
                .andExpect(status().isUnauthorized())
                .andExpect(header().doesNotExist(JwtAuthenticationFilter.RENEWED_TOKEN_HEADER));
    }

    private Auth renew(Auth auth) throws Exception {
        MockHttpServletResponse response =
                mvc.perform(
                                settingsRequest(auth)
                                        .header(HttpHeaders.ORIGIN, "http://localhost:3000"))
                        .andExpect(status().isOk())
                        .andExpect(header().exists(JwtAuthenticationFilter.RENEWED_TOKEN_HEADER))
                        .andExpect(
                                header().string(
                                                HttpHeaders.ACCESS_CONTROL_EXPOSE_HEADERS,
                                                containsString(
                                                        JwtAuthenticationFilter
                                                                .RENEWED_TOKEN_HEADER)))
                        .andReturn()
                        .getResponse();
        return new Auth(
                auth.id(),
                response.getHeader(JwtAuthenticationFilter.RENEWED_TOKEN_HEADER),
                auth.session());
    }

    private MockHttpServletRequestBuilder settingsRequest(Auth auth) {
        MockHttpServletRequestBuilder request =
                get("/api/v1/users/me/settings")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + auth.token());
        if (auth.session() != null) request.header("X-Encryption-Session", auth.session());
        return request;
    }

    private Auth agedToken(Duration age, Duration remaining) {
        String token =
                Jwts.builder()
                        .claims(claims(owner.token()))
                        .issuedAt(new Date(System.currentTimeMillis() - age.toMillis()))
                        .expiration(new Date(System.currentTimeMillis() + remaining.toMillis()))
                        .signWith(Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8)))
                        .compact();
        return new Auth(owner.id(), token, owner.session());
    }

    private Claims claims(String token) {
        return Jwts.parser()
                .verifyWith(Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8)))
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    private Auth login(String password) throws Exception {
        String username =
                jdbc.queryForObject(
                        "SELECT username FROM users WHERE id = ?", String.class, owner.id());
        JsonNode response =
                json(
                        "POST",
                        "/auth/login",
                        data("username", username, "password", password, "masterPassword", MASTER),
                        null,
                        200);
        return new Auth(
                owner.id(),
                response.path("token").asText(),
                response.path("encryptionKey").asText(null));
    }

    private void assertRevoked(Auth auth) throws Exception {
        org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request =
                get("/api/v1/users/me").header("Authorization", "Bearer " + auth.token());
        if (auth.session() != null) request.header("X-Encryption-Session", auth.session());
        mvc.perform(request)
                .andExpect(status().isUnauthorized())
                .andExpect(header().doesNotExist(JwtAuthenticationFilter.RENEWED_TOKEN_HEADER));
    }
}
