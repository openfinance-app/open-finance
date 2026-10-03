package org.openfinance.service.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/** Renders monetary claims from typed facts; model text is never treated as verified arithmetic. */
public final class FinancialResponseGuard {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Pattern FIGURE =
            Pattern.compile(
                    "[\\p{N}\\p{Sc}]|\\b(?:EUR|USD|GBP|CHF|JPY|CAD|AUD|CNY|euros?|dollars?|pounds?|livres?|francs?|yens?|yuans?|roubles?|rubles?|rupees?)\\b",
                    Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    private static final Pattern WRITTEN_FIGURE =
            Pattern.compile(
                    "\\b(?:zero|two|three|four|five|six|seven|eight|nine|ten|eleven|twelve|"
                            + "thirteen|fourteen|fifteen|sixteen|seventeen|eighteen|nineteen|twenty|"
                            + "thirty|forty|fifty|sixty|seventy|eighty|ninety|hundred|thousand|"
                            + "millions?|billions?|trillions?|half|quarter|dozen|twice|thrice|"
                            + "zéro|deux|trois|quatre|cinq|sept|huit|neuf|dix|onze|douze|treize|"
                            + "quatorze|quinze|seize|vingt|trente|quarante|cinquante|soixante|"
                            + "centaines?|cents?|milliers?|mille|milliards?|demi|moitié|quart)\\b"
                            + "|\\b(?:one|un|une)\\b(?!\\s+\\p{L})",
                    Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CHARACTER_CLASS);

    private FinancialResponseGuard() {}

    public static String verify(String response, String context, Locale locale) {
        if (response == null || response.isBlank())
            throw new AIProviderException("AI", "Empty answer");
        try {
            String cleaned =
                    response.trim()
                            .replaceFirst("^```(?:json)?\\s*", "")
                            .replaceFirst("\\s*```$", "");
            if (!cleaned.startsWith("{"))
                return containsFigure(response) ? rejected(locale) : response;
            JsonNode answer = JSON.readTree(cleaned);
            if (!answer.path("explanation").isTextual() || !answer.path("factIds").isArray())
                return rejected(locale);
            String explanation = answer.path("explanation").asText();
            if (containsFigure(explanation)) return rejected(locale);
            Map<String, FinancialFact> facts = facts(context);
            Set<String> selected = new LinkedHashSet<>();
            for (JsonNode id : answer.path("factIds")) {
                if (!id.isTextual() || !facts.containsKey(id.asText())) return rejected(locale);
                selected.add(id.asText());
            }
            StringBuilder result = new StringBuilder(explanation.strip());
            for (String id : selected) {
                FinancialFact fact = facts.get(id);
                result.append("\n\n").append(escape(fact.label()));
                if (!fact.entity().isBlank()) result.append(" — ").append(escape(fact.entity()));
                result.append(": **")
                        .append(fact.amount())
                        .append(' ')
                        .append(fact.currency())
                        .append("** (")
                        .append(fact.period())
                        .append(')');
            }
            return result.isEmpty() ? rejected(locale) : result.toString();
        } catch (com.fasterxml.jackson.core.JsonProcessingException | RuntimeException ex) {
            return rejected(locale);
        }
    }

    /** A rejected model answer must never be saved as a successful chat response. */
    public static String verifyStructured(String response, String context, Locale locale) {
        if (response == null || !response.stripLeading().startsWith("{"))
            throw new AIProviderException("AI", "Invalid structured financial answer");
        String verified = verify(response, context, locale);
        if (verified.equals(rejected(locale))) {
            // A correct fact must not be lost because prose repeats an amount or an entity
            // such as "Round Two Checking". Discard that prose; never accept its arithmetic.
            try {
                JsonNode answer = JSON.readTree(response);
                if (answer.path("explanation").isTextual()
                        && answer.path("factIds").isArray()
                        && !answer.path("factIds").isEmpty()
                        && containsFigure(answer.path("explanation").asText())) {
                    ((com.fasterxml.jackson.databind.node.ObjectNode) answer)
                            .put("explanation", "");
                    verified = verify(JSON.writeValueAsString(answer), context, locale).strip();
                }
            } catch (com.fasterxml.jackson.core.JsonProcessingException ex) {
                throw new AIProviderException("AI", "Invalid structured financial answer", ex);
            }
            if (verified.equals(rejected(locale)))
                throw new AIProviderException("AI", "Unverified financial answer");
        }
        return verified;
    }

    public static JsonNode responseSchema(String context) {
        com.fasterxml.jackson.databind.node.ObjectNode schema = JSON.createObjectNode();
        schema.put("type", "object").put("additionalProperties", false);
        schema.putArray("required").add("explanation").add("factIds");
        com.fasterxml.jackson.databind.node.ObjectNode properties = schema.putObject("properties");
        properties.putObject("explanation").put("type", "string");
        com.fasterxml.jackson.databind.node.ObjectNode ids = properties.putObject("factIds");
        ids.put("type", "array");
        com.fasterxml.jackson.databind.node.ObjectNode item =
                ids.putObject("items").put("type", "string");
        try {
            Set<String> allowed = facts(context).keySet();
            if (allowed.isEmpty()) ids.put("maxItems", 0);
            else {
                com.fasterxml.jackson.databind.node.ArrayNode values = item.putArray("enum");
                allowed.forEach(values::add);
            }
        } catch (com.fasterxml.jackson.core.JsonProcessingException ex) {
            throw new IllegalStateException("Invalid financial facts", ex);
        }
        return schema;
    }

    private static boolean containsFigure(String text) {
        return FIGURE.matcher(text).find() || WRITTEN_FIGURE.matcher(text).find();
    }

    private static Map<String, FinancialFact> facts(String context)
            throws com.fasterxml.jackson.core.JsonProcessingException {
        Map<String, FinancialFact> facts = new LinkedHashMap<>();
        for (String line : context.split("\\R")) {
            if (line.startsWith("[FACT] ")) {
                FinancialFact fact = JSON.readValue(line.substring(7), FinancialFact.class);
                facts.put(fact.id(), fact);
            }
        }
        return facts;
    }

    private static String escape(String text) {
        return text.replaceAll("[\\r\\n\\p{Cntrl}]", " ")
                .replaceAll("([\\\\`*_{}\\[\\]()#+.!<>])", "\\\\$1");
    }

    private static String rejected(Locale locale) {
        return "fr".equals(locale.getLanguage())
                ? "Je n’ai pas pu vérifier les montants de cette réponse. Consultez les soldes et le flux de trésorerie du tableau de bord."
                : "I could not verify the amounts in this response. Please check the account balances and cash flow on your dashboard.";
    }
}
