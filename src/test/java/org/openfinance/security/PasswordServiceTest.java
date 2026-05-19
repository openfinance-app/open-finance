// filepath: src/test/java/org/openfinance/security/PasswordServiceTest.java

package org.openfinance.security;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Unit tests for PasswordService.
 *
 * <p>Tests password hashing, validation, and rehashing functionality using BCrypt.
 *
 * @author Open-Finance Development Team
 * @version 1.0
 * @since 2026-01-30
 */
@DisplayName("PasswordService Tests")
class PasswordServiceTest {

    private PasswordService passwordService;
    private PasswordEncoder passwordEncoder;

    @BeforeEach
    void setUp() {
        passwordEncoder = new BCryptPasswordEncoder();
        passwordService = new PasswordService(passwordEncoder);
    }

    @Test
    @DisplayName("Should hash password successfully")
    void shouldHashPassword() {
        // Given
        String plainPassword = "SecurePassword123!";

        // When
        String hashedPassword = passwordService.hashPassword(plainPassword);

        // Then
        assertNotNull(hashedPassword, "Hashed password should not be null");
        assertNotEquals(
                plainPassword, hashedPassword, "Hashed password should differ from plain password");
        assertTrue(hashedPassword.startsWith("$2a$"), "BCrypt hash should start with $2a$");
        assertEquals(60, hashedPassword.length(), "BCrypt hash should be 60 characters");
    }

    @Test
    @DisplayName("Should produce different hashes for same password due to unique salts")
    void shouldProduceDifferentHashesForSamePassword() {
        // Given
        String plainPassword = "SamePassword123!";

        // When
        String hash1 = passwordService.hashPassword(plainPassword);
        String hash2 = passwordService.hashPassword(plainPassword);

        // Then
        assertNotEquals(
                hash1, hash2, "Two hashes of same password should be different (unique salts)");
    }

    @Test
    @DisplayName("Should validate correct password")
    void shouldValidateCorrectPassword() {
        // Given
        String plainPassword = "CorrectPassword456!";
        String hashedPassword = passwordService.hashPassword(plainPassword);

        // When
        boolean isValid = passwordService.validatePassword(plainPassword, hashedPassword);

        // Then
        assertTrue(isValid, "Correct password should validate successfully");
    }

    @Test
    @DisplayName("Should reject incorrect password")
    void shouldRejectIncorrectPassword() {
        // Given
        String correctPassword = "CorrectPassword789!";
        String incorrectPassword = "WrongPassword000!";
        String hashedPassword = passwordService.hashPassword(correctPassword);

        // When
        boolean isValid = passwordService.validatePassword(incorrectPassword, hashedPassword);

        // Then
        assertFalse(isValid, "Incorrect password should not validate");
    }

    @Test
    @DisplayName("Should throw exception when hashing null password")
    void shouldThrowExceptionWhenHashingNullPassword() {
        // When/Then
        assertThrows(
                IllegalArgumentException.class,
                () -> {
                    passwordService.hashPassword(null);
                },
                "Hashing null password should throw IllegalArgumentException");
    }

    @Test
    @DisplayName("Should throw exception when hashing empty password")
    void shouldThrowExceptionWhenHashingEmptyPassword() {
        // When/Then
        assertThrows(
                IllegalArgumentException.class,
                () -> {
                    passwordService.hashPassword("");
                },
                "Hashing empty password should throw IllegalArgumentException");
    }

    @Test
    @DisplayName("Should throw exception when hashing whitespace-only password")
    void shouldThrowExceptionWhenHashingWhitespacePassword() {
        // When/Then
        assertThrows(
                IllegalArgumentException.class,
                () -> {
                    passwordService.hashPassword("   ");
                },
                "Hashing whitespace-only password should throw IllegalArgumentException");
    }

    @Test
    @DisplayName("Should throw exception when validating with null plain password")
    void shouldThrowExceptionWhenValidatingWithNullPlainPassword() {
        // Given
        String hashedPassword = passwordService.hashPassword("SomePassword");

        // When/Then
        assertThrows(
                IllegalArgumentException.class,
                () -> {
                    passwordService.validatePassword(null, hashedPassword);
                },
                "Validating with null plain password should throw IllegalArgumentException");
    }

    @Test
    @DisplayName("Should throw exception when validating with null hashed password")
    void shouldThrowExceptionWhenValidatingWithNullHashedPassword() {
        // When/Then
        assertThrows(
                IllegalArgumentException.class,
                () -> {
                    passwordService.validatePassword("SomePassword", null);
                },
                "Validating with null hashed password should throw IllegalArgumentException");
    }

    @Test
    @DisplayName("Should handle password with special characters")
    void shouldHandlePasswordWithSpecialCharacters() {
        // Given
        String complexPassword = "P@ssw0rd!#$%^&*()_+-=[]{}|;':\",./<>?";

        // When
        String hashedPassword = passwordService.hashPassword(complexPassword);
        boolean isValid = passwordService.validatePassword(complexPassword, hashedPassword);

        // Then
        assertNotNull(hashedPassword);
        assertTrue(isValid, "Password with special characters should hash and validate correctly");
    }

    @Test
    @DisplayName("Should handle long password (256 characters)")
    void shouldHandleLongPassword() {
        // Given
        String longPassword = "A".repeat(256);

        // When
        String hashedPassword = passwordService.hashPassword(longPassword);
        boolean isValid = passwordService.validatePassword(longPassword, hashedPassword);

        // Then
        assertNotNull(hashedPassword);
        assertTrue(isValid, "Long password should hash and validate correctly");
    }

    @Test
    @DisplayName("Should handle Unicode characters in password")
    void shouldHandleUnicodePassword() {
        // Given
        String unicodePassword = "Пароль123!日本語パスワード";

        // When
        String hashedPassword = passwordService.hashPassword(unicodePassword);
        boolean isValid = passwordService.validatePassword(unicodePassword, hashedPassword);

        // Then
        assertNotNull(hashedPassword);
        assertTrue(isValid, "Unicode password should hash and validate correctly");
    }

    @Test
    @DisplayName("Should reject password with slight modification")
    void shouldRejectSlightlyModifiedPassword() {
        // Given
        String originalPassword = "OriginalPassword123!";
        String modifiedPassword = "OriginalPassword124!"; // Changed last digit
        String hashedPassword = passwordService.hashPassword(originalPassword);

        // When
        boolean isValid = passwordService.validatePassword(modifiedPassword, hashedPassword);

        // Then
        assertFalse(isValid, "Slightly modified password should not validate");
    }

    @Test
    @DisplayName("Should not need rehash for newly created hash")
    void shouldNotNeedRehashForNewHash() {
        // Given
        String password = "NewPassword123!";
        String hashedPassword = passwordService.hashPassword(password);

        // When
        boolean needsRehash = passwordService.needsRehash(hashedPassword);

        // Then
        assertFalse(needsRehash, "Newly created hash should not need rehashing");
    }

    @Test
    @DisplayName("Should indicate rehash needed for null hash")
    void shouldIndicateRehashNeededForNullHash() {
        // When
        boolean needsRehash = passwordService.needsRehash(null);

        // Then
        assertTrue(needsRehash, "Null hash should indicate rehashing needed");
    }

    @Test
    @DisplayName("Should indicate rehash needed for empty hash")
    void shouldIndicateRehashNeededForEmptyHash() {
        // When
        boolean needsRehash = passwordService.needsRehash("");

        // Then
        assertTrue(needsRehash, "Empty hash should indicate rehashing needed");
    }

    @Test
    @DisplayName("Should throw exception when constructed with null encoder")
    void shouldThrowExceptionWhenConstructedWithNullEncoder() {
        // When/Then
        assertThrows(
                IllegalArgumentException.class,
                () -> {
                    new PasswordService(null);
                },
                "Constructing with null encoder should throw IllegalArgumentException");
    }

    @Test
    @DisplayName("Should be timing-attack resistant")
    void shouldBeTimingAttackResistant() {
        // Given
        String correctPassword = "TimingTestPassword123!";
        String hashedPassword = passwordService.hashPassword(correctPassword);
        String wrongPassword1 = "A";
        String wrongPassword2 = "WrongPasswordThatIsVeryLongAndComplex123!";

        // When - measure time for short wrong password
        long start1 = System.nanoTime();
        passwordService.validatePassword(wrongPassword1, hashedPassword);
        long time1 = System.nanoTime() - start1;

        // When - measure time for long wrong password
        long start2 = System.nanoTime();
        passwordService.validatePassword(wrongPassword2, hashedPassword);
        long time2 = System.nanoTime() - start2;

        // Then - times should be similar (within 50% variance)
        double ratio = (double) Math.max(time1, time2) / Math.min(time1, time2);
        assertTrue(
                ratio < 1.5,
                "Validation time should not vary significantly based on password content");
    }
}
