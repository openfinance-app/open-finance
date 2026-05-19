package org.openfinance.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.openfinance.entity.RecurringFrequency;
import org.openfinance.entity.TransactionType;

/**
 * Data Transfer Object for recurring transaction responses.
 *
 * <p>This DTO is returned to clients when retrieving recurring transaction information. It contains
 * decrypted values and includes denormalized account and category details to avoid additional
 * lookups on the client side.
 *
 * <p>Also includes computed fields for UI convenience:
 *
 * <ul>
 *   <li>isDue - Whether the recurring transaction should generate a transaction now
 *   <li>daysUntilNext - Number of days until the next occurrence
 *   <li>isEnded - Whether the recurring transaction has reached its end date
 * </ul>
 *
 * <p>Requirement REQ-2.3.6: Users can view their recurring transaction information
 *
 * @see org.openfinance.entity.RecurringTransaction
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RecurringTransactionResponse {

    /** Unique identifier of the recurring transaction. */
    private Long id;

    /** ID of the account this recurring transaction belongs to. */
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
     * <p>Requirement REQ-2.3.6: Display recurring transaction type
     */
    private TransactionType type;

    /**
     * Amount of the recurring transaction.
     *
     * <p>Always positive. The transaction type determines the effect on account balance.
     *
     * <p>Requirement REQ-2.3.6: Display recurring transaction amount
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
     * Brief description of the recurring transaction (decrypted).
     *
     * <p>Examples: "Monthly rent payment", "Weekly grocery budget"
     *
     * <p>Requirement REQ-2.3.6: Display recurring transaction description
     */
    private String description;

    /**
     * Additional notes about the recurring transaction (decrypted).
     *
     * <p>Requirement REQ-2.3.6: Display detailed notes
     */
    private String notes;

    /**
     * Frequency at which the transaction recurs.
     *
     * <p>One of: DAILY, WEEKLY, BIWEEKLY, MONTHLY, QUARTERLY, YEARLY
     *
     * <p>Requirement REQ-2.3.6.1: Display frequency type
     */
    private RecurringFrequency frequency;

    /**
     * Human-readable frequency name (e.g., "Monthly", "Weekly").
     *
     * <p>Denormalized for UI convenience.
     */
    private String frequencyDisplayName;

    /**
     * Date when the next transaction will be generated.
     *
     * <p>This is automatically updated by the scheduled job after each transaction is created.
     *
     * <p>Requirement REQ-2.3.6: Display next occurrence date
     */
    private LocalDate nextOccurrence;

    /**
     * Optional end date after which the recurring transaction stops.
     *
     * <p>Null means the recurring transaction continues indefinitely.
     *
     * <p>Requirement REQ-2.3.6.2: Display end date
     */
    private LocalDate endDate;

    /**
     * Flag indicating whether the recurring transaction is active.
     *
     * <p>False means the recurring transaction is paused and will not generate transactions.
     *
     * <p>Requirement REQ-2.3.6.3: Display active/paused status
     */
    private Boolean isActive;

    /** Timestamp when the recurring transaction was created. */
    private LocalDateTime createdAt;

    /** Timestamp when the recurring transaction was last updated. */
    private LocalDateTime updatedAt;

    // ===== Computed Fields (for UI convenience) =====

    /**
     * Whether the recurring transaction is due to generate a transaction.
     *
     * <p>Computed field based on:
     *
     * <ul>
     *   <li>isActive = true
     *   <li>nextOccurrence <= today
     *   <li>endDate is null OR endDate >= today
     * </ul>
     *
     * <p>This is the same logic used by the scheduled job to determine which recurring transactions
     * to process.
     */
    private Boolean isDue;

    /**
     * Number of days until the next occurrence.
     *
     * <p>Negative number means the recurring transaction is overdue.
     *
     * <p>Zero means due today.
     *
     * <p>Positive number means days remaining until due.
     *
     * <p>Computed field for UI display (e.g., "Due in 5 days", "Overdue by 2 days").
     */
    private Long daysUntilNext;

    /**
     * Whether the recurring transaction has ended.
     *
     * <p>True if endDate is not null and is in the past.
     *
     * <p>Computed field for UI display.
     */
    private Boolean isEnded;

    /**
     * Computes the isDue field based on current date and entity state.
     *
     * @return true if the recurring transaction should generate a transaction now
     */
    public Boolean computeIsDue() {
        if (isActive == null || !isActive) {
            return false;
        }
        if (nextOccurrence == null) {
            return false;
        }
        LocalDate today = LocalDate.now();
        if (nextOccurrence.isAfter(today)) {
            return false;
        }
        if (endDate != null && endDate.isBefore(today)) {
            return false;
        }
        return true;
    }

    /**
     * Computes the daysUntilNext field based on current date.
     *
     * @return number of days until next occurrence (negative if overdue)
     */
    public Long computeDaysUntilNext() {
        if (nextOccurrence == null) {
            return null;
        }
        return ChronoUnit.DAYS.between(LocalDate.now(), nextOccurrence);
    }

    /**
     * Computes the isEnded field based on current date.
     *
     * @return true if the recurring transaction has reached its end date
     */
    public Boolean computeIsEnded() {
        if (endDate == null) {
            return false;
        }
        return endDate.isBefore(LocalDate.now());
    }
}
