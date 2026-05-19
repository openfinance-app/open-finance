package org.openfinance.service;

import com.rometools.rome.feed.synd.SyndEntry;
import com.rometools.rome.feed.synd.SyndFeed;
import com.rometools.rome.io.SyndFeedInput;
import com.rometools.rome.io.XmlReader;
import java.io.ByteArrayInputStream;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.openfinance.dto.RssFeedItem;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

@Service
@RequiredArgsConstructor
@Slf4j
public class RssService {

    private static final List<String> EN_RSS_URLS =
            List.of(
                    "https://finance.yahoo.com/news/rssindex",
                    "https://feeds.a.dj.com/rss/RSSMarketsMain.xml");

    private static final List<String> FR_RSS_URLS =
            List.of(
                    "https://www.lemonde.fr/economie/rss_full.xml",
                    "https://www.lefigaro.fr/rss/figaro_economie.xml");

    @Cacheable(value = "rssFeeds", key = "#locale.language", sync = true)
    public List<RssFeedItem> getFinanceFeeds(Locale locale) {
        List<RssFeedItem> allItems = new ArrayList<>();
        RestTemplate restTemplate = new RestTemplate();
        SyndFeedInput input = new SyndFeedInput();

        List<String> targetUrls =
                "fr".equalsIgnoreCase(locale.getLanguage()) ? FR_RSS_URLS : EN_RSS_URLS;

        for (String url : targetUrls) {
            try {
                HttpHeaders headers = new HttpHeaders();
                headers.set(
                        "User-Agent",
                        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36");
                HttpEntity<String> entity = new HttpEntity<>(headers);

                ResponseEntity<byte[]> response =
                        restTemplate.exchange(url, HttpMethod.GET, entity, byte[].class);
                byte[] xmlData = response.getBody();

                if (xmlData != null) {
                    SyndFeed feed = input.build(new XmlReader(new ByteArrayInputStream(xmlData)));
                    String sourceName =
                            feed.getTitle() != null ? feed.getTitle() : "Unknown Source";

                    for (SyndEntry entry : feed.getEntries()) {
                        LocalDateTime pubDate = null;
                        if (entry.getPublishedDate() != null) {
                            pubDate =
                                    entry.getPublishedDate()
                                            .toInstant()
                                            .atZone(ZoneId.systemDefault())
                                            .toLocalDateTime();
                        }

                        String description = "";
                        if (entry.getDescription() != null) {
                            description = entry.getDescription().getValue();
                        }

                        allItems.add(
                                RssFeedItem.builder()
                                        .title(entry.getTitle())
                                        .link(entry.getLink())
                                        .description(description)
                                        .pubDate(pubDate)
                                        .source(sourceName)
                                        .build());
                    }
                }
            } catch (Exception e) {
                log.error("Failed to fetch or parse RSS feed from URL: {}", url, e);
            }
        }

        // Sort by pubDate descending (handle null pubDates safely)
        allItems.sort(
                Comparator.comparing(
                                (RssFeedItem item) ->
                                        item.getPubDate() == null
                                                ? LocalDateTime.MIN
                                                : item.getPubDate())
                        .reversed());

        // Return top 100 items
        return allItems.size() > 100 ? allItems.subList(0, 100) : allItems;
    }
}
