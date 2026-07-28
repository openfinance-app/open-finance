package org.openfinance.config;

import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Configuration properties for automatic logo fetching.
 *
 * <p>Bound to {@code application.logo-fetch.*} in {@code application.yml}. Follows the same pattern
 * as {@link SchedulerProperties}.
 */
@Component
@ConfigurationProperties(prefix = "application.logo-fetch")
public class LogoFetchProperties {

    /** Master on/off switch. Defaults to {@code false}. */
    private boolean enabled = false;

    /** HTTP connect + read timeout in seconds. Defaults to {@code 5}. */
    private int timeoutSeconds = 5;

    /**
     * Minimum acceptable response body size in bytes. Responses smaller than this are rejected as
     * generic/empty icons. Defaults to {@code 100}.
     */
    private int minResponseBytes = 100;

    /**
     * TLD appended to a name-derived slug when no explicit {@link #domainOverrides override}
     * exists. There is no reliable way to infer a brand's real TLD from its name, so {@code com}
     * (the most common) is the default; operators can change it or use {@link #domainOverrides} for
     * specific brands (e.g. {@code .fr}, {@code .co.uk}). Stored without a leading dot.
     */
    private String defaultTld = "com";

    /**
     * Explicit slug → full-domain overrides for brands whose domain the {@link #defaultTld}
     * heuristic gets wrong. The key is the normalized slug produced by {@code
     * LogoFetchService.inferDomain} (lowercase, alphanumeric only, e.g. {@code caissedepargne});
     * the value is the full domain (e.g. {@code caisse-epargne.fr}). Empty by default.
     */
    private Map<String, String> domainOverrides = new LinkedHashMap<>();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public int getTimeoutSeconds() {
        return timeoutSeconds;
    }

    public void setTimeoutSeconds(int timeoutSeconds) {
        this.timeoutSeconds = timeoutSeconds;
    }

    public int getMinResponseBytes() {
        return minResponseBytes;
    }

    public void setMinResponseBytes(int minResponseBytes) {
        this.minResponseBytes = minResponseBytes;
    }

    public String getDefaultTld() {
        return defaultTld;
    }

    public void setDefaultTld(String defaultTld) {
        this.defaultTld = defaultTld;
    }

    public Map<String, String> getDomainOverrides() {
        return domainOverrides;
    }

    public void setDomainOverrides(Map<String, String> domainOverrides) {
        this.domainOverrides = domainOverrides;
    }
}
