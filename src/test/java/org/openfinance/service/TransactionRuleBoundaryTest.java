package org.openfinance.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.openfinance.dto.ImportedTransaction;
import org.openfinance.dto.TransactionRuleRequest;
import org.openfinance.entity.RuleActionType;
import org.openfinance.entity.RuleConditionField;
import org.openfinance.entity.RuleConditionOperator;
import org.openfinance.entity.TransactionRule;
import org.openfinance.entity.TransactionRuleAction;
import org.openfinance.entity.TransactionRuleCondition;
import org.openfinance.mapper.TransactionRuleMapper;
import org.openfinance.repository.TransactionRuleRepository;

@ExtendWith(MockitoExtension.class)
@DisplayName("Import rules preserve boundary amounts, deterministic matching and edit defaults")
class TransactionRuleBoundaryTest {
    @Mock private TransactionRuleRepository repository;
    @Mock private OperationHistoryService history;
    @Mock private TransactionRuleMapper mapper;
    @InjectMocks private TransactionRuleServiceImpl service;

    private TransactionRule rule(
            RuleConditionField field, RuleConditionOperator operator, String value) {
        return TransactionRule.builder()
                .id(1L)
                .userId(7L)
                .name("Rule")
                .priority(4)
                .isEnabled(true)
                .conditions(
                        new ArrayList<>(
                                List.of(
                                        TransactionRuleCondition.builder()
                                                .field(field)
                                                .operator(operator)
                                                .value(value)
                                                .build())))
                .actions(
                        new ArrayList<>(
                                List.of(
                                        TransactionRuleAction.builder()
                                                .actionType(RuleActionType.SET_CATEGORY)
                                                .actionValue("Matched")
                                                .build())))
                .build();
    }

    private boolean apply(TransactionRule rule, ImportedTransaction transaction) {
        when(repository.findByUserIdAndIsEnabledTrueOrderByPriorityAscCreatedAtAsc(7L))
                .thenReturn(List.of(rule));
        return !service.applyRules(List.of(transaction), 7L).isEmpty();
    }

    @ParameterizedTest
    @CsvSource({
        "false,PAYEE,EQUALS,FACTURE CARTE,true",
        "true,PAYEE,EQUALS,DU 071026 UBER,true",
        "false,PAYEE,CONTAINS,uber,false",
        "true,PAYEE,NOT_CONTAINS,uber,false",
        "true,PAYEE,NOT_EQUALS,FACTURE CARTE,true",
        "false,PAYEE,NOT_EQUALS,FACTURE CARTE,false",
        "true,PAYEE,NOT_EQUALS,DU 071026 UBER,false",
        "true,MEMO,EQUALS,DU 071026 UBER,true",
        "false,MEMO,CONTAINS,FACTURE CARTE,false",
        "false,DESCRIPTION,EQUALS,FACTURE CARTE DU 071026 UBER,true",
        "true,DESCRIPTION,EQUALS,DU 071026 UBER DU 071026 UBER,true"
    })
    void statementLabelsAndEarlierParserValuesRemainUsableInRules(
            boolean legacy,
            RuleConditionField field,
            RuleConditionOperator operator,
            String expected,
            boolean matches) {
        ImportedTransaction row =
                ImportedTransaction.builder()
                        .originalPayee("FACTURE CARTE")
                        .payee("FACTURE CARTE")
                        .memo("DU 071026 UBER")
                        .legacyRuleText(
                                new ImportedTransaction.RuleText(
                                        "DU 071026 UBER", "DU 071026 UBER"))
                        .build();
        TransactionRule rule = rule(field, operator, expected);
        rule.setUseLegacyImportText(legacy);
        rule.setActions(
                List.of(
                        TransactionRuleAction.builder()
                                .actionType(RuleActionType.SET_PAYEE)
                                .actionValue("Uber")
                                .build(),
                        TransactionRuleAction.builder()
                                .actionType(RuleActionType.SET_DESCRIPTION)
                                .actionValue("Dinner")
                                .build()));
        assertThat(apply(rule, row)).isEqualTo(matches);
        assertThat(row.getPayee()).isEqualTo(matches ? "Uber" : "FACTURE CARTE");
        assertThat(row.getMemo()).isEqualTo(matches ? "Dinner" : "DU 071026 UBER");
        assertThat(row.statementDescription()).isEqualTo("FACTURE CARTE");
    }

    @ParameterizedTest
    @CsvSource({
        "EQUALS,9.99,false",
        "EQUALS,10,true",
        "EQUALS,10.01,false",
        "NOT_EQUALS,9.99,true",
        "NOT_EQUALS,10,false",
        "NOT_EQUALS,10.01,true",
        "GREATER_THAN,9.99,false",
        "GREATER_THAN,10,false",
        "GREATER_THAN,10.01,true",
        "LESS_THAN,9.99,true",
        "LESS_THAN,10,false",
        "LESS_THAN,10.01,false",
        "GREATER_OR_EQUAL,9.99,false",
        "GREATER_OR_EQUAL,10,true",
        "GREATER_OR_EQUAL,10.01,true",
        "LESS_OR_EQUAL,9.99,true",
        "LESS_OR_EQUAL,10,true",
        "LESS_OR_EQUAL,10.01,false"
    })
    void comparesAbsoluteAmountsAtEachThresholdWithoutRounding(
            RuleConditionOperator operator, String value, boolean matches) {
        ImportedTransaction transaction =
                ImportedTransaction.builder().amount(new BigDecimal(value).negate()).build();
        assertThat(apply(rule(RuleConditionField.AMOUNT, operator, " 10.000 "), transaction))
                .isEqualTo(matches);
        assertThat(transaction.getAmount()).isEqualByComparingTo(new BigDecimal(value).negate());
        assertThat(transaction.getCategory()).isEqualTo(matches ? "Matched" : null);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "not a number", "NaN"})
    void incompleteNumericConditionsDoNotAbortAnImport(String value) {
        ImportedTransaction transaction =
                ImportedTransaction.builder().amount(BigDecimal.TEN).build();
        assertThat(
                        apply(
                                rule(
                                        RuleConditionField.AMOUNT,
                                        RuleConditionOperator.EQUALS,
                                        value),
                                transaction))
                .isFalse();
        assertThat(transaction.getCategory()).isNull();
        assertThat(transaction.getAmount()).isEqualByComparingTo("10");
    }

    @Test
    void aMissingTransactionAmountCannotSatisfyANumericCondition() {
        ImportedTransaction transaction = ImportedTransaction.builder().build();
        assertThat(
                        apply(
                                rule(
                                        RuleConditionField.AMOUNT,
                                        RuleConditionOperator.NOT_EQUALS,
                                        "10"),
                                transaction))
                .isFalse();
        assertThat(transaction.getCategory()).isNull();
    }

    @ParameterizedTest
    @EnumSource(
            value = RuleConditionField.class,
            names = {"PAYEE", "MEMO", "DESCRIPTION"})
    void matchingIsIndependentOfTheServersDefaultLocale(RuleConditionField field) {
        Locale original = Locale.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"));
            ImportedTransaction transaction =
                    ImportedTransaction.builder()
                            .payee(field == RuleConditionField.MEMO ? null : "ITEM")
                            .memo(field == RuleConditionField.MEMO ? "ITEM" : null)
                            .build();
            assertThat(apply(rule(field, RuleConditionOperator.EQUALS, "item"), transaction))
                    .isTrue();
            assertThat(transaction.getCategory()).isEqualTo("Matched");
        } finally {
            Locale.setDefault(original);
        }
    }

    @ParameterizedTest
    @CsvSource(
            value = {
                "NULL,memo,memo",
                "   ,memo,memo",
                "payee,NULL,payee",
                "payee,   ,payee",
                " payee , memo ,payee memo"
            },
            nullValues = "NULL")
    void combinedDescriptionTrimsOptionalPartsWithoutInventingWhitespace(
            String payee, String memo, String description) {
        ImportedTransaction transaction =
                ImportedTransaction.builder().payee(payee).memo(memo).build();
        assertThat(
                        apply(
                                rule(
                                        RuleConditionField.DESCRIPTION,
                                        RuleConditionOperator.EQUALS,
                                        description),
                                transaction))
                .isTrue();
        assertThat(transaction.getPayee()).isEqualTo(payee);
        assertThat(transaction.getMemo()).isEqualTo(memo);
    }

    @ParameterizedTest
    @CsvSource(
            value = {
                "NULL,DEBIT,true",
                "0,CREDIT,true",
                "10, income ,true",
                "-10, expense ,true",
                "10,DEBIT,false",
                "-10,NULL,false"
            },
            nullValues = "NULL")
    void transactionTypeUsesCashDirectionAndAcceptsItsNamedAliases(
            String amount, String expected, boolean matches) {
        ImportedTransaction transaction =
                ImportedTransaction.builder()
                        .amount(amount == null ? null : new BigDecimal(amount))
                        .build();
        assertThat(
                        apply(
                                rule(
                                        RuleConditionField.TRANSACTION_TYPE,
                                        RuleConditionOperator.EQUALS,
                                        expected),
                                transaction))
                .isEqualTo(matches);
    }

    @ParameterizedTest
    @ValueSource(strings = {"missing-operator", "unsupported-operator", "duplicate-warning"})
    void invalidConditionsAreReportedBeforeOrLogicCanShortCircuit(String defect) {
        TransactionRule rule =
                rule(RuleConditionField.PAYEE, RuleConditionOperator.EQUALS, "Payee");
        rule.setConditionMatch("OR");
        rule.getConditions()
                .add(
                        TransactionRuleCondition.builder()
                                .field(RuleConditionField.MEMO)
                                .operator(
                                        defect.equals("missing-operator")
                                                ? null
                                                : RuleConditionOperator.GREATER_THAN)
                                .value("1")
                                .build());
        ImportedTransaction transaction = ImportedTransaction.builder().payee("Payee").build();
        if (defect.equals("duplicate-warning"))
            transaction.addValidationError("RULE_CONDITION_INVALID: Rule");
        assertThat(apply(rule, transaction)).isFalse();
        assertThat(transaction.getValidationErrors())
                .containsExactly("RULE_CONDITION_INVALID: Rule");
        assertThat(transaction.getCategory()).isNull();
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void aRuleWithNoConditionsCannotMatchEveryImportedTransaction(boolean nullList) {
        TransactionRule rule =
                rule(RuleConditionField.PAYEE, RuleConditionOperator.EQUALS, "Payee");
        rule.setConditions(nullList ? null : List.of());
        assertThat(apply(rule, ImportedTransaction.builder().payee("Payee").build())).isFalse();
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void addingTagsDeduplicatesCaseAndWhitespaceWithoutLosingExistingOrder(boolean hasTags) {
        TransactionRule rule =
                rule(RuleConditionField.PAYEE, RuleConditionOperator.EQUALS, "Payee");
        rule.setActions(
                List.of(
                        TransactionRuleAction.builder()
                                .actionType(RuleActionType.ADD_TAG)
                                .actionValue(" food, ,FOOD,travel, ")
                                .build()));
        ImportedTransaction transaction =
                ImportedTransaction.builder()
                        .payee("Payee")
                        .tags(hasTags ? new ArrayList<>(List.of(" Food ", "Existing")) : null)
                        .build();
        assertThat(apply(rule, transaction)).isTrue();
        assertThat(transaction.getTags())
                .containsExactlyElementsOf(
                        hasTags
                                ? List.of(" Food ", "Existing", "travel")
                                : List.of("food", "travel"));
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(booleans = {false, true})
    void togglingTreatsOnlyExplicitTrueAsEnabled(Boolean enabled) {
        TransactionRule rule =
                rule(RuleConditionField.PAYEE, RuleConditionOperator.EQUALS, "Payee");
        rule.setIsEnabled(enabled);
        when(repository.findByIdAndUserId(1L, 7L)).thenReturn(Optional.of(rule));
        when(repository.save(rule)).thenReturn(rule);
        service.toggleRule(1L, 7L);
        assertThat(rule.getIsEnabled()).isEqualTo(!Boolean.TRUE.equals(enabled));
        verify(repository).save(rule);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void updatesReplaceChildRowsAndPreserveOmittedPriorityAndEnabledState(boolean explicit) {
        TransactionRule existing =
                rule(RuleConditionField.PAYEE, RuleConditionOperator.EQUALS, "Old");
        when(repository.findByIdAndUserId(1L, 7L)).thenReturn(Optional.of(existing));
        when(repository.save(existing)).thenReturn(existing);
        TransactionRuleRequest request =
                TransactionRuleRequest.builder()
                        .name("Edited")
                        .priority(explicit ? 9 : null)
                        .isEnabled(explicit ? false : null)
                        .conditionMatch(explicit ? "OR" : null)
                        .conditions(
                                List.of(
                                        TransactionRuleRequest.ConditionRequest.builder()
                                                .field(RuleConditionField.MEMO)
                                                .operator(RuleConditionOperator.CONTAINS)
                                                .value("new")
                                                .sortOrder(explicit ? 2 : null)
                                                .build()))
                        .actions(
                                List.of(
                                        TransactionRuleRequest.ActionRequest.builder()
                                                .actionType(RuleActionType.ADD_SPLIT)
                                                .actionValue("Category")
                                                .actionValue2("1.01")
                                                .actionValue3("Split memo")
                                                .sortOrder(explicit ? 3 : null)
                                                .build()))
                        .build();
        service.updateRule(1L, 7L, request);
        assertThat(existing.getName()).isEqualTo("Edited");
        assertThat(existing.getPriority()).isEqualTo(explicit ? 9 : 4);
        assertThat(existing.getIsEnabled()).isEqualTo(!explicit);
        assertThat(existing.getConditionMatch()).isEqualTo(explicit ? "OR" : "AND");
        assertThat(existing.getConditions())
                .singleElement()
                .satisfies(
                        condition -> {
                            assertThat(condition.getField()).isEqualTo(RuleConditionField.MEMO);
                            assertThat(condition.getValue()).isEqualTo("new");
                            assertThat(condition.getSortOrder()).isEqualTo(explicit ? 2 : 0);
                        });
        assertThat(existing.getActions())
                .singleElement()
                .satisfies(
                        action -> {
                            assertThat(action.getActionValue2()).isEqualTo("1.01");
                            assertThat(action.getActionValue3()).isEqualTo("Split memo");
                            assertThat(action.getSortOrder()).isEqualTo(explicit ? 3 : 0);
                        });
    }
}
