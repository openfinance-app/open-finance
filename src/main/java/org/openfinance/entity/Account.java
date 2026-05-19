package org.openfinance.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

/**
 * Entity representing a financial account in the Open-Finance system.
 *
 * <p>Accounts are containers for financial assets and liabilities. Each account belongs to a single
 * user and tracks balance, transactions, and metadata.
 *
 * <p>Requirement REQ-2.2: Account Management - Users can create and manage different types of
 * financial accounts (checking, savings, credit cards, investments, cash).
 *
 * <p><strong>Security Note:</strong> The {@code name} and {@code description} fields will be
 * encrypted by the AccountService before persisting to the database to protect sensitive financial
 * information. The database stores only encrypted values.
 */
@Entity
@Table(
        name = "accounts",
        indexes = {
            @Index(name = "idx_account_user_id", columnList = "user_id"),
            @Index(name = "idx_account_type", columnList = "account_type"),
            @Index(name = "idx_account_is_active", columnList = "is_active")
        })
@Data
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class Account {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * The user who owns this account. Requirement REQ-2.2.1: Each account belongs to a single user
     */
    @NotNull(message = "User ID cannot be null")
    @Column(name = "user_id", nullable = false)
    private Long userId;

    /** Relationship to the User entity. Lazy-loaded to optimize performance. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", insertable = false, updatable = false)
    @ToString.Exclude
    private User user;

    /**
     * Account number for matching during transaction import.
     *
     * <p>This field stores the official account number (e.g., checking account number, IBAN, or
     * other identifier) assigned by the financial institution. It is used to automatically match
     * imported transactions to the correct account.
     *
     * <p>Requirement: Account Number field for transaction import matching
     */
    @Column(name = "account_number", length = 512)
    private String accountNumber;

    /**
     * Name of the account (e.g., "Chase Checking", "401k Retirement").
     *
     * <p><strong>Encrypted Field:</strong> This field is stored encrypted in the database. The
     * AccountService handles encryption/decryption transparently. Requirement REQ-2.2.2: Account
     * must have a descriptive name
     */
    @NotNull(message = "Account name cannot be null")
    // Match database column length to avoid validation/DB mismatches when
    // encryption
    @Size(min = 1, max = 500, message = "Account name must be between 1 and 500 characters")
    @Column(name = "name", nullable = false, length = 500) // Extra length for encrypted data
    private String name;

    /**
     * Type of account (CHECKING, SAVINGS, CREDIT_CARD, INVESTMENT, CASH, OTHER). Requirement
     * REQ-2.2.2: Account type categorization
     */
    @NotNull(message = "Account type cannot be null")
    @Enumerated(EnumType.STRING)
    @Column(name = "account_type", nullable = false, length = 20)
    private AccountType type;

    /**
     * Optional relationship to an institution (bank, investment platform, etc.). Requirement
     * REQ-2.6.1.3: Predefined Financial Institutions
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "institution_id")
    @ToString.Exclude
    private Institution institution;

    /**
     * Currency code in ISO 4217 format (e.g., USD, EUR, GBP). Requirement REQ-2.7: Multi-currency
     * support
     */
    @NotNull(message = "Currency cannot be null")
    @Size(min = 3, max = 3, message = "Currency must be a 3-letter ISO 4217 code")
    @Column(name = "currency", nullable = false, length = 3)
    private String currency;

    /**
     * Current balance of the account.
     *
     * <p>Balance is calculated from transactions and stored for performance. Must be kept in sync
     * with transaction totals.
     *
     * <p>Stored with precision 19, scale 4 to handle large amounts and fractional currencies.
     *
     * <p>Note: Balances may be negative for liability accounts (e.g., credit cards). Business rules
     * for permitted ranges should be enforced in the service layer. Requirement REQ-2.2.5: Account
     * balance calculation
     */
    @NotNull(message = "Balance cannot be null")
    @Column(name = "balance", nullable = false, precision = 19, scale = 4)
    @Builder.Default
    private BigDecimal balance = BigDecimal.ZERO;

    /**
     * Opening balance of the account when it was created.
     *
     * <p>This is the initial balance that was set when the account was created. Used for
     * calculating balance history from the account's opening date.
     *
     * <p>Requirement REQ-2.6.1.2: Account Balance Tracking - Historical snapshots
     */
    @NotNull(message = "Opening balance cannot be null")
    @Column(name = "opening_balance", nullable = false, precision = 19, scale = 4)
    @Builder.Default
    private BigDecimal openingBalance = BigDecimal.ZERO;

    /**
     * Date when the account was opened.
     *
     * <p>Used as the starting point for balance history calculation.
     *
     * <p>Requirement REQ-2.6.1.2: Account Balance Tracking - Historical snapshots
     */
    @NotNull(message = "Opening date cannot be null")
    @Column(name = "opening_date", nullable = false)
    @Builder.Default
    private java.time.LocalDate openingDate = java.time.LocalDate.now();

    /**
     * Optional description of the account (e.g., institution details, account purpose).
     *
     * <p><strong>Encrypted Field:</strong> This field is stored encrypted in the database. The
     * AccountService handles encryption/decryption transparently.
     */
    @Column(name = "description", length = 1000) // Extra length for encrypted data
    private String description;

    /**
     * Flag indicating whether the account is active. Inactive accounts are soft-deleted (not shown
     * in UI but preserved for historical data).
     *
     * <p>Requirement REQ-2.2.4: Soft delete for accounts
     */
    @NotNull(message = "isActive flag cannot be null")
    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private Boolean isActive = true;

    /** Flag indicating whether interest calculation is enabled for this account. */
    @NotNull(message = "isInterestEnabled flag cannot be null")
    @Column(name = "is_interest_enabled", nullable = false)
    @Builder.Default
    private Boolean isInterestEnabled = false;

    /** The period on which interest is compounded. */
    @Enumerated(EnumType.STRING)
    @Column(name = "interest_period", length = 20)
    private InterestPeriod interestPeriod;

    /** Collection of interest rate variations over time for this account. */
    @OneToMany(mappedBy = "account", cascade = CascadeType.ALL, orphanRemoval = true)
    @ToString.Exclude
    @Builder.Default
    private List<InterestRateVariation> interestRateVariations = new ArrayList<>();

    /** Timestamp when the account was created. Automatically set by Hibernate on first insert. */
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /**
     * Timestamp when the account was last updated. Automatically updated by Hibernate on any
     * modification.
     */
    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    /**
     * Override equals to use business key (userId + name) instead of ID. This prevents issues with
     * detached entities and ensures logical equality.
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Account)) return false;
        Account other = (Account) o;
        // Prefer database identity when available
        if (this.id != null && other.id != null) {
            return this.id.equals(other.id);
        }
        // Fall back to business key (userId + name) when id is not assigned yet
        return java.util.Objects.equals(this.userId, other.userId)
                && java.util.Objects.equals(this.name, other.name);
    }

    /** Override hashCode to match equals implementation. */
    @Override
    public int hashCode() {
        // If ID is assigned, use it for hashCode to be consistent with equals
        if (this.id != null) {
            return this.id.hashCode();
        }
        return java.util.Objects.hash(this.userId, this.name);
    }

    /**
     * Override toString to prevent logging encrypted data. Requirement: Security - Never log
     * encrypted fields in plain text
     */
    @Override
    public String toString() {
        return "Account{"
                + "id="
                + id
                + ", userId="
                + userId
                + ", name='[ENCRYPTED]'"
                + ", accountNumber='"
                + accountNumber
                + '\''
                + ", type="
                + type
                + ", currency='"
                + currency
                + '\''
                + ", balance="
                + balance
                + ", isActive="
                + isActive
                + ", createdAt="
                + createdAt
                + ", updatedAt="
                + updatedAt
                + '}';
    }
}
