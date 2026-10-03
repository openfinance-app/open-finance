package org.openfinance.util;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import java.io.IOException;

/** Rejects fractional counts instead of silently truncating JSON numbers. */
public class ExactIntegerDeserializer extends JsonDeserializer<Integer> {
    @Override
    public Integer deserialize(JsonParser parser, DeserializationContext context)
            throws IOException {
        if (!parser.currentToken().isNumeric()) {
            return (Integer) context.handleUnexpectedToken(Integer.class, parser);
        }
        try {
            return parser.getDecimalValue().intValueExact();
        } catch (ArithmeticException exception) {
            throw InvalidFormatException.from(
                    parser, "Value must be a whole number", parser.getText(), Integer.class);
        }
    }
}
