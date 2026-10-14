package org.openfinance.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.openfinance.dto.HistoricalPrice;
import org.openfinance.dto.MarketQuote;
import org.openfinance.provider.MarketDataProvider;
import org.openfinance.repository.ExchangeRateRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@SpringBootTest
@ActiveProfiles("test")
class ExchangeRateWriteBoundaryIntegrationTest {
    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) throws Exception {
        String configured = System.getenv("SPRING_DATASOURCE_URL");
        if (configured == null || !configured.startsWith("jdbc:postgresql:")) {
            Path database =
                    Files.createTempFile(Path.of("target"), "exchange-write-boundary-", ".db");
            registry.add(
                    "spring.datasource.url",
                    () ->
                            "jdbc:sqlite:"
                                    + database
                                    + "?foreign_keys=on&journal_mode=WAL&busy_timeout=10000");
        }
    }

    @Autowired private ExchangeRateService service;
    @Autowired private ExchangeRateRepository rates;
    @Autowired private JdbcTemplate jdbc;
    @MockBean private MarketDataProvider provider;

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void savesRatesWhenAnotherWriterCommitsDuringRemoteFetch(boolean historical) throws Exception {
        LocalDate date = historical ? LocalDate.now().minusYears(5) : LocalDate.now();
        when(provider.getQuotes(anyList()))
                .thenAnswer(
                        call -> {
                            commitAnotherWrite();
                            return List.of(
                                    MarketQuote.builder()
                                            .symbol("EURUSD=X")
                                            .price(new BigDecimal("1.25"))
                                            .build());
                        });
        when(provider.getHistoricalPrices(anyString(), any(), any()))
                .thenAnswer(
                        call -> {
                            if (!"EURUSD=X".equals(call.getArgument(0))) return List.of();
                            commitAnotherWrite();
                            return List.of(
                                    HistoricalPrice.builder()
                                            .date(date)
                                            .close(new BigDecimal("1.25"))
                                            .build());
                        });

        int count =
                historical
                        ? service.updateExchangeRatesForDate(date)
                        : service.updateExchangeRates();

        assertThat(count).isEqualTo(2);
        assertThat(rates.findByBaseCurrencyAndTargetCurrencyAndRateDate("EUR", "USD", date))
                .hasValueSatisfying(
                        rate -> assertThat(rate.getRate()).isEqualByComparingTo("1.25"));
        assertThat(rates.findByBaseCurrencyAndTargetCurrencyAndRateDate("USD", "EUR", date))
                .hasValueSatisfying(rate -> assertThat(rate.getRate()).isEqualByComparingTo("0.8"));
    }

    private void commitAnotherWrite() throws Exception {
        CompletableFuture.runAsync(
                        () ->
                                jdbc.update(
                                        "UPDATE currencies SET is_active = is_active WHERE code = 'EUR'"))
                .get(10, TimeUnit.SECONDS);
    }
}
