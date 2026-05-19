package org.openfinance.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.openfinance.dto.NotificationResponse;
import org.openfinance.dto.NotificationResponse.NotificationSeverity;
import org.openfinance.dto.NotificationResponse.NotificationType;
import org.openfinance.entity.Account;
import org.openfinance.entity.Asset;
import org.openfinance.repository.AccountRepository;
import org.openfinance.repository.AssetRepository;
import org.openfinance.repository.ExchangeRateRepository;
import org.openfinance.repository.TransactionRepository;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service for generating system notifications.
 *
 * <p>Checks various conditions and generates notifications for:
 *
 * <ul>
 *   <li>Stale asset quotes (not updated in 1+ days)
 *   <li>Uncategorized transactions (30+ transactions)
 *   <li>Transactions without payees (30+ transactions)
 *   <li>Low account balances (below 1000)
 * </ul>
 */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
@Slf4j
public class NotificationService {

    private final AssetRepository assetRepository;
    private final TransactionRepository transactionRepository;
    private final AccountRepository accountRepository;
    private final ExchangeRateRepository exchangeRateRepository;
    private final MessageSource messageSource;

    private static final int UNCATEGORIZED_THRESHOLD = 30;
    private static final int UNLINKED_PAYEE_THRESHOLD = 30;
    private static final BigDecimal LOW_BALANCE_THRESHOLD = new BigDecimal("1000");
    private static final int STALE_QUOTE_DAYS = 1;
    private static final int STALE_EXCHANGE_RATE_DAYS = 2;

    /**
     * Gets all notifications for a user.
     *
     * @param userId the user ID
     * @return list of notifications
     */
    public List<NotificationResponse> getNotifications(Long userId) {
        if (userId == null) {
            throw new IllegalArgumentException("User ID cannot be null");
        }

        log.debug("Generating notifications for user {}", userId);

        List<NotificationResponse> notifications = new ArrayList<>();

        // Check for stale quotes
        notifications.addAll(checkStaleQuotes(userId));

        // Check for stale exchange rates
        notifications.addAll(checkStaleExchangeRates());

        // Check for uncategorized transactions
        notifications.addAll(checkUncategorizedTransactions(userId));

        // Check for transactions without payees
        notifications.addAll(checkUnlinkedPayees(userId));

        // Check for low balances
        notifications.addAll(checkLowBalances(userId));

        log.debug("Generated {} notifications for user {}", notifications.size(), userId);

        return notifications;
    }

    /** Checks for assets with stale quotes (not updated in 1+ days). */
    private List<NotificationResponse> checkStaleQuotes(Long userId) {
        LocalDateTime threshold = LocalDateTime.now().minusDays(STALE_QUOTE_DAYS);

        List<Asset> staleAssets =
                assetRepository.findByUserIdAndLastUpdatedBefore(userId, threshold);

        if (staleAssets.isEmpty()) {
            return List.of();
        }

        return List.of(
                NotificationResponse.builder()
                        .type(NotificationType.STALE_QUOTES)
                        .title(
                                messageSource.getMessage(
                                        "notification.stale.quotes.title",
                                        null,
                                        LocaleContextHolder.getLocale()))
                        .message(
                                messageSource.getMessage(
                                        "notification.stale.quotes.message",
                                        new Object[] {staleAssets.size(), STALE_QUOTE_DAYS},
                                        LocaleContextHolder.getLocale()))
                        .count(staleAssets.size())
                        .actionUrl("/assets")
                        .actionLabel(
                                messageSource.getMessage(
                                        "notification.stale.quotes.action",
                                        null,
                                        LocaleContextHolder.getLocale()))
                        .severity(NotificationSeverity.WARNING)
                        .build());
    }

    /** Checks for uncategorized transactions (30+ transactions). */
    private List<NotificationResponse> checkUncategorizedTransactions(Long userId) {
        Long count =
                transactionRepository.countByUserIdAndCategoryIdIsNullAndIsDeletedFalse(userId);

        if (count < UNCATEGORIZED_THRESHOLD) {
            return List.of();
        }

        return List.of(
                NotificationResponse.builder()
                        .type(NotificationType.UNCATEGORIZED_TRANSACTIONS)
                        .title(
                                messageSource.getMessage(
                                        "notification.uncategorized.title",
                                        null,
                                        LocaleContextHolder.getLocale()))
                        .message(
                                messageSource.getMessage(
                                        "notification.uncategorized.message",
                                        new Object[] {count},
                                        LocaleContextHolder.getLocale()))
                        .count(count.intValue())
                        .actionUrl("/transactions")
                        .actionLabel(
                                messageSource.getMessage(
                                        "notification.uncategorized.action",
                                        null,
                                        LocaleContextHolder.getLocale()))
                        .severity(NotificationSeverity.INFO)
                        .build());
    }

    /** Checks for transactions without payees (30+ transactions). */
    private List<NotificationResponse> checkUnlinkedPayees(Long userId) {
        Long count = transactionRepository.countByUserIdAndPayeeIsNullAndIsDeletedFalse(userId);

        if (count < UNLINKED_PAYEE_THRESHOLD) {
            return List.of();
        }

        return List.of(
                NotificationResponse.builder()
                        .type(NotificationType.UNLINKED_PAYEE)
                        .title(
                                messageSource.getMessage(
                                        "notification.unlinked.payee.title",
                                        null,
                                        LocaleContextHolder.getLocale()))
                        .message(
                                messageSource.getMessage(
                                        "notification.unlinked.payee.message",
                                        new Object[] {count},
                                        LocaleContextHolder.getLocale()))
                        .count(count.intValue())
                        .actionUrl("/transactions")
                        .actionLabel(
                                messageSource.getMessage(
                                        "notification.unlinked.payee.action",
                                        null,
                                        LocaleContextHolder.getLocale()))
                        .severity(NotificationSeverity.INFO)
                        .build());
    }

    /** Checks for accounts with very low balance (below 1000). */
    private List<NotificationResponse> checkLowBalances(Long userId) {
        List<Account> lowBalanceAccounts =
                accountRepository.findByUserIdAndIsActiveTrueAndBalanceLessThan(
                        userId, LOW_BALANCE_THRESHOLD);

        if (lowBalanceAccounts.isEmpty()) {
            return List.of();
        }

        return List.of(
                NotificationResponse.builder()
                        .type(NotificationType.LOW_BALANCE)
                        .title(
                                messageSource.getMessage(
                                        "notification.low.balance.title",
                                        null,
                                        LocaleContextHolder.getLocale()))
                        .message(
                                messageSource.getMessage(
                                        "notification.low.balance.message",
                                        new Object[] {
                                            lowBalanceAccounts.size(), LOW_BALANCE_THRESHOLD
                                        },
                                        LocaleContextHolder.getLocale()))
                        .count(lowBalanceAccounts.size())
                        .actionUrl("/accounts")
                        .actionLabel(
                                messageSource.getMessage(
                                        "notification.low.balance.action",
                                        null,
                                        LocaleContextHolder.getLocale()))
                        .severity(NotificationSeverity.CRITICAL)
                        .build());
    }

    /**
     * Checks whether any exchange rates have been stored in the last 2 days. If no exchange rate
     * was updated within that window the user is notified so they can trigger a manual refresh.
     */
    private List<NotificationResponse> checkStaleExchangeRates() {
        Optional<LocalDate> latestRateDateOpt = exchangeRateRepository.findLatestRateDate();

        if (latestRateDateOpt.isEmpty()) {
            // No rates at all — notify so the user can trigger the first load
            return List.of(
                    NotificationResponse.builder()
                            .type(NotificationType.STALE_EXCHANGE_RATES)
                            .title(
                                    messageSource.getMessage(
                                            "notification.stale.rates.title.missing",
                                            null,
                                            LocaleContextHolder.getLocale()))
                            .message(
                                    messageSource.getMessage(
                                            "notification.stale.rates.message.missing",
                                            null,
                                            LocaleContextHolder.getLocale()))
                            .count(0)
                            .actionUrl("/currencies")
                            .actionLabel(
                                    messageSource.getMessage(
                                            "notification.update.now",
                                            null,
                                            LocaleContextHolder.getLocale()))
                            .severity(NotificationSeverity.WARNING)
                            .build());
        }

        LocalDate latestRateDate = latestRateDateOpt.get();
        long daysSinceUpdate =
                java.time.temporal.ChronoUnit.DAYS.between(latestRateDate, LocalDate.now());

        if (daysSinceUpdate <= STALE_EXCHANGE_RATE_DAYS) {
            return List.of();
        }

        return List.of(
                NotificationResponse.builder()
                        .type(NotificationType.STALE_EXCHANGE_RATES)
                        .title(
                                messageSource.getMessage(
                                        "notification.stale.rates.title.outdated",
                                        null,
                                        LocaleContextHolder.getLocale()))
                        .message(
                                messageSource.getMessage(
                                        "notification.stale.rates.message.outdated",
                                        new Object[] {daysSinceUpdate},
                                        LocaleContextHolder.getLocale()))
                        .count((int) daysSinceUpdate)
                        .actionUrl("/currencies")
                        .actionLabel(
                                messageSource.getMessage(
                                        "notification.stale.rates.action",
                                        null,
                                        LocaleContextHolder.getLocale()))
                        .severity(NotificationSeverity.WARNING)
                        .build());
    }
}
