package org.openfinance.service.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.util.List;
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

    @Test
    void lastWeekIsTheCompletedMondayThroughSundayWeekInBothLanguages() {
        for (String question :
                new String[] {
                    "How much did I spend last week?", "Mes dépenses la semaine dernière ?"
                }) {
            FinancialQuestion query = FinancialQuestion.parse(question, today);
            assertThat(query.start()).isEqualTo(LocalDate.of(2026, 9, 21));
            assertThat(query.end()).isEqualTo(LocalDate.of(2026, 9, 27));
        }
    }

    @Test
    void currentWeekAndYesterdayHaveDistinctBounds() {
        FinancialQuestion week = FinancialQuestion.parse("Income this week", today);
        assertThat(week.start()).isEqualTo(LocalDate.of(2026, 9, 28));
        assertThat(week.end()).isEqualTo(today);
        FinancialQuestion day = FinancialQuestion.parse("Mes dépenses hier", today);
        assertThat(day.start()).isEqualTo(today.minusDays(1));
        assertThat(day.end()).isEqualTo(today.minusDays(1));
    }

    @Test
    void unsupportedDatesNeverDefaultToCurrentMonth() {
        for (String question :
                List.of(
                        "Spending last quarter",
                        "Spending from March to June",
                        "Spending before September",
                        "Mes dépenses depuis janvier",
                        "Income in 2024 and 2025")) {
            assertThat(FinancialQuestion.parse(question, today).periodResolved())
                    .as(question)
                    .isFalse();
        }
    }

    @Test
    void dateOnlyFollowupsInheritTheLastSubjectWithoutReusingItsDates() {
        FinancialQuestion query =
                FinancialQuestion.resolve(
                        "And in October?",
                        today,
                        List.of(
                                "How much did I spend on Groceries in August 2025?",
                                "And in September?"));
        assertThat(query.text()).contains("spend", "groceries");
        assertThat(query.start()).isEqualTo(LocalDate.of(2026, 10, 1));
        assertThat(query.end()).isEqualTo(today);
        FinancialQuestion french =
                FinancialQuestion.resolve(
                        "Et en octobre ?",
                        today,
                        List.of("Mes dépenses alimentaires en septembre ?"));
        assertThat(french.text()).contains("alimentaires");
        assertThat(french.start()).isEqualTo(query.start());
        FinancialQuestion newSubject =
                FinancialQuestion.resolve(
                        "My income in October",
                        today,
                        List.of("Spending on Groceries in September"));
        assertThat(newSubject.text()).doesNotContain("groceries");
    }
}
