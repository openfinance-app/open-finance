package org.openfinance.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Exception thrown when an asset-linked operation is attempted on an asset whose state or type does
 * not allow it.
 *
 * <p>This exception is thrown when:
 *
 * <ul>
 *   <li>A capitalized improvement is applied to a non-physical asset (spec §3.3: improvements track
 *       the cost basis of physical assets only)
 * </ul>
 */
@ResponseStatus(HttpStatus.CONFLICT)
public class InvalidAssetStateException extends RuntimeException implements LocalizableException {

    private final String messageKey;
    private final Object[] messageArgs;

    private InvalidAssetStateException(String message, String messageKey, Object[] messageArgs) {
        super(message);
        this.messageKey = messageKey;
        this.messageArgs = messageArgs;
    }

    /**
     * Factory method for a capitalized improvement attempted on a non-physical asset.
     *
     * @param assetId the asset ID that is not physical
     * @param type the asset type that rejected the improvement
     * @return a new InvalidAssetStateException
     */
    public static InvalidAssetStateException improvementNotPhysical(Long assetId, String type) {
        return new InvalidAssetStateException(
                String.format(
                        "Cannot apply a capital improvement to asset %d: type %s is not a"
                                + " physical asset",
                        assetId, type),
                "error.asset.improvement.not.physical",
                new Object[] {assetId, type});
    }

    @Override
    public String getMessageKey() {
        return messageKey;
    }

    public static InvalidAssetStateException valuationBoundaryMissing(Long assetId) {
        return new InvalidAssetStateException(
                "Confirm the current valuation of asset "
                        + assetId
                        + " before reversing its older capital improvements",
                "error.asset.valuation.boundary.missing",
                new Object[] {assetId});
    }

    @Override
    public Object[] getMessageArgs() {
        return messageArgs;
    }
}
