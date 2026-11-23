package org.openfinance.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.text.DecimalFormat;
import java.text.Format;
import java.text.MessageFormat;
import java.text.ParsePosition;
import java.util.List;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.openfinance.dto.InsightResponse;
import org.openfinance.entity.InsightType;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Component;

/** Presents saved insight snapshots in the reader's language, preserving the original facts. */
@Component
@RequiredArgsConstructor
public class InsightLocalization {
    private final MessageSource messages;
    private final ObjectMapper mapper;

    public InsightResponse localize(InsightResponse insight, Locale locale) {
        Locale target =
                Locale.FRENCH.getLanguage().equals(locale.getLanguage())
                        ? Locale.FRENCH
                        : Locale.ENGLISH;
        if (insight.getMessageData() != null) {
            try {
                JsonNode pair =
                        mapper.reader()
                                .with(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
                                .readTree(insight.getMessageData());
                return insight.toBuilder()
                        .title(InsightMessages.render(pair.required("title"), messages, target))
                        .description(
                                InsightMessages.render(
                                        pair.required("description"), messages, target))
                        .build();
            } catch (JsonProcessingException
                    | IllegalArgumentException
                    | org.springframework.context.NoSuchMessageException ignored) {
                // An older or unknown message format can still display its saved text.
            }
        }
        Locale source = target.equals(Locale.FRENCH) ? Locale.ENGLISH : Locale.FRENCH;
        List<String> topics = topics(insight.getType());
        if (topics.isEmpty()) return insight;
        return insight.toBuilder()
                .title(translate(insight.getTitle(), topics, ".title", source, target))
                .description(translateDescription(insight.getDescription(), topics, source, target))
                .build();
    }

    private static List<String> topics(InsightType type) {
        return switch (type) {
            case UNUSUAL_TRANSACTION -> List.of(
                    "unusual.transaction.new.payee",
                    "unusual.transaction.large.amount",
                    "unusual.transaction.large.amount.no.payee");
            case SPENDING_ANOMALY -> List.of("spending.anomaly");
            case BUDGET_WARNING -> List.of("budget.exceeded", "budget.warning");
            case CASH_FLOW_WARNING -> List.of("low.balance", "cash.flow.negative");
            case SAVINGS_OPPORTUNITY -> List.of("subscription");
            case REGION_COMPARISON -> List.of("region.income", "region.networth");
            case TAX_OBLIGATION -> List.of("tax.estimate", "tax.deduction");
            case RECURRING_BILLING -> List.of(
                    "recurring.summary", "recurring.high.ratio", "recurring.competitor");
            default -> List.of();
        };
    }

    private String translateDescription(
            String text, List<String> topics, Locale source, Locale target) {
        String notice = "insight.model.estimate.notice";
        Object[] estimate = parse(text, notice, source);
        if (estimate == null) return translate(text, topics, ".description", source, target);
        estimate[0] = translate((String) estimate[0], topics, ".description", source, target);
        return messages.getMessage(notice, estimate, target);
    }

    private String translate(
            String text, List<String> topics, String suffix, Locale source, Locale target) {
        for (String topic : topics) {
            List<String> variants =
                    topic.equals("region.income") && suffix.equals(".description")
                            ? List.of("region.income.above", "region.income.below")
                            : List.of(topic);
            for (String variant : variants) {
                String key = "insight." + variant + suffix;
                Object[] args = parse(text, key, source);
                if (args != null) {
                    if (suffix.equals(".description"))
                        translateArguments(topic, args, source, target);
                    return messages.getMessage(key, args, target);
                }
            }
        }
        return text;
    }

    private void translateArguments(String topic, Object[] args, Locale source, Locale target) {
        int countryIndex =
                switch (topic) {
                    case "region.income" -> 3;
                    case "region.networth", "tax.estimate", "tax.deduction" -> 2;
                    default -> -1;
                };
        if (countryIndex >= 0) {
            for (String code : Locale.getISOCountries()) {
                Locale country = Locale.of("", code);
                if (country.getDisplayCountry(source).equals(args[countryIndex])) {
                    args[countryIndex] = country.getDisplayCountry(target);
                    break;
                }
            }
        }
        if (topic.equals("region.networth")) {
            args[4] =
                    translate(
                            (String) args[4],
                            List.of("region.networth.above", "region.networth.below"),
                            "",
                            source,
                            target);
        }
    }

    /**
     * Older records hold rendered text. Only accept a complete, lossless match to a known template.
     */
    private Object[] parse(String text, String key, Locale source) {
        if (text == null) return null;
        String pattern = messages.getMessage(key, null, source);
        if (!pattern.contains("{")) return pattern.equals(text) ? new Object[0] : null;
        MessageFormat format = new MessageFormat(pattern, source);
        for (Format argument : format.getFormats()) {
            if (argument instanceof DecimalFormat decimal) decimal.setParseBigDecimal(true);
        }
        ParsePosition pos = new ParsePosition(0);
        Object[] args = format.parse(text, pos);
        return args != null && pos.getIndex() == text.length() && text.equals(format.format(args))
                ? args
                : null;
    }
}
