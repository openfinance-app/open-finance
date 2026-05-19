package org.openfinance.dto;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.openfinance.entity.RecurringFrequency;
import org.openfinance.entity.TransactionType;
import org.openfinance.validation.ValidCurrency;

/**
 * Data Transfer Object for creating or updating a recurring transaction.
 *
 * <p>This DTO is used for both POST (create) and PUT (update) operations. Validation annotations
 * ensure data integrity before processing.
 *
 * <p>A recurring transaction is a template that automatically generates actual transactions at
 * regular intervals.
 *
 * <p>Requirement REQ-2.3.6: Recurring transactions with configurable frequency
 *
 * <p>Requirement REQ-2.3.6.1: Support for multiple frequency types
 *
 * <p>Requirement REQ-2.3.6.2: Optional end date for recurring transactions
 *
 * @see org.openfinance.entity.RecurringTransaction
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RecurringTransactionRequest {

    /**
     * ID of the account this recurring transaction belongs to.
     *
     * <p>For TRANSFER transactions, this is the source account (money leaving).
     *
     * <p>Requirement REQ-2.3.6: Recurring transaction must be associated with an account
     */
    @NotNull(message = "{recurring.account.required}")
    private Long accountId;

    /**
     * ID of the destination account (only for TRANSFER type).
     *
     * <p>For INCOME and EXPENSE transactions, this should be null.
     *
     * <p>Requirement REQ-2.3.6: Support recurring transfers between accounts
     */
    private Long toAccountId;

    /**
     * Type of transaction: INCOME, EXPENSE, or TRANSFER.
     *
     * <p>Requirement REQ-2.3.6: Classify recurring transactions by type
     */
    @NotNull(message = "{recurring.type.required}")
    private TransactionType type;

    /**
     * Amount of the recurring transaction.
     *
     * <p>Must be positive. For expenses, the service layer will handle the sign.
     *
     * <p>Requirement REQ-2.3.6: Record recurring transaction amount
     */
    @NotNull(message = "{recurring.amount.required}")
    @DecimalMin(value = "0.01", message = "{recurring.amount.min}")
    @Digits(integer = 15, fraction = 2, message = "{recurring.amount.digits}")
    private BigDecimal amount;

    /**
     * Currency code in ISO 4217 format (e.g., "USD", "EUR", "GBP").
     *
     * <p>Should match the account's currency for consistency.
     *
     * <p>Requirement REQ-2.8: Multi-currency support
     */
    @NotBlank(message = "{account.currency.required}")
    @ValidCurrency
    private String currency;

    /**
     * ID of the category this recurring transaction belongs to (optional).
     *
     * <p>Category must match the transaction type (INCOME category for INCOME transaction).
     *
     * <p>For TRANSFER transactions, category should be null.
     *
     * <p>Requirement REQ-2.3.6: Categorize recurring transactions
     */
    private Long categoryId;

    /**
     * Brief description of the recurring transaction (optional, encrypted).
     *
     * <p>Examples: "Monthly rent payment", "Weekly grocery budget", "Annual insurance premium"
     *
     * <p>Requirement REQ-2.3.6: Add recurring transaction descriptions
     */
    @NotBlank(message = "{recurring.description.required}")
    @Size(max = 1000, message = "{recurring.description.max}")
    private String description;

    /**
     * Additional notes about the recurring transaction (optional, encrypted).
     *
     * <p>More detailed information than description. For user's reference.
     *
     * <p>Requirement REQ-2.3.6: Add detailed notes
     */
    @Size(max = 2000, message = "{recurring.notes.max}")
    private String notes;

    /**
     * Frequency at which the transaction should recur.
     *
     * <p>Supported frequencies: DAILY, WEEKLY, BIWEEKLY, MONTHLY, QUARTERLY, YEARLY
     *
     * <p>Requirement REQ-2.3.6.1: Support for multiple frequency types
     */
    @NotNull(message = "{recurring.frequency.required}")
    private RecurringFrequency frequency;

    /**
     * Date when the next transaction should be generated.
     *
     * <p>For new recurring transactions, this is typically the date of the first occurrence. When
     * the scheduled job processes the recurring transaction, this field is automatically updated to
     * the next date based on the frequency.
     *
     * <p>Can be in the future to schedule a recurring transaction to start later.
     *
     * <p>Requirement REQ-2.3.6: Schedule next occurrence date
     */
    @NotNull(message = "{recurring.next.occurrence.required}")
    private LocalDate nextOccurrence;

    /**
     * Optional end date after which the recurring transaction should stop.
     *
     * <p>If null, the recurring transaction continues indefinitely until manually stopped by the
     * user (setting isActive=false) or deleted.
     *
     * <p>When the scheduled job processes a recurring transaction and calculates the next
     * occurrence date that would be after the end date, the recurring transaction is automatically
     * set to inactive (isActive=false).
     *
     * <p>Must be after nextOccurrence date.
     *
     * <p>Requirement REQ-2.3.6.2: Optional end date for recurring transactions
     */
    @Future(message = "{recurring.end.date.past}")
    private LocalDate endDate;

    /**
     * Validates that the request is internally consistent.
     *
     * <p>Called by Jakarta Bean Validation framework during request validation.
     */
    @AssertTrue(message = "{recurring.end.date.after}")
    private boolean isEndDateValid() {
        if (endDate == null) {
            return true; // End date is optional
        }
        return nextOccurrence == null || endDate.isAfter(nextOccurrence);
    }

    /** Validates that TRANSFER transactions have a destination account. */
    @AssertTrue(message = "{recurring.transfer.destination}")
    private boolean isTransferValid() {
        if (type == TransactionType.TRANSFER) {
            return toAccountId != null;
        }
        return true;
    }
}
