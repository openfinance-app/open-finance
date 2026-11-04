package org.openfinance.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.openfinance.config.BusinessRulesProperties;
import org.openfinance.dto.NotificationResponse;
import org.openfinance.dto.NotificationResponse.NotificationSeverity;
import org.openfinance.dto.NotificationResponse.NotificationType;
import org.openfinance.entity.Account;
import org.openfinance.entity.Asset;
import org.openfinance.repository.AccountRepository;
import org.openfinance.repository.AssetRepository;
import org.openfinance.repository.ExchangeRateRepository;
import org.openfinance.repository.TransactionRepository;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.context.support.ResourceBundleMessageSource;

@ExtendWith(MockitoExtension.class)
@DisplayName("Notifications respect thresholds, user scope and requested language")
class NotificationServiceTest {
    private static final long USER_ID = 42L;
    @Mock private AssetRepository assets;
    @Mock private TransactionRepository transactions;
    @Mock private AccountRepository accounts;
    @Mock private ExchangeRateRepository rates;
    private final BusinessRulesProperties rules = new BusinessRulesProperties();
    private NotificationService service;

    @BeforeEach
    void setUp() {
        ResourceBundleMessageSource messages = new ResourceBundleMessageSource();
        messages.setBasename("i18n/messages");
        messages.setDefaultEncoding("UTF-8");
        messages.setFallbackToSystemLocale(false);
        service = new NotificationService(assets, transactions, accounts, rates, messages, rules);
        LocaleContextHolder.setLocale(Locale.ENGLISH);
    }

    @AfterEach
    void clearLocale() {
        LocaleContextHolder.resetLocaleContext();
    }

    @Test
    void missingUserIsRejectedBeforeReadingPrivateData() {
        assertThatThrownBy(() -> service.getNotifications(null))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(assets, transactions, accounts, rates);
    }

    @Test
    void healthyAccountsAndFreshRatesProduceNoNotifications() {
        freshRates();
        when(accounts.findByUserIdAndIsActive(USER_ID, true))
                .thenReturn(List.of(balance("1000"), balance("1000.001"), balance(null)));
        assertThat(service.getNotifications(USER_ID)).isEmpty();
        verify(transactions).countByUserIdAndCategoryIdIsNullAndIsDeletedFalse(USER_ID);
        verify(transactions).countByUserIdAndPayeeIsNullAndIsDeletedFalse(USER_ID);
    }

    @ParameterizedTest
    @CsvSource({"29, false", "30, true", "31, true"})
    void transactionWarningsStartAtThirtyAndUseTheActualCounts(long count, boolean expected) {
        freshRates();
        when(transactions.countByUserIdAndCategoryIdIsNullAndIsDeletedFalse(USER_ID))
                .thenReturn(count);
        when(transactions.countByUserIdAndPayeeIsNullAndIsDeletedFalse(USER_ID)).thenReturn(count);

        List<NotificationResponse> result = service.getNotifications(USER_ID);
        if (!expected) {
            assertThat(result).isEmpty();
            return;
        }
        assertThat(result)
                .extracting(NotificationResponse::getType)
                .containsExactlyInAnyOrder(
                        NotificationType.UNCATEGORIZED_TRANSACTIONS,
                        NotificationType.UNLINKED_PAYEE);
        assertThat(result)
                .allSatisfy(
                        notification -> {
                            assertThat(notification.getCount()).isEqualTo((int) count);
                            assertThat(notification.getMessage()).contains(Long.toString(count));
                            assertThat(notification.getActionUrl()).isEqualTo("/transactions");
                            assertThat(notification.getSeverity())
                                    .isEqualTo(NotificationSeverity.INFO);
                        });
    }

    @Test
    void lowBalanceUsesConfiguredThresholdWithExactDecimalComparison() {
        freshRates();
        rules.getAccounts().setLowBalanceThreshold(new BigDecimal("250.125"));
        when(accounts.findByUserIdAndIsActive(USER_ID, true))
                .thenReturn(
                        List.of(
                                balance("250.12499999"),
                                balance("-0.01"),
                                balance("250.125"),
                                balance("250.12500001"),
                                balance(null)));

        assertThat(service.getNotifications(USER_ID))
                .singleElement()
                .satisfies(
                        notification -> {
                            assertThat(notification.getType())
                                    .isEqualTo(NotificationType.LOW_BALANCE);
                            assertThat(notification.getCount()).isEqualTo(2);
                            assertThat(notification.getSeverity())
                                    .isEqualTo(NotificationSeverity.CRITICAL);
                            assertThat(notification.getActionUrl()).isEqualTo("/accounts");
                            assertThat(notification.getMessage()).contains("250.125");
                        });
        verify(accounts).findByUserIdAndIsActive(USER_ID, true);
    }

    @Test
    void missingRatesHaveAnActionableFirstLoadNotification() {
        when(rates.findLatestRateDate()).thenReturn(Optional.empty());
        assertThat(service.getNotifications(USER_ID))
                .singleElement()
                .satisfies(
                        notification -> {
                            assertThat(notification.getType())
                                    .isEqualTo(NotificationType.STALE_EXCHANGE_RATES);
                            assertThat(notification.getCount()).isZero();
                            assertThat(notification.getTitle()).isEqualTo("Exchange Rates Missing");
                            assertThat(notification.getActionUrl()).isEqualTo("/currencies");
                            assertThat(notification.getActionLabel()).isEqualTo("Update Now");
                            assertThat(notification.getSeverity())
                                    .isEqualTo(NotificationSeverity.WARNING);
                        });
    }

    @ParameterizedTest
    @CsvSource({"-1, false", "0, false", "2, false", "3, true", "10, true"})
    void exchangeRatesBecomeStaleOnlyAfterTwoDays(int age, boolean stale) {
        when(rates.findLatestRateDate()).thenReturn(Optional.of(LocalDate.now().minusDays(age)));
        List<NotificationResponse> result = service.getNotifications(USER_ID);
        if (!stale) {
            assertThat(result).isEmpty();
            return;
        }
        assertThat(result)
                .singleElement()
                .satisfies(
                        notification -> {
                            assertThat(notification.getType())
                                    .isEqualTo(NotificationType.STALE_EXCHANGE_RATES);
                            assertThat(notification.getCount()).isEqualTo(age);
                            assertThat(notification.getTitle())
                                    .isEqualTo("Exchange Rates Outdated");
                            assertThat(notification.getMessage()).contains(age + " day(s)");
                            assertThat(notification.getActionLabel()).isEqualTo("Update Rates");
                        });
    }

    @ParameterizedTest
    @ValueSource(strings = {"en", "fr"})
    void quoteWarningsResolveRealTranslationsAndUseAUserScopedCutoff(String language) {
        freshRates();
        LocaleContextHolder.setLocale(Locale.forLanguageTag(language));
        when(assets.findByUserIdAndLastUpdatedBefore(eq(USER_ID), any(LocalDateTime.class)))
                .thenReturn(
                        List.of(Asset.builder().id(1L).build(), Asset.builder().id(2L).build()));
        LocalDateTime before = LocalDateTime.now().minusDays(1);

        assertThat(service.getNotifications(USER_ID))
                .singleElement()
                .satisfies(
                        notification -> {
                            assertThat(notification.getType())
                                    .isEqualTo(NotificationType.STALE_QUOTES);
                            assertThat(notification.getCount()).isEqualTo(2);
                            assertThat(notification.getActionUrl()).isEqualTo("/assets");
                            assertThat(notification.getSeverity())
                                    .isEqualTo(NotificationSeverity.WARNING);
                            assertThat(notification.getTitle())
                                    .isEqualTo(
                                            language.equals("fr")
                                                    ? "Cotations à mettre à jour"
                                                    : "Quotes Need Update");
                            assertThat(notification.getMessage())
                                    .contains("2", "1")
                                    .doesNotContain("{0}", "{1}");
                            assertThat(notification.getActionLabel())
                                    .isEqualTo(
                                            language.equals("fr")
                                                    ? "Mettre à jour les prix"
                                                    : "Update Prices");
                        });
        ArgumentCaptor<LocalDateTime> cutoff = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(assets).findByUserIdAndLastUpdatedBefore(eq(USER_ID), cutoff.capture());
        assertThat(cutoff.getValue()).isBetween(before, LocalDateTime.now().minusDays(1));
    }

    private void freshRates() {
        when(rates.findLatestRateDate()).thenReturn(Optional.of(LocalDate.now()));
    }

    private Account balance(String value) {
        return Account.builder()
                .userId(USER_ID)
                .balance(value == null ? null : new BigDecimal(value))
                .build();
    }
}
