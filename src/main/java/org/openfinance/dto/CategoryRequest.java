package org.openfinance.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Data Transfer Object for creating or updating a category.
 *
 * <p>This DTO is used for both POST (create) and PUT (update) operations. Validation annotations
 * ensure data integrity before processing.
 *
 * <p>Requirement REQ-2.4.1: Category creation with name and optional parent
 *
 * <p>Requirement REQ-2.4.2: Category updates
 *
 * @see org.openfinance.entity.Category
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CategoryRequest {

    /**
     * Name of the category (e.g., "Groceries", "Salary", "Entertainment").
     *
     * <p>This field will be encrypted before storing in the database.
     *
     * <p>Requirement REQ-2.4.1: Category must have a descriptive name
     */
    @NotBlank(message = "{category.name.required}")
    @Size(min = 1, max = 100, message = "{category.name.between}")
    private String name;

    /**
     * ID of the parent category (for subcategories).
     *
     * <p>Null for root categories. Must reference an existing category owned by the same user.
     *
     * <p>Requirement REQ-2.4.1: Support for subcategories (hierarchical structure)
     */
    private Long parentId;

    /**
     * Icon identifier for UI display (optional).
     *
     * <p>Can be an emoji, icon name, or icon library reference. Examples: "🛒", "shopping-cart",
     * "fas fa-cart-shopping"
     */
    @Size(max = 50, message = "{category.icon.max}")
    private String icon;

    /**
     * Color code for UI display (optional).
     *
     * <p>Typically in hex format: "#FF5733", "#10B981"
     */
    @Size(max = 20, message = "{category.color.max}")
    private String color;
}
