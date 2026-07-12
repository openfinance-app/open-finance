package org.openfinance.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Configuration properties for application-layer field encryption. */
@ConfigurationProperties(prefix = "application.encryption")
public class EncryptionProperties {

    /** Master switch for master-password-derived field encryption. Defaults secure. */
    private boolean enabled = true;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
}
