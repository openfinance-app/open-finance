package org.openfinance.util;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;

/** Converts the storage clock's timestamps to UTC instants without changing calendar dates. */
public class ServerTimestampSerializer extends JsonSerializer<LocalDateTime> {
    protected ZoneId storageZone() {
        return ZoneId.systemDefault();
    }

    @Override
    public void serialize(LocalDateTime value, JsonGenerator generator, SerializerProvider provider)
            throws IOException {
        generator.writeString(value.atZone(storageZone()).toInstant().toString());
    }

    /** Operation history uses UTC even when the server's local clock uses another zone. */
    public static class Utc extends ServerTimestampSerializer {
        @Override
        protected ZoneId storageZone() {
            return ZoneOffset.UTC;
        }
    }
}
