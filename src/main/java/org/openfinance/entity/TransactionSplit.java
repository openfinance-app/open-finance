package org.openfinance.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.*;
import org.openfinance.converter.EncryptedBigDecimalConverter;
import org.openfinance.converter.EncryptedStringConverter;

/**
 * JPA entity representing a single line of a split transaction.
 *
 * <p>
 * A split allows one parent {@link Transaction} to be categorized across
 * multiple categories,
 * each with its own amount. The sum of all split amounts for a given
 * transaction must equal the
 * parent transaction's total amount (within a ±0.01 tolerance).
 *
 * <p>
 * <strong>Security:</strong> The {@code description} field is encrypted before
 * storage,
 * consistent with the pattern used in {@link Transaction}.
 *
 * <p>
 * <strong>Requirements:</strong>
 *
 * <ul>
 * <li>REQ-SPL-1.1: Split has categoryId, amount, and optional description
 * <li>REQ-SPL-1.3: Splits stored in dedicated transaction_splits table
 * <li>REQ-SPL-2.7: Cascade delete via FK ON DELETE CASCADE
 * </ul>
 *
 * @see Transaction
 * @see Category
 */
@Entity
@Table(name = "transaction_splits", indexes = {
        @Index(name = "idx_transaction_splits_transaction_id", columnList = "transaction_id")
})
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PUBLIC)
@AllArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class TransactionSplit {

    /** Primary key – unique identifier for this split line. */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Long id;

    /**
     * ID of the parent transaction this split belongs to.
     *
     * <p>
     * Foreign key to {@code transactions(id)} with {@code ON DELETE CASCADE} —
     * splits are
     * automatically removed when the parent transaction is deleted.
     *
     * <p>
     * Requirement REQ-SPL-2.7: Cascade delete
     */
    @NotNull(message = "Transaction ID is required")
    @Column(name = "transaction_id", nullable = false)
    private Long transactionId;

    /**
     * ID of the category assigned to this split line (nullable).
     *
     * <p>
     * Foreign key to {@code categories(id)} with {@code ON DELETE SET NULL}.
     *
     * <p>
     * Requirement REQ-SPL-1.1: Split must have a categoryId
     */
    @Column(name = "category_id")
    private Long categoryId;

    /** Lazy-loaded reference to the category entity. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id", insertable = false, updatable = false)
    private Category category;

    /**
     * Amount allocated to this split line.
     *
     * <p>
     * Must be positive. The sum of all split amounts for a transaction must equal
     * the parent
     * transaction amount (within ±0.01 tolerance).
     *
     * <p>
     * Requirement REQ-SPL-1.1: Amount field; REQ-SPL-1.2: Sum validation
     */
    @NotNull(message = "Split amount is required")
    @DecimalMin(value = "0.01", message = "Split amount must be at least 0.01")
    @Digits(integer = 15, fraction = 4, message = "Amount must have at most 15 integer digits and 4 decimal places")
    @Column(name = "amount", nullable = false, length = 512)
    @Convert(converter = EncryptedBigDecimalConverter.class)
    private BigDecimal amount;

    /**
     * Optional description for this split line (encrypted).
     *
     * <p>
     * <strong>Security:</strong> Encrypted before storage; column length is larger
     * than the
     * logical limit to accommodate AES-256-GCM overhead.
     *
     * <p>
     * Requirement REQ-SPL-1.1: Optional description per split
     */
    @Column(name = "description", length = 2000)
    @Convert(converter = EncryptedStringConverter.class)
    private String description;

    /** Timestamp when this split was created. */
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /** Timestamp when this split was last updated. */
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    /**
     * JPA lifecycle callback – sets {@code createdAt} and {@code updatedAt} before
     * first persist.
     */
    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    /** JPA lifecycle callback – refreshes {@code updatedAt} before every update. */
    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    /** Safe toString that omits the encrypted description field. */
    @Override
    public String toString() {
        return "TransactionSplit{"
                + "id="
                + id
                + ", transactionId="
                + transactionId
                + ", categoryId="
                + categoryId
                + ", amount="
                + amount
                + ", description='[ENCRYPTED]'"
                + ", createdAt="
                + createdAt
                + ", updatedAt="
                + updatedAt
                + '}';
    }
}
