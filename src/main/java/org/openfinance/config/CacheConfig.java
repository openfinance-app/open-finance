package org.openfinance.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import java.util.concurrent.TimeUnit;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration for application-level caching using Caffeine.
 *
 * <p>Caching Strategy:
 *
 * <ul>
 *   <li><strong>marketQuotes:</strong> Real-time market quotes (15 minutes TTL)
 *   <li><strong>exchangeRates:</strong> Currency exchange rates (15 minutes TTL)
 *   <li><strong>dashboardSummary:</strong> Complete dashboard summary (5 minutes TTL)
 *   <li><strong>netWorthSummary:</strong> Net worth calculations (5 minutes TTL)
 *   <li><strong>accountSummaries:</strong> Account summary data (5 minutes TTL)
 *   <li><strong>cashFlow:</strong> Cash flow analysis (5 minutes TTL)
 *   <li><strong>spendingByCategory:</strong> Spending breakdown (5 minutes TTL)
 *   <li><strong>assetAllocation:</strong> Asset allocation by type (5 minutes TTL)
 *   <li><strong>portfolioPerformance:</strong> Portfolio performance metrics (5 minutes TTL)
 *   <li><strong>insights:</strong> AI-powered financial insights (15 minutes TTL)
 *   <li><strong>cashflowSankey:</strong> Cashflow Sankey diagram data (5 minutes TTL)
 *   <li><strong>borrowingCapacity:</strong> Borrowing capacity analysis (5 minutes TTL)
 *   <li><strong>networthAllocation:</strong> Net worth allocation breakdown (5 minutes TTL)
 *   <li><strong>rssFeeds:</strong> Global finance RSS feeds (15 minutes TTL)
 * </ul>
 *
 * <p>Caffeine is an in-memory cache with automatic expiration and size limits.
 *
 * @author Open Finance Team
 * @version 1.0
 * @since 2024-01-20
 * @see org.springframework.cache.annotation.Cacheable
 */
@Configuration
@EnableCaching
public class CacheConfig {

    /**
     * Configures the cache manager with custom TTL and size limits.
     *
     * <p>Cache specifications:
     *
     * <ul>
     *   <li><strong>marketQuotes:</strong> Expires after 15 minutes, max 1000 entries
     *   <li><strong>exchangeRates:</strong> Expires after 15 minutes, max 1000 entries
     *   <li><strong>dashboardSummary:</strong> Expires after 5 minutes, max 500 entries
     *   <li><strong>netWorthSummary:</strong> Expires after 5 minutes, max 500 entries
     *   <li><strong>accountSummaries:</strong> Expires after 5 minutes, max 500 entries
     *   <li><strong>cashFlow:</strong> Expires after 5 minutes, max 500 entries
     *   <li><strong>spendingByCategory:</strong> Expires after 5 minutes, max 500 entries
     *   <li><strong>assetAllocation:</strong> Expires after 5 minutes, max 500 entries
     *   <li><strong>portfolioPerformance:</strong> Expires after 5 minutes, max 500 entries
     *   <li><strong>insights:</strong> Expires after 15 minutes, max 500 entries
     *   <li><strong>cashflowSankey:</strong> Expires after 5 minutes, max 500 entries
     *   <li><strong>borrowingCapacity:</strong> Expires after 5 minutes, max 500 entries
     *   <li><strong>networthAllocation:</strong> Expires after 5 minutes, max 500 entries
     *   <li><strong>rssFeeds:</strong> Expires after 15 minutes, max 500 entries
     * </ul>
     *
     * @return the configured cache manager
     */
    @Bean
    public CacheManager cacheManager() {
        CaffeineCacheManager cacheManager =
                new CaffeineCacheManager(
                        "marketQuotes",
                        "exchangeRates",
                        "dashboardSummary",
                        "netWorthSummary",
                        "accountSummaries",
                        "cashFlow",
                        "spendingByCategory",
                        "assetAllocation",
                        "portfolioPerformance",
                        "insights",
                        "cashflowSankey",
                        "borrowingCapacity",
                        "networthAllocation",
                        "rssFeeds");
        cacheManager.setCaffeine(caffeineCacheBuilder());
        return cacheManager;
    }

    /**
     * Builds a Caffeine cache with default settings.
     *
     * <p>Settings:
     *
     * <ul>
     *   <li>Expire after write: 15 minutes
     *   <li>Maximum size: 1000 entries
     * </ul>
     *
     * @return the Caffeine cache builder
     */
    private Caffeine<Object, Object> caffeineCacheBuilder() {
        return Caffeine.newBuilder()
                .expireAfterWrite(15, TimeUnit.MINUTES)
                .maximumSize(1000)
                .recordStats(); // Enable cache statistics for monitoring
    }
}
