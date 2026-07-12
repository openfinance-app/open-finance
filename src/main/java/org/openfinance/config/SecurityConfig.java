// filepath: src/main/java/org/openfinance/config/SecurityConfig.java

package org.openfinance.config;

import java.util.Arrays;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.openfinance.security.EncryptionKeyFilter;
import org.openfinance.security.JwtAuthenticationFilter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * Spring Security configuration for Open-Finance application.
 *
 * <p>
 * This configuration sets up security for the application including:
 *
 * <ul>
 * <li>JWT-based authentication with stateless sessions
 * <li>CORS configuration for frontend communication
 * <li>Password encoding with BCrypt
 * <li>Authorization rules for public and protected endpoints
 * </ul>
 *
 * <p>
 * Endpoint Security:
 *
 * <ul>
 * <li>Public: /api/v1/auth/register, /api/v1/auth/login, /api/v1/health/**
 * (health checks)
 * <li>Protected: /api/v1/auth/profile (GET/PUT) and all other endpoints require
 * valid JWT token
 * </ul>
 *
 * <p>
 * Requirement REQ-2.1.3: JWT authentication with stateless session management
 *
 * @see org.openfinance.security.JwtAuthenticationFilter
 * @see org.openfinance.security.PasswordService
 * @author Open-Finance Development Team
 * @version 1.0
 * @since 2026-01-30
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

        private final JwtAuthenticationFilter jwtAuthenticationFilter;
        private final EncryptionKeyFilter encryptionKeyFilter;
        private final SecurityHeadersFilter securityHeadersFilter;

        /**
         * Comma-separated list of allowed CORS origins, driven by application
         * configuration. Defaults
         * to localhost dev servers; override via APPLICATION_CORS_ALLOWED_ORIGINS
         * environment variable
         * in production.
         *
         * <p>
         * Requirement TASK-16.2.4: Environment variable driven secrets / configuration.
         */
        @Value("${application.cors.allowed-origins:http://localhost:3000,http://localhost:5173}")
        private String allowedOrigins;

        /**
         * Configures the security filter chain for HTTP requests.
         *
         * <p>
         * Configuration:
         *
         * <ul>
         * <li>JWT authentication filter runs before standard authentication
         * <li>CSRF protection disabled (REST API uses JWT tokens)
         * <li>CORS enabled for frontend integration
         * <li>Stateless session management (JWT-based)
         * <li>Public endpoints: /api/v1/auth/register, /api/v1/auth/login,
         * /api/v1/health/**
         * <li>Protected endpoints: /api/v1/auth/profile and all other /api/** endpoints
         * require
         * authentication
         * </ul>
         *
         * <p>
         * The JWT filter extracts and validates tokens from the Authorization header,
         * loading user
         * details and setting the SecurityContext for authenticated requests.
         *
         * @param http the HttpSecurity to configure
         * @return configured SecurityFilterChain
         * @throws Exception if configuration fails
         */
        @Bean
        public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
                http
                                // Disable CSRF (REST API with JWT tokens)
                                .csrf(AbstractHttpConfigurer::disable)

                                // Configure CORS for frontend communication
                                .cors(cors -> cors.configurationSource(corsConfigurationSource()))

                                // Configure authorization rules
                                .authorizeHttpRequests(
                                                auth -> auth
                                                                // Public auth + health endpoints
                                                                .requestMatchers(
                                                                                "/api/v1/auth/register",
                                                                                "/api/v1/auth/login")
                                                                .permitAll()
                                                                 .requestMatchers("/api/v1/config/security")
                                                                 .permitAll()
                                                                 .requestMatchers("/api/v1/health/**")
                                                                 .permitAll()
                                                                .requestMatchers("/api/v1/ai/health")
                                                                .permitAll()
                                                                // Requirement TASK-16.2.5: Expose actuator health
                                                                // endpoint
                                                                // publicly
                                                                .requestMatchers("/actuator/health", "/actuator/info")
                                                                .permitAll()
                                                                // Requirement TASK-15.3.2: Swagger / OpenAPI UI
                                                                // endpoints
                                                                // are public
                                                                .requestMatchers(
                                                                                "/swagger-ui.html",
                                                                                "/swagger-ui/**",
                                                                                "/v3/api-docs",
                                                                                "/v3/api-docs/**",
                                                                                "/swagger-resources/**",
                                                                                "/webjars/**")
                                                                .permitAll()
                                                                // All API routes (except those already permitted above)
                                                                // require authentication
                                                                .requestMatchers("/api/**")
                                                                .authenticated()
                                                                // SPA shell and static assets are public — index.html
                                                                // contains no sensitive
                                                                // data; authenticated data is fetched via protected
                                                                // /api/**
                                                                // endpoints above.
                                                                .anyRequest()
                                                                .permitAll())

                                // Configure stateless session management (JWT-based)
                                .sessionManagement(
                                                session -> session
                                                                .sessionCreationPolicy(SessionCreationPolicy.STATELESS))

                                // Add JWT authentication filter before standard authentication
                                .addFilterBefore(
                                                jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)

                                // Encryption key filter runs after JWT so that userId is available
                                .addFilterAfter(encryptionKeyFilter, JwtAuthenticationFilter.class)

                                // Requirement TASK-15.1.2: Register security headers filter
                                .addFilterAfter(securityHeadersFilter, UsernamePasswordAuthenticationFilter.class);

                return http.build();
        }

        /**
         * Configures CORS (Cross-Origin Resource Sharing) to allow frontend
         * communication.
         *
         * <p>
         * Allowed origins are configured in application.properties: <code>
         * spring.web.cors.allowed-origins</code>
         *
         * <p>
         * Configuration:
         *
         * <ul>
         * <li>Allowed origins: http://localhost:3000, http://localhost:5173 (React dev
         * servers)
         * <li>Allowed methods: GET, POST, PUT, DELETE, PATCH, OPTIONS
         * <li>Allowed headers: All (*)
         * <li>Credentials: Enabled (for cookies/auth headers)
         * </ul>
         *
         * @return CorsConfigurationSource with configured CORS settings
         */
        @Bean
        public CorsConfigurationSource corsConfigurationSource() {
                CorsConfiguration configuration = new CorsConfiguration();

                // Requirement TASK-16.2.4: CORS origins driven by environment variable.
                // In production set APPLICATION_CORS_ALLOWED_ORIGINS to your frontend URL.
                List<String> origins = Arrays.stream(allowedOrigins.split(","))
                                .map(String::trim)
                                .filter(s -> !s.isEmpty())
                                .toList();
                configuration.setAllowedOrigins(origins);

                // Allow common HTTP methods
                configuration.setAllowedMethods(
                                List.of("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS"));

                // Allow all headers
                configuration.setAllowedHeaders(List.of("*"));

                // Allow credentials (cookies, authorization headers)
                configuration.setAllowCredentials(true);

                // Apply CORS configuration to all endpoints
                UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
                source.registerCorsConfiguration("/**", configuration);

                return source;
        }

        /**
         * Creates a BCrypt password encoder for hashing user passwords.
         *
         * <p>
         * BCrypt is a strong, adaptive hashing function designed for password storage.
         * It
         * automatically handles salt generation and is resistant to brute-force
         * attacks.
         *
         * <p>
         * <strong>Important:</strong> This is used for user account passwords only, NOT
         * for data
         * encryption. Data encryption uses AES-256-GCM with PBKDF2 key derivation.
         *
         * <p>
         * Configuration:
         *
         * <ul>
         * <li>Algorithm: BCrypt
         * <li>Strength: 10 (default, provides good security/performance balance)
         * <li>Salt: Automatically generated per password
         * </ul>
         *
         * @return BCryptPasswordEncoder instance for password hashing
         * @see org.openfinance.security.PasswordService
         */
        @Bean
        public PasswordEncoder passwordEncoder() {
                return new BCryptPasswordEncoder();
        }
}
