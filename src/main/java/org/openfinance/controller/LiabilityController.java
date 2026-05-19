package org.openfinance.controller;

import jakarta.validation.Valid;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import javax.crypto.SecretKey;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.openfinance.dto.AmortizationScheduleEntry;
import org.openfinance.dto.LiabilityBreakdownResponse;
import org.openfinance.dto.LiabilityRequest;
import org.openfinance.dto.LiabilityResponse;
import org.openfinance.dto.TransactionResponse;
import org.openfinance.entity.LiabilityType;
import org.openfinance.entity.User;
import org.openfinance.service.LiabilityService;
import org.openfinance.util.EncryptionUtil;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for liability management endpoints.
 *
 * <p>Provides CRUD operations for liabilities (loans, mortgages, credit cards, personal loans). All
 * endpoints require authentication and use the user's encryption key to secure sensitive data.
 *
 * <p><strong>Endpoints:</strong>
 *
 * <ul>
 *   <li>POST /api/v1/liabilities - Create new liability
 *   <li>GET /api/v1/liabilities - List all user liabilities (with optional type filter)
 *   <li>GET /api/v1/liabilities/{id} - Get liability by ID
 *   <li>PUT /api/v1/liabilities/{id} - Update liability
 *   <li>DELETE /api/v1/liabilities/{id} - Delete liability
 *   <li>GET /api/v1/liabilities/{id}/amortization - Get amortization schedule
 *   <li>GET /api/v1/liabilities/{id}/total-interest - Get projected total interest
 *   <li>GET /api/v1/liabilities/total - Get total liabilities by currency
 * </ul>
 *
 * <p><strong>Security:</strong>
 *
 * <ul>
 *   <li>All endpoints require JWT authentication
 *   <li>Encryption key must be provided via X-Encryption-Key header
 *   <li>Users can only access their own liabilities
 *   <li>Sensitive fields (name, amounts, rates, notes) are encrypted at rest
 * </ul>
 *
 * <p>Requirement REQ-6.1: Liability Management - CRUD operations
 *
 * <p>Requirement REQ-6.1.3: Amortization calculations and interest projections
 *
 * <p>Requirement REQ-2.18: Data encryption at rest
 *
 * <p>Requirement REQ-3.2: Authorization checks
 *
 * @see LiabilityService
 * @see LiabilityRequest
 * @see LiabilityResponse
 */
@RestController
@RequestMapping("/api/v1/liabilities")
@RequiredArgsConstructor
@Slf4j
public class LiabilityController {

    private static final String ENCRYPTION_KEY_HEADER = "X-Encryption-Key";

    private final LiabilityService liabilityService;

    /**
     * Creates a new liability for the authenticated user.
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
     * "name": "Home Mortgage",
     * "type": "MORTGAGE",
     * "principal": 300000.00,
     * "currentBalance": 285000.00,
     * "interestRate": 3.75,
     * "startDate": "2024-01-01",
     * "endDate": "2054-01-01",
     * "minimumPayment": 1389.35,
     * "currency": "USD",
     * "notes": "30-year fixed rate mortgage"
     * }
     * }</pre>
     *
     * <p><strong>Success Response (HTTP 201 Created):</strong>
     *
     * <pre>{@code
     * {
     * "id": 1,
     * "userId": 1,
     * "name": "Home Mortgage",
     * "type": "MORTGAGE",
     * "principal": 300000.00,
     * "currentBalance": 285000.00,
     * "interestRate": 3.75,
     * "startDate": "2024-01-01",
     * "endDate": "2054-01-01",
     * "minimumPayment": 1389.35,
     * "currency": "USD",
     * "notes": "30-year fixed rate mortgage",
     * "createdAt": "2026-02-01T14:30:00",
     * "updatedAt": "2026-02-01T14:30:00",
     * "totalPaid": 15000.00,
     * "payoffPercentage": 5.00,
     * "monthsRemaining": 336,
     * "liabilityAgeDays": 396,
     * "monthlyInterestCost": 890.63
     * }
     * }</pre>
     *
     * <p>Requirement REQ-6.1.1: Create liability
     *
     * <p>Requirement REQ-6.1.2: Store liability details with encryption
     *
     * @param request liability creation request
     * @param encodedKey Base64-encoded encryption key from header
     * @param authentication Spring Security authentication object
     * @return HTTP 201 Created with LiabilityResponse
     */
    @PostMapping
    public ResponseEntity<LiabilityResponse> createLiability(
            @Valid @RequestBody LiabilityRequest request,
            @RequestHeader(value = ENCRYPTION_KEY_HEADER, required = false) String encodedKey,
            Authentication authentication) {

        log.info(
                "Creating liability for user: type={}, currency={}",
                request.getType(),
                request.getCurrency());

        if (encodedKey == null || encodedKey.trim().isEmpty()) {
            throw new IllegalArgumentException("Encryption key header is required");
        }

        User user = (User) authentication.getPrincipal();
        SecretKey encryptionKey = EncryptionUtil.decodeEncryptionKey(encodedKey);

        LiabilityResponse response =
                liabilityService.createLiability(user.getId(), request, encryptionKey);

        log.info(
                "Liability created successfully: id={}, type={}",
                response.getId(),
                response.getType());

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Retrieves all liabilities for the authenticated user with optional type filter.
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
     *   <li>type (optional): Filter by liability type (LOAN, MORTGAGE, CREDIT_CARD, PERSONAL_LOAN,
     *       OTHER)
     * </ul>
     *
     * <p><strong>Example Request:</strong>
     *
     * <pre>GET /api/v1/liabilities?type=MORTGAGE</pre>
     *
     * <p><strong>Success Response (HTTP 200 OK):</strong>
     *
     * <pre>{@code
     * [
     * {
     * "id": 1,
     * "name": "Home Mortgage",
     * "type": "MORTGAGE",
     * "currentBalance": 285000.00,
     * "currency": "USD",
     * ...
     * },
     * {
     * "id": 2,
     * "name": "Visa Credit Card",
     * "type": "CREDIT_CARD",
     * "currentBalance": 3500.00,
     * "currency": "USD",
     * ...
     * }
     * ]
     * }</pre>
     *
     * <p>Requirement REQ-6.1.1: List user liabilities
     *
     * <p>Requirement REQ-6.1.2: Filter by liability type
     *
     * @param type optional liability type filter
     * @param encodedKey Base64-encoded encryption key from header
     * @param authentication Spring Security authentication object
     * @return HTTP 200 OK with list of LiabilityResponse
     */
    @GetMapping
    public ResponseEntity<List<LiabilityResponse>> getLiabilities(
            @RequestParam(value = "type", required = false) LiabilityType type,
            @RequestHeader(value = ENCRYPTION_KEY_HEADER, required = false) String encodedKey,
            Authentication authentication) {

        log.info("Retrieving liabilities for user: type={}", type);

        if (encodedKey == null || encodedKey.trim().isEmpty()) {
            throw new IllegalArgumentException("Encryption key header is required");
        }

        User user = (User) authentication.getPrincipal();
        SecretKey encryptionKey = EncryptionUtil.decodeEncryptionKey(encodedKey);

        List<LiabilityResponse> liabilities;
        if (type != null) {
            liabilities = liabilityService.getLiabilitiesByType(user.getId(), type, encryptionKey);
        } else {
            liabilities = liabilityService.getLiabilitiesByUserId(user.getId(), encryptionKey);
        }

        log.info("Retrieved {} liabilities for user", liabilities.size());

        return ResponseEntity.ok(liabilities);
    }

    /**
     * Retrieves liabilities with pagination and optional filtering.
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
     *   <li>page (optional): Page number (0-indexed, default: 0)
     *   <li>size (optional): Page size (default: 20)
     *   <li>type (optional): Filter by liability type
     *   <li>search (optional): Search by liability name
     *   <li>sort (optional): Sort field and direction (e.g., "name,asc" or "createdAt,desc")
     * </ul>
     *
     * <p><strong>Example Request:</strong>
     *
     * <pre>GET
     * /api/v1/liabilities/paged?page=0&size=10&type=MORTGAGE&search=home&sort=createdAt,desc</pre>
     *
     * <p><strong>Success Response (HTTP 200 OK):</strong>
     *
     * <pre>{@code
     * {
     * "content": [...],
     * "pageable": { "pageNumber": 0, "pageSize": 10 },
     * "totalElements": 5,
     * "totalPages": 1,
     * "last": true,
     * "first": true
     * }
     * }</pre>
     *
     * <p>Requirement REQ-6.1.1: List user liabilities with pagination
     *
     * @param page page number (0-indexed)
     * @param size page size
     * @param type optional liability type filter
     * @param search optional search term
     * @param sort optional sort field and direction
     * @param encodedKey Base64-encoded encryption key from header
     * @param authentication Spring Security authentication object
     * @return HTTP 200 OK with paginated LiabilityResponse
     */
    @GetMapping("/paged")
    public ResponseEntity<Page<LiabilityResponse>> getLiabilitiesPaged(
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "20") int size,
            @RequestParam(value = "type", required = false) LiabilityType type,
            @RequestParam(value = "search", required = false) String search,
            @RequestParam(value = "sort", defaultValue = "createdAt,desc") String sort,
            @RequestHeader(value = ENCRYPTION_KEY_HEADER, required = false) String encodedKey,
            Authentication authentication) {

        log.info(
                "Retrieving liabilities paged for user: page={}, size={}, type={}, search={}, sort={}",
                page,
                size,
                type,
                search,
                sort);

        if (encodedKey == null || encodedKey.trim().isEmpty()) {
            throw new IllegalArgumentException("Encryption key header is required");
        }

        User user = (User) authentication.getPrincipal();
        SecretKey encryptionKey = EncryptionUtil.decodeEncryptionKey(encodedKey);

        // Parse sort parameter
        String[] sortParts = sort.split(",");
        String sortField = sortParts[0];
        Sort.Direction direction =
                sortParts.length > 1 && "asc".equalsIgnoreCase(sortParts[1])
                        ? Sort.Direction.ASC
                        : Sort.Direction.DESC;

        Pageable pageable = PageRequest.of(page, size, Sort.by(direction, sortField));

        Page<LiabilityResponse> liabilitiesPage =
                liabilityService.getLiabilitiesWithFilters(
                        user.getId(), type, search, pageable, encryptionKey);

        log.info(
                "Retrieved liabilities page {} of {} (total: {}) for user",
                page + 1,
                liabilitiesPage.getTotalPages(),
                liabilitiesPage.getTotalElements());

        return ResponseEntity.ok(liabilitiesPage);
    }

    /**
     * Retrieves a single liability by ID.
     *
     * <p><strong>Request Headers:</strong>
     *
     * <ul>
     *   <li>Authorization: Bearer {jwt_token}
     *   <li>X-Encryption-Key: {base64_encoded_key}
     * </ul>
     *
     * <p><strong>Example Request:</strong>
     *
     * <pre>GET /api/v1/liabilities/1</pre>
     *
     * <p><strong>Success Response (HTTP 200 OK):</strong>
     *
     * <pre>{@code
     * {
     * "id": 1,
     * "name": "Home Mortgage",
     * "type": "MORTGAGE",
     * "principal": 300000.00,
     * "currentBalance": 285000.00,
     * "interestRate": 3.75,
     * "totalPaid": 15000.00,
     * "payoffPercentage": 5.00,
     * ...
     * }
     * }</pre>
     *
     * <p>Requirement REQ-6.1.1: View liability details
     *
     * @param liabilityId liability ID
     * @param encodedKey Base64-encoded encryption key from header
     * @param authentication Spring Security authentication object
     * @return HTTP 200 OK with LiabilityResponse
     */
    @GetMapping("/{id}")
    public ResponseEntity<LiabilityResponse> getLiability(
            @PathVariable("id") Long liabilityId,
            @RequestHeader(value = ENCRYPTION_KEY_HEADER, required = false) String encodedKey,
            Authentication authentication) {

        log.info("Retrieving liability: id={}", liabilityId);

        if (encodedKey == null || encodedKey.trim().isEmpty()) {
            throw new IllegalArgumentException("Encryption key header is required");
        }

        User user = (User) authentication.getPrincipal();
        SecretKey encryptionKey = EncryptionUtil.decodeEncryptionKey(encodedKey);

        LiabilityResponse liability =
                liabilityService.getLiabilityById(liabilityId, user.getId(), encryptionKey);

        log.info("Liability retrieved successfully: id={}", liabilityId);

        return ResponseEntity.ok(liability);
    }

    /**
     * Updates an existing liability.
     *
     * <p><strong>Request Headers:</strong>
     *
     * <ul>
     *   <li>Authorization: Bearer {jwt_token}
     *   <li>X-Encryption-Key: {base64_encoded_key}
     * </ul>
     *
     * <p><strong>Request Body:</strong> Same as create request
     *
     * <p><strong>Example Request:</strong>
     *
     * <pre>PUT /api/v1/liabilities/1</pre>
     *
     * <p><strong>Success Response (HTTP 200 OK):</strong>
     *
     * <pre>{@code
     * {
     * "id": 1,
     * "name": "Home Mortgage - Updated",
     * "currentBalance": 282000.00,
     * ...
     * }
     * }</pre>
     *
     * <p>Requirement REQ-6.1.2: Update liability details
     *
     * @param liabilityId liability ID to update
     * @param request liability update request
     * @param encodedKey Base64-encoded encryption key from header
     * @param authentication Spring Security authentication object
     * @return HTTP 200 OK with updated LiabilityResponse
     */
    @PutMapping("/{id}")
    public ResponseEntity<LiabilityResponse> updateLiability(
            @PathVariable("id") Long liabilityId,
            @Valid @RequestBody LiabilityRequest request,
            @RequestHeader(value = ENCRYPTION_KEY_HEADER, required = false) String encodedKey,
            Authentication authentication) {

        log.info("Updating liability: id={}", liabilityId);

        if (encodedKey == null || encodedKey.trim().isEmpty()) {
            throw new IllegalArgumentException("Encryption key header is required");
        }

        User user = (User) authentication.getPrincipal();
        SecretKey encryptionKey = EncryptionUtil.decodeEncryptionKey(encodedKey);

        LiabilityResponse response =
                liabilityService.updateLiability(liabilityId, user.getId(), request, encryptionKey);

        log.info("Liability updated successfully: id={}", liabilityId);

        return ResponseEntity.ok(response);
    }

    /**
     * Deletes a liability.
     *
     * <p><strong>Request Headers:</strong>
     *
     * <ul>
     *   <li>Authorization: Bearer {jwt_token}
     * </ul>
     *
     * <p><strong>Example Request:</strong>
     *
     * <pre>
     * DELETE / api / v1 / liabilities / 1
     * </pre>
     *
     * <p><strong>Success Response:</strong> HTTP 204 No Content
     *
     * <p>Requirement REQ-6.1.1: Delete liability
     *
     * @param liabilityId liability ID to delete
     * @param authentication Spring Security authentication object
     * @return HTTP 204 No Content
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteLiability(
            @PathVariable("id") Long liabilityId,
            @RequestHeader(value = ENCRYPTION_KEY_HEADER, required = false) String encodedKey,
            Authentication authentication) {

        log.info("Deleting liability: id={}", liabilityId);

        User user = (User) authentication.getPrincipal();

        SecretKey encryptionKey =
                encodedKey != null && !encodedKey.trim().isEmpty()
                        ? EncryptionUtil.decodeEncryptionKey(encodedKey)
                        : null;

        liabilityService.deleteLiability(liabilityId, user.getId(), encryptionKey);

        log.info("Liability deleted successfully: id={}", liabilityId);

        return ResponseEntity.noContent().build();
    }

    /**
     * Calculates and returns the amortization schedule for a liability.
     *
     * <p>The amortization schedule shows the payment-by-payment breakdown of principal and interest
     * over the life of the loan.
     *
     * <p><strong>Request Headers:</strong>
     *
     * <ul>
     *   <li>Authorization: Bearer {jwt_token}
     *   <li>X-Encryption-Key: {base64_encoded_key}
     * </ul>
     *
     * <p><strong>Example Request:</strong>
     *
     * <pre>GET /api/v1/liabilities/1/amortization</pre>
     *
     * <p><strong>Success Response (HTTP 200 OK):</strong>
     *
     * <pre>{@code
     * [
     * {
     * "paymentNumber": 1,
     * "paymentDate": "2026-03-01",
     * "paymentAmount": 1389.35,
     * "principalPortion": 498.72,
     * "interestPortion": 890.63,
     * "remainingBalance": 284501.28,
     * "cumulativePrincipal": 498.72,
     * "cumulativeInterest": 890.63
     * },
     * {
     * "paymentNumber": 2,
     * "paymentDate": "2026-04-01",
     * "paymentAmount": 1389.35,
     * "principalPortion": 500.28,
     * "interestPortion": 889.07,
     * "remainingBalance": 284001.00,
     * "cumulativePrincipal": 999.00,
     * "cumulativeInterest": 1779.70
     * },
     * ...
     * ]
     * }</pre>
     *
     * <p>Requirement REQ-6.1.3: Generate amortization schedules
     *
     * @param liabilityId liability ID
     * @param encodedKey Base64-encoded encryption key from header
     * @param authentication Spring Security authentication object
     * @return HTTP 200 OK with list of AmortizationScheduleEntry
     */
    @GetMapping("/{id}/amortization")
    public ResponseEntity<List<AmortizationScheduleEntry>> getAmortizationSchedule(
            @PathVariable("id") Long liabilityId,
            @RequestHeader(value = ENCRYPTION_KEY_HEADER, required = false) String encodedKey,
            Authentication authentication) {

        log.info("Calculating amortization schedule for liability: id={}", liabilityId);

        if (encodedKey == null || encodedKey.trim().isEmpty()) {
            throw new IllegalArgumentException("Encryption key header is required");
        }

        User user = (User) authentication.getPrincipal();
        SecretKey encryptionKey = EncryptionUtil.decodeEncryptionKey(encodedKey);

        List<AmortizationScheduleEntry> schedule =
                liabilityService.calculateAmortizationSchedule(
                        liabilityId, user.getId(), encryptionKey);

        log.info("Amortization schedule calculated: {} payments", schedule.size());

        return ResponseEntity.ok(schedule);
    }

    /**
     * Calculates and returns the projected total interest for a liability.
     *
     * <p>This is the sum of all interest payments over the life of the loan.
     *
     * <p><strong>Request Headers:</strong>
     *
     * <ul>
     *   <li>Authorization: Bearer {jwt_token}
     *   <li>X-Encryption-Key: {base64_encoded_key}
     * </ul>
     *
     * <p><strong>Example Request:</strong>
     *
     * <pre>GET /api/v1/liabilities/1/total-interest</pre>
     *
     * <p><strong>Success Response (HTTP 200 OK):</strong>
     *
     * <pre>{@code
     * {
     * "totalInterest": 215162.00
     * }
     * }</pre>
     *
     * <p>Requirement REQ-6.1.3: Calculate projected total interest
     *
     * @param liabilityId liability ID
     * @param encodedKey Base64-encoded encryption key from header
     * @param authentication Spring Security authentication object
     * @return HTTP 200 OK with total interest amount
     */
    @GetMapping("/{id}/total-interest")
    public ResponseEntity<Map<String, BigDecimal>> getTotalInterest(
            @PathVariable("id") Long liabilityId,
            @RequestHeader(value = ENCRYPTION_KEY_HEADER, required = false) String encodedKey,
            Authentication authentication) {

        log.info("Calculating total interest for liability: id={}", liabilityId);

        if (encodedKey == null || encodedKey.trim().isEmpty()) {
            throw new IllegalArgumentException("Encryption key header is required");
        }

        User user = (User) authentication.getPrincipal();
        SecretKey encryptionKey = EncryptionUtil.decodeEncryptionKey(encodedKey);

        BigDecimal totalInterest =
                liabilityService.calculateTotalInterest(liabilityId, user.getId(), encryptionKey);

        log.info("Total interest calculated: {}", totalInterest);

        return ResponseEntity.ok(
                Map.of("totalInterest", totalInterest != null ? totalInterest : BigDecimal.ZERO));
    }

    /**
     * Returns a detailed cost breakdown for a liability.
     *
     * <p>Provides a comprehensive financial snapshot including:
     *
     * <ul>
     *   <li>Principal paid and remaining balance
     *   <li>Estimated interest paid and projected remaining interest
     *   <li>Insurance paid and projected insurance (if insurancePercentage set)
     *   <li>Fees paid and projected fees (if additionalFees set)
     *   <li>Total paid and total projected cost
     *   <li>Count and total amount of linked transactions (loan payment records)
     * </ul>
     *
     * <p><strong>Request Headers:</strong>
     *
     * <ul>
     *   <li>Authorization: Bearer {jwt_token}
     *   <li>X-Encryption-Key: {base64_encoded_key}
     * </ul>
     *
     * <p><strong>Example Request:</strong>
     *
     * <pre>
     * GET / api / v1 / liabilities / 1 / breakdown
     * </pre>
     *
     * <p>Requirement REQ-LIA-3: Display liability breakdown with cost analysis
     *
     * @param liabilityId liability ID
     * @param encodedKey Base64-encoded encryption key from header
     * @param authentication Spring Security authentication object
     * @return HTTP 200 OK with LiabilityBreakdownResponse
     */
    @GetMapping("/{id}/breakdown")
    public ResponseEntity<LiabilityBreakdownResponse> getLiabilityBreakdown(
            @PathVariable("id") Long liabilityId,
            @RequestHeader(value = ENCRYPTION_KEY_HEADER, required = false) String encodedKey,
            Authentication authentication) {

        log.info("Getting liability breakdown: id={}", liabilityId);

        if (encodedKey == null || encodedKey.trim().isEmpty()) {
            throw new IllegalArgumentException("Encryption key header is required");
        }

        User user = (User) authentication.getPrincipal();
        SecretKey encryptionKey = EncryptionUtil.decodeEncryptionKey(encodedKey);

        LiabilityBreakdownResponse breakdown =
                liabilityService.getLiabilityBreakdown(liabilityId, user.getId(), encryptionKey);

        log.info(
                "Liability breakdown retrieved: id={}, linkedTxCount={}",
                liabilityId,
                breakdown.getLinkedTransactionCount());

        return ResponseEntity.ok(breakdown);
    }

    /**
     * Retrieves all transactions linked to a specific liability.
     *
     * <p>Returns expense transactions that have been linked to this liability, typically
     * representing loan payment records.
     *
     * <p><strong>Request Headers:</strong>
     *
     * <ul>
     *   <li>Authorization: Bearer {jwt_token}
     * </ul>
     *
     * <p><strong>Example Request:</strong>
     *
     * <pre>
     * GET / api / v1 / liabilities / 1 / transactions
     * </pre>
     *
     * <p>Requirement REQ-LIA-4: Transaction-liability linking
     *
     * @param liabilityId liability ID
     * @param authentication Spring Security authentication object
     * @return HTTP 200 OK with list of linked Transaction entities
     */
    @GetMapping("/{id}/transactions")
    public ResponseEntity<List<TransactionResponse>> getLinkedTransactions(
            @PathVariable("id") Long liabilityId,
            @RequestHeader(value = ENCRYPTION_KEY_HEADER, required = false) String encodedKey,
            Authentication authentication) {

        log.info("Getting linked transactions for liability: id={}", liabilityId);

        if (encodedKey == null || encodedKey.trim().isEmpty()) {
            throw new IllegalArgumentException("Encryption key header is required");
        }

        User user = (User) authentication.getPrincipal();
        SecretKey encryptionKey = EncryptionUtil.decodeEncryptionKey(encodedKey);

        List<TransactionResponse> transactions =
                liabilityService.getLinkedTransactions(liabilityId, user.getId(), encryptionKey);

        log.info(
                "Retrieved {} linked transactions for liability {}",
                transactions.size(),
                liabilityId);

        return ResponseEntity.ok(transactions);
    }

    /**
     * Calculates and returns the total liabilities for the authenticated user, grouped by currency.
     *
     * <p><strong>Request Headers:</strong>
     *
     * <ul>
     *   <li>Authorization: Bearer {jwt_token}
     *   <li>X-Encryption-Key: {base64_encoded_key}
     * </ul>
     *
     * <p><strong>Example Request:</strong>
     *
     * <pre>GET /api/v1/liabilities/total</pre>
     *
     * <p><strong>Success Response (HTTP 200 OK):</strong>
     *
     * <pre>{@code
     * {
     * "USD": 288500.00,
     * "EUR": 15000.00
     * }
     * }</pre>
     *
     * <p>Requirement REQ-6.1.3: Calculate total liabilities
     *
     * @param encodedKey Base64-encoded encryption key from header
     * @param authentication Spring Security authentication object
     * @return HTTP 200 OK with map of currency to total amount
     */
    @GetMapping("/total")
    public ResponseEntity<Map<String, BigDecimal>> getTotalLiabilities(
            @RequestHeader(value = ENCRYPTION_KEY_HEADER, required = false) String encodedKey,
            Authentication authentication) {

        log.info("Calculating total liabilities for user");

        if (encodedKey == null || encodedKey.trim().isEmpty()) {
            throw new IllegalArgumentException("Encryption key header is required");
        }

        User user = (User) authentication.getPrincipal();
        SecretKey encryptionKey = EncryptionUtil.decodeEncryptionKey(encodedKey);

        Map<String, BigDecimal> totalsByCurrency =
                liabilityService.calculateTotalLiabilities(user.getId(), encryptionKey);

        log.info("Total liabilities calculated: {}", totalsByCurrency);

        return ResponseEntity.ok(totalsByCurrency);
    }
}
