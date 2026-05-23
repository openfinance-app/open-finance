package org.openfinance.entity;

/**
 * Enumeration representing different types of financial assets. Used to
 * categorize assets for
 * portfolio tracking and reporting.
 *
 * <p>
 * Asset types include:
 *
 * <ul>
 * <li>STOCK - Individual company stocks/equities
 * <li>ETF - Exchange-traded funds
 * <li>MUTUAL_FUND - Mutual funds and index funds
 * <li>BOND - Government and corporate bonds
 * <li>CRYPTO - Cryptocurrencies (Bitcoin, Ethereum, etc.)
 * <li>COMMODITY - Physical commodities (gold, silver, oil, etc.)
 * <li>REAL_ESTATE - Real estate investments (REITs, properties)
 * <li>OTHER - Any other asset types
 * </ul>
 *
 * @see Asset
 */
public enum AssetType {
    /** Individual company stocks/equities. Example: AAPL, GOOGL, MSFT */
    STOCK,

    /** Exchange-traded funds. Example: SPY, QQQ, VTI */
    ETF,

    /** Mutual funds and index funds. Example: VFIAX, FXAIX */
    MUTUAL_FUND,

    /**
     * Government and corporate bonds. Example: US Treasury bonds, corporate debt
     */
    BOND,

    /** Cryptocurrencies. Example: Bitcoin (BTC), Ethereum (ETH) */
    CRYPTO,

    /** Physical commodities. Example: Gold, Silver, Oil, Natural Gas */
    COMMODITY,

    /**
     * Real estate investments. Example: REITs, rental properties, commercial real
     * estate
     */
    REAL_ESTATE,

    /**
     * Vehicles (cars, motorcycles, boats, aircraft). Example: Tesla Model 3, Honda
     * Civic, Yamaha
     * YZF-R1
     */
    VEHICLE,

    /** Jewelry and precious items. Example: Watches, rings, necklaces, gemstones */
    JEWELRY,

    /**
     * Collectible items with potential appreciation. Example: Artwork, antiques,
     * coins, stamps,
     * trading cards
     */
    COLLECTIBLE,

    /**
     * Electronic devices and equipment. Example: Laptops, smartphones, cameras,
     * audio equipment
     */
    ELECTRONICS,

    /** Furniture and home furnishings. Example: Sofas, tables, beds, appliances */
    FURNITURE,

    /** Any other asset types not covered above. */
    OTHER;

    /**
     * Returns a human-readable display name for the asset type.
     *
     * @return formatted string suitable for UI display
     */
    public String getDisplayName() {
        return switch (this) {
            case STOCK -> "Stock";
            case ETF -> "ETF";
            case MUTUAL_FUND -> "Mutual Fund";
            case BOND -> "Bond";
            case CRYPTO -> "Cryptocurrency";
            case COMMODITY -> "Commodity";
            case REAL_ESTATE -> "Real Estate";
            case VEHICLE -> "Vehicle";
            case JEWELRY -> "Jewelry";
            case COLLECTIBLE -> "Collectible";
            case ELECTRONICS -> "Electronics";
            case FURNITURE -> "Furniture";
            case OTHER -> "Other";
        };
    }

    /**
     * Checks if this asset type represents a security (tradable financial
     * instrument).
     *
     * @return true if the asset is a security (STOCK, ETF, MUTUAL_FUND, BOND),
     *         false otherwise
     */
    public boolean isSecurity() {
        return this == STOCK || this == ETF || this == MUTUAL_FUND || this == BOND;
    }

    /**
     * Checks if this asset type typically has real-time market data available.
     *
     * @return true if real-time prices are typically available, false otherwise
     */
    public boolean hasRealTimeData() {
        return this == STOCK || this == ETF || this == CRYPTO;
    }

    /**
     * Checks if this asset type represents a physical asset (not financial).
     *
     * @return true if the asset is physical (VEHICLE, JEWELRY, COLLECTIBLE,
     *         ELECTRONICS,
     *         FURNITURE), false otherwise
     */
    public boolean isPhysical() {
        return this == VEHICLE
                || this == JEWELRY
                || this == COLLECTIBLE
                || this == ELECTRONICS
                || this == FURNITURE;
    }

    /**
     * Checks if this asset type typically depreciates over time.
     *
     * @return true if the asset typically loses value, false otherwise
     */
    public boolean isDepreciating() {
        return this == VEHICLE || this == ELECTRONICS || this == FURNITURE;
    }

    /**
     * Returns the default useful life in years for depreciating asset types. Used
     * when the user
     * does not explicitly provide a useful life.
     *
     * @return default useful life in years, or null if the type does not depreciate
     */
    public Integer getDefaultUsefulLifeYears() {
        return switch (this) {
            case VEHICLE -> 10;
            case ELECTRONICS -> 5;
            case FURNITURE -> 10;
            default -> null;
        };
    }
}
