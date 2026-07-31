package org.openfinance.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Unit tests for {@link ExchangeRateProperties}. */
@DisplayName("ExchangeRateProperties Tests")
class ExchangeRatePropertiesTest {

    @Test
    @DisplayName("base-currency defaults to EUR")
    void baseCurrencyDefault() {
        assertThat(new ExchangeRateProperties().getBaseCurrency()).isEqualTo("EUR");
    }
}
