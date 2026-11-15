package org.openfinance.service.parser;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.openfinance.dto.TransactionRuleRequest;
import org.openfinance.dto.TransactionRuleRequest.ConditionRequest;
import org.openfinance.entity.RuleConditionField;

@DisplayName("Skrooge rules preserve predicates instead of broadening imported automation")
class SkroogeRuleSemanticsTest {
    private final ObjectMapper json = new ObjectMapper();

    static Stream<Arguments> amounts() {
        return Stream.of("=", ">", ">=", "<", "<=", "<>")
                .flatMap(
                        operator ->
                                Stream.of("-100.01", "0", "100.01")
                                        .map(amount -> Arguments.of(operator, amount)));
    }

    @ParameterizedTest(name = "signed amount {0} {1}")
    @MethodSource("amounts")
    void preservesSignedComparisonsOrSkipsUnrepresentableCrossZeroPredicates(
            String operator, String amount) {
        BigDecimal threshold = new BigDecimal(amount);
        boolean supported =
                operator.equals("=")
                        || (operator.equals(">") && threshold.signum() >= 0)
                        || (operator.equals(">=") && threshold.signum() > 0)
                        || (operator.equals("<") && threshold.signum() <= 0)
                        || (operator.equals("<=") && threshold.signum() < 0);
        SkroogeRuleParser.Result result =
                parse(
                        group(leaf("f_REALCURRENTAMOUNT", "#ATT#" + operator + "#V1#", amount)),
                        validAction());
        if (!supported) {
            assertSkipped(result, "unsupportedCondition");
            return;
        }
        assertThat(result.skipped()).isZero();
        TransactionRuleRequest rule = result.rules().getFirst();
        for (String sample :
                List.of("-200", "-100.01", "-100", "-0.01", "0", "0.01", "100", "100.01", "200")) {
            BigDecimal signed = new BigDecimal(sample);
            boolean expected = compare(signed.compareTo(threshold), operator);
            boolean actual = rule.getConditions().stream().allMatch(c -> matches(c, signed));
            assertThat(actual)
                    .as("imported %s %s for transaction %s", operator, amount, sample)
                    .isEqualTo(expected);
        }
    }

    @ParameterizedTest
    @CsvSource(
            delimiter = '|',
            value = {
                "#ATT# LIKE '%#V1S#%'|CONTAINS",
                "#ATT# NOT LIKE '%#V1S#%'|NOT_CONTAINS",
                "#ATT#='#V1S#'|EQUALS",
                "#ATT#<>'#V1S#'|NOT_EQUALS",
                "#ATT#!='#V1S#'|NOT_EQUALS"
            })
    void mapsTextOperatorsWithoutLosingNegation(String operator, String expected) {
        SkroogeRuleParser.Result result =
                parse(group(leaf("t_PAYEE", operator, "Merchant")), validAction());
        assertThat(result.rules().getFirst().getConditions())
                .singleElement()
                .satisfies(
                        c -> {
                            assertThat(c.getField()).isEqualTo(RuleConditionField.PAYEE);
                            assertThat(c.getOperator().name()).isEqualTo(expected);
                            assertThat(c.getValue()).isEqualTo("Merchant");
                        });
    }

    static Stream<Arguments> invalidConditions() {
        String valid = leaf("t_comment", "#ATT#='#V1S#'", "Coffee");
        return Stream.of(
                Arguments.of(
                        group(leaf("f_REALCURRENTAMOUNT", "#ATT#=#V1#", "abc")),
                        "unsupportedCondition"),
                Arguments.of(
                        group(leaf("t_comment", "#ATT#='#V1S#'", "  ")), "unsupportedCondition"),
                Arguments.of(group(leaf("t_comment", "regexp", "Coffee")), "unsupportedCondition"),
                Arguments.of(
                        group(leaf("t_imported", "#ATT#='#V1S#'", "N") + valid),
                        "unsupportedCondition"),
                Arguments.of(
                        group(leaf("t_imported", "wrong", "P") + valid), "unsupportedCondition"),
                Arguments.of(
                        group(valid.replace("/>", " att2=\"other\"/>")), "unsupportedCondition"),
                Arguments.of(
                        group(valid.replace("/>", " att2s=\"other\"/>")), "unsupportedCondition"),
                Arguments.of(
                        group(valid.replace("/>", " value2=\"other\"/>")), "unsupportedCondition"),
                Arguments.of(group("<element/>"), "unsupportedCondition"),
                Arguments.of(
                        group(valid.replace("/>", ">" + valid + "</element>")),
                        "unsupportedCondition"),
                Arguments.of(
                        group(valid).replace("<!--OR-->", "<!--AND-->"), "unsupportedCondition"),
                Arguments.of(
                        group(valid).replace("<!--AND-->", "<!--OR-->"), "unsupportedCondition"),
                Arguments.of(
                        group(valid).replaceFirst("<element>", "<element attribute=\"x\">"),
                        "unsupportedCondition"),
                Arguments.of("<element/>", "emptyCondition"),
                Arguments.of(group(""), "emptyCondition"),
                Arguments.of("<other/>", "invalidXml"),
                Arguments.of(group("<other/>"), "invalidXml"),
                Arguments.of("<element>", "invalidXml"),
                Arguments.of(" ".repeat(200_001), "invalidXml"));
    }

    @ParameterizedTest
    @MethodSource("invalidConditions")
    void rejectsMalformedPredicatesAsAWhole(String xml, String warning) {
        assertSkipped(parse(xml, validAction()), warning);
    }

    static Stream<String> invalidActions() {
        return Stream.of(
                "<element/>",
                group(""),
                group(leaf("t_mode", "#ATT#='#V1S#'", "Card")),
                group(leaf("t_PAYEE", "#ATT# LIKE '%#V1S#%'", "Bank")),
                group(leaf("t_PAYEE", "#ATT#='#V1S#'", " ")),
                "<element><!--OR--><element/><!--OR--><element/></element>");
    }

    @ParameterizedTest
    @MethodSource("invalidActions")
    void neverPartiallyImportsUnsupportedActions(String xml) {
        assertSkipped(
                parse(group(leaf("t_comment", "#ATT#='#V1S#'", "Coffee")), xml),
                "unsupportedAction");
    }

    @Test
    void keepsActionOrderAndRemovesDuplicateOrPredicates() {
        String leaf = leaf("t_comment", "#ATT#='#V1S#'", "Coffee");
        String conditions =
                "<element><!--OR--><element><!--AND-->"
                        + leaf
                        + "</element><element><!--AND-->"
                        + leaf
                        + "</element></element>";
        String actions =
                group(
                        leaf("t_PAYEE", "#ATT#='#V1S#'", " Merchant ")
                                + leaf("t_comment", "#ATT#='#V1S#'", " Breakfast ")
                                + leaf("t_REALCATEGORY", "#ATT#='#V1S#'", " Food "));
        TransactionRuleRequest result = parse(conditions, actions).rules().getFirst();
        assertThat(result.getConditionMatch()).isEqualTo("OR");
        assertThat(result.getConditions()).hasSize(1);
        assertThat(result.getActions())
                .extracting(a -> a.getActionValue())
                .containsExactly("Merchant", "Breakfast", "Food");
        assertThat(result.getActions()).extracting(a -> a.getSortOrder()).containsExactly(0, 1, 2);
    }

    private SkroogeRuleParser.Result parse(String conditions, String actions) {
        ArrayNode source = json.createArrayNode();
        source.addObject()
                .put("id", 7)
                .put("t_action_type", "U")
                .put("t_definition", conditions)
                .put("t_action_definition", actions);
        return SkroogeRuleParser.parse(source);
    }

    private static boolean matches(ConditionRequest condition, BigDecimal signed) {
        if (condition.getField() == RuleConditionField.TRANSACTION_TYPE) {
            return condition.getValue().equals(signed.signum() < 0 ? "EXPENSE" : "INCOME");
        }
        int comparison = signed.abs().compareTo(new BigDecimal(condition.getValue()));
        return switch (condition.getOperator()) {
            case EQUALS -> comparison == 0;
            case GREATER_THAN -> comparison > 0;
            case GREATER_OR_EQUAL -> comparison >= 0;
            default -> throw new AssertionError(condition.getOperator());
        };
    }

    private static boolean compare(int comparison, String operator) {
        return switch (operator) {
            case "=" -> comparison == 0;
            case ">" -> comparison > 0;
            case ">=" -> comparison >= 0;
            case "<" -> comparison < 0;
            case "<=" -> comparison <= 0;
            default -> throw new AssertionError(operator);
        };
    }

    private static void assertSkipped(SkroogeRuleParser.Result result, String code) {
        assertThat(result.rules()).isEmpty();
        assertThat(result.skipped()).isEqualTo(1);
        assertThat(result.warnings())
                .singleElement()
                .satisfies(
                        w -> {
                            assertThat(w.sourceId()).isEqualTo(7);
                            assertThat(w.code()).isEqualTo(code);
                        });
    }

    private static String validAction() {
        return group(leaf("t_REALCATEGORY", "#ATT#='#V1S#'", "Food"));
    }

    private static String group(String leaves) {
        return "<element><!--OR--><element><!--AND-->" + leaves + "</element></element>";
    }

    private static String leaf(String field, String operator, String value) {
        return "<element attribute=\""
                + field
                + "\" operator=\""
                + operator.replace("<", "&lt;").replace(">", "&gt;")
                + "\" value=\""
                + value
                + "\"/>";
    }
}
