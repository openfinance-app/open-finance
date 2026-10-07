package org.openfinance.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.openfinance.dto.HistoricalPrice;
import org.openfinance.entity.ExchangeRate;
import org.openfinance.provider.MarketDataProvider;
import org.openfinance.repository.ExchangeRateRepository;
import org.openfinance.service.history.HistoryStateStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** Encrypted persistence and warmed-cache regressions for the October UI audit. */
@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
class FinanceManagementCorrectionsIntegrationTest {
    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper mapper;
    @Autowired private ExchangeRateRepository exchangeRates;

    @Autowired private PlatformTransactionManager transactionManager;

    @Autowired private ExchangeRateService exchangeRateService;
    @Autowired private HistoryStateStore historyStateStore;

    @MockitoBean private MarketDataProvider marketDataProvider;

    private String token;
    private String session;
    private final LocalDate today = LocalDate.now();

    @BeforeEach
    void register() throws Exception {
        String username = "fix" + UUID.randomUUID().toString().substring(0, 8);
        json(
                "POST",
                "/auth/register",
                Map.of(
                        "username",
                        username,
                        "email",
                        username + "@example.invalid",
                        "password",
                        "LoginPassword123!",
                        "masterPassword",
                        "SourceMaster123!",
                        "skipSeeding",
                        true),
                201);
        JsonNode auth =
                json(
                        "POST",
                        "/auth/login",
                        Map.of(
                                "username",
                                username,
                                "password",
                                "LoginPassword123!",
                                "masterPassword",
                                "SourceMaster123!"),
                        200);
        token = auth.get("token").asText();
        session = auth.get("encryptionKey").asText();
    }

    @Test
    void quarterlyAccountsPersistAndUseFourCompounds() throws Exception {
        Map<String, Object> body =
                accountBody("Quarterly", "SAVINGS", "EUR", "1000", today.minusDays(2));
        body.put("isInterestEnabled", true);
        body.put("interestPeriod", "QUARTERLY");
        long id = json("POST", "/accounts", body, 201).get("id").asLong();
        rate(id, "4", "0", today.minusDays(2));
        assertThat(json("GET", "/accounts/" + id, null, 200).get("interestPeriod").asText())
                .isEqualTo("QUARTERLY");
        assertThat(
                        json("GET", "/accounts/" + id + "/interest-estimate", null, 200)
                                .get("estimate")
                                .decimalValue())
                .isEqualByComparingTo("40.60");
    }

    @Test
    void rateBreakdownAndCustomInterestUseDatedCashAndExcludeFutureRates() throws Exception {
        Map<String, Object> body =
                accountBody("Dated cash", "SAVINGS", "EUR", "1000", today.minusDays(2));
        body.put("isInterestEnabled", true);
        body.put("interestPeriod", "ANNUAL");
        long id = json("POST", "/accounts", body, 201).get("id").asLong();
        rate(id, "36.5", "25", today.minusDays(2));
        rate(id, "73", "0", today.plusMonths(6));
        transaction(id, "INCOME", "1000", today.minusDays(1));
        JsonNode rates = json("GET", "/accounts/" + id + "/interest-variations", null, 200);
        assertThat(rates.get(0).get("activeDays").asLong()).isZero();
        assertThat(rates.get(0).get("interestProduced").decimalValue()).isZero();
        assertThat(rates.get(1).get("activeDays").asLong()).isEqualTo(3);
        assertThat(rates.get(1).get("interestProduced").decimalValue())
                .isEqualByComparingTo("3.75");
        JsonNode summary = interest(today.minusDays(2), today.minusDays(2));
        assertThat(summary.get("totalEarned").decimalValue()).isEqualByComparingTo("0.75");
        assertThat(
                        interest(today.minusDays(10), today.minusDays(3))
                                .get("totalEarned")
                                .decimalValue())
                .isZero();
        assertThat(interest(today, today.plusMonths(3)).get("totalEarned").decimalValue())
                .isEqualByComparingTo("1.50");
        json("GET", "/dashboard/estimated-interest?startDate=" + today, null, 400);
        json(
                "GET",
                "/dashboard/estimated-interest?startDate="
                        + today
                        + "&endDate="
                        + today.minusDays(1),
                null,
                400);
    }

    @Test
    void representedDebtAccruesFromNegativeCashAndCountsDebtOnce() throws Exception {
        long card =
                json(
                                "POST",
                                "/accounts",
                                accountBody(
                                        "Card",
                                        "CREDIT_CARD",
                                        "EUR",
                                        "-350.50",
                                        today.minusDays(2)),
                                201)
                        .get("id")
                        .asLong();
        json(
                "POST",
                "/liabilities",
                Map.of(
                        "name",
                        "Card terms",
                        "type",
                        "CREDIT_CARD",
                        "principal",
                        "0",
                        "currentBalance",
                        "350.50",
                        "currency",
                        "EUR",
                        "startDate",
                        today.minusDays(2),
                        "representedByAccountId",
                        card,
                        "interestRate",
                        "18"),
                201);
        transaction(card, "INCOME", "50", today.minusDays(1));
        JsonNode summary = json("GET", "/dashboard/estimated-interest?period=ALL", null, 200);
        assertThat(summary.get("totalProjected").decimalValue()).isEqualByComparingTo("-54.09");
        assertThat(summary.get("totalEarned").decimalValue()).isEqualByComparingTo("-0.47");
        assertThat(
                        json("GET", "/dashboard/summary", null, 200)
                                .get("netWorth")
                                .get("totalLiabilities")
                                .decimalValue())
                .isEqualByComparingTo("300.50");
        transaction(card, "INCOME", "400", today);
        summary = json("GET", "/dashboard/estimated-interest?period=ALL", null, 200);
        assertThat(summary.get("totalProjected").decimalValue()).isZero();
        assertThat(summary.get("totalEarned").decimalValue()).isEqualByComparingTo("-0.32");
        assertThat(
                        interest(today.minusDays(2), today.minusDays(2))
                                .get("totalEarned")
                                .decimalValue())
                .isEqualByComparingTo("-0.17");
    }

    @Test
    void oneDayDashboardIncludesOnlyTodayAcrossCardsAndHistory() throws Exception {
        long account =
                json(
                                "POST",
                                "/accounts",
                                accountBody("Cash", "CHECKING", "EUR", "1000", today.minusDays(10)),
                                201)
                        .get("id")
                        .asLong();
        transaction(account, "INCOME", "200", today.minusDays(1));
        transaction(account, "INCOME", "10", today);
        transaction(account, "EXPENSE", "8", today.minusDays(1));
        transaction(account, "EXPENSE", "3", today);
        JsonNode cash = json("GET", "/dashboard/cashflow?period=1", null, 200);
        assertThat(cash.get("income").decimalValue()).isEqualByComparingTo("10");
        assertThat(cash.get("expenses").decimalValue()).isEqualByComparingTo("3");
        JsonNode history =
                json("GET", "/dashboard/networth-history?period=1&recalculate=true", null, 200);
        assertThat(history).hasSize(1);
        assertThat(history.get(0).get("date").asText()).isEqualTo(today.toString());
        JsonNode borrowing = json("GET", "/dashboard/borrowing-capacity?period=1", null, 200);
        assertThat(borrowing.get("monthlyIncome").decimalValue()).isEqualByComparingTo("300");
        JsonNode sankey = json("GET", "/dashboard/cashflow-sankey?period=1", null, 200);
        assertThat(sankey.get("totalIncome").decimalValue()).isEqualByComparingTo("10");
    }

    @Test
    void improvementCreateEditDeleteRefreshWarmedAssetAllocation() throws Exception {
        long cash =
                json(
                                "POST",
                                "/accounts",
                                accountBody(
                                        "Cash", "CHECKING", "EUR", "1000", today.minusMonths(6)),
                                201)
                        .get("id")
                        .asLong();
        long home =
                json(
                                "POST",
                                "/real-estate",
                                Map.of(
                                        "name",
                                        "Home",
                                        "propertyType",
                                        "RESIDENTIAL",
                                        "address",
                                        "Audit address",
                                        "purchasePrice",
                                        100,
                                        "currentValue",
                                        100,
                                        "currency",
                                        "EUR",
                                        "purchaseDate",
                                        today.minusMonths(3)),
                                201)
                        .get("id")
                        .asLong();
        assertAllocation("100");
        Map<String, Object> body =
                new HashMap<>(
                        Map.of(
                                "accountId",
                                cash,
                                "type",
                                "EXPENSE",
                                "amount",
                                10,
                                "currency",
                                "EUR",
                                "date",
                                today,
                                "realEstateId",
                                home,
                                "movementType",
                                "CAPITAL_IMPROVEMENT"));
        long tx = json("POST", "/transactions", body, 201).get("id").asLong();
        assertAllocation("110");
        body.put("amount", 15);
        json("PUT", "/transactions/" + tx, body, 200);
        assertAllocation("115");
        json("DELETE", "/transactions/" + tx, null, 204);
        assertAllocation("100");
    }

    @Test
    void fourLetterCatalogCurrencyCanBeCreatedAndEditedWithExplicitBalanceUnits() throws Exception {
        Map<String, Object> body = accountBody("Tether", "CHECKING", "USDT", "0", today);
        long id = json("POST", "/accounts", body, 201).get("id").asLong();
        body.put("balanceCurrency", "USDT");
        body.put("name", "Edited Tether");
        assertThat(json("PUT", "/accounts/" + id, body, 200).get("currency").asText())
                .isEqualTo("USDT");
        body.put("balanceCurrency", "UNKNOWN");
        json("PUT", "/accounts/" + id, body, 400);
    }

    @Test
    void catalogCurrencyPersistsAcrossTransactionsAssetsPropertyDebtAndRecurringTemplates()
            throws Exception {
        exchangeRates.save(
                ExchangeRate.builder()
                        .baseCurrency("USDT")
                        .targetCurrency("EUR")
                        .rate(new BigDecimal("0.9"))
                        .rateDate(today)
                        .source("audit-fixture")
                        .build());
        long cash =
                json(
                                "POST",
                                "/accounts",
                                accountBody("Crypto cash", "CHECKING", "USDT", "0", today),
                                201)
                        .get("id")
                        .asLong();
        json(
                "POST",
                "/transactions",
                Map.of(
                        "accountId",
                        cash,
                        "type",
                        "INCOME",
                        "amount",
                        "10",
                        "currency",
                        "USDT",
                        "date",
                        today),
                201);
        assertThat(json("GET", "/accounts/" + cash, null, 200).get("ownBalance").decimalValue())
                .isEqualByComparingTo("10");
        JsonNode asset =
                json(
                        "POST",
                        "/assets",
                        Map.of(
                                "name",
                                "Fifty satoshis",
                                "type",
                                "CRYPTO",
                                "quantity",
                                "0.0000005",
                                "purchasePrice",
                                "60000",
                                "currentPrice",
                                "80000",
                                "currency",
                                "USDT",
                                "purchaseDate",
                                today,
                                "acquisitionType",
                                "GIFT"),
                        201);
        assertThat(asset.get("quantity").decimalValue()).isEqualByComparingTo("0.0000005");
        assertThat(asset.get("totalValue").decimalValue()).isEqualByComparingTo("0.04");
        long debt =
                json(
                                "POST",
                                "/liabilities",
                                Map.of(
                                        "name",
                                        "Crypto loan",
                                        "type",
                                        "PERSONAL_LOAN",
                                        "principal",
                                        "5",
                                        "currentBalance",
                                        "0",
                                        "currency",
                                        "USDT",
                                        "startDate",
                                        today),
                                201)
                        .get("id")
                        .asLong();
        json("POST", "/liabilities/" + debt + "/tranches", Map.of("plannedAmount", "1"), 201);
        json(
                "POST",
                "/real-estate",
                Map.of(
                        "name",
                        "Crypto property",
                        "propertyType",
                        "RESIDENTIAL",
                        "address",
                        "Audit address",
                        "purchasePrice",
                        "0",
                        "currentValue",
                        "2",
                        "currency",
                        "USDT",
                        "purchaseDate",
                        today,
                        "acquisitionType",
                        "GIFT"),
                201);
        json(
                "POST",
                "/recurring-transactions",
                Map.of(
                        "accountId",
                        cash,
                        "type",
                        "INCOME",
                        "amount",
                        "1",
                        "currency",
                        "USDT",
                        "description",
                        "Monthly USDT",
                        "frequency",
                        "MONTHLY",
                        "nextOccurrence",
                        today.plusDays(1)),
                201);
    }

    @Test
    void concurrentQuoteWritesKeepBothCallingTransactionsUsable() throws Exception {
        LocalDate date = today.minusYears(20);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        Callable<BigDecimal> write =
                () -> {
                    ready.countDown();
                    assertThat(start.await(10, TimeUnit.SECONDS)).isTrue();
                    return new TransactionTemplate(transactionManager)
                            .execute(
                                    status -> {
                                        exchangeRates.upsertAll(
                                                List.of(
                                                        ExchangeRate.builder()
                                                                .baseCurrency("USDT")
                                                                .targetCurrency("EUR")
                                                                .rate(new BigDecimal("0.9"))
                                                                .rateDate(date)
                                                                .source("concurrent-preview")
                                                                .build()));
                                        return exchangeRates
                                                .findByBaseCurrencyAndTargetCurrencyAndRateDate(
                                                        "USDT", "EUR", date)
                                                .orElseThrow()
                                                .getRate();
                                    });
                };
        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Future<BigDecimal> first = executor.submit(write);
            Future<BigDecimal> second = executor.submit(write);
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            assertThat(first.get(20, TimeUnit.SECONDS)).isEqualByComparingTo("0.9");
            assertThat(second.get(20, TimeUnit.SECONDS)).isEqualByComparingTo("0.9");
        }
        assertThat(exchangeRates.findByRateDate(date))
                .filteredOn(
                        rate ->
                                "USDT".equals(rate.getBaseCurrency())
                                        && "EUR".equals(rate.getTargetCurrency()))
                .hasSize(1);
    }

    @Test
    void coldQuotesDoNotWriteInsideHistorySnapshots() throws Exception {
        LocalDate date = today.minusYears(21);
        CyclicBarrier fetched = new CyclicBarrier(2);
        org.mockito.Mockito.when(
                        marketDataProvider.getHistoricalPrices(
                                org.mockito.ArgumentMatchers.eq("USDT-USD"),
                                org.mockito.ArgumentMatchers.any(),
                                org.mockito.ArgumentMatchers.eq(date)))
                .thenAnswer(
                        call -> {
                            fetched.await(10, TimeUnit.SECONDS);
                            return List.of(
                                    HistoricalPrice.builder()
                                            .symbol("USDT-USD")
                                            .date(date)
                                            .close(new BigDecimal("1.25"))
                                            .build());
                        });
        Callable<BigDecimal> convert =
                () -> {
                    TransactionTemplate transaction = new TransactionTemplate(transactionManager);
                    transaction.setIsolationLevel(historyStateStore.captureIsolation());
                    return transaction.execute(
                            status -> {
                                // Establish the snapshot before either caller fetches the missing
                                // quote.
                                assertThat(
                                                exchangeRates
                                                        .findByBaseCurrencyAndTargetCurrencyAndRateDate(
                                                                "USD", "USDT", date))
                                        .isEmpty();
                                BigDecimal result =
                                        exchangeRateService.getExchangeRate("USD", "USDT", date);
                                // The result is usable now; quote caching happens only after this
                                // commit.
                                assertThat(
                                                exchangeRates
                                                        .findByBaseCurrencyAndTargetCurrencyAndRateDate(
                                                                "USD", "USDT", date))
                                        .isEmpty();
                                return result;
                            });
                };
        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Future<BigDecimal> first = executor.submit(convert);
            Future<BigDecimal> second = executor.submit(convert);
            assertThat(first.get(20, TimeUnit.SECONDS)).isEqualByComparingTo("0.8");
            assertThat(second.get(20, TimeUnit.SECONDS)).isEqualByComparingTo("0.8");
        }
        assertThat(
                        exchangeRates
                                .findByBaseCurrencyAndTargetCurrencyAndRateDate("USD", "USDT", date)
                                .orElseThrow()
                                .getRate())
                .isEqualByComparingTo("0.8");
    }

    private void assertAllocation(String expected) throws Exception {
        JsonNode allocation = json("GET", "/dashboard/asset-allocation", null, 200);
        assertThat(allocation).hasSize(1);
        assertThat(allocation.get(0).get("totalValue").decimalValue())
                .isEqualByComparingTo(expected);
    }

    private JsonNode interest(LocalDate from, LocalDate to) throws Exception {
        return json(
                "GET",
                "/dashboard/estimated-interest?startDate=" + from + "&endDate=" + to,
                null,
                200);
    }

    private Map<String, Object> accountBody(
            String name, String type, String currency, String balance, LocalDate opening) {
        return new HashMap<>(
                Map.of(
                        "name",
                        name,
                        "type",
                        type,
                        "currency",
                        currency,
                        "initialBalance",
                        balance,
                        "openingDate",
                        opening));
    }

    private void rate(long id, String rate, String tax, LocalDate from) throws Exception {
        json(
                "POST",
                "/accounts/" + id + "/interest-variations",
                Map.of("rate", rate, "taxRate", tax, "validFrom", from),
                201);
    }

    private void transaction(long account, String type, String amount, LocalDate date)
            throws Exception {
        json(
                "POST",
                "/transactions",
                Map.of(
                        "accountId",
                        account,
                        "type",
                        type,
                        "amount",
                        amount,
                        "currency",
                        "EUR",
                        "date",
                        date),
                201);
    }

    private JsonNode json(String method, String path, Object body, int expected) throws Exception {
        MockHttpServletRequestBuilder request =
                request(HttpMethod.valueOf(method), "/api/v1" + path);
        if (token != null)
            request.header("Authorization", "Bearer " + token)
                    .header("X-Encryption-Session", session);
        if (body != null)
            request.contentType(MediaType.APPLICATION_JSON)
                    .content(
                            mapper.copy()
                                    .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                                    .writeValueAsBytes(body));
        org.springframework.mock.web.MockHttpServletResponse response =
                mvc.perform(request).andReturn().getResponse();
        String result = response.getContentAsString();
        assertThat(response.getStatus()).as("%s %s: %s", method, path, result).isEqualTo(expected);
        return result.isBlank() ? mapper.nullNode() : mapper.readTree(result);
    }
}
