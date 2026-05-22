package org.openfinance.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.*;

/**
 * JPA entity representing a budget for tracking spending limits.
 *
 * <p>Budgets allow users to set spending limits for specific categories over defined time periods
 * (weekly, monthly, quarterly, yearly). The system tracks actual spending against budgeted amounts
 * and alerts users when approaching or exceeding limits.
 *
 * <p><strong>Features:</strong>
 *
 * <ul>
 *   <li>User-specific budgets (each user has their own budgets)
 *   <li>Category-based budgets (one budget per category)
 *   <li>Multiple period types (WEEKLY, MONTHLY, QUARTERLY, YEARLY)
 *   <li>Rollover support (unused budget carries to next period)
 *   <li>Date-range tracking (startDate to endDate)
 *   <li>Encrypted amount storage for security
 * </ul>
 *
 * <p><strong>Example Use Cases:</strong>
 *
 * <pre>
 * - Monthly grocery budget: $500/month
 * - Quarterly entertainment budget: $1,200/quarter with rollover
 * - Yearly vacation budget: $5,000/year
 * </pre>
 *
 * <p><strong>Requirements:</strong>
 *
 * <ul>
 *   <li>REQ-2.9.1.1: Budget creation with category, period, amount, rollover
 *   <li>REQ-2.9.1.2: Budget tracking and progress calculation
 * </ul>
 *
 * @see Category
 * @see BudgetPeriod
 * @author Open-Finance Development Team
 * @version 1.0
 * @since 2026-02-02
 */
@Entity
@Table(
        name = "budgets",
        indexes = {
            @Index(name = "idx_budget_user_id", columnList = "user_id"),
            @Index(name = "idx_budget_category_id", columnList = "category_id"),
            @Index(name = "idx_budget_period", columnList = "period"),
            @Index(name = "idx_budget_dates", columnList = "start_date, end_date")
        })
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@ToString
public class Budget {

    /** Primary key - unique identifier for the budget. */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Long id;

    /**
     * ID of the user who owns this budget.
     *
     * <p>Budgets are user-specific - each user has their own budgets. Foreign key reference to the
     * users table.
     *
     * <p>Requirement REQ-2.9.1.1: User-specific budgets
     */
    @NotNull(message = "User ID is required")
    @Column(name = "user_id", nullable = false)
    private Long userId;

    /**
     * ID of the category this budget applies to.
     *
     * <p>Each budget is associated with one category. Users can create multiple budgets for the
     * same category if they have different periods.
     *
     * <p>Requirement REQ-2.9.1.1: Category-based budgets
     */
    @NotNull(message = "Category ID is required")
    @Column(name = "category_id", nullable = false)
    private Long categoryId;

    /**
     * Relationship to the category entity.
     *
     * <p>Many budgets can reference one category. Uses LAZY loading to avoid unnecessary database
     * queries.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id", insertable = false, updatable = false)
    private Category category;

    /**
     * Budget amount (encrypted for security).
     *
     * <p>The spending limit for this budget period. Stored as an encrypted string and decrypted at
     * runtime using the user's master password.
     *
     * <p>When decrypted, represents a BigDecimal value with precision 19, scale 4 (e.g.,
     * "1234567890123456.7890").
     *
     * <p>Requirement REQ-2.9.1.1: Budget amount tracking
     *
     * <p>Requirement REQ-1.1.1: Encryption of sensitive financial data
     */
    @NotNull(message = "Budget amount is required")
    @Column(name = "amount", nullable = false, length = 512)
    private String amount; // Encrypted BigDecimal

    /**
     * Currency code in ISO 4217 format (e.g., "USD", "EUR", "GBP").
     *
     * <p>Specifies the currency in which the budget amount is denominated. Used for multi-currency
     * support and proper amount display.
     *
     * <p>Requirement REQ-2.8: Multi-currency support
     */
    @NotNull(message = "Currency is required")
    @Size(min = 3, max = 3, message = "Currency must be a 3-letter ISO 4217 code")
    @Column(name = "currency", nullable = false, length = 3)
    private String currency;

    /** FK to the currencies table for referential integrity. */
    @Column(name = "currency_id")
    private Long currencyId;

    /** Reference to the currency entity (lazy-loaded). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "currency_id", insertable = false, updatable = false)
    private Currency currencyEntity;

    /**
     * Budget period type.
     *
     * <p>Defines how frequently the budget resets:
     *
     * <ul>
     *   <li>WEEKLY - Resets every 7 days
     *   <li>MONTHLY - Resets on 1st of each month
     *   <li>QUARTERLY - Resets every 3 months
     *   <li>YEARLY - Resets on January 1st
     * </ul>
     *
     * <p>Requirement REQ-2.9.1.1: Multiple period types
     */
    @NotNull(message = "Budget period is required")
    @Enumerated(EnumType.STRING)
    @Column(name = "period", nullable = false, length = 20)
    private BudgetPeriod period;

    /**
     * Start date of the budget period.
     *
     * <p>Defines when this budget becomes active. For ongoing budgets, this represents the start of
     * the current period.
     *
     * <p>Example: For a monthly budget created on Feb 15, 2026, startDate might be Feb 1, 2026.
     *
     * <p>Requirement REQ-2.9.1.1: Budget date range
     */
    @NotNull(message = "Start date is required")
    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    /**
     * End date of the budget period.
     *
     * <p>Defines when this budget period ends. For ongoing budgets, this represents the end of the
     * current period and will be updated when the period rolls over.
     *
     * <p>Example: For a monthly budget starting Feb 1, 2026, endDate would be Feb 28/29, 2026.
     *
     * <p>Requirement REQ-2.9.1.1: Budget date range
     */
    @NotNull(message = "End date is required")
    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    /**
     * Rollover flag - whether unused budget carries to next period.
     *
     * <p>When true, if the user spends less than the budgeted amount, the remaining balance is
     * added to the next period's budget.
     *
     * <p>Example: Monthly budget $500, spent $400, rollover = true Next month's budget: $500 + $100
     * = $600
     *
     * <p>Requirement REQ-2.9.1.1: Rollover support
     */
    @NotNull(message = "Rollover flag is required")
    @Column(name = "rollover", nullable = false)
    @Builder.Default
    private Boolean rollover = false;

    /**
     * Optional notes or description for the budget.
     *
     * <p>Users can add context or reminders about this budget. For example: "Christmas shopping",
     * "Summer vacation fund", etc.
     */
    @Size(max = 500, message = "Notes cannot exceed 500 characters")
    @Column(name = "notes", length = 500)
    private String notes;

    /**
     * Timestamp when the budget was created.
     *
     * <p>Automatically set on entity creation. Used for auditing and ordering budgets by creation
     * time.
     */
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /**
     * Timestamp when the budget was last updated.
     *
     * <p>Automatically updated on any entity modification. Used for auditing and tracking changes.
     */
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    /**
     * JPA lifecycle callback to set creation timestamp.
     *
     * <p>Called automatically before persisting a new budget entity.
     */
    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        createdAt = now;
        updatedAt = now;
    }

    /**
     * JPA lifecycle callback to update modification timestamp.
     *
     * <p>Called automatically before updating an existing budget entity.
     */
    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
