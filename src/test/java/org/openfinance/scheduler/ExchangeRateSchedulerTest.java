package org.openfinance.scheduler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.openfinance.config.SchedulerProperties;
import org.openfinance.config.SchedulerProperties.SchedulingMode;
import org.openfinance.service.ExchangeRateService;

@ExtendWith(MockitoExtension.class)
@DisplayName("Exchange-rate scheduling respects startup mode and survives provider outages")
class ExchangeRateSchedulerTest {
    @Mock private ExchangeRateService exchangeRateService;
    private final SchedulerProperties properties = new SchedulerProperties();
    private ExchangeRateScheduler scheduler;

    @BeforeEach
    void setUp() {
        scheduler = new ExchangeRateScheduler(exchangeRateService, properties);
    }

    @Test
    void defaultModeDoesNotFetchRatesDuringStartup() {
        scheduler.run(null);
        verifyNoInteractions(exchangeRateService);
    }

    @ParameterizedTest
    @EnumSource(
            value = SchedulingMode.class,
            names = {"STARTUP_ONLY", "STARTUP_AND_EVERY_X_HOURS"})
    void configuredStartupFetchesRatesOnce(SchedulingMode mode) {
        properties.getExchangeRates().setMode(mode);
        when(exchangeRateService.updateExchangeRates()).thenReturn(4);
        scheduler.run(null);
        verify(exchangeRateService).updateExchangeRates();
    }

    @Test
    void aFailedScheduledUpdateDoesNotPreventTheNextRun() {
        when(exchangeRateService.updateExchangeRates())
                .thenThrow(new IllegalStateException("Provider offline"))
                .thenReturn(0);
        assertDoesNotThrow(() -> scheduler.updateDailyExchangeRates());
        assertDoesNotThrow(() -> scheduler.updateDailyExchangeRates());
        verify(exchangeRateService, org.mockito.Mockito.times(2)).updateExchangeRates();
    }

    @Test
    void manualUpdatesReturnCountsAndExposeFailureToTheCaller() {
        when(exchangeRateService.updateExchangeRates())
                .thenReturn(3)
                .thenThrow(new IllegalStateException("Provider offline"));
        assertThat(scheduler.updateExchangeRatesNow()).isEqualTo(3);
        assertThatThrownBy(() -> scheduler.updateExchangeRatesNow())
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Provider offline");
    }
}
