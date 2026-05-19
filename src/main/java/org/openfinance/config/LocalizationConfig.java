package org.openfinance.config;

import java.util.List;
import java.util.Locale;
import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.support.ReloadableResourceBundleMessageSource;
import org.springframework.web.servlet.LocaleResolver;
import org.springframework.web.servlet.i18n.AcceptHeaderLocaleResolver;

/**
 * Configuration for Spring internationalization (i18n) support.
 *
 * <p>Registers a {@link MessageSource} backed by two property-file bundles:
 *
 * <ul>
 *   <li>{@code classpath:i18n/messages} — application-level messages (errors, notifications,
 *       insights)
 *   <li>{@code classpath:ValidationMessages} — Jakarta Validation constraint messages
 * </ul>
 *
 * <p>The {@link LocaleResolver} is an {@link AcceptHeaderLocaleResolver} that reads the {@code
 * Accept-Language} HTTP header sent by the frontend and restricts resolution to the supported
 * locales {@code en} and {@code fr}. Unsupported or missing headers fall back to {@link
 * Locale#ENGLISH}.
 *
 * <p>Requirements: REQ-3.6.1, REQ-3.6.2
 *
 * @author Open-Finance Development Team
 * @since 2026-03-09
 */
@Configuration
public class LocalizationConfig {

    /**
     * Primary message source for the application.
     *
     * <p>Loads messages from {@code i18n/messages*.properties} and {@code
     * ValidationMessages*.properties} on the classpath. UTF-8 encoding is used for all files. Falls
     * back to English when a key is missing in the requested locale.
     *
     * @return configured {@link MessageSource} bean
     */
    @Bean
    public MessageSource messageSource() {
        ReloadableResourceBundleMessageSource messageSource =
                new ReloadableResourceBundleMessageSource();
        messageSource.setBasenames("classpath:i18n/messages", "classpath:ValidationMessages");
        messageSource.setDefaultEncoding("UTF-8");
        messageSource.setFallbackToSystemLocale(false);
        messageSource.setDefaultLocale(Locale.ENGLISH);
        messageSource.setCacheSeconds(60);
        return messageSource;
    }

    /**
     * Locale resolver that honours the {@code Accept-Language} request header.
     *
     * <p>Only {@code en} and {@code fr} are supported; any other (or absent) value resolves to
     * {@link Locale#ENGLISH}.
     *
     * @return configured {@link LocaleResolver} bean
     */
    @Bean
    public LocaleResolver localeResolver() {
        AcceptHeaderLocaleResolver resolver = new AcceptHeaderLocaleResolver();
        resolver.setSupportedLocales(List.of(Locale.ENGLISH, Locale.FRENCH));
        resolver.setDefaultLocale(Locale.ENGLISH);
        return resolver;
    }
}
