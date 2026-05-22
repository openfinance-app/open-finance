package org.openfinance.controller;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.openfinance.entity.Currency;
import org.openfinance.repository.CurrencyRepository;
import org.openfinance.service.OperationHistoryService;
import org.openfinance.util.DatabaseCleanupService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * Integration tests for currency localization (i18n) feature.
 *
 * <p>Tests verify that currency names are properly localized based on the Accept-Language header
 * and that the nameKey field is included in responses.
 *
 * @author Open-Finance Development Team
 * @since Phase 3.2 of i18n & Localization feature
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class CurrencyLocalizationIntegrationTest {

    @MockBean private OperationHistoryService operationHistoryService;

    @Autowired private MockMvc mockMvc;

    @Autowired private CurrencyRepository currencyRepository;

    @Autowired private DatabaseCleanupService databaseCleanupService;

    @PersistenceContext private EntityManager entityManager;

    @BeforeEach
    void setUp() {
        // Seed test currencies with nameKey
        databaseCleanupService.execute();
        entityManager.flush();

        currencyRepository.save(
                Currency.builder()
                        .code("USD")
                        .name("US Dollar")
                        .symbol("$")
                        .nameKey("currency.usd")
                        .isActive(true)
                        .build());

        currencyRepository.save(
                Currency.builder()
                        .code("EUR")
                        .name("Euro")
                        .symbol("€")
                        .nameKey("currency.eur")
                        .isActive(true)
                        .build());

        currencyRepository.save(
                Currency.builder()
                        .code("GBP")
                        .name("British Pound")
                        .symbol("£")
                        .nameKey("currency.gbp")
                        .isActive(true)
                        .build());

        currencyRepository.save(
                Currency.builder()
                        .code("JPY")
                        .name("Japanese Yen")
                        .symbol("¥")
                        .nameKey("currency.jpy")
                        .isActive(true)
                        .build());

        currencyRepository.save(
                Currency.builder()
                        .code("CHF")
                        .name("Swiss Franc")
                        .symbol("CHF")
                        .nameKey("currency.chf")
                        .isActive(true)
                        .build());

        currencyRepository.save(
                Currency.builder()
                        .code("CAD")
                        .name("Canadian Dollar")
                        .symbol("CA$")
                        .nameKey("currency.cad")
                        .isActive(true)
                        .build());

        currencyRepository.save(
                Currency.builder()
                        .code("AUD")
                        .name("Australian Dollar")
                        .symbol("A$")
                        .nameKey("currency.aud")
                        .isActive(true)
                        .build());

        currencyRepository.save(
                Currency.builder()
                        .code("BTC")
                        .name("Bitcoin")
                        .symbol("₿")
                        .nameKey("currency.btc")
                        .isActive(true)
                        .build());

        currencyRepository.save(
                Currency.builder()
                        .code("ETH")
                        .name("Ethereum")
                        .symbol("Ξ")
                        .nameKey("currency.eth")
                        .isActive(true)
                        .build());

        currencyRepository.save(
                Currency.builder()
                        .code("CRC")
                        .name("Costa Rican Colón")
                        .symbol("CRC")
                        .nameKey("currency.crc")
                        .isActive(true)
                        .build());

        currencyRepository.save(
                Currency.builder()
                        .code("NIO")
                        .name("Nicaraguan Córdoba")
                        .symbol("NIO")
                        .nameKey("currency.nio")
                        .isActive(true)
                        .build());

        currencyRepository.save(
                Currency.builder()
                        .code("XAU")
                        .name("Gold")
                        .symbol("XAU")
                        .nameKey("currency.xau")
                        .isActive(true)
                        .build());

        currencyRepository.save(
                Currency.builder()
                        .code("XAG")
                        .name("Silver")
                        .symbol("XAG")
                        .nameKey("currency.xag")
                        .isActive(true)
                        .build());

        currencyRepository.save(
                Currency.builder()
                        .code("XPT")
                        .name("Platinum")
                        .symbol("XPT")
                        .nameKey("currency.xpt")
                        .isActive(true)
                        .build());

        currencyRepository.save(
                Currency.builder()
                        .code("XPD")
                        .name("Palladium")
                        .symbol("XPD")
                        .nameKey("currency.xpd")
                        .isActive(true)
                        .build());
    }

    /** Test that currencies endpoint returns nameKey field in responses. */
    @Test
    @WithMockUser
    void shouldIncludeNameKeyInCurrencyResponse() throws Exception {
        mockMvc.perform(get("/api/v1/currencies").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].code").exists())
                .andExpect(jsonPath("$[0].name").exists())
                .andExpect(jsonPath("$[0].symbol").exists())
                .andExpect(jsonPath("$[0].nameKey").exists());
    }

    /** Test that USD currency name is in English by default. */
    @Test
    @WithMockUser
    void shouldReturnEnglishCurrencyNamesWithoutAcceptLanguageHeader() throws Exception {
        mockMvc.perform(get("/api/v1/currencies").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.code=='USD')].name").value("US Dollar"))
                .andExpect(jsonPath("$[?(@.code=='EUR')].name").value("Euro"))
                .andExpect(jsonPath("$[?(@.code=='GBP')].name").value("British Pound"));
    }

    /** Test that currency names are localized to English when Accept-Language is 'en'. */
    @Test
    @WithMockUser
    void shouldReturnEnglishCurrencyNamesWhenAcceptLanguageIsEn() throws Exception {
        mockMvc.perform(
                        get("/api/v1/currencies")
                                .header("Accept-Language", "en")
                                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.code=='USD')].name").value("US Dollar"))
                .andExpect(jsonPath("$[?(@.code=='EUR')].name").value("Euro"))
                .andExpect(jsonPath("$[?(@.code=='GBP')].name").value("British Pound"))
                .andExpect(jsonPath("$[?(@.code=='JPY')].name").value("Japanese Yen"))
                .andExpect(jsonPath("$[?(@.code=='CHF')].name").value("Swiss Franc"));
    }

    /** Test that currency names are localized to French when Accept-Language is 'fr'. */
    @Test
    @WithMockUser
    void shouldReturnFrenchCurrencyNamesWhenAcceptLanguageIsFr() throws Exception {
        mockMvc.perform(
                        get("/api/v1/currencies")
                                .header("Accept-Language", "fr")
                                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.code=='USD')].name").value("Dollar américain"))
                .andExpect(jsonPath("$[?(@.code=='EUR')].name").value("Euro"))
                .andExpect(jsonPath("$[?(@.code=='GBP')].name").value("Livre sterling"))
                .andExpect(jsonPath("$[?(@.code=='JPY')].name").value("Yen japonais"))
                .andExpect(jsonPath("$[?(@.code=='CHF')].name").value("Franc suisse"));
    }

    /** Test that cryptocurrency names are also localized correctly. */
    @Test
    @WithMockUser
    void shouldLocalizeUSDCryptocurrencyNames() throws Exception {
        mockMvc.perform(
                        get("/api/v1/currencies")
                                .header("Accept-Language", "en")
                                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.code=='BTC')].name").value("Bitcoin"))
                .andExpect(jsonPath("$[?(@.code=='ETH')].name").value("Ethereum"));
    }

    /** Test that nameKey follows the correct pattern (currency.{code}). */
    @Test
    @WithMockUser
    void shouldHaveCorrectNameKeyFormat() throws Exception {
        mockMvc.perform(get("/api/v1/currencies").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.code=='USD')].nameKey").value("currency.usd"))
                .andExpect(jsonPath("$[?(@.code=='EUR')].nameKey").value("currency.eur"))
                .andExpect(jsonPath("$[?(@.code=='BTC')].nameKey").value("currency.btc"));
    }

    /** Test that /all endpoint also returns localized names. */
    @Test
    @WithMockUser
    void shouldLocalizeAllCurrenciesEndpoint() throws Exception {
        mockMvc.perform(
                        get("/api/v1/currencies/all")
                                .header("Accept-Language", "fr")
                                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.code=='CAD')].name").value("Dollar canadien"))
                .andExpect(jsonPath("$[?(@.code=='AUD')].name").value("Dollar australien"));
    }

    /**
     * Test that fallback to stored name works if translation is missing. This ensures robustness
     * even if a nameKey doesn't have a translation.
     */
    @Test
    @WithMockUser
    void shouldFallbackToStoredNameIfTranslationMissing() throws Exception {
        // Even with an unsupported locale, should return English (stored name)
        mockMvc.perform(
                        get("/api/v1/currencies")
                                .header("Accept-Language", "xx-XX") // Invalid locale
                                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.code=='USD')].name").value("US Dollar"));
    }

    /** Test that currencies with special characters are handled correctly. */
    @Test
    @WithMockUser
    void shouldHandleSpecialCharactersInFrenchNames() throws Exception {
        mockMvc.perform(
                        get("/api/v1/currencies")
                                .header("Accept-Language", "fr")
                                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                // Test currencies with French accents and special characters
                .andExpect(jsonPath("$[?(@.code=='CRC')].name").value("Colón costaricien"))
                .andExpect(jsonPath("$[?(@.code=='NIO')].name").value("Córdoba nicaraguayen"));
    }

    /** Test that locale preference works with region variants (e.g., fr-FR, en-US). */
    @Test
    @WithMockUser
    void shouldHandleLocaleWithRegionVariants() throws Exception {
        // Test fr-FR (French France)
        mockMvc.perform(
                        get("/api/v1/currencies")
                                .header("Accept-Language", "fr-FR")
                                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.code=='EUR')].name").value("Euro"));

        // Test en-US (English US)
        mockMvc.perform(
                        get("/api/v1/currencies")
                                .header("Accept-Language", "en-US")
                                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.code=='EUR')].name").value("Euro"));
    }

    /** Test that precious metals are localized correctly. */
    @Test
    @WithMockUser
    void shouldLocalizePreciousMetals() throws Exception {
        mockMvc.perform(
                        get("/api/v1/currencies")
                                .header("Accept-Language", "fr")
                                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.code=='XAU')].name").value("Or"))
                .andExpect(jsonPath("$[?(@.code=='XAG')].name").value("Argent"))
                .andExpect(jsonPath("$[?(@.code=='XPT')].name").value("Platine"))
                .andExpect(jsonPath("$[?(@.code=='XPD')].name").value("Palladium"));
    }
}
