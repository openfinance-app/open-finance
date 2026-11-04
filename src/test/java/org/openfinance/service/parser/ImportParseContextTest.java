package org.openfinance.service.parser;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Locale;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.openfinance.entity.UserSettings;
import org.springframework.context.i18n.LocaleContextHolder;

@DisplayName("Import date conventions are captured independently of the worker thread locale")
class ImportParseContextTest {
    @AfterEach
    void clearLocale() {
        LocaleContextHolder.resetLocaleContext();
    }

    @ParameterizedTest
    @CsvSource({
        "en-US, dd/mm/yyyy, true",
        "fr-FR, MM/DD/YYYY, false",
        "en-US, YYYY-MM-DD, true",
        "en-US, , false",
        "en-GB, , true",
        "fr-FR, , true"
    })
    void explicitDateOrderOverridesTheUserLocale(
            String language, String dateFormat, boolean dayFirst) {
        UserSettings settings = new UserSettings();
        settings.setLanguage(" " + language + " ");
        settings.setDateFormat(dateFormat);
        LocaleContextHolder.setLocale(Locale.JAPAN);
        ImportParseContext context = ImportParseContext.from(settings);
        assertThat(context.locale()).isEqualTo(Locale.forLanguageTag(language));
        assertThat(context.dayFirst()).isEqualTo(dayFirst);
    }

    @Test
    void missingSettingsUseRequestLocaleWhileMissingLanguageUsesEnglish() {
        LocaleContextHolder.setLocale(Locale.US);
        assertThat(ImportParseContext.from(null))
                .isEqualTo(new ImportParseContext(Locale.US, false));
        UserSettings settings = new UserSettings();
        settings.setLanguage(" ");
        settings.setDateFormat(" ");
        assertThat(ImportParseContext.from(settings))
                .isEqualTo(new ImportParseContext(Locale.ENGLISH, true));
    }
}
