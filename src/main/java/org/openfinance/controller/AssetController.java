package org.openfinance.controller;

import jakarta.validation.Valid;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.openfinance.dto.AssetRequest;
import org.openfinance.dto.AssetResponse;
import org.openfinance.dto.AssetSearchCriteria;
import org.openfinance.dto.AssetSummaryResponse;
import org.openfinance.entity.AssetType;
import org.openfinance.entity.User;
import org.openfinance.service.AssetService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for asset management endpoints.
 *
 * <p>Provides CRUD operations for investment assets (stocks, ETFs, crypto, bonds, etc.). All
 * endpoints require authentication and use the user's encryption key to secure sensitive data.
 *
 * <p><strong>Endpoints:</strong>
 *
 * <ul>
 *   <li>POST /api/v1/assets - Create new asset
 *   <li>GET /api/v1/assets - List all user assets (with optional filters)
 *   <li>GET /api/v1/assets/{id} - Get asset by ID
 *   <li>PUT /api/v1/assets/{id} - Update asset
 *   <li>DELETE /api/v1/assets/{id} - Delete asset
 * </ul>
 *
 * <p><strong>Security:</strong>
 *
 * <ul>
 *   <li>All endpoints require JWT authentication
 *   <li>Encryption key must be provided via X-Encryption-Session header
 *   <li>Users can only access their own assets
 *   <li>Asset name and notes are encrypted at rest
 * </ul>
 *
 * <p>Requirement REQ-2.6: Asset Management - CRUD operations
 *
 * <p>Requirement REQ-2.6.3: Display portfolio values and gains/losses
 *
 * <p>Requirement REQ-2.18: Data encryption at rest
 *
 * <p>Requirement REQ-3.2: Authorization checks
 *
 * @see AssetService
 * @see AssetRequest
 * @see AssetResponse
 */
@RestController
@RequestMapping("/api/v1/assets")
@RequiredArgsConstructor
@Slf4j
public class AssetController {
    private final AssetService assetService;

    /**
     * Creates a new investment asset for the authenticated user.
     *
     * <p><strong>Request Headers:</strong>
     *
     * <ul>
     *   <li>Authorization: Bearer {jwt_token}
     *   <li>X-Encryption-Session: {base64_encoded_key}
     * </ul>
     *
     * <p><strong>Request Body:</strong>
     *
     * <pre>{@code
     * {
     * "accountId": 1,
     * "name": "Apple Inc.",
     * "type": "STOCK",
     * "symbol": "AAPL",
     * "quantity": 10.0,
     * "purchasePrice": 150.00,
     * "currentPrice": 175.00,
     * "currency": "USD",
     * "purchaseDate": "2025-01-15",
     * "notes": "Tech portfolio allocation"
     * }
     * }</pre>
     *
     * <p><strong>Success Response (HTTP 201 Created):</strong>
     *
     * <pre>{@code
     * {
     * "id": 1,
     * "userId": 1,
     * "accountId": 1,
     * "accountName": "Fidelity Brokerage",
     * "name": "Apple Inc.",
     * "type": "STOCK",
     * "symbol": "AAPL",
     * "quantity": 10.0,
     * "purchasePrice": 150.00,
     * "currentPrice": 175.00,
     * "currency": "USD",
     * "purchaseDate": "2025-01-15",
     * "notes": "Tech portfolio allocation",
     * "lastUpdated": "2026-02-01T14:30:00",
     * "createdAt": "2026-02-01T14:30:00",
     * "updatedAt": null,
     * "totalValue": 1750.00,
     * "totalCost": 1500.00,
     * "unrealizedGain": 250.00,
     * "gainPercentage": 0.1667,
     * "holdingDays": 17
     * }
     * }</pre>
     *
     * <p>Requirement REQ-2.6.1: Create asset
     *
     * <p>Requirement REQ-2.6.3: Display calculated fields (value, gains)
     *
     * @param request asset creation request
     * @param encodedKey Base64-encoded encryption key from header
     * @param authentication Spring Security authentication object
     * @return HTTP 201 Created with AssetResponse
     */
    @PostMapping
    public ResponseEntity<AssetResponse> createAsset(
            @Valid @RequestBody AssetRequest request, Authentication authentication) {

        log.info(
                "Creating asset for user: type={}, symbol={}",
                request.getType(),
                request.getSymbol());
        User user = (User) authentication.getPrincipal();
        AssetResponse response = assetService.createAsset(user.getId(), request);
        log.info(
                "Asset created successfully: id={}, symbol={}",
                response.getId(),
                response.getSymbol());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Retrieves all assets for the authenticated user with optional filters.
     *
     * <p><strong>Request Headers:</strong>
     *
     * <ul>
     *   <li>Authorization: Bearer {jwt_token}
     *   <li>X-Encryption-Session: {base64_encoded_key}
     * </ul>
     *
     * <p><strong>Query Parameters (all optional):</strong>
     *
     * <ul>
     *   <li>accountId - Filter by account ID
     *   <li>type - Filter by asset type (STOCK, ETF, CRYPTO, etc.)
     *   <li>summary - when {@code true}, returns a lightweight {@link AssetSummaryResponse} list
     *       instead of the full {@link AssetResponse} (default: false)
     * </ul>
     *
     * <p>Requirement REQ-2.6.1: List user assets
     *
     * <p>Requirement TASK-14.1.3: Sparse fieldsets via {@code ?summary=true}
     *
     * @param accountId optional account ID filter
     * @param type optional asset type filter
     * @param summary when {@code true} returns lightweight AssetSummaryResponse list
     * @param encodedKey Base64-encoded encryption key from header
     * @param authentication Spring Security authentication object
     * @return HTTP 200 OK with list of AssetResponse or AssetSummaryResponse (may be empty)
     */
    @GetMapping
    public ResponseEntity<?> getAllAssets(
            @RequestParam(value = "accountId", required = false) Long accountId,
            @RequestParam(value = "type", required = false) AssetType type,
            @RequestParam(value = "summary", required = false, defaultValue = "false")
                    boolean summary,
            Authentication authentication) {

        log.info(
                "Retrieving assets for user: accountId={}, type={}, summary={}",
                accountId,
                type,
                summary);
        User user = (User) authentication.getPrincipal();
        if (summary) {
            // Return lightweight summary projection (TASK-14.1.3)
            List<AssetSummaryResponse> summaries = assetService.getAssetsSummary(user.getId());
            log.info("Retrieved {} asset summaries for user", summaries.size());
            return ResponseEntity.ok(summaries);
        }

        List<AssetResponse> assets;

        if (accountId != null) {
            // Filter by account
            assets = assetService.getAssetsByAccountId(accountId, user.getId());
            log.info(
                    "Retrieved {} assets for user (filtered by account={})",
                    assets.size(),
                    accountId);
        } else if (type != null) {
            // Filter by type
            assets = assetService.getAssetsByType(user.getId(), type);
            log.info("Retrieved {} assets for user (filtered by type={})", assets.size(), type);
        } else {
            // No filters - get all assets
            assets = assetService.getAssetsByUserId(user.getId());
            log.info("Retrieved {} assets for user (no filters)", assets.size());
        }

        return ResponseEntity.ok(assets);
    }

    /**
     * Searches assets with filters and pagination.
     *
     * <p><strong>Request Headers:</strong>
     *
     * <ul>
     *   <li>Authorization: Bearer {jwt_token}
     *   <li>X-Encryption-Session: {base64_encoded_key}
     * </ul>
     *
     * <p><strong>Query Parameters:</strong>
     *
     * <ul>
     *   <li>keyword (optional): Search in asset name
     *   <li>type (optional): Filter by asset type (STOCK, ETF, CRYPTO, etc.)
     *   <li>accountId (optional): Filter by account ID
     *   <li>currency (optional): Filter by currency code (USD, EUR, etc.)
     *   <li>symbol (optional): Filter by ticker symbol
     *   <li>purchaseDateFrom (optional): Filter by purchase date >= this date
     *   <li>purchaseDateTo (optional): Filter by purchase date <= this date
     *   <li>valueMin (optional): Minimum total value
     *   <li>valueMax (optional): Maximum total value
     *   <li>page (optional): Page number (0-indexed, default: 0)
     *   <li>size (optional): Page size (default: 20)
     *   <li>sort (optional): Sort field and direction (e.g., "name,asc" or "currentPrice,desc")
     * </ul>
     *
     * <p><strong>Success Response (HTTP 200 OK):</strong>
     *
     * <pre>{@code
     * {
     * "content": [
     * {
     * "id": 1,
     * "name": "Apple Inc.",
     * "type": "STOCK",
     * "symbol": "AAPL",
     * "quantity": 10.0,
     * "currentPrice": 175.00,
     * "totalValue": 1750.00,
     * ...
     * }
     * ],
     * "totalElements": 10,
     * "totalPages": 1,
     * "number": 0,
     * "size": 20
     * }
     * }</pre>
     *
     * @param keyword optional keyword to search in asset name
     * @param type optional asset type filter
     * @param accountId optional account ID filter
     * @param currency optional currency filter
     * @param symbol optional symbol filter
     * @param purchaseDateFrom optional purchase date from filter
     * @param purchaseDateTo optional purchase date to filter
     * @param valueMin optional minimum value filter
     * @param valueMax optional maximum value filter
     * @param pageable pagination and sorting parameters
     * @param encodedKey Base64-encoded encryption key from header
     * @param authentication Spring Security authentication object
     * @return HTTP 200 OK with Page<AssetResponse>
     */
    @GetMapping("/search")
    public ResponseEntity<Page<AssetResponse>> searchAssets(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) AssetType type,
            @RequestParam(required = false) Long accountId,
            @RequestParam(required = false) String currency,
            @RequestParam(required = false) String symbol,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                    LocalDate purchaseDateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                    LocalDate purchaseDateTo,
            @RequestParam(required = false) BigDecimal valueMin,
            @RequestParam(required = false) BigDecimal valueMax,
            @PageableDefault(size = 20, sort = "name", direction = Sort.Direction.ASC)
                    Pageable pageable,
            Authentication authentication) {

        log.info(
                "Searching assets for user: keyword={}, type={}, accountId={}, page={}, size={}",
                keyword,
                type,
                accountId,
                pageable.getPageNumber(),
                pageable.getPageSize());
        User user = (User) authentication.getPrincipal();
        // Build search criteria
        AssetSearchCriteria criteria =
                AssetSearchCriteria.builder()
                        .keyword(keyword)
                        .type(type)
                        .accountId(accountId)
                        .currency(currency)
                        .symbol(symbol)
                        .purchaseDateFrom(purchaseDateFrom)
                        .purchaseDateTo(purchaseDateTo)
                        .valueMin(valueMin)
                        .valueMax(valueMax)
                        .build();

        // Execute search with pagination
        Page<AssetResponse> results = assetService.searchAssets(user.getId(), criteria, pageable);

        log.info(
                "Search returned {} assets (page {}/{}, total: {})",
                results.getNumberOfElements(),
                results.getNumber() + 1,
                results.getTotalPages(),
                results.getTotalElements());

        return ResponseEntity.ok(results);
    }

    /**
     * Retrieves a specific asset by ID.
     *
     * <p><strong>Request Headers:</strong>
     *
     * <ul>
     *   <li>Authorization: Bearer {jwt_token}
     *   <li>X-Encryption-Session: {base64_encoded_key}
     * </ul>
     *
     * <p><strong>Success Response:</strong> Same as create asset response
     *
     * <p><strong>Error Responses:</strong>
     *
     * <ul>
     *   <li>HTTP 404 Not Found - Asset not found or doesn't belong to user
     * </ul>
     *
     * <p>Requirement REQ-2.6.1: Get asset by ID
     *
     * <p>Requirement REQ-3.2: Authorization - verify ownership
     *
     * @param assetId the asset ID
     * @param encodedKey Base64-encoded encryption key from header
     * @param authentication Spring Security authentication object
     * @return HTTP 200 OK with AssetResponse
     */
    @GetMapping("/{id}")
    public ResponseEntity<AssetResponse> getAssetById(
            @PathVariable("id") Long assetId, Authentication authentication) {

        log.info("Retrieving asset: id={}", assetId);
        User user = (User) authentication.getPrincipal();
        AssetResponse response = assetService.getAssetById(assetId, user.getId());

        log.info("Asset retrieved successfully: id={}, symbol={}", assetId, response.getSymbol());

        return ResponseEntity.ok(response);
    }

    /**
     * Updates an existing asset.
     *
     * <p><strong>Request Headers:</strong>
     *
     * <ul>
     *   <li>Authorization: Bearer {jwt_token}
     *   <li>X-Encryption-Session: {base64_encoded_key}
     * </ul>
     *
     * <p><strong>Request Body:</strong> Same as create asset request
     *
     * <p><strong>Success Response:</strong> Same as create asset response with updated values
     *
     * <p><strong>Error Responses:</strong>
     *
     * <ul>
     *   <li>HTTP 400 Bad Request - Validation errors
     *   <li>HTTP 404 Not Found - Asset not found or doesn't belong to user
     * </ul>
     *
     * <p><strong>Note:</strong> If currentPrice is updated, the lastUpdated timestamp is
     * automatically set to the current time.
     *
     * <p>Requirement REQ-2.6.2: Update asset
     *
     * <p>Requirement REQ-2.6.4: Update current price with timestamp
     *
     * <p>Requirement REQ-3.2: Authorization - verify ownership
     *
     * @param assetId the asset ID
     * @param request asset update request
     * @param encodedKey Base64-encoded encryption key from header
     * @param authentication Spring Security authentication object
     * @return HTTP 200 OK with updated AssetResponse
     */
    @PutMapping("/{id}")
    public ResponseEntity<AssetResponse> updateAsset(
            @PathVariable("id") Long assetId,
            @Valid @RequestBody AssetRequest request,
            Authentication authentication) {

        log.info("Updating asset: id={}", assetId);
        User user = (User) authentication.getPrincipal();
        AssetResponse response = assetService.updateAsset(assetId, user.getId(), request);

        log.info("Asset updated successfully: id={}, symbol={}", assetId, response.getSymbol());

        return ResponseEntity.ok(response);
    }

    /**
     * Deletes an asset (hard delete).
     *
     * <p>Permanently removes the asset from the database. This operation cannot be undone.
     *
     * <p><strong>Request Headers:</strong>
     *
     * <ul>
     *   <li>Authorization: Bearer {jwt_token}
     * </ul>
     *
     * <p><strong>Error Responses:</strong>
     *
     * <ul>
     *   <li>HTTP 404 Not Found - Asset not found or doesn't belong to user
     * </ul>
     *
     * <p>Requirement REQ-2.6.2: Delete asset
     *
     * <p>Requirement REQ-3.2: Authorization - verify ownership
     *
     * @param assetId the asset ID
     * @param authentication Spring Security authentication object
     * @return HTTP 204 No Content on success
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteAsset(
            @PathVariable("id") Long assetId, Authentication authentication) {

        log.info("Deleting asset: id={}", assetId);
        User user = (User) authentication.getPrincipal();
        assetService.deleteAsset(assetId, user.getId());

        log.info("Asset deleted successfully: id={}", assetId);

        return ResponseEntity.noContent().build();
    }
}
