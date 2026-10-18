package org.openfinance.service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.openfinance.dto.SkroogeImportMetadata;
import org.openfinance.dto.TransactionRuleRequest;
import org.openfinance.dto.TransactionRuleRequest.ActionRequest;
import org.openfinance.dto.TransactionRuleResponse;
import org.openfinance.entity.Category;
import org.openfinance.entity.RuleActionType;
import org.openfinance.repository.CategoryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class SkroogeRuleImportServiceImpl implements SkroogeRuleImportService {
    private final TransactionRuleService rules;
    private final CategoryRepository categories;

    @Override
    public void importRules(
            SkroogeImportMetadata metadata, Map<Long, Long> categoryIdsBySource, Long userId) {
        List<TransactionRuleResponse> existing = rules.getRulesForUser(userId);
        Set<String> names =
                existing.stream().map(TransactionRuleResponse::getName).collect(Collectors.toSet());
        int priority =
                existing.stream()
                        .mapToInt(rule -> rule.getPriority() == null ? 0 : rule.getPriority())
                        .max()
                        .orElse(-1);
        Map<Long, Category> owned =
                categories.findByUserId(userId).stream()
                        .collect(Collectors.toMap(Category::getId, c -> c));
        for (TransactionRuleRequest source : metadata.getRules()) {
            if (!names.add(source.getName())) {
                metadata.setExistingRuleCount(metadata.getExistingRuleCount() + 1);
                continue;
            }
            List<ActionRequest> actions =
                    remapActions(source, metadata, categoryIdsBySource, owned);
            if (actions == null) {
                metadata.getRuleWarnings()
                        .add(new SkroogeImportMetadata.RuleWarning(null, "unresolvedCategory"));
                continue;
            }
            // Keep existing user rules ahead of newly imported rules. Reimports never overwrite
            // edits.
            if (priority < Integer.MAX_VALUE) priority++;
            rules.createRule(
                    userId,
                    TransactionRuleRequest.builder()
                            .name(source.getName())
                            .priority(priority)
                            .isEnabled(source.getIsEnabled())
                            .conditionMatch(source.getConditionMatch())
                            .conditions(source.getConditions())
                            .actions(actions)
                            .build());
            metadata.setImportedRuleCount(metadata.getImportedRuleCount() + 1);
        }
    }

    private List<ActionRequest> remapActions(
            TransactionRuleRequest source,
            SkroogeImportMetadata metadata,
            Map<Long, Long> ids,
            Map<Long, Category> owned) {
        List<ActionRequest> actions = new ArrayList<>();
        for (ActionRequest action : source.getActions()) {
            String value = action.getActionValue();
            if (action.getActionType() == RuleActionType.SET_CATEGORY) {
                String sourcePath = value;
                Long target =
                        metadata.getCategories().stream()
                                .filter(
                                        category ->
                                                sourcePath.equalsIgnoreCase(category.getFullName()))
                                .map(category -> ids.get(category.getSourceId()))
                                .filter(java.util.Objects::nonNull)
                                .findFirst()
                                .orElse(null);
                value = path(target, owned);
                if (value == null) return null;
            }
            actions.add(
                    ActionRequest.builder()
                            .actionType(action.getActionType())
                            .actionValue(value)
                            .actionValue2(action.getActionValue2())
                            .actionValue3(action.getActionValue3())
                            .sortOrder(action.getSortOrder())
                            .build());
        }
        return actions;
    }

    private String path(Long id, Map<Long, Category> owned) {
        List<String> names = new ArrayList<>();
        Set<Long> seen = new HashSet<>();
        while (id != null) {
            Category category = owned.get(id);
            if (category == null || !seen.add(id)) return null;
            names.add(0, category.getName());
            id = category.getParentId();
        }
        return names.isEmpty() ? null : String.join(":", names);
    }
}
