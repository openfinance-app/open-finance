package org.openfinance.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;

import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.openfinance.config.ExchangeRateProperties;
import org.openfinance.entity.User;
import org.openfinance.repository.UserRepository;

/**
 * Unit tests for {@link DefaultCurrencyProvider} — the single source of truth for the application
 * default currency and per-user base-currency resolution.
 */
@ExtendWith(MockitoExtension.class)
class DefaultCurrencyProviderTest {

    @Mock private UserRepository userRepository;

    private ExchangeRateProperties properties;
    private DefaultCurrencyProvider provider;

    @BeforeEach
    void setUp() {
        properties = new ExchangeRateProperties();
        properties.setBaseCurrency("EUR");
        provider = new DefaultCurrencyProvider(properties, userRepository);
    }

    @Test
    @DisplayName("getDefaultCurrency returns the configured application base currency")
    void getDefaultCurrencyReturnsConfiguredValue() {
        assertThat(provider.getDefaultCurrency()).isEqualTo("EUR");
    }

    @Test
    @DisplayName("getDefaultCurrency reflects a changed configuration value")
    void getDefaultCurrencyReflectsConfig() {
        properties.setBaseCurrency("USD");
        assertThat(provider.getDefaultCurrency()).isEqualTo("USD");
    }

    @Test
    @DisplayName("resolve returns the supplied currency when it is a non-blank value")
    void resolveReturnsSuppliedCurrency() {
        assertThat(provider.resolve("GBP")).isEqualTo("GBP");
    }

    @Test
    @DisplayName("resolve falls back to the default currency when null")
    void resolveFallsBackWhenNull() {
        assertThat(provider.resolve(null)).isEqualTo("EUR");
    }

    @Test
    @DisplayName("resolve falls back to the default currency when blank")
    void resolveFallsBackWhenBlank() {
        assertThat(provider.resolve("   ")).isEqualTo("EUR");
    }

    @Test
    @DisplayName("resolveForUser returns the user's configured base currency")
    void resolveForUserReturnsUserCurrency() {
        User user = User.builder().id(1L).baseCurrency("JPY").build();
        lenient().when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        assertThat(provider.resolveForUser(1L)).isEqualTo("JPY");
    }

    @Test
    @DisplayName("resolveForUser falls back to default when the user's base currency is blank")
    void resolveForUserFallsBackWhenUserCurrencyBlank() {
        User user = User.builder().id(1L).baseCurrency("  ").build();
        lenient().when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        assertThat(provider.resolveForUser(1L)).isEqualTo("EUR");
    }

    @Test
    @DisplayName("resolveForUser falls back to default when the user is not found")
    void resolveForUserFallsBackWhenUserMissing() {
        lenient().when(userRepository.findById(99L)).thenReturn(Optional.empty());

        assertThat(provider.resolveForUser(99L)).isEqualTo("EUR");
    }

    @Test
    @DisplayName("resolveForUser falls back to default when userId is null")
    void resolveForUserFallsBackWhenUserIdNull() {
        assertThat(provider.resolveForUser(null)).isEqualTo("EUR");
    }
}
