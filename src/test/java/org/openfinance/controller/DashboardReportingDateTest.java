package org.openfinance.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.openfinance.entity.NetWorth;
import org.openfinance.entity.User;
import org.openfinance.service.DefaultCurrencyProvider;
import org.openfinance.service.NetWorthService;
import org.openfinance.util.ReportingClock;
import org.springframework.security.core.Authentication;

@ExtendWith(MockitoExtension.class)
class DashboardReportingDateTest {
    @Mock private NetWorthService netWorthService;
    @Mock private DefaultCurrencyProvider defaultCurrencyProvider;
    @Mock private Authentication authentication;
    @InjectMocks private DashboardController controller;

    @ParameterizedTest
    @CsvSource({
        "2026-11-15T22:51:00Z,UTC,2026-11-15,2026-11-15",
        "2026-11-15T23:51:00Z,Europe/Paris,2026-11-16,2026-11-16",
        "2026-11-15T23:51:00Z,America/New_York,2026-11-16,2026-11-15"
    })
    void usesReportingDateForBothClampingAndSnapshotCreation(
            String instant, String userZone, String requestedEnd, String expectedEnd) {
        LocalDate start = LocalDate.of(2026, 11, 1);
        LocalDate requestEnd = LocalDate.parse(requestedEnd);
        LocalDate expected = LocalDate.parse(expectedEnd);
        LocalDate userToday =
                LocalDate.now(Clock.fixed(Instant.parse(instant), ZoneId.of(userZone)));
        assertThat(userToday).isEqualTo(expected);
        when(authentication.getPrincipal())
                .thenReturn(User.builder().id(1L).baseCurrency("EUR").build());
        when(defaultCurrencyProvider.resolve("EUR")).thenReturn("EUR");
        when(netWorthService.getNetWorthHistory(1L, start, expected))
                .thenReturn(List.of(snapshot(start, "100"), snapshot(expected, "200")));
        when(netWorthService.inReportingCurrency(any(NetWorth.class), eq("EUR")))
                .thenAnswer(invocation -> invocation.getArgument(0));
        try (ReportingClock.Scope ignored =
                ReportingClock.use(Clock.fixed(Instant.parse(instant), ZoneId.of(userZone)))) {
            assertThat(
                            controller
                                    .getNetWorthHistory(
                                            authentication, 16, start, requestEnd, false)
                                    .getBody())
                    .isNotNull()
                    .last()
                    .extracting("date")
                    .isEqualTo(expected);
            verify(netWorthService).saveNetWorthSnapshot(1L, expected, "EUR");
        }
    }

    private NetWorth snapshot(LocalDate date, String amount) {
        return NetWorth.builder()
                .userId(1L)
                .snapshotDate(date)
                .netWorth(new BigDecimal(amount))
                .totalAssets(new BigDecimal(amount))
                .totalLiabilities(BigDecimal.ZERO)
                .currency("EUR")
                .build();
    }
}
