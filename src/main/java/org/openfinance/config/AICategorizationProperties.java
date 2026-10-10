package org.openfinance.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** Configuration for optional AI suggestions during import review. */
@Component
@ConfigurationProperties(prefix = "application.ai.categorization")
@Getter
@Setter
public class AICategorizationProperties {
    private boolean enabled = true;
}
