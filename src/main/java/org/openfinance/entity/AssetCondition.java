package org.openfinance.entity;

/**
 * Enumeration representing the physical condition of an asset. Used for physical assets (VEHICLE,
 * JEWELRY, COLLECTIBLE, ELECTRONICS, FURNITURE) to assess current value and calculate depreciation.
 *
 * <p>Condition affects:
 *
 * <ul>
 *   <li>Current market value estimation
 *   <li>Depreciation rate calculation
 *   <li>Insurance valuation
 *   <li>Resale value prediction
 * </ul>
 *
 * @see Asset
 * @see AssetType
 */
public enum AssetCondition {
    /**
     * Brand new, unused, with original packaging and warranty. Typically retains 90-100% of
     * purchase price in the short term.
     */
    NEW,

    /**
     * Like new, minimal to no signs of use, fully functional. Well-maintained with no visible wear
     * or defects. Typically retains 75-90% of purchase price.
     */
    EXCELLENT,

    /**
     * Normal wear from regular use, fully functional. Minor cosmetic imperfections but no
     * functional issues. Typically retains 50-75% of purchase price.
     */
    GOOD,

    /**
     * Noticeable wear and tear, may have minor functional issues. Cosmetic damage present but asset
     * is still usable. Typically retains 25-50% of purchase price.
     */
    FAIR,

    /**
     * Heavy wear, significant cosmetic or functional issues. May require repairs or maintenance to
     * function properly. Typically retains 0-25% of purchase price.
     */
    POOR;

    /**
     * Returns a human-readable display name for the condition.
     *
     * @return formatted string suitable for UI display
     */
    public String getDisplayName() {
        return switch (this) {
            case NEW -> "New";
            case EXCELLENT -> "Excellent";
            case GOOD -> "Good";
            case FAIR -> "Fair";
            case POOR -> "Poor";
        };
    }

    /**
     * Returns the typical value retention percentage for this condition. Used as a multiplier for
     * estimating current value from purchase price.
     *
     * @return decimal value between 0.0 and 1.0 (e.g., 0.75 = 75% retention)
     */
    public double getValueRetentionFactor() {
        return switch (this) {
            case NEW -> 0.95; // 95% retention
            case EXCELLENT -> 0.82; // 82% retention
            case GOOD -> 0.62; // 62% retention
            case FAIR -> 0.37; // 37% retention
            case POOR -> 0.12; // 12% retention
        };
    }

    /**
     * Returns the condition description for help text or tooltips.
     *
     * @return detailed description of what this condition means
     */
    public String getDescription() {
        return switch (this) {
            case NEW -> "Brand new, unused, with original packaging and warranty";
            case EXCELLENT -> "Like new, minimal signs of use, fully functional";
            case GOOD -> "Normal wear from regular use, fully functional";
            case FAIR -> "Noticeable wear, may have minor functional issues";
            case POOR -> "Heavy wear, significant cosmetic or functional issues";
        };
    }
}
