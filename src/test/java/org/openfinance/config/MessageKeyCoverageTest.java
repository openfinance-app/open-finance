package org.openfinance.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashSet;
import java.util.Properties;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

/**
 * Test that verifies all i18n message keys in English files have corresponding translations in
 * French. This ensures translation completeness and prevents missing translations at runtime.
 */
class MessageKeyCoverageTest {

    @Test
    void shouldHaveAllMessagesKeysInFrench() throws IOException {
        // Load English messages
        Properties englishMessages = loadProperties("i18n/messages.properties");

        // Load French messages
        Properties frenchMessages = loadProperties("i18n/messages_fr.properties");

        // Get all keys from both
        Set<String> englishKeys = englishMessages.stringPropertyNames();
        Set<String> frenchKeys = frenchMessages.stringPropertyNames();

        // Find missing keys in French
        Set<String> missingInFrench = new HashSet<>(englishKeys);
        missingInFrench.removeAll(frenchKeys);

        // Assert no missing keys
        assertThat(missingInFrench)
                .withFailMessage(
                        "The following %d message keys exist in messages.properties but are missing in messages_fr.properties:%n%s",
                        missingInFrench.size(), formatKeyList(missingInFrench))
                .isEmpty();
    }

    @Test
    void shouldHaveAllValidationKeysInFrench() throws IOException {
        // Load English validation messages
        Properties englishValidation = loadProperties("ValidationMessages.properties");

        // Load French validation messages
        Properties frenchValidation = loadProperties("ValidationMessages_fr.properties");

        // Get all keys from both
        Set<String> englishKeys = englishValidation.stringPropertyNames();
        Set<String> frenchKeys = frenchValidation.stringPropertyNames();

        // Find missing keys in French
        Set<String> missingInFrench = new HashSet<>(englishKeys);
        missingInFrench.removeAll(frenchKeys);

        // Assert no missing keys
        assertThat(missingInFrench)
                .withFailMessage(
                        "The following %d validation keys exist in ValidationMessages.properties but are missing in ValidationMessages_fr.properties:%n%s",
                        missingInFrench.size(), formatKeyList(missingInFrench))
                .isEmpty();
    }

    @Test
    void shouldHaveNoExtraKeysInFrench() throws IOException {
        // Load English messages
        Properties englishMessages = loadProperties("i18n/messages.properties");

        // Load French messages
        Properties frenchMessages = loadProperties("i18n/messages_fr.properties");

        // Get all keys from both
        Set<String> englishKeys = englishMessages.stringPropertyNames();
        Set<String> frenchKeys = frenchMessages.stringPropertyNames();

        // Find extra keys in French (keys that don't exist in English)
        Set<String> extraInFrench = new HashSet<>(frenchKeys);
        extraInFrench.removeAll(englishKeys);

        // Assert no extra keys
        assertThat(extraInFrench)
                .withFailMessage(
                        "The following %d message keys exist in messages_fr.properties but are missing in messages.properties:%n%s",
                        extraInFrench.size(), formatKeyList(extraInFrench))
                .isEmpty();
    }

    @Test
    void shouldHaveNoExtraValidationKeysInFrench() throws IOException {
        // Load English validation messages
        Properties englishValidation = loadProperties("ValidationMessages.properties");

        // Load French validation messages
        Properties frenchValidation = loadProperties("ValidationMessages_fr.properties");

        // Get all keys from both
        Set<String> englishKeys = englishValidation.stringPropertyNames();
        Set<String> frenchKeys = frenchValidation.stringPropertyNames();

        // Find extra keys in French (keys that don't exist in English)
        Set<String> extraInFrench = new HashSet<>(frenchKeys);
        extraInFrench.removeAll(englishKeys);

        // Assert no extra keys
        assertThat(extraInFrench)
                .withFailMessage(
                        "The following %d validation keys exist in ValidationMessages_fr.properties but are missing in ValidationMessages.properties:%n%s",
                        extraInFrench.size(), formatKeyList(extraInFrench))
                .isEmpty();
    }

    @Test
    void shouldNotHaveEmptyTranslations() throws IOException {
        // Load French messages
        Properties frenchMessages = loadProperties("i18n/messages_fr.properties");

        // Find empty values
        Set<String> emptyKeys =
                frenchMessages.stringPropertyNames().stream()
                        .filter(
                                key -> {
                                    String value = frenchMessages.getProperty(key);
                                    return value == null || value.trim().isEmpty();
                                })
                        .collect(Collectors.toSet());

        // Assert no empty translations
        assertThat(emptyKeys)
                .withFailMessage(
                        "The following %d message keys have empty translations in messages_fr.properties:%n%s",
                        emptyKeys.size(), formatKeyList(emptyKeys))
                .isEmpty();
    }

    @Test
    void shouldNotHaveEmptyValidationTranslations() throws IOException {
        // Load French validation messages
        Properties frenchValidation = loadProperties("ValidationMessages_fr.properties");

        // Find empty values
        Set<String> emptyKeys =
                frenchValidation.stringPropertyNames().stream()
                        .filter(
                                key -> {
                                    String value = frenchValidation.getProperty(key);
                                    return value == null || value.trim().isEmpty();
                                })
                        .collect(Collectors.toSet());

        // Assert no empty translations
        assertThat(emptyKeys)
                .withFailMessage(
                        "The following %d validation keys have empty translations in ValidationMessages_fr.properties:%n%s",
                        emptyKeys.size(), formatKeyList(emptyKeys))
                .isEmpty();
    }

    /** Helper method to load properties from classpath */
    private Properties loadProperties(String resourcePath) throws IOException {
        Properties properties = new Properties();
        ClassPathResource resource = new ClassPathResource(resourcePath);

        try (InputStream inputStream = resource.getInputStream()) {
            properties.load(inputStream);
        }

        return properties;
    }

    /** Helper method to format a set of keys as a readable list */
    private String formatKeyList(Set<String> keys) {
        if (keys.isEmpty()) {
            return "(none)";
        }

        return keys.stream().sorted().map(key -> "  - " + key).collect(Collectors.joining("\n"));
    }
}
