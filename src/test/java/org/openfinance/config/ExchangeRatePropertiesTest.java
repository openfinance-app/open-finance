package org.openfinance.config;

import static org.assertj.core.api.Assertions.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

/**
 * Unit tests for {@link ExchangeRateProperties}, focused on the externalized cryptocurrency code
 * list (PO NOTE 2 — the crypto list must be configurable rather than hardcoded).
 */
@DisplayName("ExchangeRateProperties Tests")
class ExchangeRatePropertiesTest {

    @Test
    @DisplayName("crypto-codes default to the previously-hardcoded list")
    void cryptoCodesDefaults() {
        ExchangeRateProperties props = new ExchangeRateProperties();

        assertThat(props.getCryptoCodes())
                .containsExactly(
                        "BTC", "ETH", "BNB", "XRP", "ADA", "SOL", "DOT", "DOGE", "USDT", "USDC",
                        "MATIC", "AVAX", "LINK", "UNI");
    }

    @Test
    @DisplayName("crypto-codes are overridable from application.exchange-rates configuration")
    void cryptoCodesOverridable() {
        Map<String, Object> config = new HashMap<>();
        config.put("application.exchange-rates.crypto-codes[0]", "BTC");
        config.put("application.exchange-rates.crypto-codes[1]", "ETH");
        config.put("application.exchange-rates.crypto-codes[2]", "SHIB");

        ExchangeRateProperties bound =
                new Binder(new MapConfigurationPropertySource(config))
                        .bind("application.exchange-rates", ExchangeRateProperties.class)
                        .get();

        assertThat(bound.getCryptoCodes()).isEqualTo(List.of("BTC", "ETH", "SHIB"));
    }
}
