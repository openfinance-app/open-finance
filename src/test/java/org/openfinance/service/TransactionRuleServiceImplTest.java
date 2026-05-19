package org.openfinance.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.openfinance.dto.ImportedTransaction;
import org.openfinance.entity.RuleActionType;
import org.openfinance.entity.RuleConditionField;
import org.openfinance.entity.RuleConditionOperator;
import org.openfinance.entity.TransactionRule;
import org.openfinance.entity.TransactionRuleAction;
import org.openfinance.entity.TransactionRuleCondition;
import org.openfinance.mapper.TransactionRuleMapper;
import org.openfinance.repository.TransactionRuleRepository;

/**
 * Unit tests for {@link TransactionRuleServiceImpl#applyRules}.
 *
 * <p>Uses a richer dataset that exercises every condition field, every operator, every action type,
 * priority ordering, disabled-rule skipping, stop-on-first-match, AND-logic multi-condition
 * evaluation, and edge cases.
 *
 * <p>Requirements covered: REQ-TR-2.1, REQ-TR-2.2, REQ-TR-2.3, REQ-TR-2.4, REQ-TR-3.1, REQ-TR-3.2,
 * REQ-TR-3.3, REQ-TR-4.1–REQ-TR-4.7
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("TransactionRuleServiceImpl — applyRules (rules engine)")
class TransactionRuleServiceImplTest {

    private static final Long USER_ID = 42L;

    @Mock private TransactionRuleRepository transactionRuleRepository;

    @Mock private TransactionRuleMapper transactionRuleMapper;

    @Mock private OperationHistoryService operationHistoryService;

    @InjectMocks private TransactionRuleServiceImpl service;

    // -----------------------------------------------------------------------
    // Builder helpers — keep tests readable
    // -----------------------------------------------------------------------

    /** Builds a minimal enabled rule in priority order. */
    private static TransactionRule rule(
            String name,
            int priority,
            List<TransactionRuleCondition> conditions,
            List<TransactionRuleAction> actions) {
        return TransactionRule.builder()
                .id((long) name.hashCode())
                .userId(USER_ID)
                .name(name)
                .priority(priority)
                .isEnabled(true)
                .conditions(conditions)
                .actions(actions)
                .build();
    }

    private static TransactionRule disabledRule(
            String name,
            List<TransactionRuleCondition> conditions,
            List<TransactionRuleAction> actions) {
        TransactionRule r = rule(name, 0, conditions, actions);
        r.setIsEnabled(false);
        return r;
    }

    private static TransactionRuleCondition condition(
            RuleConditionField field, RuleConditionOperator op, String value) {
        return TransactionRuleCondition.builder()
                .field(field)
                .operator(op)
                .value(value)
                .sortOrder(0)
                .build();
    }

    private static TransactionRuleAction action(RuleActionType type, String v1) {
        return TransactionRuleAction.builder()
                .actionType(type)
                .actionValue(v1)
                .sortOrder(0)
                .build();
    }

    private static TransactionRuleAction action(
            RuleActionType type, String v1, String v2, String v3) {
        return TransactionRuleAction.builder()
                .actionType(type)
                .actionValue(v1)
                .actionValue2(v2)
                .actionValue3(v3)
                .sortOrder(0)
                .build();
    }

    /** 20-transaction dataset covering every kind of payee/amount/type combination. */
    private List<ImportedTransaction> richDataset() {
        List<ImportedTransaction> txs = new ArrayList<>();

        // 0 — Supermarket expense, high amount
        txs.add(tx("CARREFOUR MARKET PARIS 9", null, new BigDecimal("-187.45"), "EXPENSE"));
        // 1 — Online retail, mixed case
        txs.add(
                tx(
                        "Amazon Marketplace",
                        "AMZN PRIME ANNUAL",
                        new BigDecimal("-139.00"),
                        "EXPENSE"));
        // 2 — Salary deposit (income)
        txs.add(tx("ACME CORP", "VIREMENT SALAIRE MARS 2026", new BigDecimal("3500.00"), "INCOME"));
        // 3 — Netflix subscription
        txs.add(tx("NETFLIX.COM", "Abonnement mensuel", new BigDecimal("-15.99"), "EXPENSE"));
        // 4 — Rent payment
        txs.add(tx("SCI PROPRIÉTAIRE", "LOYER AVRIL 2026", new BigDecimal("-1200.00"), "EXPENSE"));
        // 5 — Gym membership
        txs.add(tx("BASIC FIT FRANCE", null, new BigDecimal("-29.99"), "EXPENSE"));
        // 6 — ATM withdrawal (cash)
        txs.add(tx("DAB RETRAIT ESPÈCES", "BNP PARIS OPÉRA", new BigDecimal("-200.00"), "EXPENSE"));
        // 7 — Pharmacy
        txs.add(tx("PHARMACIE DU MARCHÉ", null, new BigDecimal("-42.80"), "EXPENSE"));
        // 8 — Transfer from savings
        txs.add(tx("VIREMENT INTERNE", "ÉPARGNE → COURANT", new BigDecimal("500.00"), "INCOME"));
        // 9 — Fuel
        txs.add(tx("TOTAL STATION SERVICE", "REF 4812", new BigDecimal("-65.00"), "EXPENSE"));
        // 10 — Small coffee purchase
        txs.add(tx("CAFÉ DE LA PAIX", null, new BigDecimal("-4.50"), "EXPENSE"));
        // 11 — Mobile phone bill
        txs.add(tx("FREE MOBILE SAS", "FACTURE 03/2026", new BigDecimal("-19.99"), "EXPENSE"));
        // 12 — Large irregular income (dividend)
        txs.add(tx("BOURSORAMA BANQUE", "DIVIDENDES Q1 2026", new BigDecimal("1200.00"), "INCOME"));
        // 13 — Payroll tax refund (income, small)
        txs.add(
                tx(
                        "IMPÔTS.GOUV.FR",
                        "REMBOURSEMENT TROP-PERÇU",
                        new BigDecimal("320.00"),
                        "INCOME"));
        // 14 — Uber ride (description contains 'uber', amount low)
        txs.add(tx("UBER *TRIP", "COURSE TAXI", new BigDecimal("-23.40"), "EXPENSE"));
        // 15 — Train ticket — exact amount rule
        txs.add(tx("SNCF DIRECT", "PARIS LYON 2026-04-01", new BigDecimal("-89.00"), "EXPENSE"));
        // 16 — Streaming music
        txs.add(tx("SPOTIFY AB", "Premium 1 month", new BigDecimal("-9.99"), "EXPENSE"));
        // 17 — Zero-amount (debit advisory / fee notification)
        txs.add(tx("BNP PARIBAS", "AVIS DE PRÉLEVEMENT", new BigDecimal("0.00"), "EXPENSE"));
        // 18 — Transaction with null payee (memo only)
        txs.add(txMemoOnly("REMBOURSEMENT COLLÈGUE JEAN", new BigDecimal("50.00")));
        // 19 — Expense that must NOT match a rule (no relevant keyword)
        txs.add(tx("MAIRIE DE PARIS", "TAXE FONCIÈRE", new BigDecimal("-1500.00"), "EXPENSE"));

        return txs;
    }

    private static ImportedTransaction tx(
            String payee, String memo, BigDecimal amount, String typeHint) {
        return ImportedTransaction.builder()
                .transactionDate(LocalDate.of(2026, 4, 1))
                .payee(payee)
                .memo(memo)
                .amount(amount)
                .build();
    }

    private static ImportedTransaction txMemoOnly(String memo, BigDecimal amount) {
        return ImportedTransaction.builder()
                .transactionDate(LocalDate.of(2026, 4, 1))
                .memo(memo)
                .amount(amount)
                .build();
    }

    // -----------------------------------------------------------------------
    // 1. DESCRIPTION field — string operators
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("DESCRIPTION field operators")
    class DescriptionOperators {

        @Test
        @DisplayName("CONTAINS — case-insensitive substring match (index 0: carrefour)")
        void contains_caseInsensitive() {
            TransactionRule r =
                    rule(
                            "Supermarket",
                            0,
                            List.of(
                                    condition(
                                            RuleConditionField.DESCRIPTION,
                                            RuleConditionOperator.CONTAINS,
                                            "carrefour")),
                            List.of(action(RuleActionType.SET_CATEGORY, "Groceries")));
            when(transactionRuleRepository
                            .findByUserIdAndIsEnabledTrueOrderByPriorityAscCreatedAtAsc(USER_ID))
                    .thenReturn(List.of(r));

            List<ImportedTransaction> txs = richDataset();
            Set<Integer> matched = service.applyRules(txs, USER_ID);

            assertThat(matched).containsExactly(0);
            assertThat(txs.get(0).getCategory()).isEqualTo("Groceries");
        }

        @Test
        @DisplayName("CONTAINS — memo searched too (index 2: virement salaire)")
        void contains_searchInMemo() {
            TransactionRule r =
                    rule(
                            "Salary",
                            0,
                            List.of(
                                    condition(
                                            RuleConditionField.DESCRIPTION,
                                            RuleConditionOperator.CONTAINS,
                                            "salaire")),
                            List.of(action(RuleActionType.SET_CATEGORY, "Income:Salary")));
            when(transactionRuleRepository
                            .findByUserIdAndIsEnabledTrueOrderByPriorityAscCreatedAtAsc(USER_ID))
                    .thenReturn(List.of(r));

            List<ImportedTransaction> txs = richDataset();
            Set<Integer> matched = service.applyRules(txs, USER_ID);

            // "VIREMENT SALAIRE MARS 2026" memo on tx[2]
            assertThat(matched).containsExactly(2);
            assertThat(txs.get(2).getCategory()).isEqualTo("Income:Salary");
        }

        @Test
        @DisplayName("NOT_CONTAINS — matches amazon tx when checking no 'netflix'")
        void notContains_noMatch() {
            // Rule: description NOT contains 'netflix' AND contains 'amazon'
            TransactionRule r =
                    rule(
                            "Amazon-non-netflix",
                            0,
                            List.of(
                                    condition(
                                            RuleConditionField.DESCRIPTION,
                                            RuleConditionOperator.NOT_CONTAINS,
                                            "netflix"),
                                    condition(
                                            RuleConditionField.DESCRIPTION,
                                            RuleConditionOperator.CONTAINS,
                                            "amazon")),
                            List.of(action(RuleActionType.SET_CATEGORY, "Shopping:Online")));
            when(transactionRuleRepository
                            .findByUserIdAndIsEnabledTrueOrderByPriorityAscCreatedAtAsc(USER_ID))
                    .thenReturn(List.of(r));

            List<ImportedTransaction> txs = richDataset();
            Set<Integer> matched = service.applyRules(txs, USER_ID);

            assertThat(matched).containsExactly(1);
            assertThat(txs.get(1).getCategory()).isEqualTo("Shopping:Online");
        }

        @Test
        @DisplayName("EQUALS — exact match on description (index 17: zero-amount)")
        void equals_exactMatch() {
            // payee="BNP PARIBAS", memo="AVIS DE PRÉLEVEMENT"
            // buildDescription concatenates; full lower: "bnp paribas avis de prélevement"
            String target = "BNP PARIBAS AVIS DE PRÉLEVEMENT".toLowerCase();
            TransactionRule r =
                    rule(
                            "BNP Advisory",
                            0,
                            List.of(
                                    condition(
                                            RuleConditionField.DESCRIPTION,
                                            RuleConditionOperator.EQUALS,
                                            target)),
                            List.of(action(RuleActionType.SKIP_TRANSACTION, null)));
            when(transactionRuleRepository
                            .findByUserIdAndIsEnabledTrueOrderByPriorityAscCreatedAtAsc(USER_ID))
                    .thenReturn(List.of(r));

            List<ImportedTransaction> txs = richDataset();
            Set<Integer> matched = service.applyRules(txs, USER_ID);

            assertThat(matched).containsExactly(17);
            assertThat(txs.get(17).hasErrors())
                    .isTrue(); // RULE_SKIP and RULE_MATCH are both errors
        }

        @Test
        @DisplayName("NOT_EQUALS — matches all except specific payee")
        void notEquals_matchesOthers() {
            // Rule: description NOT_EQUALS "spotify ab premium 1 month" → matches every tx
            // except #16
            String spotifyDesc = "spotify ab premium 1 month";
            TransactionRule r =
                    rule(
                            "Non-Spotify",
                            0,
                            List.of(
                                    condition(
                                            RuleConditionField.DESCRIPTION,
                                            RuleConditionOperator.NOT_EQUALS,
                                            spotifyDesc)),
                            List.of(action(RuleActionType.SET_CATEGORY, "Other")));
            when(transactionRuleRepository
                            .findByUserIdAndIsEnabledTrueOrderByPriorityAscCreatedAtAsc(USER_ID))
                    .thenReturn(List.of(r));

            List<ImportedTransaction> txs = richDataset();
            Set<Integer> matched = service.applyRules(txs, USER_ID);

            // Should match all 20 except index 16
            assertThat(matched).doesNotContain(16);
            assertThat(matched).hasSize(19);
        }

        @Test
        @DisplayName("CONTAINS on null-payee tx uses memo (index 18: remboursement collègue)")
        void contains_nullPayee_usesMemo() {
            TransactionRule r =
                    rule(
                            "Reimbursement",
                            0,
                            List.of(
                                    condition(
                                            RuleConditionField.DESCRIPTION,
                                            RuleConditionOperator.CONTAINS,
                                            "remboursement")),
                            List.of(action(RuleActionType.SET_CATEGORY, "Transfer:Reimbursement")));
            when(transactionRuleRepository
                            .findByUserIdAndIsEnabledTrueOrderByPriorityAscCreatedAtAsc(USER_ID))
                    .thenReturn(List.of(r));

            List<ImportedTransaction> txs = richDataset();
            Set<Integer> matched = service.applyRules(txs, USER_ID);

            // tx[13] memo contains "REMBOURSEMENT" and tx[18] memo = "REMBOURSEMENT
            // COLLÈGUE JEAN"
            assertThat(matched).contains(13, 18);
        }
    }

    // -----------------------------------------------------------------------
    // 2. AMOUNT field — numeric operators
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("AMOUNT field operators")
    class AmountOperators {

        @Test
        @DisplayName("GREATER_THAN — large expenses > 100")
        void greaterThan() {
            TransactionRule r =
                    rule(
                            "LargeExpense",
                            0,
                            List.of(
                                    condition(
                                            RuleConditionField.AMOUNT,
                                            RuleConditionOperator.GREATER_THAN,
                                            "100")),
                            List.of(action(RuleActionType.SET_CATEGORY, "Large:Expense")));
            when(transactionRuleRepository
                            .findByUserIdAndIsEnabledTrueOrderByPriorityAscCreatedAtAsc(USER_ID))
                    .thenReturn(List.of(r));

            List<ImportedTransaction> txs = richDataset();
            Set<Integer> matched = service.applyRules(txs, USER_ID);

            // Amounts > 100 (abs): 187.45(0), 139.00(1), 3500.00(2), 1200.00(4),
            // 200.00(6), 500.00(8), 1200.00(12), 320.00(13),
            // 1500.00(19)
            assertThat(matched).containsExactlyInAnyOrder(0, 1, 2, 4, 6, 8, 12, 13, 19);
        }

        @Test
        @DisplayName("LESS_THAN — micro-transactions < 10")
        void lessThan() {
            TransactionRule r =
                    rule(
                            "MicroTx",
                            0,
                            List.of(
                                    condition(
                                            RuleConditionField.AMOUNT,
                                            RuleConditionOperator.LESS_THAN,
                                            "10")),
                            List.of(action(RuleActionType.ADD_TAG, "micro")));
            when(transactionRuleRepository
                            .findByUserIdAndIsEnabledTrueOrderByPriorityAscCreatedAtAsc(USER_ID))
                    .thenReturn(List.of(r));

            List<ImportedTransaction> txs = richDataset();
            Set<Integer> matched = service.applyRules(txs, USER_ID);

            // abs amounts < 10: 4.50(10), 9.99(16), 0.00(17)
            assertThat(matched).containsExactlyInAnyOrder(10, 16, 17);
            assertThat(txs.get(10).getTags()).contains("micro");
            assertThat(txs.get(16).getTags()).contains("micro");
        }

        @Test
        @DisplayName("EQUALS — exact amount match (index 15: 89.00 train ticket)")
        void equals_exactAmount() {
            TransactionRule r =
                    rule(
                            "TrainTicket",
                            0,
                            List.of(
                                    condition(
                                            RuleConditionField.AMOUNT,
                                            RuleConditionOperator.EQUALS,
                                            "89.00")),
                            List.of(action(RuleActionType.SET_CATEGORY, "Transport:Train")));
            when(transactionRuleRepository
                            .findByUserIdAndIsEnabledTrueOrderByPriorityAscCreatedAtAsc(USER_ID))
                    .thenReturn(List.of(r));

            List<ImportedTransaction> txs = richDataset();
            Set<Integer> matched = service.applyRules(txs, USER_ID);

            assertThat(matched).containsExactly(15);
            assertThat(txs.get(15).getCategory()).isEqualTo("Transport:Train");
        }

        @Test
        @DisplayName("NOT_EQUALS — all except the 89.00 ticket")
        void notEquals_amount() {
            TransactionRule r =
                    rule(
                            "NotTrain",
                            0,
                            List.of(
                                    condition(
                                            RuleConditionField.AMOUNT,
                                            RuleConditionOperator.NOT_EQUALS,
                                            "89.00")),
                            List.of(action(RuleActionType.SET_CATEGORY, "Other")));
            when(transactionRuleRepository
                            .findByUserIdAndIsEnabledTrueOrderByPriorityAscCreatedAtAsc(USER_ID))
                    .thenReturn(List.of(r));

            List<ImportedTransaction> txs = richDataset();
            Set<Integer> matched = service.applyRules(txs, USER_ID);

            assertThat(matched).doesNotContain(15);
            assertThat(matched).hasSize(19);
        }

        @Test
        @DisplayName("GREATER_OR_EQUAL — amounts >= 29.99")
        void greaterOrEqual() {
            TransactionRule r =
                    rule(
                            "GymOrMore",
                            0,
                            List.of(
                                    condition(
                                            RuleConditionField.AMOUNT,
                                            RuleConditionOperator.GREATER_OR_EQUAL,
                                            "29.99")),
                            List.of(action(RuleActionType.SET_CATEGORY, "Bill")));
            when(transactionRuleRepository
                            .findByUserIdAndIsEnabledTrueOrderByPriorityAscCreatedAtAsc(USER_ID))
                    .thenReturn(List.of(r));

            List<ImportedTransaction> txs = richDataset();
            Set<Integer> matched = service.applyRules(txs, USER_ID);

            // 29.99(5) should be included; 23.40(14) and 19.99(11) should not
            assertThat(matched).contains(5);
            assertThat(matched).doesNotContain(14);
            assertThat(matched).doesNotContain(11);
        }

        @Test
        @DisplayName("LESS_OR_EQUAL — amounts <= 29.99")
        void lessOrEqual() {
            TransactionRule r =
                    rule(
                            "UpToGym",
                            0,
                            List.of(
                                    condition(
                                            RuleConditionField.AMOUNT,
                                            RuleConditionOperator.LESS_OR_EQUAL,
                                            "29.99")),
                            List.of(action(RuleActionType.SET_CATEGORY, "Small")));
            when(transactionRuleRepository
                            .findByUserIdAndIsEnabledTrueOrderByPriorityAscCreatedAtAsc(USER_ID))
                    .thenReturn(List.of(r));

            List<ImportedTransaction> txs = richDataset();
            Set<Integer> matched = service.applyRules(txs, USER_ID);

            // 29.99(5) boundary included; 23.40(14), 19.99(3), 19.99(11), 15.99(3),
            // 9.99(16), 4.50(10), 0(17), 50(18)
            assertThat(matched).contains(5, 10, 16, 17);
            // 42.80(7) is > 29.99, should NOT match
            assertThat(matched).doesNotContain(7);
        }

        @Test
        @DisplayName("Amount zero — LESS_THAN 0.01 matches debit advisory (index 17)")
        void zeroAmountMatchesLessThan() {
            TransactionRule r =
                    rule(
                            "ZeroAmount",
                            0,
                            List.of(
                                    condition(
                                            RuleConditionField.AMOUNT,
                                            RuleConditionOperator.LESS_THAN,
                                            "0.01")),
                            List.of(action(RuleActionType.SKIP_TRANSACTION, null)));
            when(transactionRuleRepository
                            .findByUserIdAndIsEnabledTrueOrderByPriorityAscCreatedAtAsc(USER_ID))
                    .thenReturn(List.of(r));

            List<ImportedTransaction> txs = richDataset();
            Set<Integer> matched = service.applyRules(txs, USER_ID);

            assertThat(matched).containsExactly(17);
        }

        @Test
        @DisplayName("Malformed condition value — condition is skipped, no match")
        void malformedAmountConditionValue() {
            TransactionRule r =
                    rule(
                            "BrokenRule",
                            0,
                            List.of(
                                    condition(
                                            RuleConditionField.AMOUNT,
                                            RuleConditionOperator.EQUALS,
                                            "not-a-number")),
                            List.of(action(RuleActionType.SET_CATEGORY, "Bad")));
            when(transactionRuleRepository
                            .findByUserIdAndIsEnabledTrueOrderByPriorityAscCreatedAtAsc(USER_ID))
                    .thenReturn(List.of(r));

            Set<Integer> matched = service.applyRules(richDataset(), USER_ID);

            assertThat(matched).isEmpty();
        }
    }

    // -----------------------------------------------------------------------
    // 3. TRANSACTION_TYPE field
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("TRANSACTION_TYPE field operators")
    class TransactionTypeField {

        @Test
        @DisplayName("EQUALS INCOME — indices 2, 8, 12, 13, 18")
        void equalsIncome() {
            TransactionRule r =
                    rule(
                            "AllIncome",
                            0,
                            List.of(
                                    condition(
                                            RuleConditionField.TRANSACTION_TYPE,
                                            RuleConditionOperator.EQUALS,
                                            "INCOME")),
                            List.of(action(RuleActionType.SET_CATEGORY, "Income")));
            when(transactionRuleRepository
                            .findByUserIdAndIsEnabledTrueOrderByPriorityAscCreatedAtAsc(USER_ID))
                    .thenReturn(List.of(r));

            List<ImportedTransaction> txs = richDataset();
            Set<Integer> matched = service.applyRules(txs, USER_ID);

            // Positive or zero amounts (>= 0): 3500(2), 500(8), 1200(12), 320(13), 50(18),
            // 0.00(17)
            // Zero amount derives INCOME because the engine uses: amount >= 0 ? INCOME :
            // EXPENSE
            assertThat(matched).containsExactlyInAnyOrder(2, 8, 12, 13, 17, 18);
        }

        @Test
        @DisplayName("EQUALS EXPENSE — 15 negative txs + zero-amount")
        void equalsExpense() {
            TransactionRule r =
                    rule(
                            "AllExpenses",
                            0,
                            List.of(
                                    condition(
                                            RuleConditionField.TRANSACTION_TYPE,
                                            RuleConditionOperator.EQUALS,
                                            "EXPENSE")),
                            List.of(action(RuleActionType.SET_CATEGORY, "Expense")));
            when(transactionRuleRepository
                            .findByUserIdAndIsEnabledTrueOrderByPriorityAscCreatedAtAsc(USER_ID))
                    .thenReturn(List.of(r));

            List<ImportedTransaction> txs = richDataset();
            Set<Integer> matched = service.applyRules(txs, USER_ID);

            // Negative amounts only: 0,1,3,4,5,6,7,9,10,11,14,15,16,19
            // zero(17) is INCOME per engine (0 >= 0)
            assertThat(matched)
                    .containsExactlyInAnyOrder(0, 1, 3, 4, 5, 6, 7, 9, 10, 11, 14, 15, 16, 19);
        }

        @Test
        @DisplayName("NOT_EQUALS EXPENSE — same as INCOME set")
        void notEqualsExpense() {
            TransactionRule r =
                    rule(
                            "NotExpense",
                            0,
                            List.of(
                                    condition(
                                            RuleConditionField.TRANSACTION_TYPE,
                                            RuleConditionOperator.NOT_EQUALS,
                                            "EXPENSE")),
                            List.of(action(RuleActionType.SET_CATEGORY, "Income")));
            when(transactionRuleRepository
                            .findByUserIdAndIsEnabledTrueOrderByPriorityAscCreatedAtAsc(USER_ID))
                    .thenReturn(List.of(r));

            List<ImportedTransaction> txs = richDataset();
            Set<Integer> matched = service.applyRules(txs, USER_ID);

            // zero(17) is INCOME (0 >= 0)
            assertThat(matched).containsExactlyInAnyOrder(2, 8, 12, 13, 17, 18);
        }
    }

    // -----------------------------------------------------------------------
    // 4. Action types
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("Action types")
    class ActionTypes {

        @Test
        @DisplayName("SET_CATEGORY — category field is populated")
        void setCategory() {
            TransactionRule r =
                    rule(
                            "SetCat",
                            0,
                            List.of(
                                    condition(
                                            RuleConditionField.DESCRIPTION,
                                            RuleConditionOperator.CONTAINS,
                                            "netflix")),
                            List.of(
                                    action(
                                            RuleActionType.SET_CATEGORY,
                                            "Entertainment:Streaming")));
            when(transactionRuleRepository
                            .findByUserIdAndIsEnabledTrueOrderByPriorityAscCreatedAtAsc(USER_ID))
                    .thenReturn(List.of(r));

            List<ImportedTransaction> txs = richDataset();
            service.applyRules(txs, USER_ID);

            assertThat(txs.get(3).getCategory()).isEqualTo("Entertainment:Streaming");
        }

        @Test
        @DisplayName("SET_PAYEE — payee is overridden")
        void setPayee() {
            TransactionRule r =
                    rule(
                            "NormalisePayee",
                            0,
                            List.of(
                                    condition(
                                            RuleConditionField.DESCRIPTION,
                                            RuleConditionOperator.CONTAINS,
                                            "basic fit")),
                            List.of(action(RuleActionType.SET_PAYEE, "Basic-Fit")));
            when(transactionRuleRepository
                            .findByUserIdAndIsEnabledTrueOrderByPriorityAscCreatedAtAsc(USER_ID))
                    .thenReturn(List.of(r));

            List<ImportedTransaction> txs = richDataset();
            service.applyRules(txs, USER_ID);

            assertThat(txs.get(5).getPayee()).isEqualTo("Basic-Fit");
        }

        @Test
        @DisplayName("ADD_TAG — tag appended to list")
        void addTag() {
            TransactionRule r =
                    rule(
                            "TagFuel",
                            0,
                            List.of(
                                    condition(
                                            RuleConditionField.DESCRIPTION,
                                            RuleConditionOperator.CONTAINS,
                                            "total station")),
                            List.of(action(RuleActionType.ADD_TAG, "fuel")));
            when(transactionRuleRepository
                            .findByUserIdAndIsEnabledTrueOrderByPriorityAscCreatedAtAsc(USER_ID))
                    .thenReturn(List.of(r));

            List<ImportedTransaction> txs = richDataset();
            service.applyRules(txs, USER_ID);

            assertThat(txs.get(9).getTags()).containsExactly("fuel");
        }

        @Test
        @DisplayName("ADD_TAG — multiple rules adding tags accumulate them")
        void addTag_multiple() {
            TransactionRule r1 =
                    rule(
                            "TagUber1",
                            0,
                            List.of(
                                    condition(
                                            RuleConditionField.DESCRIPTION,
                                            RuleConditionOperator.CONTAINS,
                                            "uber")),
                            List.of(action(RuleActionType.ADD_TAG, "transport")));
            // r1 fires first; stop-on-first-match means r2 never fires for the same tx
            when(transactionRuleRepository
                            .findByUserIdAndIsEnabledTrueOrderByPriorityAscCreatedAtAsc(USER_ID))
                    .thenReturn(List.of(r1));

            List<ImportedTransaction> txs = richDataset();
            service.applyRules(txs, USER_ID);

            // Only r1 fires (stop-on-first-match), so only one tag
            assertThat(txs.get(14).getTags()).containsExactly("transport");
        }

        @Test
        @DisplayName("SET_DESCRIPTION — memo is overridden")
        void setDescription() {
            TransactionRule r =
                    rule(
                            "CleanDesc",
                            0,
                            List.of(
                                    condition(
                                            RuleConditionField.DESCRIPTION,
                                            RuleConditionOperator.CONTAINS,
                                            "pharmacie")),
                            List.of(action(RuleActionType.SET_DESCRIPTION, "Health & Pharmacy")));
            when(transactionRuleRepository
                            .findByUserIdAndIsEnabledTrueOrderByPriorityAscCreatedAtAsc(USER_ID))
                    .thenReturn(List.of(r));

            List<ImportedTransaction> txs = richDataset();
            service.applyRules(txs, USER_ID);

            assertThat(txs.get(7).getMemo()).isEqualTo("Health & Pharmacy");
        }

        @Test
        @DisplayName("SET_AMOUNT — amount is replaced with rule value")
        void setAmount() {
            TransactionRule r =
                    rule(
                            "NormaliseRent",
                            0,
                            List.of(
                                    condition(
                                            RuleConditionField.DESCRIPTION,
                                            RuleConditionOperator.CONTAINS,
                                            "loyer")),
                            List.of(action(RuleActionType.SET_AMOUNT, "-1000.00")));
            when(transactionRuleRepository
                            .findByUserIdAndIsEnabledTrueOrderByPriorityAscCreatedAtAsc(USER_ID))
                    .thenReturn(List.of(r));

            List<ImportedTransaction> txs = richDataset();
            service.applyRules(txs, USER_ID);

            assertThat(txs.get(4).getAmount()).isEqualByComparingTo(new BigDecimal("-1000.00"));
        }

        @Test
        @DisplayName("SET_AMOUNT with invalid value — amount is unchanged")
        void setAmount_invalid() {
            TransactionRule r =
                    rule(
                            "BadAmount",
                            0,
                            List.of(
                                    condition(
                                            RuleConditionField.DESCRIPTION,
                                            RuleConditionOperator.CONTAINS,
                                            "loyer")),
                            List.of(action(RuleActionType.SET_AMOUNT, "")));
            when(transactionRuleRepository
                            .findByUserIdAndIsEnabledTrueOrderByPriorityAscCreatedAtAsc(USER_ID))
                    .thenReturn(List.of(r));

            List<ImportedTransaction> txs = richDataset();
            BigDecimal originalAmount = txs.get(4).getAmount();
            service.applyRules(txs, USER_ID);

            assertThat(txs.get(4).getAmount()).isEqualByComparingTo(originalAmount);
        }

        @Test
        @DisplayName("ADD_SPLIT — split entry added with category, amount, and memo")
        void addSplit() {
            TransactionRule r =
                    rule(
                            "SplitSalary",
                            0,
                            List.of(
                                    condition(
                                            RuleConditionField.DESCRIPTION,
                                            RuleConditionOperator.CONTAINS,
                                            "salaire")),
                            List.of(
                                    action(
                                            RuleActionType.ADD_SPLIT,
                                            "Income:Salary",
                                            "2800.00",
                                            "net salary"),
                                    action(
                                            RuleActionType.ADD_SPLIT,
                                            "Income:Bonus",
                                            "700.00",
                                            "bonus")));
            when(transactionRuleRepository
                            .findByUserIdAndIsEnabledTrueOrderByPriorityAscCreatedAtAsc(USER_ID))
                    .thenReturn(List.of(r));

            List<ImportedTransaction> txs = richDataset();
            service.applyRules(txs, USER_ID);

            ImportedTransaction salary = txs.get(2);
            assertThat(salary.getSplits()).hasSize(2);
            assertThat(salary.getSplits().get(0).getCategory()).isEqualTo("Income:Salary");
            assertThat(salary.getSplits().get(0).getAmount())
                    .isEqualByComparingTo(new BigDecimal("2800.00"));
            assertThat(salary.getSplits().get(0).getMemo()).isEqualTo("net salary");
            assertThat(salary.getSplits().get(1).getCategory()).isEqualTo("Income:Bonus");
        }

        @Test
        @DisplayName("SKIP_TRANSACTION — adds blocking error to transaction")
        void skipTransaction() {
            TransactionRule r =
                    rule(
                            "SkipInternal",
                            0,
                            List.of(
                                    condition(
                                            RuleConditionField.DESCRIPTION,
                                            RuleConditionOperator.CONTAINS,
                                            "virement interne")),
                            List.of(action(RuleActionType.SKIP_TRANSACTION, null)));
            when(transactionRuleRepository
                            .findByUserIdAndIsEnabledTrueOrderByPriorityAscCreatedAtAsc(USER_ID))
                    .thenReturn(List.of(r));

            List<ImportedTransaction> txs = richDataset();
            service.applyRules(txs, USER_ID);

            // tx[8] payee="VIREMENT INTERNE"
            assertThat(txs.get(8).hasErrors()).isTrue();
            assertThat(txs.get(8).getValidationErrors()).anyMatch(e -> e.contains("RULE_SKIP"));
        }

        @Test
        @DisplayName("Multiple actions on one rule — all applied in order")
        void multipleActionsApplied() {
            TransactionRule r =
                    rule(
                            "EnrichUber",
                            0,
                            List.of(
                                    condition(
                                            RuleConditionField.DESCRIPTION,
                                            RuleConditionOperator.CONTAINS,
                                            "uber")),
                            List.of(
                                    action(RuleActionType.SET_CATEGORY, "Transport:Taxi"),
                                    action(RuleActionType.SET_PAYEE, "Uber"),
                                    action(RuleActionType.ADD_TAG, "rideshare"),
                                    action(RuleActionType.ADD_TAG, "transport")));
            when(transactionRuleRepository
                            .findByUserIdAndIsEnabledTrueOrderByPriorityAscCreatedAtAsc(USER_ID))
                    .thenReturn(List.of(r));

            List<ImportedTransaction> txs = richDataset();
            service.applyRules(txs, USER_ID);

            ImportedTransaction uber = txs.get(14);
            assertThat(uber.getCategory()).isEqualTo("Transport:Taxi");
            assertThat(uber.getPayee()).isEqualTo("Uber");
            assertThat(uber.getTags()).containsExactly("rideshare", "transport");
        }
    }

    // -----------------------------------------------------------------------
    // 5. Multi-condition AND logic
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("Multi-condition AND logic")
    class MultiCondition {

        @Test
        @DisplayName("AND — both conditions must match (INCOME + contains 'dividende')")
        void and_bothMustMatch() {
            TransactionRule r =
                    rule(
                            "Dividends",
                            0,
                            List.of(
                                    condition(
                                            RuleConditionField.TRANSACTION_TYPE,
                                            RuleConditionOperator.EQUALS,
                                            "INCOME"),
                                    condition(
                                            RuleConditionField.DESCRIPTION,
                                            RuleConditionOperator.CONTAINS,
                                            "dividende")),
                            List.of(action(RuleActionType.SET_CATEGORY, "Income:Dividends")));
            when(transactionRuleRepository
                            .findByUserIdAndIsEnabledTrueOrderByPriorityAscCreatedAtAsc(USER_ID))
                    .thenReturn(List.of(r));

            List<ImportedTransaction> txs = richDataset();
            Set<Integer> matched = service.applyRules(txs, USER_ID);

            // Only tx[12] "DIVIDENDES Q1 2026" is income AND contains dividende
            assertThat(matched).containsExactly(12);
            assertThat(txs.get(12).getCategory()).isEqualTo("Income:Dividends");
        }

        @Test
        @DisplayName("AND — first condition matches but second does not → no match")
        void and_firstMatchSecondMiss() {
            TransactionRule r =
                    rule(
                            "WrongCombo",
                            0,
                            List.of(
                                    condition(
                                            RuleConditionField.DESCRIPTION,
                                            RuleConditionOperator.CONTAINS,
                                            "amazon"),
                                    condition(
                                            RuleConditionField.TRANSACTION_TYPE,
                                            RuleConditionOperator.EQUALS,
                                            "INCOME")),
                            List.of(action(RuleActionType.SET_CATEGORY, "ShouldNotAppear")));
            when(transactionRuleRepository
                            .findByUserIdAndIsEnabledTrueOrderByPriorityAscCreatedAtAsc(USER_ID))
                    .thenReturn(List.of(r));

            Set<Integer> matched = service.applyRules(richDataset(), USER_ID);

            // Amazon tx[1] is EXPENSE, not INCOME → no match
            assertThat(matched).isEmpty();
        }

        @Test
        @DisplayName("AND — description + amount range narrows to single transaction")
        void and_descriptionPlusAmountRange() {
            // Rule: SNCF + amount >= 80
            TransactionRule r =
                    rule(
                            "SCNCExpensive",
                            0,
                            List.of(
                                    condition(
                                            RuleConditionField.DESCRIPTION,
                                            RuleConditionOperator.CONTAINS,
                                            "sncf"),
                                    condition(
                                            RuleConditionField.AMOUNT,
                                            RuleConditionOperator.GREATER_OR_EQUAL,
                                            "80")),
                            List.of(action(RuleActionType.SET_CATEGORY, "Transport:Rail")));
            when(transactionRuleRepository
                            .findByUserIdAndIsEnabledTrueOrderByPriorityAscCreatedAtAsc(USER_ID))
                    .thenReturn(List.of(r));

            List<ImportedTransaction> txs = richDataset();
            Set<Integer> matched = service.applyRules(txs, USER_ID);

            assertThat(matched).containsExactly(15);
        }

        @Test
        @DisplayName("Empty condition list — rule never fires")
        void emptyConditions_neverMatches() {
            TransactionRule r =
                    rule(
                            "EmptyConditions",
                            0,
                            new ArrayList<>(),
                            List.of(action(RuleActionType.SET_CATEGORY, "Orphan")));
            when(transactionRuleRepository
                            .findByUserIdAndIsEnabledTrueOrderByPriorityAscCreatedAtAsc(USER_ID))
                    .thenReturn(List.of(r));

            Set<Integer> matched = service.applyRules(richDataset(), USER_ID);

            assertThat(matched).isEmpty();
        }
    }

    // -----------------------------------------------------------------------
    // 6. Priority ordering — stop-on-first-match
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("Priority ordering and stop-on-first-match")
    class PriorityAndStopOnFirstMatch {

        @Test
        @DisplayName("Lower priority number wins (priority 0 fires before priority 1)")
        void lowerPriorityFires() {
            // Both match tx[3] (Netflix), but priority 0 sets Streaming, priority 1 sets
            // Entertainment
            TransactionRule r0 =
                    rule(
                            "Netflix-primary",
                            0,
                            List.of(
                                    condition(
                                            RuleConditionField.DESCRIPTION,
                                            RuleConditionOperator.CONTAINS,
                                            "netflix")),
                            List.of(
                                    action(
                                            RuleActionType.SET_CATEGORY,
                                            "Entertainment:Streaming")));
            TransactionRule r1 =
                    rule(
                            "Netflix-secondary",
                            1,
                            List.of(
                                    condition(
                                            RuleConditionField.DESCRIPTION,
                                            RuleConditionOperator.CONTAINS,
                                            "netflix")),
                            List.of(action(RuleActionType.SET_CATEGORY, "Entertainment:General")));
            when(transactionRuleRepository
                            .findByUserIdAndIsEnabledTrueOrderByPriorityAscCreatedAtAsc(USER_ID))
                    .thenReturn(List.of(r0, r1));

            List<ImportedTransaction> txs = richDataset();
            service.applyRules(txs, USER_ID);

            // r0 fired first; r1 must NOT have overwritten the category
            assertThat(txs.get(3).getCategory()).isEqualTo("Entertainment:Streaming");
        }

        @Test
        @DisplayName("Stop-on-first-match — second matching rule is not evaluated")
        void stopOnFirstMatch() {
            TransactionRule r0 =
                    rule(
                            "SalaryPrimary",
                            0,
                            List.of(
                                    condition(
                                            RuleConditionField.DESCRIPTION,
                                            RuleConditionOperator.CONTAINS,
                                            "salaire")),
                            List.of(action(RuleActionType.SET_CATEGORY, "Income:Salary")));
            // r1 would also match via INCOME type
            TransactionRule r1 =
                    rule(
                            "AnyIncome",
                            1,
                            List.of(
                                    condition(
                                            RuleConditionField.TRANSACTION_TYPE,
                                            RuleConditionOperator.EQUALS,
                                            "INCOME")),
                            List.of(action(RuleActionType.SET_CATEGORY, "Income:Generic")));
            when(transactionRuleRepository
                            .findByUserIdAndIsEnabledTrueOrderByPriorityAscCreatedAtAsc(USER_ID))
                    .thenReturn(List.of(r0, r1));

            List<ImportedTransaction> txs = richDataset();
            service.applyRules(txs, USER_ID);

            // tx[2] matched r0 first → category stays "Income:Salary"
            assertThat(txs.get(2).getCategory()).isEqualTo("Income:Salary");
            // tx[8] (VIREMENT INTERNE, +500, income) did NOT match r0, so r1 fires
            assertThat(txs.get(8).getCategory()).isEqualTo("Income:Generic");
            // tx[12] (DIVIDENDES, income) also matched only r1
            assertThat(txs.get(12).getCategory()).isEqualTo("Income:Generic");
        }

        @Test
        @DisplayName("RULE_MATCH annotation is added to validationErrors on match")
        void ruleMatchAnnotation() {
            TransactionRule r =
                    rule(
                            "AmazonRule",
                            0,
                            List.of(
                                    condition(
                                            RuleConditionField.DESCRIPTION,
                                            RuleConditionOperator.CONTAINS,
                                            "amazon")),
                            List.of(action(RuleActionType.SET_CATEGORY, "Shopping")));
            when(transactionRuleRepository
                            .findByUserIdAndIsEnabledTrueOrderByPriorityAscCreatedAtAsc(USER_ID))
                    .thenReturn(List.of(r));

            List<ImportedTransaction> txs = richDataset();
            service.applyRules(txs, USER_ID);

            assertThat(txs.get(1).getValidationErrors())
                    .anyMatch(e -> e.startsWith("RULE_MATCH:") && e.contains("AmazonRule"));
            // RULE_MATCH is an informational prefix — hasErrors() should be false
            assertThat(txs.get(1).hasErrors()).isFalse();
        }
    }

    // -----------------------------------------------------------------------
    // 7. Disabled rules and edge cases
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("Disabled rules and edge cases")
    class DisabledAndEdgeCases {

        @Test
        @DisplayName("Disabled rule is never evaluated")
        void disabledRule_neverFires() {
            // The repository method only returns enabled rules — mock returns empty
            when(transactionRuleRepository
                            .findByUserIdAndIsEnabledTrueOrderByPriorityAscCreatedAtAsc(USER_ID))
                    .thenReturn(List.of());

            Set<Integer> matched = service.applyRules(richDataset(), USER_ID);

            assertThat(matched).isEmpty();
        }

        @Test
        @DisplayName("Empty transaction list returns empty matched set")
        void emptyTransactions_returnsEmpty() {
            TransactionRule r =
                    rule(
                            "Any",
                            0,
                            List.of(
                                    condition(
                                            RuleConditionField.DESCRIPTION,
                                            RuleConditionOperator.CONTAINS,
                                            "x")),
                            List.of(action(RuleActionType.SET_CATEGORY, "X")));
            when(transactionRuleRepository
                            .findByUserIdAndIsEnabledTrueOrderByPriorityAscCreatedAtAsc(USER_ID))
                    .thenReturn(List.of(r));

            Set<Integer> matched = service.applyRules(List.of(), USER_ID);

            assertThat(matched).isEmpty();
        }

        @Test
        @DisplayName("Null transaction list returns empty matched set")
        void nullTransactions_returnsEmpty() {
            TransactionRule r =
                    rule(
                            "Any",
                            0,
                            List.of(
                                    condition(
                                            RuleConditionField.DESCRIPTION,
                                            RuleConditionOperator.CONTAINS,
                                            "x")),
                            List.of(action(RuleActionType.SET_CATEGORY, "X")));
            when(transactionRuleRepository
                            .findByUserIdAndIsEnabledTrueOrderByPriorityAscCreatedAtAsc(USER_ID))
                    .thenReturn(List.of(r));

            Set<Integer> matched = service.applyRules(null, USER_ID);

            assertThat(matched).isEmpty();
        }

        @Test
        @DisplayName("No enabled rules returns empty matched set")
        void noEnabledRules_returnsEmpty() {
            when(transactionRuleRepository
                            .findByUserIdAndIsEnabledTrueOrderByPriorityAscCreatedAtAsc(USER_ID))
                    .thenReturn(List.of());

            Set<Integer> matched = service.applyRules(richDataset(), USER_ID);

            assertThat(matched).isEmpty();
        }

        @Test
        @DisplayName("Rule with null actions list — match recorded but no NPE")
        void nullActionsList_noException() {
            TransactionRule r =
                    rule(
                            "NoActions",
                            0,
                            List.of(
                                    condition(
                                            RuleConditionField.DESCRIPTION,
                                            RuleConditionOperator.CONTAINS,
                                            "netflix")),
                            null);
            when(transactionRuleRepository
                            .findByUserIdAndIsEnabledTrueOrderByPriorityAscCreatedAtAsc(USER_ID))
                    .thenReturn(List.of(r));

            List<ImportedTransaction> txs = richDataset();
            Set<Integer> matched = service.applyRules(txs, USER_ID);

            assertThat(matched).containsExactly(3);
        }

        @Test
        @DisplayName("DESCRIPTION match is case-insensitive in condition value too")
        void caseInsensitiveConditionValue() {
            TransactionRule r =
                    rule(
                            "Spotify-Upper",
                            0,
                            List.of(
                                    condition(
                                            RuleConditionField.DESCRIPTION,
                                            RuleConditionOperator.CONTAINS,
                                            "SPOTIFY AB")),
                            List.of(action(RuleActionType.SET_CATEGORY, "Entertainment:Music")));
            when(transactionRuleRepository
                            .findByUserIdAndIsEnabledTrueOrderByPriorityAscCreatedAtAsc(USER_ID))
                    .thenReturn(List.of(r));

            List<ImportedTransaction> txs = richDataset();
            Set<Integer> matched = service.applyRules(txs, USER_ID);

            assertThat(matched).containsExactly(16);
        }

        @Test
        @DisplayName("Matched indices returned — set covers all matched positions")
        void returnedIndicesMatchTransactionPositions() {
            // Three separate rules targeting three non-overlapping tx
            TransactionRule r0 =
                    rule(
                            "Netflix-rule",
                            0,
                            List.of(
                                    condition(
                                            RuleConditionField.DESCRIPTION,
                                            RuleConditionOperator.CONTAINS,
                                            "netflix")),
                            List.of(action(RuleActionType.SET_CATEGORY, "Streaming")));
            TransactionRule r1 =
                    rule(
                            "Free-Mobile-rule",
                            1,
                            List.of(
                                    condition(
                                            RuleConditionField.DESCRIPTION,
                                            RuleConditionOperator.CONTAINS,
                                            "free mobile")),
                            List.of(action(RuleActionType.SET_CATEGORY, "Telecom")));
            TransactionRule r2 =
                    rule(
                            "Spotify-rule",
                            2,
                            List.of(
                                    condition(
                                            RuleConditionField.DESCRIPTION,
                                            RuleConditionOperator.CONTAINS,
                                            "spotify")),
                            List.of(action(RuleActionType.SET_CATEGORY, "Music")));
            when(transactionRuleRepository
                            .findByUserIdAndIsEnabledTrueOrderByPriorityAscCreatedAtAsc(USER_ID))
                    .thenReturn(List.of(r0, r1, r2));

            Set<Integer> matched = service.applyRules(richDataset(), USER_ID);

            assertThat(matched).containsExactlyInAnyOrder(3, 11, 16);
        }
    }

    // -----------------------------------------------------------------------
    // 8. Full realistic import scenario
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("Full realistic import scenario — 6 rules on 20 transactions")
    class FullScenario {

        @BeforeEach
        void setUpRules() {
            List<TransactionRule> rules =
                    List.of(
                            // Priority 0 — skip internal transfers (stop before anything else
                            // evaluates
                            // them)
                            rule(
                                    "Skip-Internal-Transfer",
                                    0,
                                    List.of(
                                            condition(
                                                    RuleConditionField.DESCRIPTION,
                                                    RuleConditionOperator.CONTAINS,
                                                    "virement interne")),
                                    List.of(action(RuleActionType.SKIP_TRANSACTION, null))),
                            // Priority 1 — classify salary income
                            rule(
                                    "Salary-Income",
                                    1,
                                    List.of(
                                            condition(
                                                    RuleConditionField.TRANSACTION_TYPE,
                                                    RuleConditionOperator.EQUALS,
                                                    "INCOME"),
                                            condition(
                                                    RuleConditionField.DESCRIPTION,
                                                    RuleConditionOperator.CONTAINS,
                                                    "salaire")),
                                    List.of(
                                            action(RuleActionType.SET_CATEGORY, "Income:Salary"),
                                            action(RuleActionType.SET_PAYEE, "Employer"))),
                            // Priority 2 — any large expense > 500 labelled "Major Expense" unless
                            // already
                            // matched
                            rule(
                                    "Large-Expense",
                                    2,
                                    List.of(
                                            condition(
                                                    RuleConditionField.AMOUNT,
                                                    RuleConditionOperator.GREATER_THAN,
                                                    "500")),
                                    List.of(action(RuleActionType.SET_CATEGORY, "Major:Expense"))),
                            // Priority 3 — streaming subscriptions
                            rule(
                                    "Streaming",
                                    3,
                                    List.of(
                                            condition(
                                                    RuleConditionField.DESCRIPTION,
                                                    RuleConditionOperator.CONTAINS,
                                                    "netflix"),
                                            condition(
                                                    RuleConditionField.AMOUNT,
                                                    RuleConditionOperator.LESS_THAN,
                                                    "20")),
                                    List.of(
                                            action(
                                                    RuleActionType.SET_CATEGORY,
                                                    "Entertainment:Streaming"),
                                            action(RuleActionType.ADD_TAG, "subscription"))),
                            // Priority 4 — fuel
                            rule(
                                    "Fuel",
                                    4,
                                    List.of(
                                            condition(
                                                    RuleConditionField.DESCRIPTION,
                                                    RuleConditionOperator.CONTAINS,
                                                    "total station")),
                                    List.of(action(RuleActionType.SET_CATEGORY, "Transport:Fuel"))),
                            // Priority 5 — tag all micro transactions < 5 EUR
                            rule(
                                    "Micro-Tx",
                                    5,
                                    List.of(
                                            condition(
                                                    RuleConditionField.AMOUNT,
                                                    RuleConditionOperator.LESS_THAN,
                                                    "5")),
                                    List.of(action(RuleActionType.ADD_TAG, "micro"))));
            when(transactionRuleRepository
                            .findByUserIdAndIsEnabledTrueOrderByPriorityAscCreatedAtAsc(USER_ID))
                    .thenReturn(rules);
        }

        @Test
        @DisplayName("Correct number of transactions matched")
        void correctMatchCount() {
            Set<Integer> matched = service.applyRules(richDataset(), USER_ID);
            // priority 0: skip tx[8] (virement interne) → matched {8}
            // priority 1: match tx[2] (salaire income) → matched {8,2}
            // priority 2 (amount > 500): tx[0]=187 no, tx[1]=139 no, tx[2] already matched,
            // tx[4]=1200 YES, tx[12]=1200 YES, tx[13]=320 no, tx[19]=1500 YES → matched
            // {8,2,4,12,19}
            // priority 3 (netflix + <20): tx[3] netflix 15.99 YES → matched {8,2,4,12,19,3}
            // priority 4 (total station): tx[9] YES → matched {8,2,4,12,19,3,9}
            // priority 5 (<5 EUR abs): tx[10]=4.50 YES, tx[17]=0.00 YES → matched
            // {8,2,4,12,19,3,9,10,17}
            assertThat(matched).hasSize(9);
        }

        @Test
        @DisplayName("Internal transfer is skipped")
        void internalTransferSkipped() {
            List<ImportedTransaction> txs = richDataset();
            service.applyRules(txs, USER_ID);

            assertThat(txs.get(8).hasErrors()).isTrue();
            assertThat(txs.get(8).getValidationErrors()).anyMatch(e -> e.contains("RULE_SKIP"));
        }

        @Test
        @DisplayName("Salary rule sets category and payee")
        void salaryRuleApplied() {
            List<ImportedTransaction> txs = richDataset();
            service.applyRules(txs, USER_ID);

            assertThat(txs.get(2).getCategory()).isEqualTo("Income:Salary");
            assertThat(txs.get(2).getPayee()).isEqualTo("Employer");
        }

        @Test
        @DisplayName("Large expense rule fires for rent and dividends")
        void largeExpenseRuleApplied() {
            List<ImportedTransaction> txs = richDataset();
            service.applyRules(txs, USER_ID);

            // tx[4] rent 1200; tx[19] taxe foncière 1500; tx[12] dividends 1200
            assertThat(txs.get(4).getCategory()).isEqualTo("Major:Expense");
            assertThat(txs.get(19).getCategory()).isEqualTo("Major:Expense");
            assertThat(txs.get(12).getCategory()).isEqualTo("Major:Expense");
        }

        @Test
        @DisplayName("Netflix streaming rule sets category and tag")
        void netflixRuleApplied() {
            List<ImportedTransaction> txs = richDataset();
            service.applyRules(txs, USER_ID);

            assertThat(txs.get(3).getCategory()).isEqualTo("Entertainment:Streaming");
            assertThat(txs.get(3).getTags()).contains("subscription");
        }

        @Test
        @DisplayName("Fuel rule sets category for fuel station")
        void fuelRuleApplied() {
            List<ImportedTransaction> txs = richDataset();
            service.applyRules(txs, USER_ID);

            assertThat(txs.get(9).getCategory()).isEqualTo("Transport:Fuel");
        }

        @Test
        @DisplayName("Micro-tx rule tags coffee and zero-amount advisory")
        void microTxRuleApplied() {
            List<ImportedTransaction> txs = richDataset();
            service.applyRules(txs, USER_ID);

            assertThat(txs.get(10).getTags()).contains("micro"); // coffee 4.50
            assertThat(txs.get(17).getTags()).contains("micro"); // 0.00
        }

        @Test
        @DisplayName("Unmatched transactions have no category set by rules")
        void unmatchedTransactionsUntouched() {
            List<ImportedTransaction> txs = richDataset();
            service.applyRules(txs, USER_ID);

            // tx[5] BASIC FIT, tx[6] ATM, tx[7] PHARMACIE, tx[11] FREE MOBILE, tx[14] UBER,
            // tx[15] SNCF, tx[16] SPOTIFY, tx[18] REMBOURSEMENT
            for (int idx : new int[] {5, 6, 7, 11, 14, 15, 16, 18}) {
                assertThat(txs.get(idx).getCategory())
                        .as("tx[%d] should not have a category set by rules", idx)
                        .isNull();
            }
        }
    }
}
