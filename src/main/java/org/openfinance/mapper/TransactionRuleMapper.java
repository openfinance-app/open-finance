package org.openfinance.mapper;

import java.util.List;
import java.util.stream.Collectors;
import org.openfinance.dto.TransactionRuleRequest;
import org.openfinance.dto.TransactionRuleResponse;
import org.openfinance.entity.TransactionRule;
import org.openfinance.entity.TransactionRuleAction;
import org.openfinance.entity.TransactionRuleCondition;
import org.springframework.stereotype.Component;

/**
 * Mapper for converting between {@link TransactionRule} entities and their request / response DTOs.
 *
 * <p>This mapper is a hand-written Spring component following the same pattern used by {@link
 * AccountMapper}, {@link BudgetMapper}, etc. in this project.
 *
 * <p><strong>Requirement:</strong> design.md mapper pattern, REQ-TR-5.1
 */
@Component
public class TransactionRuleMapper {

    /**
     * Converts a {@link TransactionRule} entity to a {@link TransactionRuleResponse} DTO.
     *
     * @param entity the persisted rule entity
     * @return the response DTO (never {@code null})
     */
    public TransactionRuleResponse toResponse(TransactionRule entity) {
        if (entity == null) {
            throw new IllegalArgumentException("TransactionRule entity must not be null");
        }

        List<TransactionRuleResponse.ConditionResponse> conditionResponses =
                entity.getConditions() == null
                        ? List.of()
                        : entity.getConditions().stream()
                                .map(this::toConditionResponse)
                                .collect(Collectors.toList());

        List<TransactionRuleResponse.ActionResponse> actionResponses =
                entity.getActions() == null
                        ? List.of()
                        : entity.getActions().stream()
                                .map(this::toActionResponse)
                                .collect(Collectors.toList());

        return TransactionRuleResponse.builder()
                .id(entity.getId())
                .name(entity.getName())
                .priority(entity.getPriority())
                .isEnabled(entity.getIsEnabled())
                .conditionMatch(
                        entity.getConditionMatch() != null ? entity.getConditionMatch() : "AND")
                .conditions(conditionResponses)
                .actions(actionResponses)
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }

    /**
     * Creates a new {@link TransactionRule} entity from a user ID and a {@link
     * TransactionRuleRequest} DTO.
     *
     * <p>The returned entity has no {@code id} or timestamps — those are set by JPA lifecycle
     * callbacks on persist.
     *
     * @param userId the ID of the owning user
     * @param request the create/update request
     * @return a new (un-persisted) entity
     */
    public TransactionRule toEntity(Long userId, TransactionRuleRequest request) {
        if (userId == null) {
            throw new IllegalArgumentException("userId must not be null");
        }
        if (request == null) {
            throw new IllegalArgumentException("TransactionRuleRequest must not be null");
        }

        List<TransactionRuleCondition> conditions =
                request.getConditions() == null
                        ? List.of()
                        : request.getConditions().stream()
                                .map(this::toConditionEntity)
                                .collect(Collectors.toList());

        List<TransactionRuleAction> actions =
                request.getActions() == null
                        ? List.of()
                        : request.getActions().stream()
                                .map(this::toActionEntity)
                                .collect(Collectors.toList());

        return TransactionRule.builder()
                .userId(userId)
                .name(request.getName())
                .priority(request.getPriority() != null ? request.getPriority() : 0)
                .isEnabled(request.getIsEnabled() != null ? request.getIsEnabled() : true)
                .conditionMatch(
                        request.getConditionMatch() != null ? request.getConditionMatch() : "AND")
                .conditions(conditions)
                .actions(actions)
                .build();
    }

    // -----------------------------------------------------------------------
    // Private helpers
    // -----------------------------------------------------------------------

    private TransactionRuleResponse.ConditionResponse toConditionResponse(
            TransactionRuleCondition c) {
        return TransactionRuleResponse.ConditionResponse.builder()
                .id(c.getId())
                .field(c.getField())
                .operator(c.getOperator())
                .value(c.getValue())
                .sortOrder(c.getSortOrder())
                .build();
    }

    private TransactionRuleResponse.ActionResponse toActionResponse(TransactionRuleAction a) {
        return TransactionRuleResponse.ActionResponse.builder()
                .id(a.getId())
                .actionType(a.getActionType())
                .actionValue(a.getActionValue())
                .actionValue2(a.getActionValue2())
                .actionValue3(a.getActionValue3())
                .sortOrder(a.getSortOrder())
                .build();
    }

    private TransactionRuleCondition toConditionEntity(
            TransactionRuleRequest.ConditionRequest req) {
        return TransactionRuleCondition.builder()
                .field(req.getField())
                .operator(req.getOperator())
                .value(req.getValue())
                .sortOrder(req.getSortOrder() != null ? req.getSortOrder() : 0)
                .build();
    }

    private TransactionRuleAction toActionEntity(TransactionRuleRequest.ActionRequest req) {
        return TransactionRuleAction.builder()
                .actionType(req.getActionType())
                .actionValue(req.getActionValue())
                .actionValue2(req.getActionValue2())
                .actionValue3(req.getActionValue3())
                .sortOrder(req.getSortOrder() != null ? req.getSortOrder() : 0)
                .build();
    }
}
