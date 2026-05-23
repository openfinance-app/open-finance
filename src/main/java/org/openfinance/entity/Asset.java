package org.openfinance.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

/**
 * Entity representing a financial asset (stock, ETF, crypto, etc.) in the
 * Open-Finance system.
 *
 * <p>
 * Assets represent investment holdings that can appreciate or depreciate over
 * time. Each asset
 * belongs to a user and optionally to an account (e.g., brokerage account).
 *
 * <p>
 * Requirement REQ-2.6: Asset Management - Users can track various types of
 * financial assets
 * including stocks, ETFs, mutual funds, bonds, cryptocurrencies, commodities,
 * and real estate.
 *
 * <p>
 * <strong>Security Note:</strong> The {@code name} and {@code notes} fields
 * will be encrypted by
 * the AssetService before persisting to the database to protect sensitive
 * financial information.
 */
@Entity
@Table(name = "assets", indexes = {
        @Index(name = "idx_asset_user_id", columnList = "user_id"),
        @Index(name = "idx_asset_account_id", columnList = "account_id"),
        @Index(name = "idx_asset_type", columnList = "asset_type"),
        @Index(name = "idx_asset_symbol", columnList = "symbol")
})
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@ToString(onlyExplicitlyIncluded = true)
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class Asset {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @ToString.Include
    @EqualsAndHashCode.Include
    private Long id;

    /**
     * The user who owns this asset. Requirement REQ-2.6.1: Each asset belongs to a
     * single user
     */
    @NotNull(message = "User ID cannot be null")
    @Column(name = "user_id", nullable = false)
    @ToString.Include
    private Long userId;

    /** Relationship to the User entity. Lazy-loaded to optimize performance. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", insertable = false, updatable = false)
    private User user;

    /**
     * Optional account this asset belongs to (e.g., brokerage account). Requirement
     * REQ-2.6.2:
     * Assets can be linked to accounts
     */
    @Column(name = "account_id")
    private Long accountId;

    /** Relationship to the Account entity. Lazy-loaded to optimize performance. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "account_id", insertable = false, updatable = false)
    private Account account;

    /**
     * Name of the asset (e.g., "Apple Inc.", "Bitcoin", "S&P 500 ETF").
     *
     * <p>
     * <strong>Encrypted Field:</strong> This field is stored encrypted in the
     * database. The
     * AssetService handles encryption/decryption transparently. Requirement
     * REQ-2.6.2: Asset must
     * have a descriptive name
     */
    @NotNull(message = "Asset name cannot be null")
    @Size(min = 1, max = 500, message = "Asset name must be between 1 and 500 characters")
    @Column(name = "name", nullable = false, length = 500) // Extra length for encrypted data
    private String name;

    /**
     * Type of asset (STOCK, ETF, MUTUAL_FUND, BOND, CRYPTO, COMMODITY, REAL_ESTATE,
     * OTHER).
     * Requirement REQ-2.6: Asset type categorization
     */
    @NotNull(message = "Asset type cannot be null")
    @Enumerated(EnumType.STRING)
    @Column(name = "asset_type", nullable = false, length = 20)
    @ToString.Include
    private AssetType type;

    /**
     * Ticker symbol or identifier (e.g., "AAPL", "BTC-USD", "SPY"). Used for
     * fetching market data
     * and price updates.
     *
     * <p>
     * Requirement REQ-2.6.4: Symbol for market data integration
     */
    @Size(max = 20, message = "Symbol must not exceed 20 characters")
    @Column(name = "symbol", length = 20)
    @ToString.Include
    private String symbol;

    /**
     * Quantity or number of units owned.
     *
     * <p>
     * Examples:
     *
     * <ul>
     * <li>100 shares of AAPL
     * <li>0.5 Bitcoin
     * <li>250 units of mutual fund
     * </ul>
     *
     * <p>
     * Stored with precision 19, scale 8 to handle fractional assets (e.g.,
     * cryptocurrencies).
     * Requirement REQ-2.6.2: Track quantity of assets
     */
    @NotNull(message = "Quantity cannot be null")
    @DecimalMin(value = "0.00000001", message = "Quantity must be greater than 0")
    @Column(name = "quantity", nullable = false, precision = 19, scale = 8)
    private BigDecimal quantity;

    /**
     * Purchase price per unit in the specified currency. Requirement REQ-2.6.2:
     * Track purchase
     * price for gain/loss calculation
     */
    @NotNull(message = "Purchase price cannot be null")
    @DecimalMin(value = "0.00", message = "Purchase price must be non-negative")
    @Column(name = "purchase_price", nullable = false, precision = 19, scale = 4)
    private BigDecimal purchasePrice;

    /**
     * Current market price per unit in the specified currency. Updated by market
     * data integration
     * or manually by user.
     *
     * <p>
     * Requirement REQ-2.6.4: Track current price for portfolio valuation
     */
    @NotNull(message = "Current price cannot be null")
    @DecimalMin(value = "0.00", message = "Current price must be non-negative")
    @Column(name = "current_price", nullable = false, precision = 19, scale = 4)
    private BigDecimal currentPrice;

    /**
     * Currency code in ISO 4217 format (e.g., USD, EUR, GBP). Requirement REQ-2.8:
     * Multi-currency
     * support for assets
     */
    @NotNull(message = "Currency cannot be null")
    @Size(min = 3, max = 3, message = "Currency must be a 3-letter ISO 4217 code")
    @Column(name = "currency", nullable = false, length = 3)
    private String currency;

    /** FK to the currencies table for referential integrity. */
    @Column(name = "currency_id")
    private Long currencyId;

    /** Reference to the currency entity (lazy-loaded). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "currency_id", insertable = false, updatable = false)
    private Currency currencyEntity;

    /**
     * Date when the asset was purchased. Used for calculating holding period and
     * tax implications.
     *
     * <p>
     * Requirement REQ-2.6.2: Track purchase date
     */
    @NotNull(message = "Purchase date cannot be null")
    @Column(name = "purchase_date", nullable = false)
    private LocalDate purchaseDate;

    /**
     * Optional notes about the asset (e.g., investment thesis, broker info).
     *
     * <p>
     * <strong>Encrypted Field:</strong> This field is stored encrypted in the
     * database. The
     * AssetService handles encryption/decryption transparently.
     */
    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    // ===== Physical Asset Fields (for VEHICLE, JEWELRY, COLLECTIBLE, ELECTRONICS,
    // FURNITURE) =====

    /**
     * Serial number or identification number for physical assets. Examples: VIN for
     * vehicles,
     * serial number for electronics, certificate number for jewelry.
     *
     * <p>
     * <strong>Encrypted Field:</strong> This field is stored encrypted in the
     * database.
     *
     * <p>
     * Requirement REQ-2.6: Track physical asset identification
     */
    @Size(max = 500, message = "Serial number must not exceed 500 characters")
    @Column(name = "serial_number", length = 500)
    private String serialNumber;

    /**
     * Brand or manufacturer name for physical assets. Examples: Tesla, Apple,
     * Rolex, IKEA.
     *
     * <p>
     * <strong>Encrypted Field:</strong> This field is stored encrypted in the
     * database.
     */
    @Size(max = 500, message = "Brand must not exceed 500 characters")
    @Column(name = "brand", length = 500)
    private String brand;

    /**
     * Model name or number for physical assets. Examples: Model 3, iPhone 15 Pro,
     * Submariner, MALM.
     *
     * <p>
     * <strong>Encrypted Field:</strong> This field is stored encrypted in the
     * database.
     */
    @Size(max = 500, message = "Model must not exceed 500 characters")
    @Column(name = "model", length = 500)
    private String model;

    /**
     * Physical condition of the asset. Used for depreciation calculation and value
     * assessment.
     *
     * <p>
     * Valid values: NEW, EXCELLENT, GOOD, FAIR, POOR
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "condition", length = 20)
    private AssetCondition condition;

    /**
     * Warranty expiration date for physical assets. Used to track coverage and
     * factor into resale
     * value.
     */
    @Column(name = "warranty_expiration")
    private LocalDate warrantyExpiration;

    /**
     * Expected useful life in years (for depreciation calculation). Used for
     * straight-line
     * depreciation of physical assets.
     *
     * <p>
     * Typical values:
     *
     * <ul>
     * <li>Vehicles: 10-15 years
     * <li>Electronics: 3-5 years
     * <li>Furniture: 7-10 years
     * <li>Jewelry/Collectibles: N/A (may appreciate)
     * </ul>
     */
    @Column(name = "useful_life_years")
    private Integer usefulLifeYears;

    /**
     * Path to photo or image file for the asset. Stored as relative path to the
     * uploads directory.
     *
     * <p>
     * Examples: assets/photos/IMG_1234.jpg, assets/photos/vehicle_vin12345.png
     */
    @Size(max = 500, message = "Photo path must not exceed 500 characters")
    @Column(name = "photo_path", length = 500)
    private String photoPath;

    // ===== End Physical Asset Fields =====

    /**
     * Timestamp when the current price was last updated. Set when market data is
     * refreshed.
     *
     * <p>
     * Requirement REQ-2.6.5: Track when prices were last updated
     */
    @Column(name = "last_updated")
    private LocalDateTime lastUpdated;

    /**
     * Timestamp when the asset record was created. Automatically set by Hibernate
     * on first insert.
     */
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /**
     * Timestamp when the asset record was last modified. Automatically updated by
     * Hibernate on any
     * modification.
     */
    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    /**
     * Calculates the total value of this asset (quantity * currentPrice).
     *
     * @return total current value
     */
    public BigDecimal getTotalValue() {
        return quantity.multiply(currentPrice);
    }

    /**
     * Calculates the total cost basis (quantity * purchasePrice).
     *
     * @return total purchase cost
     */
    public BigDecimal getTotalCost() {
        return quantity.multiply(purchasePrice);
    }

    /**
     * Calculates the unrealized gain/loss amount.
     *
     * @return (currentPrice - purchasePrice) * quantity
     */
    public BigDecimal getUnrealizedGain() {
        return currentPrice.subtract(purchasePrice).multiply(quantity);
    }

    /**
     * Calculates the unrealized gain/loss percentage.
     *
     * @return percentage gain/loss (0.15 = 15% gain, -0.10 = 10% loss)
     */
    public BigDecimal getGainPercentage() {
        if (purchasePrice.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }
        return currentPrice
                .subtract(purchasePrice)
                .divide(purchasePrice, 4, java.math.RoundingMode.HALF_UP);
    }

    /**
     * Calculates the depreciated value of a physical asset using straight-line
     * depreciation. Only
     * applicable to physical assets with a useful life defined.
     *
     * <p>
     * Formula: Purchase Price - (Purchase Price / Useful Life * Years Owned)
     *
     * @return depreciated value, or current price if not a depreciating physical
     *         asset
     */
    public BigDecimal getDepreciatedValue() {
        // Only calculate depreciation for physical assets with useful life
        if (type == null || !type.isPhysical()) {
            return null;
        }

        // Use explicit usefulLifeYears, then fall back to type default
        Integer effectiveLife = usefulLifeYears;
        if (effectiveLife == null || effectiveLife <= 0) {
            effectiveLife = type.getDefaultUsefulLifeYears();
        }
        if (effectiveLife == null || effectiveLife <= 0) {
            return null;
        }

        // Calculate years owned
        long daysOwned = java.time.temporal.ChronoUnit.DAYS.between(purchaseDate, LocalDate.now());
        BigDecimal yearsOwned = BigDecimal.valueOf(daysOwned)
                .divide(BigDecimal.valueOf(365), 4, java.math.RoundingMode.HALF_UP);

        // If asset is fully depreciated, return salvage value (10% of purchase price)
        if (yearsOwned.compareTo(BigDecimal.valueOf(effectiveLife)) >= 0) {
            return getTotalCost().multiply(BigDecimal.valueOf(0.10));
        }

        // Calculate annual depreciation
        BigDecimal annualDepreciation = getTotalCost()
                .divide(
                        BigDecimal.valueOf(effectiveLife),
                        4,
                        java.math.RoundingMode.HALF_UP);
        BigDecimal totalDepreciation = annualDepreciation.multiply(yearsOwned);

        // Depreciated value = Purchase Cost - Total Depreciation
        BigDecimal depreciatedValue = getTotalCost().subtract(totalDepreciation);

        // Never go below 10% salvage value
        BigDecimal salvageValue = getTotalCost().multiply(BigDecimal.valueOf(0.10));
        return depreciatedValue.max(salvageValue);
    }

    /**
     * Calculates the condition-adjusted value of a physical asset. Applies the
     * condition retention
     * factor to the depreciated value.
     *
     * @return condition-adjusted value, or depreciated value if condition is not
     *         set
     */
    public BigDecimal getConditionAdjustedValue() {
        if (!isPhysical()) {
            return null;
        }
        if (condition == null) {
            return getDepreciatedValue();
        }

        // On purchase day (daysOwned == 0), return full cost — condition wear hasn't
        // accrued yet
        if (purchaseDate != null) {
            long daysOwned = java.time.temporal.ChronoUnit.DAYS.between(purchaseDate, LocalDate.now());
            if (daysOwned == 0) {
                return getTotalCost();
            }
        }

        BigDecimal baseValue = getDepreciatedValue();
        if (baseValue == null) {
            return null;
        }
        return baseValue.multiply(BigDecimal.valueOf(condition.getValueRetentionFactor()));
    }

    /**
     * Checks if this asset is a physical asset (not financial).
     *
     * @return true if the asset type is physical, false otherwise
     */
    public boolean isPhysical() {
        return type != null && type.isPhysical();
    }

    /**
     * Checks if the warranty is still valid.
     *
     * @return true if warranty expiration date is in the future, false otherwise
     */
    public boolean isWarrantyValid() {
        return warrantyExpiration != null && warrantyExpiration.isAfter(LocalDate.now());
    }
}
