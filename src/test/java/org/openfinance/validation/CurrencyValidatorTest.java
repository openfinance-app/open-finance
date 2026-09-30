package org.openfinance.validation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.openfinance.entity.Currency;
import org.openfinance.repository.CurrencyRepository;

@ExtendWith(MockitoExtension.class)
class CurrencyValidatorTest {
    @Mock private CurrencyRepository currencies;

    @Test
    void acceptsActiveCatalogCryptoAndRejectsUnknownOrInactiveCodes() {
        CurrencyValidator validator = new CurrencyValidator(currencies);
        when(currencies.findByCode("BTC"))
                .thenReturn(Optional.of(Currency.builder().code("BTC").isActive(true).build()));
        when(currencies.findByCode("OLD"))
                .thenReturn(Optional.of(Currency.builder().code("OLD").isActive(false).build()));
        assertThat(validator.isValid("btc", null)).isTrue();
        assertThat(validator.isValid("EUR", null)).isTrue();
        assertThat(validator.isValid("OLD", null)).isFalse();
        assertThat(validator.isValid("INVALID", null)).isFalse();
    }
}
