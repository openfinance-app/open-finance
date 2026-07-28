package org.openfinance.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.openfinance.config.LogoFetchProperties;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

/**
 * Unit tests for LogoFetchService.
 *
 * <p>Tests domain inference, HTTP fetch, base64 encoding, size validation, and all failure paths
 * (network errors, too-small responses, exceptions).
 */
@ExtendWith(MockitoExtension.class)
class LogoFetchServiceTest {

    @Mock private LogoFetchProperties properties;

    @Mock private RestTemplate restTemplate;

    @InjectMocks private LogoFetchService logoFetchService;

    @BeforeEach
    void setUpDomainDefaults() {
        // Default heuristic: no overrides, ".com" TLD. Lenient because the "disabled" test never
        // reaches inferDomain.
        lenient().when(properties.getDefaultTld()).thenReturn("com");
        lenient().when(properties.getDomainOverrides()).thenReturn(Map.of());
    }

    // -------------------------------------------------------------------------
    // Feature disabled
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("fetchLogo returns empty when feature is disabled")
    void shouldReturnEmptyWhenDisabled() {
        when(properties.isEnabled()).thenReturn(false);

        Optional<String> result = logoFetchService.fetchLogo("Netflix");

        assertThat(result).isEmpty();
        verify(restTemplate, never()).getForObject(anyString(), eq(byte[].class));
    }

    // -------------------------------------------------------------------------
    // Domain inference
    // -------------------------------------------------------------------------

    @ParameterizedTest(name = "''{0}'' -> {1}")
    @DisplayName("inferDomain converts name to lowercase alphanumeric .com domain")
    @CsvSource({
        "Netflix,                    netflix.com",
        "BNP Paribas,                bnpparibas.com",
        "Soci\u00e9t\u00e9 G\u00e9n\u00e9rale, societegenerale.com",
        "McDonald's,                  mcdonalds.com",
        "Caisse d\u2019Epargne,       caissedepargne.com",
        "Hello bank!,                 hellobank.com",
        "SNCF,                        sncf.com"
    })
    void shouldInferCorrectDomain(String name, String expectedDomain) {
        // Call package-private inferDomain directly
        String domain = logoFetchService.inferDomain(name.trim());
        assertThat(domain).isEqualTo(expectedDomain.trim());
    }

    @Test
    @DisplayName("inferDomain appends the configured default TLD instead of a hardcoded .com")
    void shouldUseConfiguredDefaultTld() {
        when(properties.getDefaultTld()).thenReturn("fr");

        assertThat(logoFetchService.inferDomain("Boursorama")).isEqualTo("boursorama.fr");
    }

    @Test
    @DisplayName("inferDomain uses an explicit domain override when one matches the slug")
    void shouldUseDomainOverride() {
        when(properties.getDomainOverrides())
                .thenReturn(Map.of("caissedepargne", "caisse-epargne.fr"));

        // Override wins over the default-TLD heuristic (which would give caissedepargne.com).
        assertThat(logoFetchService.inferDomain("Caisse d\u2019Epargne"))
                .isEqualTo("caisse-epargne.fr");
    }

    // -------------------------------------------------------------------------
    // Successful fetch
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("fetchLogo returns data URI on successful response above threshold")
    void shouldReturnBase64DataUriOnSuccess() {
        when(properties.isEnabled()).thenReturn(true);
        when(properties.getMinResponseBytes()).thenReturn(100);

        byte[] fakeIcon = new byte[200];
        Arrays.fill(fakeIcon, (byte) 42);
        when(restTemplate.getForObject(anyString(), eq(byte[].class))).thenReturn(fakeIcon);

        Optional<String> result = logoFetchService.fetchLogo("Netflix");

        assertThat(result).isPresent();
        assertThat(result.get()).startsWith("data:image/png;base64,");
    }

    // -------------------------------------------------------------------------
    // Response too small
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("fetchLogo returns empty when response is smaller than minResponseBytes")
    void shouldReturnEmptyWhenResponseTooSmall() {
        when(properties.isEnabled()).thenReturn(true);
        when(properties.getMinResponseBytes()).thenReturn(100);

        when(restTemplate.getForObject(anyString(), eq(byte[].class))).thenReturn(new byte[50]);

        assertThat(logoFetchService.fetchLogo("Netflix")).isEmpty();
    }

    @Test
    @DisplayName("fetchLogo returns empty when response is null")
    void shouldReturnEmptyWhenResponseIsNull() {
        when(properties.isEnabled()).thenReturn(true);
        when(properties.getMinResponseBytes()).thenReturn(100);

        when(restTemplate.getForObject(anyString(), eq(byte[].class))).thenReturn(null);

        assertThat(logoFetchService.fetchLogo("Netflix")).isEmpty();
    }

    // -------------------------------------------------------------------------
    // Failure paths — always non-fatal
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("fetchLogo returns empty (never throws) on RestClientException")
    void shouldReturnEmptyOnRestClientException() {
        when(properties.isEnabled()).thenReturn(true);

        when(restTemplate.getForObject(anyString(), eq(byte[].class)))
                .thenThrow(new RestClientException("connection timeout"));

        assertThat(logoFetchService.fetchLogo("Netflix")).isEmpty();
    }

    @Test
    @DisplayName("fetchLogo returns empty (never throws) on unexpected RuntimeException")
    void shouldReturnEmptyOnUnexpectedException() {
        when(properties.isEnabled()).thenReturn(true);

        when(restTemplate.getForObject(anyString(), eq(byte[].class)))
                .thenThrow(new RuntimeException("unexpected"));

        assertThat(logoFetchService.fetchLogo("Netflix")).isEmpty();
    }
}
