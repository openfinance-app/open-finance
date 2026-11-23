package org.openfinance.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.math.BigDecimal;
import java.util.Locale;
import org.openfinance.entity.Category;
import org.openfinance.entity.Insight;
import org.springframework.context.MessageSource;

/** Message keys and snapshot arguments, kept encrypted together with the insight. */
final class InsightMessages {
    private static final JsonNodeFactory JSON = JsonNodeFactory.instance;

    private InsightMessages() {}

    static ObjectNode message(String key, Object... arguments) {
        ObjectNode message = JSON.objectNode().put("key", key);
        ArrayNode args = message.putArray("args");
        for (Object argument : arguments) {
            switch (argument) {
                case JsonNode nested -> args.add(nested);
                case Number number -> args.add(new BigDecimal(number.toString()));
                case String text -> args.add(text);
                case null -> args.addNull();
                default -> throw new IllegalArgumentException(
                        "Unsupported insight message argument");
            }
        }
        return message;
    }

    static JsonNode categoryName(Category category) {
        if (category == null) return message("category.uncategorized");
        if (Boolean.TRUE.equals(category.getIsSystem())
                && category.getNameKey() != null
                && !category.getNameKey().isBlank()) {
            return message(category.getNameKey()).put("fallback", category.getName());
        }
        return JSON.textNode(category.getName());
    }

    static ObjectNode countryName(String isoCode) {
        return JSON.objectNode().put("country", isoCode);
    }

    static Insight.InsightBuilder withText(
            MessageSource source, Locale locale, ObjectNode title, ObjectNode description) {
        ObjectNode pair = JSON.objectNode();
        pair.set("title", title);
        pair.set("description", description);
        return Insight.builder()
                .messageData(pair.toString())
                .title(render(title, source, locale))
                .description(render(description, source, locale));
    }

    static String render(JsonNode message, MessageSource source, Locale locale) {
        JsonNode args = message.required("args");
        Object[] values = new Object[args.size()];
        for (int i = 0; i < values.length; i++) {
            JsonNode arg = args.get(i);
            if (arg.has("country"))
                values[i] = Locale.of("", arg.get("country").asText()).getDisplayCountry(locale);
            else if (arg.isObject()) values[i] = render(arg, source, locale);
            else if (arg.isNumber()) values[i] = arg.decimalValue();
            else values[i] = arg.textValue();
        }
        String key = message.required("key").asText();
        if (message.hasNonNull("fallback")) {
            return source.getMessage(key, values, message.get("fallback").asText(), locale);
        }
        return source.getMessage(key, values, locale);
    }
}
