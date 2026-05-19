package org.openfinance.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.openfinance.entity.PropertyType;

/**
 * Data Transfer Object for real estate property search criteria.
 *
 * <p>This DTO is used to build dynamic queries for searching properties. All fields are optional -
 * if a field is null, it won't be included in the search filter. Multiple criteria can be combined
 * (AND logic).
 *
 * <p><strong>Supported Search Filters:</strong>
 *
 * <ul>
 *   <li><strong>keyword</strong> - Search in property name or address (case-insensitive, partial
 *       match)
 *   <li><strong>propertyType</strong> - Filter by property type (RESIDENTIAL, COMMERCIAL, LAND,
 *       etc.)
 *   <li><strong>currency</strong> - Filter by currency code (USD, EUR, etc.)
 *   <li><strong>isActive</strong> - Filter by active status (true=active, false=inactive)
 *   <li><strong>hasMortgage</strong> - Filter by whether property has a mortgage
 *   <li><strong>purchaseDateFrom</strong> - Filter by purchase date >= this date
 *   <li><strong>purchaseDateTo</strong> - Filter by purchase date <= this date
 *   <li><strong>valueMin</strong> - Filter properties with current value >= this value
 *   <li><strong>valueMax</strong> - Filter properties with current value <= this value
 *   <li><strong>priceMin</strong> - Filter properties with purchase price >= this value
 *   <li><strong>priceMax</strong> - Filter properties with purchase price <= this value
 *   <li><strong>rentalIncomeMin</strong> - Filter properties with rental income >= this value
 * </ul>
 *
 * @see org.openfinance.entity.RealEstateProperty
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RealEstateSearchCriteria {

    /**
     * Keyword to search in property name or address (case-insensitive).
     *
     * <p>Uses LIKE query with wildcards: %keyword%
     *
     * <p>Example: "main" will match "Main Residence", "123 Main St", etc.
     */
    private String keyword;

    /**
     * Filter by property type.
     *
     * <p>If null, includes all property types.
     */
    private PropertyType propertyType;

    /**
     * Filter by currency code.
     *
     * <p>If null, includes properties in all currencies.
     *
     * <p>Example: "USD", "EUR", "GBP"
     */
    private String currency;

    /**
     * Filter by active status.
     *
     * <ul>
     *   <li>true - only active properties
     *   <li>false - only inactive properties
     *   <li>null - all properties (both active and inactive)
     * </ul>
     */
    private Boolean isActive;

    /**
     * Filter by whether property has a mortgage.
     *
     * <ul>
     *   <li>true - only properties with a mortgage
     *   <li>false - only properties without a mortgage
     *   <li>null - all properties
     * </ul>
     */
    private Boolean hasMortgage;

    /**
     * Filter properties purchased on or after this date.
     *
     * <p>If null, no lower date bound.
     */
    private LocalDate purchaseDateFrom;

    /**
     * Filter properties purchased on or before this date.
     *
     * <p>If null, no upper date bound.
     */
    private LocalDate purchaseDateTo;

    /**
     * Filter properties with current value greater than or equal to this value.
     *
     * <p>If null, no lower value bound.
     */
    private BigDecimal valueMin;

    /**
     * Filter properties with current value less than or equal to this value.
     *
     * <p>If null, no upper value bound.
     */
    private BigDecimal valueMax;

    /**
     * Filter properties with purchase price greater than or equal to this value.
     *
     * <p>If null, no lower price bound.
     */
    private BigDecimal priceMin;

    /**
     * Filter properties with purchase price less than or equal to this value.
     *
     * <p>If null, no upper price bound.
     */
    private BigDecimal priceMax;

    /**
     * Filter properties with monthly rental income greater than or equal to this value.
     *
     * <p>If null, no lower rental income bound.
     */
    private BigDecimal rentalIncomeMin;

    /**
     * Checks if any search criteria is provided.
     *
     * @return true if at least one filter is set, false if all fields are null
     */
    public boolean hasAnyCriteria() {
        return keyword != null
                || propertyType != null
                || currency != null
                || isActive != null
                || hasMortgage != null
                || purchaseDateFrom != null
                || purchaseDateTo != null
                || valueMin != null
                || valueMax != null
                || priceMin != null
                || priceMax != null
                || rentalIncomeMin != null;
    }
}
