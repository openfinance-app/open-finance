package org.openfinance.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.Builder;
import lombok.Data;

/** A dated reference quote; it is an estimate of units, not an execution confirmation. */
@Data
@Builder
public class InvestmentQuote {
    private Long assetId;
    private BigDecimal quantity;

    /** Units removable on the quote date without invalidating subsequent movements. */
    private BigDecimal availableQuantity;

    private BigDecimal price;
    private BigDecimal value;
    private String currency;
    private LocalDate priceDate;
    private String source;
}
