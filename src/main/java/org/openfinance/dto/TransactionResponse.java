package org.openfinance.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.openfinance.entity.PaymentMethod;
import org.openfinance.entity.TransactionType;

/**
 * Data Transfer Object for transaction responses.
 *
 * <p>This DTO is returned to clients when retrieving transaction information. It contains decrypted
 * values and includes denormalized account and category details to avoid additional lookups on the
 * client side.
 *
 * <p>Requirement REQ-2.3.1: Users can view their transaction information
 *
 * @see org.openfinance.entity.Transaction
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransactionResponse {

    /** Unique identifier of the transaction. */
    private Long id;

    /** ID of the account this transaction belongs to. */
    private Long accountId;

    /**
     * Name of the account (decrypted, denormalized for convenience).
     *
     * <p>Avoids additional API calls to fetch account details.
     */
    private String accountName;

    /** ID of the destination account (only for TRANSFER transactions). */
    private Long toAccountId;

    /** Name of the destination account (decrypted, null for non-transfer transactions). */
    private String toAccountName;

    /**
     * Type of transaction: INCOME, EXPENSE, or TRANSFER.
     *
     * <p>Requirement REQ-2.3.1: Display transaction type
     */
    private TransactionType type;

    /**
     * Amount of the transaction.
     *
     * <p>Positive for INCOME and TRANSFER. May be negative for EXPENSE depending on implementation.
     *
     * <p>Requirement REQ-2.3.1: Display transaction amount
     */
    private BigDecimal amount;

    /**
     * Currency code in ISO 4217 format (e.g., "USD", "EUR", "GBP").
     *
     * <p>Requirement REQ-2.8: Multi-currency support
     */
    private String currency;

    /** ID of the category (null for transfers or uncategorized transactions). */
    private Long categoryId;

    /** Name of the category (decrypted, denormalized for convenience). */
    private String categoryName;

    /** Icon of the category (for UI display). */
    private String categoryIcon;

    /** Color of the category (for UI display). */
    private String categoryColor;

    /**
     * Date when the transaction occurred.
     *
     * <p>Requirement REQ-2.3.1: Display transaction date
     */
    private LocalDate date;

    /**
     * Brief description of the transaction (decrypted).
     *
     * <p>Requirement REQ-2.3.1: Display transaction description
     */
    private String description;

    /**
     * Additional notes about the transaction (decrypted).
     *
     * <p>Requirement REQ-2.3.1: Display detailed notes
     */
    private String notes;

    /**
     * Comma-separated tags for organization.
     *
     * <p>Requirement REQ-2.3.5: Display transaction tags
     */
    private String tags;

    /**
     * Payee name (person or merchant).
     *
     * <p>Requirement REQ-2.3.1: Display payee information
     */
    private String payee;

    /**
     * Payment method used for this transaction.
     *
     * <p>How the transaction was paid: cash, cheque, credit card, debit card, bank transfer,
     * deposit, standing order, direct debit, online, or other.
     *
     * <p>Requirement REQ-2.3.1: Display payment method
     */
    private PaymentMethod paymentMethod;

    /**
     * Transfer ID linking two transactions in a transfer operation.
     *
     * <p>When viewing a TRANSFER transaction, this ID links the source and destination
     * transactions. Both transactions in the pair share the same transferId.
     *
     * <p>Requirement REQ-2.4.1.4: Display transfer relationship
     */
    private String transferId;

    /**
     * Optional ID of the liability this transaction is a payment for.
     *
     * <p>When set, this transaction is linked to a loan, mortgage, or other liability, allowing
     * tracking of loan payments against the liability balance.
     *
     * <p>Requirement REQ-LIA-4: Transaction-liability linking
     */
    private Long liabilityId;

    /**
     * Flag indicating whether the transaction has been reconciled.
     *
     * <p>Requirement REQ-2.3.3: Display reconciliation status
     */
    private Boolean isReconciled;

    /**
     * Flag indicating whether the transaction has been soft-deleted.
     *
     * <p>Soft-deleted transactions are excluded from normal queries but can be restored.
     *
     * <p>Requirement REQ-2.3.2: Indicate deleted transactions
     */
    private Boolean isDeleted;

    /** Timestamp when the transaction was created. */
    private LocalDateTime createdAt;

    /** Timestamp when the transaction was last updated. */
    private LocalDateTime updatedAt;

    /**
     * Flag indicating whether this transaction has split lines.
     *
     * <p>When {@code true}, the {@code splits} list contains the individual category allocations.
     * The parent-level {@code categoryId} is ignored for categorization purposes when splits are
     * present.
     *
     * <p>Requirement REQ-SPL-2.4: hasSplits flag in list/search responses
     */
    private Boolean hasSplits;

    /**
     * List of split lines for this transaction (may be empty or null).
     *
     * <p>Populated when {@code hasSplits} is {@code true}. Each entry contains the category
     * details, amount, and optional description for that split.
     *
     * <p>Requirement REQ-SPL-2.3: Include splits in GET single transaction
     *
     * <p>Requirement REQ-SPL-2.4: Include splits in list/search responses
     */
    private List<TransactionSplitResponse> splits;

    // === Currency Conversion Fields (Requirement REQ-9.1) ===

    /**
     * Transaction amount converted to the user's base currency.
     *
     * <p>Populated only when the transaction currency differs from the user's base currency and a
     * valid exchange rate is available.
     *
     * <p>Requirement REQ-9.1: Conversion metadata for base-currency display
     */
    private BigDecimal amountInBaseCurrency;

    /**
     * The user's base currency (ISO 4217) at the time this response was built.
     *
     * <p>Requirement REQ-9.1: Base currency reference
     */
    private String baseCurrency;

    /**
     * Exchange rate used to convert {@code amount} to {@code amountInBaseCurrency}.
     *
     * <p>Represents the rate: 1 unit of {@code currency} = {@code exchangeRate} units of {@code
     * baseCurrency}. Null when no conversion was performed.
     *
     * <p>Requirement REQ-9.1: Exchange rate used for conversion
     */
    private BigDecimal exchangeRate;

    /**
     * Whether the amount has been converted from a foreign currency to the base currency.
     *
     * <p>{@code true} only when {@code currency != baseCurrency} AND conversion succeeded. {@code
     * false} when currencies match or conversion failed (fallback to native).
     *
     * <p>Requirement REQ-9.1: isConverted flag semantics
     */
    private Boolean isConverted;
}
