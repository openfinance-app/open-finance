package org.openfinance.exception;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.MessageSource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.context.request.WebRequest;

/**
 * Unit tests for {@link GlobalExceptionHandler#handleBackupException} — verifies HTTP status is
 * derived from the explicit {@link BackupException.Kind} rather than fragile English substring
 * matching on the message (which broke as soon as messages were translated).
 */
@DisplayName("GlobalExceptionHandler — BackupException mapping")
class GlobalExceptionHandlerBackupTest {

    private final GlobalExceptionHandler handler =
            new GlobalExceptionHandler(mock(MessageSource.class));

    private WebRequest request() {
        WebRequest req = mock(WebRequest.class);
        when(req.getDescription(false)).thenReturn("uri=/api/v1/backup");
        return req;
    }

    @Test
    @DisplayName("VALIDATION kind maps to 400 Bad Request")
    void validationKindMapsTo400() {
        ResponseEntity<GlobalExceptionHandler.ErrorResponse> response =
                handler.handleBackupException(
                        BackupException.validation("Uploaded file is empty"), request());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("NOT_FOUND kind maps to 404 Not Found")
    void notFoundKindMapsTo404() {
        ResponseEntity<GlobalExceptionHandler.ErrorResponse> response =
                handler.handleBackupException(
                        BackupException.notFound("Backup file not found"), request());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("INTERNAL kind maps to 500 Internal Server Error")
    void internalKindMapsTo500() {
        ResponseEntity<GlobalExceptionHandler.ErrorResponse> response =
                handler.handleBackupException(
                        BackupException.internal("Failed to read backup file"), request());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    }
}
