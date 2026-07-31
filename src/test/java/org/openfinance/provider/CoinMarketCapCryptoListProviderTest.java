package org.openfinance.provider;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.openfinance.config.CryptoListProperties;
import org.openfinance.dto.CryptoCurrencyInfo;

@DisplayName("CoinMarketCapCryptoListProvider Tests")
class CoinMarketCapCryptoListProviderTest {

    private CoinMarketCapCryptoListProvider newProvider(String apiKey) {
        CryptoListProperties props = new CryptoListProperties();
        props.getCoinmarketcap().setApiKey(apiKey);
        return new CoinMarketCapCryptoListProvider(props, new ObjectMapper());
    }

    @Test
    @DisplayName("keyless path uses the /public-api prefix; keyed path drops it")
    void buildPath() {
        assertThat(newProvider("").buildPath())
                .isEqualTo("/public-api/v3/cryptocurrency/listings/latest");
        assertThat(newProvider("KEY-123").buildPath())
                .isEqualTo("/v3/cryptocurrency/listings/latest");
    }

    @Test
    @DisplayName("parse maps listings/latest JSON with USD price and cmc_rank")
    void parse() {
        String json =
                """
                {
                  "status": {"error_code": 0},
                  "data": [
                    {"symbol":"BTC","name":"Bitcoin","cmc_rank":1,"quote":{"USD":{"price":95000.50}}},
                    {"symbol":"ETH","name":"Ethereum","cmc_rank":2,"quote":{"USD":{"price":3200}}}
                  ]
                }
                """;

        List<CryptoCurrencyInfo> result = newProvider("").parse(json);

        assertThat(result).extracting(CryptoCurrencyInfo::code).containsExactly("BTC", "ETH");
        assertThat(result.get(0).priceUsd()).isEqualByComparingTo("95000.50");
        assertThat(result.get(0).marketCapRank()).isEqualTo(1);
        assertThat(result.get(0).symbol()).isEqualTo("BTC");
    }
}
