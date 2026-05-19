package org.openfinance.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.openfinance.entity.User;
import org.openfinance.repository.UserRepository;
import org.openfinance.service.JwtService;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Unit tests for JwtAuthenticationFilter.
 *
 * <p>Tests JWT token extraction, validation, user authentication, and error handling in the
 * authentication filter.
 *
 * @author Open-Finance Development Team
 * @version 1.0
 * @since 2026-01-30
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("JwtAuthenticationFilter Tests")
class JwtAuthenticationFilterTest {

    @Mock private JwtService jwtService;

    @Mock private UserRepository userRepository;

    @Mock private HttpServletRequest request;

    @Mock private HttpServletResponse response;

    @Mock private FilterChain filterChain;

    @InjectMocks private JwtAuthenticationFilter jwtAuthenticationFilter;

    private User testUser;
    private static final String VALID_TOKEN = "valid.jwt.token";
    private static final String INVALID_TOKEN = "invalid.jwt.token";
    private static final String TEST_USERNAME = "john_doe";

    @BeforeEach
    void setUp() {
        // Clear security context before each test
        SecurityContextHolder.clearContext();

        // Create test user
        testUser =
                User.builder()
                        .id(1L)
                        .username(TEST_USERNAME)
                        .email("john@example.com")
                        .passwordHash("$2a$10$hashedPassword")
                        .masterPasswordSalt("saltBase64")
                        .build();
    }

    @Test
    @DisplayName("Should authenticate user with valid JWT token")
    void shouldAuthenticateUserWithValidToken() throws ServletException, IOException {
        // Given: Valid Authorization header with Bearer token
        when(request.getHeader("Authorization")).thenReturn("Bearer " + VALID_TOKEN);
        when(jwtService.validateToken(VALID_TOKEN)).thenReturn(true);
        when(jwtService.extractUsername(VALID_TOKEN)).thenReturn(TEST_USERNAME);
        when(userRepository.findByUsername(TEST_USERNAME)).thenReturn(Optional.of(testUser));

        // When: Filter processes the request
        jwtAuthenticationFilter.doFilterInternal(request, response, filterChain);

        // Then: Authentication is set in SecurityContext
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        assertThat(authentication).isNotNull();
        assertThat(authentication.getPrincipal()).isEqualTo(testUser);
        assertThat(authentication.isAuthenticated()).isTrue();

        // Filter chain continues
        verify(filterChain).doFilter(request, response);
    }

    @Test
    @DisplayName("Should continue filter chain when Authorization header is missing")
    void shouldContinueWhenAuthorizationHeaderMissing() throws ServletException, IOException {
        // Given: No Authorization header
        when(request.getHeader("Authorization")).thenReturn(null);

        // When: Filter processes the request
        jwtAuthenticationFilter.doFilterInternal(request, response, filterChain);

        // Then: No authentication is set
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        assertThat(authentication).isNull();

        // JWT service is never called
        verify(jwtService, never()).validateToken(anyString());
        verify(jwtService, never()).extractUsername(anyString());
        verify(userRepository, never()).findByUsername(anyString());

        // Filter chain continues
        verify(filterChain).doFilter(request, response);
    }

    @Test
    @DisplayName("Should continue filter chain when Authorization header doesn't start with Bearer")
    void shouldContinueWhenAuthorizationHeaderInvalid() throws ServletException, IOException {
        // Given: Authorization header without Bearer prefix
        when(request.getHeader("Authorization")).thenReturn("Basic dXNlcjpwYXNz");

        // When: Filter processes the request
        jwtAuthenticationFilter.doFilterInternal(request, response, filterChain);

        // Then: No authentication is set
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        assertThat(authentication).isNull();

        // JWT service is never called
        verify(jwtService, never()).validateToken(anyString());

        // Filter chain continues
        verify(filterChain).doFilter(request, response);
    }

    @Test
    @DisplayName("Should not authenticate when JWT token validation fails")
    void shouldNotAuthenticateWhenTokenInvalid() throws ServletException, IOException {
        // Given: Invalid JWT token
        when(request.getHeader("Authorization")).thenReturn("Bearer " + INVALID_TOKEN);
        when(jwtService.validateToken(INVALID_TOKEN)).thenReturn(false);

        // When: Filter processes the request
        jwtAuthenticationFilter.doFilterInternal(request, response, filterChain);

        // Then: No authentication is set
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        assertThat(authentication).isNull();

        // Username extraction is never called
        verify(jwtService, never()).extractUsername(anyString());
        verify(userRepository, never()).findByUsername(anyString());

        // Filter chain continues
        verify(filterChain).doFilter(request, response);
    }

    @Test
    @DisplayName("Should not authenticate when username extraction fails")
    void shouldNotAuthenticateWhenUsernameExtractionFails() throws ServletException, IOException {
        // Given: Token validation succeeds but username extraction fails
        when(request.getHeader("Authorization")).thenReturn("Bearer " + VALID_TOKEN);
        when(jwtService.validateToken(VALID_TOKEN)).thenReturn(true);
        when(jwtService.extractUsername(VALID_TOKEN))
                .thenThrow(new IllegalArgumentException("Invalid token"));

        // When: Filter processes the request
        jwtAuthenticationFilter.doFilterInternal(request, response, filterChain);

        // Then: No authentication is set
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        assertThat(authentication).isNull();

        // User repository is never called
        verify(userRepository, never()).findByUsername(anyString());

        // Filter chain continues (error is logged but doesn't block request)
        verify(filterChain).doFilter(request, response);
    }

    @Test
    @DisplayName("Should not authenticate when user not found in database")
    void shouldNotAuthenticateWhenUserNotFound() throws ServletException, IOException {
        // Given: Valid token but user doesn't exist in database
        when(request.getHeader("Authorization")).thenReturn("Bearer " + VALID_TOKEN);
        when(jwtService.validateToken(VALID_TOKEN)).thenReturn(true);
        when(jwtService.extractUsername(VALID_TOKEN)).thenReturn(TEST_USERNAME);
        when(userRepository.findByUsername(TEST_USERNAME)).thenReturn(Optional.empty());

        // When: Filter processes the request
        jwtAuthenticationFilter.doFilterInternal(request, response, filterChain);

        // Then: No authentication is set (exception is caught)
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        assertThat(authentication).isNull();

        // Filter chain continues (error is logged but doesn't block request)
        verify(filterChain).doFilter(request, response);
    }

    @Test
    @DisplayName("Should not set authentication when authentication already exists")
    void shouldNotOverrideExistingAuthentication() throws ServletException, IOException {
        // Given: Authentication is already set in SecurityContext
        Authentication existingAuth = mock(Authentication.class);
        SecurityContextHolder.getContext().setAuthentication(existingAuth);

        when(request.getHeader("Authorization")).thenReturn("Bearer " + VALID_TOKEN);

        // When: Filter processes the request
        jwtAuthenticationFilter.doFilterInternal(request, response, filterChain);

        // Then: Existing authentication is preserved
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        assertThat(authentication).isEqualTo(existingAuth);

        // JWT validation is never called
        verify(jwtService, never()).validateToken(anyString());

        // Filter chain continues
        verify(filterChain).doFilter(request, response);
    }

    @Test
    @DisplayName("Should extract token correctly from Bearer Authorization header")
    void shouldExtractTokenFromBearerHeader() throws ServletException, IOException {
        // Given: Authorization header with Bearer token and extra spaces
        String tokenWithSpaces = "Bearer  " + VALID_TOKEN; // Extra space
        when(request.getHeader("Authorization")).thenReturn(tokenWithSpaces);
        when(jwtService.validateToken(anyString())).thenReturn(false); // Prevent full auth flow

        // When: Filter processes the request
        jwtAuthenticationFilter.doFilterInternal(request, response, filterChain);

        // Then: Token is extracted correctly (extra spaces trimmed)
        verify(jwtService).validateToken(VALID_TOKEN);

        // Filter chain continues
        verify(filterChain).doFilter(request, response);
    }

    @Test
    @DisplayName("Should continue filter chain even when unexpected exception occurs")
    void shouldContinueWhenUnexpectedExceptionOccurs() throws ServletException, IOException {
        // Given: Unexpected exception during token processing
        when(request.getHeader("Authorization")).thenReturn("Bearer " + VALID_TOKEN);
        when(jwtService.validateToken(VALID_TOKEN))
                .thenThrow(new RuntimeException("Unexpected error"));

        // When: Filter processes the request
        jwtAuthenticationFilter.doFilterInternal(request, response, filterChain);

        // Then: No authentication is set
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        assertThat(authentication).isNull();

        // Filter chain continues (exception is caught and logged)
        verify(filterChain).doFilter(request, response);
    }

    @Test
    @DisplayName("Should set authentication details from request")
    void shouldSetAuthenticationDetailsFromRequest() throws ServletException, IOException {
        // Given: Valid token and user
        when(request.getHeader("Authorization")).thenReturn("Bearer " + VALID_TOKEN);
        when(jwtService.validateToken(VALID_TOKEN)).thenReturn(true);
        when(jwtService.extractUsername(VALID_TOKEN)).thenReturn(TEST_USERNAME);
        when(userRepository.findByUsername(TEST_USERNAME)).thenReturn(Optional.of(testUser));

        // When: Filter processes the request
        jwtAuthenticationFilter.doFilterInternal(request, response, filterChain);

        // Then: Authentication contains details from request
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        assertThat(authentication).isNotNull();
        assertThat(authentication.getDetails()).isNotNull();

        // Filter chain continues
        verify(filterChain).doFilter(request, response);
    }

    @Test
    @DisplayName("Should include user authorities in authentication")
    void shouldIncludeUserAuthorities() throws ServletException, IOException {
        // Given: Valid token and user
        when(request.getHeader("Authorization")).thenReturn("Bearer " + VALID_TOKEN);
        when(jwtService.validateToken(VALID_TOKEN)).thenReturn(true);
        when(jwtService.extractUsername(VALID_TOKEN)).thenReturn(TEST_USERNAME);
        when(userRepository.findByUsername(TEST_USERNAME)).thenReturn(Optional.of(testUser));

        // When: Filter processes the request
        jwtAuthenticationFilter.doFilterInternal(request, response, filterChain);

        // Then: Authentication contains user authorities
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        assertThat(authentication).isNotNull();
        assertThat(authentication.getAuthorities()).isNotEmpty();
        assertThat(authentication.getAuthorities())
                .hasSize(1)
                .allMatch(auth -> auth.getAuthority().equals("ROLE_USER"));

        // Filter chain continues
        verify(filterChain).doFilter(request, response);
    }

    @Test
    @DisplayName("Should clear SecurityContext properly between requests")
    void shouldClearSecurityContextBetweenRequests() throws ServletException, IOException {
        // Given: First request with valid authentication
        when(request.getHeader("Authorization")).thenReturn("Bearer " + VALID_TOKEN);
        when(jwtService.validateToken(VALID_TOKEN)).thenReturn(true);
        when(jwtService.extractUsername(VALID_TOKEN)).thenReturn(TEST_USERNAME);
        when(userRepository.findByUsername(TEST_USERNAME)).thenReturn(Optional.of(testUser));

        // When: First request is processed
        jwtAuthenticationFilter.doFilterInternal(request, response, filterChain);
        Authentication firstAuth = SecurityContextHolder.getContext().getAuthentication();
        assertThat(firstAuth).isNotNull();

        // Then: Clear context manually (simulating new request)
        SecurityContextHolder.clearContext();

        // When: Second request without token
        when(request.getHeader("Authorization")).thenReturn(null);
        jwtAuthenticationFilter.doFilterInternal(request, response, filterChain);

        // Then: Authentication is cleared
        Authentication secondAuth = SecurityContextHolder.getContext().getAuthentication();
        assertThat(secondAuth).isNull();
    }
}
