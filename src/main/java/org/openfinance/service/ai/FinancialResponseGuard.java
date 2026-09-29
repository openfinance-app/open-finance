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
                return FIGURE.matcher(response).find() ? rejected(locale) : response;
            JsonNode answer = JSON.readTree(cleaned);
            if (!answer.path("explanation").isTextual() || !answer.path("factIds").isArray())
                return rejected(locale);
            String explanation = answer.path("explanation").asText();
            if (FIGURE.matcher(explanation).find()) return rejected(locale);
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
