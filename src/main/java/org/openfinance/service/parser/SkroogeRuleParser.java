package org.openfinance.service.parser;

import com.fasterxml.jackson.databind.JsonNode;
import java.io.IOException;
import java.io.StringReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import org.openfinance.dto.SkroogeImportMetadata.RuleWarning;
import org.openfinance.dto.TransactionRuleRequest;
import org.openfinance.dto.TransactionRuleRequest.ActionRequest;
import org.openfinance.dto.TransactionRuleRequest.ConditionRequest;
import org.openfinance.entity.RuleActionType;
import org.openfinance.entity.RuleConditionField;
import org.openfinance.entity.RuleConditionOperator;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;
import org.xml.sax.helpers.DefaultHandler;

/** Maps Skrooge's OR-of-AND expressions without dropping unsupported predicates or actions. */
public final class SkroogeRuleParser {
    private SkroogeRuleParser() {}

    public record Result(
            List<TransactionRuleRequest> rules, List<RuleWarning> warnings, int skipped) {
        public Result {
            rules = List.copyOf(rules);
            warnings = List.copyOf(warnings);
        }
    }

    public static Result parse(JsonNode source) {
        List<TransactionRuleRequest> rules = new ArrayList<>();
        List<RuleWarning> warnings = new ArrayList<>();
        List<JsonNode> ordered = new ArrayList<>();
        source.forEach(ordered::add);
        ordered.sort(Comparator.comparing(n -> n.path("f_sortorder").decimalValue()));
        int skipped = 0;
        for (JsonNode rule : ordered) {
            long id = rule.path("id").asLong();
            try {
                if (!"U".equals(rule.path("t_action_type").asText())) fail("unsupportedAction");
                List<ActionRequest> actions = actions(rule.path("t_action_definition").asText());
                List<List<ConditionRequest>> groups =
                        conditions(rule.path("t_definition").asText());
                String name = name(rule);
                if (groups.stream().allMatch(group -> group.size() == 1)) {
                    rules.add(
                            request(
                                    name,
                                    "OR",
                                    groups.stream().map(g -> g.get(0)).distinct().toList(),
                                    actions));
                } else {
                    for (int i = 0; i < groups.size(); i++) {
                        rules.add(
                                request(
                                        shorten(name, 90) + " / " + (i + 1),
                                        "AND",
                                        groups.get(i),
                                        actions));
                    }
                }
                if (rule.path("t_definition").asText().contains("t_imported")) {
                    warnings.add(new RuleWarning(id, "importScope"));
                }
            } catch (IllegalArgumentException ex) {
                skipped++;
                warnings.add(new RuleWarning(id, ex.getMessage()));
            }
        }
        return new Result(rules, warnings, skipped);
    }

    private static TransactionRuleRequest request(
            String name,
            String match,
            List<ConditionRequest> conditions,
            List<ActionRequest> actions) {
        return TransactionRuleRequest.builder()
                .name(name)
                .conditionMatch(match)
                .conditions(conditions)
                .actions(actions)
                .build();
    }

    private static String name(JsonNode rule) {
        try {
            String fingerprint =
                    HexFormat.of()
                            .formatHex(
                                    MessageDigest.getInstance("SHA-256")
                                            .digest(
                                                    (rule.path("t_definition").asText()
                                                                    + "\n"
                                                                    + rule.path(
                                                                                    "t_action_definition")
                                                                            .asText())
                                                            .getBytes(StandardCharsets.UTF_8)))
                            .substring(0, 12);
            return shorten(
                    "Skrooge #"
                            + rule.path("id").asLong()
                            + " ["
                            + fingerprint
                            + "] "
                            + rule.path("t_action_description").asText("Rule"),
                    100);
        } catch (java.security.NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }

    private static String shorten(String text, int length) {
        return text.substring(0, Math.min(text.length(), length));
    }

    private static List<List<ConditionRequest>> conditions(String xml) {
        Element root = document(xml);
        requireLogic(root, "OR");
        List<List<ConditionRequest>> result = new ArrayList<>();
        for (Element group : children(root)) {
            requireLogic(group, "AND");
            List<ConditionRequest> conditions = new ArrayList<>();
            for (Element leaf : children(group)) {
                requireLeaf(leaf);
                String field = leaf.getAttribute("attribute");
                String value = leaf.getAttribute("value");
                String operator = leaf.getAttribute("operator");
                if ("t_imported".equals(field)
                        && "#ATT#='#V1S#'".equals(operator)
                        && "P".equals(value)) continue;
                if ("f_REALCURRENTAMOUNT".equals(field))
                    conditions.addAll(amountConditions(operator, value));
                else {
                    RuleConditionField target =
                            switch (field) {
                                case "t_comment" -> RuleConditionField.MEMO;
                                case "t_PAYEE" -> RuleConditionField.PAYEE;
                                default -> throw new IllegalArgumentException(
                                        "unsupportedCondition");
                            };
                    if (value.isBlank()) fail("unsupportedCondition");
                    conditions.add(condition(target, stringOperator(operator), value));
                }
            }
            if (conditions.isEmpty()) fail("emptyCondition");
            result.add(conditions.stream().distinct().toList());
        }
        if (result.isEmpty()) fail("emptyCondition");
        return result;
    }

    private static RuleConditionOperator stringOperator(String operator) {
        return switch (operator) {
            case "#ATT# LIKE '%#V1S#%'" -> RuleConditionOperator.CONTAINS;
            case "#ATT# NOT LIKE '%#V1S#%'" -> RuleConditionOperator.NOT_CONTAINS;
            case "#ATT#='#V1S#'" -> RuleConditionOperator.EQUALS;
            case "#ATT#<>'#V1S#'", "#ATT#!='#V1S#'" -> RuleConditionOperator.NOT_EQUALS;
            default -> throw new IllegalArgumentException("unsupportedCondition");
        };
    }

    private static List<ConditionRequest> amountConditions(String operator, String value) {
        BigDecimal amount;
        try {
            amount = new BigDecimal(value);
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("unsupportedCondition");
        }
        boolean negative =
                amount.signum() < 0 || (amount.signum() == 0 && "#ATT#<#V1#".equals(operator));
        RuleConditionOperator comparison =
                switch (operator) {
                    case "#ATT#=#V1#" -> RuleConditionOperator.EQUALS;
                    case "#ATT#>#V1#" -> amount.signum() >= 0
                            ? RuleConditionOperator.GREATER_THAN
                            : null;
                    case "#ATT#>=#V1#" -> amount.signum() > 0
                            ? RuleConditionOperator.GREATER_OR_EQUAL
                            : null;
                    case "#ATT#<#V1#" -> negative ? RuleConditionOperator.GREATER_THAN : null;
                    case "#ATT#<=#V1#" -> amount.signum() < 0
                            ? RuleConditionOperator.GREATER_OR_EQUAL
                            : null;
                    default -> null;
                };
        if (comparison == null) fail("unsupportedCondition");
        List<ConditionRequest> result = new ArrayList<>();
        if (amount.signum() != 0 || comparison != RuleConditionOperator.EQUALS) {
            result.add(
                    condition(
                            RuleConditionField.TRANSACTION_TYPE,
                            RuleConditionOperator.EQUALS,
                            negative ? "EXPENSE" : "INCOME"));
        }
        result.add(condition(RuleConditionField.AMOUNT, comparison, amount.abs().toPlainString()));
        return result;
    }

    private static ConditionRequest condition(
            RuleConditionField field, RuleConditionOperator operator, String value) {
        return ConditionRequest.builder().field(field).operator(operator).value(value).build();
    }

    private static List<ActionRequest> actions(String xml) {
        Element root = document(xml);
        requireLogic(root, "OR");
        List<Element> groups = children(root);
        if (groups.size() != 1) fail("unsupportedAction");
        requireLogic(groups.get(0), "AND");
        List<ActionRequest> result = new ArrayList<>();
        for (Element leaf : children(groups.get(0))) {
            requireLeaf(leaf);
            if (!"#ATT#='#V1S#'".equals(leaf.getAttribute("operator"))) fail("unsupportedAction");
            RuleActionType type =
                    switch (leaf.getAttribute("attribute")) {
                        case "t_REALCATEGORY" -> RuleActionType.SET_CATEGORY;
                        case "t_PAYEE" -> RuleActionType.SET_PAYEE;
                        case "t_comment" -> RuleActionType.SET_DESCRIPTION;
                        default -> throw new IllegalArgumentException("unsupportedAction");
                    };
            String value = leaf.getAttribute("value").trim();
            if (value.isEmpty()) fail("unsupportedAction");
            result.add(
                    ActionRequest.builder()
                            .actionType(type)
                            .actionValue(value)
                            .sortOrder(result.size())
                            .build());
        }
        if (result.isEmpty()) fail("unsupportedAction");
        return result;
    }

    private static void requireLeaf(Element leaf) {
        if (!children(leaf).isEmpty()
                || !leaf.hasAttribute("attribute")
                || !leaf.getAttribute("att2").isBlank()
                || !leaf.getAttribute("att2s").isBlank()
                || !leaf.getAttribute("value2").isBlank()) fail("unsupportedCondition");
    }

    private static void requireLogic(Element group, String logic) {
        if (group.hasAttribute("attribute")) fail("unsupportedCondition");
        for (Node node = group.getFirstChild(); node != null; node = node.getNextSibling()) {
            if (node.getNodeType() == Node.COMMENT_NODE
                    && !logic.equals(node.getNodeValue().trim())) {
                fail("unsupportedCondition");
            }
        }
    }

    private static List<Element> children(Element parent) {
        List<Element> result = new ArrayList<>();
        for (Node node = parent.getFirstChild(); node != null; node = node.getNextSibling()) {
            if (node instanceof Element element) {
                if (!"element".equals(element.getTagName())) fail("invalidXml");
                result.add(element);
            }
        }
        return result;
    }

    private static Element document(String xml) {
        if (xml.length() > 200_000) fail("invalidXml");
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            factory.setXIncludeAware(false);
            factory.setExpandEntityReferences(false);
            javax.xml.parsers.DocumentBuilder builder = factory.newDocumentBuilder();
            builder.setErrorHandler(
                    new DefaultHandler() {
                        @Override
                        public void fatalError(SAXParseException ex) throws SAXParseException {
                            throw ex;
                        }

                        @Override
                        public void error(SAXParseException ex) throws SAXParseException {
                            throw ex;
                        }
                    });
            // Accept only the export's inert marker, never a DTD or entity declaration.
            Element root =
                    builder.parse(
                                    new InputSource(
                                            new StringReader(xml.replace("<!DOCTYPE SKGML>", ""))))
                            .getDocumentElement();
            if (!"element".equals(root.getTagName())) fail("invalidXml");
            return root;
        } catch (ParserConfigurationException | SAXException | IOException ex) {
            throw new IllegalArgumentException("invalidXml", ex);
        }
    }

    private static void fail(String code) {
        throw new IllegalArgumentException(code);
    }
}
