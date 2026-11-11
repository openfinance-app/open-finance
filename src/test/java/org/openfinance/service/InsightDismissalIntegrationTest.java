package org.openfinance.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Bulk insight dismissal preserves ownership and stored content")
class InsightDismissalIntegrationTest extends AuditApiTestSupport {
    @Test
    void dismissesBeyondTheTopThreeWithoutTouchingAnotherUser() throws Exception {
        Auth other = register();
        for (int index = 0; index < 5; index++) insertInsight(owner, "owner:" + index, false);
        insertInsight(owner, "already-dismissed", true);
        insertInsight(other, "other-user", false);
        json("POST", "/insights/dismiss-all?userId=" + other.id(), null, owner, 204);
        assertThat(activeCount(owner)).isZero();
        assertThat(activeCount(other)).isEqualTo(1);
        assertThat(
                        jdbc.queryForObject(
                                "SELECT COUNT(*) FROM insights WHERE user_id = ?",
                                Integer.class,
                                owner.id()))
                .isEqualTo(6);
        assertThat(
                        jdbc.queryForList(
                                "SELECT title FROM insights WHERE user_id = ?",
                                String.class,
                                owner.id()))
                .containsOnly("Stored content must not be rewritten");
        // Idempotent when there are no remaining active insights.
        json("POST", "/insights/dismiss-all", null, owner, 204);
        assertThat(activeCount(other)).isEqualTo(1);
    }

    @Test
    void requiresAuthentication() throws Exception {
        insertInsight(owner, "private", false);
        json("POST", "/insights/dismiss-all", null, null, 401);
        assertThat(activeCount(owner)).isEqualTo(1);
    }

    private void insertInsight(Auth user, String sourceKey, boolean dismissed) {
        // A bulk state update must not read or transform the encrypted content columns.
        jdbc.update(
                "INSERT INTO insights (user_id, type, priority, title, description, source_key, dismissed, created_at) VALUES (?, 'GENERAL_TIP', 'LOW', ?, ?, ?, ?, CURRENT_TIMESTAMP)",
                user.id(),
                "Stored content must not be rewritten",
                "Stored description",
                sourceKey,
                dismissed);
    }

    private int activeCount(Auth user) {
        return jdbc.queryForObject(
                "SELECT COUNT(*) FROM insights WHERE user_id = ? AND dismissed = FALSE",
                Integer.class,
                user.id());
    }
}
