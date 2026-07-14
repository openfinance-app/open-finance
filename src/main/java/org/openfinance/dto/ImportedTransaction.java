package org.openfinance.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Data Transfer Object for imported transactions from QIF/OFX/CSV files.
 *
 * <p>This DTO represents a transaction extracted from an import file before it is mapped to the
 * application's Transaction entity. It includes all fields that can be parsed from various import
 * formats.
 *
 * <p>Requirements: - REQ-2.5.1.1: File Format Support - REQ-2.5.1.3: Import Validation
 *
 * @see org.openfinance.entity.Transaction
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ImportedTransaction {

    /** Transaction date (required) */
    private LocalDate transactionDate;

    /** Payee/merchant name (can be empty). Can be updated by AutoCategorization. */
    private String payee;

    /** Original Payee/merchant name parsed from the file before any modification. */
    private String originalPayee;

    /** Transaction amount (required) Negative for expenses, positive for income */
    private BigDecimal amount;

    /** Memo/notes/description */
    private String memo;

    /**
     * Category name from import file or suggested by system Can be null if not specified in file
     */
    private String category;

    /** Confidence score (0.0 to 1.0) if the category and payee were auto-assigned. */
    private Double categorizationConfidence;

    /** Cleared status from QIF Values: "cleared", "reconciled", "uncleared", null */
    private String clearedStatus;

    /** Check/reference number (optional) Used as import ID for duplicate detection */
    private String referenceNumber;

    /** Account name from import file Used to match against user's accounts */
    private String accountName;

    /**
     * QIF account type from the !Account T field or !Type directive (e.g., "Bank", "CCard", "Cash",
     * "Oth A", "Invst"). Used to map to the correct {@code AccountType} during account creation.
     */
    private String qifAccountType;

    /**
     * Source account ID from the import format, when available.
     *
     * <p>Used by high-fidelity importers (such as Skrooge JSON) to preserve stable account
     * relationships without relying on fuzzy name matching.
     */
    private Long sourceAccountId;

    /** Source account number from the import format, when available. */
    private String accountNumber;

    /** Line number in source file (for error reporting) */
    private Integer lineNumber;

    /**
     * Split transaction details (if applicable) For transactions split across multiple categories
     */
    @Builder.Default private List<SplitEntry> splits = new ArrayList<>();

    /** Validation errors for this transaction */
    @Builder.Default private List<String> validationErrors = new ArrayList<>();

    /** Whether this transaction is a potential duplicate */
    @Builder.Default private boolean potentialDuplicate = false;

    /** Import source file name */
    private String sourceFileName;

    /** Raw line from import file (for debugging) */
    private String rawData;

    /**
     * Currency code for this transaction (e.g., "USD", "EUR"). Null means the account/default
     * currency is assumed.
     */
    private String currency;

    /**
     * Source balance delta derived from the import format's account balance table, when available.
     *
     * <p>Skrooge JSON exposes cumulative operation balances; the parser derives this per-operation
     * delta so imports can preserve source rows even when Open-Finance has no historical FX rate for
     * the operation currency/account currency pair.
     */
    private BigDecimal sourceAccountBalanceDelta;

    /** Source category ID from the import format, when available. */
    private Long sourceCategoryId;

    /**
     * Indicates whether this imported transaction represents one side of a transfer between two
     * accounts.
     */
    @Builder.Default private boolean transfer = false;

    /** Stable key linking both sides of an imported transfer. */
    private String transferGroupKey;

    /** Destination account name for imported transfer transactions. */
    private String toAccountName;

    /** Destination account source ID for imported transfer transactions. */
    private Long toAccountSourceId;

    /**
     * Tags associated with this transaction (e.g., from CSV "tags" column or QIF category classes).
     */
    @Builder.Default private List<String> tags = new ArrayList<>();

    /**
     * Raw payment method string from the import file (e.g., "Débit", "Crédit", "Virement").
     *
     * <p>Mapped to a {@link org.openfinance.entity.PaymentMethod} during import. Null when the
     * source file does not carry payment-mode information.
     */
    private String paymentMethod;

    /**
     * Institution (bank) name associated with the account, as read from the import file (e.g.,
     * Skrooge CSV "bank" column). Used to link the account to an institution during import.
     */
    private String institutionName;

    /**
     * Indicates whether this row represents an account opening balance rather than a regular
     * transaction.
     *
     * <p>Skrooge encodes opening balances with the synthetic date {@code 0000-00-00}. When this
     * flag is true, the transaction should be used to set the account's opening balance instead of
     * being persisted as a regular transaction.
     */
    @Builder.Default private boolean openingBalance = false;

    /**
     * Source operation ID grouping suboperations into a single logical operation (e.g., Skrooge CSV
     * "idtransaction" column). Used by the CSV parser to merge split suboperations before returning
     * the final transaction list. Not persisted.
     */
    @JsonIgnore private String sourceOperationId;

    /**
     * Prefixes that mark informational / advisory messages rather than blocking validation errors.
     * Messages with these prefixes are surfaced in the UI review step for transparency but must NOT
     * prevent a transaction from being imported via the {@link #hasErrors()} check.
     *
     * <ul>
     *   <li>{@code AUTO-MATCH:} — auto-categorisation hit from transaction history
     *   <li>{@code CATEGORY_SUGGESTION:} — fuzzy-matched category suggestion
     *   <li>{@code CATEGORY_UNKNOWN:} — category from file not found in user's list
     *   <li>{@code DUPLICATE:} — possible duplicate; handled separately by {@link #isDuplicate()}
     *       and the {@code skipDuplicates} flag
     * </ul>
     */
    private static final List<String> INFO_PREFIXES =
            List.of(
                    "AUTO-MATCH:",
                    "AI_MATCH:",
                    "CATEGORY_SUGGESTION:",
                    "CATEGORY_UNKNOWN:",
                    "DUPLICATE:",
                    "RULE_MATCH:" // Requirement: REQ-TR-4.6 — informational rule-match annotation
                    // (non-blocking)
                    // Note: "RULE_SKIP:" is intentionally NOT listed here — it IS blocking
                    // (REQ-TR-4.7)
                    );

    /** Add a validation error (or an informational annotation). */
    public void addValidationError(String error) {
        this.validationErrors.add(error);
    }

    /**
     * Check if transaction has <em>blocking</em> validation errors.
     *
     * <p>Informational annotations written by the auto-categorisation and duplicate-detection
     * helpers (prefixed with {@code AUTO-MATCH:}, {@code CATEGORY_SUGGESTION:}, {@code
     * CATEGORY_UNKNOWN:}) are stored in the same list for UI display purposes but are
     * <strong>not</strong> considered errors for import filtering. Only entries that do not start
     * with a known informational prefix are treated as real errors.
     */
    public boolean hasErrors() {
        return validationErrors.stream()
                .anyMatch(e -> INFO_PREFIXES.stream().noneMatch(e::startsWith));
    }

    /** Check if transaction is a split transaction */
    @JsonIgnore
    public boolean isSplitTransaction() {
        return splits != null && !splits.isEmpty();
    }

    /** Represents a split entry in a split transaction */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SplitEntry {
        /** Category for this split */
        private String category;

        /** Memo for this split */
        private String memo;

        /** Source category ID from the import format, when available. */
        private Long sourceCategoryId;

        /** Amount for this split */
        private BigDecimal amount;
    }
}
