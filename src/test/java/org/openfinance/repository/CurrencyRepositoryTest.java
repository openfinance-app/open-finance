package org.openfinance.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.openfinance.entity.Currency;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Integration tests for the CurrencyRepository.
 *
 * <p>Tests custom query methods and JPA repository functionality:
 *
 * <ul>
 *   <li>findByCode() - find currency by ISO code
 *   <li>findByIsActiveTrueOrderByCodeAsc() - find active currencies sorted
 *   <li>findAllByOrderByCodeAsc() - find all currencies sorted
 *   <li>existsByCode() - check currency existence
 *   <li>Basic CRUD operations inherited from JpaRepository
 * </ul>
 *
 * @author Open-Finance Development Team
 * @since 1.0
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test") // Uses SQLite test database
@DisplayName("Currency Repository Tests")
class CurrencyRepositoryTest {

    @Autowired private CurrencyRepository currencyRepository;

    @BeforeEach
    void setUp() {
        // Clean database before each test
        currencyRepository.deleteAll();
    }

    // ==================== findByCode() Tests ====================

    @Test
    @DisplayName("Should find currency by code")
    void shouldFindCurrencyByCode() {
        // Arrange
        Currency usd = createCurrency("USD", "US Dollar", "$", true);
        currencyRepository.save(usd);

        // Act
        Optional<Currency> result = currencyRepository.findByCode("USD");

        // Assert
        assertThat(result).isPresent();
        assertThat(result.get().getCode()).isEqualTo("USD");
        assertThat(result.get().getName()).isEqualTo("US Dollar");
        assertThat(result.get().getSymbol()).isEqualTo("$");
    }

    @Test
    @DisplayName("Should return empty when currency code not found")
    void shouldReturnEmptyWhenCodeNotFound() {
        // Act
        Optional<Currency> result = currencyRepository.findByCode("XXX");

        // Assert
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("Should be case sensitive when finding by code")
    void shouldBeCaseSensitiveWhenFindingByCode() {
        // Arrange
        Currency usd = createCurrency("USD", "US Dollar", "$", true);
        currencyRepository.save(usd);

        // Act
        Optional<Currency> result = currencyRepository.findByCode("usd");

        // Assert
        assertThat(result).isEmpty();
    }

    // ==================== findByIsActiveTrueOrderByCodeAsc() Tests ====================

    @Test
    @DisplayName("Should find only active currencies sorted by code")
    void shouldFindOnlyActiveCurrenciesSorted() {
        // Arrange
        Currency usd = createCurrency("USD", "US Dollar", "$", true);
        Currency eur = createCurrency("EUR", "Euro", "€", true);
        Currency gbp = createCurrency("GBP", "British Pound", "£", false); // inactive
        Currency jpy = createCurrency("JPY", "Japanese Yen", "¥", true);

        currencyRepository.saveAll(List.of(usd, jpy, gbp, eur));

        // Act
        List<Currency> activeCurrencies = currencyRepository.findByIsActiveTrueOrderByCodeAsc();

        // Assert
        assertThat(activeCurrencies).hasSize(3);
        assertThat(activeCurrencies)
                .extracting(Currency::getCode)
                .containsExactly("EUR", "JPY", "USD"); // sorted alphabetically
        assertThat(activeCurrencies).allMatch(Currency::getIsActive);
    }

    @Test
    @DisplayName("Should return empty list when no active currencies")
    void shouldReturnEmptyListWhenNoActiveCurrencies() {
        // Arrange
        Currency gbp = createCurrency("GBP", "British Pound", "£", false);
        Currency xau = createCurrency("XAU", "Gold Ounce", "XAU", false);
        currencyRepository.saveAll(List.of(gbp, xau));

        // Act
        List<Currency> activeCurrencies = currencyRepository.findByIsActiveTrueOrderByCodeAsc();

        // Assert
        assertThat(activeCurrencies).isEmpty();
    }

    // ==================== findAllByOrderByCodeAsc() Tests ====================

    @Test
    @DisplayName("Should find all currencies sorted by code")
    void shouldFindAllCurrenciesSorted() {
        // Arrange
        Currency usd = createCurrency("USD", "US Dollar", "$", true);
        Currency eur = createCurrency("EUR", "Euro", "€", true);
        Currency gbp = createCurrency("GBP", "British Pound", "£", false);

        currencyRepository.saveAll(List.of(usd, gbp, eur));

        // Act
        List<Currency> allCurrencies = currencyRepository.findAllByOrderByCodeAsc();

        // Assert
        assertThat(allCurrencies).hasSize(3);
        assertThat(allCurrencies)
                .extracting(Currency::getCode)
                .containsExactly("EUR", "GBP", "USD"); // sorted alphabetically
    }

    @Test
    @DisplayName("Should include both active and inactive currencies")
    void shouldIncludeBothActiveAndInactiveCurrencies() {
        // Arrange
        Currency usd = createCurrency("USD", "US Dollar", "$", true);
        Currency xau = createCurrency("XAU", "Gold Ounce", "XAU", false);

        currencyRepository.saveAll(List.of(usd, xau));

        // Act
        List<Currency> allCurrencies = currencyRepository.findAllByOrderByCodeAsc();

        // Assert
        assertThat(allCurrencies).hasSize(2);
        assertThat(allCurrencies).anyMatch(Currency::getIsActive);
        assertThat(allCurrencies).anyMatch(c -> !c.getIsActive());
    }

    @Test
    @DisplayName("Should return empty list when no currencies exist")
    void shouldReturnEmptyListWhenNoCurrenciesExist() {
        // Act
        List<Currency> allCurrencies = currencyRepository.findAllByOrderByCodeAsc();

        // Assert
        assertThat(allCurrencies).isEmpty();
    }

    // ==================== existsByCode() Tests ====================

    @Test
    @DisplayName("Should return true when currency code exists")
    void shouldReturnTrueWhenCodeExists() {
        // Arrange
        Currency usd = createCurrency("USD", "US Dollar", "$", true);
        currencyRepository.save(usd);

        // Act
        boolean exists = currencyRepository.existsByCode("USD");

        // Assert
        assertThat(exists).isTrue();
    }

    @Test
    @DisplayName("Should return false when currency code does not exist")
    void shouldReturnFalseWhenCodeDoesNotExist() {
        // Act
        boolean exists = currencyRepository.existsByCode("XXX");

        // Assert
        assertThat(exists).isFalse();
    }

    @Test
    @DisplayName("Should return true for inactive currency code")
    void shouldReturnTrueForInactiveCurrencyCode() {
        // Arrange
        Currency xau = createCurrency("XAU", "Gold Ounce", "XAU", false);
        currencyRepository.save(xau);

        // Act
        boolean exists = currencyRepository.existsByCode("XAU");

        // Assert
        assertThat(exists).isTrue();
    }

    // ==================== CRUD Operations Tests ====================

    @Test
    @DisplayName("Should save and retrieve currency")
    void shouldSaveAndRetrieveCurrency() {
        // Arrange
        Currency btc = createCurrency("BTC", "Bitcoin", "₿", true);

        // Act
        Currency saved = currencyRepository.save(btc);

        // Assert
        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getCode()).isEqualTo("BTC");

        // Verify retrieval
        Optional<Currency> retrieved = currencyRepository.findById(saved.getId());
        assertThat(retrieved).isPresent();
        assertThat(retrieved.get().getCode()).isEqualTo("BTC");
    }

    @Test
    @DisplayName("Should update currency")
    void shouldUpdateCurrency() {
        // Arrange
        Currency usd = createCurrency("USD", "US Dollar", "$", true);
        Currency saved = currencyRepository.save(usd);

        // Act
        saved.setIsActive(false);
        Currency updated = currencyRepository.save(saved);

        // Assert
        assertThat(updated.getIsActive()).isFalse();

        // Verify update persisted
        Optional<Currency> retrieved = currencyRepository.findById(saved.getId());
        assertThat(retrieved).isPresent();
        assertThat(retrieved.get().getIsActive()).isFalse();
    }

    @Test
    @DisplayName("Should delete currency")
    void shouldDeleteCurrency() {
        // Arrange
        Currency usd = createCurrency("USD", "US Dollar", "$", true);
        Currency saved = currencyRepository.save(usd);

        // Act
        currencyRepository.deleteById(saved.getId());

        // Assert
        Optional<Currency> deleted = currencyRepository.findById(saved.getId());
        assertThat(deleted).isEmpty();
    }

    // ==================== Helper Methods ====================

    private Currency createCurrency(String code, String name, String symbol, boolean isActive) {
        return Currency.builder().code(code).name(name).symbol(symbol).isActive(isActive).build();
    }
}
