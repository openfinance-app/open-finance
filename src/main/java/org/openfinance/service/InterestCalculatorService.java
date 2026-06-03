package org.openfinance.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import javax.crypto.SecretKey;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.openfinance.dto.AccountResponse;
import org.openfinance.dto.BalanceHistoryPoint;
import org.openfinance.entity.InterestPeriod;
import org.openfinance.entity.InterestRateVariation;
import org.openfinance.repository.InterestRateVariationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service for calculating interest for accounts.
 *
 * <p>Two calculation modes: 1. calculateInterestEstimate – forward-looking 1-year compound interest
 * projection 2. calculateHistoricalAccumulated – actual net interest earned across elapsed days
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class InterestCalculatorService {

    private final InterestRateVariationRepository variationRepository;
    private final AccountService accountService;

    // -------------------------------------------------------------------------
    // Forward-looking projection (compound interest formula)
    // -------------------------------------------------------------------------

    /**
     * Projects net interest earnings over 1 year using the compound interest formula: A = P × (1 +
     * r/n)^(n×t) − P, applied to the current balance and the most recently applicable rate
     * variation.
     */
    @Transactional(readOnly = true)
    public BigDecimal calculateInterestEstimate(
            Long accountId, Long userId, String period) {
        log.debug(
                "Calculating interest projection for account {} over period {}", accountId, period);

        AccountResponse account = accountService.getAccountById(accountId, userId);
        if (!Boolean.TRUE.equals(account.getIsInterestEnabled())) return BigDecimal.ZERO;

        InterestPeriod interestPeriodType = account.getInterestPeriod();
        if (interestPeriodType == null) return BigDecimal.ZERO;

        List<InterestRateVariation> variations =
                variationRepository.findByAccountIdOrderByValidFromDesc(accountId);
        if (variations.isEmpty()) return BigDecimal.ZERO;

        BigDecimal balance = account.getBalance();
        if (balance == null || balance.compareTo(BigDecimal.ZERO) <= 0) return BigDecimal.ZERO;

        InterestRateVariation variation = getVariationForDate(variations, LocalDate.now());
        if (variation == null) variation = variations.get(0);

        BigDecimal ratePct = variation.getRate();
        BigDecimal taxPct =
                variation.getTaxRate() != null ? variation.getTaxRate() : BigDecimal.ZERO;

        int n =
                switch (interestPeriodType) {
                    case DAILY -> 365;
                    case MONTHLY -> 12;
                    case HALF_YEARLY -> 2;
                    case ANNUAL -> 1;
                };

        double r = ratePct.doubleValue() / 100.0;
        double grossInterest = balance.doubleValue() * (Math.pow(1 + r / n, (double) n) - 1);
        double tax = taxPct.doubleValue() / 100.0;
        double netInterest = grossInterest * (1 - tax);

        log.debug(
                "Projection: balance={}, rate={}%, n={}, gross={}, tax={}%, net={}",
                balance, ratePct, n, grossInterest, taxPct, netInterest);

        return BigDecimal.valueOf(netInterest).setScale(2, RoundingMode.HALF_UP);
    }

    // -------------------------------------------------------------------------
    // Historical accumulation (actual elapsed days)
    // -------------------------------------------------------------------------

    /**
     * Sums the actual net interest earned day-by-day since account creation, using the rate that
     * was applicable on each calendar day.
     */
    @Transactional(readOnly = true)
    public BigDecimal calculateHistoricalAccumulated(
            Long accountId, Long userId, String period) {
        log.debug("Calculating historical accumulated interest for account {}", accountId);

        AccountResponse account = accountService.getAccountById(accountId, userId);
        if (!Boolean.TRUE.equals(account.getIsInterestEnabled())) return BigDecimal.ZERO;

        List<InterestRateVariation> variations =
                variationRepository.findByAccountIdOrderByValidFromDesc(accountId);
        if (variations.isEmpty()) return BigDecimal.ZERO;

        List<BalanceHistoryPoint> history =
                accountService.getAccountBalanceHistory(accountId, userId, period);
        if (history.isEmpty()) return BigDecimal.ZERO;

        TreeMap<LocalDate, BigDecimal> dailyBalances = expandToDailyBalances(history);
        BigDecimal totalNetInterest = BigDecimal.ZERO;

        for (Map.Entry<LocalDate, BigDecimal> entry : dailyBalances.entrySet()) {
            LocalDate date = entry.getKey();
            BigDecimal balance = entry.getValue();
            if (balance.compareTo(BigDecimal.ZERO) <= 0) continue;

            InterestRateVariation applicable = getVariationForDate(variations, date);
            if (applicable == null) continue;

            BigDecimal annualRate =
                    applicable.getRate().divide(new BigDecimal("100"), 10, RoundingMode.HALF_UP);
            BigDecimal dailyGross =
                    balance.multiply(annualRate)
                            .divide(new BigDecimal("365"), 10, RoundingMode.HALF_UP);

            BigDecimal taxPct =
                    applicable.getTaxRate() != null ? applicable.getTaxRate() : BigDecimal.ZERO;
            BigDecimal keep =
                    BigDecimal.ONE.subtract(
                            taxPct.divide(new BigDecimal("100"), 10, RoundingMode.HALF_UP));
            totalNetInterest = totalNetInterest.add(dailyGross.multiply(keep));
        }

        return totalNetInterest.setScale(2, RoundingMode.HALF_UP);
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private TreeMap<LocalDate, BigDecimal> expandToDailyBalances(
            List<BalanceHistoryPoint> history) {
        TreeMap<LocalDate, BigDecimal> dailyBalances = new TreeMap<>();
        if (history.isEmpty()) return dailyBalances;

        LocalDate startDate = history.get(0).date();
        LocalDate endDate = LocalDate.now();
        BigDecimal current = BigDecimal.ZERO;
        int index = 0;

        for (LocalDate date = startDate; !date.isAfter(endDate); date = date.plusDays(1)) {
            while (index < history.size() && !history.get(index).date().isAfter(date)) {
                current = history.get(index).balance();
                index++;
            }
            dailyBalances.put(date, current);
        }
        return dailyBalances;
    }

    private InterestRateVariation getVariationForDate(
            List<InterestRateVariation> sortedDesc, LocalDate date) {
        for (InterestRateVariation v : sortedDesc) {
            if (!v.getValidFrom().isAfter(date)) return v;
        }
        return null;
    }
}
