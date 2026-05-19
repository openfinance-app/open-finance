package org.openfinance.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.openfinance.entity.User;
import org.openfinance.repository.UserRepository;
import org.openfinance.service.JwtService;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * JWT authentication filter that intercepts HTTP requests and validates JWT tokens.
 *
 * <p>This filter extracts JWT tokens from the Authorization header, validates them, and sets the
 * Spring Security authentication context for authenticated users.
 *
 * <p>Filter behavior:
 *
 * <ul>
 *   <li>Extracts token from "Authorization: Bearer {token}" header
 *   <li>Validates token signature and expiration
 *   <li>Loads user from database and sets authentication context
 *   <li>Continues filter chain regardless of authentication success (allows public endpoints)
 * </ul>
 *
 * <p>Requirement REQ-2.1.3: JWT-based authentication filter for stateless session management
 *
 * @author Open-Finance Development Team
 * @version 1.0
 * @since 2026-01-30
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtService jwtService;
    private final UserRepository userRepository;

    /**
     * Filters incoming HTTP requests to extract and validate JWT tokens.
     *
     * <p>Process:
     *
     * <ol>
     *   <li>Extract JWT token from Authorization header
     *   <li>Validate token signature and expiration
     *   <li>Extract username from token
     *   <li>Load user from database
     *   <li>Set authentication in SecurityContext
     *   <li>Continue filter chain
     * </ol>
     *
     * <p>If token is missing, invalid, or user not found, the filter continues without setting
     * authentication. SecurityConfig will handle authorization for protected endpoints.
     *
     * @param request HTTP request
     * @param response HTTP response
     * @param filterChain filter chain to continue processing
     * @throws ServletException if servlet error occurs
     * @throws IOException if I/O error occurs
     */
    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain)
            throws ServletException, IOException {

        try {
            // Extract JWT token from Authorization header
            String token = extractTokenFromRequest(request);

            // If token exists and no authentication is already set
            if (token != null && SecurityContextHolder.getContext().getAuthentication() == null) {
                authenticateUser(token, request);
            }
        } catch (RuntimeException e) {
            // Log at debug to avoid leaking sensitive information and to reduce noise for
            // expected
            // token validation failures. Do not block the request - let SecurityConfig
            // handle
            // authorization for protected endpoints.
            log.debug("Error during JWT authentication", e);
        }

        // Continue filter chain regardless of authentication result
        filterChain.doFilter(request, response);
    }

    /**
     * Extracts JWT token from the Authorization header.
     *
     * <p>Expected header format: "Authorization: Bearer {token}"
     *
     * @param request HTTP request
     * @return JWT token string, or null if header is missing or invalid format
     */
    private String extractTokenFromRequest(HttpServletRequest request) {
        String authHeader = request.getHeader(AUTHORIZATION_HEADER);

        if (authHeader == null || !authHeader.startsWith(BEARER_PREFIX)) {
            log.debug("No valid Authorization header found in request");
            return null;
        }

        String token = authHeader.substring(BEARER_PREFIX.length()).trim();
        log.debug("Extracted JWT token from Authorization header");
        return token;
    }

    /**
     * Authenticates user by validating JWT token and loading user details.
     *
     * <p>Steps:
     *
     * <ol>
     *   <li>Validate token signature and expiration
     *   <li>Extract username from token
     *   <li>Load user from database
     *   <li>Create authentication token
     *   <li>Set authentication in SecurityContext
     * </ol>
     *
     * @param token JWT token string
     * @param request HTTP request for setting authentication details
     */
    private void authenticateUser(String token, HttpServletRequest request) {
        // Validate token signature and expiration
        if (!jwtService.validateToken(token)) {
            log.debug("JWT token validation failed");
            return;
        }

        // Extract username from token
        String username;
        try {
            username = jwtService.extractUsername(token);
        } catch (Exception e) {
            // Username extraction failures are expected for malformed/invalid tokens; log
            // at debug
            // to avoid alarming log noise and to prevent leaking token internals.
            log.debug("Failed to extract username from token", e);
            return;
        }

        // Load user from database
        User user =
                userRepository
                        .findByUsername(username)
                        .orElseThrow(
                                () -> {
                                    // Missing user for a valid username claim might indicate stale
                                    // token;
                                    // log at debug to avoid exposing user enumeration attempts in
                                    // logs.
                                    log.debug("User not found for username: {}", username);
                                    return new UsernameNotFoundException(
                                            "User not found: " + username);
                                });

        // Create authentication token with user details
        UsernamePasswordAuthenticationToken authToken =
                new UsernamePasswordAuthenticationToken(
                        user, // principal (User entity)
                        null, // credentials (not needed after authentication)
                        user.getAuthorities() // user roles/authorities
                        );

        // Set additional details from request (IP address, session ID, etc.)
        authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

        // Set authentication in SecurityContext
        SecurityContextHolder.getContext().setAuthentication(authToken);

        // Set userId as request attribute for @RequestAttribute annotations in
        // controllers
        request.setAttribute("userId", user.getId());

        // Log at debug to avoid writing usernames at info level in production logs.
        log.debug("Successfully authenticated user: {}", username);
    }
}
