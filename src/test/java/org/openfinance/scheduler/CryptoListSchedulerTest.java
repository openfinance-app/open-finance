package org.openfinance.scheduler;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.openfinance.config.SchedulerProperties;
import org.openfinance.config.SchedulerProperties.SchedulerConfig;
import org.openfinance.config.SchedulerProperties.SchedulingMode;
import org.openfinance.service.CryptoListService;
import org.springframework.boot.ApplicationArguments;

@ExtendWith(MockitoExtension.class)
@DisplayName("CryptoListScheduler Tests")
class CryptoListSchedulerTest {

    @Mock private CryptoListService cryptoListService;
    @Mock private SchedulerProperties schedulerProperties;
    @Mock private ApplicationArguments args;
    @InjectMocks private CryptoListScheduler scheduler;

    @Test
    @DisplayName("runs on startup when mode requests it")
    void runsOnStartup() {
        SchedulerConfig config = new SchedulerConfig();
        config.setMode(SchedulingMode.STARTUP_ONLY);
        when(schedulerProperties.getCryptoList()).thenReturn(config);

        scheduler.run(args);

        verify(cryptoListService).refresh();
    }

    @Test
    @DisplayName("does not run on startup in DEFAULT mode")
    void skipsStartupInDefaultMode() {
        SchedulerConfig config = new SchedulerConfig();
        config.setMode(SchedulingMode.DEFAULT);
        when(schedulerProperties.getCryptoList()).thenReturn(config);

        scheduler.run(args);

        verify(cryptoListService, never()).refresh();
    }

    @Test
    @DisplayName("scheduled refresh swallows exceptions")
    void scheduledRefreshSwallowsExceptions() {
        when(schedulerProperties.getCryptoList()).thenReturn(new SchedulerConfig());
        when(cryptoListService.refresh()).thenThrow(new RuntimeException("boom"));

        scheduler.refreshCryptoList(); // should not throw
    }
}
