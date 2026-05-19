package org.openfinance.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.*;

/**
 * JPA entity representing a transaction category.
 *
 * <p>Categories organize transactions into meaningful groups for budgeting, reporting, and
 * analysis. Supports hierarchical structure with parent-child relationships for subcategories.
 *
 * <p><strong>Features:</strong>
 *
 * <ul>
 *   <li>User-specific categories (each user has their own set)
 *   <li>Hierarchical structure (parent/subcategories)
 *   <li>Type classification (INCOME or EXPENSE)
 *   <li>Customizable with icons and colors for UI display
 *   <li>System-provided default categories can be created on user registration
 * </ul>
 *
 * <p><strong>Example Hierarchy:</strong>
 *
 * <pre>
 * Shopping (parent)
 *   ├─ Groceries (subcategory)
 *   ├─ Clothing (subcategory)
 *   └─ Electronics (subcategory)
 * </pre>
 *
 * <p><strong>Requirements:</strong>
 *
 * <ul>
 *   <li>REQ-2.10.1: Category creation and management
 *   <li>REQ-2.10.2: Hierarchical category structure
 * </ul>
 *
 * @see Transaction
 * @see CategoryType
 * @since 1.0
 */
@Entity
@Table(
        name = "categories",
        indexes = {
            @Index(name = "idx_category_user_id", columnList = "user_id"),
            @Index(name = "idx_category_type", columnList = "category_type"),
            @Index(name = "idx_category_parent_id", columnList = "parent_id")
        })
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class Category {

    /** Primary key - unique identifier for the category. */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Long id;

    /**
     * ID of the user who owns this category.
     *
     * <p>Categories are user-specific - each user has their own set. Foreign key reference to the
     * users table.
     *
     * <p>Requirement REQ-2.10.1: User-specific categories
     */
    @NotNull(message = "User ID is required")
    @Column(name = "user_id", nullable = false)
    private Long userId;

    /**
     * Display name of the category.
     *
     * <p>Examples: "Groceries", "Salary", "Rent", "Entertainment"
     *
     * <p><strong>Note:</strong> This field is stored encrypted for privacy.
     */
    @NotBlank(message = "Category name is required")
    @Size(min = 1, max = 100, message = "Category name must be between 1 and 100 characters")
    @Column(name = "name", nullable = false, length = 100)
    private String name;

    /**
     * Type of category - INCOME or EXPENSE.
     *
     * <p>Determines which transactions can use this category:
     *
     * <ul>
     *   <li>INCOME categories: For salary, dividends, gifts, etc.
     *   <li>EXPENSE categories: For groceries, rent, utilities, etc.
     * </ul>
     *
     * <p>Requirement REQ-2.10.1: Category type classification
     */
    @NotNull(message = "Category type is required")
    @Enumerated(EnumType.STRING)
    @Column(name = "category_type", nullable = false, length = 20)
    private CategoryType type;

    /**
     * ID of the parent category for hierarchical structure (nullable for root categories).
     *
     * <p>Allows creation of subcategories. For example:
     *
     * <ul>
     *   <li>"Shopping" (parentId = null, root category)
     *   <li>"Groceries" (parentId = Shopping.id, subcategory)
     * </ul>
     *
     * <p>Requirement REQ-2.10.2: Hierarchical category structure
     */
    @Column(name = "parent_id")
    private Long parentId;

    /**
     * Reference to the parent category (bidirectional relationship).
     *
     * <p>Lazy-loaded to avoid unnecessary database queries.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id", insertable = false, updatable = false)
    private Category parent;

    /**
     * List of subcategories under this category.
     *
     * <p>Bidirectional relationship - enables navigation from parent to children. Lazy-loaded.
     * Children must be deleted manually before parent deletion.
     *
     * <p><strong>Note:</strong> Service layer must handle cascade delete logic manually to maintain
     * data integrity and avoid orphaned records.
     */
    @OneToMany(
            mappedBy = "parent",
            cascade = {CascadeType.PERSIST, CascadeType.MERGE},
            fetch = FetchType.LAZY)
    @Builder.Default
    private List<Category> subcategories = new ArrayList<>();

    /**
     * Icon identifier for UI display (optional).
     *
     * <p>Can be an emoji, icon name, or icon library reference. Examples: "🛒", "shopping-cart",
     * "fas fa-cart-shopping"
     */
    @Size(max = 50, message = "Icon must not exceed 50 characters")
    @Column(name = "icon", length = 50)
    private String icon;

    /**
     * Color code for UI display (optional).
     *
     * <p>Hex color code for category visualization in charts and reports. Examples: "#FF5733",
     * "#3498DB", "#2ECC71"
     */
    @Size(max = 20, message = "Color must not exceed 20 characters")
    @Column(name = "color", length = 20)
    private String color;

    /**
     * ISO 18245 Merchant Category Code (MCC) for this category.
     *
     * <p>Used for standardized financial transaction categorization. Examples: "5411" for
     * Groceries, "5812" for Restaurants, "6012" for Salary
     *
     * <p>Reference: ISO 18245 - Retail financial services - Merchant category codes
     */
    @Size(max = 10, message = "MCC code must not exceed 10 characters")
    @Column(name = "mcc_code", length = 10)
    private String mccCode;

    /**
     * i18n message key for the category name (system categories only).
     *
     * <p>When non-null, the display name should be resolved via {@code
     * MessageSource.getMessage(nameKey, null, fallbackName, locale)} so that system categories
     * appear in the user's preferred language.
     *
     * <p>Null for user-created categories — those use the encrypted {@code name} field.
     */
    @Column(name = "name_key", length = 100)
    private String nameKey;

    /**
     * Indicates if this is a system-provided default category.
     *
     * <p>System categories are created automatically on user registration and cannot be deleted
     * (but can be customized).
     */
    @Column(name = "is_system", nullable = false)
    @Builder.Default
    private Boolean isSystem = false;

    /**
     * Timestamp when the category was created.
     *
     * <p>Automatically set on entity creation.
     */
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /**
     * Timestamp when the category was last updated.
     *
     * <p>Automatically updated on entity modification.
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
     * Override toString to protect encrypted name field from logs. Requirement: Security - Never
     * log encrypted fields in plain text
     */
    @Override
    public String toString() {
        return "Category{"
                + "id="
                + id
                + ", userId="
                + userId
                + ", name='[ENCRYPTED]'"
                + ", nameKey='"
                + nameKey
                + '\''
                + ", type="
                + type
                + ", parentId="
                + parentId
                + ", icon='"
                + icon
                + '\''
                + ", color='"
                + color
                + '\''
                + ", mccCode='"
                + mccCode
                + '\''
                + ", isSystem="
                + isSystem
                + ", createdAt="
                + createdAt
                + ", updatedAt="
                + updatedAt
                + '}';
    }
}
