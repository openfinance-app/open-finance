package org.openfinance.entity;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for the {@link User} entity, focusing on the {@code secondaryCurrency} field
 * introduced by Flyway migration V46.
 *
 * <p>Requirement REQ-2.2: Secondary currency field on the User entity — nullable, max 3 chars,
 * persisted to {@code users.secondary_currency}.
 */
@DisplayName("User Entity — secondaryCurrency field tests (REQ-2.2)")
class UserEntityTest {

    @Test
    @DisplayName("secondaryCurrency defaults to null when not set via builder")
    void secondaryCurrencyShouldDefaultToNull() {
        // Requirement REQ-2.2: field is optional / nullable
        User user =
                User.builder()
                        .id(1L)
                        .username("testuser")
                        .email("test@example.com")
                        .passwordHash("hash")
                        .masterPasswordSalt("salt")
                        .baseCurrency("USD")
                        .build();

        assertThat(user.getSecondaryCurrency()).isNull();
    }

    @Test
    @DisplayName("secondaryCurrency can be set and retrieved correctly")
    void secondaryCurrencyShouldRoundTrip() {
        // Requirement REQ-2.2: field accepts valid ISO 4217 codes (3 chars)
        User user =
                User.builder()
                        .id(2L)
                        .username("testuser2")
                        .email("test2@example.com")
                        .passwordHash("hash")
                        .masterPasswordSalt("salt")
                        .baseCurrency("EUR")
                        .secondaryCurrency("USD")
                        .build();

        assertThat(user.getSecondaryCurrency()).isEqualTo("USD");
    }

    @Test
    @DisplayName("secondaryCurrency can be updated via setter after construction")
    void secondaryCurrencyShouldBeUpdatableViaSetter() {
        // Requirement REQ-2.2: field is mutable (Lombok @Data generates setter)
        User user =
                User.builder()
                        .id(3L)
                        .username("testuser3")
                        .email("test3@example.com")
                        .passwordHash("hash")
                        .masterPasswordSalt("salt")
                        .baseCurrency("GBP")
                        .build();

        assertThat(user.getSecondaryCurrency()).isNull();

        user.setSecondaryCurrency("JPY");

        assertThat(user.getSecondaryCurrency()).isEqualTo("JPY");
    }

    @Test
    @DisplayName("secondaryCurrency can be set back to null via setter")
    void secondaryCurrencyShouldBeNullable() {
        // Requirement REQ-2.2: field is nullable — setting to null clears the preference
        User user =
                User.builder()
                        .id(4L)
                        .username("testuser4")
                        .email("test4@example.com")
                        .passwordHash("hash")
                        .masterPasswordSalt("salt")
                        .baseCurrency("USD")
                        .secondaryCurrency("EUR")
                        .build();

        assertThat(user.getSecondaryCurrency()).isEqualTo("EUR");

        user.setSecondaryCurrency(null);

        assertThat(user.getSecondaryCurrency()).isNull();
    }

    @Test
    @DisplayName(
            "User builder sets all fields including secondaryCurrency independently of baseCurrency")
    void secondaryCurrencyShouldBeIndependentOfBaseCurrency() {
        // Requirement REQ-2.2: secondary currency is separate from base currency
        User user =
                User.builder()
                        .id(5L)
                        .username("testuser5")
                        .email("test5@example.com")
                        .passwordHash("hash")
                        .masterPasswordSalt("salt")
                        .baseCurrency("EUR")
                        .secondaryCurrency("CHF")
                        .build();

        assertThat(user.getBaseCurrency()).isEqualTo("EUR");
        assertThat(user.getSecondaryCurrency()).isEqualTo("CHF");
        assertThat(user.getBaseCurrency()).isNotEqualTo(user.getSecondaryCurrency());
    }

    @Test
    @DisplayName("toString does not include sensitive data but does include secondaryCurrency")
    void toStringShouldIncludeSecondaryCurrency() {
        // Verify the custom toString includes secondaryCurrency for debuggability
        User user =
                User.builder()
                        .id(6L)
                        .username("debuguser")
                        .email("debug@example.com")
                        .passwordHash("secret-hash")
                        .masterPasswordSalt("secret-salt")
                        .baseCurrency("USD")
                        .secondaryCurrency("JPY")
                        .build();

        String str = user.toString();

        // Should include secondaryCurrency
        assertThat(str).contains("secondaryCurrency='JPY'");
        // Should NOT include sensitive fields
        assertThat(str).doesNotContain("secret-hash");
        assertThat(str).doesNotContain("secret-salt");
    }
}
