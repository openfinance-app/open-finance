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
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

/**
 * Unit tests for {@link RssService}.
 *
 * <p>Covers the two LOW-severity findings: (1) a single injected {@link RestTemplate} is reused
 * across calls instead of constructing a new one per invocation, and (2) the User-Agent header is a
 * named constant rather than an inline literal.
 */
class RssServiceTest {

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
