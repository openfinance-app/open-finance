// filepath: src/main/java/org/openfinance/converter/EncryptedBigDecimalConverter.java

package org.openfinance.converter;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import java.math.BigDecimal;
import javax.crypto.SecretKey;
import org.openfinance.config.SpringContextHolder;
import org.openfinance.security.EncryptionContext;
import org.openfinance.security.EncryptionService;

/**
 * JPA AttributeConverter that transparently encrypts {@link BigDecimal} fields by converting them
 * to their plain string representation, encrypting, and storing the ciphertext. On read, the
 * ciphertext is decrypted and parsed back to {@link BigDecimal}.
 *
 * <p>Apply to entity fields via {@code @Convert(converter = EncryptedBigDecimalConverter.class)}.
 * The database column type should be {@code TEXT} or {@code VARCHAR} (not {@code DECIMAL}) since
 * encrypted values are Base64 strings.
 *
 * <p>If no encryption key is available in the current thread, the converter attempts to parse the
 * raw column value directly as a BigDecimal (pass-through for unencrypted data).
 */
@Converter
public class EncryptedBigDecimalConverter implements AttributeConverter<BigDecimal, String> {

    private EncryptionService encryptionService;

    private EncryptionService getEncryptionService() {
        if (encryptionService == null) {
            encryptionService = SpringContextHolder.getBean(EncryptionService.class);
        }
        return encryptionService;
    }

    @Override
    public String convertToDatabaseColumn(BigDecimal attribute) {
        if (attribute == null) {
            return null;
        }

        SecretKey key = EncryptionContext.getKey();
        if (key == null) {
            return attribute.toPlainString();
        }

        return getEncryptionService().encrypt(attribute.toPlainString(), key);
    }

    @Override
    public BigDecimal convertToEntityAttribute(String dbData) {
        if (dbData == null) {
            return null;
        }

        SecretKey key = EncryptionContext.getKey();
        if (key == null) {
            return parseBigDecimal(dbData);
        }

        try {
            String decrypted = getEncryptionService().decrypt(dbData, key);
            return new BigDecimal(decrypted);
        } catch (Exception e) {
            // Fallback: data might be stored unencrypted (plain numeric string)
            return parseBigDecimal(dbData);
        }
    }

    private BigDecimal parseBigDecimal(String value) {
        try {
            return new BigDecimal(value);
        } catch (NumberFormatException e) {
            throw new IllegalStateException(
                    "Cannot decrypt or parse BigDecimal from column value. "
                            + "Ensure an encryption key is set in EncryptionContext.",
                    e);
        }
    }
}
