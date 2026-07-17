package org.openfinance.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Optional;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.openfinance.config.EncryptionProperties;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

@ExtendWith(MockitoExtension.class)
@DisplayName("EncryptionKeyFilter Unit Tests")
class EncryptionKeyFilterTest {

    private static final String SESSION_HEADER = "X-Encryption-Session";

    @Mock private EncryptionKeyCache encryptionKeyCache;

    @Mock private FilterChain filterChain;

    @AfterEach
    void tearDown() {
        EncryptionContext.clear();
    }

    @Test
    @DisplayName("Should skip encryption session lookup when encryption is disabled")
    void shouldSkipSessionLookupAndClearStaleContextWhenEncryptionDisabled() throws Exception {
        EncryptionProperties encryptionProperties = new EncryptionProperties();
        encryptionProperties.setEnabled(false);
        EncryptionKeyFilter filter =
                new EncryptionKeyFilter(encryptionKeyCache, encryptionProperties);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(SESSION_HEADER, "stale-session");
        MockHttpServletResponse response = new MockHttpServletResponse();
        EncryptionContext.setKey(new SecretKeySpec(new byte[32], "AES"));
        doAnswer(
                        invocation -> {
                            assertThat(EncryptionContext.getKey()).isNull();
                            return null;
                        })
                .when(filterChain)
                .doFilter(request, response);

        filter.doFilter(request, response, filterChain);

        assertThat(EncryptionContext.getKey()).isNull();
        verify(encryptionKeyCache, never()).getKeyBySessionToken(anyString());
        verifyNoInteractions(encryptionKeyCache);
        verify(filterChain).doFilter(request, response);
    }

    @Test
    @DisplayName("Should ignore encryption header when no authenticated user is present")
    void shouldIgnoreEncryptionHeaderWhenNoAuthenticatedUserIsPresent() throws Exception {
        EncryptionProperties encryptionProperties = new EncryptionProperties();
        encryptionProperties.setEnabled(true);
        EncryptionKeyFilter filter =
                new EncryptionKeyFilter(encryptionKeyCache, encryptionProperties);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(SESSION_HEADER, "invalid-session");
        MockHttpServletResponse response = new MockHttpServletResponse();
        EncryptionContext.setKey(new SecretKeySpec(new byte[32], "AES"));
        doAnswer(
                        invocation -> {
                            assertThat(EncryptionContext.getKey()).isNull();
                            return null;
                        })
                .when(filterChain)
                .doFilter(request, response);

        filter.doFilter(request, response, filterChain);

        assertThat(EncryptionContext.getKey()).isNull();
        verifyNoInteractions(encryptionKeyCache);
        verify(filterChain).doFilter(request, response);
    }

    @Test
    @DisplayName("Should skip public endpoint even when authenticated user is present")
    void shouldSkipPublicEndpointEvenWhenAuthenticatedUserIsPresent() throws Exception {
        EncryptionProperties encryptionProperties = new EncryptionProperties();
        encryptionProperties.setEnabled(true);
        EncryptionKeyFilter filter =
                new EncryptionKeyFilter(encryptionKeyCache, encryptionProperties);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/api/v1/auth/login");
        request.setAttribute("userId", 1L);
        MockHttpServletResponse response = new MockHttpServletResponse();
        EncryptionContext.setKey(new SecretKeySpec(new byte[32], "AES"));
        doAnswer(
                        invocation -> {
                            assertThat(EncryptionContext.getKey()).isNull();
                            return null;
                        })
                .when(filterChain)
                .doFilter(request, response);

        filter.doFilter(request, response, filterChain);

        assertThat(EncryptionContext.getKey()).isNull();
        verifyNoInteractions(encryptionKeyCache);
        verify(filterChain).doFilter(request, response);
    }

    @Test
    @DisplayName("Should reject authenticated request when encryption session is missing")
    void shouldRejectAuthenticatedRequestWhenEncryptionSessionMissing() throws Exception {
        EncryptionProperties encryptionProperties = new EncryptionProperties();
        encryptionProperties.setEnabled(true);
        EncryptionKeyFilter filter =
                new EncryptionKeyFilter(encryptionKeyCache, encryptionProperties);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute("userId", 1L);
        MockHttpServletResponse response = new MockHttpServletResponse();
        EncryptionContext.setKey(new SecretKeySpec(new byte[32], "AES"));

        filter.doFilter(request, response, filterChain);

        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_UNAUTHORIZED);
        assertThat(EncryptionContext.getKey()).isNull();
        verifyNoInteractions(encryptionKeyCache);
        verify(filterChain, never()).doFilter(request, response);
    }

    @Test
    @DisplayName("Should reject authenticated request when encryption session is invalid")
    void shouldRejectAuthenticatedRequestWhenEncryptionSessionInvalid() throws Exception {
        EncryptionProperties encryptionProperties = new EncryptionProperties();
        encryptionProperties.setEnabled(true);
        EncryptionKeyFilter filter =
                new EncryptionKeyFilter(encryptionKeyCache, encryptionProperties);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute("userId", 1L);
        request.addHeader(SESSION_HEADER, "invalid-session");
        MockHttpServletResponse response = new MockHttpServletResponse();
        when(encryptionKeyCache.getKeyBySessionToken("invalid-session"))
                .thenReturn(Optional.empty());

        filter.doFilter(request, response, filterChain);

        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_UNAUTHORIZED);
        assertThat(EncryptionContext.getKey()).isNull();
        verify(encryptionKeyCache).getKeyBySessionToken("invalid-session");
        verify(filterChain, never()).doFilter(request, response);
    }

    @Test
    @DisplayName("Should reject raw encryption key header and clear stale context")
    void shouldRejectRawEncryptionKeyHeaderAndClearStaleContext() throws Exception {
        EncryptionProperties encryptionProperties = new EncryptionProperties();
        encryptionProperties.setEnabled(true);
        EncryptionKeyFilter filter =
                new EncryptionKeyFilter(encryptionKeyCache, encryptionProperties);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute("userId", 1L);
        request.addHeader(SESSION_HEADER, "+Ifn53/WIpprMdk+ToP0VZ4b9PiMeT24/r/U5VLreYM=");
        MockHttpServletResponse response = new MockHttpServletResponse();
        EncryptionContext.setKey(new SecretKeySpec(new byte[32], "AES"));
        when(encryptionKeyCache.getKeyBySessionToken(
                        "+Ifn53/WIpprMdk+ToP0VZ4b9PiMeT24/r/U5VLreYM="))
                .thenReturn(Optional.empty());

        assertThatCode(() -> filter.doFilter(request, response, filterChain))
                .doesNotThrowAnyException();

        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_UNAUTHORIZED);
        assertThat(EncryptionContext.getKey()).isNull();
        verify(encryptionKeyCache)
                .getKeyBySessionToken("+Ifn53/WIpprMdk+ToP0VZ4b9PiMeT24/r/U5VLreYM=");
        verify(filterChain, never()).doFilter(request, response);
    }

    @Test
    @DisplayName(
            "Should set and clear encryption context for valid session when encryption is enabled")
    void shouldSetAndClearContextForValidSessionWhenEncryptionEnabled() throws Exception {
        EncryptionProperties encryptionProperties = new EncryptionProperties();
        encryptionProperties.setEnabled(true);
        EncryptionKeyFilter filter =
                new EncryptionKeyFilter(encryptionKeyCache, encryptionProperties);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute("userId", 1L);
        request.addHeader(SESSION_HEADER, "valid-session");
        MockHttpServletResponse response = new MockHttpServletResponse();
        SecretKey key = new SecretKeySpec(new byte[32], "AES");
        when(encryptionKeyCache.getKeyBySessionToken("valid-session")).thenReturn(Optional.of(key));
        when(encryptionKeyCache.getUserIdBySessionToken("valid-session"))
                .thenReturn(Optional.of(1L));
        doAnswer(
                        invocation -> {
                            assertThat(EncryptionContext.getKey()).isSameAs(key);
                            return null;
                        })
                .when(filterChain)
                .doFilter(request, response);

        filter.doFilter(request, response, filterChain);

        assertThat(EncryptionContext.getKey()).isNull();
        verify(encryptionKeyCache).getKeyBySessionToken("valid-session");
        verify(encryptionKeyCache).getUserIdBySessionToken("valid-session");
        verify(filterChain).doFilter(request, response);
    }

    @Test
    @DisplayName("Should reject encryption session owned by another user")
    void shouldRejectEncryptionSessionOwnedByAnotherUser() throws Exception {
        EncryptionProperties encryptionProperties = new EncryptionProperties();
        encryptionProperties.setEnabled(true);
        EncryptionKeyFilter filter =
                new EncryptionKeyFilter(encryptionKeyCache, encryptionProperties);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute("userId", 1L);
        request.addHeader(SESSION_HEADER, "other-user-session");
        MockHttpServletResponse response = new MockHttpServletResponse();
        SecretKey key = new SecretKeySpec(new byte[32], "AES");
        when(encryptionKeyCache.getKeyBySessionToken("other-user-session"))
                .thenReturn(Optional.of(key));
        when(encryptionKeyCache.getUserIdBySessionToken("other-user-session"))
                .thenReturn(Optional.of(2L));

        filter.doFilter(request, response, filterChain);

        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_UNAUTHORIZED);
        assertThat(EncryptionContext.getKey()).isNull();
        verify(filterChain, never()).doFilter(request, response);
    }
}
