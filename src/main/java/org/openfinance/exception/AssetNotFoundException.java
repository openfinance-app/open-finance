package org.openfinance.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Exception thrown when an asset is not found in the database.
 *
 * <p>This exception is typically thrown when attempting to retrieve, update, or delete an asset by
 * ID that doesn't exist or doesn't belong to the user.
 *
 * <p>Requirement REQ-3.2: Authorization - Users can only access their own assets
 */
@ResponseStatus(HttpStatus.NOT_FOUND)
public class AssetNotFoundException extends RuntimeException {

    /**
     * Constructs a new AssetNotFoundException with a detail message.
     *
     * @param message the detail message
     */
    public AssetNotFoundException(String message) {
        super(message);
    }

    /**
     * Constructs a new AssetNotFoundException with a detail message and cause.
     *
     * @param message the detail message
     * @param cause the cause of the exception
     */
    public AssetNotFoundException(String message, Throwable cause) {
        super(message, cause);
    }

    /**
     * Creates an exception for an asset not found by ID.
     *
     * @param assetId the asset ID that was not found
     * @return a new AssetNotFoundException
     */
    public static AssetNotFoundException byId(Long assetId) {
        return new AssetNotFoundException("Asset not found with id: " + assetId);
    }

    /**
     * Creates an exception for an asset not found for a specific user.
     *
     * @param assetId the asset ID
     * @param userId the user ID
     * @return a new AssetNotFoundException
     */
    public static AssetNotFoundException byIdAndUser(Long assetId, Long userId) {
        return new AssetNotFoundException(
                String.format("Asset not found with id: %d for user: %d", assetId, userId));
    }
}
