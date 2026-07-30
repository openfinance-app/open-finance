package org.openfinance.exception;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link DuplicatePayeeException}.
 *
 * <p>Pins that the exception is localizable so the pre-existing {@code error.payee.duplicate} i18n
 * key (EN/FR) is actually reachable, carrying the payee name as a message argument.
 */
@DisplayName("DuplicatePayeeException Tests")
class DuplicatePayeeExceptionTest {

    @Test
    @DisplayName("carries the error.payee.duplicate key and the payee name as an argument")
    void isLocalizableWithKeyAndName() {
        DuplicatePayeeException ex = new DuplicatePayeeException("Netflix");

        assertThat(ex).isInstanceOf(LocalizableException.class);
        LocalizableException localizable = ex;
        assertThat(localizable.getMessageKey()).isEqualTo("error.payee.duplicate");
        assertThat(localizable.getMessageArgs()).containsExactly("Netflix");
        // Raw fallback message still includes the name for non-localized contexts / logs.
        assertThat(ex.getMessage()).contains("Netflix");
    }

    @Test
    @DisplayName("resolves to localized EN/FR messages with the payee name substituted")
    void resolvesLocalizedMessagesWithName() {
        org.springframework.context.support.ResourceBundleMessageSource messageSource =
                new org.springframework.context.support.ResourceBundleMessageSource();
        messageSource.setBasename("i18n/messages");
        messageSource.setDefaultEncoding("UTF-8");
        messageSource.setFallbackToSystemLocale(false);

        DuplicatePayeeException ex = new DuplicatePayeeException("Netflix");

        String en =
                messageSource.getMessage(
                        ex.getMessageKey(), ex.getMessageArgs(), java.util.Locale.ENGLISH);
        String fr =
                messageSource.getMessage(
                        ex.getMessageKey(), ex.getMessageArgs(), java.util.Locale.FRENCH);

        assertThat(en).isEqualTo("A payee with this name already exists: Netflix");
        assertThat(fr)
                .isEqualTo("Un b\u00e9n\u00e9ficiaire avec ce nom existe d\u00e9j\u00e0 : Netflix");
    }
}
