package org.openfinance.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

/** Logout and both password-change routes must revoke real authenticated access. */
class AuditSessionRevocationIntegrationTest extends AuditApiTestSupport {
    @Test
    void logoutRevokesOnlyThePresentedJwt() throws Exception {
        Auth second = login("LoginPassword123!");
        assertThat(second.token()).isNotEqualTo(owner.token());
        json("POST", "/auth/logout", null, owner, 204);
        assertRevoked(owner);
        json("GET", "/users/me", null, second, 200);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM revoked_tokens", Long.class))
                .isPositive();
    }

    @Test
    void profilePasswordChangeRevokesExistingLoginsAndAllowsNewCredentials() throws Exception {
        Auth second = login("LoginPassword123!");
        json(
                "PUT",
                "/auth/profile",
                data("currentPassword", "LoginPassword123!", "newPassword", "ChangedPassword456!"),
                second,
                200);
        assertRevoked(owner);
        assertRevoked(second);
        json("GET", "/users/me", null, login("ChangedPassword456!"), 200);
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
        int status = mvc.perform(request).andReturn().getResponse().getStatus();
        assertThat(status).isIn(401, 403);
    }
}
