// filepath: src/test/java/org/openfinance/security/EncryptionServiceTest.java

package org.openfinance.security;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Arrays;
import java.util.Base64;
import java.util.HashSet;
import java.util.Set;
import javax.crypto.SecretKey;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for EncryptionService.
 *
 * <p>Tests AES-256-GCM encryption/decryption, authentication, binary data handling, and security
 * properties.
 *
 * @author Open-Finance Development Team
 * @version 1.0
 * @since 2026-01-30
 */
@DisplayName("EncryptionService Tests")
class EncryptionServiceTest {

    private EncryptionService encryptionService;
    private KeyManagementService keyManagementService;
    private SecretKey testKey;

    @BeforeEach
    void setUp() {
        encryptionService = new EncryptionService("AES/GCM/NoPadding", 12, 128);
        keyManagementService = new KeyManagementServiceImpl(100000, 256);

        // Generate test key
        char[] password = "TestMasterPassword123!".toCharArray();
        byte[] salt = keyManagementService.generateSalt();
        testKey = keyManagementService.deriveKey(password, salt);
    }

    // ========== Basic Encryption/Decryption Tests ==========

    @Test
    @DisplayName("Should encrypt and decrypt text successfully")
    void shouldEncryptAndDecryptText() {
        // Given
        String plaintext = "Sensitive account number: 1234-5678-9012";

        // When
        String encrypted = encryptionService.encrypt(plaintext, testKey);
        String decrypted = encryptionService.decrypt(encrypted, testKey);

        // Then
        assertNotNull(encrypted, "Encrypted text should not be null");
        assertNotEquals(plaintext, encrypted, "Encrypted text should differ from plaintext");
        assertEquals(plaintext, decrypted, "Decrypted text should match original plaintext");
    }

    @Test
    @DisplayName("Should encrypt and decrypt empty string")
    void shouldEncryptAndDecryptEmptyString() {
        // Given
        String plaintext = "";

        // When
        String encrypted = encryptionService.encrypt(plaintext, testKey);
        String decrypted = encryptionService.decrypt(encrypted, testKey);

        // Then
        assertEquals(plaintext, decrypted, "Empty string should round-trip correctly");
    }

    @Test
    @DisplayName("Should encrypt and decrypt short text")
    void shouldEncryptAndDecryptShortText() {
        // Given
        String plaintext = "Hi";

        // When
        String encrypted = encryptionService.encrypt(plaintext, testKey);
        String decrypted = encryptionService.decrypt(encrypted, testKey);

        // Then
        assertEquals(plaintext, decrypted, "Short text should round-trip correctly");
    }

    @Test
    @DisplayName("Should encrypt and decrypt long text")
    void shouldEncryptAndDecryptLongText() {
        // Given
        String plaintext = "A".repeat(10000); // 10KB of data

        // When
        String encrypted = encryptionService.encrypt(plaintext, testKey);
        String decrypted = encryptionService.decrypt(encrypted, testKey);

        // Then
        assertEquals(plaintext, decrypted, "Long text should round-trip correctly");
    }

    @Test
    @DisplayName("Should encrypt and decrypt Unicode text")
    void shouldEncryptAndDecryptUnicodeText() {
        // Given
        String plaintext = "Account notes: 日本語テキスト, Emoji: 💰💵, Cyrillic: Привет";

        // When
        String encrypted = encryptionService.encrypt(plaintext, testKey);
        String decrypted = encryptionService.decrypt(encrypted, testKey);

        // Then
        assertEquals(plaintext, decrypted, "Unicode text should round-trip correctly");
    }

    @Test
    @DisplayName("Should encrypt and decrypt text with special characters")
    void shouldEncryptAndDecryptSpecialCharacters() {
        // Given
        String plaintext = "Special: !@#$%^&*()_+-=[]{}|;':\",./<>?`~";

        // When
        String encrypted = encryptionService.encrypt(plaintext, testKey);
        String decrypted = encryptionService.decrypt(encrypted, testKey);

        // Then
        assertEquals(plaintext, decrypted, "Special characters should round-trip correctly");
    }

    @Test
    @DisplayName("Should encrypt and decrypt multiline text")
    void shouldEncryptAndDecryptMultilineText() {
        // Given
        String plaintext =
                "Line 1: Account details\nLine 2: Notes\nLine 3: Comments\r\nLine 4: Tabs\there";

        // When
        String encrypted = encryptionService.encrypt(plaintext, testKey);
        String decrypted = encryptionService.decrypt(encrypted, testKey);

        // Then
        assertEquals(plaintext, decrypted, "Multiline text should round-trip correctly");
    }

    // ========== Security Properties Tests ==========

    @Test
    @DisplayName("Should produce different ciphertext for same plaintext due to unique IVs")
    void shouldProduceDifferentCiphertextForSamePlaintext() {
        // Given
        String plaintext = "Same plaintext";

        // When
        String encrypted1 = encryptionService.encrypt(plaintext, testKey);
        String encrypted2 = encryptionService.encrypt(plaintext, testKey);

        // Then
        assertNotEquals(
                encrypted1,
                encrypted2,
                "Same plaintext should produce different ciphertext (unique IVs)");
    }

    @Test
    @DisplayName("Should return Base64-encoded ciphertext")
    void shouldReturnBase64EncodedCiphertext() {
        // Given
        String plaintext = "Test data";

        // When
        String encrypted = encryptionService.encrypt(plaintext, testKey);

        // Then
        assertDoesNotThrow(
                () -> {
                    Base64.getDecoder().decode(encrypted);
                },
                "Encrypted data should be valid Base64");
    }

    @Test
    @DisplayName("Should include IV in ciphertext")
    void shouldIncludeIvInCiphertext() {
        // Given
        String plaintext = "Test";

        // When
        String encrypted = encryptionService.encrypt(plaintext, testKey);
        byte[] encryptedBytes = Base64.getDecoder().decode(encrypted);

        // Then
        // Minimum size: 12 bytes (IV) + at least 1 byte plaintext + 16 bytes (auth tag)
        assertTrue(
                encryptedBytes.length >= 12 + 1 + 16,
                "Ciphertext should include IV (12 bytes) + encrypted data + auth tag (16 bytes)");
    }

    @Test
    @DisplayName("Should generate unique IVs for multiple encryptions")
    void shouldGenerateUniqueIvs() {
        // Given
        String plaintext = "Same text";
        Set<String> uniqueIvs = new HashSet<>();
        int samples = 100;

        // When
        for (int i = 0; i < samples; i++) {
            String encrypted = encryptionService.encrypt(plaintext, testKey);
            byte[] encryptedBytes = Base64.getDecoder().decode(encrypted);
            byte[] iv = Arrays.copyOfRange(encryptedBytes, 0, 12);
            uniqueIvs.add(Arrays.toString(iv));
        }

        // Then
        assertEquals(samples, uniqueIvs.size(), "All IVs should be unique");
    }

    // ========== Nullable and Batch Helpers (New in v2) ==========

    @Test
    @DisplayName("Should return null when encryptNullable is called with null")
    void shouldReturnNullWhenEncryptNullableWithNull() {
        assertNull(encryptionService.encryptNullable(null, testKey));
    }

    @Test
    @DisplayName("Should encrypt successfully when encryptNullable is called with text")
    void shouldEncryptWhenEncryptNullableWithText() {
        String plaintext = "Test data";
        String encrypted = encryptionService.encryptNullable(plaintext, testKey);
        assertNotNull(encrypted);
        assertEquals(plaintext, encryptionService.decrypt(encrypted, testKey));
    }

    @Test
    @DisplayName("Should return null when decryptNullable is called with null or blank")
    void shouldReturnNullWhenDecryptNullableWithNullOrBlank() {
        assertNull(encryptionService.decryptNullable(null, testKey));
        assertNull(encryptionService.decryptNullable("", testKey));
        assertNull(encryptionService.decryptNullable("   ", testKey));
    }

    @Test
    @DisplayName("Should return empty list when encryptBatch is called with null or empty list")
    void shouldReturnEmptyListWhenEncryptBatchWithNullOrEmpty() {
        assertTrue(encryptionService.encryptBatch(null, testKey).isEmpty());
        assertTrue(
                encryptionService
                        .encryptBatch(java.util.Collections.emptyList(), testKey)
                        .isEmpty());
    }

    @Test
    @DisplayName("Should encrypt all strings in batch")
    void shouldEncryptBatch() {
        java.util.List<String> plaintexts = java.util.Arrays.asList("one", "two", null, "three");
        java.util.List<String> ciphertexts = encryptionService.encryptBatch(plaintexts, testKey);

        assertEquals(4, ciphertexts.size());
        assertEquals("one", encryptionService.decrypt(ciphertexts.get(0), testKey));
        assertEquals("two", encryptionService.decrypt(ciphertexts.get(1), testKey));
        assertNull(ciphertexts.get(2));
        assertEquals("three", encryptionService.decrypt(ciphertexts.get(3), testKey));
    }

    @Test
    @DisplayName("Should decrypt all strings in batch")
    void shouldDecryptBatch() {
        java.util.List<String> plaintexts = java.util.Arrays.asList("one", "two", "three");
        java.util.List<String> ciphertexts = encryptionService.encryptBatch(plaintexts, testKey);
        java.util.List<String> decrypted = encryptionService.decryptBatch(ciphertexts, testKey);

        assertEquals(plaintexts, decrypted);
    }

    // ========== Binary Data Tests ==========

    @Test
    @DisplayName("Should encrypt and decrypt binary data")
    void shouldEncryptAndDecryptBinaryData() {
        // Given
        byte[] binaryData = new byte[] {0x00, 0x01, 0x02, (byte) 0xFF, (byte) 0xFE, 0x7F};

        // When
        byte[] encrypted = encryptionService.encryptBytes(binaryData, testKey);
        byte[] decrypted = encryptionService.decryptBytes(encrypted, testKey);

        // Then
        assertArrayEquals(binaryData, decrypted, "Binary data should round-trip correctly");
    }

    @Test
    @DisplayName("Should encrypt and decrypt large binary file")
    void shouldEncryptAndDecryptLargeBinaryFile() {
        // Given - simulate 1MB file
        byte[] largeData = new byte[1024 * 1024];
        Arrays.fill(largeData, (byte) 0xAB);

        // When
        byte[] encrypted = encryptionService.encryptBytes(largeData, testKey);
        byte[] decrypted = encryptionService.decryptBytes(encrypted, testKey);

        // Then
        assertArrayEquals(largeData, decrypted, "Large binary file should round-trip correctly");
    }

    @Test
    @DisplayName("Should encrypt and decrypt empty binary data")
    void shouldEncryptAndDecryptEmptyBinaryData() {
        // Given
        byte[] emptyData = new byte[0];

        // When
        byte[] encrypted = encryptionService.encryptBytes(emptyData, testKey);
        byte[] decrypted = encryptionService.decryptBytes(encrypted, testKey);

        // Then
        assertArrayEquals(emptyData, decrypted, "Empty binary data should round-trip correctly");
    }

    // ========== Authentication/Tampering Detection Tests ==========

    @Test
    @DisplayName("Should detect tampering when ciphertext is modified")
    void shouldDetectTamperingWhenCiphertextModified() {
        // Given
        String plaintext = "Sensitive data";
        String encrypted = encryptionService.encrypt(plaintext, testKey);

        // When - tamper with ciphertext
        byte[] encryptedBytes = Base64.getDecoder().decode(encrypted);
        encryptedBytes[20] ^= 0x01; // Flip a bit in the ciphertext
        String tamperedEncrypted = Base64.getEncoder().encodeToString(encryptedBytes);

        // Then
        assertThrows(
                IllegalStateException.class,
                () -> {
                    encryptionService.decrypt(tamperedEncrypted, testKey);
                },
                "Decryption should fail for tampered ciphertext");
    }

    @Test
    @DisplayName("Should detect tampering when IV is modified")
    void shouldDetectTamperingWhenIvModified() {
        // Given
        String plaintext = "Secret data";
        String encrypted = encryptionService.encrypt(plaintext, testKey);

        // When - tamper with IV
        byte[] encryptedBytes = Base64.getDecoder().decode(encrypted);
        encryptedBytes[5] ^= 0x01; // Flip a bit in the IV
        String tamperedEncrypted = Base64.getEncoder().encodeToString(encryptedBytes);

        // Then
        assertThrows(
                IllegalStateException.class,
                () -> {
                    encryptionService.decrypt(tamperedEncrypted, testKey);
                },
                "Decryption should fail when IV is modified");
    }

    @Test
    @DisplayName("Should detect tampering when auth tag is corrupted")
    void shouldDetectTamperingWhenAuthTagCorrupted() {
        // Given
        String plaintext = "Protected data";
        String encrypted = encryptionService.encrypt(plaintext, testKey);

        // When - tamper with auth tag at the end
        byte[] encryptedBytes = Base64.getDecoder().decode(encrypted);
        encryptedBytes[encryptedBytes.length - 1] ^= 0x01; // Flip a bit in auth tag
        String tamperedEncrypted = Base64.getEncoder().encodeToString(encryptedBytes);

        // Then
        assertThrows(
                IllegalStateException.class,
                () -> {
                    encryptionService.decrypt(tamperedEncrypted, testKey);
                },
                "Decryption should fail when authentication tag is corrupted");
    }

    @Test
    @DisplayName("Should fail decryption with wrong key")
    void shouldFailDecryptionWithWrongKey() {
        // Given
        String plaintext = "Secret message";
        String encrypted = encryptionService.encrypt(plaintext, testKey);

        // Generate different key
        char[] wrongPassword = "WrongPassword456!".toCharArray();
        byte[] wrongSalt = keyManagementService.generateSalt();
        SecretKey wrongKey = keyManagementService.deriveKey(wrongPassword, wrongSalt);

        // Then
        assertThrows(
                IllegalStateException.class,
                () -> {
                    encryptionService.decrypt(encrypted, wrongKey);
                },
                "Decryption should fail with wrong key");
    }

    // ========== Edge Cases and Validation Tests ==========

    @Test
    @DisplayName("Should throw exception when encrypting null plaintext")
    void shouldThrowExceptionWhenEncryptingNullPlaintext() {
        // When/Then
        assertThrows(
                IllegalArgumentException.class,
                () -> {
                    encryptionService.encrypt(null, testKey);
                },
                "Encrypting null plaintext should throw IllegalArgumentException");
    }

    @Test
    @DisplayName("Should throw exception when decrypting null ciphertext")
    void shouldThrowExceptionWhenDecryptingNullCiphertext() {
        // When/Then
        assertThrows(
                IllegalArgumentException.class,
                () -> {
                    encryptionService.decrypt(null, testKey);
                },
                "Decrypting null ciphertext should throw IllegalArgumentException");
    }

    @Test
    @DisplayName("Should throw exception when decrypting too short ciphertext")
    void shouldThrowExceptionWhenDecryptingTooShortCiphertext() {
        // Given - create Base64 of data shorter than IV size
        byte[] tooShort = new byte[5];
        String tooShortBase64 = Base64.getEncoder().encodeToString(tooShort);

        // When/Then
        assertThrows(
                IllegalArgumentException.class,
                () -> {
                    encryptionService.decrypt(tooShortBase64, testKey);
                },
                "Decrypting too short ciphertext should throw IllegalArgumentException");
    }

    // ========== Constructor Validation Tests ==========

    @Test
    @DisplayName("Should throw exception when constructed with wrong algorithm")
    void shouldThrowExceptionWhenConstructedWithWrongAlgorithm() {
        // When/Then
        assertThrows(
                IllegalArgumentException.class,
                () -> {
                    new EncryptionService("AES/CBC/PKCS5Padding", 12, 128);
                },
                "Constructor should reject non-GCM algorithm");
    }

    @Test
    @DisplayName("Should throw exception when constructed with wrong IV size")
    void shouldThrowExceptionWhenConstructedWithWrongIvSize() {
        // When/Then
        assertThrows(
                IllegalArgumentException.class,
                () -> {
                    new EncryptionService("AES/GCM/NoPadding", 16, 128);
                },
                "Constructor should reject IV size != 12");
    }

    @Test
    @DisplayName("Should throw exception when constructed with invalid tag size")
    void shouldThrowExceptionWhenConstructedWithInvalidTagSize() {
        // When/Then
        assertThrows(
                IllegalArgumentException.class,
                () -> {
                    new EncryptionService("AES/GCM/NoPadding", 12, 64);
                },
                "Constructor should reject invalid tag size");
    }

    // ========== Performance and Large Data Tests ==========

    @Test
    @DisplayName("Should handle very large text efficiently")
    void shouldHandleVeryLargeTextEfficiently() {
        // Given - 5MB of text (reduced from 10MB for CI speed)
        String largeText = "X".repeat(5 * 1024 * 1024);

        // When
        String encrypted = encryptionService.encrypt(largeText, testKey);
        String decrypted = encryptionService.decrypt(encrypted, testKey);

        // Then
        assertEquals(largeText, decrypted, "Very large text should round-trip correctly");
    }
}
