package org.openfinance.service.ai;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Locale;
import org.junit.jupiter.api.Test;

class FinancialResponseGuardTest {
    private static final String CONTEXT =
            """
            [VERIFIED_FINANCIAL_DATA]
            Total Account Balances: 12,934.21 EUR
            Month-to-date cash flow: 784.96 EUR (surplus)
            """;

    @Test
    void rejectsFabricatedBalancesAndDeficits() {
        String invented = "Solde négatif de −9 265,54 EUR et déficit mensuel de 1 300 EUR.";
        String result = FinancialResponseGuard.verify(invented, CONTEXT, Locale.FRENCH);
        assertThat(result).contains("vérifier").doesNotContain("9 265", "1 300");
    }

    @Test
    void acceptsEquivalentFrenchAndEnglishCurrencyFormatting() {
        for (String response :
                new String[] {
                    "Account balance: €12,934.21; cash flow: +784.96 EUR.",
                    "Solde : 12\u202f934,21 € ; flux : +784,96 EUR."
                }) {
            assertThat(FinancialResponseGuard.verify(response, CONTEXT, Locale.ENGLISH))
                    .isEqualTo(response);
        }
    }

    @Test
    void rejectsFlippedSignOrCurrency() {
        for (String response :
                new String[] {"−784,96 EUR", "$12,934.21", "−€12,934.21", "- EUR 784.96"}) {
            assertThat(FinancialResponseGuard.verify(response, CONTEXT, Locale.ENGLISH))
                    .contains("could not verify");
        }
    }
}
