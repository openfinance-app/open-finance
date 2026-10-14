package org.openfinance.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

/**
 * Spring configuration for RSS-feed-fetching infrastructure.
 *
 * <p>Registers a dedicated {@link RestTemplate} bean, reused across all calls to {@link
 * org.openfinance.service.RssService#getFinanceFeeds}. Isolated from other {@code RestTemplate}
 * beans (e.g. {@link LogoFetchConfig}) to avoid cross-contamination of settings, following the same
 * pattern as {@link LogoFetchConfig}.
 */
@Configuration
public class RssConfig {

    /**
     * RestTemplate used exclusively by {@link org.openfinance.service.RssService}.
     *
     * @return a RestTemplate with bounded timeouts so an unavailable feed cannot hang a request
     */
    @Bean
    public RestTemplate rssRestTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5000);
        factory.setReadTimeout(10000);
        return new RestTemplate(factory);
    }
}
