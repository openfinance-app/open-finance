package org.openfinance.entity;

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
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

/**
 * Entity representing a liability (debt/loan) Task 6.1.2: Create Liability entity
 *
 * <p>Tracks debts such as mortgages, loans, credit cards, etc. Supports interest calculations and
 * amortization schedules.
 */
@Entity
@Table(
        name = "liabilities",
        indexes = {
            @Index(name = "idx_liability_user_id", columnList = "user_id"),
            @Index(name = "idx_liability_type", columnList = "type")
        })
@Getter
@Setter
@ToString(onlyExplicitlyIncluded = true)
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class Liability {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @ToString.Include
    @EqualsAndHashCode.Include
    private Long id;

    @NotNull(message = "User ID cannot be null")
    @Column(name = "user_id", nullable = false)
    @ToString.Include
    private Long userId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", insertable = false, updatable = false)
    private User user;

    /**
     * Encrypted liability name (e.g., "Home Mortgage", "Car Loan") Max encrypted length: 512 chars
     * for AES-256
     */
    @Column(name = "name", nullable = false, length = 512)
    @NotBlank(message = "Liability name is required")
    @ToString.Include
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 20)
    @NotNull(message = "Liability type is required")
    @ToString.Include
    private LiabilityType type;

    /** Original principal amount (encrypted) Max encrypted length: 512 chars */
    @Column(name = "principal", nullable = false, length = 512)
    @NotNull(message = "Principal amount is required")
    private String principal;

    /** Current outstanding balance (encrypted) Max encrypted length: 512 chars */
    @Column(name = "current_balance", nullable = false, length = 512)
    @NotNull(message = "Current balance is required")
    private String currentBalance;

    /** Annual interest rate as percentage (e.g., 5.25 for 5.25%) Stored as encrypted string */
    @Column(name = "interest_rate", length = 512)
    private String interestRate;

    @Column(name = "start_date", nullable = false)
    @NotNull(message = "Start date is required")
    @ToString.Include
    private LocalDate startDate;

    /** Expected end date for the liability (maturity date) */
    @Column(name = "end_date")
    private LocalDate endDate;

    /** Minimum monthly payment (encrypted) Max encrypted length: 512 chars */
    @Column(name = "minimum_payment", length = 512)
    private String minimumPayment;

    @Column(name = "currency", nullable = false, length = 3)
    @NotBlank(message = "Currency is required")
    @Pattern(regexp = "^[A-Z]{3}$", message = "Currency must be a 3-letter ISO code")
    @ToString.Include
    private String currency;

    /**
     * Annual insurance rate as a percentage of the principal amount (encrypted). Example: 0.5 means
     * 0.5% of principal per year for insurance. Monthly insurance cost = principal ×
     * (insurancePercentage / 100) / 12
     *
     * <p>Requirement REQ-LIA-1: Insurance Percentage Field
     */
    @Column(name = "insurance_percentage", length = 512)
    private String insurancePercentage;

    /**
     * Additional fees associated with this liability (encrypted). Covers one-time or periodic fees
     * such as processing fees, origination fees, or late payment fees already incurred.
     *
     * <p>Requirement REQ-LIA-2: Additional Fees Field
     */
    @Column(name = "additional_fees", length = 512)
    private String additionalFees;

    /**
     * Encrypted notes (e.g., "Refinanced in 2023") Max encrypted length: 2048 chars for longer
     * notes
     */
    @Column(name = "notes", length = 2048)
    private String notes;

    /** Optional relationship to a predefined financial institution holding the liability. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "institution_id")
    private Institution institution;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
