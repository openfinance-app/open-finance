package org.openfinance.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.openfinance.entity.SecurityAuditLog;
import org.openfinance.repository.SecurityAuditLogRepository;
import org.springframework.mock.web.MockHttpServletRequest;

@ExtendWith(MockitoExtension.class)
@DisplayName("Security audit records tolerate missing and malformed request metadata")
class SecurityAuditBoundaryTest {
    @Mock private SecurityAuditLogRepository logs;
    @InjectMocks private SecurityAuditService service;

    private SecurityAuditLog saved() {
        ArgumentCaptor<SecurityAuditLog> entry = ArgumentCaptor.forClass(SecurityAuditLog.class);
        verify(logs).save(entry.capture());
        return entry.getValue();
    }

    @ParameterizedTest
    @CsvSource(
            value = {
                "NULL|NULL|192.0.2.5",
                " | |192.0.2.5",
                "NULL| 2001:db8::1 |2001:db8::1",
                "203.0.113.1, 198.51.100.2|198.51.100.3|203.0.113.1",
                ",|198.51.100.3|198.51.100.3",
                ",,|NULL|192.0.2.5",
                " , 198.51.100.2|NULL|192.0.2.5"
            },
            delimiter = '|',
            nullValues = "NULL",
            ignoreLeadingAndTrailingWhitespace = false)
    void recordsTheFirstForwardedClientOrFallsBackWhenItIsMissing(
            String forwarded, String real, String expected) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("192.0.2.5");
        if (forwarded != null) request.addHeader("X-Forwarded-For", forwarded);
        if (real != null) request.addHeader("X-Real-IP", real);
        service.logEvent(
                7L,
                "alice",
                SecurityAuditLog.EventType.LOGIN_FAILURE,
                request,
                "Incorrect password");
        SecurityAuditLog entry = saved();
        assertThat(entry.getIpAddress()).isEqualTo(expected);
        assertThat(entry.getUserId()).isEqualTo(7L);
        assertThat(entry.getDetails()).isEqualTo("Incorrect password");
        assertThat(entry.getEventType()).isEqualTo(SecurityAuditLog.EventType.LOGIN_FAILURE);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 511, 512, 513, 1000})
    void boundsUserAgentStorageWithoutTruncatingOtherAuditFields(int length) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("User-Agent", "x".repeat(length));
        service.logEvent(7L, "alice", SecurityAuditLog.EventType.LOGIN_SUCCESS, request, null);
        assertThat(saved().getUserAgent()).isEqualTo("x".repeat(Math.min(length, 512)));
    }

    @Test
    void scheduledAndPreauthenticationEventsKeepExplicitUnknownIdentity() {
        service.logEvent(
                null, null, SecurityAuditLog.EventType.UNAUTHORIZED_ACCESS, "Background request");
        SecurityAuditLog entry = saved();
        assertThat(entry.getUserId()).isNull();
        assertThat(entry.getUsername()).isEqualTo("unknown");
        assertThat(entry.getIpAddress()).isEqualTo("unknown");
        assertThat(entry.getUserAgent()).isNull();
        assertThat(entry.getDetails()).isEqualTo("Background request");
    }
}
