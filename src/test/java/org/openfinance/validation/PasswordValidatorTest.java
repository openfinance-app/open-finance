package org.openfinance.validation;

import static org.junit.jupiter.api.Assertions.*;

import jakarta.validation.ConstraintValidatorContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

class PasswordValidatorTest {

    private PasswordValidator validator;

    @Mock private ConstraintValidatorContext context;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        validator = new PasswordValidator();
    }

    @Test
    void shouldReturnTrueWhenPasswordIsNull() {
        // Given: null input (delegated to @NotNull)
        // When/Then
        assertTrue(validator.isValid(null, context));
    }

    @Test
    void shouldReturnFalseWhenPasswordIsEmpty() {
        // Given: empty string
        // When/Then
        assertFalse(validator.isValid("", context));
    }

    @Test
    void shouldReturnFalseWhenPasswordIsTooShort() {
        // Given: 7 chars, meets all other criteria
        // When/Then
        assertFalse(validator.isValid("Ab1!def", context));
    }

    @Test
    void shouldReturnFalseWhenMissingUppercase() {
        // Given: 8+ chars, missing uppercase
        // When/Then
        assertFalse(validator.isValid("test@1234", context));
    }

    @Test
    void shouldReturnFalseWhenMissingLowercase() {
        // Given: 8+ chars, missing lowercase
        // When/Then
        assertFalse(validator.isValid("TEST@1234", context));
    }

    @Test
    void shouldReturnFalseWhenMissingDigit() {
        // Given: 8+ chars, missing digit
        // When/Then
        assertFalse(validator.isValid("Test@abcd", context));
    }

    @Test
    void shouldReturnFalseWhenMissingSpecialChar() {
        // Given: 8+ chars, missing special char
        // When/Then
        assertFalse(validator.isValid("Test12345", context));
    }

    @Test
    void shouldReturnTrueWhenPasswordIsValid() {
        // Given: valid password like "Test@1234"
        // When/Then
        assertTrue(validator.isValid("Test@1234", context));
    }

    @Test
    void shouldReturnFalseWhenPasswordIsOnlyLowercase() {
        // Given: "password" (only lowercase, no complexity)
        // When/Then
        assertFalse(validator.isValid("password", context));
    }

    @ParameterizedTest
    @ValueSource(strings = {"LongPasswordWith1!", "P@ssw0rd2026", "OpenFinance#1"})
    void shouldReturnTrueForVariousValidPasswords(String password) {
        assertTrue(validator.isValid(password, context));
    }
}
