package org.openfinance.entity;

/**
 * Enumeration of property types for real estate tracking in the Open-Finance system.
 *
 * <p>This enum categorizes different types of real estate properties, allowing users to classify
 * their real estate holdings appropriately.
 *
 * <p>Requirement REQ-2.16: Real Estate Management - Users can track different types of real estate
 * properties including residential, commercial, and land.
 *
 * @author Open-Finance Development Team
 * @version 1.0
 * @since 1.0
 */
public enum PropertyType {

    /**
     * Residential property (houses, apartments, condos, etc.) Used for properties primarily
     * intended for living purposes.
     */
    RESIDENTIAL("Residential"),

    /**
     * Commercial property (office buildings, retail spaces, warehouses, etc.) Used for properties
     * intended for business or commercial use.
     */
    COMMERCIAL("Commercial"),

    /**
     * Undeveloped land or lots Used for vacant land, agricultural land, or lots awaiting
     * development.
     */
    LAND("Land"),

    /**
     * Mixed-use property combining residential and commercial Used for properties that serve both
     * residential and commercial purposes.
     */
    MIXED_USE("Mixed-Use"),

    /**
     * Industrial property (factories, manufacturing plants, etc.) Used for properties used for
     * industrial or manufacturing purposes.
     */
    INDUSTRIAL("Industrial"),

    /**
     * Other types not covered by the above categories Catch-all for unique or specialized property
     * types.
     */
    OTHER("Other");

    private final String displayName;

    PropertyType(String displayName) {
        this.displayName = displayName;
    }

    /**
     * Gets the human-readable display name for this property type.
     *
     * @return the display name
     */
    public String getDisplayName() {
        return displayName;
    }

    /**
     * Checks if this property type is typically used for income generation (rental).
     *
     * @return true if the property type is commonly used for rental income
     */
    public boolean isIncomeGenerating() {
        return this == COMMERCIAL || this == INDUSTRIAL || this == MIXED_USE;
    }

    /**
     * Checks if this property type is residential or has residential components.
     *
     * @return true if the property is residential or mixed-use
     */
    public boolean hasResidentialComponent() {
        return this == RESIDENTIAL || this == MIXED_USE;
    }
}
