package org.openfinance.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.openfinance.dto.BorrowingCapacity;
import org.openfinance.dto.CashflowSankeyDto;
import org.openfinance.entity.NetWorth;
import org.openfinance.entity.User;
import org.openfinance.service.DashboardService;
import org.openfinance.service.DefaultCurrencyProvider;
import org.openfinance.service.NetWorthService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;

@ExtendWith(MockitoExtension.class)
@DisplayName("Dashboard reporting ranges are validated before financial queries")
class DashboardReportingBoundaryTest {
    @Mock private DashboardService dashboard;
    @Mock private NetWorthService netWorth;
    @Mock private DefaultCurrencyProvider currencies;
    @InjectMocks private DashboardController controller;
    private Authentication authentication;
    private final LocalDate start = LocalDate.of(2025, 1, 1);
    private final LocalDate end = LocalDate.of(2025, 1, 31);

    @BeforeEach
    void authenticate() {
        User user = User.builder().id(7L).baseCurrency("EUR").build();
        authentication = new UsernamePasswordAuthenticationToken(user, null);
    }

    private ResponseEntity<?> request(String endpoint, int period, LocalDate from, LocalDate to) {
        return switch (endpoint) {
            case "cashflow" -> controller.getCashFlow(authentication, period, from, to);
            case "spending" -> controller.getSpendingByCategory(authentication, period, from, to);
            case "borrowing" -> controller.getBorrowingCapacity(authentication, period, from, to);
            case "portfolio" -> controller.getPortfolioPerformance(
                    authentication, period, from, to);
            case "sankey" -> controller.getCashflowSankey(authentication, period, from, to);
            default -> throw new AssertionError(endpoint);
        };
    }

    static Stream<Arguments> reportingRanges() {
        return Stream.of("cashflow", "spending", "borrowing", "portfolio", "sankey")
                .flatMap(
                        endpoint ->
                                Stream.of("none", "start-only", "end-only", "complete")
                                        .map(range -> Arguments.of(endpoint, range)));
    }

    @ParameterizedTest
    @MethodSource("reportingRanges")
    void routesCompleteRangesAndDefaultsIncompleteRangesWithinTheAuthenticatedUser(
            String endpoint, String range) {
        boolean complete = range.equals("complete");
        LocalDate from = complete || range.equals("start-only") ? start : null;
        LocalDate to = complete || range.equals("end-only") ? end : null;
        int period = complete ? 0 : 17;
        switch (endpoint) {
            case "cashflow" -> {
                Map<String, BigDecimal> values =
                        Map.of(
                                "income",
                                new BigDecimal("101.01"),
                                "expenses",
                                new BigDecimal("1.01"),
                                "netCashFlow",
                                new BigDecimal("100.00"));
                if (complete) when(dashboard.getCashFlow(7L, start, end)).thenReturn(values);
                else when(dashboard.getCashFlow(7L, period)).thenReturn(values);
            }
            case "spending" -> {
                Map<String, BigDecimal> values = Map.of("Rent", new BigDecimal("300.01"));
                if (complete)
                    when(dashboard.getSpendingByCategory(7L, start, end)).thenReturn(values);
                else when(dashboard.getSpendingByCategory(7L, period)).thenReturn(values);
            }
            case "borrowing" -> {
                BorrowingCapacity value =
                        BorrowingCapacity.builder()
                                .currency("EUR")
                                .availableBorrowingCapacity(new BigDecimal("42.01"))
                                .build();
                if (complete)
                    when(dashboard.getBorrowingCapacity(7L, start, end)).thenReturn(value);
                else when(dashboard.getBorrowingCapacity(7L, period)).thenReturn(value);
            }
            case "sankey" -> {
                CashflowSankeyDto value =
                        CashflowSankeyDto.builder()
                                .incomeSources(List.of())
                                .expenseCategories(List.of())
                                .totalIncome(new BigDecimal("101.01"))
                                .build();
                if (complete) when(dashboard.getCashflowSankey(7L, start, end)).thenReturn(value);
                else when(dashboard.getCashflowSankey(7L, period)).thenReturn(value);
            }
            case "portfolio" -> {
                if (complete)
                    when(dashboard.getPortfolioPerformance(7L, start, end)).thenReturn(List.of());
                else when(dashboard.getPortfolioPerformance(7L, period)).thenReturn(List.of());
            }
            default -> throw new AssertionError(endpoint);
        }
        ResponseEntity<?> response = request(endpoint, period, from, to);
        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).isNotNull();
        // Strict stubbing also checks the chosen range and authenticated user's identity.
        verifyNoInteractions(netWorth);
    }

    static Stream<Arguments> invalidPeriods() {
        return Stream.of("cashflow", "spending", "borrowing", "portfolio", "sankey")
                .flatMap(
                        endpoint ->
                                Stream.of(0, -1, Integer.MIN_VALUE)
                                        .map(period -> Arguments.of(endpoint, period)));
    }

    @ParameterizedTest
    @MethodSource("invalidPeriods")
    void rejectsNonpositivePeriodsBeforeLoadingAnyFinancialData(String endpoint, int period) {
        assertThat(request(endpoint, period, start, null).getStatusCode().value()).isEqualTo(400);
        verifyNoInteractions(dashboard, netWorth);
    }

    @ParameterizedTest
    @ValueSource(strings = {"cashflow", "spending", "borrowing", "portfolio", "sankey"})
    void rejectsReversedExplicitRanges(String endpoint) {
        assertThat(request(endpoint, 30, end, start).getStatusCode().value()).isEqualTo(400);
        verifyNoInteractions(dashboard, netWorth);
    }

    @ParameterizedTest
    @CsvSource(
            value = {"NULL,NULL", "2024,NULL", "NULL,2", "2024,2"},
            nullValues = "NULL")
    void dailyReportDefaultsOnlyTheOmittedCalendarFields(Integer year, Integer month) {
        LocalDate today = LocalDate.now();
        assertThat(controller.getDailyCashFlow(authentication, year, month).getStatusCode().value())
                .isEqualTo(200);
        verify(dashboard)
                .getDailyCashFlow(
                        7L,
                        year == null ? today.getYear() : year,
                        month == null ? today.getMonthValue() : month);
    }

    private NetWorth observation(LocalDate date) {
        return NetWorth.builder()
                .userId(7L)
                .snapshotDate(date)
                .currency("EUR")
                .totalAssets(new BigDecimal("101.01"))
                .totalLiabilities(new BigDecimal("1.01"))
                .netWorth(new BigDecimal("100.00"))
                .build();
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "complete",
                "missing-start",
                "missing-end",
                "sparse",
                "empty",
                "recalculate"
            })
    void fillsOnlyMissingHistoricalBoundariesAndReloadsRebuiltObservations(String state) {
        when(currencies.resolve("EUR")).thenReturn("EUR");
        List<NetWorth> complete =
                List.of(observation(start), observation(start.plusDays(10)), observation(end));
        List<NetWorth> initial =
                switch (state) {
                    case "missing-start" -> List.of(
                            observation(start.plusDays(1)),
                            observation(start.plusDays(10)),
                            observation(end));
                    case "missing-end" -> List.of(
                            observation(start),
                            observation(start.plusDays(10)),
                            observation(end.minusDays(1)));
                    case "sparse" -> List.of(observation(start), observation(end));
                    case "empty" -> List.of();
                    default -> complete;
                };
        boolean recalculate = state.equals("recalculate");
        when(netWorth.getNetWorthHistory(7L, start, end)).thenReturn(initial, complete);
        for (NetWorth value : complete) {
            lenient().when(netWorth.inReportingCurrency(value, "EUR")).thenReturn(value);
        }
        if (!state.equals("complete") && !recalculate) {
            when(netWorth.backfillNetWorthHistory(7L, start, end, "EUR")).thenReturn(1);
        }
        assertThat(
                        controller
                                .getNetWorthHistory(authentication, 0, start, end, recalculate)
                                .getBody())
                .hasSize(3);
        if (recalculate) {
            verify(netWorth).backfillNetWorthHistory(7L, start, end, "EUR", true);
        } else if (state.equals("complete")) {
            verify(netWorth, never()).backfillNetWorthHistory(7L, start, end, "EUR");
        } else {
            verify(netWorth).backfillNetWorthHistory(7L, start, end, "EUR");
        }
        verify(netWorth, never()).saveNetWorthSnapshot(7L, LocalDate.now(), "EUR");
    }
}
