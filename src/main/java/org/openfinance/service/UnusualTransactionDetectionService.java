package org.openfinance.service;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.openfinance.entity.Insight;
import org.openfinance.entity.InsightPriority;
import org.openfinance.entity.InsightType;
import org.openfinance.entity.Transaction;
import org.openfinance.entity.TransactionType;
import org.openfinance.entity.User;
import org.openfinance.repository.InsightRepository;
import org.openfinance.repository.TransactionRepository;
import org.openfinance.repository.UserRepository;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service that analyses newly created transactions and flags any that look unusual or potentially
 * fraudulent.
 *
 * <h2>Detection algorithms</h2>
 *
 * <ol>
 *   <li><strong>New payee</strong> — the payee name appears for the first time for this user.
 *       Priority HIGH.
 *   <li><strong>Large amount (with history)</strong> — the transaction amount exceeds mean +
 *       {@value #Z_SCORE_THRESHOLD} × std-dev computed over all prior transactions to the same
 *       payee. Requires at least {@value #MIN_HISTORY_FOR_STDDEV} historical records. Priority
 *       HIGH.
 *   <li><strong>Large relative amount (sparse history)</strong> — when fewer than {@value
 *       #MIN_HISTORY_FOR_STDDEV} prior transactions exist for that payee (or the transaction has no
 *       payee), flags if the amount is {@value #RELATIVE_THRESHOLD_FACTOR}× the
 *       per-payee/per-category average. Priority MEDIUM.
 * </ol>
 *
 * <p>Each detected anomaly is persisted as an {@link Insight} of type {@link
 * InsightType#UNUSUAL_TRANSACTION} so the existing insight pipeline delivers it to the user without
 * any additional wiring.
 *
 * <p>This service is intentionally stateless and side-effect free beyond persisting {@link Insight}
 * records; it does <em>not</em> modify transactions.
 *
 * @since Sprint 12 – Unusual Transaction Detection
 */
@Service
@Transactional
@RequiredArgsConstructor
@Slf4j
public class UnusualTransactionDetectionService {

    private static final BigDecimal Z_SCORE_THRESHOLD = new BigDecimal("2.5");
    private static final int MIN_HISTORY_FOR_STDDEV = 5;
    private static final BigDecimal RELATIVE_THRESHOLD_FACTOR = new BigDecimal("3");

    private final TransactionRepository transactionRepository;
    private final InsightRepository insightRepository;
    private final UserRepository userRepository;
    private final MessageSource messageSource;
    private final ExchangeRateService exchangeRateService;

    /**
     * Analyses all transactions created for {@code userId} since {@code since} and persists an
     * {@link Insight} for each anomaly found.
     *
     * @param userId the user to analyse
     * @param since lower-bound timestamp for "newly created" transactions
     * @return number of unusual-transaction insights generated
     */
    public int detectAndPersist(Long userId, LocalDateTime since) {
        log.debug("Detecting unusual transactions for user {} since {}", userId, since);

        User user = userRepository.findById(userId).orElse(null);
        if (user == null) {
            log.warn("User {} not found – skipping unusual transaction detection", userId);
            return 0;
        }

        List<Transaction> recentTransactions =
                transactionRepository.findByUserIdAndCreatedAtAfter(userId, since);

        if (recentTransactions.isEmpty()) {
            log.debug("No new transactions for user {} since {}", userId, since);
            return 0;
        }

        List<Transaction> history =
                new ArrayList<>(
                        transactionRepository.findByUserIdAndCreatedAtBefore(userId, since));
        Set<String> existing =
                insightRepository
                        .findByUser_IdAndType(userId, InsightType.UNUSUAL_TRANSACTION)
                        .stream()
                        .map(Insight::getSourceKey)
                        .collect(Collectors.toSet());
        List<Insight> insights = new ArrayList<>();
        recentTransactions = new ArrayList<>(recentTransactions);
        recentTransactions.sort(
                java.util.Comparator.comparing(Transaction::getCreatedAt)
                        .thenComparing(Transaction::getId));
        for (Transaction tx : recentTransactions) {
            // Only analyse expense and income movements; skip internal transfers
            if (tx.getType() == TransactionType.TRANSFER
                    || tx.getTransferId() != null
                    || Boolean.TRUE.equals(tx.getIsDeleted())) {
                continue;
            }
            try {
                insights.addAll(
                        analyseTransaction(tx, user, history).stream()
                                .filter(insight -> existing.add(insight.getSourceKey()))
                                .toList());
                history.add(tx);
            } catch (Exception e) {
                log.warn(
                        "Error analysing transaction {} for user {}: {}",
                        tx.getId(),
                        userId,
                        e.getMessage());
            }
        }

        if (!insights.isEmpty()) {
            insightRepository.saveAll(insights);
            log.info(
                    "Persisted {} unusual-transaction insight(s) for user {}",
                    insights.size(),
                    userId);
        }

        return insights.size();
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private List<Insight> analyseTransaction(Transaction tx, User user, List<Transaction> prior) {
        List<Insight> insights = new ArrayList<>();
        String payee = tx.getPayee();
        BigDecimal amount = tx.getAmount();
        if (payee != null && !payee.isBlank()) {
            List<Transaction> payeeHistory = prior.stream().filter(t -> samePayee(tx, t)).toList();
            if (payeeHistory.isEmpty()) {
                Insight insight = buildNewPayeeInsight(user, tx, payee, amount);
                insight.setSourceKey("unusual:new-payee:" + tx.getId());
                insights.add(insight);
                return insights;
            }
            List<BigDecimal> amounts = historicalAmounts(payeeHistory, tx);
            if (!amounts.isEmpty()) {
                BigDecimal mean = mean(amounts);
                BigDecimal deviation = stdDev(amounts, mean);
                BigDecimal threshold =
                        amounts.size() >= MIN_HISTORY_FOR_STDDEV && deviation.signum() > 0
                                ? mean.add(Z_SCORE_THRESHOLD.multiply(deviation))
                                : mean.multiply(RELATIVE_THRESHOLD_FACTOR);
                if (mean.signum() > 0 && amount.compareTo(threshold) > 0) {
                    BigDecimal pct =
                            amount.subtract(mean)
                                    .multiply(new BigDecimal("100"))
                                    .divide(mean, 0, RoundingMode.HALF_UP);
                    Insight insight = buildLargeAmountInsight(user, tx, payee, amount, mean, pct);
                    insight.setSourceKey("unusual:amount:" + tx.getId());
                    insights.add(insight);
                }
            }
        } else if (tx.getCategoryId() != null && tx.getType() == TransactionType.EXPENSE) {
            List<BigDecimal> amounts =
                    historicalAmounts(
                            prior.stream()
                                    .filter(t -> tx.getCategoryId().equals(t.getCategoryId()))
                                    .toList(),
                            tx);
            if (!amounts.isEmpty()
                    && amount.compareTo(mean(amounts).multiply(RELATIVE_THRESHOLD_FACTOR)) > 0) {
                Insight insight = buildLargeAmountNoPayeeInsight(user, tx, amount);
                insight.setSourceKey("unusual:amount:" + tx.getId());
                insights.add(insight);
            }
        }
        return insights;
    }

    private boolean samePayee(Transaction first, Transaction second) {
        if (first.getPayeeId() != null && second.getPayeeId() != null)
            return first.getPayeeId().equals(second.getPayeeId());
        return first.getPayee() != null
                && second.getPayee() != null
                && first.getPayee().strip().equalsIgnoreCase(second.getPayee().strip());
    }

    private List<BigDecimal> historicalAmounts(List<Transaction> history, Transaction current) {
        return history.stream()
                .filter(t -> t.getType() == current.getType() && t.getTransferId() == null)
                .map(
                        t ->
                                exchangeRateService.convert(
                                        t.getAmount(),
                                        t.getCurrency(),
                                        current.getCurrency(),
                                        t.getDate()))
                .toList();
    }

    private Insight buildNewPayeeInsight(
            User user, Transaction tx, String payee, BigDecimal amount) {
        String title =
                messageSource.getMessage(
                        "insight.unusual.transaction.new.payee.title",
                        new Object[] {payee},
                        LocaleContextHolder.getLocale());
        String description =
                messageSource.getMessage(
                        "insight.unusual.transaction.new.payee.description",
                        new Object[] {payee, amount, tx.getCurrency()},
                        LocaleContextHolder.getLocale());
        return buildInsight(user, title, description, InsightPriority.HIGH);
    }

    private Insight buildLargeAmountInsight(
            User user,
            Transaction tx,
            String payee,
            BigDecimal amount,
            BigDecimal mean,
            BigDecimal pct) {
        BigDecimal meanDecimal = mean.setScale(2, RoundingMode.HALF_UP);
        BigDecimal pctLong = pct.setScale(0, RoundingMode.HALF_UP);
        String title =
                messageSource.getMessage(
                        "insight.unusual.transaction.large.amount.title",
                        null,
                        LocaleContextHolder.getLocale());
        String description =
                messageSource.getMessage(
                        "insight.unusual.transaction.large.amount.description",
                        new Object[] {amount, tx.getCurrency(), payee, pctLong, meanDecimal},
                        LocaleContextHolder.getLocale());
        return buildInsight(user, title, description, InsightPriority.HIGH);
    }

    private Insight buildLargeAmountNoPayeeInsight(User user, Transaction tx, BigDecimal amount) {
        String title =
                messageSource.getMessage(
                        "insight.unusual.transaction.large.amount.no.payee.title",
                        null,
                        LocaleContextHolder.getLocale());
        String description =
                messageSource.getMessage(
                        "insight.unusual.transaction.large.amount.no.payee.description",
                        new Object[] {amount, tx.getCurrency(), tx.getDate().toString()},
                        LocaleContextHolder.getLocale());
        return buildInsight(user, title, description, InsightPriority.MEDIUM);
    }

    private Insight buildInsight(
            User user, String title, String description, InsightPriority priority) {
        return Insight.builder()
                .user(user)
                .type(InsightType.UNUSUAL_TRANSACTION)
                .title(title)
                .description(description)
                .priority(priority)
                .dismissed(false)
                .build();
    }

    // -------------------------------------------------------------------------
    // Statistics helpers
    // -------------------------------------------------------------------------

    private BigDecimal mean(List<BigDecimal> amounts) {
        return amounts.stream()
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(amounts.size()), MathContext.DECIMAL128);
    }

    private BigDecimal stdDev(List<BigDecimal> amounts, BigDecimal mean) {
        return amounts.stream()
                .map(amount -> amount.subtract(mean).pow(2))
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(amounts.size()), MathContext.DECIMAL128)
                .sqrt(MathContext.DECIMAL128);
    }
}
