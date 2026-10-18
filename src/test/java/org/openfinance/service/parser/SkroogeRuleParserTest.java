package org.openfinance.service.parser;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.openfinance.entity.RuleConditionField;

class SkroogeRuleParserTest {
    private final ObjectMapper json = new ObjectMapper();

    @Test
    void mapsRealExportRulesAndReportsTheTwoUnsupportedPaymentModeActions() throws Exception {
        com.fasterxml.jackson.databind.JsonNode root =
                json.readTree(Path.of("docs/sample/json/export_anonymized.json").toFile());
        SkroogeRuleParser.Result result = SkroogeRuleParser.parse(root.path("rule"));
        assertThat(result.rules()).hasSize(29);
        assertThat(result.skipped()).isEqualTo(2);
        assertThat(result.warnings())
                .filteredOn(w -> !w.code().equals("importScope"))
                .extracting(w -> w.sourceId())
                .containsExactly(36L, 37L);
        assertThat(result.rules())
                .allSatisfy(
                        rule -> {
                            assertThat(rule.getConditions()).isNotEmpty();
                            assertThat(rule.getActions()).isNotEmpty();
                            assertThat(rule.getName()).hasSizeLessThanOrEqualTo(100);
                        });
        assertThat(result.rules().get(1).getConditions())
                .extracting(c -> c.getField())
                .containsExactly(RuleConditionField.MEMO, RuleConditionField.PAYEE);
    }

    @Test
    void preservesMixedOrAndGroupsAsSeparateRules() {
        String xml =
                "<element><!--OR--><element><!--AND-->"
                        + leaf("t_comment", "#ATT# LIKE '%#V1S#%'", "Coffee")
                        + leaf("t_PAYEE", "#ATT#='#V1S#'", "Merchant")
                        + "</element><element><!--AND-->"
                        + leaf("t_comment", "#ATT# LIKE '%#V1S#%'", "Tea")
                        + "</element></element>";
        SkroogeRuleParser.Result result = SkroogeRuleParser.parse(source(xml));
        assertThat(result.rules())
                .hasSize(2)
                .allSatisfy(r -> assertThat(r.getConditionMatch()).isEqualTo("AND"));
        assertThat(result.rules().get(0).getConditions()).hasSize(2);
        assertThat(result.rules().get(1).getConditions()).hasSize(1);
        assertThat(result.rules().get(0).getName()).isNotEqualTo(result.rules().get(1).getName());
    }

    @Test
    void skipsWholeRuleWhenOneConditionIsUnsupportedInsteadOfBroadeningIt() {
        SkroogeRuleParser.Result result =
                SkroogeRuleParser.parse(
                        source(
                                group(
                                        leaf("t_comment", "#ATT# LIKE '%#V1S#%'", "Coffee")
                                                + leaf("t_mode", "#ATT#='#V1S#'", "Card"))));
        assertThat(result.rules()).isEmpty();
        assertThat(result.skipped()).isEqualTo(1);
        assertThat(result.warnings().getFirst().code()).isEqualTo("unsupportedCondition");
    }

    @Test
    void doesNotCreateAnUnconditionalRuleAfterRemovingImportScope() {
        assertThat(
                        SkroogeRuleParser.parse(
                                        source(group(leaf("t_imported", "#ATT#='#V1S#'", "P"))))
                                .rules())
                .isEmpty();
    }

    @Test
    void rejectsEntitiesAndExternalDtds() {
        String xml =
                "<!DOCTYPE element [<!ENTITY secret SYSTEM 'file:///etc/passwd'>]>"
                        + group(leaf("t_comment", "#ATT#='#V1S#'", "&secret;"));
        SkroogeRuleParser.Result result = SkroogeRuleParser.parse(source(xml));
        assertThat(result.rules()).isEmpty();
        assertThat(result.warnings().getFirst().code()).isEqualTo("invalidXml");
    }

    @Test
    void preservesSignedAmountMeaningForExpenseConditions() {
        SkroogeRuleParser.Result result =
                SkroogeRuleParser.parse(
                        source(group(leaf("f_REALCURRENTAMOUNT", "#ATT#&lt;#V1#", "-100"))));
        assertThat(result.rules().getFirst().getConditions())
                .extracting(c -> c.getValue())
                .containsExactly("EXPENSE", "100");
    }

    @Test
    void mergesAgencyAddressWithoutLosingNotesOrDuplicatingExistingLines() {
        assertThat(
                        SkroogeJsonParser.mergeAccountDescription(
                                "Personal notes\nMain branch", "Main branch\n12 High Street"))
                .isEqualTo("Personal notes\nMain branch\n12 High Street");
        assertThat(SkroogeJsonParser.mergeAccountDescription(null, " ")).isNull();
    }

    private ArrayNode source(String conditions) {
        ArrayNode result = json.createArrayNode();
        result.addObject()
                .put("id", 7)
                .put("t_action_type", "U")
                .put("t_definition", conditions)
                .put(
                        "t_action_definition",
                        "<!DOCTYPE SKGML>"
                                + group(leaf("t_REALCATEGORY", "#ATT#='#V1S#'", "Food > Coffee")));
        return result;
    }

    private String group(String leaves) {
        return "<element><!--OR--><element><!--AND-->" + leaves + "</element></element>";
    }

    private String leaf(String field, String operator, String value) {
        return "<element attribute=\""
                + field
                + "\" operator=\""
                + operator
                + "\" value=\""
                + value
                + "\"/>";
    }
}
