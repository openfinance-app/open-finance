package org.openfinance.dto;

import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.openfinance.entity.AssetType;

/**
 * DTO representing asset allocation by type.
 *
 * <p>Used for dashboard treemap visualization showing portfolio composition.
 *
 * <p><b>Task 4.3.6:</b> Asset allocation chart component data
 *
 * <p><b>Requirement REQ-2.6.3:</b> Portfolio analytics and visualization
 *
 * @since 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AssetAllocation {

    /** Asset type category (e.g., STOCK, CRYPTO, BOND) */
    private AssetType type;

    /** Display name for the asset type */
    private String typeName;

    /** Total value of all assets in this type */
    private BigDecimal totalValue;

    /** Percentage of total portfolio value */
    private BigDecimal percentage;

    /** Number of individual assets in this type */
    private long assetCount;

    /** Currency code (e.g., "EUR", "USD") */
    private String currency;
}
