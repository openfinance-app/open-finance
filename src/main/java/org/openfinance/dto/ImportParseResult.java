package org.openfinance.dto;

import java.math.BigDecimal;
import java.util.List;
import lombok.Builder;
import lombok.Data;

/**
 * Encapsulates the result of parsing an import file, typically used for formats like OFX that
 * provide global meta-information (like account currency and ledger balance) alongside the list of
 * transactions.
 */
@Data
@Builder
public class ImportParseResult {
    /** The imported transactions parsed from the file. */
    private List<ImportedTransaction> transactions;

    /** The starting or closing ledger balance declared in the file, if any. */
    private BigDecimal ledgerBalance;

    /**
     * The global currency declared in the file, used as fallback when individual transactions omit
     * currency.
     */
    private String currency;
}
