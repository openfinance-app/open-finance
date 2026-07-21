package org.openfinance.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.openfinance.dto.AccountResponse;
import org.openfinance.dto.AmortizationScheduleEntry;
import org.openfinance.dto.LiabilityBreakdownResponse;
import org.openfinance.dto.LiabilityRequest;
import org.openfinance.dto.LiabilityResponse;
import org.openfinance.entity.Liability;
import org.openfinance.entity.LiabilityType;
import org.openfinance.entity.Transaction;
import org.openfinance.entity.User;
import org.openfinance.exception.LiabilityNotFoundException;
import org.openfinance.exception.ResourceNotFoundException;
import org.openfinance.repository.CurrencyRepository;
import org.openfinance.repository.LiabilityRepository;
import org.openfinance.repository.NetWorthRepository;
import org.openfinance.repository.RealEstateRepository;
import org.openfinance.repository.TransactionRepository;
import org.openfinance.repository.UserRepository;
import org.openfinance.security.EncryptionService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service layer for managing liabilities (debts, loans, mortgages, credit cards).
 *
 * <p>This service handles business logic for liability CRUD operations, including:
 *
 * <ul>
 *   <li>Creating new liabilities with encrypted sensitive fields
 *   <li>Updating existing liabilities
 *   <li>Deleting liabilities
 *   <li>Retrieving liabilities with decrypted data and calculated fields
 *   <li>Amortization schedule calculations
 *   <li>Liability analytics (total debt, interest projections)
 * </ul>
 *
 * <p><strong>Security Note:</strong> The {@code name}, {@code principal}, {@code currentBalance},
 * {@code interestRate}, {@code minimumPayment}, and {@code notes} fields are encrypted before
 * storing in the database and decrypted when reading. The encryption key must be provided by the
 * caller (typically from the user's session after authentication).
 *
 * <p>Requirement REQ-6.1: Liability Management - CRUD operations for liabilities
 *
 * <p>Requirement REQ-6.1.2: Track liability details (name, type, balances, rates)
 *
 * <p>Requirement REQ-6.1.3: Calculate amortization schedules and interest projections
 *
 * <p>Requirement REQ-2.18: Data encryption at rest for sensitive fields
 *
 * <p>Requirement REQ-3.2: Authorization - Users can only access their own liabilities
 *
 * @see org.openfinance.entity.Liability
 * @see org.openfinance.dto.LiabilityRequest
 * @see org.openfinance.dto.LiabilityResponse
 */
@Service
@Transactional
@RequiredArgsConstructor
@Slf4j
public class LiabilityService {

    private final LiabilityRepository liabilityRepository;
    private final CurrencyRepository currencyRepository;
    private final EncryptionService encryptionService;
    private final TransactionRepository transactionRepository;
    private final TransactionService transactionService;
    private final UserRepository userRepository;
    private final ExchangeRateService exchangeRateService;
    private final RealEstateRepository realEstateRepository;
    private final org.openfinance.repository.InstitutionRepository institutionRepository;
    private final NetWorthRepository netWorthRepository;
    private final OperationHistoryService operationHistoryService;
    private final SearchTokenService searchTokenService;
    private final DefaultCurrencyProvider defaultCurrencyProvider;

    // Constants for calculations
    private static final int MAX_AMORTIZATION_PERIODS = 360; // Max 30 years of monthly payments
    private static final int MONTHS_PER_YEAR = 12;
    private static final int SCALE = 6; // Decimal precision for intermediate calculations

    /**
     * Creates a new liability for the specified user.
     *
     * <p>Sensitive fields (name, principal, currentBalance, interestRate, minimumPayment, notes)
     * are encrypted before storing in the database.
     *
     * <p>Requirement REQ-6.1.1: Create new liability with encrypted sensitive data
     *
     * <p>Requirement REQ-6.1.2: Store liability details
     *
     * @param userId the ID of the user creating the liability
     * @param request the liability creation request containing liability details
     * @param encryptionKey the AES-256 encryption key for sensitive fields
     * @return the created liability with decrypted data and calculated fields
     * @throws IllegalArgumentException if userId, request, or encryptionKey is null
     */
    public LiabilityResponse createLiability(Long userId, LiabilityRequest request) {
        if (userId == null) {
            throw new IllegalArgumentException("User ID cannot be null");
        }
        if (request == null) {
            throw new IllegalArgumentException("Liability request cannot be null");
        }
        log.debug(
                "Creating liability for user {}: type={}, currency={}",
                userId,
                request.getType(),
                request.getCurrency());

        // Create entity and set basic fields
        Liability liability = new Liability();
        liability.setUserId(userId);
        liability.setType(request.getType());
        liability.setStartDate(request.getStartDate());
        liability.setEndDate(request.getEndDate());
        liability.setCurrency(request.getCurrency());
        liability.setCurrencyId(resolveCurrencyId(request.getCurrency()));

        // Set sensitive fields (encryption handled by JPA AttributeConverter)
        liability.setName(request.getName());
        liability.setPrincipal(request.getPrincipal().toString());
        liability.setCurrentBalance(request.getCurrentBalance().toString());

        if (request.getInterestRate() != null) {
            liability.setInterestRate(request.getInterestRate().toString());
        }

        if (request.getMinimumPayment() != null) {
            liability.setMinimumPayment(request.getMinimumPayment().toString());
        }

        if (request.getNotes() != null && !request.getNotes().isBlank()) {
            liability.setNotes(request.getNotes());
        }

        if (request.getInstitutionId() != null) {
            org.openfinance.entity.Institution institution =
                    institutionRepository
                            .findById(request.getInstitutionId())
                            .orElseThrow(
                                    () ->
                                            new org.openfinance.exception
                                                    .InstitutionNotFoundException(
                                                    request.getInstitutionId()));
            liability.setInstitution(institution);
        }

        // Set optional insurance percentage (Requirement REQ-LIA-1)
        if (request.getInsurancePercentage() != null) {
            liability.setInsurancePercentage(request.getInsurancePercentage().toString());
        }

        // Set optional additional fees (Requirement REQ-LIA-2)
        if (request.getAdditionalFees() != null) {
            liability.setAdditionalFees(request.getAdditionalFees().toString());
        }

        // Save to database
        Liability savedLiability = liabilityRepository.save(liability);
        indexLiabilitySearchTokens(savedLiability, request.getName());
        log.info(
                "Liability created successfully: id={}, userId={}, type={}",
                savedLiability.getId(),
                userId,
                savedLiability.getType());
        invalidateSnapshotsFrom(userId, savedLiability.getStartDate());

        // If a real estate property ID is provided, link this mortgage to that property
        if (request.getRealEstateId() != null) {
            realEstateRepository
                    .findById(request.getRealEstateId())
                    .ifPresent(
                            property -> {
                                if (property.getUserId().equals(userId)) {
                                    property.setMortgageId(savedLiability.getId());
                                    realEstateRepository.save(property);
                                    log.info(
                                            "Linked liability {} to real estate property {} as mortgage",
                                            savedLiability.getId(),
                                            property.getId());
                                }
                            });
        }

        // Decrypt and return response with calculated fields
        LiabilityResponse liabilityCreateResponse = toResponseWithDecryption(savedLiability);

        // Record in operation history
        operationHistoryService.record(
                userId,
                org.openfinance.entity.EntityType.LIABILITY,
                savedLiability.getId(),
                request.getName(),
                org.openfinance.entity.OperationType.CREATE,
                (Object) null,
                null);

        return liabilityCreateResponse;
    }

    /**
     * Updates an existing liability.
     *
     * <p>Only the liability owner can update the liability. Sensitive fields are re-encrypted if
     * they have changed.
     *
     * <p>Requirement REQ-6.1.2: Update liability details
     *
     * <p>Requirement REQ-3.2: Authorization check - verify liability ownership
     *
     * @param liabilityId the ID of the liability to update
     * @param userId the ID of the user updating the liability (for authorization)
     * @param request the liability update request
     * @param encryptionKey the AES-256 encryption key for sensitive fields
     * @return the updated liability with decrypted data and calculated fields
     * @throws ResourceNotFoundException if liability not found or doesn't belong to user
     * @throws IllegalArgumentException if any parameter is null
     */
    public LiabilityResponse updateLiability(
            Long liabilityId, Long userId, LiabilityRequest request) {
        if (liabilityId == null) {
            throw new IllegalArgumentException("Liability ID cannot be null");
        }
        if (userId == null) {
            throw new IllegalArgumentException("User ID cannot be null");
        }
        if (request == null) {
            throw new IllegalArgumentException("Liability request cannot be null");
        }
        log.debug(
                "Updating liability {}: userId={}, type={}",
                liabilityId,
                userId,
                request.getType());

        // Fetch liability and verify ownership (Requirement 3.2: Authorization)
        Liability liability =
                liabilityRepository
                        .findByIdAndUserId(liabilityId, userId)
                        .orElseThrow(
                                () -> LiabilityNotFoundException.byIdAndUser(liabilityId, userId));

        // Capture snapshot before update for history
        LiabilityResponse beforeSnapshot = toResponseWithDecryption(liability);

        // Capture the old start date before overwriting, for net worth invalidation
        LocalDate oldStartDate = liability.getStartDate();

        // Update basic fields
        liability.setType(request.getType());
        liability.setStartDate(request.getStartDate());
        liability.setEndDate(request.getEndDate());
        liability.setCurrency(request.getCurrency());
        liability.setCurrencyId(resolveCurrencyId(request.getCurrency()));

        // Set sensitive fields (encryption handled by JPA AttributeConverter)
        liability.setName(request.getName());
        liability.setPrincipal(request.getPrincipal().toString());
        liability.setCurrentBalance(request.getCurrentBalance().toString());

        if (request.getInterestRate() != null) {
            liability.setInterestRate(request.getInterestRate().toString());
        } else {
            liability.setInterestRate(null);
        }

        if (request.getMinimumPayment() != null) {
            liability.setMinimumPayment(request.getMinimumPayment().toString());
        } else {
            liability.setMinimumPayment(null);
        }

        if (request.getNotes() != null && !request.getNotes().isBlank()) {
            liability.setNotes(request.getNotes());
        } else {
            liability.setNotes(null);
        }

        if (request.getInstitutionId() != null) {
            org.openfinance.entity.Institution institution =
                    institutionRepository
                            .findById(request.getInstitutionId())
                            .orElseThrow(
                                    () ->
                                            new org.openfinance.exception
                                                    .InstitutionNotFoundException(
                                                    request.getInstitutionId()));
            liability.setInstitution(institution);
        } else {
            liability.setInstitution(null);
        }

        // Update optional insurance percentage (Requirement REQ-LIA-1)
        if (request.getInsurancePercentage() != null) {
            liability.setInsurancePercentage(request.getInsurancePercentage().toString());
        } else {
            liability.setInsurancePercentage(null);
        }

        // Update optional additional fees (Requirement REQ-LIA-2)
        if (request.getAdditionalFees() != null) {
            liability.setAdditionalFees(request.getAdditionalFees().toString());
        } else {
            liability.setAdditionalFees(null);
        }

        // Save updated liability
        Liability updatedLiability = liabilityRepository.save(liability);
        indexLiabilitySearchTokens(updatedLiability, request.getName());
        log.info("Liability updated successfully: id={}, userId={}", liabilityId, userId);
        LocalDate newStartDate = updatedLiability.getStartDate();
        LocalDate cutoff =
                (oldStartDate != null
                                && (newStartDate == null || oldStartDate.isBefore(newStartDate)))
                        ? oldStartDate
                        : newStartDate;
        invalidateSnapshotsFrom(userId, cutoff);

        // Decrypt and return response with calculated fields
        LiabilityResponse liabilityUpdateResponse = toResponseWithDecryption(updatedLiability);

        // Record in operation history
        operationHistoryService.record(
                userId,
                org.openfinance.entity.EntityType.LIABILITY,
                liabilityId,
                request.getName(),
                org.openfinance.entity.OperationType.UPDATE,
                beforeSnapshot,
                null);

        return liabilityUpdateResponse;
    }

    /**
     * Deletes a liability.
     *
     * <p>Only the liability owner can delete the liability.
     *
     * <p>Requirement REQ-6.1.1: Delete liability
     *
     * <p>Requirement REQ-3.2: Authorization check - verify liability ownership
     *
     * @param liabilityId the ID of the liability to delete
     * @param userId the ID of the user deleting the liability (for authorization)
     * @throws ResourceNotFoundException if liability not found or doesn't belong to user
     * @throws IllegalArgumentException if liabilityId or userId is null
     */
    public void deleteLiability(Long liabilityId, Long userId) {
        if (liabilityId == null) {
            throw new IllegalArgumentException("Liability ID cannot be null");
        }
        if (userId == null) {
            throw new IllegalArgumentException("User ID cannot be null");
        }

        log.debug("Deleting liability {}: userId={}", liabilityId, userId);

        // Fetch liability to obtain its start date before deletion
        Liability liability =
                liabilityRepository
                        .findByIdAndUserId(liabilityId, userId)
                        .orElseThrow(
                                () -> LiabilityNotFoundException.byIdAndUser(liabilityId, userId));

        String label = null;
        LiabilityResponse snapshot = null;
        snapshot = toResponseWithDecryption(liability);
        label = snapshot.getName();

        LocalDate liabilityStartDate = liability.getStartDate();

        // Delete liability
        liabilityRepository.delete(liability);
        searchTokenService.removeEntity("LIABILITY", liabilityId);
        log.info("Liability deleted successfully: id={}, userId={}", liabilityId, userId);
        invalidateSnapshotsFrom(userId, liabilityStartDate);

        // Record in operation history
        operationHistoryService.record(
                userId,
                org.openfinance.entity.EntityType.LIABILITY,
                liabilityId,
                label,
                org.openfinance.entity.OperationType.DELETE,
                snapshot,
                null);
    }

    /**
     * Retrieves a single liability by ID.
     *
     * <p>Returns the liability with decrypted data and calculated fields.
     *
     * <p>Requirement REQ-6.1.1: View liability details
     *
     * <p>Requirement REQ-3.2: Authorization check - verify liability ownership
     *
     * @param liabilityId the ID of the liability to retrieve
     * @param userId the ID of the user retrieving the liability (for authorization)
     * @param encryptionKey the AES-256 encryption key for decrypting sensitive fields
     * @return the liability with decrypted data and calculated fields
     * @throws ResourceNotFoundException if liability not found or doesn't belong to user
     * @throws IllegalArgumentException if any parameter is null
     */
    @Transactional(readOnly = true)
    public LiabilityResponse getLiabilityById(Long liabilityId, Long userId) {
        log.debug("Retrieving liability {}: userId={}", liabilityId, userId);

        if (liabilityId == null) {
            throw new IllegalArgumentException("Liability ID cannot be null");
        }
        if (userId == null) {
            throw new IllegalArgumentException("User ID cannot be null");
        }
        // Fetch liability and verify ownership (Requirement 3.2: Authorization)
        Liability liability =
                liabilityRepository
                        .findByIdAndUserId(liabilityId, userId)
                        .orElseThrow(
                                () -> LiabilityNotFoundException.byIdAndUser(liabilityId, userId));

        // Decrypt and return response with calculated fields
        return toResponseWithDecryption(liability);
    }

    /**
     * Retrieves all liabilities for a user.
     *
     * <p>Returns all liabilities with decrypted data and calculated fields, ordered by creation
     * date (most recent first).
     *
     * <p>Requirement REQ-6.1.1: List all user liabilities
     *
     * <p>Requirement REQ-6.1.3: Display total debt
     *
     * @param userId the ID of the user
     * @param encryptionKey the AES-256 encryption key for decrypting sensitive fields
     * @return list of liabilities with decrypted data and calculated fields (may be empty)
     * @throws IllegalArgumentException if userId or encryptionKey is null
     */
    @Transactional(readOnly = true)
    public List<LiabilityResponse> getLiabilitiesByUserId(Long userId) {
        log.debug("Retrieving all liabilities for user {}", userId);

        if (userId == null) {
            throw new IllegalArgumentException("User ID cannot be null");
        }
        // Fetch all liabilities for user (ordered by created_at DESC in repository)
        List<Liability> liabilities = liabilityRepository.findByUserIdOrderByCreatedAtDesc(userId);

        log.debug("Found {} liabilities for user {}", liabilities.size(), userId);

        // Decrypt and map to responses with calculated fields
        return liabilities.stream()
                .map(liability -> toResponseWithDecryption(liability))
                .collect(Collectors.toList());
    }

    /**
     * Retrieves all liabilities of a specific type for a user.
     *
     * <p>Filters liabilities by type (e.g., all mortgages, all credit cards).
     *
     * <p>Requirement REQ-6.1.2: Filter liabilities by type
     *
     * @param userId the ID of the user
     * @param type the liability type to filter by
     * @param encryptionKey the AES-256 encryption key for decrypting sensitive fields
     * @return list of liabilities of the specified type (may be empty)
     * @throws IllegalArgumentException if any parameter is null
     */
    @Transactional(readOnly = true)
    public List<LiabilityResponse> getLiabilitiesByType(Long userId, LiabilityType type) {
        log.debug("Retrieving liabilities for user {} by type {}", userId, type);

        if (userId == null) {
            throw new IllegalArgumentException("User ID cannot be null");
        }
        if (type == null) {
            throw new IllegalArgumentException("Liability type cannot be null");
        }
        // Fetch liabilities by type
        List<Liability> liabilities =
                liabilityRepository.findByUserIdAndTypeOrderByCreatedAtDesc(userId, type);

        log.debug("Found {} liabilities of type {} for user {}", liabilities.size(), type, userId);

        // Decrypt and map to responses with calculated fields
        return liabilities.stream()
                .map(liability -> toResponseWithDecryption(liability))
                .collect(Collectors.toList());
    }

    /**
     * Fields whose values are AES-256-encrypted in the database. Sorting or searching on these
     * fields must be done in memory after decryption; they cannot be delegated to the SQL layer.
     */
    private static final Set<String> ENCRYPTED_SORT_FIELDS =
            Set.of("name", "currentBalance", "principal", "interestRate", "minimumPayment");

    /**
     * Retrieves liabilities with pagination and optional filters.
     *
     * <p>When {@code search} is non-null <em>or</em> the requested sort field is one of the
     * AES-256-encrypted columns, the method falls back to a full in-memory fetch, decrypt, filter
     * and sort cycle before paginating manually. This avoids meaningless LIKE / ORDER BY operations
     * on cipher-text bytes.
     */
    @Transactional(readOnly = true)
    public Page<LiabilityResponse> getLiabilitiesWithFilters(
            Long userId, LiabilityType type, String search, Pageable pageable) {

        log.debug(
                "Retrieving liabilities for user {} with filters: type={}, search={}, page={}, size={}",
                userId,
                type,
                search,
                pageable.getPageNumber(),
                pageable.getPageSize());

        if (userId == null) {
            throw new IllegalArgumentException("User ID cannot be null");
        }
        if (pageable == null) {
            throw new IllegalArgumentException("Pageable cannot be null");
        }
        boolean hasSearch = search != null && !search.isBlank();
        boolean sortOnEncrypted =
                pageable.getSort().stream()
                        .anyMatch(order -> ENCRYPTED_SORT_FIELDS.contains(order.getProperty()));

        if (hasSearch || sortOnEncrypted) {
            // In-memory path: fetch ALL matching liabilities, decrypt, filter, sort, page
            List<Liability> all = liabilityRepository.findAllByUserIdAndType(userId, type);

            List<LiabilityResponse> responses =
                    all.stream().map(l -> toResponseWithDecryption(l)).collect(Collectors.toList());

            // Apply search filter on decrypted name
            if (hasSearch) {
                String searchLower = search.toLowerCase();
                responses =
                        responses.stream()
                                .filter(
                                        r ->
                                                r.getName() != null
                                                        && r.getName()
                                                                .toLowerCase()
                                                                .contains(searchLower))
                                .collect(Collectors.toList());
            }

            // Sort in memory
            Sort sort = pageable.getSort();
            if (sort.isSorted()) {
                Comparator<LiabilityResponse> comparator = null;
                for (Sort.Order order : sort) {
                    Comparator<LiabilityResponse> fc = buildResponseComparator(order.getProperty());
                    if (fc != null) {
                        if (order.isDescending()) {
                            fc = fc.reversed();
                        }
                        comparator = (comparator == null) ? fc : comparator.thenComparing(fc);
                    }
                }
                if (comparator != null) {
                    responses.sort(comparator);
                }
            }

            // Manual pagination
            int total = responses.size();
            int start = (int) pageable.getOffset();
            int end = Math.min(start + pageable.getPageSize(), total);
            List<LiabilityResponse> pageContent =
                    (start < total) ? responses.subList(start, end) : Collections.emptyList();

            log.debug(
                    "In-memory filter/sort: {} total, page {}/{} ({})",
                    total,
                    pageable.getPageNumber() + 1,
                    (total + pageable.getPageSize() - 1) / pageable.getPageSize(),
                    pageContent.size());

            return new PageImpl<>(new ArrayList<>(pageContent), pageable, total);
        }

        // Fast path: push type filter and sort to the database
        Page<Liability> liabilitiesPage =
                liabilityRepository.findByUserIdWithFilters(userId, type, pageable);

        log.debug(
                "Found {} liabilities (page {} of {}) for user {}",
                liabilitiesPage.getTotalElements(),
                liabilitiesPage.getNumber() + 1,
                liabilitiesPage.getTotalPages(),
                userId);

        return liabilitiesPage.map(liability -> toResponseWithDecryption(liability));
    }

    /**
     * Returns a {@link Comparator} for the given {@link LiabilityResponse} field. Returns {@code
     * null} for unknown field names (the sort is silently ignored).
     */
    @SuppressWarnings("unchecked")
    private Comparator<LiabilityResponse> buildResponseComparator(String field) {
        return switch (field) {
            case "name" -> Comparator.comparing(
                    r -> r.getName() != null ? r.getName().toLowerCase() : "",
                    Comparator.naturalOrder());
            case "currentBalance" -> (Comparator<LiabilityResponse>)
                    Comparator.comparing(
                            (LiabilityResponse r) ->
                                    r.getCurrentBalance() != null
                                            ? r.getCurrentBalance()
                                            : BigDecimal.ZERO);
            case "principal" -> (Comparator<LiabilityResponse>)
                    Comparator.comparing(
                            (LiabilityResponse r) ->
                                    r.getPrincipal() != null ? r.getPrincipal() : BigDecimal.ZERO);
            case "interestRate" -> (Comparator<LiabilityResponse>)
                    Comparator.comparing(
                            (LiabilityResponse r) ->
                                    r.getInterestRate() != null
                                            ? r.getInterestRate()
                                            : BigDecimal.ZERO);
            case "minimumPayment" -> (Comparator<LiabilityResponse>)
                    Comparator.comparing(
                            (LiabilityResponse r) ->
                                    r.getMinimumPayment() != null
                                            ? r.getMinimumPayment()
                                            : BigDecimal.ZERO);
            case "startDate" -> (Comparator<LiabilityResponse>)
                    Comparator.comparing(
                            (LiabilityResponse r) ->
                                    r.getStartDate() != null ? r.getStartDate() : LocalDate.MIN);
            case "endDate" -> (Comparator<LiabilityResponse>)
                    Comparator.comparing(
                            (LiabilityResponse r) ->
                                    r.getEndDate() != null ? r.getEndDate() : LocalDate.MAX);
            case "createdAt" -> (Comparator<LiabilityResponse>)
                    Comparator.comparing(
                            (LiabilityResponse r) ->
                                    r.getCreatedAt() != null
                                            ? r.getCreatedAt()
                                            : LocalDateTime.MIN);
            case "type" -> Comparator.comparing(
                    r -> r.getType() != null ? r.getType().name() : "", Comparator.naturalOrder());
            default -> null;
        };
    }

    /**
     * Calculates the total of all liabilities for a user.
     *
     * <p>Sums all current balances across all liabilities. Liabilities in different currencies are
     * NOT converted; they are summed separately.
     *
     * <p>Requirement REQ-6.1.3: Calculate total liabilities
     *
     * @param userId the ID of the user
     * @param encryptionKey the AES-256 encryption key for decrypting balance fields
     * @return map of currency code to total liability amount in that currency
     * @throws IllegalArgumentException if userId or encryptionKey is null
     */
    @Transactional(readOnly = true)
    public Map<String, BigDecimal> calculateTotalLiabilities(Long userId) {
        log.debug("Calculating total liabilities for user {}", userId);

        if (userId == null) {
            throw new IllegalArgumentException("User ID cannot be null");
        }
        // Fetch all liabilities
        List<Liability> liabilities = liabilityRepository.findByUserIdOrderByCreatedAtDesc(userId);

        // Sum current balances by currency
        Map<String, BigDecimal> totalsByCurrency =
                liabilities.stream()
                        .collect(
                                Collectors.groupingBy(
                                        Liability::getCurrency,
                                        Collectors.reducing(
                                                BigDecimal.ZERO,
                                                liability ->
                                                        decryptAmount(
                                                                liability.getCurrentBalance()),
                                                BigDecimal::add)));

        log.info("Total liabilities for user {}: {}", userId, totalsByCurrency);
        return totalsByCurrency;
    }

    /**
     * Calculates the amortization schedule for a liability.
     *
     * <p>Generates a payment-by-payment breakdown showing how much principal and interest is paid
     * with each payment until the loan is paid off.
     *
     * <p>This method requires:
     *
     * <ul>
     *   <li>Current balance (starting balance for calculation)
     *   <li>Interest rate (annual percentage rate)
     *   <li>Minimum payment (fixed monthly payment amount)
     * </ul>
     *
     * <p>If any required field is missing, returns an empty schedule.
     *
     * <p>Requirement REQ-6.1.3: Generate amortization schedules
     *
     * @param liabilityId the ID of the liability
     * @param userId the ID of the user (for authorization)
     * @param encryptionKey the AES-256 encryption key for decrypting fields
     * @return list of amortization schedule entries (may be empty if data is insufficient)
     * @throws ResourceNotFoundException if liability not found or doesn't belong to user
     * @throws IllegalArgumentException if any parameter is null
     */
    @Transactional(readOnly = true)
    public List<AmortizationScheduleEntry> calculateAmortizationSchedule(
            Long liabilityId, Long userId) {
        log.debug(
                "Calculating amortization schedule for liability {}: userId={}",
                liabilityId,
                userId);

        if (liabilityId == null) {
            throw new IllegalArgumentException("Liability ID cannot be null");
        }
        if (userId == null) {
            throw new IllegalArgumentException("User ID cannot be null");
        }
        // Fetch liability and verify ownership
        Liability liability =
                liabilityRepository
                        .findByIdAndUserId(liabilityId, userId)
                        .orElseThrow(
                                () -> LiabilityNotFoundException.byIdAndUser(liabilityId, userId));

        // Decrypt required fields
        BigDecimal currentBalance = decryptAmount(liability.getCurrentBalance());
        BigDecimal interestRate = decryptAmount(liability.getInterestRate());
        BigDecimal minimumPayment = decryptAmount(liability.getMinimumPayment());

        // Validate required fields
        if (currentBalance.compareTo(BigDecimal.ZERO) <= 0) {
            log.warn("Cannot calculate amortization schedule: current balance is zero or negative");
            return new ArrayList<>();
        }

        if (interestRate == null || interestRate.compareTo(BigDecimal.ZERO) < 0) {
            log.warn(
                    "Cannot calculate amortization schedule: interest rate is missing or negative");
            return new ArrayList<>();
        }

        // Calculate missing minimumPayment
        if (minimumPayment == null || minimumPayment.compareTo(BigDecimal.ZERO) <= 0) {
            BigDecimal principal = decryptAmount(liability.getPrincipal());
            if (principal != null
                    && principal.compareTo(BigDecimal.ZERO) > 0
                    && liability.getEndDate() != null
                    && liability.getStartDate() != null) {

                long totalMonths =
                        java.time.temporal.ChronoUnit.MONTHS.between(
                                liability.getStartDate().withDayOfMonth(1),
                                liability.getEndDate().withDayOfMonth(1));

                if (totalMonths > 0) {
                    BigDecimal mRate =
                            interestRate.divide(
                                    BigDecimal.valueOf(MONTHS_PER_YEAR * 100),
                                    SCALE,
                                    RoundingMode.HALF_UP);

                    if (mRate.compareTo(BigDecimal.ZERO) > 0) {
                        try {
                            BigDecimal onePlusRPowN =
                                    mRate.add(BigDecimal.ONE).pow((int) totalMonths);
                            BigDecimal numerator = mRate.multiply(onePlusRPowN);
                            BigDecimal denominator = onePlusRPowN.subtract(BigDecimal.ONE);
                            minimumPayment =
                                    principal
                                            .multiply(numerator)
                                            .divide(denominator, 2, RoundingMode.HALF_UP);
                            log.info(
                                    "Calculated missing minimum payment for liability {}: {} over {} total months using principal {}",
                                    liabilityId,
                                    minimumPayment,
                                    totalMonths,
                                    principal);
                        } catch (ArithmeticException e) {
                            log.warn("Error calculating minimum payment: {}", e.getMessage());
                        }
                    } else {
                        // 0% interest rate
                        minimumPayment =
                                principal.divide(
                                        BigDecimal.valueOf(totalMonths), 2, RoundingMode.HALF_UP);
                    }
                }
            }

            // if it is still missing or zero after auto-calculation
            if (minimumPayment == null || minimumPayment.compareTo(BigDecimal.ZERO) <= 0) {
                log.warn(
                        "Cannot calculate amortization schedule: minimum payment is missing and cannot be auto-calculated");
                return new ArrayList<>();
            }
        }

        // Calculate monthly interest rate (annual rate / 12 / 100)
        BigDecimal monthlyRate =
                interestRate.divide(
                        BigDecimal.valueOf(MONTHS_PER_YEAR * 100), SCALE, RoundingMode.HALF_UP);

        // Check if payment covers interest (avoid infinite loop)
        BigDecimal firstMonthInterest = currentBalance.multiply(monthlyRate);
        if (minimumPayment.compareTo(firstMonthInterest) <= 0) {
            log.warn(
                    "Cannot calculate amortization schedule: minimum payment ({}) does not cover first month interest ({})",
                    minimumPayment,
                    firstMonthInterest);
            return new ArrayList<>();
        }

        // Generate amortization schedule
        List<AmortizationScheduleEntry> schedule = new ArrayList<>();
        BigDecimal remainingBalance = currentBalance;
        LocalDate currentDate = LocalDate.now();
        int paymentNumber = 1;

        BigDecimal cumulativePrincipal = BigDecimal.ZERO;
        BigDecimal cumulativeInterest = BigDecimal.ZERO;

        while (remainingBalance.compareTo(BigDecimal.ZERO) > 0
                && paymentNumber <= MAX_AMORTIZATION_PERIODS) {
            // Calculate interest for this period
            BigDecimal interestPortion =
                    remainingBalance.multiply(monthlyRate).setScale(2, RoundingMode.HALF_UP);

            // Calculate principal portion
            BigDecimal principalPortion = minimumPayment.subtract(interestPortion);

            // Adjust for final payment (don't overpay)
            if (principalPortion.compareTo(remainingBalance) > 0) {
                principalPortion = remainingBalance;
                remainingBalance = BigDecimal.ZERO;
            } else {
                remainingBalance = remainingBalance.subtract(principalPortion);
            }

            // Update cumulative totals
            cumulativePrincipal = cumulativePrincipal.add(principalPortion);
            cumulativeInterest = cumulativeInterest.add(interestPortion);

            // Actual payment amount for this period
            BigDecimal actualPayment = principalPortion.add(interestPortion);

            // Create schedule entry
            AmortizationScheduleEntry entry =
                    AmortizationScheduleEntry.builder()
                            .paymentNumber(paymentNumber)
                            .paymentDate(currentDate)
                            .paymentAmount(actualPayment)
                            .principalPortion(principalPortion)
                            .interestPortion(interestPortion)
                            .remainingBalance(remainingBalance.max(BigDecimal.ZERO))
                            .cumulativePrincipal(cumulativePrincipal)
                            .cumulativeInterest(cumulativeInterest)
                            .build();

            schedule.add(entry);

            // Move to next month
            currentDate = currentDate.plusMonths(1);
            paymentNumber++;
        }

        log.info(
                "Amortization schedule calculated for liability {}: {} payments, total interest: {}",
                liabilityId,
                schedule.size(),
                cumulativeInterest);

        return schedule;
    }

    /**
     * Calculates projected total interest for a liability.
     *
     * <p>This is the sum of all interest portions from the amortization schedule.
     *
     * <p>Requirement REQ-6.1.3: Calculate projected total interest
     *
     * @param liabilityId the ID of the liability
     * @param userId the ID of the user (for authorization)
     * @param encryptionKey the AES-256 encryption key for decrypting fields
     * @return projected total interest, or null if calculation not possible
     * @throws ResourceNotFoundException if liability not found or doesn't belong to user
     * @throws IllegalArgumentException if any parameter is null
     */
    @Transactional(readOnly = true)
    public BigDecimal calculateTotalInterest(Long liabilityId, Long userId) {
        log.debug("Calculating total interest for liability {}: userId={}", liabilityId, userId);

        List<AmortizationScheduleEntry> schedule =
                calculateAmortizationSchedule(liabilityId, userId);

        if (schedule.isEmpty()) {
            return null;
        }

        // Sum all interest portions
        BigDecimal totalInterest =
                schedule.stream()
                        .map(AmortizationScheduleEntry::getInterestPortion)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);

        log.info("Total interest for liability {}: {}", liabilityId, totalInterest);
        return totalInterest;
    }

    /**
     * Calculates the detailed cost breakdown for a liability.
     *
     * <p>Produces a comprehensive breakdown including:
     *
     * <ul>
     *   <li>Principal paid and remaining
     *   <li>Estimated interest already paid
     *   <li>Estimated insurance already paid
     *   <li>Fees paid (additionalFees from the liability record)
     *   <li>Projected remaining interest, insurance, and fees
     *   <li>Summary of linked transactions (payment count and total)
     * </ul>
     *
     * <p>Requirement REQ-LIA-3: Display liability breakdown with cost analysis
     *
     * @param liabilityId the ID of the liability
     * @param userId the ID of the user (for authorization)
     * @param encryptionKey the AES-256 encryption key
     * @return detailed breakdown response
     * @throws LiabilityNotFoundException if liability not found or doesn't belong to user
     * @throws IllegalArgumentException if any parameter is null
     */
    @Transactional(readOnly = true)
    public LiabilityBreakdownResponse getLiabilityBreakdown(Long liabilityId, Long userId) {
        if (liabilityId == null) {
            throw new IllegalArgumentException("Liability ID cannot be null");
        }
        if (userId == null) {
            throw new IllegalArgumentException("User ID cannot be null");
        }
        log.debug("Computing liability breakdown for liability {}: userId={}", liabilityId, userId);

        // Fetch liability and verify ownership (Requirement REQ-3.2: Authorization)
        Liability liability =
                liabilityRepository
                        .findByIdAndUserId(liabilityId, userId)
                        .orElseThrow(
                                () -> LiabilityNotFoundException.byIdAndUser(liabilityId, userId));

        // Read fields (decryption handled by JPA AttributeConverter)
        String decryptedName = liability.getName();
        BigDecimal principal = decryptAmount(liability.getPrincipal());
        BigDecimal currentBalance = decryptAmount(liability.getCurrentBalance());
        BigDecimal interestRate = decryptAmount(liability.getInterestRate());
        BigDecimal insurancePercentage = decryptAmount(liability.getInsurancePercentage());
        BigDecimal additionalFees = decryptAmount(liability.getAdditionalFees());

        // --- Principal paid ---
        BigDecimal principalPaid = principal.subtract(currentBalance);

        // --- Months elapsed and remaining ---
        long monthsElapsed = ChronoUnit.MONTHS.between(liability.getStartDate(), LocalDate.now());
        if (monthsElapsed < 0) monthsElapsed = 0;

        Integer monthsRemaining = null;
        if (liability.getEndDate() != null) {
            int mr = (int) ChronoUnit.MONTHS.between(LocalDate.now(), liability.getEndDate());
            monthsRemaining = Math.max(mr, 0);
        }

        // --- Monthly insurance cost ---
        BigDecimal monthlyInsuranceCost = null;
        if (insurancePercentage != null && insurancePercentage.compareTo(BigDecimal.ZERO) > 0) {
            monthlyInsuranceCost =
                    principal
                            .multiply(insurancePercentage)
                            .divide(BigDecimal.valueOf(100), SCALE, RoundingMode.HALF_UP)
                            .divide(BigDecimal.valueOf(MONTHS_PER_YEAR), 2, RoundingMode.HALF_UP);
        }

        // --- Insurance paid so far ---
        BigDecimal insurancePaid = BigDecimal.ZERO;
        if (monthlyInsuranceCost != null) {
            insurancePaid =
                    monthlyInsuranceCost
                            .multiply(BigDecimal.valueOf(monthsElapsed))
                            .setScale(2, RoundingMode.HALF_UP);
        }

        // --- Projected remaining insurance ---
        BigDecimal projectedInsurance = BigDecimal.ZERO;
        if (monthlyInsuranceCost != null && monthsRemaining != null) {
            projectedInsurance =
                    monthlyInsuranceCost
                            .multiply(BigDecimal.valueOf(monthsRemaining))
                            .setScale(2, RoundingMode.HALF_UP);
        }

        // --- Fees paid / projected fees ---
        // additionalFees is a one-time upfront fee: it has already been paid at loan
        // inception,
        // so it contributes entirely to feesPaid and nothing to projectedFees.
        BigDecimal feesPaid = additionalFees != null ? additionalFees : BigDecimal.ZERO;
        BigDecimal projectedFees = BigDecimal.ZERO;

        // --- Projected remaining interest (from amortization) ---
        List<AmortizationScheduleEntry> schedule =
                calculateAmortizationSchedule(liabilityId, userId);

        BigDecimal projectedInterest = BigDecimal.ZERO;
        BigDecimal estimatedInterestPaid = BigDecimal.ZERO;

        if (!schedule.isEmpty()) {
            projectedInterest =
                    schedule.stream()
                            .map(AmortizationScheduleEntry::getInterestPortion)
                            .reduce(BigDecimal.ZERO, BigDecimal::add);

            // Estimate interest already paid: if we know principalPaid and have rate,
            // approximate using simple interest paid = totalOriginalInterest -
            // projectedInterest
            // We calculate totalOriginalInterest by simulating from the original principal
            // but that is expensive. Instead, use a simpler approximation:
            // interestPaid ≈ (monthly rate) × average balance × months elapsed
            if (interestRate != null && interestRate.compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal monthlyRate =
                        interestRate.divide(
                                BigDecimal.valueOf(MONTHS_PER_YEAR * 100),
                                SCALE,
                                RoundingMode.HALF_UP);
                // Use average of original principal and current balance as approximate average
                // balance
                BigDecimal avgBalance =
                        principal
                                .add(currentBalance)
                                .divide(BigDecimal.valueOf(2), SCALE, RoundingMode.HALF_UP);
                estimatedInterestPaid =
                        avgBalance
                                .multiply(monthlyRate)
                                .multiply(BigDecimal.valueOf(monthsElapsed))
                                .setScale(2, RoundingMode.HALF_UP);
            }
        }

        // --- Total paid so far ---
        BigDecimal totalPaid =
                principalPaid.add(estimatedInterestPaid).add(insurancePaid).add(feesPaid);

        // --- Total projected cost (from today to payoff) ---
        BigDecimal totalProjectedCost =
                currentBalance.add(projectedInterest).add(projectedInsurance).add(projectedFees);

        // --- Linked transactions summary (Requirement REQ-LIA-4) ---
        List<Transaction> linkedTransactions =
                transactionRepository.findByLiabilityIdAndUserId(liabilityId, userId);
        int linkedTransactionCount = linkedTransactions.size();
        BigDecimal linkedTransactionsTotalAmount =
                linkedTransactions.stream()
                        .map(Transaction::getAmount)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);

        log.info(
                "Liability breakdown computed for liability {}: principalPaid={}, projectedInterest={}, linkedTxCount={}",
                liabilityId,
                principalPaid,
                projectedInterest,
                linkedTransactionCount);

        return LiabilityBreakdownResponse.builder()
                .liabilityId(liabilityId)
                .name(decryptedName)
                .currency(liability.getCurrency())
                .principal(principal)
                .currentBalance(currentBalance)
                .principalPaid(principalPaid)
                .interestPaid(estimatedInterestPaid)
                .insurancePaid(insurancePaid)
                .feesPaid(feesPaid)
                .totalPaid(totalPaid)
                .projectedInterest(projectedInterest)
                .projectedInsurance(projectedInsurance)
                .projectedFees(projectedFees)
                .totalProjectedCost(totalProjectedCost)
                .linkedTransactionCount(linkedTransactionCount)
                .linkedTransactionsTotalAmount(linkedTransactionsTotalAmount)
                .build();
    }

    /**
     * Retrieves all transactions linked to a specific liability.
     *
     * <p>Returns transactions where {@code liabilityId} matches the given liability Decrypts
     * sensitive fields using the providing encryption key.
     *
     * @param liabilityId the ID of the liability
     * @param userId the ID of the user (for authorization)
     * @param encryptionKey the AES-256 encryption key for decryption
     * @return list of linked transaction responses (may be empty)
     * @throws LiabilityNotFoundException if liability not found or doesn't belong to user
     * @throws IllegalArgumentException if any parameter is null
     */
    @Transactional(readOnly = true)
    public List<org.openfinance.dto.TransactionResponse> getLinkedTransactions(
            Long liabilityId, Long userId) {
        if (liabilityId == null) {
            throw new IllegalArgumentException("Liability ID cannot be null");
        }
        if (userId == null) {
            throw new IllegalArgumentException("User ID cannot be null");
        }
        log.debug(
                "Retrieving linked transactions for liability {}: userId={}", liabilityId, userId);

        // Verify liability exists and belongs to user (Requirement REQ-3.2:
        // Authorization)
        if (!liabilityRepository.existsByIdAndUserId(liabilityId, userId)) {
            throw LiabilityNotFoundException.byIdAndUser(liabilityId, userId);
        }

        List<Transaction> transactions =
                transactionRepository.findByLiabilityIdAndUserId(liabilityId, userId);

        log.debug(
                "Found {} linked transactions for liability {}", transactions.size(), liabilityId);

        return transactions.stream()
                .map(t -> transactionService.toResponseWithDecryption(t))
                .collect(Collectors.toList());
    }

    // ===========================
    // Private Helper Methods
    // ===========================

    /**
     * Converts a Liability entity to LiabilityResponse with decrypted fields and calculated values.
     *
     * @param liability the liability entity
     * @param encryptionKey the encryption key for decryption
     * @return the liability response DTO with decrypted and calculated fields
     */
    private LiabilityResponse toResponseWithDecryption(Liability liability) {
        // Read fields (decryption handled by JPA AttributeConverter)
        String decryptedName = liability.getName();
        BigDecimal decryptedPrincipal = decryptAmount(liability.getPrincipal());
        BigDecimal decryptedCurrentBalance = decryptAmount(liability.getCurrentBalance());
        BigDecimal decryptedInterestRate = decryptAmount(liability.getInterestRate());
        BigDecimal decryptedMinimumPayment = decryptAmount(liability.getMinimumPayment());
        String decryptedNotes =
                liability.getNotes() != null && !liability.getNotes().isBlank()
                        ? liability.getNotes()
                        : null;
        AccountResponse.InstitutionInfo institutionInfo = null;
        if (liability.getInstitution() != null) {
            org.openfinance.entity.Institution inst = liability.getInstitution();
            institutionInfo =
                    AccountResponse.InstitutionInfo.builder()
                            .id(inst.getId())
                            .name(inst.getName())
                            .bic(inst.getBic())
                            .country(inst.getCountry())
                            .logo(inst.getLogo())
                            .build();
        }

        // Find linked property if applicable
        Long linkedPropertyId = null;
        String linkedPropertyName = null;
        var linkedPropertyOpt = realEstateRepository.findFirstByMortgageId(liability.getId());
        if (linkedPropertyOpt.isPresent()) {
            linkedPropertyId = linkedPropertyOpt.get().getId();
            linkedPropertyName = linkedPropertyOpt.get().getName();
        }

        // Decrypt new optional fields (Requirement REQ-LIA-1, REQ-LIA-2)
        BigDecimal decryptedInsurancePercentage = decryptAmount(liability.getInsurancePercentage());
        BigDecimal decryptedAdditionalFees = decryptAmount(liability.getAdditionalFees());

        // Calculate derived fields
        BigDecimal totalPaid = decryptedPrincipal.subtract(decryptedCurrentBalance);
        BigDecimal payoffPercentage =
                decryptedPrincipal.compareTo(BigDecimal.ZERO) > 0
                        ? totalPaid
                                .divide(decryptedPrincipal, 4, RoundingMode.HALF_UP)
                                .multiply(BigDecimal.valueOf(100))
                        : BigDecimal.ZERO;

        Integer monthsRemaining = null;
        if (liability.getEndDate() != null) {
            monthsRemaining =
                    (int) ChronoUnit.MONTHS.between(LocalDate.now(), liability.getEndDate());
            if (monthsRemaining < 0) monthsRemaining = 0;
        }

        Long liabilityAgeDays = ChronoUnit.DAYS.between(liability.getStartDate(), LocalDate.now());

        // principalPaid = principal - currentBalance (Requirement REQ-LIA-3.4)
        BigDecimal principalPaid = totalPaid;

        // Calculate monthly interest cost
        BigDecimal monthlyInterestCost = null;
        if (decryptedInterestRate != null && decryptedInterestRate.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal monthlyRate =
                    decryptedInterestRate.divide(
                            BigDecimal.valueOf(MONTHS_PER_YEAR * 100), SCALE, RoundingMode.HALF_UP);
            monthlyInterestCost =
                    decryptedCurrentBalance.multiply(monthlyRate).setScale(2, RoundingMode.HALF_UP);
        }

        // Calculate monthly insurance cost (Requirement REQ-LIA-3.2)
        // Formula: principal × (insurancePercentage / 100) / 12
        BigDecimal monthlyInsuranceCost = null;
        BigDecimal totalInsuranceCost = null;
        if (decryptedInsurancePercentage != null
                && decryptedInsurancePercentage.compareTo(BigDecimal.ZERO) > 0) {
            monthlyInsuranceCost =
                    decryptedPrincipal
                            .multiply(decryptedInsurancePercentage)
                            .divide(BigDecimal.valueOf(100), SCALE, RoundingMode.HALF_UP)
                            .divide(BigDecimal.valueOf(MONTHS_PER_YEAR), 2, RoundingMode.HALF_UP);

            // Total insurance cost over remaining months (Requirement REQ-LIA-3.2)
            if (monthsRemaining != null) {
                totalInsuranceCost =
                        monthlyInsuranceCost
                                .multiply(BigDecimal.valueOf(monthsRemaining))
                                .setScale(2, RoundingMode.HALF_UP);
            }
        }

        // For projected total interest, we'd need to call calculateTotalInterest,
        // but that would be recursive/expensive. Instead, we'll calculate it on-demand
        // or set to null.
        // For now, setting to null - client can request amortization schedule
        // separately.

        // Calculate total cost (Requirement REQ-LIA-3.3)
        // totalCost = currentBalance + projectedTotalInterest + totalInsuranceCost +
        // additionalFees
        // Since projectedTotalInterest is null here, totalCost is also null (calculated
        // on breakdown endpoint)
        BigDecimal totalCost = null;

        LiabilityResponse response =
                LiabilityResponse.builder()
                        .id(liability.getId())
                        .userId(liability.getUserId())
                        .name(decryptedName)
                        .type(liability.getType())
                        .principal(decryptedPrincipal)
                        .currentBalance(decryptedCurrentBalance)
                        .interestRate(decryptedInterestRate)
                        .startDate(liability.getStartDate())
                        .endDate(liability.getEndDate())
                        .minimumPayment(decryptedMinimumPayment)
                        .currency(liability.getCurrency())
                        .notes(decryptedNotes)
                        .institution(institutionInfo)
                        .linkedPropertyId(linkedPropertyId)
                        .linkedPropertyName(linkedPropertyName)
                        .insurancePercentage(decryptedInsurancePercentage)
                        .additionalFees(decryptedAdditionalFees)
                        .monthlyInsuranceCost(monthlyInsuranceCost)
                        .totalInsuranceCost(totalInsuranceCost)
                        .totalCost(totalCost)
                        .principalPaid(principalPaid)
                        .createdAt(liability.getCreatedAt())
                        .updatedAt(liability.getUpdatedAt())
                        // Calculated fields
                        .totalPaid(totalPaid)
                        .payoffPercentage(payoffPercentage)
                        .monthsRemaining(monthsRemaining)
                        .liabilityAgeDays(liabilityAgeDays)
                        .projectedTotalInterest(null) // Calculate on-demand via separate endpoint
                        .monthlyInterestCost(monthlyInterestCost)
                        .build();

        // Populate currency conversion metadata (Requirement REQ-3.3, REQ-3.5)
        populateConversionFields(
                response, liability.getUserId(), liability.getCurrency(), decryptedCurrentBalance);

        return response;
    }

    /**
     * Populates currency conversion metadata fields on a LiabilityResponse.
     *
     * <p>Fetches the user's base currency from the database, then attempts to convert the {@code
     * currentBalance} to the base currency using {@link ExchangeRateService}. On failure, falls
     * back to the native amount with {@code isConverted=false}.
     *
     * <p>Also performs secondary currency conversion when the user has a secondary currency
     * configured and it differs from the native currency.
     *
     * <p>Requirement REQ-3.3: LiabilityService populates conversion fields
     *
     * <p>Requirement REQ-3.5: Graceful fallback when conversion unavailable
     *
     * <p>Requirement REQ-3.6: isConverted semantics
     *
     * <p>Requirement REQ-4.3, REQ-4.5: Secondary conversion logic
     *
     * @param response the response DTO to populate
     * @param userId the liability owner's user ID
     * @param nativeCurrency the liability's native currency code (ISO 4217)
     * @param nativeBalance the native current balance
     */
    private void populateConversionFields(
            LiabilityResponse response,
            Long userId,
            String nativeCurrency,
            BigDecimal nativeBalance) {
        User user = userId != null ? userRepository.findById(userId).orElse(null) : null;
        String baseCurrency =
                defaultCurrencyProvider.resolve(user != null ? user.getBaseCurrency() : null);
        String secCurrency = user != null ? user.getSecondaryCurrency() : null;
        response.setBaseCurrency(baseCurrency);

        // Step 1: Base conversion
        boolean needsConversion = nativeCurrency != null && !nativeCurrency.equals(baseCurrency);
        if (!needsConversion || nativeBalance == null) {
            response.setBalanceInBaseCurrency(nativeBalance);
            response.setIsConverted(false);
        } else {
            try {
                BigDecimal rate =
                        exchangeRateService.getExchangeRate(nativeCurrency, baseCurrency, null);
                BigDecimal converted =
                        exchangeRateService.convert(nativeBalance, nativeCurrency, baseCurrency);
                response.setBalanceInBaseCurrency(converted);
                response.setExchangeRate(rate);
                response.setIsConverted(true);
            } catch (Exception e) {
                log.warn(
                        "Currency conversion failed for liability (user={}, {}->{}) – falling back to native: {}",
                        userId,
                        nativeCurrency,
                        baseCurrency,
                        e.getMessage());
                response.setBalanceInBaseCurrency(nativeBalance);
                response.setIsConverted(false);
            }
        }

        // Step 2: Secondary conversion (Requirement REQ-4.3, REQ-4.5)
        if (secCurrency != null
                && !secCurrency.isBlank()
                && nativeCurrency != null
                && !nativeCurrency.equals(secCurrency)
                && nativeBalance != null) {
            try {
                BigDecimal secRate =
                        exchangeRateService.getExchangeRate(nativeCurrency, secCurrency, null);
                BigDecimal secAmount =
                        exchangeRateService.convert(nativeBalance, nativeCurrency, secCurrency);
                response.setBalanceInSecondaryCurrency(secAmount);
                response.setSecondaryCurrency(secCurrency);
                response.setSecondaryExchangeRate(secRate);
            } catch (Exception e) {
                log.warn(
                        "Secondary currency conversion failed for liability (user={}, {}->{}) – omitting: {}",
                        userId,
                        nativeCurrency,
                        secCurrency,
                        e.getMessage());
                response.setSecondaryCurrency(secCurrency);
            }
        } else if (secCurrency != null && !secCurrency.isBlank()) {
            response.setSecondaryCurrency(secCurrency);
        }
    }

    /**
     * Decrypts an encrypted BigDecimal amount field.
     *
     * @param encryptedValue the encrypted value (may be null or empty)
     * @param encryptionKey the encryption key
     * @return the decrypted BigDecimal, or null if input is null/empty
     */
    private BigDecimal decryptAmount(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return new BigDecimal(value);
    }

    /**
     * Invalidates net worth snapshots from {@code fromDate} onward (up to today). Called after any
     * liability write so the dashboard chart rebuilds affected months.
     */
    private void invalidateSnapshotsFrom(Long userId, LocalDate fromDate) {
        if (fromDate == null) return;
        try {
            int deleted =
                    netWorthRepository.deleteByUserIdAndSnapshotDateBetween(
                            userId, fromDate.withDayOfMonth(1), LocalDate.now());
            if (deleted > 0) {
                log.debug(
                        "Invalidated {} net worth snapshots for user {} (liability change from {})",
                        deleted,
                        userId,
                        fromDate);
            }
        } catch (Exception e) {
            log.warn(
                    "Could not invalidate net worth snapshots for user {} after liability change from {}: {}",
                    userId,
                    fromDate,
                    e.getMessage());
        }
    }

    private Long resolveCurrencyId(String currencyCode) {
        if (currencyCode == null || currencyCode.isBlank()) return null;
        return currencyRepository
                .findByCode(currencyCode)
                .map(org.openfinance.entity.Currency::getId)
                .orElse(null);
    }

    private void indexLiabilitySearchTokens(Liability liability, String name) {
        try {
            javax.crypto.SecretKey key = org.openfinance.security.EncryptionContext.getKey();
            if (key == null) {
                return;
            }
            javax.crypto.SecretKey searchKey = searchTokenService.deriveSearchKey(key);
            searchTokenService.indexEntity(
                    liability.getUserId(),
                    "LIABILITY",
                    liability.getId(),
                    java.util.List.<String[]>of(new String[] {"name", name}),
                    searchKey);
        } catch (Exception e) {
            log.warn(
                    "Failed to index liability {} search tokens: {}",
                    liability.getId(),
                    e.getMessage());
        }
    }
}
