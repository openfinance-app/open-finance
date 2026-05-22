package org.openfinance.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * NetWorth entity representing a snapshot of user's financial position at a specific date.
 *
 * <p>This entity stores daily snapshots of:
 *
 * <ul>
 *   <li>Total assets value (sum of all account balances)
 *   <li>Total liabilities (sum of all debts)
 *   <li>Net worth (assets - liabilities)
 * </ul>
 *
 * <p>All monetary values are stored in the user's base currency (typically EUR).
 *
 * <p>Requirements: REQ-2.5.1, REQ-2.5.2
 *
 * @author Open-Finance Development Team
 * @since 1.0
 */
@Entity
@Table(
        name = "net_worth",
        indexes = {
            @Index(
                    name = "idx_net_worth_user_date",
                    columnList = "user_id, snapshot_date",
                    unique = true)
        })
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NetWorth {

    /** Unique identifier for this net worth snapshot. */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** ID of the user this snapshot belongs to. Foreign key reference to users table. */
    @Column(name = "user_id", nullable = false)
    private Long userId;

    /** Date of this net worth snapshot. Used for tracking historical net worth trends. */
    @Column(name = "snapshot_date", nullable = false)
    private LocalDate snapshotDate;

    /**
     * Total value of all assets at snapshot date. Includes cash, investments, property, etc.
     * converted to base currency.
     *
     * <p>Precision: 19 digits total, 2 decimal places (standard currency precision)
     */
    @Column(name = "total_assets", nullable = false, precision = 19, scale = 2)
    private BigDecimal totalAssets;

    /**
     * Total value of all liabilities at snapshot date. Includes loans, mortgages, credit card debt,
     * etc.
     *
     * <p>Precision: 19 digits total, 2 decimal places
     */
    @Column(name = "total_liabilities", nullable = false, precision = 19, scale = 2)
    private BigDecimal totalLiabilities;

    /**
     * Calculated net worth (totalAssets - totalLiabilities). Can be negative if liabilities exceed
     * assets.
     *
     * <p>Precision: 19 digits total, 2 decimal places
     */
    @Column(name = "net_worth", nullable = false, precision = 19, scale = 2)
    private BigDecimal netWorth;

    /**
     * Currency code for all monetary values in this snapshot. Typically the user's base currency
     * (e.g., "EUR").
     *
     * <p>ISO 4217 currency code (3 characters)
     */
    @Column(name = "currency", nullable = false, length = 3)
    private String currency;

    /** FK to the currencies table for referential integrity. */
    @Column(name = "currency_id")
    private Long currencyId;

    /** Reference to the currency entity (lazy-loaded). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "currency_id", insertable = false, updatable = false)
    private Currency currencyEntity;

    /** Timestamp when this snapshot was created. */
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /** JPA lifecycle callback to set creation timestamp before persisting. */
    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }

    /**
     * Calculates the change in net worth from a previous snapshot.
     *
     * @param previousSnapshot the previous net worth snapshot to compare against
     * @return the absolute change in net worth (current - previous)
     */
    public BigDecimal calculateChangeFrom(NetWorth previousSnapshot) {
        if (previousSnapshot == null) {
            return BigDecimal.ZERO;
        }
        return this.netWorth.subtract(previousSnapshot.getNetWorth());
    }

    /**
     * Calculates the percentage change in net worth from a previous snapshot.
     *
     * @param previousSnapshot the previous net worth snapshot to compare against
     * @return the percentage change (e.g., 5.25 for 5.25% increase)
     */
    public BigDecimal calculatePercentageChangeFrom(NetWorth previousSnapshot) {
        if (previousSnapshot == null
                || previousSnapshot.getNetWorth().compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }
        BigDecimal change = calculateChangeFrom(previousSnapshot);
        return change.divide(previousSnapshot.getNetWorth().abs(), 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100));
    }
}
