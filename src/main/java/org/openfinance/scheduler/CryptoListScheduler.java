package org.openfinance.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.openfinance.config.SchedulerProperties;
import org.openfinance.service.CryptoListService;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Refreshes the cryptocurrency list on a weekly schedule (the top-N set changes slowly). Optionally
 * runs once on startup depending on {@code application.scheduled.crypto-list.mode}. Mirrors {@code
 * ExchangeRateScheduler}.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CryptoListScheduler implements ApplicationRunner {

    /** Default cron: weekly on Monday at 03:00 (server timezone). */
    static final String DEFAULT_CRON = "0 0 3 * * MON";

    private final CryptoListService cryptoListService;
    private final SchedulerProperties schedulerProperties;

    @Override
    public void run(ApplicationArguments args) {
        if (schedulerProperties.getCryptoList().isRunOnStartup()) {
            log.info(
                    "Executing startup crypto-list refresh (mode={})",
                    schedulerProperties.getCryptoList().getMode());
            refreshCryptoList();
        }
    }

    @Scheduled(cron = "#{schedulerProperties.cryptoList.effectiveCron('" + DEFAULT_CRON + "')}")
    public void refreshCryptoList() {
        log.info(
                "Starting scheduled crypto-list refresh (mode={})",
                schedulerProperties.getCryptoList().getMode());
        try {
            int count = cryptoListService.refresh();
            log.info("Crypto-list refresh completed: {} cryptocurrencies", count);
        } catch (Exception e) {
            log.error("Failed to refresh crypto list: {}", e.getMessage(), e);
        }
    }
}
