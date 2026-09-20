package org.openfinance.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.openfinance.entity.ExchangeRate;
import org.openfinance.entity.NetWorth;
import org.openfinance.entity.RecurringFrequency;
import org.openfinance.entity.RecurringTransaction;
import org.openfinance.repository.ExchangeRateRepository;
import org.openfinance.repository.NetWorthRepository;
import org.openfinance.security.EncryptionContext;
import org.openfinance.security.EncryptionKeyCache;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.CacheManager;

/** Authenticated regressions for the September codebase remediation. */
class AuditCodebaseRemediationIntegrationTest extends AuditApiTestSupport {
    @Autowired ExchangeRateRepository rates;
    @Autowired NetWorthRepository snapshots;
    @Autowired EncryptionKeyCache keys;
    @Autowired CacheManager caches;

    @BeforeEach
    void configureAudit() {
        jdbc.update("UPDATE users SET base_currency = 'EUR' WHERE id = ?", owner.id());
        caches.getCacheNames().forEach(name -> caches.getCache(name).clear());
        for (LocalDate date : List.of(START, LocalDate.now())) {
            ExchangeRate rate =
                    rates.findByBaseCurrencyAndTargetCurrencyAndRateDate("EUR", "USD", date)
                            .orElse(
                                    ExchangeRate.builder()
                                            .baseCurrency("EUR")
                                            .targetCurrency("USD")
                                            .rateDate(date)
                                            .source("audit-fixed")
                                            .build());
            rate.setRate(new BigDecimal("2"));
            rates.saveAndFlush(rate);
        }
    }

    @Test
    void auditCurrencyChangeRefreshesWarmedPortfolio() throws Exception {
        json(
                "POST",
                "/assets",
                data(
                        "name",
                        "Audit holding",
                        "type",
                        "FURNITURE",
                        "quantity",
                        1,
                        "purchasePrice",
                        1000,
                        "currentPrice",
                        1000,
                        "currency",
                        "EUR",
                        "purchaseDate",
                        START.toString()),
                owner,
                201);
        JsonNode before =
                json("GET", "/dashboard/portfolio-performance?period=30", null, owner, 200);
        json("PUT", "/users/me/base-currency", data("baseCurrency", "USD"), owner, 200);
        JsonNode after =
                json("GET", "/dashboard/portfolio-performance?period=30", null, owner, 200);
        System.out.println("AUDIT_PORTFOLIO before=" + before + " after=" + after);
        assertThat(after.get(0).path("currency").asText()).isEqualTo("USD");
        money(after.get(0), "currentValue", "2000");
    }

    @Test
    void auditCurrencyChangeDoesNotInventNetWorthGain() throws Exception {
        account(owner);
        EncryptionContext.setKey(keys.getKey(owner.id()).orElseThrow());
        try {
            snapshots.saveAndFlush(
                    NetWorth.builder()
                            .userId(owner.id())
                            .snapshotDate(LocalDate.now().minusMonths(1))
                            .totalAssets(new BigDecimal("1000"))
                            .totalLiabilities(BigDecimal.ZERO)
                            .netWorth(new BigDecimal("1000"))
                            .currency("EUR")
                            .build());
        } finally {
            EncryptionContext.clear();
        }
        json("PUT", "/users/me/base-currency", data("baseCurrency", "USD"), owner, 200);
        JsonNode summary = json("GET", "/dashboard", null, owner, 200);
        System.out.println("AUDIT_NETWORTH " + summary.path("netWorth"));
        money(summary.path("netWorth"), "netWorth", "2000");
        money(summary.path("netWorth"), "monthlyChangeAmount", "0");
    }

    @Test
    void auditUnknownBaseCurrencyIsRejected() throws Exception {
        json("PUT", "/users/me/base-currency", data("baseCurrency", "ZZZ"), owner, 400);
    }

    @Test
    void auditPasswordChangeRevokesAnotherLogin() throws Exception {
        String username =
                jdbc.queryForObject(
                        "SELECT username FROM users WHERE id = ?", String.class, owner.id());
        JsonNode login =
                json(
                        "POST",
                        "/auth/login",
                        data(
                                "username",
                                username,
                                "password",
                                "LoginPassword123!",
                                "masterPassword",
                                MASTER),
                        null,
                        200);
        Auth another =
                new Auth(
                        owner.id(),
                        login.path("token").asText(),
                        login.path("encryptionKey").asText());
        json(
                "PUT",
                "/users/me/password",
                data("currentPassword", "LoginPassword123!", "newPassword", "ChangedPassword456!"),
                another,
                200);
        int status =
                mvc.perform(
                                get("/api/v1/users/me")
                                        .header("Authorization", "Bearer " + owner.token())
                                        .header("X-Encryption-Session", owner.session()))
                        .andReturn()
                        .getResponse()
                        .getStatus();
        System.out.println("AUDIT_OLD_SESSION_AFTER_PASSWORD_CHANGE " + status);
        assertThat(status).isIn(401, 403);
    }

    @Test
    void auditMonthlySchedulePreservesDayAcrossFebruary() {
        RecurringTransaction template =
                RecurringTransaction.builder()
                        .frequency(RecurringFrequency.MONTHLY)
                        .nextOccurrence(LocalDate.of(2025, 1, 31))
                        .build();
        template.setNextOccurrence(template.calculateNextOccurrence());
        LocalDate march = template.calculateNextOccurrence();
        System.out.println(
                "AUDIT_MONTHLY_DATES 2025-01-31 -> "
                        + template.getNextOccurrence()
                        + " -> "
                        + march);
        assertThat(march).isEqualTo(LocalDate.of(2025, 3, 31));
    }

    @Test
    void recurringPostingReloadsThePersistedMonthEndAnchor() throws Exception {
        long cash = account(owner);
        LocalDate first = LocalDate.now().minusYears(1).withMonth(1).withDayOfMonth(31);
        JsonNode template =
                json(
                        "POST",
                        "/recurring-transactions",
                        data(
                                "accountId",
                                cash,
                                "type",
                                "EXPENSE",
                                "amount",
                                10,
                                "currency",
                                "EUR",
                                "description",
                                "Month-end schedule",
                                "frequency",
                                "MONTHLY",
                                "nextOccurrence",
                                first.toString()),
                        owner,
                        201);
        json("POST", "/recurring-transactions/process", null, owner, 200);
        List<String> posted =
                jdbc.queryForList(
                        "SELECT transaction_date FROM transactions WHERE user_id = ? ORDER BY transaction_date",
                        String.class,
                        owner.id());
        assertThat(posted)
                .startsWith(
                        first.toString(),
                        first.plusMonths(1).toString(),
                        first.plusMonths(2).toString());
        assertThat(
                        jdbc.queryForObject(
                                "SELECT anchor_day FROM recurring_transactions WHERE id = ?",
                                Integer.class,
                                template.path("id").asLong()))
                .isEqualTo(31);
        json("POST", "/recurring-transactions/process", null, owner, 200);
        assertThat(
                        jdbc.queryForObject(
                                "SELECT COUNT(*) FROM transactions WHERE user_id = ?",
                                Integer.class,
                                owner.id()))
                .isEqualTo(posted.size());
    }

    @Test
    void auditExportPreservesSplitCategories() throws Exception {
        long cash = account(owner);
        long a =
                json(
                                "POST",
                                "/categories",
                                data("name", "Audit food", "type", "EXPENSE"),
                                owner,
                                201)
                        .path("id")
                        .asLong();
        long b =
                json(
                                "POST",
                                "/categories",
                                data("name", "Audit travel", "type", "EXPENSE"),
                                owner,
                                201)
                        .path("id")
                        .asLong();
        Map<String, Object> payload = movement(cash, 100, LocalDate.now());
        payload.put(
                "splits",
                List.of(data("categoryId", a, "amount", 40), data("categoryId", b, "amount", 60)));
        json("POST", "/transactions", payload, owner, 201);
        JsonNode exported = json("POST", "/data/export", data("format", "JSON"), owner, 200);
        System.out.println("AUDIT_EXPORTED_TRANSACTION " + exported.path("transactions").get(0));
        JsonNode transaction = exported.path("transactions").get(0);
        assertThat(transaction.path("splits")).hasSize(2);
        assertThat(transaction.path("splits").get(0).path("categoryId").asLong()).isEqualTo(a);
        assertThat(transaction.path("splits").get(1).path("categoryId").asLong()).isEqualTo(b);
        money(transaction.path("splits").get(0), "amount", "40");
        money(transaction.path("splits").get(1), "amount", "60");
        money(transaction, "accountAmount", "100");
        assertThat(transaction.path("accountCurrency").asText()).isEqualTo("EUR");
    }

    @Test
    void acceptsActiveLongCurrencyCodesAndRejectsInactiveCatalogEntries() throws Exception {
        json("PUT", "/users/me/base-currency", data("baseCurrency", "USDT"), owner, 200);
        assertThat(
                        json("GET", "/dashboard", null, owner, 200)
                                .path("netWorth")
                                .path("currency")
                                .asText())
                .isEqualTo("USDT");
        assertThat(
                        jdbc.queryForObject(
                                "SELECT base_currency FROM users WHERE id = ?",
                                String.class,
                                owner.id()))
                .isEqualTo("USDT");
        jdbc.update(
                "INSERT INTO currencies(code, name, symbol, is_active) VALUES ('OFF', 'Inactive audit currency', 'O', false) ON CONFLICT (code) DO UPDATE SET is_active = false");
        json("PUT", "/users/me/base-currency", data("baseCurrency", "OFF"), owner, 400);
    }

    @Test
    void reportingCurrencyEvictsEveryPeriodOfBorrowingAndPortfolioCaches() throws Exception {
        for (String name : List.of("portfolioPerformance", "borrowingCapacity")) {
            caches.getCache(name).put(owner.id() + "_30", "stale");
            caches.getCache(name).put(owner.id() + "_90", "stale");
        }
        json("PUT", "/users/me/base-currency", data("baseCurrency", "USD"), owner, 200);
        for (String name : List.of("portfolioPerformance", "borrowingCapacity")) {
            assertThat(caches.getCache(name).get(owner.id() + "_30")).isNull();
            assertThat(caches.getCache(name).get(owner.id() + "_90")).isNull();
        }
    }

    @Test
    void historyUsesOneCurrencyAndPreservesStoredSnapshot() throws Exception {
        account(owner);
        LocalDate previousDate = LocalDate.now().minusMonths(1);
        EncryptionContext.setKey(keys.getKey(owner.id()).orElseThrow());
        try {
            snapshots.saveAndFlush(
                    NetWorth.builder()
                            .userId(owner.id())
                            .snapshotDate(previousDate)
                            .totalAssets(new BigDecimal("1000"))
                            .totalLiabilities(BigDecimal.ZERO)
                            .netWorth(new BigDecimal("1000"))
                            .currency("EUR")
                            .build());
        } finally {
            EncryptionContext.clear();
        }
        json("PUT", "/users/me/base-currency", data("baseCurrency", "USD"), owner, 200);
        JsonNode history =
                json(
                        "GET",
                        "/dashboard/networth-history?startDate="
                                + previousDate
                                + "&endDate="
                                + previousDate,
                        null,
                        owner,
                        200);
        assertThat(history).hasSize(1);
        assertThat(history.get(0).path("currency").asText()).isEqualTo("USD");
        money(history.get(0), "netWorth", "2000");
        assertThat(
                        jdbc.queryForObject(
                                "SELECT currency FROM net_worth WHERE user_id = ? AND snapshot_date = ?",
                                String.class,
                                owner.id(),
                                previousDate.toString()))
                .isEqualTo("EUR");
    }
}
