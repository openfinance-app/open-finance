package org.openfinance.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.openfinance.entity.Currency;
import org.openfinance.entity.CurrencyType;
import org.openfinance.repository.CurrencyRepository;

@ExtendWith(MockitoExtension.class)
@DisplayName("CurrencyTypeResolver Tests")
class CurrencyTypeResolverTest {

    @Mock private CurrencyRepository currencyRepository;
    @InjectMocks private CurrencyTypeResolver resolver;

    private Currency currency(String code, CurrencyType type) {
        return Currency.builder()
                .code(code)
                .name(code)
                .symbol(code)
                .isActive(true)
                .type(type)
                .build();
    }

    @BeforeEach
    void seed() {
        when(currencyRepository.findAll())
                .thenReturn(
                        List.of(
                                currency("USD", CurrencyType.FIAT),
                                currency("JPY", CurrencyType.FIAT),
                                currency("BTC", CurrencyType.CRYPTO)));
    }

    @Test
    @DisplayName("isCrypto reflects the stored type, case-insensitively")
    void isCrypto() {
        assertThat(resolver.isCrypto("BTC")).isTrue();
        assertThat(resolver.isCrypto("btc")).isTrue();
        assertThat(resolver.isCrypto("USD")).isFalse();
        assertThat(resolver.isCrypto("UNKNOWN")).isFalse();
        assertThat(resolver.isCrypto(null)).isFalse();
    }

    @Test
    @DisplayName("decimalsFor returns 8 for crypto, 0 for zero-decimal fiat, 2 otherwise")
    void decimalsFor() {
        assertThat(resolver.decimalsFor("BTC")).isEqualTo(8);
        assertThat(resolver.decimalsFor("JPY")).isEqualTo(0);
        assertThat(resolver.decimalsFor("USD")).isEqualTo(2);
        assertThat(resolver.decimalsFor("  btc  ")).isEqualTo(8);
        assertThat(resolver.decimalsFor(null)).isEqualTo(2);
        assertThat(resolver.decimalsFor("XYZ")).isEqualTo(2);
    }

    @Test
    @DisplayName("reload() refreshes the snapshot so subsequent lookups reflect new data")
    void reloadRefreshesSnapshot() {
        // Overrides the @BeforeEach stub with consecutive returns.
        when(currencyRepository.findAll())
                .thenReturn(List.of(currency("BTC", CurrencyType.CRYPTO)))
                .thenReturn(List.of(currency("BTC", CurrencyType.FIAT)));

        assertThat(resolver.isCrypto("BTC")).isTrue();
        resolver.reload();
        assertThat(resolver.isCrypto("BTC")).isFalse();
    }
}
