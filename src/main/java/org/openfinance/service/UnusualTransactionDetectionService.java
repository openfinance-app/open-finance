package org.openfinance.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
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

    private static final double Z_SCORE_THRESHOLD = 2.5;
    private static final int MIN_HISTORY_FOR_STDDEV = 5;
    private static final double RELATIVE_THRESHOLD_FACTOR = 3.0;

    private final TransactionRepository transactionRepository;
    private final InsightRepository insightRepository;
    private final UserRepository userRepository;
    private final MessageSource messageSource;

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

        List<Insight> insights = new ArrayList<>();
        for (Transaction tx : recentTransactions) {
            // Only analyse expense and income movements; skip internal transfers
            if (tx.getType() == TransactionType.TRANSFER) {
                continue;
            }
            try {
                insights.addAll(analyseTransaction(tx, user, since));
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

    private List<Insight> analyseTransaction(Transaction tx, User user, LocalDateTime cutoff) {
        List<Insight> insights = new ArrayList<>();

        String payee = tx.getPayee();
        BigDecimal amount = tx.getAmount();

        if (payee != null && !payee.isBlank()) {
            // --- Algorithm 1: first-time payee ---
            long priorCount =
                    transactionRepository.countByUserIdAndPayeeAndCreatedAtBefore(
                            user.getId(), payee, cutoff);

            if (priorCount == 0) {
                insights.add(buildNewPayeeInsight(user, tx, payee, amount));
                // A new payee is already flagged – no need to also flag large amount
                return insights;
            }

            // --- Algorithm 2 / 3: amount anomaly for known payee ---
            List<Transaction> history =
                    transactionRepository.findByUserIdAndPayeeAndCreatedAtBefore(
                            user.getId(), payee, cutoff);

            if (history.size() >= MIN_HISTORY_FOR_STDDEV) {
                // Sufficient history → Z-score check
                double mean = mean(history);
                double stdDev = stdDev(history, mean);
                double threshold = mean + Z_SCORE_THRESHOLD * stdDev;
                if (amount.doubleValue() > threshold && stdDev > 0) {
                    double pct = ((amount.doubleValue() - mean) / mean) * 100;
                    insights.add(buildLargeAmountInsight(user, tx, payee, amount, mean, pct));
                }
            } else if (!history.isEmpty()) {
                // Sparse history → relative factor check
                double mean = mean(history);
                if (mean > 0 && amount.doubleValue() > RELATIVE_THRESHOLD_FACTOR * mean) {
                    double pct = ((amount.doubleValue() - mean) / mean) * 100;
                    insights.add(buildLargeAmountInsight(user, tx, payee, amount, mean, pct));
                }
            }

        } else if (tx.getCategoryId() != null && tx.getType() == TransactionType.EXPENSE) {
            // --- Algorithm 3 (no payee): compare against category average ---
            List<Transaction> history =
                    transactionRepository.findExpensesByUserIdAndCategoryIdAndCreatedAtBefore(
                            user.getId(), tx.getCategoryId(), cutoff);

            if (!history.isEmpty()) {
                double mean = mean(history);
                if (mean > 0 && amount.doubleValue() > RELATIVE_THRESHOLD_FACTOR * mean) {
                    insights.add(buildLargeAmountNoPayeeInsight(user, tx, amount));
                }
            }
        }

        return insights;
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
            User user, Transaction tx, String payee, BigDecimal amount, double mean, double pct) {
        BigDecimal meanDecimal = BigDecimal.valueOf(mean).setScale(2, RoundingMode.HALF_UP);
        long pctLong = Math.round(pct);
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

    private double mean(List<Transaction> transactions) {
        return transactions.stream()
                .mapToDouble(t -> t.getAmount().doubleValue())
                .average()
                .orElse(0.0);
    }

    private double stdDev(List<Transaction> transactions, double mean) {
        double variance =
                transactions.stream()
                        .mapToDouble(
                                t -> {
                                    double diff = t.getAmount().doubleValue() - mean;
                                    return diff * diff;
                                })
                        .average()
                        .orElse(0.0);
        return Math.sqrt(variance);
    }
}
