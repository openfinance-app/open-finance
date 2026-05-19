package org.openfinance.service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.openfinance.dto.ImportedTransaction;
import org.openfinance.dto.TransactionRuleRequest;
import org.openfinance.dto.TransactionRuleResponse;
import org.openfinance.entity.RuleConditionField;
import org.openfinance.entity.RuleConditionOperator;
import org.openfinance.entity.TransactionRule;
import org.openfinance.entity.TransactionRuleAction;
import org.openfinance.entity.TransactionRuleCondition;
import org.openfinance.exception.TransactionRuleNotFoundException;
import org.openfinance.mapper.TransactionRuleMapper;
import org.openfinance.repository.TransactionRuleRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Default implementation of {@link TransactionRuleService}.
 *
 * <p>Manages CRUD for user-defined transaction rules and runs the rules engine during import via
 * {@link #applyRules(List, Long)}.
 *
 * <p><strong>Requirements:</strong> REQ-TR-1 through REQ-TR-4
 */
@Service
@Transactional
@RequiredArgsConstructor
@Slf4j
public class TransactionRuleServiceImpl implements TransactionRuleService {

    private final TransactionRuleRepository transactionRuleRepository;
    private final TransactionRuleMapper transactionRuleMapper;

    // -----------------------------------------------------------------------
    // CRUD
    // -----------------------------------------------------------------------

    /** {@inheritDoc} */
    @Override
    @Transactional(readOnly = true)
    public List<TransactionRuleResponse> getRulesForUser(Long userId) {
        log.debug("Fetching all rules for userId={}", userId);
        return transactionRuleRepository.findByUserIdOrderByPriorityAscCreatedAtAsc(userId).stream()
                .map(transactionRuleMapper::toResponse)
                .toList();
    }

    /** {@inheritDoc} */
    @Override
    @Transactional(readOnly = true)
    public TransactionRuleResponse getRule(Long id, Long userId) {
        log.debug("Fetching rule id={} for userId={}", id, userId);
        TransactionRule rule = findOwnedRule(id, userId);
        return transactionRuleMapper.toResponse(rule);
    }

    /** {@inheritDoc} */
    @Override
    public TransactionRuleResponse createRule(Long userId, TransactionRuleRequest request) {
        log.debug("Creating rule '{}' for userId={}", request.getName(), userId);
        TransactionRule rule = transactionRuleMapper.toEntity(userId, request);
        TransactionRule saved = transactionRuleRepository.save(rule);
        log.info("Created transaction rule id={} for userId={}", saved.getId(), userId);
        return transactionRuleMapper.toResponse(saved);
    }

    /**
     * {@inheritDoc}
     *
     * <p>The entire conditions and actions collections are replaced with the values from the
     * request (orphan removal handles the old child records).
     */
    @Override
    public TransactionRuleResponse updateRule(
            Long id, Long userId, TransactionRuleRequest request) {
        log.debug("Updating rule id={} for userId={}", id, userId);
        TransactionRule existing = findOwnedRule(id, userId);

        existing.setName(request.getName());
        existing.setPriority(
                request.getPriority() != null ? request.getPriority() : existing.getPriority());
        existing.setIsEnabled(
                request.getIsEnabled() != null ? request.getIsEnabled() : existing.getIsEnabled());
        existing.setConditionMatch(
                request.getConditionMatch() != null ? request.getConditionMatch() : "AND");

        // Replace conditions (orphan removal will delete old records)
        existing.getConditions().clear();
        if (request.getConditions() != null) {
            for (TransactionRuleRequest.ConditionRequest cr : request.getConditions()) {
                existing.getConditions()
                        .add(
                                TransactionRuleCondition.builder()
                                        .field(cr.getField())
                                        .operator(cr.getOperator())
                                        .value(cr.getValue())
                                        .sortOrder(
                                                cr.getSortOrder() != null ? cr.getSortOrder() : 0)
                                        .build());
            }
        }

        // Replace actions (orphan removal will delete old records)
        existing.getActions().clear();
        if (request.getActions() != null) {
            for (TransactionRuleRequest.ActionRequest ar : request.getActions()) {
                existing.getActions()
                        .add(
                                TransactionRuleAction.builder()
                                        .actionType(ar.getActionType())
                                        .actionValue(ar.getActionValue())
                                        .actionValue2(ar.getActionValue2())
                                        .actionValue3(ar.getActionValue3())
                                        .sortOrder(
                                                ar.getSortOrder() != null ? ar.getSortOrder() : 0)
                                        .build());
            }
        }

        TransactionRule saved = transactionRuleRepository.save(existing);
        log.info("Updated transaction rule id={}", saved.getId());
        return transactionRuleMapper.toResponse(saved);
    }

    /** {@inheritDoc} */
    @Override
    public void deleteRule(Long id, Long userId) {
        log.debug("Deleting rule id={} for userId={}", id, userId);
        TransactionRule rule = findOwnedRule(id, userId);
        transactionRuleRepository.delete(rule);
        log.info("Deleted transaction rule id={}", id);
    }

    /** {@inheritDoc} */
    @Override
    public TransactionRuleResponse toggleRule(Long id, Long userId) {
        log.debug("Toggling rule id={} for userId={}", id, userId);
        TransactionRule rule = findOwnedRule(id, userId);
        rule.setIsEnabled(!Boolean.TRUE.equals(rule.getIsEnabled()));
        TransactionRule saved = transactionRuleRepository.save(rule);
        log.info(
                "Toggled transaction rule id={} → isEnabled={}",
                saved.getId(),
                saved.getIsEnabled());
        return transactionRuleMapper.toResponse(saved);
    }

    // -----------------------------------------------------------------------
    // Rules Engine
    // -----------------------------------------------------------------------

    /**
     * {@inheritDoc}
     *
     * <p>Implementation details:
     *
     * <ol>
     *   <li>Load all enabled rules for the user, ordered by ascending priority.
     *   <li>For each transaction, iterate rules and evaluate all conditions (AND logic).
     *   <li>On a match: apply actions, annotate the transaction with {@code RULE_MATCH:}, record
     *       the index, and break (stop-on-first-match).
     * </ol>
     *
     * Requirement: REQ-TR-4.1 through REQ-TR-4.7
     */
    @Override
    @Transactional(readOnly = true)
    public Set<Integer> applyRules(List<ImportedTransaction> transactions, Long userId) {
        List<TransactionRule> enabledRules =
                transactionRuleRepository
                        .findByUserIdAndIsEnabledTrueOrderByPriorityAscCreatedAtAsc(userId);

        Set<Integer> matchedIndices = new HashSet<>();

        if (enabledRules.isEmpty() || transactions == null || transactions.isEmpty()) {
            return matchedIndices;
        }

        log.debug(
                "Applying {} rules to {} transactions for userId={}",
                enabledRules.size(),
                transactions.size(),
                userId);

        for (int i = 0; i < transactions.size(); i++) {
            ImportedTransaction tx = transactions.get(i);

            for (TransactionRule rule : enabledRules) {
                if (allConditionsMatch(rule.getConditions(), rule.getConditionMatch(), tx)) {
                    applyActions(rule.getActions(), tx);
                    tx.addValidationError("RULE_MATCH: Rule '" + rule.getName() + "' matched");
                    matchedIndices.add(i);
                    log.debug("Rule '{}' matched transaction index={}", rule.getName(), i);
                    break; // stop-on-first-match — REQ-TR-4.5
                }
            }
        }

        log.debug(
                "Rules engine matched {}/{} transactions",
                matchedIndices.size(),
                transactions.size());
        return matchedIndices;
    }

    // -----------------------------------------------------------------------
    // Private helpers — condition evaluation
    // -----------------------------------------------------------------------

    /**
     * Returns {@code true} if conditions match the transaction according to the given match mode:
     * 'AND' (all must match) or 'OR' (any must match). An empty condition list never matches.
     * Requirement: REQ-TR-2.4
     */
    private boolean allConditionsMatch(
            List<TransactionRuleCondition> conditions,
            String conditionMatch,
            ImportedTransaction tx) {
        if (conditions == null || conditions.isEmpty()) {
            return false;
        }
        boolean useOrLogic = "OR".equalsIgnoreCase(conditionMatch);
        if (useOrLogic) {
            for (TransactionRuleCondition condition : conditions) {
                if (evaluateCondition(condition, tx)) {
                    return true;
                }
            }
            return false;
        }
        for (TransactionRuleCondition condition : conditions) {
            if (!evaluateCondition(condition, tx)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Evaluates a single condition against an imported transaction.
     *
     * <p>Field dispatch:
     *
     * <ul>
     *   <li>{@code DESCRIPTION} — combined payee+memo string; string operators only
     *   <li>{@code AMOUNT} — absolute value of amount; numeric operators
     *   <li>{@code TRANSACTION_TYPE} — "INCOME" vs "EXPENSE" based on sign; EQUALS/NOT_EQUALS
     * </ul>
     *
     * Requirement: REQ-TR-2.1, REQ-TR-2.2
     */
    private boolean evaluateCondition(TransactionRuleCondition condition, ImportedTransaction tx) {
        RuleConditionField field = condition.getField();
        RuleConditionOperator operator = condition.getOperator();
        String conditionValue = condition.getValue();

        switch (field) {
            case DESCRIPTION -> {
                String description = buildDescription(tx);
                return evaluateStringCondition(operator, description, conditionValue);
            }
            case AMOUNT -> {
                if (tx.getAmount() == null) {
                    return false;
                }
                BigDecimal absAmount = tx.getAmount().abs();
                return evaluateNumericCondition(operator, absAmount, conditionValue);
            }
            case TRANSACTION_TYPE -> {
                String transactionType = deriveTransactionType(tx);
                return evaluateStringCondition(operator, transactionType, conditionValue);
            }
            default -> {
                log.warn("Unknown condition field: {}", field);
                return false;
            }
        }
    }

    /**
     * Builds the combined description string (payee + memo) used for DESCRIPTION matching. Mirrors
     * what AutoCategorizationService uses for its analysis string.
     */
    private String buildDescription(ImportedTransaction tx) {
        StringBuilder sb = new StringBuilder();
        if (tx.getPayee() != null && !tx.getPayee().isBlank()) {
            sb.append(tx.getPayee().trim());
        }
        if (tx.getMemo() != null && !tx.getMemo().isBlank()) {
            if (sb.length() > 0) {
                sb.append(' ');
            }
            sb.append(tx.getMemo().trim());
        }
        return sb.toString().toLowerCase();
    }

    /**
     * Derives the transaction type string ("INCOME" or "EXPENSE") from the amount sign.
     * Requirement: REQ-TR-2.1 (TRANSACTION_TYPE field)
     */
    private String deriveTransactionType(ImportedTransaction tx) {
        if (tx.getAmount() == null) {
            return "EXPENSE"; // treat missing amount as expense
        }
        return tx.getAmount().compareTo(BigDecimal.ZERO) >= 0 ? "INCOME" : "EXPENSE";
    }

    /** Evaluates a string-based operator. Requirement: REQ-TR-2.2 */
    private boolean evaluateStringCondition(
            RuleConditionOperator operator, String actual, String expected) {
        if (actual == null) {
            actual = "";
        }
        String actualLower = actual.toLowerCase();
        String expectedLower = expected != null ? expected.toLowerCase() : "";

        return switch (operator) {
            case CONTAINS -> actualLower.contains(expectedLower);
            case NOT_CONTAINS -> !actualLower.contains(expectedLower);
            case EQUALS -> actualLower.equals(expectedLower);
            case NOT_EQUALS -> !actualLower.equals(expectedLower);
            default -> {
                log.warn("Operator {} is not valid for string fields; condition skipped", operator);
                yield false;
            }
        };
    }

    /** Evaluates a numeric operator against a BigDecimal actual value. Requirement: REQ-TR-2.2 */
    private boolean evaluateNumericCondition(
            RuleConditionOperator operator, BigDecimal actual, String expectedStr) {
        BigDecimal expected;
        try {
            expected = new BigDecimal(expectedStr.trim());
        } catch (NumberFormatException e) {
            log.warn("Condition value '{}' is not a valid number; condition skipped", expectedStr);
            return false;
        }

        int cmp = actual.compareTo(expected);
        return switch (operator) {
            case EQUALS -> cmp == 0;
            case NOT_EQUALS -> cmp != 0;
            case GREATER_THAN -> cmp > 0;
            case LESS_THAN -> cmp < 0;
            case GREATER_OR_EQUAL -> cmp >= 0;
            case LESS_OR_EQUAL -> cmp <= 0;
            default -> {
                log.warn(
                        "Operator {} is not valid for numeric fields; condition skipped", operator);
                yield false;
            }
        };
    }

    // -----------------------------------------------------------------------
    // Private helpers — action application
    // -----------------------------------------------------------------------

    /**
     * Applies all actions in the given list to the imported transaction. Actions are processed in
     * their natural (sortOrder-ascending) list order. Requirement: REQ-TR-3.1, REQ-TR-3.2
     */
    private void applyActions(List<TransactionRuleAction> actions, ImportedTransaction tx) {
        if (actions == null) {
            return;
        }
        for (TransactionRuleAction action : actions) {
            applyAction(action, tx);
        }
    }

    /** Applies a single action to the transaction. Requirement: REQ-TR-3.2, REQ-TR-4.7 */
    private void applyAction(TransactionRuleAction action, ImportedTransaction tx) {
        switch (action.getActionType()) {
            case SET_CATEGORY -> {
                if (action.getActionValue() != null) {
                    tx.setCategory(action.getActionValue());
                    log.debug("SET_CATEGORY → '{}'", action.getActionValue());
                }
            }
            case SET_PAYEE -> {
                if (action.getActionValue() != null) {
                    tx.setPayee(action.getActionValue());
                    log.debug("SET_PAYEE → '{}'", action.getActionValue());
                }
            }
            case ADD_TAG -> {
                if (action.getActionValue() != null && !action.getActionValue().isBlank()) {
                    if (tx.getTags() == null) {
                        tx.setTags(new ArrayList<>());
                    }
                    tx.getTags().add(action.getActionValue().trim());
                    log.debug("ADD_TAG → '{}'", action.getActionValue());
                }
            }
            case SET_DESCRIPTION -> {
                if (action.getActionValue() != null) {
                    tx.setMemo(action.getActionValue());
                    log.debug("SET_DESCRIPTION → '{}'", action.getActionValue());
                }
            }
            case SET_AMOUNT -> {
                if (action.getActionValue() != null) {
                    try {
                        BigDecimal newAmount = new BigDecimal(action.getActionValue().trim());
                        tx.setAmount(newAmount);
                        log.debug("SET_AMOUNT → {}", newAmount);
                    } catch (NumberFormatException e) {
                        log.warn(
                                "SET_AMOUNT action value '{}' is not a valid number; skipping",
                                action.getActionValue());
                    }
                }
            }
            case ADD_SPLIT -> {
                String category = action.getActionValue();
                String amountStr = action.getActionValue2();
                String description = action.getActionValue3();

                if (category != null && amountStr != null) {
                    try {
                        BigDecimal splitAmount = new BigDecimal(amountStr.trim());
                        ImportedTransaction.SplitEntry split =
                                ImportedTransaction.SplitEntry.builder()
                                        .category(category)
                                        .amount(splitAmount)
                                        .memo(description)
                                        .build();
                        if (tx.getSplits() == null) {
                            tx.setSplits(new ArrayList<>());
                        }
                        tx.getSplits().add(split);
                        log.debug("ADD_SPLIT → category='{}', amount={}", category, splitAmount);
                    } catch (NumberFormatException e) {
                        log.warn(
                                "ADD_SPLIT amount '{}' is not a valid number; skipping", amountStr);
                    }
                }
            }
            case SKIP_TRANSACTION -> {
                // RULE_SKIP: is a blocking annotation — not in INFO_PREFIXES.
                // hasErrors() will return true for this transaction.
                tx.addValidationError("RULE_SKIP: Transaction marked for skipping by rule");
                log.debug("SKIP_TRANSACTION applied");
            }
            default -> log.warn("Unknown action type: {}", action.getActionType());
        }
    }

    // -----------------------------------------------------------------------
    // Private utility
    // -----------------------------------------------------------------------

    /**
     * Loads a rule that belongs to the given user, throwing {@link
     * TransactionRuleNotFoundException} if not found or not owned. Requirement: REQ-TR-NFR-3
     */
    private TransactionRule findOwnedRule(Long id, Long userId) {
        return transactionRuleRepository
                .findByIdAndUserId(id, userId)
                .orElseThrow(() -> new TransactionRuleNotFoundException(id));
    }
}
