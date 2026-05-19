package org.openfinance.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * SpringDoc OpenAPI 3 configuration for the Open-Finance REST API.
 *
 * <p>Requirement TASK-15.3.2: Provides interactive API documentation via Swagger UI at {@code
 * /swagger-ui.html} and the raw OpenAPI 3.0 spec at {@code /v3/api-docs}.
 *
 * <p>Authentication: All endpoints are secured with Bearer JWT tokens. The Swagger UI includes an
 * "Authorize" button that accepts the JWT token for interactive testing.
 *
 * <p>UI can be disabled in production by setting:
 *
 * <pre>springdoc.swagger-ui.enabled=false</pre>
 *
 * @author Open Finance Team
 * @version 1.0
 * @since 2026-03-20
 * @see <a href="http://localhost:8080/swagger-ui.html">Swagger UI (local)</a>
 * @see <a href="http://localhost:8080/v3/api-docs">OpenAPI JSON spec (local)</a>
 */
@Configuration
public class OpenApiConfig {

    /** Security scheme name used for Bearer JWT throughout the spec. */
    private static final String BEARER_AUTH_SCHEME = "bearerAuth";

    @Value("${application.version:0.1.0}")
    private String appVersion;

    /**
     * Builds the {@link OpenAPI} bean with application metadata, server list, JWT security scheme,
     * and global security requirement.
     *
     * @return the configured {@link OpenAPI} instance
     */
    @Bean
    public OpenAPI openFinanceOpenAPI() {
        return new OpenAPI()
                .info(buildApiInfo())
                .addServersItem(
                        new Server()
                                .url("http://localhost:8080")
                                .description("Local development server"))
                .components(
                        new Components()
                                .addSecuritySchemes(BEARER_AUTH_SCHEME, buildJwtSecurityScheme()))
                .addSecurityItem(new SecurityRequirement().addList(BEARER_AUTH_SCHEME));
    }

    /**
     * Constructs the {@link Info} block describing the Open-Finance API.
     *
     * @return populated {@link Info} object
     */
    private Info buildApiInfo() {
        return new Info()
                .title("Open-Finance API")
                .version(appVersion)
                .description(
                        "Personal wealth management REST API.\n\n"
                                + "**Features:**\n"
                                + "- Multi-currency asset and account management\n"
                                + "- Transaction entry, splits, and recurring rules\n"
                                + "- QIF / OFX / QFX file import with duplicate detection\n"
                                + "- Budget tracking and category management\n"
                                + "- PDF / CSV / Excel report generation\n"
                                + "- Local AI assistant (Ollama integration)\n"
                                + "- Market data and ECB exchange rate feeds\n\n"
                                + "**Authentication:** All endpoints (except `/api/v1/auth/login` and "
                                + "`/api/v1/auth/register`) require a valid JWT Bearer token obtained "
                                + "from the `/api/v1/auth/login` endpoint.\n"
                                + "Use the **Authorize** button above to enter your token.")
                .contact(
                        new Contact()
                                .name("Open Finance Team")
                                .url("https://github.com/open-finance"))
                .license(
                        new License()
                                .name("MIT License")
                                .url("https://opensource.org/licenses/MIT"));
    }

    /**
     * Creates the HTTP Bearer JWT security scheme definition.
     *
     * @return a {@link SecurityScheme} configured for JWT Bearer tokens
     */
    private SecurityScheme buildJwtSecurityScheme() {
        return new SecurityScheme()
                .type(SecurityScheme.Type.HTTP)
                .scheme("bearer")
                .bearerFormat("JWT")
                .description(
                        "JWT token obtained from POST /api/v1/auth/login. "
                                + "Enter the token value (without the 'Bearer ' prefix).");
    }
}
