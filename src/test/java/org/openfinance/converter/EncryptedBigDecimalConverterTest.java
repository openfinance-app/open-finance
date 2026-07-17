package org.openfinance.converter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.openfinance.security.EncryptionContext;
import org.openfinance.security.EncryptionService;
import org.springframework.test.util.ReflectionTestUtils;

@DisplayName("EncryptedBigDecimalConverter Tests")
class EncryptedBigDecimalConverterTest {

    private static final SecretKey TEST_KEY =
            new SecretKeySpec(
                    "12345678901234567890123456789012".getBytes(StandardCharsets.UTF_8), "AES");

    private EncryptedBigDecimalConverter converter;

    @BeforeEach
    void setUp() {
        converter = new EncryptedBigDecimalConverter();
        ReflectionTestUtils.setField(
                converter,
                "encryptionService",
                new EncryptionService("AES/GCM/NoPadding", 12, 128));
    }

    @AfterEach
    void tearDown() {
        EncryptionContext.clear();
    }

    @Test
    @DisplayName("Encrypts BigDecimal values on write and decrypts them on read")
    void encryptsBigDecimalValuesOnWriteAndDecryptsThemOnRead() {
        BigDecimal amount = new BigDecimal("1234.5600");
        EncryptionContext.setKey(TEST_KEY);

        String storedValue = converter.convertToDatabaseColumn(amount);

        assertThat(storedValue).isNotEqualTo("1234.5600");
        assertThat(converter.convertToEntityAttribute(storedValue)).isEqualByComparingTo(amount);
    }

    @Test
    @DisplayName("Parses plain decimal values when no key is present")
    void parsesPlainDecimalValuesWhenNoKeyIsPresent() {
        assertThat(converter.convertToDatabaseColumn(new BigDecimal("42.10"))).isEqualTo("42.10");
        assertThat(converter.convertToEntityAttribute("42.10"))
                .isEqualByComparingTo(new BigDecimal("42.10"));
    }

    @Test
    @DisplayName("Falls back to parsing plain numeric strings when decryption fails")
    void fallsBackToParsingPlainNumericStringsWhenDecryptionFails() {
        EncryptionContext.setKey(TEST_KEY);

        assertThat(converter.convertToEntityAttribute("9876.54"))
                .isEqualByComparingTo(new BigDecimal("9876.54"));
    }

    @Test
    @DisplayName("Throws a clear error when a non numeric value cannot be decrypted")
    void throwsAClearErrorWhenANonNumericValueCannotBeDecrypted() {
        assertThatThrownBy(() -> converter.convertToEntityAttribute("not-a-number"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Ensure an encryption key is set in EncryptionContext");
    }
}
