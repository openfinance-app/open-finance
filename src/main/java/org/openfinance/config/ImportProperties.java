package org.openfinance.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Configuration properties for the transaction import feature. */
@ConfigurationProperties(prefix = "application.import")
public class ImportProperties {

    /** Master switch for the Skrooge JSON import format. Defaults enabled. */
    private boolean skroogeJsonEnabled = true;

    public boolean isSkroogeJsonEnabled() {
        return skroogeJsonEnabled;
    }

    public void setSkroogeJsonEnabled(boolean skroogeJsonEnabled) {
        this.skroogeJsonEnabled = skroogeJsonEnabled;
    }
}
