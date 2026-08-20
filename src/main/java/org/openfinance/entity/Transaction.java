package org.openfinance.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.*;
import org.openfinance.converter.EncryptedBigDecimalConverter;
import org.openfinance.converter.EncryptedStringConverter;

/**
 * JPA entity representing a financial transaction.
 *
 * <p>
 * A transaction records a financial movement - income received, expenses paid,
 * or transfers
 * between accounts. Transactions are the core building blocks for tracking
 * financial activity,
 * calculating balances, and generating reports.
 *
 * <p>
 * <strong>Transaction Types:</strong>
 *
 * <ul>
 * <li><strong>INCOME</strong>: Money received (salary, dividends, gifts)
 * <li><strong>EXPENSE</strong>: Money spent (groceries, rent, utilities)
 * <li><strong>TRANSFER</strong>: Money moved between accounts (uses both
 * accountId and
 * toAccountId)
 * </ul>
 *
 * <p>
 * <strong>Security:</strong>
 *
 * <ul>
 * <li>Description and notes fields are encrypted for privacy
 * <li>All transactions are user-specific and access-controlled
 * </ul>
 *
 * <p>
 * <strong>Requirements:</strong>
 *
 * <ul>
 * <li>REQ-2.4.1.1: Transaction creation with date, amount, currency, account,
 * category
 * <li>REQ-2.4.1.2: Transaction modification with balance recalculation
 * <li>REQ-2.4.1.3: Transaction deletion (soft delete)
 * </ul>
 *
 * @see TransactionType
 * @see Account
 * @see Category
 * @since 1.0
 */
@Entity
@Table(name = "transactions", indexes = {
        @Index(name = "idx_transaction_user_id", columnList = "user_id"),
        @Index(name = "idx_transaction_account_id", columnList = "account_id"),
        @Index(name = "idx_transaction_category_id", columnList = "category_id"),
        @Index(name = "idx_transaction_date", columnList = "transaction_date"),
        @Index(name = "idx_transaction_type", columnList = "transaction_type")
})
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class Transaction {

    /** Primary key - unique identifier for the transaction. */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Long id;

    /**
     * ID of the user who owns this transaction.
     *
     * <p>
     * Foreign key reference to the users table. Required for access control and
     * multi-user
     * isolation.
     *
     * <p>
     * Requirement REQ-3.2: User-specific data isolation
     */
    @NotNull(message = "{transaction.userId.notnull}")
    @Column(name = "user_id", nullable = false)
    private Long userId;

    /**
     * ID of the account associated with this transaction.
     *
     * <p>
     * For INCOME and EXPENSE: The account receiving/spending money
     *
     * <p>
     * For TRANSFER: The source account (money leaving)
     *
     * <p>
     * Foreign key reference to the accounts table.
     *
     * <p>
     * Requirement REQ-2.4.1.1: Account association
     */
    @NotNull(message = "{transaction.accountId.notnull}")
    @Column(name = "account_id", nullable = false)
    private Long accountId;

    /** Reference to the account entity (lazy-loaded). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "account_id", insertable = false, updatable = false)
    private Account account;

    /**
     * ID of the destination account for TRANSFER transactions (nullable for
     * INCOME/EXPENSE).
     *
     * <p>
     * Only used when transaction type is TRANSFER. Represents the account receiving
     * the money.
     *
     * <p>
     * Foreign key reference to the accounts table.
     *
     * <p>
     * Requirement REQ-2.4.1.1: Transfer support
     */
    @Column(name = "to_account_id")
    private Long toAccountId;

    /** Reference to the destination account for transfers (lazy-loaded). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "to_account_id", insertable = false, updatable = false)
    private Account toAccount;

    /**
     * Type of transaction: INCOME, EXPENSE, or TRANSFER.
     *
     * <p>
     * Requirement REQ-2.4.1.1: Transaction types
     */
    @NotNull(message = "{transaction.type.notnull}")
    @Enumerated(EnumType.STRING)
    @Column(name = "transaction_type", nullable = false, length = 20)
    private TransactionType type;

    /**
     * Transaction amount.
     *
     * <p>
     * Must be positive. The transaction type determines whether this increases or
     * decreases the
     * account balance:
     *
     * <ul>
     * <li>INCOME: Increases account balance
     * <li>EXPENSE: Decreases account balance
     * <li>TRANSFER: Decreases source account, increases destination account
     * </ul>
     *
     * <p>
     * Stored as BigDecimal for precision (up to 19 digits, 4 decimal places).
     *
     * <p>
     * Requirement REQ-2.4.1.1: Amount validation (non-zero)
     */
    @NotNull(message = "{transaction.amount.notnull}")
    @DecimalMin(value = "0.0001", message = "{transaction.amount.decimalMin}")
    @Digits(integer = 26, fraction = 18, message = "{transaction.amount.digits}")
    @Column(name = "amount", nullable = false, length = 512)
    @Convert(converter = EncryptedBigDecimalConverter.class)
    private BigDecimal amount;

    /**
     * Currency code in ISO 4217 format (e.g., "USD", "EUR", "GBP").
     *
     * <p>
     * Must match the account's currency for INCOME/EXPENSE transactions. For
     * TRANSFER
     * transactions, conversion rates may apply if currencies differ.
     *
     * <p>
     * Requirement REQ-2.8: Multi-currency support
     */
    @NotBlank(message = "{transaction.currency.notblank}")
    @Size(min = 3, max = 3, message = "{transaction.currency.size}")
    @Column(name = "currency", nullable = false, length = 3)
    private String currency;

    /**
     * ID of the currency entity linked to this transaction (nullable).
     *
     * <p>
     * Foreign key reference to the currencies table. Provides referential integrity
     * for the
     * currency code.
     */
    @Column(name = "currency_id")
    private Long currencyId;

    /** Reference to the currency entity (lazy-loaded). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "currency_id", insertable = false, updatable = false)
    private Currency currencyEntity;

    /**
     * Original (pre-conversion) amount the user entered, expressed in
     * {@link #originalCurrency}.
     * Null unless a currency conversion was applied at save time (entered currency
     * != account
     * currency). Encrypted at rest like {@link #amount}.
     * {@code originalAmount * conversionRate ≈
     * amount}.
     */
    @Digits(integer = 26, fraction = 18, message = "{transaction.amount.digits}")
    @Column(name = "original_amount", length = 512)
    @Convert(converter = EncryptedBigDecimalConverter.class)
    private BigDecimal originalAmount;

    /**
     * ISO 4217 currency the user originally entered (e.g. "USD"). Null unless a
     * conversion was
     * applied; when set it differs from {@link #currency} (the account currency).
     */
    @Size(min = 3, max = 3, message = "{transaction.currency.size}")
    @Column(name = "original_currency", length = 3)
    private String originalCurrency;

    /**
     * Exchange rate applied at save time:
     * {@code 1 originalCurrency = conversionRate * currency}.
     * Plaintext (a market rate, like {@code exchange_rates.rate}). Null unless
     * converted.
     */
    @Column(name = "conversion_rate", precision = 18, scale = 8)
    private BigDecimal conversionRate;

    /**
     * ID of the category this transaction belongs to (nullable).
     *
     * <p>
     * Categories organize transactions for budgeting and reporting. Can be null for
     * uncategorized transactions.
     *
     * <p>
     * Foreign key reference to the categories table.
     *
     * <p>
     * Requirement REQ-2.4.1.1: Category association
     */
    @Column(name = "category_id")
    private Long categoryId;

    /** Reference to the category entity (lazy-loaded). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id", insertable = false, updatable = false)
    private Category category;

    /**
     * Date when the transaction occurred.
     *
     * <p>
     * Not the same as createdAt - this is the actual transaction date (e.g.,
     * purchase date,
     * deposit date). Can be in the past or future.
     *
     * <p>
     * Note: Future dates are allowed for financial planning purposes. For recurring
     * scheduled
     * transactions, use RecurringTransaction entity instead.
     *
     * <p>
     * Requirement REQ-2.4.1.1: Date tracking
     */
    @NotNull(message = "{transaction.date.notnull}")
    @Column(name = "transaction_date", nullable = false)
    private LocalDate date;

    /**
     * Brief description of the transaction (encrypted).
     *
     * <p>
     * Examples: "Grocery shopping", "Monthly salary", "Transfer to savings"
     *
     * <p>
     * <strong>Security:</strong> This field is encrypted before storage for
     * privacy. Column
     * length is larger than validation constraint to accommodate encryption
     * overhead.
     *
     * <p>
     * Requirement REQ-2.4.1.1: Description field
     */
    @Size(max = 255, message = "{transaction.description.size}")
    @Column(name = "description", length = 1000) // Extra space for AES-256-GCM encryption
    @Convert(converter = EncryptedStringConverter.class)
    private String description;

    /**
     * Additional notes or details about the transaction (encrypted, nullable).
     *
     * <p>
     * For longer-form notes, receipts details, etc.
     *
     * <p>
     * <strong>Security:</strong> This field is encrypted before storage for
     * privacy. Column
     * length is larger than validation constraint to accommodate encryption
     * overhead.
     */
    @Size(max = 1000, message = "{transaction.notes.size}")
    @Column(name = "notes", length = 2000) // Extra space for AES-256-GCM encryption
    @Convert(converter = EncryptedStringConverter.class)
    private String notes;

    /**
     * Comma-separated tags for flexible categorization (nullable).
     *
     * <p>
     * Examples: "business,tax-deductible", "vacation,hawaii", "gift,birthday"
     *
     * <p>
     * Requirement REQ-2.4.1.1: Tags support
     */
    @Size(max = 500, message = "{transaction.tags.size}")
    @Column(name = "tags", length = 1000)
    @Convert(converter = EncryptedStringConverter.class)
    private String tags;

    /**
     * Payee or payer name (denormalized from Payee entity for fast reads/search).
     *
     * <p>
     * Who the money was paid to (for expenses) or received from (for income). Kept
     * in sync with
     * {@link #payeeEntity} name for performance — avoids JOINs in queries, FTS
     * indexing, rule
     * matching, and reporting.
     *
     * <p>
     * Examples: "Walmart", "John Doe", "Acme Corporation"
     */
    @Size(max = 100, message = "{transaction.payee.size}")
    @Column(name = "payee", length = 512)
    @Convert(converter = EncryptedStringConverter.class)
    private String payee;

    /**
     * ID of the payee entity linked to this transaction (nullable).
     *
     * <p>
     * Foreign key reference to the payees table. When set, this provides richer
     * payee
     * information including default category, logo, etc.
     */
    @Column(name = "payee_id")
    private Long payeeId;

    /** Reference to the payee entity (lazy-loaded). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payee_id", insertable = false, updatable = false)
    private Payee payeeEntity;

    /**
     * Payment method used for this transaction (optional).
     *
     * <p>
     * How the transaction was paid for: cash, cheque, credit card, debit card, bank
     * transfer,
     * deposit, standing order, direct debit, online, or other.
     *
     * <p>
     * Examples: "CREDIT_CARD" for a purchase, "DEPOSIT" for salary
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method", length = 20)
    private PaymentMethod paymentMethod;

    /**
     * Optional link to a liability this transaction is a payment for.
     *
     * <p>
     * When set, this transaction represents a payment (typically EXPENSE type) that
     * reduces the
     * outstanding balance of a loan, mortgage, or other liability.
     *
     * <p>
     * Foreign key reference to the liabilities table. ON DELETE SET NULL means if
     * the liability
     * is deleted, this field becomes null.
     *
     * <p>
     * Requirement REQ-LIA-4: Transaction-liability linking
     */
    @Column(name = "liability_id")
    private Long liabilityId;

    /**
     * External reference ID from the original import file (nullable).
     *
     * <p>
     * Stores the financial-institution transaction ID that accompanied the
     * transaction in the
     * import file, e.g.:
     *
     * <ul>
     * <li>OFX/QFX: the {@code FITID} field (Financial Institution Transaction ID)
     * <li>QIF: the {@code N} (check/reference number) field
     * <li>CSV: any mapped reference column
     * </ul>
     *
     * <p>
     * This value is set once during import and is never changed afterwards. It is
     * used by {@code
     * ImportService.detectDuplicates()} as an authoritative signal: if an incoming
     * transaction's
     * referenceNumber matches this column for the same account, it is a definite
     * duplicate and no
     * fuzzy matching is needed.
     *
     * <p>
     * Null for manually-entered transactions and for transactions imported before
     * this column
     * was introduced (Flyway migration V49).
     *
     * <p>
     * Requirement: REQ-2.10.4 (Duplicate transaction detection — reference-based)
     */
    @Size(max = 255, message = "{transaction.externalReference.size}")
    @Column(name = "external_reference", length = 512)
    @Convert(converter = EncryptedStringConverter.class)
    private String externalReference;

    /**
     * Unique identifier linking two transactions in a transfer operation.
     *
     * <p>
     * When a TRANSFER transaction is created, two transactions are generated:
     *
     * <ul>
     * <li>Source transaction (EXPENSE from accountId)
     * <li>Destination transaction (INCOME to toAccountId)
     * </ul>
     *
     * Both transactions share the same transferId to maintain the relationship.
     *
     * <p>
     * Requirement REQ-2.4.1.4: Transfer transaction linking
     */
    @Column(name = "transfer_id", length = 36)
    private String transferId;

    /**
     * Indicates if this transaction has been reconciled with bank statements.
     *
     * <p>
     * Reconciliation helps verify that recorded transactions match actual
     * bank/account
     * statements.
     */
    @Column(name = "is_reconciled", nullable = false)
    @Builder.Default
    private Boolean isReconciled = false;

    /**
     * Soft delete flag - if true, transaction is logically deleted.
     *
     * <p>
     * Soft deletes preserve historical data and maintain referential integrity
     * while hiding the
     * transaction from normal queries.
     *
     * <p>
     * Requirement REQ-2.4.1.3: Soft delete support
     */
    @Column(name = "is_deleted", nullable = false)
    @Builder.Default
    private Boolean isDeleted = false;

    /**
     * Timestamp when the transaction was created in the system.
     *
     * <p>
     * Automatically set on entity creation.
     */
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /**
     * Timestamp when the transaction was last updated.
     *
     * <p>
     * Automatically updated on entity modification.
     *
     * <p>
     * Requirement REQ-2.4.1.2: Modification audit
     */
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    /** JPA lifecycle callback - sets createdAt timestamp before persist. */
    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    /** JPA lifecycle callback - sets updatedAt timestamp before update. */
    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    /**
     * Override toString to protect encrypted description and notes fields from
     * logs. Requirement:
     * Security - Never log encrypted fields in plain text
     */
    @Override
    public String toString() {
        return "Transaction{"
                + "id="
                + id
                + ", userId="
                + userId
                + ", accountId="
                + accountId
                + ", toAccountId="
                + toAccountId
                + ", type="
                + type
                + ", amount="
                + amount
                + ", currency='"
                + currency
                + '\''
                + ", categoryId="
                + categoryId
                + ", date="
                + date
                + ", description='[ENCRYPTED]'"
                + ", notes='[ENCRYPTED]'"
                + ", tags='"
                + tags
                + '\''
                + ", payee='"
                + payee
                + '\''
                + ", payeeId="
                + payeeId
                + ", currencyId="
                + currencyId
                + ", paymentMethod="
                + paymentMethod
                + ", externalReference='"
                + externalReference
                + '\''
                + ", transferId='"
                + transferId
                + '\''
                + ", liabilityId="
                + liabilityId
                + ", isReconciled="
                + isReconciled
                + ", isDeleted="
                + isDeleted
                + ", createdAt="
                + createdAt
                + ", updatedAt="
                + updatedAt
                + '}';
    }
}
