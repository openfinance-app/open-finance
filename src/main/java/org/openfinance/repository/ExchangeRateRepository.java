package org.openfinance.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.openfinance.entity.ExchangeRate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

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
