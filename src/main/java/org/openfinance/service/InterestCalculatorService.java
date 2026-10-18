package org.openfinance.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.openfinance.dto.AccountResponse;
import org.openfinance.dto.BalanceHistoryPoint;
import org.openfinance.dto.InterestPreviewRequest;
import org.openfinance.entity.InterestRateVariation;
import org.openfinance.entity.InterestValueDateMode;
import org.openfinance.repository.InterestRateVariationRepository;
import org.openfinance.util.CalendarInterestProjection;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Recorded-cash accruals and calendar-year projections with scheduled net capitalization. */
@Service
@RequiredArgsConstructor
@Slf4j
public class InterestCalculatorService {

    private final InterestRateVariationRepository variationRepository;
    private final AccountService accountService;
    private final CurrencyTypeResolver currencyTypeResolver;

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

        return totalAccrued(
                account,
                accrue(
                        variations,
                        eligibleHistory(
                                accountId, userId, variations, history, LocalDate.now(), false),
                        LocalDate.now()));
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
        List<InterestRateVariation> variations =
                variationRepository.findByAccountIdOrderByValidFromDesc(accountId);
        List<BalanceHistoryPoint> cash =
                accountService.getAccountBalanceHistory(accountId, userId, startDate, through);
        return totalAccrued(
                account,
                accrue(
                        variations,
                        eligibleHistory(accountId, userId, variations, cash, through, false),
                        through));
    }

    /** Accrual estimates use interest value dates; ordinary account history stays booked cash. */
    @Transactional(readOnly = true)
    public Map<Long, Accrual> calculateAccrualsByVariation(Long accountId, Long userId) {
        AccountResponse account = accountService.getAccountById(accountId, userId);
        if (!Boolean.TRUE.equals(account.getIsInterestEnabled())) return Map.of();
        List<InterestRateVariation> variations =
                variationRepository.findByAccountIdOrderByValidFromDesc(accountId);
        return accrue(
                variations,
                eligibleHistory(
                        accountId,
                        userId,
                        variations,
                        accountService.getAccountBalanceHistory(accountId, userId, "ALL"),
                        LocalDate.now(),
                        false),
                LocalDate.now());
    }

    public record YearEstimate(
            int year,
            LocalDate through,
            BigDecimal accrued,
            BigDecimal calendarYearEstimate,
            Map<Long, Accrual> accruals) {
        public YearEstimate {
            accruals = Map.copyOf(accruals);
        }
    }

    @Transactional(readOnly = true)
    public YearEstimate calculateYear(Long accountId, Long userId, int year) {
        if (year < 1900 || year > 9998) throw new IllegalArgumentException("Invalid interest year");
        AccountResponse account = accountService.getAccountById(accountId, userId);
        LocalDate start = LocalDate.of(year, 1, 1);
        LocalDate end = LocalDate.of(year, 12, 31);
        LocalDate through = end.isAfter(LocalDate.now()) ? LocalDate.now() : end;
        if (!Boolean.TRUE.equals(account.getIsInterestEnabled())) {
            return new YearEstimate(year, through, BigDecimal.ZERO, BigDecimal.ZERO, Map.of());
        }
        List<InterestRateVariation> variations =
                variationRepository.findByAccountIdOrderByValidFromDesc(accountId);
        Map<Long, Accrual> accrued =
                start.isAfter(through)
                        ? Map.of()
                        : accrue(
                                variations,
                                eligibleHistory(
                                        accountId,
                                        userId,
                                        variations,
                                        accountService.getAccountBalanceHistory(
                                                accountId, userId, start, through),
                                        through,
                                        false),
                                through);
        List<BalanceHistoryPoint> annualHistory =
                eligibleHistory(
                        accountId,
                        userId,
                        variations,
                        accountService.getAccountBalanceHistory(accountId, userId, start, end),
                        end,
                        true);
        CalendarInterestProjection.Result annual =
                CalendarInterestProjection.calculate(
                        expandToDailyBalances(annualHistory, end),
                        variations,
                        account.getInterestPeriod(),
                        postedInterest(accountId, userId, variations, start, end));
        return new YearEstimate(
                year,
                through,
                totalAccrued(account, accrued),
                round(annual.netInterest(), account.getCurrency()),
                accrued);
    }

    public record Preview(
            int year,
            LocalDate startDate,
            LocalDate endDate,
            BigDecimal grossInterest,
            BigDecimal taxAmount,
            BigDecimal netInterest) {}

    /** No account, balance adjustment or transaction is created by a form preview. */
    @Transactional(readOnly = true)
    public Preview preview(InterestPreviewRequest request) {
        LocalDate start = LocalDate.now().withDayOfYear(1);
        LocalDate end = start.withMonth(12).withDayOfMonth(31);
        if (request.openingDate().isAfter(start)) start = request.openingDate();
        InterestRateVariation rate =
                InterestRateVariation.builder()
                        .rate(request.rate())
                        .taxRate(request.taxRate())
                        .validFrom(request.openingDate())
                        .build();
        CalendarInterestProjection.Result result =
                CalendarInterestProjection.calculate(
                        expandToDailyBalances(
                                List.of(new BalanceHistoryPoint(start, request.balance())), end),
                        List.of(rate),
                        request.interestPeriod(),
                        Map.of());
        return new Preview(
                end.getYear(),
                start,
                end,
                round(result.grossInterest(), request.currency()),
                round(result.taxAmount(), request.currency()),
                round(result.netInterest(), request.currency()));
    }

    private BigDecimal round(BigDecimal amount, String currency) {
        return amount.setScale(currencyTypeResolver.decimalsFor(currency), RoundingMode.HALF_UP);
    }

    private Map<LocalDate, BigDecimal> postedInterest(
            Long accountId,
            Long userId,
            List<InterestRateVariation> variations,
            LocalDate start,
            LocalDate end) {
        Map<LocalDate, BigDecimal> credits = new TreeMap<>();
        for (AccountService.CashMovement movement :
                accountService.getCashMovements(
                        accountId, userId, start.minusMonths(1), end.plusMonths(1))) {
            if (!movement.interestCredit()) continue;
            InterestRateVariation rate = getVariationForDate(variations, movement.date());
            LocalDate date =
                    rate == null
                            ? movement.date()
                            : rate.getCreditValueDateMode().valueDate(movement.date(), true);
            if (!date.isBefore(start) && !date.isAfter(end))
                credits.merge(date, movement.balanceDelta(), BigDecimal::add);
        }
        return credits;
    }

    private List<BalanceHistoryPoint> eligibleHistory(
            Long accountId,
            Long userId,
            List<InterestRateVariation> variations,
            List<BalanceHistoryPoint> cash,
            LocalDate end,
            boolean forecast) {
        if (cash.isEmpty()
                || variations.stream()
                        .allMatch(
                                v ->
                                        v.getCreditValueDateMode()
                                                        == InterestValueDateMode.TRANSACTION_DATE
                                                && v.getDebitValueDateMode()
                                                        == InterestValueDateMode
                                                                .TRANSACTION_DATE)) {
            return cash;
        }
        TreeMap<LocalDate, BigDecimal> changes = new TreeMap<>();
        BigDecimal previous = BigDecimal.ZERO;
        for (BalanceHistoryPoint point : cash) {
            changes.merge(point.date(), point.balance().subtract(previous), BigDecimal::add);
            previous = point.balance();
        }
        LocalDate start = cash.get(0).date();
        LocalDate bookedThrough = end.plusMonths(1);
        if (!forecast && bookedThrough.isAfter(LocalDate.now())) bookedThrough = LocalDate.now();
        for (AccountService.CashMovement transaction :
                accountService.getCashMovements(
                        accountId, userId, start.minusMonths(1), bookedThrough)) {
            InterestRateVariation rule = getVariationForDate(variations, transaction.date());
            if (rule == null) continue;
            BigDecimal signed = transaction.balanceDelta();
            boolean credit = signed.signum() >= 0;
            InterestValueDateMode mode =
                    credit ? rule.getCreditValueDateMode() : rule.getDebitValueDateMode();
            LocalDate valueDate = mode.valueDate(transaction.date(), credit);
            if (valueDate.equals(transaction.date())) continue;
            LocalDate booked = transaction.date().isBefore(start) ? start : transaction.date();
            LocalDate eligible = valueDate.isBefore(start) ? start : valueDate;
            changes.merge(booked, signed.negate(), BigDecimal::add);
            changes.merge(eligible, signed, BigDecimal::add);
        }
        List<BalanceHistoryPoint> result = new ArrayList<>();
        BigDecimal balance = BigDecimal.ZERO;
        for (Map.Entry<LocalDate, BigDecimal> entry : changes.entrySet()) {
            balance = balance.add(entry.getValue());
            if (!entry.getKey().isAfter(end))
                result.add(new BalanceHistoryPoint(entry.getKey(), balance));
        }
        return result;
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
                    applicable
                            .getDayCountBasis()
                            .dailyInterest(
                                    entry.getValue().max(BigDecimal.ZERO).multiply(annualRate),
                                    entry.getKey());
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
