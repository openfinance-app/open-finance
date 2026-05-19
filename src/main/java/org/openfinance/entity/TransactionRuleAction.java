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
import jakarta.validation.constraints.NotNull;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * JPA entity representing a single action in a {@link TransactionRule}.
 *
 * <p>Actions are applied in {@code sortOrder} sequence when all rule conditions are satisfied.
 * Different action types use different combinations of the three value fields.
 *
 * <p><strong>Parameter conventions by action type:</strong>
 *
 * <ul>
 *   <li>{@code SET_CATEGORY} — actionValue = category name
 *   <li>{@code SET_PAYEE} — actionValue = payee name
 *   <li>{@code ADD_TAG} — actionValue = tag string
 *   <li>{@code SET_DESCRIPTION}— actionValue = description text
 *   <li>{@code SET_AMOUNT} — actionValue = BigDecimal string
 *   <li>{@code ADD_SPLIT} — actionValue = category, actionValue2 = amount, actionValue3 =
 *       description
 *   <li>{@code SKIP_TRANSACTION}— no parameters needed
 * </ul>
 *
 * <p><strong>Requirements:</strong>
 *
 * <ul>
 *   <li>REQ-TR-3.1: Actions applied in order when conditions match
 *   <li>REQ-TR-3.2: Supported action types
 *   <li>REQ-TR-3.3: Multiple ADD_SPLIT actions define all splits
 * </ul>
 *
 * @see TransactionRule
 * @see RuleActionType
 */
@Entity
@Table(
        name = "transaction_rule_actions",
        indexes = {@Index(name = "idx_transaction_rule_actions_rule", columnList = "rule_id")})
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PUBLIC)
@AllArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class TransactionRuleAction {

    /** Primary key — unique identifier for this action. */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Long id;

    /**
     * ID of the parent rule this action belongs to. Managed by the owning {@link TransactionRule}
     * via JoinColumn.
     */
    @Column(name = "rule_id", insertable = false, updatable = false)
    private Long ruleId;

    /** The type of action to perform. Requirement: REQ-TR-3.2 */
    @NotNull(message = "Action type is required")
    @Enumerated(EnumType.STRING)
    @Column(name = "action_type", nullable = false, length = 30)
    private RuleActionType actionType;

    /**
     * Primary action parameter. Usage depends on actionType: - SET_CATEGORY: category name -
     * SET_PAYEE: payee name - ADD_TAG: tag string - SET_DESCRIPTION: description text - SET_AMOUNT:
     * BigDecimal string - ADD_SPLIT: category name for split Requirement: REQ-TR-3.2
     */
    @Column(name = "action_value")
    private String actionValue;

    /**
     * Secondary action parameter. Usage: ADD_SPLIT → amount as BigDecimal string. Requirement:
     * REQ-TR-3.2
     */
    @Column(name = "action_value2")
    private String actionValue2;

    /**
     * Tertiary action parameter. Usage: ADD_SPLIT → optional description for the split line.
     * Requirement: REQ-TR-3.2
     */
    @Column(name = "action_value3")
    private String actionValue3;

    /**
     * Display order of this action within the rule. Actions are applied in ascending sort order.
     */
    @Column(name = "sort_order", nullable = false)
    @Builder.Default
    private Integer sortOrder = 0;

    @Override
    public String toString() {
        return "TransactionRuleAction{"
                + "id="
                + id
                + ", ruleId="
                + ruleId
                + ", actionType="
                + actionType
                + ", actionValue='"
                + actionValue
                + '\''
                + ", actionValue2='"
                + actionValue2
                + '\''
                + ", actionValue3='"
                + actionValue3
                + '\''
                + ", sortOrder="
                + sortOrder
                + '}';
    }
}
