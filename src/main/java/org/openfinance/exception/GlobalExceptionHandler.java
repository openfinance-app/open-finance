package org.openfinance.exception;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;

/**
 * Global exception handler for REST API endpoints.
 *
 * <p>Provides centralized exception handling across all controllers using Spring's
 * {@code @ControllerAdvice}. Converts exceptions into standardized error responses with appropriate
 * HTTP status codes.
 *
 * <p><strong>Handled Exception Types:</strong>
 *
 * <ul>
 *   <li>{@link DuplicateUserException} - HTTP 409 Conflict
 *   <li>{@link BadCredentialsException} - HTTP 401 Unauthorized
 *   <li>{@link MethodArgumentNotValidException} - HTTP 400 Bad Request
 *   <li>{@link IllegalArgumentException} - HTTP 400 Bad Request
 *   <li>{@link Exception} - HTTP 500 Internal Server Error (catch-all)
 * </ul>
 *
 * <p>Requirement REQ-2.1.1: User registration error handling
 *
 * <p>Requirement REQ-2.1.3: User authentication error handling
 *
 * @see ErrorResponse
 * @author Open-Finance Development Team
 * @version 1.0
 * @since 2026-01-30
 */
@ControllerAdvice
@RequiredArgsConstructor
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private final MessageSource messageSource;

    /**
     * Handles duplicate user exceptions (username or email already exists).
     *
     * @param ex the exception
     * @param request the web request
     * @return HTTP 409 Conflict with error details
     */
    @ExceptionHandler(DuplicateUserException.class)
    public ResponseEntity<ErrorResponse> handleDuplicateUser(
            DuplicateUserException ex, WebRequest request) {

        log.warn("Duplicate user registration attempt: {}", ex.getMessage());

        ErrorResponse errorResponse =
                ErrorResponse.builder()
                        .timestamp(LocalDateTime.now())
                        .status(HttpStatus.CONFLICT.value())
                        .error(HttpStatus.CONFLICT.getReasonPhrase())
                        .message(ex.getMessage())
                        .path(getRequestPath(request))
                        .build();

        return ResponseEntity.status(HttpStatus.CONFLICT).body(errorResponse);
    }

    /** Handles duplicate payee name exceptions (409 Conflict). */
    @ExceptionHandler(DuplicatePayeeException.class)
    public ResponseEntity<ErrorResponse> handleDuplicatePayee(
            DuplicatePayeeException ex, WebRequest request) {

        log.warn("Duplicate payee name: {}", ex.getMessage());

        ErrorResponse errorResponse =
                ErrorResponse.builder()
                        .timestamp(LocalDateTime.now())
                        .status(HttpStatus.CONFLICT.value())
                        .error(HttpStatus.CONFLICT.getReasonPhrase())
                        .message(ex.getMessage())
                        .path(getRequestPath(request))
                        .build();

        return ResponseEntity.status(HttpStatus.CONFLICT).body(errorResponse);
    }

    /**
     * Handles duplicate simulation name exceptions.
     *
     * <p>Thrown when attempting to create or update a simulation with a name that already exists
     * for the current user.
     *
     * @param ex the exception
     * @param request the web request
     * @return HTTP 409 Conflict with error details
     */
    @ExceptionHandler(DuplicateSimulationException.class)
    public ResponseEntity<ErrorResponse> handleDuplicateSimulation(
            DuplicateSimulationException ex, WebRequest request) {

        log.warn("Duplicate simulation name: {}", ex.getMessage());

        ErrorResponse errorResponse =
                ErrorResponse.builder()
                        .timestamp(LocalDateTime.now())
                        .status(HttpStatus.CONFLICT.value())
                        .error(HttpStatus.CONFLICT.getReasonPhrase())
                        .message(ex.getMessage())
                        .path(getRequestPath(request))
                        .build();

        return ResponseEntity.status(HttpStatus.CONFLICT).body(errorResponse);
    }

    /**
     * Handles authentication failures (invalid credentials).
     *
     * <p>This handler is invoked when login fails due to:
     *
     * <ul>
     *   <li>Username not found
     *   <li>Incorrect login password
     *   <li>Incorrect master password
     * </ul>
     *
     * @param ex the authentication exception
     * @param request the web request
     * @return HTTP 401 Unauthorized with error message
     */
    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ErrorResponse> handleBadCredentials(
            BadCredentialsException ex, WebRequest request) {

        log.warn("Authentication failed: {}", ex.getMessage());

        ErrorResponse errorResponse =
                ErrorResponse.builder()
                        .timestamp(LocalDateTime.now())
                        .status(HttpStatus.UNAUTHORIZED.value())
                        .error(HttpStatus.UNAUTHORIZED.getReasonPhrase())
                        .message(ex.getMessage())
                        .path(getRequestPath(request))
                        .build();

        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(errorResponse);
    }

    /**
     * Handles validation errors from {@code @Valid} annotation on request bodies.
     *
     * @param ex the validation exception
     * @param request the web request
     * @return HTTP 400 Bad Request with field-specific validation errors
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationErrors(
            MethodArgumentNotValidException ex, WebRequest request) {

        Map<String, String> validationErrors = new HashMap<>();
        ex.getBindingResult()
                .getAllErrors()
                .forEach(
                        error -> {
                            String fieldName = ((FieldError) error).getField();
                            String errorMessage = error.getDefaultMessage();
                            validationErrors.put(fieldName, errorMessage);
                        });

        log.warn("Validation failed: {}", validationErrors);

        String message =
                messageSource.getMessage(
                        "error.validation.failed", null, LocaleContextHolder.getLocale());

        ErrorResponse errorResponse =
                ErrorResponse.builder()
                        .timestamp(LocalDateTime.now())
                        .status(HttpStatus.BAD_REQUEST.value())
                        .error(HttpStatus.BAD_REQUEST.getReasonPhrase())
                        .message(message)
                        .path(getRequestPath(request))
                        .validationErrors(validationErrors)
                        .build();

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse);
    }

    /**
     * Handles method-level validation errors (Spring 6+).
     *
     * <p>Introduced in Spring Framework 6.0 to support method-level validation with
     * {@code @Validated} on controller methods. Provides similar functionality to {@link
     * #handleValidationErrors(MethodArgumentNotValidException, WebRequest)} but for method
     * parameter validation.
     *
     * @param ex the validation exception
     * @param request the web request
     * @return HTTP 400 Bad Request with validation error details
     */
    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<ErrorResponse> handleHandlerMethodValidation(
            HandlerMethodValidationException ex, WebRequest request) {

        Map<String, String> validationErrors = new HashMap<>();

        // Extract parameter validation errors
        ex.getAllValidationResults()
                .forEach(
                        parameterValidationResult -> {
                            String parameterName =
                                    parameterValidationResult
                                            .getMethodParameter()
                                            .getParameterName();
                            parameterValidationResult
                                    .getResolvableErrors()
                                    .forEach(
                                            error -> {
                                                String errorMessage = error.getDefaultMessage();
                                                validationErrors.put(
                                                        parameterName != null
                                                                ? parameterName
                                                                : "parameter",
                                                        errorMessage);
                                            });
                        });

        log.warn("Method validation failed: {}", validationErrors);

        String message =
                messageSource.getMessage(
                        "error.validation.failed", null, LocaleContextHolder.getLocale());

        ErrorResponse errorResponse =
                ErrorResponse.builder()
                        .timestamp(LocalDateTime.now())
                        .status(HttpStatus.BAD_REQUEST.value())
                        .error(HttpStatus.BAD_REQUEST.getReasonPhrase())
                        .message(message)
                        .path(getRequestPath(request))
                        .validationErrors(validationErrors)
                        .build();

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse);
    }

    /**
     * Handles account not found exceptions.
     *
     * @param ex the exception
     * @param request the web request
     * @return HTTP 404 Not Found with error message
     */
    @ExceptionHandler(AccountNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleAccountNotFound(
            AccountNotFoundException ex, WebRequest request) {

        log.warn("Account not found: {}", ex.getMessage());

        ErrorResponse errorResponse =
                ErrorResponse.builder()
                        .timestamp(LocalDateTime.now())
                        .status(HttpStatus.NOT_FOUND.value())
                        .error(HttpStatus.NOT_FOUND.getReasonPhrase())
                        .message(ex.getMessage())
                        .path(getRequestPath(request))
                        .build();

        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(errorResponse);
    }

    /**
     * Handles account has transactions exceptions.
     *
     * <p>Thrown when attempting to delete an account that has active transactions. Returns HTTP 400
     * Bad Request with a user-friendly error message.
     *
     * @param ex the exception
     * @param request the web request
     * @return HTTP 400 Bad Request with error message
     */
    @ExceptionHandler(AccountHasTransactionsException.class)
    public ResponseEntity<ErrorResponse> handleAccountHasTransactions(
            AccountHasTransactionsException ex, WebRequest request) {

        log.warn("Cannot delete account with transactions: {}", ex.getMessage());

        ErrorResponse errorResponse =
                ErrorResponse.builder()
                        .timestamp(LocalDateTime.now())
                        .status(HttpStatus.BAD_REQUEST.value())
                        .error(HttpStatus.BAD_REQUEST.getReasonPhrase())
                        .message(ex.getMessage())
                        .path(getRequestPath(request))
                        .build();

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse);
    }

    /**
     * Handles category not found exceptions.
     *
     * @param ex the exception
     * @param request the web request
     * @return HTTP 404 Not Found with error message
     */
    @ExceptionHandler(CategoryNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleCategoryNotFound(
            CategoryNotFoundException ex, WebRequest request) {

        log.warn("Category not found: {}", ex.getMessage());

        ErrorResponse errorResponse =
                ErrorResponse.builder()
                        .timestamp(LocalDateTime.now())
                        .status(HttpStatus.NOT_FOUND.value())
                        .error(HttpStatus.NOT_FOUND.getReasonPhrase())
                        .message(resolveMessage(ex))
                        .path(getRequestPath(request))
                        .build();

        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(errorResponse);
    }

    /**
     * Handles invalid category operation exceptions.
     *
     * <p>Thrown when attempting an invalid category operation such as:
     *
     * <ul>
     *   <li>Deleting a system category
     *   <li>Deleting a category that has subcategories
     *   <li>Deleting a category that is assigned to transactions
     *   <li>A category being its own parent
     * </ul>
     *
     * @param ex the exception
     * @param request the web request
     * @return HTTP 400 Bad Request with error message
     */
    @ExceptionHandler(InvalidCategoryException.class)
    public ResponseEntity<ErrorResponse> handleInvalidCategory(
            InvalidCategoryException ex, WebRequest request) {

        log.warn("Invalid category operation: {}", ex.getMessage());

        ErrorResponse errorResponse =
                ErrorResponse.builder()
                        .timestamp(LocalDateTime.now())
                        .status(HttpStatus.BAD_REQUEST.value())
                        .error(HttpStatus.BAD_REQUEST.getReasonPhrase())
                        .message(ex.getMessage())
                        .path(getRequestPath(request))
                        .build();

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse);
    }

    /**
     * Handles invalid transaction exceptions.
     *
     * @param ex the exception
     * @param request the web request
     * @return HTTP 400 Bad Request with error message
     */
    @ExceptionHandler(InvalidTransactionException.class)
    public ResponseEntity<ErrorResponse> handleInvalidTransaction(
            InvalidTransactionException ex, WebRequest request) {

        log.warn("Invalid transaction: {}", ex.getMessage());

        ErrorResponse errorResponse =
                ErrorResponse.builder()
                        .timestamp(LocalDateTime.now())
                        .status(HttpStatus.BAD_REQUEST.value())
                        .error(HttpStatus.BAD_REQUEST.getReasonPhrase())
                        .message(ex.getMessage())
                        .path(getRequestPath(request))
                        .build();

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse);
    }

    /**
     * Handles transaction not found exceptions.
     *
     * @param ex the exception
     * @param request the web request
     * @return HTTP 404 Not Found with error message
     */
    @ExceptionHandler(TransactionNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleTransactionNotFound(
            TransactionNotFoundException ex, WebRequest request) {

        log.warn("Transaction not found: {}", ex.getMessage());

        ErrorResponse errorResponse =
                ErrorResponse.builder()
                        .timestamp(LocalDateTime.now())
                        .status(HttpStatus.NOT_FOUND.value())
                        .error(HttpStatus.NOT_FOUND.getReasonPhrase())
                        .message(ex.getMessage())
                        .path(getRequestPath(request))
                        .build();

        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(errorResponse);
    }

    /**
     * Handles asset not found exceptions.
     *
     * @param ex the exception
     * @param request the web request
     * @return HTTP 404 Not Found with error message
     */
    @ExceptionHandler(AssetNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleAssetNotFound(
            AssetNotFoundException ex, WebRequest request) {

        log.warn("Asset not found: {}", ex.getMessage());

        ErrorResponse errorResponse =
                ErrorResponse.builder()
                        .timestamp(LocalDateTime.now())
                        .status(HttpStatus.NOT_FOUND.value())
                        .error(HttpStatus.NOT_FOUND.getReasonPhrase())
                        .message(ex.getMessage())
                        .path(getRequestPath(request))
                        .build();

        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(errorResponse);
    }

    /**
     * Handles liability not found exceptions.
     *
     * @param ex the exception
     * @param request the web request
     * @return HTTP 404 Not Found with error message
     */
    @ExceptionHandler(LiabilityNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleLiabilityNotFound(
            LiabilityNotFoundException ex, WebRequest request) {

        log.warn("Liability not found: {}", ex.getMessage());

        ErrorResponse errorResponse =
                ErrorResponse.builder()
                        .timestamp(LocalDateTime.now())
                        .status(HttpStatus.NOT_FOUND.value())
                        .error(HttpStatus.NOT_FOUND.getReasonPhrase())
                        .message(ex.getMessage())
                        .path(getRequestPath(request))
                        .build();

        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(errorResponse);
    }

    /**
     * Handles budget not found exceptions.
     *
     * @param ex the exception
     * @param request the web request
     * @return HTTP 404 Not Found with error message
     */
    @ExceptionHandler(BudgetNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleBudgetNotFound(
            BudgetNotFoundException ex, WebRequest request) {

        log.warn("Budget not found: {}", ex.getMessage());

        ErrorResponse errorResponse =
                ErrorResponse.builder()
                        .timestamp(LocalDateTime.now())
                        .status(HttpStatus.NOT_FOUND.value())
                        .error(HttpStatus.NOT_FOUND.getReasonPhrase())
                        .message(ex.getMessage())
                        .path(getRequestPath(request))
                        .build();

        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(errorResponse);
    }

    /**
     * Handles real estate property not found exceptions.
     *
     * @param ex the exception
     * @param request the web request
     * @return HTTP 404 Not Found with error message
     */
    @ExceptionHandler(RealEstatePropertyNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleRealEstatePropertyNotFound(
            RealEstatePropertyNotFoundException ex, WebRequest request) {

        log.warn("Real estate property not found: {}", ex.getMessage());

        ErrorResponse errorResponse =
                ErrorResponse.builder()
                        .timestamp(LocalDateTime.now())
                        .status(HttpStatus.NOT_FOUND.value())
                        .error(HttpStatus.NOT_FOUND.getReasonPhrase())
                        .message(ex.getMessage())
                        .path(getRequestPath(request))
                        .build();

        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(errorResponse);
    }

    /**
     * Handles attachment not found exceptions.
     *
     * @param ex the exception
     * @param request the web request
     * @return HTTP 404 Not Found with error message
     */
    @ExceptionHandler(AttachmentNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleAttachmentNotFound(
            AttachmentNotFoundException ex, WebRequest request) {

        log.warn("Attachment not found: {}", ex.getMessage());

        ErrorResponse errorResponse =
                ErrorResponse.builder()
                        .timestamp(LocalDateTime.now())
                        .status(HttpStatus.NOT_FOUND.value())
                        .error(HttpStatus.NOT_FOUND.getReasonPhrase())
                        .message(ex.getMessage())
                        .path(getRequestPath(request))
                        .build();

        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(errorResponse);
    }

    /**
     * Handles recurring transaction not found exceptions.
     *
     * @param ex the exception
     * @param request the web request
     * @return HTTP 404 Not Found with error message
     */
    @ExceptionHandler(RecurringTransactionNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleRecurringTransactionNotFound(
            RecurringTransactionNotFoundException ex, WebRequest request) {

        log.warn("Recurring transaction not found: {}", ex.getMessage());

        ErrorResponse errorResponse =
                ErrorResponse.builder()
                        .timestamp(LocalDateTime.now())
                        .status(HttpStatus.NOT_FOUND.value())
                        .error(HttpStatus.NOT_FOUND.getReasonPhrase())
                        .message(ex.getMessage())
                        .path(getRequestPath(request))
                        .build();

        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(errorResponse);
    }

    /**
     * Handles backup exceptions with appropriate status codes based on error type.
     *
     * @param ex the exception
     * @param request the web request
     * @return HTTP 400/404/500 depending on error type
     */
    @ExceptionHandler(BackupException.class)
    public ResponseEntity<ErrorResponse> handleBackupException(
            BackupException ex, WebRequest request) {

        String message = ex.getMessage();
        HttpStatus status;

        // Determine HTTP status based on exception message
        // Check for validation errors first (400 Bad Request)
        if (message != null
                && (message.contains("empty")
                        || message.contains("Invalid")
                        || message.contains("corrupted")
                        || message.contains("Cannot restore")
                        || message.contains("GZIP format")
                        || message.contains("format"))) {
            status = HttpStatus.BAD_REQUEST;
            log.warn("Invalid backup request: {}", message);
        }
        // Then check for resource not found (404 Not Found) - specifically "Backup not
        // found" or "access denied"
        else if (message != null
                && message.contains("Backup")
                && (message.contains("not found") || message.contains("access denied"))) {
            status = HttpStatus.NOT_FOUND;
            log.warn("Backup not found: {}", message);
        }
        // Everything else is internal server error (500)
        else {
            status = HttpStatus.INTERNAL_SERVER_ERROR;
            log.error("Backup operation failed: {}", message, ex);
        }

        ErrorResponse errorResponse =
                ErrorResponse.builder()
                        .timestamp(LocalDateTime.now())
                        .status(status.value())
                        .error(status.getReasonPhrase())
                        .message(message)
                        .path(getRequestPath(request))
                        .build();

        return ResponseEntity.status(status).body(errorResponse);
    }

    /**
     * Handles file storage exceptions (file system errors).
     *
     * @param ex the exception
     * @param request the web request
     * @return HTTP 500 Internal Server Error with error message
     */
    @ExceptionHandler(FileStorageException.class)
    public ResponseEntity<ErrorResponse> handleFileStorageException(
            FileStorageException ex, WebRequest request) {

        log.error("File storage error: {}", ex.getMessage(), ex);

        String message =
                messageSource.getMessage(
                        "error.file.storage", null, LocaleContextHolder.getLocale());
        ErrorResponse errorResponse =
                ErrorResponse.builder()
                        .timestamp(LocalDateTime.now())
                        .status(HttpStatus.INTERNAL_SERVER_ERROR.value())
                        .error(HttpStatus.INTERNAL_SERVER_ERROR.getReasonPhrase())
                        .message(message)
                        .path(getRequestPath(request))
                        .build();

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
    }

    /**
     * Handles resource not found exceptions (generic).
     *
     * @param ex the exception
     * @param request the web request
     * @return HTTP 404 Not Found with error message
     */
    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleResourceNotFound(
            ResourceNotFoundException ex, WebRequest request) {

        log.warn("Resource not found: {}", ex.getMessage());

        ErrorResponse errorResponse =
                ErrorResponse.builder()
                        .timestamp(LocalDateTime.now())
                        .status(HttpStatus.NOT_FOUND.value())
                        .error(HttpStatus.NOT_FOUND.getReasonPhrase())
                        .message(resolveMessage(ex))
                        .path(getRequestPath(request))
                        .build();

        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(errorResponse);
    }

    /**
     * Handles illegal state exceptions (invalid operation).
     *
     * @param ex the exception
     * @param request the web request
     * @return HTTP 400 Bad Request with error message
     */
    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ErrorResponse> handleIllegalState(
            IllegalStateException ex, WebRequest request) {

        log.warn("Illegal state: {}", ex.getMessage());

        ErrorResponse errorResponse =
                ErrorResponse.builder()
                        .timestamp(LocalDateTime.now())
                        .status(HttpStatus.BAD_REQUEST.value())
                        .error(HttpStatus.BAD_REQUEST.getReasonPhrase())
                        .message(ex.getMessage())
                        .path(getRequestPath(request))
                        .build();

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse);
    }

    /**
     * Handles Spring Security access denied exceptions (forbidden access).
     *
     * <p>Thrown when a user tries to access a resource or endpoint they don't have permission for,
     * typically from @PreAuthorize or other method security annotations.
     *
     * @param ex the exception
     * @param request the web request
     * @return HTTP 403 Forbidden with error message
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDeniedException(
            AccessDeniedException ex, WebRequest request) {

        log.warn("Access denied: {}", ex.getMessage());

        String message =
                messageSource.getMessage(
                        "error.access.denied",
                        null,
                        "Access denied. You do not have permission to perform this operation.",
                        LocaleContextHolder.getLocale());

        ErrorResponse errorResponse =
                ErrorResponse.builder()
                        .timestamp(LocalDateTime.now())
                        .status(HttpStatus.FORBIDDEN.value())
                        .error(HttpStatus.FORBIDDEN.getReasonPhrase())
                        .message(message)
                        .path(getRequestPath(request))
                        .build();

        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(errorResponse);
    }

    /**
     * Handles security exceptions (forbidden access).
     *
     * @param ex the exception
     * @param request the web request
     * @return HTTP 403 Forbidden with error message
     */
    @ExceptionHandler(SecurityException.class)
    public ResponseEntity<ErrorResponse> handleSecurityException(
            SecurityException ex, WebRequest request) {

        log.warn("Security exception: {}", ex.getMessage());

        ErrorResponse errorResponse =
                ErrorResponse.builder()
                        .timestamp(LocalDateTime.now())
                        .status(HttpStatus.FORBIDDEN.value())
                        .error(HttpStatus.FORBIDDEN.getReasonPhrase())
                        .message(ex.getMessage())
                        .path(getRequestPath(request))
                        .build();

        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(errorResponse);
    }

    /**
     * Handles illegal argument exceptions (bad request parameters).
     *
     * @param ex the exception
     * @param request the web request
     * @return HTTP 400 Bad Request with error message
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgument(
            IllegalArgumentException ex, WebRequest request) {

        log.warn("Illegal argument: {}", ex.getMessage());

        ErrorResponse errorResponse =
                ErrorResponse.builder()
                        .timestamp(LocalDateTime.now())
                        .status(HttpStatus.BAD_REQUEST.value())
                        .error(HttpStatus.BAD_REQUEST.getReasonPhrase())
                        .message(ex.getMessage())
                        .path(getRequestPath(request))
                        .build();

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse);
    }

    /**
     * Handles all other uncaught exceptions (fallback handler).
     *
     * @param ex the exception
     * @param request the web request
     * @return HTTP 500 Internal Server Error with generic error message
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGlobalException(Exception ex, WebRequest request) {

        log.error("Unexpected error occurred: {}", ex.getMessage(), ex);

        String unexpectedMsg =
                messageSource.getMessage("error.unexpected", null, LocaleContextHolder.getLocale());
        ErrorResponse errorResponse =
                ErrorResponse.builder()
                        .timestamp(LocalDateTime.now())
                        .status(HttpStatus.INTERNAL_SERVER_ERROR.value())
                        .error(HttpStatus.INTERNAL_SERVER_ERROR.getReasonPhrase())
                        .message(unexpectedMsg)
                        .path(getRequestPath(request))
                        .build();

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
    }

    /**
     * Handles calculation validation exceptions.
     *
     * <p>Thrown when user-provided input values fail validation checks, such as negative savings,
     * invalid return rates, or missing required fields.
     *
     * @param ex the exception
     * @param request the web request
     * @return HTTP 400 Bad Request with error details
     */
    @ExceptionHandler(CalculationValidationException.class)
    public ResponseEntity<ErrorResponse> handleCalculationValidationException(
            CalculationValidationException ex, WebRequest request) {

        log.warn(
                "Calculation validation error: {} - Field: {}", ex.getMessage(), ex.getFieldName());

        Map<String, String> validationErrors = new HashMap<>();
        if (ex.getFieldName() != null) {
            validationErrors.put(ex.getFieldName(), ex.getMessage());
        }

        ErrorResponse errorResponse =
                ErrorResponse.builder()
                        .timestamp(LocalDateTime.now())
                        .status(HttpStatus.BAD_REQUEST.value())
                        .error(HttpStatus.BAD_REQUEST.getReasonPhrase())
                        .message(ex.getMessage())
                        .path(getRequestPath(request))
                        .validationErrors(validationErrors)
                        .build();

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse);
    }

    /**
     * Handles calculation overflow exceptions.
     *
     * <p>Thrown when calculation results exceed representable limits, typically with very large
     * savings amounts or long projection periods.
     *
     * @param ex the exception
     * @param request the web request
     * @return HTTP 400 Bad Request with error details
     */
    @ExceptionHandler(CalculationOverflowException.class)
    public ResponseEntity<ErrorResponse> handleCalculationOverflowException(
            CalculationOverflowException ex, WebRequest request) {

        log.warn("Calculation overflow: {} - Operation: {}", ex.getMessage(), ex.getOperation());

        ErrorResponse errorResponse =
                ErrorResponse.builder()
                        .timestamp(LocalDateTime.now())
                        .status(HttpStatus.BAD_REQUEST.value())
                        .error(HttpStatus.BAD_REQUEST.getReasonPhrase())
                        .message(ex.getMessage())
                        .path(getRequestPath(request))
                        .build();

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse);
    }

    /**
     * Handles generic calculation exceptions.
     *
     * <p>Catches all other calculation-related exceptions that don't have specific handlers. Maps
     * to HTTP 400 Bad Request by default.
     *
     * @param ex the exception
     * @param request the web request
     * @return HTTP 400 Bad Request with error details
     */
    @ExceptionHandler(CalculationException.class)
    public ResponseEntity<ErrorResponse> handleCalculationException(
            CalculationException ex, WebRequest request) {

        log.warn("Calculation error: {}", ex.getMessage());

        ErrorResponse errorResponse =
                ErrorResponse.builder()
                        .timestamp(LocalDateTime.now())
                        .status(HttpStatus.BAD_REQUEST.value())
                        .error(HttpStatus.BAD_REQUEST.getReasonPhrase())
                        .message(ex.getMessage())
                        .path(getRequestPath(request))
                        .build();

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse);
    }

    /**
     * Handles missing multipart file parameter exceptions.
     *
     * @param ex the exception
     * @param request the web request
     * @return HTTP 400 Bad Request with error message
     */
    @ExceptionHandler(
            org.springframework.web.multipart.support.MissingServletRequestPartException.class)
    public ResponseEntity<ErrorResponse> handleMissingServletRequestPart(
            org.springframework.web.multipart.support.MissingServletRequestPartException ex,
            WebRequest request) {

        log.warn("Missing file parameter: {}", ex.getMessage());

        String missingFileMsg =
                messageSource.getMessage(
                        "error.missing.file.parameter", null, LocaleContextHolder.getLocale());
        ErrorResponse errorResponse =
                ErrorResponse.builder()
                        .timestamp(LocalDateTime.now())
                        .status(HttpStatus.BAD_REQUEST.value())
                        .error(HttpStatus.BAD_REQUEST.getReasonPhrase())
                        .message(missingFileMsg)
                        .path(getRequestPath(request))
                        .build();

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse);
    }

    /**
     * Extracts the request path from the web request.
     *
     * @param request the web request
     * @return the request path or "unknown" if not available
     */
    private String getRequestPath(WebRequest request) {
        String description = request.getDescription(false);
        if (description != null && description.startsWith("uri=")) {
            return description.substring(4);
        }
        return "unknown";
    }

    /**
     * Resolves a localized message for exceptions that implement {@link LocalizableException}.
     * Falls back to {@code ex.getMessage()} when no key is available.
     *
     * @param ex the exception
     * @return the resolved message
     */
    private String resolveMessage(RuntimeException ex) {
        if (ex instanceof LocalizableException localizable && localizable.getMessageKey() != null) {
            return messageSource.getMessage(
                    localizable.getMessageKey(),
                    localizable.getMessageArgs(),
                    ex.getMessage(),
                    LocaleContextHolder.getLocale());
        }
        return ex.getMessage();
    }

    /** Standardized error response structure for API errors. */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ErrorResponse {
        /** Timestamp when the error occurred. */
        private LocalDateTime timestamp;

        /** HTTP status code. */
        private int status;

        /** HTTP status reason phrase (e.g., "Bad Request"). */
        private String error;

        /** Human-readable error message. */
        private String message;

        /** Request path where the error occurred. */
        private String path;

        /** Field-specific validation errors (only for validation failures). */
        private Map<String, String> validationErrors;
    }
}
