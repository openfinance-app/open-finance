package org.openfinance.service;

import java.text.Normalizer;
import java.util.Base64;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.openfinance.config.LogoFetchProperties;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

/**
 * Service that automatically fetches a brand favicon for a given name.
 *
 * <p>Uses Google's Favicon API ({@code https://www.google.com/s2/favicons}) to retrieve a 128px
 * icon. The name is converted to a best-guess domain via {@link #inferDomain(String)} (configurable
 * default TLD + per-brand overrides via {@link LogoFetchProperties}).
 *
 * <p>This service is always safe to call — all failure paths return {@link Optional#empty()} and
 * log a WARN. No exception is ever propagated to the caller.
 *
 * <p>The feature is controlled by {@link LogoFetchProperties#isEnabled()}. When disabled, returns
 * {@link Optional#empty()} immediately without making any HTTP request.
 */
@Service
@Slf4j
public class LogoFetchService {

    private static final String FAVICON_URL_TEMPLATE =
            "https://www.google.com/s2/favicons?domain=%s&sz=128";

    private final LogoFetchProperties properties;
    private final RestTemplate restTemplate;

    public LogoFetchService(
            LogoFetchProperties properties,
            @Qualifier("logoFetchRestTemplate") RestTemplate restTemplate) {
        this.properties = properties;
        this.restTemplate = restTemplate;
    }

    /**
     * Fetches a logo for the given name from Google's Favicon API.
     *
     * @param name human-readable name (e.g. "Netflix", "BNP Paribas")
     * @return base64 PNG data URI, or {@link Optional#empty()} on any failure
     */
    public Optional<String> fetchLogo(String name) {
        if (!properties.isEnabled()) {
            log.debug("Logo auto-fetch disabled, skipping for: {}", name);
            return Optional.empty();
        }
        try {
            String domain = inferDomain(name);
            String url = String.format(FAVICON_URL_TEMPLATE, domain);
            log.debug("Fetching logo for '{}' via URL: {}", name, url);

            byte[] bytes = restTemplate.getForObject(url, byte[].class);

            if (bytes == null || bytes.length < properties.getMinResponseBytes()) {
                log.warn(
                        "Logo response too small for {} ({} bytes, min {}), skipping",
                        domain,
                        bytes == null ? 0 : bytes.length,
                        properties.getMinResponseBytes());
                return Optional.empty();
            }

            String base64 = Base64.getEncoder().encodeToString(bytes);
            String dataUri = "data:image/png;base64," + base64;
            log.debug("Fetched logo for '{}' from {} ({} bytes)", name, domain, bytes.length);
            return Optional.of(dataUri);

        } catch (RestClientException e) {
            log.warn("Could not fetch logo for '{}': {}", name, e.getMessage());
            return Optional.empty();
        } catch (Exception e) {
            log.warn("Unexpected error fetching logo for '{}': {}", name, e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Converts a human name to a best-guess domain.
     *
     * <p>Pipeline:
     *
     * <ol>
     *   <li>NFD unicode normalise (decomposes accented chars, e.g. é → e + combining mark)
     *   <li>Strip non-ASCII chars (removes combining marks)
     *   <li>Lowercase
     *   <li>Remove all non-alphanumeric chars (spaces, hyphens, apostrophes, etc.)
     *   <li>Return the configured {@link LogoFetchProperties#getDomainOverrides() override} for
     *       that slug if present, else append the configured {@link
     *       LogoFetchProperties#getDefaultTld() default TLD}
     * </ol>
     *
     * <p>Examples (default config): "Société Générale" → "societegenerale.com", "McDonald's" →
     * "mcdonalds.com", "Hello bank!" → "hellobank.com". With an override {@code caissedepargne →
     * caisse-epargne.fr}, "Caisse d'Epargne" → "caisse-epargne.fr".
     *
     * <p>Package-private to allow direct testing.
     */
    String inferDomain(String name) {
        // 1. NFD: decompose accented chars (é → e + U+0301 combining acute accent)
        String normalized = Normalizer.normalize(name, Normalizer.Form.NFD);
        // 2. Strip non-ASCII (removes combining marks)
        String asciiOnly = normalized.replaceAll("[^\\x00-\\x7F]", "");
        // 3. Lowercase
        String lower = asciiOnly.toLowerCase();
        // 4. Strip everything that is not a-z or 0-9
        String slug = lower.replaceAll("[^a-z0-9]", "");
        // 5. Explicit per-brand override wins (no reliable way to infer a real TLD from a name)
        String override = properties.getDomainOverrides().get(slug);
        if (override != null && !override.isBlank()) {
            return override;
        }
        // 6. Otherwise append the configured default TLD
        return slug + "." + properties.getDefaultTld();
    }
}
