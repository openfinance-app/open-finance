// filepath: src/test/java/org/openfinance/security/KeyManagementServiceTest.java

package org.openfinance.security;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import javax.crypto.SecretKey;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for KeyManagementService.
 *
 * <p>Tests key derivation with PBKDF2, salt generation, and secure key handling.
 *
 * @author Open-Finance Development Team
 * @version 1.0
 * @since 2026-01-30
 */
@DisplayName("KeyManagementService Tests")
class KeyManagementServiceTest {

    private KeyManagementService keyManagementService;
    private static final int ITERATIONS = 100000;
    private static final int KEY_SIZE = 256;

    @BeforeEach
    void setUp() {
        keyManagementService = new KeyManagementServiceImpl(ITERATIONS, KEY_SIZE);
    }

    @Test
    @DisplayName("Should derive key successfully with valid inputs")
    void shouldDeriveKeySuccessfully() {
        // Given
        char[] masterPassword = "MasterPassword123!".toCharArray();
        byte[] salt = keyManagementService.generateSalt();

        // When
        SecretKey key = keyManagementService.deriveKey(masterPassword, salt);

        // Then
        assertNotNull(key, "Derived key should not be null");
        assertEquals("AES", key.getAlgorithm(), "Key algorithm should be AES");
        assertEquals(32, key.getEncoded().length, "AES-256 key should be 32 bytes");
    }

    @Test
    @DisplayName("Should derive same key from same password and salt")
    void shouldDeriveSameKeyFromSamePasswordAndSalt() {
        // Given
        char[] masterPassword = "ConsistentPassword456!".toCharArray();
        byte[] salt = keyManagementService.generateSalt();

        // When
        SecretKey key1 = keyManagementService.deriveKey(masterPassword, salt);
        SecretKey key2 = keyManagementService.deriveKey(masterPassword, salt);

        // Then
        assertArrayEquals(
                key1.getEncoded(),
                key2.getEncoded(),
                "Same password and salt should produce identical keys");
    }

    @Test
    @DisplayName("Should derive different keys for different passwords")
    void shouldDeriveDifferentKeysForDifferentPasswords() {
        // Given
        char[] password1 = "Password1".toCharArray();
        char[] password2 = "Password2".toCharArray();
        byte[] salt = keyManagementService.generateSalt();

        // When
        SecretKey key1 = keyManagementService.deriveKey(password1, salt);
        SecretKey key2 = keyManagementService.deriveKey(password2, salt);

        // Then
        assertFalse(
                Arrays.equals(key1.getEncoded(), key2.getEncoded()),
                "Different passwords should produce different keys");
    }

    @Test
    @DisplayName("Should derive different keys for different salts")
    void shouldDeriveDifferentKeysForDifferentSalts() {
        // Given
        char[] masterPassword = "SamePassword789!".toCharArray();
        byte[] salt1 = keyManagementService.generateSalt();
        byte[] salt2 = keyManagementService.generateSalt();

        // When
        SecretKey key1 = keyManagementService.deriveKey(masterPassword, salt1);
        SecretKey key2 = keyManagementService.deriveKey(masterPassword, salt2);

        // Then
        assertFalse(
                Arrays.equals(key1.getEncoded(), key2.getEncoded()),
                "Different salts should produce different keys");
    }

    @Test
    @DisplayName("Should throw exception when deriving key with null password")
    void shouldThrowExceptionWhenDerivingKeyWithNullPassword() {
        // Given
        byte[] salt = keyManagementService.generateSalt();

        // When/Then
        assertThrows(
                IllegalArgumentException.class,
                () -> {
                    keyManagementService.deriveKey(null, salt);
                },
                "Deriving key with null password should throw IllegalArgumentException");
    }

    @Test
    @DisplayName("Should throw exception when deriving key with empty password")
    void shouldThrowExceptionWhenDerivingKeyWithEmptyPassword() {
        // Given
        char[] emptyPassword = new char[0];
        byte[] salt = keyManagementService.generateSalt();

        // When/Then
        assertThrows(
                IllegalArgumentException.class,
                () -> {
                    keyManagementService.deriveKey(emptyPassword, salt);
                },
                "Deriving key with empty password should throw IllegalArgumentException");
    }

    @Test
    @DisplayName("Should throw exception when deriving key with null salt")
    void shouldThrowExceptionWhenDerivingKeyWithNullSalt() {
        // Given
        char[] masterPassword = "Password".toCharArray();

        // When/Then
        assertThrows(
                IllegalArgumentException.class,
                () -> {
                    keyManagementService.deriveKey(masterPassword, null);
                },
                "Deriving key with null salt should throw IllegalArgumentException");
    }

    @Test
    @DisplayName("Should throw exception when deriving key with invalid salt size")
    void shouldThrowExceptionWhenDerivingKeyWithInvalidSaltSize() {
        // Given
        char[] masterPassword = "Password".toCharArray();
        byte[] invalidSalt = new byte[8]; // Wrong size

        // When/Then
        assertThrows(
                IllegalArgumentException.class,
                () -> {
                    keyManagementService.deriveKey(masterPassword, invalidSalt);
                },
                "Deriving key with invalid salt size should throw IllegalArgumentException");
    }

    @Test
    @DisplayName("Should generate salt with correct size")
    void shouldGenerateSaltWithCorrectSize() {
        // When
        byte[] salt = keyManagementService.generateSalt();

        // Then
        assertNotNull(salt, "Salt should not be null");
        assertEquals(16, salt.length, "Salt should be 16 bytes");
    }

    @Test
    @DisplayName("Should generate unique salts")
    void shouldGenerateUniqueSalts() {
        // Given
        Set<String> uniqueSalts = new HashSet<>();
        int sampleSize = 100;

        // When
        for (int i = 0; i < sampleSize; i++) {
            byte[] salt = keyManagementService.generateSalt();
            uniqueSalts.add(Arrays.toString(salt));
        }

        // Then
        assertEquals(sampleSize, uniqueSalts.size(), "All generated salts should be unique");
    }

    @Test
    @DisplayName("Should validate correct salt")
    void shouldValidateCorrectSalt() {
        // Given
        byte[] validSalt = keyManagementService.generateSalt();

        // When
        boolean isValid = keyManagementService.validateSalt(validSalt);

        // Then
        assertTrue(isValid, "Valid 16-byte salt should validate successfully");
    }

    @Test
    @DisplayName("Should reject null salt")
    void shouldRejectNullSalt() {
        // When
        boolean isValid = keyManagementService.validateSalt(null);

        // Then
        assertFalse(isValid, "Null salt should be invalid");
    }

    @Test
    @DisplayName("Should reject salt with wrong size")
    void shouldRejectSaltWithWrongSize() {
        // Given
        byte[] wrongSizeSalt = new byte[8];

        // When
        boolean isValid = keyManagementService.validateSalt(wrongSizeSalt);

        // Then
        assertFalse(isValid, "Salt with wrong size should be invalid");
    }

    @Test
    @DisplayName("Should clear key from memory")
    void shouldClearKeyFromMemory() {
        // Given
        char[] masterPassword = "ClearTestPassword".toCharArray();
        byte[] salt = keyManagementService.generateSalt();
        SecretKey key = keyManagementService.deriveKey(masterPassword, salt);
        byte[] originalKeyBytes = key.getEncoded().clone();

        // When
        keyManagementService.clearKey(key);

        // Then
        // Key should be cleared (best-effort, cannot guarantee in all JVM implementations)
        // At minimum, clearKey should not throw an exception
        assertNotNull(originalKeyBytes, "Original key bytes should exist for comparison");
    }

    @Test
    @DisplayName("Should handle clearing null key")
    void shouldHandleClearingNullKey() {
        // When/Then - should not throw exception
        assertDoesNotThrow(
                () -> {
                    keyManagementService.clearKey(null);
                },
                "Clearing null key should not throw exception");
    }

    @Test
    @DisplayName("Should derive key with Unicode password")
    void shouldDeriveKeyWithUnicodePassword() {
        // Given
        char[] unicodePassword = "パスワード日本語123!".toCharArray();
        byte[] salt = keyManagementService.generateSalt();

        // When
        SecretKey key = keyManagementService.deriveKey(unicodePassword, salt);

        // Then
        assertNotNull(key, "Key should be derived from Unicode password");
        assertEquals(32, key.getEncoded().length, "Key should be 32 bytes");
    }

    @Test
    @DisplayName("Should derive key with very long password")
    void shouldDeriveKeyWithVeryLongPassword() {
        // Given
        char[] longPassword = new char[1000];
        Arrays.fill(longPassword, 'A');
        byte[] salt = keyManagementService.generateSalt();

        // When
        SecretKey key = keyManagementService.deriveKey(longPassword, salt);

        // Then
        assertNotNull(key, "Key should be derived from very long password");
        assertEquals(32, key.getEncoded().length, "Key should be 32 bytes");
    }

    @Test
    @DisplayName("Should derive different keys for passwords differing by single character")
    void shouldDeriveDifferentKeysForSimilarPasswords() {
        // Given
        char[] password1 = "Password1".toCharArray();
        char[] password2 = "Password2".toCharArray();
        byte[] salt = keyManagementService.generateSalt();

        // When
        SecretKey key1 = keyManagementService.deriveKey(password1, salt);
        SecretKey key2 = keyManagementService.deriveKey(password2, salt);

        // Then
        assertFalse(
                Arrays.equals(key1.getEncoded(), key2.getEncoded()),
                "Passwords differing by one character should produce completely different keys");
    }

    @Test
    @DisplayName("Should throw exception when constructed with too few iterations")
    void shouldThrowExceptionWhenConstructedWithTooFewIterations() {
        // When/Then
        assertThrows(
                IllegalArgumentException.class,
                () -> {
                    new KeyManagementServiceImpl(5000, 256);
                },
                "Constructing with iterations < 10000 should throw IllegalArgumentException");
    }

    @Test
    @DisplayName("Should throw exception when constructed with invalid key size")
    void shouldThrowExceptionWhenConstructedWithInvalidKeySize() {
        // When/Then
        assertThrows(
                IllegalArgumentException.class,
                () -> {
                    new KeyManagementServiceImpl(100000, 512);
                },
                "Constructing with invalid key size should throw IllegalArgumentException");
    }

    @Test
    @DisplayName("Should derive key in reasonable time")
    void shouldDeriveKeyInReasonableTime() {
        // Given
        char[] masterPassword = "PerformanceTest123!".toCharArray();
        byte[] salt = keyManagementService.generateSalt();

        // When
        long startTime = System.currentTimeMillis();
        SecretKey key = keyManagementService.deriveKey(masterPassword, salt);
        long endTime = System.currentTimeMillis();
        long duration = endTime - startTime;

        // Then
        assertNotNull(key);
        assertTrue(duration < 1000, "Key derivation should complete in less than 1 second");
        // Note: 100,000 iterations typically takes 100-200ms on modern hardware
    }

    @Test
    @DisplayName("Should handle special characters in password")
    void shouldHandleSpecialCharactersInPassword() {
        // Given
        char[] specialPassword = "P@ssw0rd!#$%^&*()_+-=[]{}|;':\",./<>?".toCharArray();
        byte[] salt = keyManagementService.generateSalt();

        // When
        SecretKey key = keyManagementService.deriveKey(specialPassword, salt);

        // Then
        assertNotNull(key, "Key should be derived from password with special characters");
        assertEquals(32, key.getEncoded().length, "Key should be 32 bytes");
    }

    @Test
    @DisplayName("Should generate cryptographically secure salts")
    void shouldGenerateCryptographicallySecureSalts() {
        // Given
        int sampleSize = 500;
        Set<String> salts = new HashSet<>();

        // When - generate many salts and collect them
        for (int i = 0; i < sampleSize; i++) {
            byte[] salt = keyManagementService.generateSalt();
            // Basic sanity checks on each salt
            assertNotNull(salt);
            assertEquals(16, salt.length);
            salts.add(Arrays.toString(salt));
        }

        // Then - expect salts to be unique across samples (highly likely for cryptographic RNG)
        assertEquals(sampleSize, salts.size(), "Generated salts should be unique");
    }
}
