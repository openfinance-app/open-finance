package org.openfinance.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Exception thrown when a real estate property is not found in the database.
 *
 * <p>This exception is typically thrown when attempting to retrieve, update, or delete a property
 * by ID that doesn't exist or doesn't belong to the user.
 *
 * <p>Requirement REQ-3.2: Authorization - Users can only access their own properties
 */
@ResponseStatus(HttpStatus.NOT_FOUND)
public class RealEstatePropertyNotFoundException extends RuntimeException {

    /**
     * Constructs a new RealEstatePropertyNotFoundException with a detail message.
     *
     * @param message the detail message
     */
    public RealEstatePropertyNotFoundException(String message) {
        super(message);
    }

    /**
     * Constructs a new RealEstatePropertyNotFoundException with a detail message and cause.
     *
     * @param message the detail message
     * @param cause the cause of the exception
     */
    public RealEstatePropertyNotFoundException(String message, Throwable cause) {
        super(message, cause);
    }

    /**
     * Creates an exception for a property not found by ID.
     *
     * @param propertyId the property ID that was not found
     * @return a new RealEstatePropertyNotFoundException
     */
    public static RealEstatePropertyNotFoundException byId(Long propertyId) {
        return new RealEstatePropertyNotFoundException(
                "Real estate property not found with id: " + propertyId);
    }

    /**
     * Creates an exception for a property not found for a specific user.
     *
     * @param propertyId the property ID
     * @param userId the user ID
     * @return a new RealEstatePropertyNotFoundException
     */
    public static RealEstatePropertyNotFoundException byIdAndUser(Long propertyId, Long userId) {
        return new RealEstatePropertyNotFoundException(
                String.format(
                        "Real estate property not found with id: %d for user: %d",
                        propertyId, userId));
    }
}
