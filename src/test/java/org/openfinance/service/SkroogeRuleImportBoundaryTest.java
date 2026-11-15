package org.openfinance.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.openfinance.dto.SkroogeImportMetadata;
import org.openfinance.dto.TransactionRuleRequest;
import org.openfinance.dto.TransactionRuleResponse;
import org.openfinance.entity.Category;
import org.openfinance.entity.RuleActionType;
import org.openfinance.repository.CategoryRepository;

@ExtendWith(MockitoExtension.class)
@DisplayName("Imported rules preserve user priority and resolve only owned category hierarchies")
class SkroogeRuleImportBoundaryTest {
    @Mock TransactionRuleService rules;
    @Mock CategoryRepository categories;
    @InjectMocks SkroogeRuleImportServiceImpl service;

    @ParameterizedTest
    @NullSource
    @ValueSource(ints = {0, Integer.MAX_VALUE})
    void importedRulesFollowExistingRulesWithoutPriorityOverflow(Integer priority) {
        when(rules.getRulesForUser(3L))
                .thenReturn(
                        List.of(
                                TransactionRuleResponse.builder()
                                        .name("Existing")
                                        .priority(priority)
                                        .build()));
        SkroogeImportMetadata metadata = metadata(RuleActionType.SET_PAYEE, "Merchant");
        service.importRules(metadata, Map.of(), 3L);
        ArgumentCaptor<TransactionRuleRequest> captured =
                ArgumentCaptor.forClass(TransactionRuleRequest.class);
        verify(rules).createRule(eq(3L), captured.capture());
        assertThat(captured.getValue().getPriority())
                .isEqualTo(
                        priority == null
                                ? 1
                                : priority == Integer.MAX_VALUE ? Integer.MAX_VALUE : priority + 1);
        assertThat(captured.getValue().getActions().getFirst().getActionValue())
                .isEqualTo("Merchant");
        assertThat(metadata.getImportedRuleCount()).isEqualTo(1);
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "missing-mapping",
                "missing-category",
                "foreign-parent",
                "cycle",
                "unknown-path"
            })
    void unresolvedOrCyclicCategoryPathsNeverCreatePartialRules(String reason) {
        SkroogeImportMetadata metadata =
                metadata(
                        RuleActionType.SET_CATEGORY,
                        reason.equals("unknown-path") ? "Other" : "Source:Food");
        Category category =
                Category.builder()
                        .id(10L)
                        .name("Food")
                        .parentId(reason.equals("cycle") ? 10L : 99L)
                        .build();
        when(categories.findByUserId(3L))
                .thenReturn(reason.equals("missing-category") ? List.of() : List.of(category));
        service.importRules(
                metadata, reason.equals("missing-mapping") ? Map.of() : Map.of(1L, 10L), 3L);
        verify(rules, never()).createRule(any(), any());
        assertThat(metadata.getImportedRuleCount()).isZero();
        assertThat(metadata.getRuleWarnings()).hasSize(1);
    }

    @Test
    void remappingUsesTheOwnedCategoryPathInsteadOfTheSourceName() {
        SkroogeImportMetadata metadata = metadata(RuleActionType.SET_CATEGORY, "source:food");
        when(categories.findByUserId(3L))
                .thenReturn(
                        List.of(
                                Category.builder().id(10L).name("Meals").parentId(11L).build(),
                                Category.builder().id(11L).name("Living").build()));
        service.importRules(metadata, Map.of(1L, 10L), 3L);
        ArgumentCaptor<TransactionRuleRequest> captured =
                ArgumentCaptor.forClass(TransactionRuleRequest.class);
        verify(rules).createRule(eq(3L), captured.capture());
        assertThat(captured.getValue().getActions().getFirst().getActionValue())
                .isEqualTo("Living:Meals");
    }

    @Test
    void duplicateNamesWithinTheFileAndAlreadySavedRulesAreNotOverwritten() {
        SkroogeImportMetadata metadata = metadata(RuleActionType.SET_PAYEE, "Merchant");
        metadata.setRules(List.of(metadata.getRules().getFirst(), metadata.getRules().getFirst()));
        when(rules.getRulesForUser(3L))
                .thenReturn(
                        List.of(
                                TransactionRuleResponse.builder()
                                        .name("Imported")
                                        .priority(1)
                                        .build()));
        service.importRules(metadata, Map.of(), 3L);
        verify(rules, never()).createRule(any(), any());
        assertThat(metadata.getExistingRuleCount()).isEqualTo(2);
        assertThat(metadata.getImportedRuleCount()).isZero();
    }

    private SkroogeImportMetadata metadata(RuleActionType action, String value) {
        return SkroogeImportMetadata.builder()
                .categories(
                        List.of(
                                SkroogeImportMetadata.SkroogeCategory.builder()
                                        .sourceId(1L)
                                        .fullName("Source:Food")
                                        .name("Food")
                                        .build()))
                .rules(
                        List.of(
                                TransactionRuleRequest.builder()
                                        .name("Imported")
                                        .priority(0)
                                        .isEnabled(true)
                                        .conditions(List.of())
                                        .actions(
                                                List.of(
                                                        TransactionRuleRequest.ActionRequest
                                                                .builder()
                                                                .actionType(action)
                                                                .actionValue(value)
                                                                .sortOrder(0)
                                                                .build()))
                                        .build()))
                .build();
    }
}
