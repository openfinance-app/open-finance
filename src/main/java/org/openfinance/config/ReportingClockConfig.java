package org.openfinance.config;

import java.time.Clock;
import org.openfinance.util.ReportingClock;
import org.springframework.boot.autoconfigure.validation.ValidationConfigurationCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ReportingClockConfig {
    @Bean
    public Clock reportingSystemClock() {
        return Clock.systemUTC();
    }

    @Bean
    public ValidationConfigurationCustomizer reportingValidationClock() {
        Clock clock = ReportingClock.validationClock();
        return configuration -> configuration.clockProvider(() -> clock);
    }
}
