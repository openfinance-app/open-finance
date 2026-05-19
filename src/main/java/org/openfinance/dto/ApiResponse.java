package org.openfinance.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Generic API response wrapper for consistent response format across all endpoints. Provides
 * standardized structure for success and error responses.
 *
 * <p>Example success response:
 *
 * <pre>
 * {
 *   "success": true,
 *   "message": "User created successfully",
 *   "data": { "id": 1, "username": "john" },
 *   "timestamp": "2024-01-30T10:30:00"
 * }
 * </pre>
 *
 * <p>Example error response:
 *
 * <pre>
 * {
 *   "success": false,
 *   "message": "User not found",
 *   "error": "RESOURCE_NOT_FOUND",
 *   "timestamp": "2024-01-30T10:30:00"
 * }
 * </pre>
 *
 * @param <T> the type of data in the response
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiResponse<T> {

    /** Indicates if the request was successful */
    private boolean success;

    /** Human-readable message describing the response */
    private String message;

    /** The response data payload (null for errors) */
    private T data;

    /** Error code for failed requests */
    private String error;

    /** Timestamp when the response was generated */
    private LocalDateTime timestamp;

    /**
     * Create a successful response with data
     *
     * @param data the response data
     * @param <T> the type of data
     * @return ApiResponse instance
     */
    public static <T> ApiResponse<T> success(T data) {
        return success(null, data);
    }

    /**
     * Create a successful response with message and data
     *
     * @param message the success message
     * @param data the response data
     * @param <T> the type of data
     * @return ApiResponse instance
     */
    public static <T> ApiResponse<T> success(String message, T data) {
        ApiResponse<T> response = new ApiResponse<>();
        response.setSuccess(true);
        response.setMessage(message);
        response.setData(data);
        response.setTimestamp(LocalDateTime.now());
        return response;
    }

    /**
     * Create a successful response with only a message
     *
     * @param message the success message
     * @param <T> the type of data
     * @return ApiResponse instance
     */
    public static <T> ApiResponse<T> success(String message) {
        return success(message, null);
    }

    /**
     * Create an error response
     *
     * @param message the error message
     * @param <T> the type of data
     * @return ApiResponse instance
     */
    public static <T> ApiResponse<T> error(String message) {
        return error(message, null);
    }

    /**
     * Create an error response with error code
     *
     * @param message the error message
     * @param errorCode the error code
     * @param <T> the type of data
     * @return ApiResponse instance
     */
    public static <T> ApiResponse<T> error(String message, String errorCode) {
        ApiResponse<T> response = new ApiResponse<>();
        response.setSuccess(false);
        response.setMessage(message);
        response.setError(errorCode);
        response.setTimestamp(LocalDateTime.now());
        return response;
    }

    /**
     * Create a validation error response
     *
     * @param message the validation error message
     * @param <T> the type of data
     * @return ApiResponse instance
     */
    public static <T> ApiResponse<T> validationError(String message) {
        return error(message, "VALIDATION_ERROR");
    }

    /**
     * Create a not found error response
     *
     * @param message the not found message
     * @param <T> the type of data
     * @return ApiResponse instance
     */
    public static <T> ApiResponse<T> notFound(String message) {
        return error(message, "RESOURCE_NOT_FOUND");
    }

    /**
     * Create an unauthorized error response
     *
     * @param message the unauthorized message
     * @param <T> the type of data
     * @return ApiResponse instance
     */
    public static <T> ApiResponse<T> unauthorized(String message) {
        return error(message, "UNAUTHORIZED");
    }

    /**
     * Create a forbidden error response
     *
     * @param message the forbidden message
     * @param <T> the type of data
     * @return ApiResponse instance
     */
    public static <T> ApiResponse<T> forbidden(String message) {
        return error(message, "FORBIDDEN");
    }

    /**
     * Create an internal server error response
     *
     * @param <T> the type of data
     * @return ApiResponse instance
     */
    public static <T> ApiResponse<T> internalError() {
        return error("An internal server error occurred", "INTERNAL_SERVER_ERROR");
    }
}
