package org.openfinance.dto;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AccountSummaryResponseTest {

    @Test
    @DisplayName("should build AccountSummaryResponse correctly")
    void shouldBuildAccountSummaryResponse() {
        // Given
        Long id = 1L;
        String name = "Test Account";
        String type = "CHECKING";
        String currency = "USD";
        BigDecimal balance = new BigDecimal("100.50");
        boolean active = true;
        String institution = "Test Bank";

        // When
        AccountSummaryResponse response =
                AccountSummaryResponse.builder()
                        .id(id)
                        .name(name)
                        .type(type)
                        .currency(currency)
                        .balance(balance)
                        .isActive(active)
                        .institutionName(institution)
                        .build();

        // Then
        assertThat(response.getId()).isEqualTo(id);
        assertThat(response.getName()).isEqualTo(name);
        assertThat(response.getType()).isEqualTo(type);
        assertThat(response.getCurrency()).isEqualTo(currency);
        assertThat(response.getBalance()).isEqualTo(balance);
        assertThat(response.isActive()).isTrue();
        assertThat(response.getInstitutionName()).isEqualTo(institution);
    }
}
