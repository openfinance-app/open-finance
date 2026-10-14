package org.openfinance.util;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

public class TagsDeserializer extends JsonDeserializer<String> {

    @Override
    public String deserialize(JsonParser parser, DeserializationContext context)
            throws IOException {
        JsonToken token = parser.currentToken();
        if (token == JsonToken.VALUE_NULL) {
            return null;
        }
        if (token == JsonToken.VALUE_STRING) {
            String value = parser.getValueAsString();
            return (value == null || value.isBlank()) ? "" : value;
        }
        if (token == JsonToken.START_ARRAY) {
            List<String> tags = parser.readValueAs(new TypeReference<List<String>>() {});
            if (tags == null || tags.isEmpty()) {
                // An explicit empty value clears tags; null means no update to the mapper.
                return "";
            }
            String joined =
                    tags.stream()
                            .filter(tag -> tag != null && !tag.isBlank())
                            .map(String::trim)
                            .collect(Collectors.joining(","));
            return joined;
        }

        String value = parser.getValueAsString();
        return (value == null || value.isBlank()) ? null : value;
    }
}
