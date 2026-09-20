package org.openfinance.service.ai;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Rejects unsupported monetary figures before an assistant answer reaches the user or history. */
public final class FinancialResponseGuard {
    private static final String NUMBER = "[-+−]?\\s*\\d+(?:[\\s\\u00a0\\u202f.,]\\d+)*";
    private static final Pattern MONEY =
            Pattern.compile(
                    "(?:(?<prefixSign>[-+−])?\\s*(?<code>[A-Z]{3,5}|[€$£])\\s*(?<before>"
                            + NUMBER
                            + ")|(?<after>"
                            + NUMBER
                            + ")\\s*(?<suffix>[A-Z]{3,5}|[€$£]))");

    private FinancialResponseGuard() {}

    public static String verify(String response, String context, Locale locale) {
        if (response == null || !context.contains("[VERIFIED_FINANCIAL_DATA]")) return response;
        Set<String> facts = monetaryValues(context);
        if (facts.containsAll(monetaryValues(response))) return response;
        return "fr".equals(locale.getLanguage())
                ? "Je n’ai pas pu vérifier les montants de cette réponse. Consultez les soldes et le flux de trésorerie du tableau de bord."
                : "I could not verify the amounts in this response. Please check the account balances and cash flow on your dashboard.";
    }

    private static Set<String> monetaryValues(String text) {
        Set<String> values = new HashSet<>();
        Matcher matcher = MONEY.matcher(text);
        while (matcher.find()) {
            String code =
                    matcher.group("code") != null ? matcher.group("code") : matcher.group("suffix");
            code =
                    switch (code) {
                        case "€" -> "EUR";
                        case "$" -> "USD";
                        case "£" -> "GBP";
                        default -> code;
                    };
            String number =
                    matcher.group("before") != null
                            ? matcher.group("before")
                            : matcher.group("after");
            number = number.replaceAll("[\\s\\u00a0\\u202f]", "").replace('−', '-');
            String prefixSign = matcher.group("prefixSign");
            if (prefixSign != null && (prefixSign.equals("-") || prefixSign.equals("−"))) {
                number = "-" + number.replaceFirst("^[+-]", "");
            }
            int separator = Math.max(number.lastIndexOf('.'), number.lastIndexOf(','));
            // Financial context uses two decimals. Also accept grouped whole amounts and French
            // decimals.
            if (separator >= 0 && number.length() - separator - 1 != 3) {
                number =
                        number.substring(0, separator).replaceAll("[.,]", "")
                                + "."
                                + number.substring(separator + 1);
            } else {
                number = number.replaceAll("[.,]", "");
            }
            values.add(code + ":" + new BigDecimal(number).stripTrailingZeros().toPlainString());
        }
        return values;
    }
}
