package org.openfinance.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * JPA entity representing a single condition in a {@link TransactionRule}.
 *
 * <p>Conditions are evaluated with AND logic — all conditions in a rule must be satisfied for the
 * rule to fire against an imported transaction.
 *
 * <p><strong>Requirements:</strong>
 *
 * <ul>
 *   <li>REQ-TR-2.1: Field targeting (DESCRIPTION, AMOUNT, TRANSACTION_TYPE)
 *   <li>REQ-TR-2.2: Operators (CONTAINS, NOT_CONTAINS, EQUALS, etc.)
 *   <li>REQ-TR-2.3: Value stored as string, interpreted per field+operator
 *   <li>REQ-TR-2.4: AND logic between conditions
 * </ul>
 *
 * @see TransactionRule
 * @see RuleConditionField
 * @see RuleConditionOperator
 */
@Entity
@Table(
        name = "transaction_rule_conditions",
        indexes = {@Index(name = "idx_transaction_rule_conditions_rule", columnList = "rule_id")})
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PUBLIC)
@AllArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class TransactionRuleCondition {

    /** Primary key — unique identifier for this condition. */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Long id;

    /**
     * ID of the parent rule this condition belongs to. Managed by the owning {@link
     * TransactionRule} via JoinColumn.
     */
    @Column(name = "rule_id", insertable = false, updatable = false)
    private Long ruleId;

    /** The field of the imported transaction this condition targets. Requirement: REQ-TR-2.1 */
    @NotNull(message = "Condition field is required")
    @Enumerated(EnumType.STRING)
    @Column(name = "field", nullable = false, length = 30)
    private RuleConditionField field;

    /** The comparison operator to apply. Requirement: REQ-TR-2.2 */
    @NotNull(message = "Condition operator is required")
    @Enumerated(EnumType.STRING)
    @Column(name = "operator", nullable = false, length = 30)
    private RuleConditionOperator operator;

    /**
     * The value to compare against. For string operators: literal substring. For numeric operators:
     * decimal string (parsed as BigDecimal). For TRANSACTION_TYPE: "INCOME" or "EXPENSE".
     * Requirement: REQ-TR-2.3
     */
    @NotBlank(message = "Condition value is required")
    @Column(name = "value", nullable = false)
    private String value;

    /** Display order of this condition within the rule. */
    @Column(name = "sort_order", nullable = false)
    @Builder.Default
    private Integer sortOrder = 0;

    @Override
    public String toString() {
        return "TransactionRuleCondition{"
                + "id="
                + id
                + ", ruleId="
                + ruleId
                + ", field="
                + field
                + ", operator="
                + operator
                + ", value='"
                + value
                + '\''
                + ", sortOrder="
                + sortOrder
                + '}';
    }
}
