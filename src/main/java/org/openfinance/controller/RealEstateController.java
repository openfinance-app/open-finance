package org.openfinance.controller;

import jakarta.validation.Valid;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import javax.crypto.SecretKey;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.openfinance.dto.*;
import org.openfinance.entity.PropertyType;
import org.openfinance.service.RealEstateService;
import org.openfinance.util.ControllerUtil;
import org.openfinance.util.EncryptionUtil;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

/**
 * REST controller for real estate property management endpoints.
 *
 * <p>Provides CRUD operations for real estate properties (residential, commercial, land, etc.). All
 * endpoints require authentication and use the user's encryption key to secure sensitive data.
 *
 * <p><strong>Endpoints:</strong>
 *
 * <ul>
 *   <li>POST /api/v1/real-estate - Create new property
 *   <li>GET /api/v1/real-estate - List all user properties (with optional filters)
 *   <li>GET /api/v1/real-estate/{id} - Get property by ID
 *   <li>PUT /api/v1/real-estate/{id} - Update property
 *   <li>DELETE /api/v1/real-estate/{id} - Delete property (soft delete)
 *   <li>GET /api/v1/real-estate/{id}/equity - Calculate property equity
 *   <li>GET /api/v1/real-estate/{id}/roi - Calculate property ROI
 *   <li>PUT /api/v1/real-estate/{id}/value - Update property value estimate
 * </ul>
 *
 * <p><strong>Security:</strong>
 *
 * <ul>
 *   <li>All endpoints require JWT authentication
 *   <li>Encryption key must be provided via X-Encryption-Key header
 *   <li>Users can only access their own properties
 *   <li>Sensitive fields are encrypted at rest (name, address, prices, notes)
 * </ul>
 *
 * <p>Requirement REQ-2.16: Real Estate & Physical Assets - CRUD operations
 *
 * <p>Requirement REQ-2.16.1: Track property details
 *
 * <p>Requirement REQ-2.16.2: Calculate equity and ROI
 *
 * <p>Requirement REQ-2.18: Data encryption at rest
 *
 * <p>Requirement REQ-3.2: Authorization checks
 *
 * @see RealEstateService
 * @see RealEstatePropertyRequest
 * @see RealEstatePropertyResponse
 */
@RestController
@RequestMapping("/api/v1/real-estate")
@RequiredArgsConstructor
@Slf4j
public class RealEstateController {

    private static final String ENCRYPTION_KEY_HEADER = "X-Encryption-Key";

    private final RealEstateService realEstateService;

    /**
     * Creates a new real estate property for the authenticated user.
     *
     * <p><strong>Request Headers:</strong>
     *
     * <ul>
     *   <li>Authorization: Bearer {jwt_token}
     *   <li>X-Encryption-Key: {base64_encoded_key}
     * </ul>
     *
     * <p><strong>Request Body:</strong>
     *
     * <pre>{@code
     * {
     * "name": "Main Residence",
     * "propertyType": "RESIDENTIAL",
     * "address": "123 Main St, San Francisco, CA 94102",
     * "purchasePrice": 850000.00,
     * "currentValue": 1200000.00,
     * "purchaseDate": "2020-03-15",
     * "currency": "USD",
     * "mortgageId": 1,
     * "rentalIncome": null,
     * "latitude": 37.7749,
     * "longitude": -122.4194,
     * "isActive": true,
     * "notes": "Primary residence"
     * }
     * }</pre>
     *
     * <p><strong>Success Response (HTTP 201 Created):</strong>
     *
     * <pre>{@code
     * {
     * "id": 1,
     * "userId": 1,
     * "name": "Main Residence",
     * "propertyType": "RESIDENTIAL",
     * "address": "123 Main St, San Francisco, CA 94102",
     * "purchasePrice": 850000.00,
     * "currentValue": 1200000.00,
     * "purchaseDate": "2020-03-15",
     * "currency": "USD",
     * "mortgageId": 1,
     * "mortgageName": "Home Mortgage",
     * "rentalIncome": null,
     * "latitude": 37.7749,
     * "longitude": -122.4194,
     * "isActive": true,
     * "notes": "Primary residence",
     * "documents": null,
     * "createdAt": "2026-02-02T10:00:00",
     * "updatedAt": null,
     * "appreciation": 350000.00,
     * "appreciationPercentage": 41.18,
     * "rentalYield": null,
     * "equity": 800000.00,
     * "equityPercentage": 66.67,
     * "mortgageBalance": 400000.00,
     * "yearsOwned": 6,
     * "roi": 41.18
     * }
     * }</pre>
     *
     * <p>Requirement REQ-2.16.1: Create property
     *
     * <p>Requirement REQ-2.16.2: Calculate equity and appreciation
     *
     * @param request property creation request
     * @param encodedKey Base64-encoded encryption key from header
     * @param authentication Spring Security authentication object
     * @return HTTP 201 Created with RealEstatePropertyResponse
     */
    @PostMapping
    public ResponseEntity<RealEstatePropertyResponse> createProperty(
            @Valid @RequestBody RealEstatePropertyRequest request,
            @RequestHeader(value = ENCRYPTION_KEY_HEADER, required = false) String encodedKey,
            Authentication authentication) {

        log.info(
                "Creating property for user: type={}, currency={}",
                request.getPropertyType(),
                request.getCurrency());

        if (encodedKey == null || encodedKey.trim().isEmpty()) {
            throw new IllegalArgumentException("Encryption key header is required");
        }

        Long userId = ControllerUtil.extractUserId(authentication);
        SecretKey encryptionKey = EncryptionUtil.decodeEncryptionKey(encodedKey);

        RealEstatePropertyResponse response =
                realEstateService.createProperty(userId, request, encryptionKey);

        log.info(
                "Property created successfully: id={}, type={}",
                response.getId(),
                response.getPropertyType());

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Retrieves all properties for the authenticated user with optional filters.
     *
     * <p><strong>Request Headers:</strong>
     *
     * <ul>
     *   <li>Authorization: Bearer {jwt_token}
     *   <li>X-Encryption-Key: {base64_encoded_key}
     * </ul>
     *
     * <p><strong>Query Parameters:</strong>
     *
     * <ul>
     *   <li>propertyType (optional) - Filter by property type (RESIDENTIAL, COMMERCIAL, LAND, etc.)
     *   <li>includeInactive (optional, default: false) - Include soft-deleted properties
     * </ul>
     *
     * <p><strong>Examples:</strong>
     *
     * <ul>
     *   <li>GET /api/v1/real-estate - All active properties
     *   <li>GET /api/v1/real-estate?propertyType=RESIDENTIAL - Active residential properties only
     *   <li>GET /api/v1/real-estate?includeInactive=true - All properties including inactive
     * </ul>
     *
     * <p><strong>Success Response (HTTP 200 OK):</strong>
     *
     * <pre>{@code
     * [
     * {
     * "id": 1,
     * "name": "Main Residence",
     * "propertyType": "RESIDENTIAL",
     * // ... full property response
     * },
     * {
     * "id": 2,
     * "name": "Rental Condo",
     * "propertyType": "RESIDENTIAL",
     * // ... full property response
     * }
     * ]
     * }</pre>
     *
     * <p>Requirement REQ-2.16.1: List all properties with filtering
     *
     * @param propertyType optional property type filter
     * @param includeInactive whether to include inactive properties
     * @param encodedKey Base64-encoded encryption key from header
     * @param authentication Spring Security authentication object
     * @return HTTP 200 OK with list of RealEstatePropertyResponse
     */
    @GetMapping
    public ResponseEntity<List<RealEstatePropertyResponse>> getProperties(
            @RequestParam(required = false) PropertyType propertyType,
            @RequestParam(required = false, defaultValue = "false") boolean includeInactive,
            @RequestHeader(value = ENCRYPTION_KEY_HEADER, required = false) String encodedKey,
            Authentication authentication) {

        log.info(
                "Retrieving properties: propertyType={}, includeInactive={}",
                propertyType,
                includeInactive);

        if (encodedKey == null || encodedKey.trim().isEmpty()) {
            throw new IllegalArgumentException("Encryption key header is required");
        }

        Long userId = ControllerUtil.extractUserId(authentication);
        SecretKey encryptionKey = EncryptionUtil.decodeEncryptionKey(encodedKey);

        List<RealEstatePropertyResponse> properties =
                realEstateService.getPropertiesByUserId(
                        userId, propertyType, includeInactive, encryptionKey);

        log.info("Retrieved {} properties", properties.size());

        return ResponseEntity.ok(properties);
    }

    /**
     * Searches properties with filters and pagination.
     *
     * <p><strong>Request Headers:</strong>
     *
     * <ul>
     *   <li>Authorization: Bearer {jwt_token}
     *   <li>X-Encryption-Key: {base64_encoded_key}
     * </ul>
     *
     * <p><strong>Query Parameters:</strong>
     *
     * <ul>
     *   <li>keyword (optional): Search in property name or address
     *   <li>propertyType (optional): Filter by property type (RESIDENTIAL, COMMERCIAL, LAND, etc.)
     *   <li>currency (optional): Filter by currency code (USD, EUR, etc.)
     *   <li>isActive (optional): Filter by active status (true/false)
     *   <li>hasMortgage (optional): Filter by mortgage presence (true/false)
     *   <li>purchaseDateFrom (optional): Filter by purchase date >= this date
     *   <li>purchaseDateTo (optional): Filter by purchase date <= this date
     *   <li>valueMin (optional): Minimum current value
     *   <li>valueMax (optional): Maximum current value
     *   <li>priceMin (optional): Minimum purchase price
     *   <li>priceMax (optional): Maximum purchase price
     *   <li>rentalIncomeMin (optional): Minimum rental income
     *   <li>page (optional): Page number (0-indexed, default: 0)
     *   <li>size (optional): Page size (default: 20)
     *   <li>sort (optional): Sort field and direction (e.g., "name,asc" or "currentValue,desc")
     * </ul>
     *
     * <p><strong>Success Response (HTTP 200 OK):</strong>
     *
     * <pre>{@code
     * {
     * "content": [
     * {
     * "id": 1,
     * "name": "Main Residence",
     * "propertyType": "RESIDENTIAL",
     * "currentValue": 1200000.00,
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
     * @param keyword optional keyword to search in name or address
     * @param propertyType optional property type filter
     * @param currency optional currency filter
     * @param isActive optional active status filter
     * @param hasMortgage optional mortgage presence filter
     * @param purchaseDateFrom optional purchase date from filter
     * @param purchaseDateTo optional purchase date to filter
     * @param valueMin optional minimum value filter
     * @param valueMax optional maximum value filter
     * @param priceMin optional minimum price filter
     * @param priceMax optional maximum price filter
     * @param rentalIncomeMin optional minimum rental income filter
     * @param pageable pagination and sorting parameters
     * @param encodedKey Base64-encoded encryption key from header
     * @param authentication Spring Security authentication object
     * @return HTTP 200 OK with Page<RealEstatePropertyResponse>
     */
    @GetMapping("/search")
    public ResponseEntity<Page<RealEstatePropertyResponse>> searchProperties(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) PropertyType propertyType,
            @RequestParam(required = false) String currency,
            @RequestParam(required = false) Boolean isActive,
            @RequestParam(required = false) Boolean hasMortgage,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                    LocalDate purchaseDateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                    LocalDate purchaseDateTo,
            @RequestParam(required = false) BigDecimal valueMin,
            @RequestParam(required = false) BigDecimal valueMax,
            @RequestParam(required = false) BigDecimal priceMin,
            @RequestParam(required = false) BigDecimal priceMax,
            @RequestParam(required = false) BigDecimal rentalIncomeMin,
            @PageableDefault(size = 20, sort = "name", direction = Sort.Direction.ASC)
                    Pageable pageable,
            @RequestHeader(value = ENCRYPTION_KEY_HEADER, required = false) String encodedKey,
            Authentication authentication) {

        log.info(
                "Searching properties for user: keyword={}, propertyType={}, page={}, size={}",
                keyword,
                propertyType,
                pageable.getPageNumber(),
                pageable.getPageSize());

        if (encodedKey == null || encodedKey.trim().isEmpty()) {
            throw new IllegalArgumentException("Encryption key header is required");
        }

        Long userId = ControllerUtil.extractUserId(authentication);
        SecretKey encryptionKey = EncryptionUtil.decodeEncryptionKey(encodedKey);

        // Build search criteria
        RealEstateSearchCriteria criteria =
                RealEstateSearchCriteria.builder()
                        .keyword(keyword)
                        .propertyType(propertyType)
                        .currency(currency)
                        .isActive(isActive)
                        .hasMortgage(hasMortgage)
                        .purchaseDateFrom(purchaseDateFrom)
                        .purchaseDateTo(purchaseDateTo)
                        .valueMin(valueMin)
                        .valueMax(valueMax)
                        .priceMin(priceMin)
                        .priceMax(priceMax)
                        .rentalIncomeMin(rentalIncomeMin)
                        .build();

        // Execute search with pagination
        Page<RealEstatePropertyResponse> results =
                realEstateService.searchProperties(userId, criteria, pageable, encryptionKey);

        log.info(
                "Search returned {} properties (page {}/{}, total: {})",
                results.getNumberOfElements(),
                results.getNumber() + 1,
                results.getTotalPages(),
                results.getTotalElements());

        return ResponseEntity.ok(results);
    }

    /**
     * Retrieves a single property by ID.
     *
     * <p><strong>Request Headers:</strong>
     *
     * <ul>
     *   <li>Authorization: Bearer {jwt_token}
     *   <li>X-Encryption-Key: {base64_encoded_key}
     * </ul>
     *
     * <p><strong>Success Response:</strong> Same as create property response
     *
     * <p><strong>Error Responses:</strong>
     *
     * <ul>
     *   <li>HTTP 404 Not Found - Property not found or doesn't belong to user
     * </ul>
     *
     * <p>Requirement REQ-2.16.1: Get property by ID
     *
     * <p>Requirement REQ-3.2: Authorization - verify ownership
     *
     * @param propertyId the property ID
     * @param encodedKey Base64-encoded encryption key from header
     * @param authentication Spring Security authentication object
     * @return HTTP 200 OK with RealEstatePropertyResponse
     */
    @GetMapping("/{id}")
    public ResponseEntity<RealEstatePropertyResponse> getPropertyById(
            @PathVariable("id") Long propertyId,
            @RequestHeader(value = ENCRYPTION_KEY_HEADER, required = false) String encodedKey,
            Authentication authentication) {

        log.info("Retrieving property: id={}", propertyId);

        if (encodedKey == null || encodedKey.trim().isEmpty()) {
            throw new IllegalArgumentException("Encryption key header is required");
        }

        Long userId = ControllerUtil.extractUserId(authentication);
        SecretKey encryptionKey = EncryptionUtil.decodeEncryptionKey(encodedKey);

        RealEstatePropertyResponse response =
                realEstateService.getPropertyById(propertyId, userId, encryptionKey);

        log.info(
                "Property retrieved successfully: id={}, type={}",
                propertyId,
                response.getPropertyType());

        return ResponseEntity.ok(response);
    }

    /**
     * Updates an existing property.
     *
     * <p><strong>Request Headers:</strong>
     *
     * <ul>
     *   <li>Authorization: Bearer {jwt_token}
     *   <li>X-Encryption-Key: {base64_encoded_key}
     * </ul>
     *
     * <p><strong>Request Body:</strong> Same as create property request
     *
     * <p><strong>Success Response:</strong> Same as create property response with updated values
     *
     * <p><strong>Error Responses:</strong>
     *
     * <ul>
     *   <li>HTTP 400 Bad Request - Validation errors
     *   <li>HTTP 404 Not Found - Property not found or doesn't belong to user
     * </ul>
     *
     * <p>Requirement REQ-2.16.1: Update property
     *
     * <p>Requirement REQ-3.2: Authorization - verify ownership
     *
     * @param propertyId the property ID
     * @param request property update request
     * @param encodedKey Base64-encoded encryption key from header
     * @param authentication Spring Security authentication object
     * @return HTTP 200 OK with updated RealEstatePropertyResponse
     */
    @PutMapping("/{id}")
    public ResponseEntity<RealEstatePropertyResponse> updateProperty(
            @PathVariable("id") Long propertyId,
            @Valid @RequestBody RealEstatePropertyRequest request,
            @RequestHeader(value = ENCRYPTION_KEY_HEADER, required = false) String encodedKey,
            Authentication authentication) {

        log.info("Updating property: id={}", propertyId);

        if (encodedKey == null || encodedKey.trim().isEmpty()) {
            throw new IllegalArgumentException("Encryption key header is required");
        }

        Long userId = ControllerUtil.extractUserId(authentication);
        SecretKey encryptionKey = EncryptionUtil.decodeEncryptionKey(encodedKey);

        RealEstatePropertyResponse response =
                realEstateService.updateProperty(propertyId, userId, request, encryptionKey);

        log.info(
                "Property updated successfully: id={}, type={}",
                propertyId,
                response.getPropertyType());

        return ResponseEntity.ok(response);
    }

    /**
     * Deletes a property (soft delete by setting isActive = false).
     *
     * <p>The property is not physically removed from the database but marked as inactive for
     * historical tracking purposes. It will no longer appear in default listings or net worth
     * calculations.
     *
     * <p><strong>Request Headers:</strong>
     *
     * <ul>
     *   <li>Authorization: Bearer {jwt_token}
     * </ul>
     *
     * <p><strong>Success Response:</strong>
     *
     * <ul>
     *   <li>HTTP 204 No Content - Property successfully deleted
     * </ul>
     *
     * <p><strong>Error Responses:</strong>
     *
     * <ul>
     *   <li>HTTP 404 Not Found - Property not found or doesn't belong to user
     * </ul>
     *
     * <p>Requirement REQ-2.16.1: Delete property (soft delete)
     *
     * <p>Requirement REQ-3.2: Authorization - verify ownership
     *
     * @param propertyId the property ID
     * @param authentication Spring Security authentication object
     * @return HTTP 204 No Content
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteProperty(
            @PathVariable("id") Long propertyId,
            @RequestHeader(value = ENCRYPTION_KEY_HEADER, required = false) String encodedKey,
            Authentication authentication) {

        log.info("Deleting property: id={}", propertyId);

        Long userId = ControllerUtil.extractUserId(authentication);
        SecretKey encryptionKey =
                encodedKey != null && !encodedKey.trim().isEmpty()
                        ? EncryptionUtil.decodeEncryptionKey(encodedKey)
                        : null;

        realEstateService.deleteProperty(propertyId, userId, encryptionKey);

        log.info("Property deleted successfully: id={}", propertyId);

        return ResponseEntity.noContent().build();
    }

    /**
     * Calculates detailed equity breakdown for a property.
     *
     * <p>Equity represents the portion of the property owned outright (current value - mortgage
     * balance). If no mortgage is linked, equity equals current value.
     *
     * <p><strong>Request Headers:</strong>
     *
     * <ul>
     *   <li>Authorization: Bearer {jwt_token}
     *   <li>X-Encryption-Key: {base64_encoded_key}
     * </ul>
     *
     * <p><strong>Success Response (HTTP 200 OK):</strong>
     *
     * <pre>{@code
     * {
     * "propertyId": 1,
     * "propertyName": "Main Residence",
     * "currentValue": 1200000.00,
     * "mortgageBalance": 400000.00,
     * "equity": 800000.00,
     * "equityPercentage": 66.67,
     * "loanToValueRatio": 33.33,
     * "currency": "USD",
     * "mortgageId": 1,
     * "hasMortgage": true
     * }
     * }</pre>
     *
     * <p>Requirement REQ-2.16.2: Calculate property equity
     *
     * @param propertyId the property ID
     * @param encodedKey Base64-encoded encryption key from header
     * @param authentication Spring Security authentication object
     * @return HTTP 200 OK with PropertyEquityResponse
     */
    @GetMapping("/{id}/equity")
    public ResponseEntity<PropertyEquityResponse> calculateEquity(
            @PathVariable("id") Long propertyId,
            @RequestHeader(value = ENCRYPTION_KEY_HEADER, required = false) String encodedKey,
            Authentication authentication) {

        log.info("Calculating equity for property: id={}", propertyId);

        if (encodedKey == null || encodedKey.trim().isEmpty()) {
            throw new IllegalArgumentException("Encryption key header is required");
        }

        Long userId = ControllerUtil.extractUserId(authentication);
        SecretKey encryptionKey = EncryptionUtil.decodeEncryptionKey(encodedKey);

        PropertyEquityResponse response =
                realEstateService.calculateEquity(propertyId, userId, encryptionKey);

        log.info(
                "Equity calculated: propertyId={}, equity={}, equityPercentage={}%",
                propertyId, response.getEquity(), response.getEquityPercentage());

        return ResponseEntity.ok(response);
    }

    /**
     * Calculates detailed Return on Investment (ROI) for a property.
     *
     * <p>ROI considers both capital appreciation and rental income over the holding period.
     * Formula: ((currentValue - purchasePrice + totalRentalIncome) / purchasePrice) * 100
     *
     * <p><strong>Request Headers:</strong>
     *
     * <ul>
     *   <li>Authorization: Bearer {jwt_token}
     *   <li>X-Encryption-Key: {base64_encoded_key}
     * </ul>
     *
     * <p><strong>Success Response (HTTP 200 OK):</strong>
     *
     * <pre>{@code
     * {
     * "propertyId": 1,
     * "propertyName": "Rental Condo",
     * "purchasePrice": 400000.00,
     * "currentValue": 550000.00,
     * "purchaseDate": "2019-06-01",
     * "yearsOwned": 7,
     * "appreciation": 150000.00,
     * "appreciationPercentage": 37.50,
     * "monthlyRentalIncome": 2500.00,
     * "totalRentalIncome": 210000.00,
     * "rentalYield": 5.45,
     * "totalROI": 90.00,
     * "annualizedReturn": 12.86,
     * "currency": "USD",
     * "isRentalProperty": true
     * }
     * }</pre>
     *
     * <p>Requirement REQ-2.16.2: Calculate property ROI
     *
     * @param propertyId the property ID
     * @param encodedKey Base64-encoded encryption key from header
     * @param authentication Spring Security authentication object
     * @return HTTP 200 OK with PropertyROIResponse
     */
    @GetMapping("/{id}/roi")
    public ResponseEntity<PropertyROIResponse> calculateROI(
            @PathVariable("id") Long propertyId,
            @RequestHeader(value = ENCRYPTION_KEY_HEADER, required = false) String encodedKey,
            Authentication authentication) {

        log.info("Calculating ROI for property: id={}", propertyId);

        if (encodedKey == null || encodedKey.trim().isEmpty()) {
            throw new IllegalArgumentException("Encryption key header is required");
        }

        Long userId = ControllerUtil.extractUserId(authentication);
        SecretKey encryptionKey = EncryptionUtil.decodeEncryptionKey(encodedKey);

        PropertyROIResponse response =
                realEstateService.calculateROI(propertyId, userId, encryptionKey);

        log.info(
                "ROI calculated: propertyId={}, totalROI={}%, annualizedReturn={}%",
                propertyId, response.getTotalROI(), response.getAnnualizedReturn());

        return ResponseEntity.ok(response);
    }

    /**
     * Updates the estimated current value of a property.
     *
     * <p>This endpoint allows updating the property's current market value based on new appraisals,
     * market data, or automated valuation models without updating other property fields.
     *
     * <p><strong>Request Headers:</strong>
     *
     * <ul>
     *   <li>Authorization: Bearer {jwt_token}
     *   <li>X-Encryption-Key: {base64_encoded_key}
     * </ul>
     *
     * <p><strong>Request Body:</strong>
     *
     * <pre>{@code
     * {
     * "newValue": 1250000.00
     * }
     * }</pre>
     *
     * <p><strong>Success Response:</strong> Same as get property response with updated currentValue
     * and recalculated derived fields (appreciation, equity, ROI)
     *
     * <p><strong>Error Responses:</strong>
     *
     * <ul>
     *   <li>HTTP 400 Bad Request - Invalid value (negative or null)
     *   <li>HTTP 404 Not Found - Property not found or doesn't belong to user
     * </ul>
     *
     * <p>Requirement REQ-2.16.1: Update property value estimates
     *
     * <p>Requirement REQ-3.2: Authorization - verify ownership
     *
     * @param propertyId the property ID
     * @param newValue the new estimated value
     * @param encodedKey Base64-encoded encryption key from header
     * @param authentication Spring Security authentication object
     * @return HTTP 200 OK with updated RealEstatePropertyResponse
     */
    @PutMapping("/{id}/value")
    public ResponseEntity<RealEstatePropertyResponse> estimateValue(
            @PathVariable("id") Long propertyId,
            @RequestBody BigDecimal newValue,
            @RequestHeader(value = ENCRYPTION_KEY_HEADER, required = false) String encodedKey,
            Authentication authentication) {

        log.info("Updating estimated value for property: id={}, newValue={}", propertyId, newValue);

        if (encodedKey == null || encodedKey.trim().isEmpty()) {
            throw new IllegalArgumentException("Encryption key header is required");
        }

        Long userId = ControllerUtil.extractUserId(authentication);
        SecretKey encryptionKey = EncryptionUtil.decodeEncryptionKey(encodedKey);

        RealEstatePropertyResponse response =
                realEstateService.estimateValue(propertyId, userId, newValue, encryptionKey);

        log.info("Property value updated: id={}, newValue={}", propertyId, newValue);

        return ResponseEntity.ok(response);
    }
}
