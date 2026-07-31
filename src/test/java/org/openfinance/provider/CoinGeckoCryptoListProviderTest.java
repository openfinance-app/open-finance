package org.openfinance.provider;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.openfinance.config.CryptoListProperties;
import org.openfinance.dto.CryptoCurrencyInfo;

@DisplayName("CoinGeckoCryptoListProvider Tests")
class CoinGeckoCryptoListProviderTest {

    private CoinGeckoCryptoListProvider provider;

    @BeforeEach
    void setUp() {
        provider = new CoinGeckoCryptoListProvider(new CryptoListProperties(), new ObjectMapper());
    }

    @Test
    @DisplayName("name and enabled reflect configuration")
    void metadata() {
        assertThat(provider.name()).isEqualTo("coingecko");
        assertThat(provider.isEnabled()).isTrue();
    }

    @Test
    @DisplayName(
            "parse maps /coins/markets JSON, upper-cases symbols, and skips non-conforming codes")
    void parse() {
        String json =
                """
                [
                  {"id":"bitcoin","symbol":"btc","name":"Bitcoin","current_price":95000.50,"market_cap_rank":1},
                  {"id":"ethereum","symbol":"eth","name":"Ethereum","current_price":3200,"market_cap_rank":2},
                  {"id":"1inch","symbol":"1inch","name":"1inch","current_price":0.42,"market_cap_rank":95}
                ]
                """;

        List<CryptoCurrencyInfo> result = provider.parse(json);

        assertThat(result).hasSize(2); // "1INCH" contains a digit -> skipped
        CryptoCurrencyInfo btc = result.get(0);
        assertThat(btc.code()).isEqualTo("BTC");
        assertThat(btc.name()).isEqualTo("Bitcoin");
        assertThat(btc.symbol()).isEqualTo("BTC");
        assertThat(btc.marketCapRank()).isEqualTo(1);
        assertThat(btc.priceUsd()).isEqualByComparingTo("95000.50");
        assertThat(result).extracting(CryptoCurrencyInfo::code).containsExactly("BTC", "ETH");
    }
}
