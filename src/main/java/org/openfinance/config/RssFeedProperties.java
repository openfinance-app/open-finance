package org.openfinance.config;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Externalized RSS feed URLs for {@link org.openfinance.service.RssService}, bound to {@code
 * application.rss.feeds.*} so operators can swap news providers without recompiling.
 */
@Component
@ConfigurationProperties(prefix = "application.rss")
public class RssFeedProperties {

    private Feeds feeds = new Feeds();

    public Feeds getFeeds() {
        return feeds;
    }

    public void setFeeds(Feeds feeds) {
        this.feeds = feeds;
    }

    public static class Feeds {

        private List<String> en =
                List.of(
                        "https://finance.yahoo.com/news/rssindex",
                        "https://feeds.a.dj.com/rss/RSSMarketsMain.xml");

        private List<String> fr =
                List.of(
                        "https://www.lemonde.fr/economie/rss_full.xml",
                        "https://www.lefigaro.fr/rss/figaro_economie.xml");

        public List<String> getEn() {
            return en;
        }

        public void setEn(List<String> en) {
            this.en = en;
        }

        public List<String> getFr() {
            return fr;
        }

        public void setFr(List<String> fr) {
            this.fr = fr;
        }
    }
}
