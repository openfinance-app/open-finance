package org.openfinance.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;

import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.mock.mockito.MockBean;

class AuditExchangeRateFailureIntegrationTest extends AuditApiTestSupport {
    @MockBean private ExchangeRateService exchangeRateService;

    @Test
    void unavailableQuoteReturns503AndDoesNotPersistMislabeledSnapshots() throws Exception {
        json(
                "POST",
                "/accounts",
                data(
                        "name",
                        "Yen",
                        "type",
                        "CHECKING",
                        "currency",
                        "JPY",
                        "initialBalance",
                        100000,
                        "openingDate",
                        START.toString()),
                owner,
                201);
        doThrow(new IllegalStateException("Provider unavailable"))
                .when(exchangeRateService)
                .convert(any(BigDecimal.class), eq("JPY"), eq("EUR"));
        doThrow(new IllegalStateException("Provider unavailable"))
                .when(exchangeRateService)
                .convert(any(BigDecimal.class), eq("JPY"), eq("EUR"), any(LocalDate.class));
        assertThat(json("GET", "/dashboard/summary", null, owner, 503).path("message").asText())
                .contains("JPY", "EUR");
        json(
                "GET",
                "/dashboard/networth-history?startDate="
                        + START
                        + "&endDate="
                        + START
                        + "&recalculate=true",
                null,
                owner,
                503);
        assertThat(
                        jdbc.queryForObject(
                                "SELECT COUNT(*) FROM net_worth WHERE user_id = ?",
                                Integer.class,
                                owner.id()))
                .isZero();
    }

    @Test
    void missingHistoricalRatesFailAllCashflowAndCategoryReports() throws Exception {
        long cash = account(owner);
        json("POST", "/transactions", movement(cash, 100, START.plusMonths(1)), owner, 201);
        jdbc.update("UPDATE users SET base_currency = 'JPY' WHERE id = ?", owner.id());
        doThrow(new IllegalStateException("Historical quote unavailable"))
                .when(exchangeRateService)
                .convert(any(BigDecimal.class), eq("EUR"), eq("JPY"), any(LocalDate.class));
        for (String endpoint :
                java.util.List.of(
                        "/cashflow?period=365",
                        "/spending?period=365",
                        "/cashflow-sankey?period=365",
                        "/borrowing-capacity?period=365",
                        "/daily-cashflow?year="
                                + START.plusMonths(1).getYear()
                                + "&month="
                                + START.plusMonths(1).getMonthValue())) {
            assertThat(
                            json("GET", "/dashboard" + endpoint, null, owner, 503)
                                    .path("message")
                                    .asText())
                    .contains("EUR", "JPY");
        }
    }
}
