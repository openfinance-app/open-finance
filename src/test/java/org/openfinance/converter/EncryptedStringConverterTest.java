package org.openfinance.converter;

import static org.assertj.core.api.Assertions.assertThat;

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

@DisplayName("EncryptedStringConverter Tests")
class EncryptedStringConverterTest {

    private static final SecretKey TEST_KEY =
            new SecretKeySpec(
                    "12345678901234567890123456789012".getBytes(StandardCharsets.UTF_8), "AES");

    private EncryptedStringConverter converter;

    @BeforeEach
    void setUp() {
        converter = new EncryptedStringConverter();
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
    @DisplayName("Encrypts on write and decrypts on read when a key is present")
    void encryptsOnWriteAndDecryptsOnReadWhenAKeyIsPresent() {
        String plaintext = "Primary household account";
        EncryptionContext.setKey(TEST_KEY);

        String storedValue = converter.convertToDatabaseColumn(plaintext);

        assertThat(storedValue).isNotEqualTo(plaintext);
        assertThat(converter.convertToEntityAttribute(storedValue)).isEqualTo(plaintext);
    }

    @Test
    @DisplayName("Passes through values unchanged when no key is present")
    void passesThroughValuesUnchangedWhenNoKeyIsPresent() {
        assertThat(converter.convertToDatabaseColumn("Visible value")).isEqualTo("Visible value");
        assertThat(converter.convertToEntityAttribute("Visible value")).isEqualTo("Visible value");
        assertThat(converter.convertToDatabaseColumn(null)).isNull();
        assertThat(converter.convertToEntityAttribute(null)).isNull();
    }

    @Test
    @DisplayName("Returns the original value when decryption fails")
    void returnsTheOriginalValueWhenDecryptionFails() {
        EncryptionContext.setKey(TEST_KEY);

        assertThat(converter.convertToEntityAttribute("not-encrypted-data"))
                .isEqualTo("not-encrypted-data");
    }
}
