package org.openfinance.service.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class FinancialQuestionTest {
    private final LocalDate today = LocalDate.of(2026, 10, 3);

    @Test
    void resolvesEnglishFrenchExplicitAndRelativePeriods() {
        for (String question :
                new String[] {
                    "How much did I spend in September?", "Dépenses du mois dernier ?",
                    "My spending last month", "Mes dépenses en septembre 2026 ?"
                }) {
            FinancialQuestion query = FinancialQuestion.parse(question, today);
            assertThat(query.start()).isEqualTo(LocalDate.of(2026, 9, 1));
            assertThat(query.end()).isEqualTo(LocalDate.of(2026, 9, 30));
        }
        assertThat(FinancialQuestion.parse("Spent in December", today).start())
                .isEqualTo(LocalDate.of(2025, 12, 1));
        assertThat(FinancialQuestion.parse("Revenus cette année", today).start())
                .isEqualTo(LocalDate.of(2026, 1, 1));
    }

    @Test
    void preservesExplicitRangesAndRejectsInvalidOrUnboundedRanges() {
        FinancialQuestion query =
                FinancialQuestion.parse("Spent from 2025-02-01 to 2025-02-18", today);
        assertThat(query.start()).isEqualTo(LocalDate.of(2025, 2, 1));
        assertThat(query.end()).isEqualTo(LocalDate.of(2025, 2, 18));
        assertThatThrownBy(() -> FinancialQuestion.parse("2026-09-01 to 2026-08-01", today))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> FinancialQuestion.parse("2020-01-01 to 2026-09-01", today))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
