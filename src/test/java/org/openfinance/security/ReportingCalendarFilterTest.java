package org.openfinance.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import jakarta.servlet.ServletException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.openfinance.entity.User;
import org.openfinance.repository.UserSettingsRepository;
import org.openfinance.service.UserReportingClock;
import org.openfinance.util.ReportingClock;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

class ReportingCalendarFilterTest {
    private final UserSettingsRepository settings = mock(UserSettingsRepository.class);
    private final ReportingCalendarFilter filter =
            new ReportingCalendarFilter(
                    new UserReportingClock(
                            settings,
                            Clock.fixed(Instant.parse("2026-11-15T23:51:00Z"), ZoneOffset.UTC)));

    @AfterEach
    void clearAuthentication() {
        SecurityContextHolder.clearContext();
    }

    private MockHttpServletRequest request(long id, String timezone) {
        SecurityContextHolder.getContext()
                .setAuthentication(
                        new UsernamePasswordAuthenticationToken(
                                User.builder().id(id).build(), null));
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(ReportingCalendarFilter.TIMEZONE_HEADER, timezone);
        return request;
    }

    @Test
    void successiveUsersOnTheSameThreadHaveIndependentCalendars() throws Exception {
        Clock original = ReportingClock.current();
        filter.doFilter(
                request(1L, "Europe/Paris"),
                new MockHttpServletResponse(),
                (req, res) -> {
                    assertThat(ReportingClock.today()).isEqualTo("2026-11-16");
                });
        assertThat(ReportingClock.current()).isSameAs(original);
        filter.doFilter(
                request(2L, "America/New_York"),
                new MockHttpServletResponse(),
                (req, res) -> {
                    assertThat(ReportingClock.today()).isEqualTo("2026-11-15");
                });
        assertThat(ReportingClock.current()).isSameAs(original);
    }

    @Test
    void accountOverrideWinsOverTheHeaderAndExceptionsStillClearTheScope() {
        UserSettingsRepository.TimezonePreferences preferences =
                mock(UserSettingsRepository.TimezonePreferences.class);
        when(preferences.getTimezone()).thenReturn("America/New_York");
        when(settings.findTimezonePreferencesByUserId(1L)).thenReturn(Optional.of(preferences));
        Clock original = ReportingClock.current();
        assertThatThrownBy(
                        () ->
                                filter.doFilter(
                                        request(1L, "Europe/Paris"),
                                        new MockHttpServletResponse(),
                                        (req, res) -> {
                                            assertThat(ReportingClock.today())
                                                    .isEqualTo("2026-11-15");
                                            throw new ServletException("failure");
                                        }))
                .isInstanceOf(ServletException.class);
        assertThat(ReportingClock.current()).isSameAs(original);
    }
}
