package org.openfinance.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Servlet filter that adds security-hardening HTTP response headers to every response.
 *
 * <p>The following headers are set on every response:
 *
 * <ul>
 *   <li><strong>Content-Security-Policy</strong> — restricts resource loading to same origin and
 *       trusted CDNs, mitigating XSS and data-injection attacks.
 *   <li><strong>X-Frame-Options</strong> — prevents the application from being embedded in an
 *       iframe, mitigating clickjacking.
 *   <li><strong>X-Content-Type-Options</strong> — instructs browsers not to MIME-sniff responses,
 *       preventing content-type confusion attacks.
 *   <li><strong>Strict-Transport-Security (HSTS)</strong> — enforces HTTPS connections and
 *       pre-loads the domain into browser HSTS lists (only sent over HTTPS).
 *   <li><strong>Referrer-Policy</strong> — limits the information included in the {@code Referer}
 *       header for privacy and security.
 *   <li><strong>Permissions-Policy</strong> — disables browser features that are not needed by the
 *       application (geolocation, microphone, camera, etc.).
 *   <li><strong>X-XSS-Protection</strong> — enables legacy XSS filter in older browsers.
 *   <li><strong>Cache-Control</strong> — prevents caching of API responses which may contain
 *       sensitive financial data.
 * </ul>
 *
 * <p>Requirement REQ-3.2: Security headers for OWASP compliance (TASK-15.1.2)
 *
 * @author Open-Finance Development Team
 * @version 1.0
 * @since 2026-03-20
 */
@Component
public class SecurityHeadersFilter extends OncePerRequestFilter {

    /** HSTS max-age in seconds (1 year = 31 536 000 s). */
    private static final long HSTS_MAX_AGE_SECONDS = 31_536_000L;

    /**
     * Whether to include {@code includeSubDomains} in the HSTS directive. Defaults to {@code true}.
     */
    @Value("${application.security.hsts.include-sub-domains:true}")
    private boolean hstsIncludeSubDomains;

    /**
     * Whether to include the {@code preload} directive in the HSTS header. Defaults to {@code
     * false} since preloading requires manual submission to browser lists.
     */
    @Value("${application.security.hsts.preload:false}")
    private boolean hstsPreload;

    /**
     * Adds all security-related HTTP response headers before the filter chain continues.
     *
     * @param request the current HTTP request
     * @param response the current HTTP response
     * @param filterChain the remaining filter chain
     * @throws ServletException if a servlet exception occurs
     * @throws IOException if an I/O error occurs
     */
    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        // Requirement TASK-15.1.2: Content-Security-Policy
        // Restricts resource loading to same origin; allows inline styles (needed for Vite/React
        // dev).  The 'unsafe-inline' for style-src is a pragmatic trade-off; a nonce-based
        // approach can be adopted when the frontend moves to a CDN.
        response.setHeader(
                "Content-Security-Policy",
                "default-src 'self'; "
                        + "script-src 'self'; "
                        + "style-src 'self' 'unsafe-inline'; "
                        + "img-src 'self' data: blob:; "
                        + "font-src 'self'; "
                        + "connect-src 'self'; "
                        + "frame-ancestors 'none'; "
                        + "form-action 'self'; "
                        + "base-uri 'self'; "
                        + "object-src 'none'");

        // Requirement TASK-15.1.2: X-Frame-Options – clickjacking prevention
        response.setHeader("X-Frame-Options", "DENY");

        // Requirement TASK-15.1.2: X-Content-Type-Options – MIME sniffing prevention
        response.setHeader("X-Content-Type-Options", "nosniff");

        // Requirement TASK-15.1.2: Strict-Transport-Security (HSTS) – enforce HTTPS
        // Only send over HTTPS to avoid HSTS over HTTP (which has no effect and wastes bandwidth).
        if (request.isSecure()) {
            StringBuilder hsts = new StringBuilder("max-age=").append(HSTS_MAX_AGE_SECONDS);
            if (hstsIncludeSubDomains) {
                hsts.append("; includeSubDomains");
            }
            if (hstsPreload) {
                hsts.append("; preload");
            }
            response.setHeader("Strict-Transport-Security", hsts.toString());
        }

        // Referrer-Policy – restrict referrer information for privacy
        response.setHeader("Referrer-Policy", "strict-origin-when-cross-origin");

        // Permissions-Policy – disable unneeded browser features
        response.setHeader(
                "Permissions-Policy",
                "geolocation=(), microphone=(), camera=(), payment=(), usb=()");

        // Legacy XSS filter for older browsers
        response.setHeader("X-XSS-Protection", "1; mode=block");

        // Cache-Control – prevent caching of API responses (sensitive financial data)
        String requestUri = request.getRequestURI();
        if (requestUri.startsWith("/api/")) {
            response.setHeader("Cache-Control", "no-store, no-cache, must-revalidate, private");
            response.setHeader("Pragma", "no-cache");
            response.setHeader("Expires", "0");
        }

        filterChain.doFilter(request, response);
    }
}
