package org.openfinance.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * JPA entity representing a user-defined transaction assignment rule.
 *
 * <p>A rule consists of one or more {@link TransactionRuleCondition} objects (evaluated with AND
 * logic) and one or more {@link TransactionRuleAction} objects that are applied when all conditions
 * match an imported transaction.
 *
 * <p>Rules are evaluated during the import review phase, before {@code AutoCategorizationService}
 * runs. The first matching rule wins (stop-on-first-match behaviour, ordered by priority
 * ascending).
 *
 * <p><strong>Requirements:</strong>
 *
 * <ul>
 *   <li>REQ-TR-1.1: Rule with name, conditions, actions, priority, enabled flag
 *   <li>REQ-TR-1.5: Rules scoped to authenticated user
 *   <li>REQ-TR-NFR-2: Persisted in relational table with FK cascades
 * </ul>
 *
 * @see TransactionRuleCondition
 * @see TransactionRuleAction
 */
@Entity
@Table(
        name = "transaction_rules",
        indexes = {
            @Index(
                    name = "idx_transaction_rules_user_priority",
                    columnList = "user_id, is_enabled, priority")
        })
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PUBLIC)
@AllArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class TransactionRule {

    /** Primary key — unique identifier for this rule. */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Long id;

    /**
     * ID of the user who owns this rule. Rules are user-scoped; a user can only access their own
     * rules. Requirement: REQ-TR-1.5, REQ-TR-NFR-3
     */
    @NotNull(message = "User ID is required")
    @Column(name = "user_id", nullable = false)
    private Long userId;

    /** Human-readable name for this rule. Requirement: REQ-TR-1.1 */
    @NotBlank(message = "Rule name is required")
    @Size(max = 100, message = "Rule name must not exceed 100 characters")
    @Column(name = "name", nullable = false, length = 100)
    private String name;

    /**
     * Priority for rule evaluation order. Lower number = higher priority. Rules are evaluated in
     * ascending priority order; first match wins. Requirement: REQ-TR-1.1, REQ-TR-4.5
     */
    @Column(name = "priority", nullable = false)
    @Builder.Default
    private Integer priority = 0;

    /**
     * Whether this rule is active and should be evaluated during import. Disabled rules are never
     * evaluated. Requirement: REQ-TR-1.1, REQ-TR-4.4
     */
    @Column(name = "is_enabled", nullable = false)
    @Builder.Default
    private Boolean isEnabled = true;

    /**
     * Condition match logic: 'AND' (all conditions must match) or 'OR' (any condition matches).
     * Defaults to 'AND' for backward compatibility. Requirement: REQ-TR-2.4
     */
    @Column(name = "condition_match", nullable = false, length = 3)
    @Builder.Default
    private String conditionMatch = "AND";

    /**
     * Ordered list of conditions for this rule. All conditions must be true for the rule to fire
     * (AND logic). Requirement: REQ-TR-2.4
     */
    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @JoinColumn(name = "rule_id", nullable = false)
    @OrderBy("sortOrder ASC")
    @Builder.Default
    private List<TransactionRuleCondition> conditions = new ArrayList<>();

    /**
     * Ordered list of actions to apply when all conditions are satisfied. Actions are applied in
     * sortOrder sequence. Requirement: REQ-TR-3.1
     */
    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @JoinColumn(name = "rule_id", nullable = false)
    @OrderBy("sortOrder ASC")
    @Builder.Default
    private List<TransactionRuleAction> actions = new ArrayList<>();

    /** Timestamp when this rule was created. */
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /** Timestamp when this rule was last updated. */
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    /** JPA lifecycle callback — sets createdAt and updatedAt before first persist. */
    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    /** JPA lifecycle callback — refreshes updatedAt before every update. */
    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    @Override
    public String toString() {
        return "TransactionRule{"
                + "id="
                + id
                + ", userId="
                + userId
                + ", name='"
                + name
                + '\''
                + ", priority="
                + priority
                + ", isEnabled="
                + isEnabled
                + ", conditionCount="
                + (conditions != null ? conditions.size() : 0)
                + ", actionCount="
                + (actions != null ? actions.size() : 0)
                + ", createdAt="
                + createdAt
                + '}';
    }
}
