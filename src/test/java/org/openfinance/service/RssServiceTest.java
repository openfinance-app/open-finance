package org.openfinance.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.openfinance.dto.RssFeedItem;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

/**
 * Unit tests for {@link RssService}.
 *
 * <p>Covers the two LOW-severity findings: (1) a single injected {@link RestTemplate} is reused
 * across calls instead of constructing a new one per invocation, and (2) the User-Agent header is a
 * named constant rather than an inline literal.
 */
class RssServiceTest {

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(ints = {403, 404, 429, 0})
    void unavailableFeedDoesNotDiscardHealthyProviderItems(int status) {
        RestTemplate client = mock(RestTemplate.class);
        RuntimeException failure =
                status == 0
                        ? new ResourceAccessException("Timed out")
                        : HttpClientErrorException.create(
                                HttpStatus.valueOf(status),
                                "Unavailable",
                                HttpHeaders.EMPTY,
                                new byte[0],
                                StandardCharsets.UTF_8);
        when(client.exchange(
                        anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(byte[].class)))
                .thenThrow(failure)
                .thenReturn(ResponseEntity.ok(MINIMAL_RSS_XML.getBytes(StandardCharsets.UTF_8)));
        assertThat(new RssService(client).getFinanceFeeds(Locale.ENGLISH))
                .extracting(RssFeedItem::getTitle)
                .containsExactly("Headline");
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"null", "malformed"})
    void emptyOrMalformedProvidersDoNotHideHealthyHeadlines(String kind) {
        RestTemplate client = mock(RestTemplate.class);
        when(client.exchange(
                        anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(byte[].class)))
                .thenReturn(
                        kind.equals("null")
                                ? ResponseEntity.ok().build()
                                : ResponseEntity.ok("invalid XML".getBytes(StandardCharsets.UTF_8)))
                .thenReturn(ResponseEntity.ok(MINIMAL_RSS_XML.getBytes(StandardCharsets.UTF_8)));
        assertThat(new RssService(client).getFinanceFeeds(Locale.ENGLISH))
                .extracting(RssFeedItem::getTitle)
                .containsExactly("Headline");
    }

    @Test
    void frenchReadersUseConfiguredFrenchSourcesAndMissingMetadataStaysUsable() {
        RestTemplate client = mock(RestTemplate.class);
        org.openfinance.config.RssFeedProperties properties =
                new org.openfinance.config.RssFeedProperties();
        properties.getFeeds().setFr(List.of("https://example.invalid/fr"));
        String xml =
                "<rss version=\"2.0\"><channel><item><title>French headline</title></item></channel></rss>";
        when(client.exchange(
                        eq("https://example.invalid/fr"),
                        eq(HttpMethod.GET),
                        any(HttpEntity.class),
                        eq(byte[].class)))
                .thenReturn(ResponseEntity.ok(xml.getBytes(StandardCharsets.UTF_8)));
        List<RssFeedItem> items =
                new RssService(client, properties).getFinanceFeeds(Locale.CANADA_FRENCH);
        assertThat(items).hasSize(1);
        assertThat(items.getFirst().getSource()).isEqualTo("Unknown Source");
        assertThat(items.getFirst().getDescription()).isEmpty();
        assertThat(items.getFirst().getPubDate()).isNull();
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(ints = {99, 100, 101})
    void limitsTheMergedFeedAfterSortingNewestFirst(int count) {
        RestTemplate client = mock(RestTemplate.class);
        org.openfinance.config.RssFeedProperties properties =
                new org.openfinance.config.RssFeedProperties();
        properties.getFeeds().setEn(List.of("https://example.invalid/en"));
        StringBuilder xml =
                new StringBuilder(
                        "<rss version=\"2.0\"><channel><title>News</title><item><title>Undated</title></item>");
        for (int index = 0; index < count; index++) {
            String date =
                    java.time.format.DateTimeFormatter.RFC_1123_DATE_TIME.format(
                            java.time.ZonedDateTime.of(
                                            2024, 1, 1, 0, 0, 0, 0, java.time.ZoneOffset.UTC)
                                    .plusDays(index));
            xml.append("<item><title>Headline ")
                    .append(index)
                    .append("</title><pubDate>")
                    .append(date)
                    .append("</pubDate></item>");
        }
        xml.append("</channel></rss>");
        when(client.exchange(
                        anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(byte[].class)))
                .thenReturn(ResponseEntity.ok(xml.toString().getBytes(StandardCharsets.UTF_8)));
        List<RssFeedItem> items =
                new RssService(client, properties).getFinanceFeeds(Locale.ENGLISH);
        assertThat(items).hasSize(100);
        assertThat(items.getFirst().getTitle()).isEqualTo("Headline " + (count - 1));
        assertThat(items.getLast().getTitle())
                .isEqualTo(count == 99 ? "Undated" : "Headline " + (count - 100));
    }

    private static final String MINIMAL_RSS_XML =
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                    + "<rss version=\"2.0\"><channel>"
                    + "<title>Test Feed</title>"
                    + "<item><title>Headline</title><link>https://example.com/1</link>"
                    + "<description>Body</description></item>"
                    + "</channel></rss>";

    @Test
    @DisplayName("Uses the injected RestTemplate instance (never constructs its own)")
    void reusesInjectedRestTemplate() {
        RestTemplate restTemplate = mock(RestTemplate.class);
        when(restTemplate.exchange(
                        anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(byte[].class)))
                .thenReturn(ResponseEntity.ok(MINIMAL_RSS_XML.getBytes(StandardCharsets.UTF_8)));

        RssService service = new RssService(restTemplate);
        List<RssFeedItem> firstCallItems = service.getFinanceFeeds(Locale.ENGLISH);

        // A second call must reuse the SAME injected mock (constructor-injected field), not a new
        // RestTemplate() created internally — Mockito would not see calls on an internally
        // constructed instance at all, so any successful stubbed response here proves reuse.
        List<RssFeedItem> secondCallItems = service.getFinanceFeeds(Locale.ENGLISH);

        assertThat(firstCallItems).isNotEmpty();
        assertThat(secondCallItems).isNotEmpty();
        // 2 English URLs per call x 2 calls = 4 total invocations on the ONE injected mock
        verify(restTemplate, times(4))
                .exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(byte[].class));
    }

    @Test
    @DisplayName("Sends the shared User-Agent constant on every request")
    void sendsUserAgentConstant() {
        RestTemplate restTemplate = mock(RestTemplate.class);
        when(restTemplate.exchange(
                        anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(byte[].class)))
                .thenReturn(ResponseEntity.ok(MINIMAL_RSS_XML.getBytes(StandardCharsets.UTF_8)));

        RssService service = new RssService(restTemplate);
        service.getFinanceFeeds(Locale.ENGLISH);

        ArgumentCaptor<HttpEntity> entityCaptor = ArgumentCaptor.forClass(HttpEntity.class);
        verify(restTemplate, times(2))
                .exchange(
                        anyString(), eq(HttpMethod.GET), entityCaptor.capture(), eq(byte[].class));

        for (HttpEntity<?> entity : entityCaptor.getAllValues()) {
            assertThat(entity.getHeaders().getFirst("User-Agent")).isEqualTo(RssService.USER_AGENT);
        }
    }
}
