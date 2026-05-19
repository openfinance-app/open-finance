package org.openfinance;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.retry.annotation.EnableRetry;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Open-Finance Backend Application
 *
 * <p>Main entry point for the Open-Finance personal wealth management application. This application
 * provides comprehensive wealth tracking including bank accounts, investment portfolios, insurance,
 * real estate, loans, and budget management with automated market data updates.
 *
 * <p>Requirements: REQ-1.1 (Java/Spring Boot), REQ-1.2 (Architecture)
 *
 * @version 0.1.0
 * @since 2026-01-30
 */
@SpringBootApplication
@EnableCaching
@EnableScheduling
@EnableAsync
@EnableRetry
public class OpenFinanceApplication {

    /**
     * Main method to start the Spring Boot application.
     *
     * @param args Command line arguments
     */
    public static void main(String[] args) {
        SpringApplication.run(OpenFinanceApplication.class, args);
    }
}
