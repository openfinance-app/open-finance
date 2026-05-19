package org.openfinance.entity;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("AccountType enum tests")
class AccountTypeTest {

    @Test
    @DisplayName("Each AccountType should have a non-empty display name")
    void eachShouldHaveDisplayName() {
        for (AccountType t : AccountType.values()) {
            assertThat(t.getDisplayName()).isNotNull().isNotEmpty();
        }
    }

    @Test
    @DisplayName("Display name should match expected values for known types")
    void displayNameMatchesKnownValues() {
        assertThat(AccountType.CHECKING.getDisplayName()).isEqualTo("Checking Account");
        assertThat(AccountType.SAVINGS.getDisplayName()).isEqualTo("Savings Account");
        assertThat(AccountType.CREDIT_CARD.getDisplayName()).isEqualTo("Credit Card");
        assertThat(AccountType.INVESTMENT.getDisplayName()).isEqualTo("Investment Account");
        assertThat(AccountType.CASH.getDisplayName()).isEqualTo("Cash");
        assertThat(AccountType.OTHER.getDisplayName()).isEqualTo("Other");
    }
}
