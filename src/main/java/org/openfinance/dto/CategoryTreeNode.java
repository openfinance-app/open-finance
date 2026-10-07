package org.openfinance.dto;

import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO representing a category tree node for hierarchical display.
 *
 * <p>Used by the category management page to display categories in a tree structure with parent
 * categories containing their subcategories.
 *
 * <p>Requirements: REQ-CAT-3.1 - Hierarchical category tree display
 *
 * @see CategoryResponse
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CategoryTreeNode {

    /** Unique identifier for the category. */
    private Long id;

    /** Display name of the category (decrypted for user categories). */
    private String name;

    /** Icon identifier for UI display. */
    private String icon;

    /** Color code for UI display. */
    private String color;

    /** ISO 18245 Merchant Category Code. */
    private String mccCode;

    /** Parent category ID (null for root categories). */
    private Long parentId;

    /** List of subcategories under this category. */
    @Builder.Default private List<CategoryTreeNode> subcategories = new ArrayList<>();

    /** Number of transactions using this category. */
    private Long transactionCount;

    /** Net incoming minus outgoing activity, including descendants, in currency. */
    private java.math.BigDecimal totalAmount;

    private java.math.BigDecimal incomeAmount;
    private java.math.BigDecimal expenseAmount;
    private String currency;

    /** Whether this is a system-provided category. */
    private Boolean isSystem;
}
