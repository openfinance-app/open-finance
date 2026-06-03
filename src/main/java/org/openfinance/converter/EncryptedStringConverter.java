// filepath: src/main/java/org/openfinance/converter/EncryptedStringConverter.java

package org.openfinance.converter;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import javax.crypto.SecretKey;
import org.openfinance.config.SpringContextHolder;
import org.openfinance.security.EncryptionContext;
import org.openfinance.security.EncryptionService;

/**
 * JPA AttributeConverter that transparently encrypts {@code String} fields on
 * write and decrypts on
 * read using the per-request key stored in {@link EncryptionContext}.
 *
 * <p>
 * Apply to entity fields via
 * {@code @Convert(converter = EncryptedStringConverter.class)}.
 *
 * <p>
 * If no encryption key is available in the current thread (e.g. during Flyway
 * migrations or
 * anonymous access), the value passes through unmodified. This allows safe
 * startup and migration
 * scenarios.
 */
@Converter
public class EncryptedStringConverter implements AttributeConverter<String, String> {

    private EncryptionService encryptionService;

    private EncryptionService getEncryptionService() {
        if (encryptionService == null) {
            encryptionService = SpringContextHolder.getBean(EncryptionService.class);
        }
        return encryptionService;
    }

    @Override
    public String convertToDatabaseColumn(String attribute) {
        if (attribute == null) {
            return null;
        }

        SecretKey key = EncryptionContext.getKey();
        if (key == null) {
            return attribute;
        }

        return getEncryptionService().encrypt(attribute, key);
    }

    @Override
    public String convertToEntityAttribute(String dbData) {
        if (dbData == null) {
            return null;
        }

        SecretKey key = EncryptionContext.getKey();
        if (key == null) {
            return dbData;
        }

        try {
            return getEncryptionService().decrypt(dbData, key);
        } catch (Exception e) {
            // If decryption fails (e.g. data was stored unencrypted), return as-is
            return dbData;
        }
    }
}
