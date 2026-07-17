package org.openfinance.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.openfinance.converter.EncryptedStringConverter;

/**
 * Entity representing a real estate property in the Open-Finance system.
 *
 * <p>Real estate properties include residential homes, commercial buildings, land parcels, and
 * other types of real property. Each property belongs to a user and optionally links to a mortgage
 * liability.
 *
 * <p>Requirement REQ-2.16: Real Estate Management - Users can track real estate properties
 * including purchase price, current value, rental income, and associated mortgages.
 *
 * <p><strong>Security Note:</strong> The {@code name}, {@code address}, {@code notes}, and {@code
 * documents} fields will be encrypted by the RealEstateService before persisting to the database to
 * protect sensitive personal information.
 *
 * <p><strong>Location Data:</strong> Latitude and longitude are stored for future mapping features
 * but are not encrypted as they don't directly reveal personal information when stored separately
 * from the address.
 *
 * @author Open-Finance Development Team
 * @version 1.0
 * @since 1.0
 */
@Entity
@Table(
        name = "real_estate_properties",
        indexes = {
            @Index(name = "idx_real_estate_user_id", columnList = "user_id"),
            @Index(name = "idx_real_estate_property_type", columnList = "property_type"),
            @Index(name = "idx_real_estate_user_type", columnList = "user_id, property_type"),
            @Index(name = "idx_real_estate_mortgage_id", columnList = "mortgage_id"),
            @Index(name = "idx_real_estate_purchase_date", columnList = "purchase_date")
        })
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@ToString(onlyExplicitlyIncluded = true)
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class RealEstateProperty {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @ToString.Include
    @EqualsAndHashCode.Include
    private Long id;

    /**
     * The user who owns this property. Requirement REQ-2.16.1: Each property belongs to a single
     * user
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
     * Name of the property (e.g., "Main Residence", "Rental Property #1").
     *
     * <p><strong>Encrypted Field:</strong> This field is stored encrypted in the database. The
     * RealEstateService handles encryption/decryption transparently. Requirement REQ-2.16.2:
     * Property must have a descriptive name
     */
    @NotNull(message = "Property name cannot be null")
    @Size(min = 1, max = 500, message = "Property name must be between 1 and 500 characters")
    @Column(name = "name", nullable = false, length = 500) // Extra length for encrypted data
    @Convert(converter = EncryptedStringConverter.class)
    private String name;

    /**
     * Full address of the property.
     *
     * <p><strong>Encrypted Field:</strong> This field is stored encrypted in the database to
     * protect the user's property location information. Requirement REQ-2.16.3: Property must have
     * a full address
     */
    @NotNull(message = "Property address cannot be null")
    @Size(min = 1, max = 1000, message = "Property address must be between 1 and 1000 characters")
    @Column(name = "address", nullable = false, length = 1000) // Extra length for encrypted data
    @Convert(converter = EncryptedStringConverter.class)
    private String address;

    /**
     * Type of property (RESIDENTIAL, COMMERCIAL, LAND, etc.). Requirement REQ-2.16: Property type
     * categorization
     */
    @NotNull(message = "Property type cannot be null")
    @Enumerated(EnumType.STRING)
    @Column(name = "property_type", nullable = false, length = 20)
    private PropertyType propertyType;

    /**
     * Purchase price of the property. Stored as encrypted string to protect financial information.
     *
     * <p><strong>Encrypted Field:</strong> Stored as encrypted string. Use
     * getPurchasePriceDecimal() after decryption for calculations. Requirement REQ-2.16.4: Track
     * purchase price with high precision
     */
    @NotNull(message = "Purchase price cannot be null")
    @Size(max = 500, message = "Purchase price (encrypted) cannot exceed 500 characters")
    @Column(name = "purchase_price", nullable = false, length = 500)
    @Convert(converter = EncryptedStringConverter.class)
    private String purchasePrice;

    /** Date when the property was purchased. Requirement REQ-2.16.5: Track purchase date */
    @NotNull(message = "Purchase date cannot be null")
    @PastOrPresent(message = "Purchase date cannot be in the future")
    @Column(name = "purchase_date", nullable = false)
    private LocalDate purchaseDate;

    /**
     * Current estimated value of the property. Stored as encrypted string to protect financial
     * information.
     *
     * <p><strong>Encrypted Field:</strong> Stored as encrypted string. Use getCurrentValueDecimal()
     * after decryption for calculations. Requirement REQ-2.16.6: Track current estimated value
     */
    @NotNull(message = "Current value cannot be null")
    @Size(max = 500, message = "Current value (encrypted) cannot exceed 500 characters")
    @Column(name = "current_value", nullable = false, length = 500)
    @Convert(converter = EncryptedStringConverter.class)
    private String currentValue;

    /**
     * Currency code for monetary amounts (ISO 4217). Requirement REQ-2.8: Multi-currency support
     */
    @NotNull(message = "Currency cannot be null")
    @Pattern(regexp = "[A-Z]{3}", message = "Currency must be a 3-letter ISO 4217 code")
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
     * Optional link to associated mortgage liability. Requirement REQ-2.16.7: Link property to
     * mortgage for equity calculation
     */
    @Column(name = "mortgage_id")
    private Long mortgageId;

    /** Relationship to the Liability entity (mortgage). Lazy-loaded to optimize performance. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "mortgage_id", insertable = false, updatable = false)
    private Liability mortgage;

    /**
     * ID of the associated generic Asset record. Links this property to the centralized portfolio
     * tracking.
     */
    @Column(name = "asset_id")
    private Long assetId;

    /** Relationship to the Asset entity. Lazy-loaded to optimize performance. */
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "asset_id", insertable = false, updatable = false)
    private Asset asset;

    /**
     * Monthly rental income generated by this property (if applicable). Stored as encrypted string
     * to protect financial information. Can be null if property is not rented.
     *
     * <p><strong>Encrypted Field:</strong> Stored as encrypted string. Use getRentalIncomeDecimal()
     * after decryption for calculations. Requirement REQ-2.16.8: Track rental income for investment
     * properties
     */
    @Size(max = 500, message = "Rental income (encrypted) cannot exceed 500 characters")
    @Column(name = "rental_income", length = 500)
    @Convert(converter = EncryptedStringConverter.class)
    private String rentalIncome;

    /**
     * Additional notes about the property.
     *
     * <p><strong>Encrypted Field:</strong> This field is stored encrypted in the database.
     * Requirement REQ-2.16.9: Allow additional notes for property details
     */
    @Size(max = 2000, message = "Notes cannot exceed 2000 characters")
    @Column(name = "notes", columnDefinition = "TEXT")
    @Convert(converter = EncryptedStringConverter.class)
    private String notes;

    /**
     * JSON string containing references to attached documents (e.g., deeds, contracts).
     *
     * <p><strong>Encrypted Field:</strong> This field is stored encrypted in the database.
     *
     * <p>Format: JSON array of document metadata (file IDs, names, types) Requirement REQ-2.12:
     * File attachment support for real estate documents
     */
    @Size(max = 2000, message = "Documents metadata cannot exceed 2000 characters")
    @Column(name = "documents", length = 2000)
    @Convert(converter = EncryptedStringConverter.class)
    private String documents;

    /**
     * Latitude coordinate for property location. Used for future mapping features.
     *
     * <p>Not encrypted as it doesn't directly reveal personal information when stored separately
     * from the address. Requirement REQ-2.16.10: Store location coordinates for mapping
     */
    @DecimalMin(value = "-90.0", message = "Latitude must be >= -90")
    @DecimalMax(value = "90.0", message = "Latitude must be <= 90")
    @Column(name = "latitude", precision = 10, scale = 7)
    private BigDecimal latitude;

    /**
     * Longitude coordinate for property location. Used for future mapping features.
     *
     * <p>Not encrypted as it doesn't directly reveal personal information when stored separately
     * from the address. Requirement REQ-2.16.10: Store location coordinates for mapping
     */
    @DecimalMin(value = "-180.0", message = "Longitude must be >= -180")
    @DecimalMax(value = "180.0", message = "Longitude must be <= 180")
    @Column(name = "longitude", precision = 10, scale = 7)
    private BigDecimal longitude;

    /**
     * Indicates whether this property record is active. Soft delete: inactive properties are
     * excluded from net worth calculations.
     */
    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private boolean isActive = true;

    /** Timestamp when this property record was created. */
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /** Timestamp when this property record was last updated. */
    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    // ==================== Helper Methods ====================

    /**
     * Parses the decrypted purchase price string to BigDecimal. This method should be called AFTER
     * the purchasePrice has been decrypted.
     *
     * @return the purchase price as BigDecimal, or null if purchase price is null/empty
     * @throws NumberFormatException if the decrypted string is not a valid number
     */
    public BigDecimal getPurchasePriceDecimal() {
        if (purchasePrice == null || purchasePrice.isBlank()) {
            return null;
        }
        return new BigDecimal(purchasePrice).setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * Parses the decrypted current value string to BigDecimal. This method should be called AFTER
     * the currentValue has been decrypted.
     *
     * @return the current value as BigDecimal, or null if current value is null/empty
     * @throws NumberFormatException if the decrypted string is not a valid number
     */
    public BigDecimal getCurrentValueDecimal() {
        if (currentValue == null || currentValue.isBlank()) {
            return null;
        }
        return new BigDecimal(currentValue).setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * Parses the decrypted rental income string to BigDecimal. This method should be called AFTER
     * the rentalIncome has been decrypted.
     *
     * @return the rental income as BigDecimal, or null if rental income is null/empty
     * @throws NumberFormatException if the decrypted string is not a valid number
     */
    public BigDecimal getRentalIncomeDecimal() {
        if (rentalIncome == null || rentalIncome.isBlank()) {
            return null;
        }
        return new BigDecimal(rentalIncome).setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * Calculates the total appreciation/depreciation of the property. This method should be called
     * AFTER decryption.
     *
     * <p>Formula: Current Value - Purchase Price
     *
     * @return appreciation (positive) or depreciation (negative), or null if values are missing
     */
    public BigDecimal getAppreciation() {
        BigDecimal current = getCurrentValueDecimal();
        BigDecimal purchase = getPurchasePriceDecimal();

        if (current == null || purchase == null) {
            return null;
        }

        return current.subtract(purchase).setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * Calculates the appreciation percentage. This method should be called AFTER decryption.
     *
     * <p>Formula: ((Current Value - Purchase Price) / Purchase Price) * 100
     *
     * @return appreciation percentage, or null if values are missing or purchase price is zero
     */
    public BigDecimal getAppreciationPercentage() {
        BigDecimal appreciation = getAppreciation();
        BigDecimal purchase = getPurchasePriceDecimal();

        if (appreciation == null || purchase == null || purchase.compareTo(BigDecimal.ZERO) == 0) {
            return null;
        }

        return appreciation
                .divide(purchase, 4, RoundingMode.HALF_UP)
                .multiply(new BigDecimal("100"))
                .setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * Calculates the annual rental yield (if property generates rental income). This method should
     * be called AFTER decryption.
     *
     * <p>Formula: (Monthly Rental Income * 12 / Current Value) * 100
     *
     * @return annual rental yield percentage, or null if values are missing or current value is
     *     zero
     */
    public BigDecimal getRentalYield() {
        BigDecimal monthly = getRentalIncomeDecimal();
        BigDecimal current = getCurrentValueDecimal();

        if (monthly == null || current == null || current.compareTo(BigDecimal.ZERO) == 0) {
            return null;
        }

        BigDecimal annualIncome = monthly.multiply(new BigDecimal("12"));
        return annualIncome
                .divide(current, 4, RoundingMode.HALF_UP)
                .multiply(new BigDecimal("100"))
                .setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * Checks if this property generates rental income.
     *
     * @return true if rental income is set and greater than zero
     */
    public boolean isRentalProperty() {
        BigDecimal rental = getRentalIncomeDecimal();
        return rental != null && rental.compareTo(BigDecimal.ZERO) > 0;
    }

    /**
     * Checks if this property has an associated mortgage.
     *
     * @return true if mortgage ID is set
     */
    public boolean hasMortgage() {
        return mortgageId != null;
    }
}
