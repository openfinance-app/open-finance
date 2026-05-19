package org.openfinance.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.hibernate.annotations.CreationTimestamp;

/**
 * Entity representing an exchange rate between two currencies.
 *
 * <p>Exchange rates are stored with historical data to allow for accurate currency conversion at
 * any point in time. Rates are typically fetched from external APIs and cached in the database.
 *
 * <p>Requirement REQ-2.8.2: Store historical exchange rates for accurate financial reporting and
 * conversion.
 *
 * @author Open-Finance Team
 * @version 1.0
 * @since Sprint 6
 */
@Entity
@Table(
        name = "exchange_rates",
        uniqueConstraints = {
            @UniqueConstraint(
                    name = "uk_exchange_rate_currencies_date",
                    columnNames = {"base_currency", "target_currency", "rate_date"})
        },
        indexes = {
            @Index(name = "idx_exchange_rate_base", columnList = "base_currency"),
            @Index(name = "idx_exchange_rate_target", columnList = "target_currency"),
            @Index(name = "idx_exchange_rate_date", columnList = "rate_date"),
            @Index(
                    name = "idx_exchange_rate_base_target_date",
                    columnList = "base_currency, target_currency, rate_date")
        })
@Getter
@Setter
@ToString
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class ExchangeRate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Base currency code (ISO 4217). Example: For USD/EUR rate, this would be "USD" Requirement
     * REQ-2.8.3: Support currency pair conversion
     */
    @NotNull(message = "Base currency cannot be null")
    @Pattern(regexp = "^[A-Z]{3,10}$", message = "Base currency must be 3 to 10 uppercase letters")
    @Size(min = 3, max = 10, message = "Base currency must be between 3 and 10 characters")
    @Column(name = "base_currency", nullable = false, length = 10)
    @EqualsAndHashCode.Include
    private String baseCurrency;

    /** Target currency code (ISO 4217). Example: For USD/EUR rate, this would be "EUR" */
    @NotNull(message = "Target currency cannot be null")
    @Pattern(
            regexp = "^[A-Z]{3,10}$",
            message = "Target currency must be 3 to 10 uppercase letters")
    @Size(min = 3, max = 10, message = "Target currency must be between 3 and 10 characters")
    @Column(name = "target_currency", nullable = false, length = 10)
    @EqualsAndHashCode.Include
    private String targetCurrency;

    /**
     * Exchange rate: 1 base currency = rate * target currency Example: USD/EUR = 0.85 means 1 USD =
     * 0.85 EUR
     *
     * <p>Must be positive and stored with high precision for accurate calculations.
     */
    @NotNull(message = "Exchange rate cannot be null")
    @DecimalMin(value = "0.0", inclusive = false, message = "Exchange rate must be greater than 0")
    @Column(name = "rate", nullable = false, precision = 18, scale = 8)
    private BigDecimal rate;

    /**
     * Date for which this exchange rate is valid. Typically set to the date when the rate was
     * fetched. Requirement REQ-2.8.4: Historical rate tracking for point-in-time conversion
     */
    @NotNull(message = "Rate date cannot be null")
    @Column(name = "rate_date", nullable = false)
    @EqualsAndHashCode.Include
    private LocalDate rateDate;

    /** Source of the exchange rate data. Examples: "yfinance" */
    @Size(max = 100, message = "Source cannot exceed 100 characters")
    @Column(name = "source", length = 100)
    @Builder.Default
    private String source = "system";

    /** Timestamp when this exchange rate record was created. */
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /**
     * Convenience method to get the inverse exchange rate. Example: If USD/EUR = 0.85, then EUR/USD
     * = 1/0.85 ≈ 1.176
     *
     * @return The inverse rate (1 / rate)
     */
    public BigDecimal getInverseRate() {
        if (rate == null || rate.compareTo(BigDecimal.ZERO) == 0) {
            throw new IllegalStateException("Cannot calculate inverse of zero or null rate");
        }
        return BigDecimal.ONE.divide(rate, 8, java.math.RoundingMode.HALF_UP);
    }

    /**
     * Convenience method to check if this rate is for a specific currency pair.
     *
     * @param base The base currency code
     * @param target The target currency code
     * @return true if this rate matches the currency pair
     */
    public boolean isForCurrencyPair(String base, String target) {
        return this.baseCurrency.equals(base) && this.targetCurrency.equals(target);
    }
}
