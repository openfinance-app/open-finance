package org.openfinance.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import org.openfinance.entity.User;
import org.openfinance.service.UserReportingClock;
import org.openfinance.util.ReportingClock;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/** Installed after authentication; never changes the JVM timezone shared by other users. */
@RequiredArgsConstructor
public class ReportingCalendarFilter extends OncePerRequestFilter {
    public static final String TIMEZONE_HEADER = "X-Device-Timezone";
    private final UserReportingClock reportingClock;

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        Long userId =
                authentication != null && authentication.getPrincipal() instanceof User user
                        ? user.getId()
                        : null;
        try (ReportingClock.Scope ignored =
                ReportingClock.use(
                        reportingClock.forRequest(userId, request.getHeader(TIMEZONE_HEADER)))) {
            chain.doFilter(request, response);
        }
    }
}
