package org.openfinance.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.*;

/**
 * JPA entity representing a recurring financial transaction template.
 *
 * <p>A recurring transaction is a template that automatically generates actual transactions at
 * regular intervals (daily, weekly, monthly, etc.). This is useful for modeling regular income
 * (salary) or expenses (rent, subscriptions, bills).
 *
 * <p><strong>Lifecycle:</strong>
 *
 * <ul>
 *   <li>Created with a frequency (DAILY, WEEKLY, MONTHLY, etc.)
 *   <li>Scheduled job runs daily to check for due recurring transactions
 *   <li>When due, creates an actual Transaction and updates nextOccurrence
 *   <li>Can be paused (isActive=false) or ended (endDate reached)
 * </ul>
 *
 * <p><strong>Security:</strong>
 *
 * <ul>
 *   <li>Description and notes fields are encrypted for privacy
 *   <li>All recurring transactions are user-specific and access-controlled
 * </ul>
 *
 * <p><strong>Requirements:</strong>
 *
 * <ul>
 *   <li>REQ-2.3.6: Recurring transactions with configurable frequency
 *   <li>REQ-2.3.6.1: Support for DAILY, WEEKLY, MONTHLY, YEARLY frequencies
 *   <li>REQ-2.3.6.2: Optional end date for recurring transactions
 *   <li>REQ-2.3.6.3: Ability to pause/resume recurring transactions
 * </ul>
 *
 * @see RecurringFrequency
 * @see Transaction
 * @see Account
 * @see Category
 * @since 1.0
 */
@Entity
@Table(
        name = "recurring_transactions",
        indexes = {
            @Index(name = "idx_recurring_user_id", columnList = "user_id"),
            @Index(name = "idx_recurring_account_id", columnList = "account_id"),
            @Index(name = "idx_recurring_next_occurrence", columnList = "next_occurrence"),
            @Index(name = "idx_recurring_is_active", columnList = "is_active")
        })
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@ToString(onlyExplicitlyIncluded = true)
public class RecurringTransaction {

    /** Primary key - unique identifier for the recurring transaction. */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    @ToString.Include
    private Long id;

    /**
     * ID of the user who owns this recurring transaction.
     *
     * <p>Foreign key reference to the users table. Required for access control and multi-user
     * isolation.
     *
     * <p>Requirement REQ-3.2: User-specific data isolation
     */
    @NotNull(message = "User ID is required")
    @Column(name = "user_id", nullable = false)
    private Long userId;

    /**
     * ID of the account associated with this recurring transaction.
     *
     * <p>Foreign key reference to the accounts table. For TRANSFER type transactions, this
     * represents the source account (money leaves this account).
     */
    @NotNull(message = "Account ID is required")
    @Column(name = "account_id", nullable = false)
    private Long accountId;

    /**
     * ID of the destination account for TRANSFER type transactions.
     *
     * <p>Only populated when type = TRANSFER. Money is transferred FROM accountId TO toAccountId.
     */
    @Column(name = "to_account_id")
    private Long toAccountId;

    /**
     * Type of transaction: INCOME, EXPENSE, or TRANSFER.
     *
     * <p>Determines the financial impact:
     *
     * <ul>
     *   <li>INCOME: Increases account balance
     *   <li>EXPENSE: Decreases account balance
     *   <li>TRANSFER: Moves money between accounts (requires toAccountId)
     * </ul>
     */
    @NotNull(message = "Transaction type is required")
    @Enumerated(EnumType.STRING)
    @Column(name = "transaction_type", nullable = false, length = 20)
    @ToString.Include
    private TransactionType type;

    /**
     * Transaction amount in the specified currency.
     *
     * <p>Always positive. The transaction type determines debit/credit behavior.
     */
    @NotNull(message = "Amount is required")
    @DecimalMin(value = "0.01", message = "Amount must be greater than 0")
    @Digits(integer = 15, fraction = 2, message = "Amount must have at most 2 decimal places")
    @Column(nullable = false, precision = 17, scale = 2)
    @ToString.Include
    private BigDecimal amount;

    /**
     * ISO 4217 currency code (e.g., USD, EUR, GBP).
     *
     * <p>Three-letter uppercase code. Defaults to user's base currency if not specified.
     */
    @NotNull(message = "Currency is required")
    @Size(min = 3, max = 3, message = "Currency code must be exactly 3 characters")
    @Column(nullable = false, length = 3)
    private String currency;

    /** FK to the currencies table for referential integrity. */
    @Column(name = "currency_id")
    private Long currencyId;

    /** Reference to the currency entity (lazy-loaded). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "currency_id", insertable = false, updatable = false)
    private Currency currencyEntity;

    /**
     * ID of the category associated with this recurring transaction.
     *
     * <p>Foreign key reference to the categories table. Helps organize transactions for budgeting
     * and reporting.
     *
     * <p>Not required for TRANSFER transactions.
     */
    @Column(name = "category_id")
    private Long categoryId;

    /**
     * Brief description of the recurring transaction (ENCRYPTED).
     *
     * <p>Stored encrypted in the database for privacy. Examples: "Monthly Rent", "Weekly Grocery
     * Budget", "Biweekly Paycheck".
     *
     * <p><strong>Security:</strong> Encrypted before persistence, decrypted after retrieval
     *
     * <p><strong>Validation Note:</strong> Validation is performed at the DTO layer
     * (RecurringTransactionRequest) on the plaintext value BEFORE encryption. Do NOT add @NotBlank
     * or @Size here as they would validate the encrypted Base64 string, which has unpredictable
     * length and may cause decryption failures.
     *
     * @see org.openfinance.security.EncryptionService
     */
    @Column(nullable = false, length = 1500) // Extra space for encryption overhead
    @ToString.Include
    private String description;

    /**
     * Additional notes or details about the recurring transaction (ENCRYPTED).
     *
     * <p>Stored encrypted in the database for privacy. Use for longer explanations, reminders, or
     * context.
     *
     * <p><strong>Security:</strong> Encrypted before persistence, decrypted after retrieval
     *
     * <p><strong>Validation Note:</strong> Validation is performed at the DTO layer on the
     * plaintext value BEFORE encryption. Do NOT add @Size here as it would validate the encrypted
     * Base64 string.
     *
     * @see org.openfinance.security.EncryptionService
     */
    @Column(columnDefinition = "TEXT")
    private String notes;

    /**
     * Frequency at which the transaction recurs.
     *
     * <p>Options: DAILY, WEEKLY, BIWEEKLY, MONTHLY, QUARTERLY, YEARLY
     *
     * <p>Determines how nextOccurrence is calculated after each transaction.
     */
    @NotNull(message = "Frequency is required")
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @ToString.Include
    private RecurringFrequency frequency;

    /**
     * Date when the next transaction should be created.
     *
     * <p>The scheduled job checks this date daily. When current date >= nextOccurrence, a new
     * transaction is created and this date is updated based on frequency.
     */
    @NotNull(message = "Next occurrence date is required")
    @Column(name = "next_occurrence", nullable = false)
    @ToString.Include
    private LocalDate nextOccurrence;

    /**
     * Optional end date for the recurring transaction.
     *
     * <p>When current date > endDate, no more transactions are created and isActive is set to
     * false. Leave null for indefinite recurrence.
     */
    @Column(name = "end_date")
    private LocalDate endDate;

    /**
     * Indicates whether the recurring transaction is currently active.
     *
     * <p>When false, the scheduled job skips this recurring transaction. Users can pause/resume
     * transactions by toggling this flag.
     *
     * <p>Default: true
     */
    @NotNull(message = "Active status is required")
    @Column(name = "is_active", nullable = false)
    @ToString.Include
    private Boolean isActive;

    /**
     * Timestamp when this recurring transaction was created.
     *
     * <p>Automatically set by Hibernate on persist.
     */
    @Column(name = "created_at", nullable = false, updatable = false)
    @org.hibernate.annotations.CreationTimestamp
    private LocalDateTime createdAt;

    /**
     * Timestamp when this recurring transaction was last updated.
     *
     * <p>Automatically set by Hibernate on update.
     */
    @Column(name = "updated_at", nullable = false)
    @org.hibernate.annotations.UpdateTimestamp
    private LocalDateTime updatedAt;

    /**
     * Checks if the recurring transaction is due to generate a new transaction.
     *
     * <p>Returns true if:
     *
     * <ul>
     *   <li>isActive is true
     *   <li>current date >= nextOccurrence
     *   <li>endDate is null OR current date <= endDate
     * </ul>
     *
     * @return true if a transaction should be generated, false otherwise
     */
    public boolean isDue() {
        if (!isActive) {
            return false;
        }

        LocalDate today = LocalDate.now();

        if (today.isBefore(nextOccurrence)) {
            return false;
        }

        if (endDate != null && today.isAfter(endDate)) {
            return false;
        }

        return true;
    }

    /**
     * Checks if the recurring transaction has ended.
     *
     * <p>Returns true if endDate is set and current date > endDate.
     *
     * @return true if ended, false otherwise
     */
    public boolean isEnded() {
        return endDate != null && LocalDate.now().isAfter(endDate);
    }

    /**
     * Calculates the next occurrence date based on current nextOccurrence and frequency.
     *
     * <p>This method is called after creating a transaction to schedule the next one.
     *
     * @return the new next occurrence date
     */
    public LocalDate calculateNextOccurrence() {
        return switch (frequency) {
            case DAILY -> nextOccurrence.plusDays(1);
            case WEEKLY -> nextOccurrence.plusWeeks(1);
            case BIWEEKLY -> nextOccurrence.plusWeeks(2);
            case MONTHLY -> nextOccurrence.plusMonths(1);
            case QUARTERLY -> nextOccurrence.plusMonths(3);
            case YEARLY -> nextOccurrence.plusYears(1);
        };
    }

    /**
     * Checks if this is a transfer transaction.
     *
     * @return true if type is TRANSFER, false otherwise
     */
    public boolean isTransfer() {
        return type == TransactionType.TRANSFER;
    }
}
