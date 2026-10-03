package org.openfinance.service.ai;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Locale;
import org.junit.jupiter.api.Test;

class FinancialResponseGuardTest {
    private static final String CONTEXT =
            """
            [VERIFIED_FINANCIAL_DATA]
            [FACT] {"id":"net_worth","label":"Net worth","amount":"3600.00","currency":"EUR","period":"2026-09-29","entity":""}
            [FACT] {"id":"cashflow.expenses","label":"Month-to-date expenses","amount":"170.00","currency":"EUR","period":"2026-09-01 / 2026-09-29","entity":""}
            [FACT] {"id":"budget.1.limit","label":"Budget limit","amount":"100.00","currency":"EUR","period":"2026-09","entity":"Groceries"}
            """;

    @Test
    void rendersAllServerSelectedFactsWhenTheModelOmitsPartOfACompoundRequest() {
        String context =
                CONTEXT.lines()
                        .filter(line -> !line.contains("cashflow.expenses"))
                        .collect(java.util.stream.Collectors.joining("\n"));
        for (String ids : new String[] {"[\"net_worth\"]", "[]"}) {
            String verified =
                    FinancialResponseGuard.verifyRequestedFacts(
                            "{\"explanation\":\"Your budget is unavailable.\",\"factIds\":"
                                    + ids
                                    + "}",
                            context,
                            Locale.ENGLISH);
            assertThat(verified)
                    .contains(
                            "Net worth: **3600.00 EUR**",
                            "Budget limit — Groceries: **100.00 EUR**")
                    .doesNotContain("unavailable", "Month-to-date expenses");
        }
        org.assertj.core.api.Assertions.assertThatThrownBy(
                        () ->
                                FinancialResponseGuard.verifyRequestedFacts(
                                        "{\"explanation\":\"Overview\",\"factIds\":[\"invented\"]}",
                                        context,
                                        Locale.ENGLISH))
                .isInstanceOf(AIProviderException.class);
    }

    @Test
    void structuredRepliesRenderKnownFactsWithoutAcceptingModelAmountsOrNumberWordsInNames() {
        for (String explanation :
                new String[] {
                    "Round Two Checking is available.",
                    "Le solde est 3600 euros.",
                    "Your balance is 999999."
                }) {
            String answer = "{\"explanation\":\"" + explanation + "\",\"factIds\":[\"net_worth\"]}";
            String verified =
                    FinancialResponseGuard.verifyStructured(answer, CONTEXT, Locale.ENGLISH);
            assertThat(verified)
                    .contains("Net worth: **3600.00 EUR**")
                    .doesNotContain("999999", "Round Two", "Le solde");
        }
        org.assertj.core.api.Assertions.assertThatThrownBy(
                        () ->
                                FinancialResponseGuard.verifyStructured(
                                        "{\"explanation\":\"999 euros\",\"factIds\":[\"not_a_fact\"]}",
                                        CONTEXT,
                                        Locale.ENGLISH))
                .isInstanceOf(AIProviderException.class);
    }

    @Test
    void bindsAnAmountToItsMetricPeriodAndCurrency() {
        String response =
                FinancialResponseGuard.verify(
                        "{\"explanation\":\"Here is your spending.\",\"factIds\":[\"cashflow.expenses\"]}",
                        CONTEXT,
                        Locale.ENGLISH);
        assertThat(response)
                .contains("Month-to-date expenses: **170.00 EUR** (2026-09-01 / 2026-09-29)")
                .doesNotContain("3600");
    }

    @Test
    void rejectsRelabelledFiguresEvenWhenTheNumberExistsInAnotherFact() {
        for (String response :
                new String[] {
                    "Your expenses are 3600.00 EUR",
                    "Your savings are 999999 euros",
                    "−€3600.00",
                    "nine thousand euros",
                    "{\"explanation\":\"Expenses are 3600 euros\",\"factIds\":[\"net_worth\"]}"
                }) {
            assertThat(FinancialResponseGuard.verify(response, CONTEXT, Locale.ENGLISH))
                    .contains("could not verify");
        }
    }

    @Test
    void acceptsBudgetFactsWithoutInferringCurrencyOrAmounts() {
        assertThat(
                        FinancialResponseGuard.verify(
                                "{\"explanation\":\"Review this limit.\",\"factIds\":[\"budget.1.limit\"]}",
                                CONTEXT,
                                Locale.ENGLISH))
                .contains("Budget limit — Groceries: **100.00 EUR** (2026-09)");
    }

    @Test
    void rejectsUnknownFactReferencesAndLocalizesFailure() {
        assertThat(
                        FinancialResponseGuard.verify(
                                "{\"explanation\":\"Votre budget.\",\"factIds\":[\"budget.999.limit\"]}",
                                CONTEXT,
                                Locale.FRENCH))
                .contains("vérifier");
    }

    @Test
    void keepsQualitativeAnswersAndEscapesUntrustedNames() {
        assertThat(
                        FinancialResponseGuard.verify(
                                "Review your recurring subscriptions.", CONTEXT, Locale.ENGLISH))
                .isEqualTo("Review your recurring subscriptions.");
        String context = CONTEXT.replace("Groceries", "[click](https://example.test)");
        assertThat(
                        FinancialResponseGuard.verify(
                                "{\"explanation\":\"\",\"factIds\":[\"budget.1.limit\"]}",
                                context,
                                Locale.ENGLISH))
                .contains("\\[click\\]\\(https://example\\.test\\)");
    }

    @Test
    void rejectsWrittenAmountsWithoutCurrencyInPlainAndStructuredReplies() {
        for (String claim :
                new String[] {
                    "Your account balance is one million.", "Your balance is twenty-five.",
                    "Votre solde est deux mille.", "Votre solde est un million.",
                    "Your savings cover half your expenses."
                }) {
            assertThat(FinancialResponseGuard.verify(claim, CONTEXT, Locale.ENGLISH))
                    .contains("could not verify");
            assertThat(
                            FinancialResponseGuard.verify(
                                    "{\"explanation\":\""
                                            + claim
                                            + "\",\"factIds\":[\"budget.1.limit\"]}",
                                    CONTEXT,
                                    Locale.ENGLISH))
                    .contains("could not verify");
        }
        assertThat(
                        FinancialResponseGuard.verify(
                                "Examinez un budget adapté.", CONTEXT, Locale.FRENCH))
                .isEqualTo("Examinez un budget adapté.");
    }
}
