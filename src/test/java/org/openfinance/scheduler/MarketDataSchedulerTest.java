package org.openfinance.scheduler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.openfinance.config.SchedulerProperties;
import org.openfinance.repository.UserRepository;
import org.openfinance.service.MarketDataService;

/**
 * Unit tests for the configurable market-hours guard used by {@link MarketDataScheduler} in {@code
 * DEFAULT} mode. Verifies the window is driven by {@code application.scheduled.market-hours.*}
 * rather than hardcoded 9 AM - 4 PM ET.
 */
@DisplayName("MarketDataScheduler market-hours guard")
class MarketDataSchedulerTest {

    // 2024-01-17 is a Wednesday; 2024-01-20 is a Saturday.
    private static final LocalDate WEDNESDAY = LocalDate.of(2024, 1, 17);
    private static final LocalDate SATURDAY = LocalDate.of(2024, 1, 20);

    private MarketDataScheduler schedulerWith(String zone, int openHour, int closeHour) {
        SchedulerProperties props = new SchedulerProperties();
        props.getMarketHours().setZone(zone);
        props.getMarketHours().setOpenHour(openHour);
        props.getMarketHours().setCloseHour(closeHour);
        return new MarketDataScheduler(
                mock(MarketDataService.class), mock(UserRepository.class), props);
    }

    @Test
    @DisplayName("Uses configured open/close hours instead of hardcoded 9-16")
    void usesConfiguredHours() {
        MarketDataScheduler scheduler = schedulerWith("America/New_York", 10, 15);

        assertThat(scheduler.isMarketHours(WEDNESDAY.atTime(9, 0))).isFalse();
        assertThat(scheduler.isMarketHours(WEDNESDAY.atTime(10, 0))).isTrue();
        assertThat(scheduler.isMarketHours(WEDNESDAY.atTime(15, 0))).isTrue();
        assertThat(scheduler.isMarketHours(WEDNESDAY.atTime(16, 0))).isFalse();
    }

    @Test
    @DisplayName("Excludes weekends regardless of hour")
    void excludesWeekends() {
        MarketDataScheduler scheduler = schedulerWith("America/New_York", 9, 16);

        assertThat(scheduler.isMarketHours(SATURDAY.atTime(12, 0))).isFalse();
    }

    @Test
    @DisplayName("Supports a non-US exchange window (Tokyo 9-15 JST)")
    void supportsNonUsExchangeWindow() {
        MarketDataScheduler scheduler = schedulerWith("Asia/Tokyo", 9, 15);

        assertThat(scheduler.isMarketHours(WEDNESDAY.atTime(9, 30))).isTrue();
        assertThat(scheduler.isMarketHours(WEDNESDAY.atTime(15, 0))).isTrue();
        assertThat(scheduler.isMarketHours(WEDNESDAY.atTime(16, 0))).isFalse();
    }

    @Test
    @DisplayName("Defaults preserve the original 9 AM - 4 PM ET window")
    void defaultsPreserveOriginalWindow() {
        SchedulerProperties props = new SchedulerProperties();
        MarketDataScheduler scheduler =
                new MarketDataScheduler(
                        mock(MarketDataService.class), mock(UserRepository.class), props);

        assertThat(props.getMarketHours().getZone()).isEqualTo("America/New_York");
        assertThat(props.getMarketHours().getOpenHour()).isEqualTo(9);
        assertThat(props.getMarketHours().getCloseHour()).isEqualTo(16);
        assertThat(scheduler.isMarketHours(WEDNESDAY.atTime(9, 0))).isTrue();
        assertThat(scheduler.isMarketHours(WEDNESDAY.atTime(16, 0))).isTrue();
        assertThat(scheduler.isMarketHours(WEDNESDAY.atTime(8, 0))).isFalse();
        assertThat(scheduler.isMarketHours(WEDNESDAY.atTime(17, 0))).isFalse();
    }
}
