package org.openfinance.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.crypto.SecretKey;
import org.junit.jupiter.api.Test;
import org.openfinance.dto.MarketQuote;
import org.openfinance.entity.Insight;
import org.openfinance.entity.InsightPriority;
import org.openfinance.entity.InsightType;
import org.openfinance.entity.User;
import org.openfinance.provider.MarketDataProvider;
import org.openfinance.repository.InsightRepository;
import org.openfinance.security.EncryptionContext;
import org.openfinance.security.EncryptionKeyCache;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.support.TransactionSynchronizationManager;

class UnusualDetectionWriteBoundaryIntegrationTest extends AuditApiTestSupport {
    @Autowired private UnusualTransactionDetectionService detector;
    @Autowired private InsightRepository insights;
    @Autowired private InsightWriter writer;
    @Autowired private EncryptionKeyCache keys;
    @SpyBean private ExchangeRateService exchangeRates;
    @MockBean private MarketDataProvider provider;

    @DynamicPropertySource
    static void shortBusyTimeout(DynamicPropertyRegistry registry) {
        if (!System.getenv()
                .getOrDefault("SPRING_DATASOURCE_URL", "")
                .startsWith("jdbc:postgresql:")) {
            registry.add(
                    "spring.datasource.hikari.connection-init-sql",
                    () -> "PRAGMA busy_timeout=100");
        }
    }

    @Test
    void exchangeRatesCommitWhileDetectionIsStillAnalysing() throws Exception {
        long cash = account(owner);
        for (int amount : List.of(10, 100)) {
            Map<String, Object> request = movement(cash, amount, LocalDate.now());
            request.put("payee", "Known merchant");
            json("POST", "/transactions", request, owner, 201);
        }
        CountDownLatch analysing = new CountDownLatch(1);
        CountDownLatch resume = new CountDownLatch(1);
        AtomicBoolean analysisTransaction = new AtomicBoolean();
        doAnswer(
                        call -> {
                            analysisTransaction.set(
                                    TransactionSynchronizationManager.isActualTransactionActive());
                            analysing.countDown();
                            assertThat(resume.await(10, TimeUnit.SECONDS)).isTrue();
                            return call.callRealMethod();
                        })
                .when(exchangeRates)
                .convert(any(), eq("EUR"), eq("EUR"), any());
        when(provider.getQuotes(anyList()))
                .thenReturn(
                        List.of(
                                MarketQuote.builder()
                                        .symbol("EURUSD=X")
                                        .price(new BigDecimal("1.25"))
                                        .build()));
        SecretKey key = keys.getKeyBySessionToken(owner.session()).orElseThrow();
        LocalDateTime since = LocalDateTime.now().minusHours(1);
        CompletableFuture<Integer> detection =
                CompletableFuture.supplyAsync(
                        () -> {
                            EncryptionContext.setKey(key);
                            try {
                                return detector.detectAndPersist(owner.id(), since);
                            } finally {
                                EncryptionContext.clear();
                            }
                        });
        try {
            assertThat(analysing.await(10, TimeUnit.SECONDS)).isTrue();
            assertThat(exchangeRates.updateExchangeRates()).isEqualTo(2);
            assertThat(analysisTransaction).isFalse();
            assertThat(detection).isNotDone();
        } finally {
            resume.countDown();
            detection.get(10, TimeUnit.SECONDS);
        }
        assertThat(detection.get()).isEqualTo(2);
        EncryptionContext.setKey(key);
        assertThat(insights.findByUser_IdAndType(owner.id(), InsightType.UNUSUAL_TRANSACTION))
                .hasSize(2);
        assertThat(detector.detectAndPersist(owner.id(), since)).isZero();
    }

    @Test
    void concurrentPublicationKeepsOneAlertAndPreservesItsDismissal() throws Exception {
        SecretKey key = keys.getKeyBySessionToken(owner.session()).orElseThrow();
        EncryptionContext.setKey(key);
        // Warm up the entity/transaction path before starting the competing writes.
        assertThat(writer.appendUnusual(owner.id(), List.of(alert("warmup")))).isEqualTo(1);
        CountDownLatch start = new CountDownLatch(1);
        java.util.function.Supplier<Integer> publish =
                () -> {
                    EncryptionContext.setKey(key);
                    try {
                        if (!start.await(10, TimeUnit.SECONDS))
                            throw new IllegalStateException("Start timeout");
                        return writer.appendUnusual(owner.id(), List.of(alert("shared")));
                    } catch (InterruptedException interrupted) {
                        Thread.currentThread().interrupt();
                        throw new IllegalStateException(interrupted);
                    } finally {
                        EncryptionContext.clear();
                    }
                };
        CompletableFuture<Integer> first = CompletableFuture.supplyAsync(publish);
        CompletableFuture<Integer> second = CompletableFuture.supplyAsync(publish);
        start.countDown();
        assertThat(first.get(10, TimeUnit.SECONDS) + second.get(10, TimeUnit.SECONDS)).isEqualTo(1);
        jdbc.update(
                "UPDATE insights SET dismissed = TRUE WHERE user_id = ? AND source_key = ?",
                owner.id(),
                "unusual:shared");
        assertThat(writer.appendUnusual(owner.id(), List.of(alert("shared")))).isZero();
        assertThat(insights.findByUser_IdAndType(owner.id(), InsightType.UNUSUAL_TRANSACTION))
                .filteredOn(insight -> insight.getSourceKey().equals("unusual:shared"))
                .singleElement()
                .satisfies(insight -> assertThat(insight.getDismissed()).isTrue());
    }

    private Insight alert(String source) {
        return Insight.builder()
                .user(User.builder().id(owner.id()).build())
                .type(InsightType.UNUSUAL_TRANSACTION)
                .sourceKey("unusual:" + source)
                .priority(InsightPriority.HIGH)
                .title("Synthetic detection")
                .description("Synthetic alert")
                .dismissed(false)
                .build();
    }
}
