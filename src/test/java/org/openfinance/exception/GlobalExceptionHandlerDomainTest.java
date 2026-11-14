package org.openfinance.exception;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.util.Locale;
import org.apache.catalina.connector.ClientAbortException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.openfinance.exception.GlobalExceptionHandler.ErrorResponse;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;

/**
 * Handlers not covered by the backup/contract suites: account deletion guard, file storage,
 * unauthorized and account-locked mapping, and client-abort handling. Beyond status codes these pin
 * the no-leak contract: internal details (file paths, entity ids, causes) must never reach the API
 * response body.
 */
@DisplayName("GlobalExceptionHandler — domain exception mapping")
class GlobalExceptionHandlerDomainTest {

    private final ResourceBundleMessageSource messages = messages();
    private final GlobalExceptionHandler handler = new GlobalExceptionHandler(messages);
    private final ServletWebRequest request =
            new ServletWebRequest(new MockHttpServletRequest("DELETE", "/api/v1/accounts/5"));

    @BeforeEach
    void useEnglishLocale() {
        // The JVM default locale on some dev machines/CI is fr_FR; pin English unless a test
        // explicitly switches (see fileStorageMessageIsLocalized).
        LocaleContextHolder.setLocale(Locale.ENGLISH);
    }

    @AfterEach
    void resetLocale() {
        LocaleContextHolder.resetLocaleContext();
    }

    @Test
    @DisplayName("Account with transactions maps to 400 with the detailed guard message")
    void accountHasTransactionsMapsTo400() {
        ResponseEntity<ErrorResponse> response =
                handler.handleAccountHasTransactions(
                        AccountHasTransactionsException.forAccount("Checking", 3), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        ErrorResponse body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.getStatus()).isEqualTo(400);
        assertThat(body.getError()).isEqualTo("Bad Request");
        assertThat(body.getMessage()).contains("Checking").contains("3 active transactions");
        assertThat(body.getPath()).isEqualTo("/api/v1/accounts/5");
        assertThat(body.getTimestamp()).isNotNull();
    }

    @Test
    @DisplayName("Singular transaction count is not pluralised in the guard message")
    void accountHasTransactionsSingularGrammar() {
        AccountHasTransactionsException ex = AccountHasTransactionsException.forAccount("Vault", 1);
        assertThat(ex.getMessage()).contains("1 active transaction.");
        assertThat(ex.getMessage()).doesNotContain("1 active transactions");
    }

    @Test
    @DisplayName(
            "File storage failures map to a generic localized 500 without leaking paths or causes")
    void fileStorageMapsToGeneric500() {
        FileStorageException ex =
                FileStorageException.writeFailure(
                        "/srv/private/user-42/backup.enc",
                        new IOException("disk full at /dev/sda1"));

        ResponseEntity<ErrorResponse> response = handler.handleFileStorageException(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        ErrorResponse body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.getMessage())
                .isEqualTo(messages.getMessage("error.file.storage", null, Locale.ENGLISH));
        assertThat(body.getMessage())
                .doesNotContain("backup.enc")
                .doesNotContain("/srv")
                .doesNotContain("disk full");
    }

    @Test
    @DisplayName("File storage message follows the request locale")
    void fileStorageMessageIsLocalized() {
        LocaleContextHolder.setLocale(Locale.FRENCH);
        ResponseEntity<ErrorResponse> response =
                handler.handleFileStorageException(new FileStorageException("boom"), request);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getMessage())
                .isEqualTo(messages.getMessage("error.file.storage", null, Locale.FRENCH));
    }

    @Test
    @DisplayName("Cross-user simulation access maps to a generic 403 that hides both ids")
    void unauthorizedSimulationAccessHidesIds() {
        UnauthorizedException ex = UnauthorizedException.accessingAnotherUsersSimulation(99L, 55L);
        // The exception itself keeps the detail for server-side logs...
        assertThat(ex.getMessage()).contains("99").contains("55");
        assertThat(ex.getMessageKey()).isEqualTo("error.unauthorized");
        assertThat(ex.getMessageArgs()).isEmpty();

        ResponseEntity<ErrorResponse> response = handler.handleUnauthorized(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody()).isNotNull();
        // ...but the API response must not confirm which simulation exists or who asked.
        assertThat(response.getBody().getMessage())
                .isEqualTo(messages.getMessage("error.unauthorized", null, Locale.ENGLISH))
                .doesNotContain("99")
                .doesNotContain("55")
                .doesNotContain("simulation");
    }

    @Test
    @DisplayName("Unauthorized operation factory names the blocked operation for logs only")
    void unauthorizedOperationFactory() {
        UnauthorizedException ex = UnauthorizedException.operation("deleteAccount");
        assertThat(ex.getMessage()).isEqualTo("Unauthorized to perform operation: deleteAccount");

        ResponseEntity<ErrorResponse> response = handler.handleUnauthorized(ex, request);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getMessage()).doesNotContain("deleteAccount");
    }

    @Test
    @DisplayName("Account locked maps to 403 with the localized lock message")
    void accountLockedMapsTo403() {
        ResponseEntity<ErrorResponse> response =
                handler.handleAccountLocked(
                        new AccountLockedException("locked after 5 attempts for user bob"),
                        request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getMessage())
                .isEqualTo(messages.getMessage("error.account.locked", null, Locale.ENGLISH))
                .doesNotContain("bob");
    }

    @Test
    @DisplayName("Client abort is swallowed (no response can be written to a closed socket)")
    void clientAbortReturnsNull() {
        assertThat(handler.handleClientAbort(new ClientAbortException("broken pipe"))).isNull();
    }

    @Test
    @DisplayName("Request path falls back to 'unknown' without a uri= description")
    void requestPathFallback() {
        WebRequest noDescription = mock(WebRequest.class);
        when(noDescription.getDescription(false)).thenReturn(null);
        ResponseEntity<ErrorResponse> nullDesc =
                handler.handleUnauthorized(new UnauthorizedException("x"), noDescription);
        assertThat(nullDesc.getBody()).isNotNull();
        assertThat(nullDesc.getBody().getPath()).isEqualTo("unknown");

        WebRequest oddDescription = mock(WebRequest.class);
        when(oddDescription.getDescription(false)).thenReturn("session=abc");
        ResponseEntity<ErrorResponse> oddDesc =
                handler.handleUnauthorized(new UnauthorizedException("x"), oddDescription);
        assertThat(oddDesc.getBody()).isNotNull();
        assertThat(oddDesc.getBody().getPath()).isEqualTo("unknown");
    }

    private static ResourceBundleMessageSource messages() {
        ResourceBundleMessageSource source = new ResourceBundleMessageSource();
        source.setBasename("i18n/messages");
        source.setDefaultEncoding("UTF-8");
        source.setDefaultLocale(Locale.ENGLISH);
        return source;
    }
}
