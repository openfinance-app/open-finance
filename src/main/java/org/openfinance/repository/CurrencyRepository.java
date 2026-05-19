package org.openfinance.repository;

import java.util.List;
import java.util.Optional;
import org.openfinance.entity.Currency;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Repository interface for {@link Currency} entity operations.
 *
 * <p>Provides methods for querying currencies by code, active status, and existence checks. All
 * queries are derived from method names using Spring Data JPA naming conventions.
 *
 * @see Currency
 * @author Open Finance
 * @since 1.0.0
 */
@Repository
public interface CurrencyRepository extends JpaRepository<Currency, Long> {

    /**
     * Finds a currency by its ISO 4217 code.
     *
     * @param code the 3-letter currency code (e.g., "USD", "EUR")
     * @return an Optional containing the currency if found, empty otherwise
     */
    Optional<Currency> findByCode(String code);

    /**
     * Finds all active currencies ordered by code.
     *
     * @return list of active currencies sorted alphabetically by code
     */
    List<Currency> findByIsActiveTrueOrderByCodeAsc();

    /**
     * Finds all currencies (active and inactive) ordered by code.
     *
     * @return list of all currencies sorted alphabetically by code
     */
    List<Currency> findAllByOrderByCodeAsc();

    /**
     * Checks if a currency with the given code exists.
     *
     * @param code the 3-letter currency code to check
     * @return true if a currency with the code exists, false otherwise
     */
    boolean existsByCode(String code);
}
