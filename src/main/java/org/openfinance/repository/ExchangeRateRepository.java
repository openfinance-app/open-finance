package org.openfinance.repository;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import org.openfinance.entity.ExchangeRate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Repository interface for {@link ExchangeRate} entity operations.
 *
 * <p>Provides methods for querying exchange rates by currency pairs, dates, and historical lookups.
 * Supports both exact date matches and "closest earlier date" queries for historical rate
 * retrieval.
 *
 * <p><strong>Query Strategies:</strong>
 *
 * <ul>
 *   <li><strong>Latest Rate:</strong> {@link #findLatestByBaseCurrencyAndTargetCurrency(String,
 *       String)}
 *   <li><strong>Exact Date:</strong> {@link #findByBaseCurrencyAndTargetCurrencyAndRateDate(String,
 *       String, LocalDate)}
 *   <li><strong>Historical:</strong> {@link
 *       #findByBaseCurrencyAndTargetCurrencyAndRateDateLessThanEqualOrderByRateDateDesc(String,
 *       String, LocalDate)}
 * </ul>
 *
 * @see ExchangeRate
 * @author Open Finance
 * @since 1.0.0
 */
@Repository
public interface ExchangeRateRepository extends JpaRepository<ExchangeRate, Long> {

    /** A preview and financial write may fetch the same rate concurrently. */
    @Transactional
    default void upsertAll(List<ExchangeRate> rates) {
        rates.stream()
                .sorted(
                        Comparator.comparing(ExchangeRate::getBaseCurrency)
                                .thenComparing(ExchangeRate::getTargetCurrency)
                                .thenComparing(ExchangeRate::getRateDate))
                .forEach(
                        rate ->
                                upsert(
                                        rate.getBaseCurrency(),
                                        rate.getTargetCurrency(),
                                        rate.getRate().toPlainString(),
                                        rate.getRateDate().toString(),
                                        rate.getSource()));
    }

    /** Runs after the financial transaction commits, when its SQLite write lock is released. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    default void upsertAllIndependently(List<ExchangeRate> rates) {
        upsertAll(rates);
    }

    /** Atomic on both SQLite and PostgreSQL; a competing insert cannot abort the caller. */
    @Modifying
    @Transactional
    @Query(
            value =
                    """
            INSERT INTO exchange_rates (base_currency, target_currency, rate, rate_date, source, created_at)
            VALUES (:base, :target, :rate, date(:rateDate), :source, CURRENT_TIMESTAMP)
            ON CONFLICT (base_currency, target_currency, rate_date)
            DO UPDATE SET rate = excluded.rate, source = excluded.source
            """,
            nativeQuery = true)
    int upsert(
            @Param("base") String base,
            @Param("target") String target,
            @Param("rate") String rate,
            @Param("rateDate") String rateDate,
            @Param("source") String source);

    /**
     * Finds the latest exchange rate for a currency pair.
     *
     * <p>Returns rates ordered by date descending (most recent first). Caller should take the first
     * element for the latest rate.
     *
     * @param baseCurrency the base currency code (e.g., "USD")
     * @param targetCurrency the target currency code (e.g., "EUR")
     * @return list of exchange rates ordered by date descending, empty if no rates found
     */
    @Query(
            "SELECT er FROM ExchangeRate er WHERE er.baseCurrency = :base "
                    + "AND er.targetCurrency = :target ORDER BY er.rateDate DESC")
    List<ExchangeRate> findLatestByBaseCurrencyAndTargetCurrency(
            @Param("base") String baseCurrency, @Param("target") String targetCurrency);

    /**
     * Finds an exchange rate for a specific currency pair and date.
     *
     * @param baseCurrency the base currency code
     * @param targetCurrency the target currency code
     * @param rateDate the exact date of the rate
     * @return an Optional containing the rate if found, empty otherwise
     */
    Optional<ExchangeRate> findByBaseCurrencyAndTargetCurrencyAndRateDate(
            String baseCurrency, String targetCurrency, LocalDate rateDate);

    /**
     * Finds the most recent exchange rate on or before a specific date.
     *
     * <p>This method is useful for historical conversions when an exact date match doesn't exist.
     * It returns rates on or before the specified date, ordered by date descending. Caller should
     * take the first element.
     *
     * <p><strong>Example:</strong> If requesting rate for 2024-03-15 and only rates exist for
     * 2024-03-10 and 2024-03-20, this returns the 2024-03-10 rate.
     *
     * @param baseCurrency the base currency code
     * @param targetCurrency the target currency code
     * @param date the reference date (inclusive upper bound)
     * @return list of exchange rates ordered by date descending, empty if no rates found
     */
    @Query(
            "SELECT er FROM ExchangeRate er WHERE er.baseCurrency = :base "
                    + "AND er.targetCurrency = :target AND er.rateDate <= :date "
                    + "ORDER BY er.rateDate DESC")
    List<ExchangeRate>
            findByBaseCurrencyAndTargetCurrencyAndRateDateLessThanEqualOrderByRateDateDesc(
                    @Param("base") String baseCurrency,
                    @Param("target") String targetCurrency,
                    @Param("date") LocalDate date);

    /**
     * Finds all exchange rates for a specific date.
     *
     * <p>Useful for bulk rate imports or date-specific rate snapshots.
     *
     * @param rateDate the date to query
     * @return list of all exchange rates for the given date
     */
    List<ExchangeRate> findByRateDate(LocalDate rateDate);

    /**
     * Deletes all exchange rates older than a cutoff date.
     *
     * <p>Used for cleanup/archival of historical data. Should be called within a transactional
     * context.
     *
     * @param cutoffDate the date before which all rates should be deleted (exclusive)
     */
    void deleteByRateDateBefore(LocalDate cutoffDate);

    /**
     * Returns the most recent rate date stored across all currency pairs.
     *
     * <p>Used by the notification service to determine whether exchange rates are stale (i.e. not
     * updated within the acceptable staleness window).
     *
     * @return the latest rate date, or {@code null} if no rates exist
     */
    @Query("SELECT MAX(er.rateDate) FROM ExchangeRate er")
    Optional<LocalDate> findLatestRateDate();
}
