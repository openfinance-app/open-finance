package org.openfinance.service;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.openfinance.dto.AccountResponse;
import org.openfinance.dto.BalanceHistoryPoint;
import org.openfinance.entity.InterestPeriod;
import org.openfinance.entity.InterestRateVariation;
import org.openfinance.repository.InterestRateVariationRepository;
import org.openfinance.util.MathConstants;
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
    private final CurrencyTypeResolver currencyTypeResolver;

    // -------------------------------------------------------------------------
    // Forward-looking projection (compound interest formula)
    // -------------------------------------------------------------------------

    /**
     * Projects net interest earnings over 1 year using the compound interest formula: A = P × (1 +
     * r/n)^(n×t) − P, applied to the current balance and the most recently applicable rate
     * variation.
     */
    @Transactional(readOnly = true)
    public BigDecimal calculateInterestEstimate(Long accountId, Long userId, String period) {
        log.debug(
                "Calculating interest projection for account {} over period {}", accountId, period);

        AccountResponse account = accountService.getAccountById(accountId, userId);
        if (!Boolean.TRUE.equals(account.getIsInterestEnabled())) return BigDecimal.ZERO;

        InterestPeriod interestPeriodType = account.getInterestPeriod();
        if (interestPeriodType == null) return BigDecimal.ZERO;

        List<InterestRateVariation> variations =
                variationRepository.findByAccountIdOrderByValidFromDesc(accountId);
        if (variations.isEmpty()) return BigDecimal.ZERO;

        BigDecimal balance = account.getOwnBalance();
        if (balance == null || balance.compareTo(BigDecimal.ZERO) <= 0) return BigDecimal.ZERO;

        InterestRateVariation variation = getVariationForDate(variations, LocalDate.now());
        if (variation == null) return BigDecimal.ZERO;

        BigDecimal ratePct = variation.getRate();
        BigDecimal taxPct =
                variation.getTaxRate() != null ? variation.getTaxRate() : BigDecimal.ZERO;

        int n =
                switch (interestPeriodType) {
                    case DAILY -> 365;
                    case MONTHLY -> 12;
                    case QUARTERLY -> 4;
                    case HALF_YEARLY -> 2;
                    case ANNUAL -> 1;
                };

        // Compound interest computed entirely in BigDecimal (never double). The exponent n is an
        // integer number of compounding periods, so BigDecimal.pow(int, MathContext) is exact.
        MathContext mc = new MathContext(20, RoundingMode.HALF_UP);

        BigDecimal r = ratePct.divide(MathConstants.HUNDRED, mc); // annual rate as a fraction
        BigDecimal perPeriodRate = r.divide(BigDecimal.valueOf(n), mc);
        BigDecimal compoundFactor = BigDecimal.ONE.add(perPeriodRate).pow(n, mc);
        BigDecimal grossInterest = balance.multiply(compoundFactor.subtract(BigDecimal.ONE));

        BigDecimal taxFraction = taxPct.divide(MathConstants.HUNDRED, mc);
        BigDecimal netInterest = grossInterest.multiply(BigDecimal.ONE.subtract(taxFraction));

        log.debug(
                "Projection: balance={}, rate={}%, n={}, gross={}, tax={}%, net={}",
                balance, ratePct, n, grossInterest, taxPct, netInterest);

        return netInterest.setScale(
                currencyTypeResolver.decimalsFor(account.getCurrency()), RoundingMode.HALF_UP);
    }

    // -------------------------------------------------------------------------
    // Historical accumulation (actual elapsed days)
    // -------------------------------------------------------------------------

    /**
     * Sums the actual net interest earned day-by-day since account creation, using the rate that
     * was applicable on each calendar day.
     */
    @Transactional(readOnly = true)
    public BigDecimal calculateHistoricalAccumulated(Long accountId, Long userId, String period) {
        log.debug("Calculating historical accumulated interest for account {}", accountId);

        AccountResponse account = accountService.getAccountById(accountId, userId);
        if (!Boolean.TRUE.equals(account.getIsInterestEnabled())) return BigDecimal.ZERO;

        List<InterestRateVariation> variations =
                variationRepository.findByAccountIdOrderByValidFromDesc(accountId);
        if (variations.isEmpty()) return BigDecimal.ZERO;

        List<BalanceHistoryPoint> history =
                accountService.getAccountBalanceHistory(accountId, userId, period);
        if (history.isEmpty()) return BigDecimal.ZERO;

        return totalAccrued(account, accrue(variations, history, LocalDate.now()));
    }

    /** Estimates interest only within the selected inclusive range, capped at today. */
    @Transactional(readOnly = true)
    public BigDecimal calculateHistoricalAccumulated(
            Long accountId, Long userId, LocalDate startDate, LocalDate endDate) {
        if (startDate == null || endDate == null || startDate.isAfter(endDate)) {
            throw new IllegalArgumentException("A valid interest date range is required");
        }
        AccountResponse account = accountService.getAccountById(accountId, userId);
        LocalDate through = endDate.isAfter(LocalDate.now()) ? LocalDate.now() : endDate;
        if (!Boolean.TRUE.equals(account.getIsInterestEnabled()) || startDate.isAfter(through)) {
            return BigDecimal.ZERO;
        }
        return totalAccrued(
                account,
                accrue(
                        variationRepository.findByAccountIdOrderByValidFromDesc(accountId),
                        accountService.getAccountBalanceHistory(
                                accountId, userId, startDate, through),
                        through));
    }

    /** Unrounded per-rate estimates share the same daily cash ledger as the summary. */
    @Transactional(readOnly = true)
    public Map<Long, Accrual> calculateAccrualsByVariation(Long accountId, Long userId) {
        AccountResponse account = accountService.getAccountById(accountId, userId);
        if (!Boolean.TRUE.equals(account.getIsInterestEnabled())) return Map.of();
        return accrue(
                variationRepository.findByAccountIdOrderByValidFromDesc(accountId),
                accountService.getAccountBalanceHistory(accountId, userId, "ALL"),
                LocalDate.now());
    }

    public record Accrual(long activeDays, BigDecimal interestProduced) {
        public static final Accrual ZERO = new Accrual(0, BigDecimal.ZERO);

        Accrual add(Accrual other) {
            return new Accrual(
                    activeDays + other.activeDays, interestProduced.add(other.interestProduced));
        }
    }

    private BigDecimal totalAccrued(AccountResponse account, Map<Long, Accrual> accruals) {
        return accruals.values().stream()
                .map(Accrual::interestProduced)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(
                        currencyTypeResolver.decimalsFor(account.getCurrency()),
                        RoundingMode.HALF_UP);
    }

    private Map<Long, Accrual> accrue(
            List<InterestRateVariation> variations,
            List<BalanceHistoryPoint> history,
            LocalDate endDate) {
        Map<Long, Accrual> accruals = new HashMap<>();
        for (Map.Entry<LocalDate, BigDecimal> entry :
                expandToDailyBalances(history, endDate).entrySet()) {
            InterestRateVariation applicable = getVariationForDate(variations, entry.getKey());
            if (applicable == null) continue;
            BigDecimal annualRate = applicable.getRate().movePointLeft(2);
            BigDecimal dailyGross =
                    entry.getValue()
                            .max(BigDecimal.ZERO)
                            .multiply(annualRate)
                            .divide(new BigDecimal("365"), 18, RoundingMode.HALF_UP);
            BigDecimal taxPct =
                    applicable.getTaxRate() == null ? BigDecimal.ZERO : applicable.getTaxRate();
            BigDecimal net = dailyGross.multiply(BigDecimal.ONE.subtract(taxPct.movePointLeft(2)));
            accruals.merge(applicable.getId(), new Accrual(1, net), Accrual::add);
        }
        return accruals;
    }

    /** Dated closing cash is also the source of principal for account-backed liabilities. */
    @Transactional(readOnly = true)
    public Map<LocalDate, BigDecimal> getDailyCashBalances(
            Long accountId, Long userId, LocalDate startDate, LocalDate endDate) {
        return expandToDailyBalances(
                accountService.getAccountBalanceHistory(accountId, userId, startDate, endDate),
                endDate);
    }

    private TreeMap<LocalDate, BigDecimal> expandToDailyBalances(
            List<BalanceHistoryPoint> history, LocalDate endDate) {
        TreeMap<LocalDate, BigDecimal> dailyBalances = new TreeMap<>();
        if (history.isEmpty()) return dailyBalances;
        LocalDate startDate = history.get(0).date();
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
