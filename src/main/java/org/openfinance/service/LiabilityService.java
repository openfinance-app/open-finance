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
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.openfinance.dto.AccountResponse;
import org.openfinance.dto.AmortizationScheduleEntry;
import org.openfinance.dto.DisbursementRequest;
import org.openfinance.dto.LiabilityBreakdownResponse;
import org.openfinance.dto.LiabilityRequest;
import org.openfinance.dto.LiabilityResponse;
import org.openfinance.dto.LiabilityTrancheRequest;
import org.openfinance.dto.LiabilityTrancheResponse;
import org.openfinance.dto.RepaymentPreviewResponse;
import org.openfinance.dto.TransactionRequest;
import org.openfinance.entity.Account;
import org.openfinance.entity.Liability;
import org.openfinance.entity.LiabilityTranche;
import org.openfinance.entity.LiabilityType;
import org.openfinance.entity.MovementType;
import org.openfinance.entity.RealEstateProperty;
import org.openfinance.entity.RealEstateValueHistory;
import org.openfinance.entity.TrancheStatus;
import org.openfinance.entity.Transaction;
import org.openfinance.entity.TransactionType;
import org.openfinance.exception.InvalidLiabilityStateException;
import org.openfinance.exception.InvalidTransactionException;
import org.openfinance.exception.LiabilityNotFoundException;
import org.openfinance.exception.RealEstatePropertyNotFoundException;
import org.openfinance.exception.ResourceNotFoundException;
import org.openfinance.repository.AccountRepository;
import org.openfinance.repository.CurrencyRepository;
import org.openfinance.repository.LiabilityRepository;
import org.openfinance.repository.LiabilityTrancheRepository;
import org.openfinance.repository.NetWorthRepository;
import org.openfinance.repository.RealEstateRepository;
import org.openfinance.repository.RealEstateValueHistoryRepository;
import org.openfinance.repository.TransactionRepository;
import org.openfinance.repository.UserRepository;
import org.openfinance.security.EncryptionService;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Caching;
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
    private final org.openfinance.repository.AssetRepository backingAssetRepository;
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
    private final CurrencyConversionHelper currencyConversionHelper;
    private final LiabilityTrancheRepository liabilityTrancheRepository;
    private final AssetFinancingService assetFinancingService;
    private final AccountRepository accountRepository;
    private final RealEstateValueHistoryRepository realEstateValueHistoryRepository;
    private final LiabilityTrancheService liabilityTrancheService;

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
    @Caching(
            evict = {
                @CacheEvict(
                        value = {"dashboardSummary", "netWorthSummary", "networthAllocation"},
                        key = "#userId"),
                @CacheEvict(
                        value = {"portfolioPerformance", "borrowingCapacity"},
                        allEntries = true)
            })
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
        liability.setCreditLimit(request.getCreditLimit());
        configureAccountSource(userId, liability, request);
        if (liability.getId() == null || !isBalanceLocked(liability)) {
            liability.setOpeningBalance(request.getCurrentBalance());
            liability.setOpeningPrincipal(
                    request.getCurrentBalance().signum() > 0
                                    || Boolean.TRUE.equals(request.getPreviouslyFunded())
                            ? request.getPrincipal().max(request.getCurrentBalance())
                            : BigDecimal.ZERO);
        }

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
                            .findVisibleById(request.getInstitutionId(), userId)
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

        if (request.getRealEstateId() != null) {
            updatePrimaryProperty(userId, savedLiability.getId(), request.getRealEstateId());
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
    @Caching(
            evict = {
                @CacheEvict(
                        value = {"dashboardSummary", "netWorthSummary", "networthAllocation"},
                        key = "#userId"),
                @CacheEvict(
                        value = {"portfolioPerformance", "borrowingCapacity"},
                        allEntries = true)
            })
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

        // Balance-lock guard (spec §4): once linked transactions exist, the balance is owned by
        // those movements and the tranche reconciler — manual edits are rejected. DRAWN tranches
        // alone (direct-route loans, no transactions) also lock the balance: it is derived from
        // the DRAWN tranches and a manual edit would break the spec §3.2 invariant. PLANNED-only
        // tranches do not lock: nothing has been drawn yet, so the manual balance stays
        // authoritative.
        BigDecimal requestedBalance = request.getCurrentBalance();
        BigDecimal existingBalance = currentDebt(liability);
        boolean balanceChanged =
                requestedBalance != null
                        && (existingBalance == null
                                || requestedBalance.compareTo(existingBalance) != 0);
        boolean hasLinkedTransactions =
                !transactionRepository.findByLiabilityIdAndUserId(liabilityId, userId).isEmpty();
        boolean hasDrawnTranches =
                liabilityTrancheRepository.findByLiabilityIdAndUserId(liabilityId, userId).stream()
                        .anyMatch(t -> t.getDrawnAmount() != null);
        if (balanceChanged
                && (hasLinkedTransactions
                        || hasDrawnTranches
                        || liability.getRepresentedByAccountId() != null)) {
            throw InvalidLiabilityStateException.liabilityBalanceLocked(liabilityId);
        }

        if (!liability.getCurrency().equalsIgnoreCase(request.getCurrency())) {
            if (hasLinkedTransactions
                    || hasDrawnTranches
                    || liability.getRepresentedByAccountId() != null) {
                throw new InvalidTransactionException(
                        "A loan with recorded borrowing history cannot change currency; record a separate refinancing loan to change denomination");
            }
            for (LiabilityTranche tranche :
                    liabilityTrancheRepository.findByLiabilityIdAndUserId(liabilityId, userId)) {
                tranche.setCurrency(request.getCurrency());
                liabilityTrancheRepository.save(tranche);
            }
        }

        if (request.isRealEstateIdPresent() || request.getRealEstateId() != null) {
            updatePrimaryProperty(userId, liabilityId, request.getRealEstateId());
        }

        if (liability.getRepresentedByAccountId() != null
                && request.getType() != LiabilityType.CREDIT_CARD) {
            throw new InvalidTransactionException(
                    "A liability backed by a credit-card account must remain a credit card");
        }

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
        liability.setCreditLimit(request.getCreditLimit());
        configureAccountSource(userId, liability, request);
        if (liability.getId() == null || !isBalanceLocked(liability)) {
            liability.setOpeningBalance(request.getCurrentBalance());
            liability.setOpeningPrincipal(
                    request.getCurrentBalance().signum() > 0
                                    || Boolean.TRUE.equals(request.getPreviouslyFunded())
                            ? request.getPrincipal().max(request.getCurrentBalance())
                            : BigDecimal.ZERO);
        }

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
                            .findVisibleById(request.getInstitutionId(), userId)
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
    @Caching(
            evict = {
                @CacheEvict(
                        value = {"dashboardSummary", "netWorthSummary", "networthAllocation"},
                        key = "#userId"),
                @CacheEvict(
                        value = {"portfolioPerformance", "borrowingCapacity"},
                        allEntries = true)
            })
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

        // Deletion guard: a liability with disbursed money (DRAWN tranches) or linked
        // transactions owns historical movements — deleting it would orphan them.
        List<LiabilityTranche> tranches =
                liabilityTrancheRepository.findByLiabilityIdAndUserId(liabilityId, userId);
        if (tranches.stream().anyMatch(t -> t.getDrawnAmount() != null)) {
            log.warn("Blocked liability deletion {}: DRAWN tranches exist", liabilityId);
            throw InvalidLiabilityStateException.liabilityDeletionBlocked(liabilityId);
        }
        if (!transactionRepository.findByLiabilityIdAndUserId(liabilityId, userId).isEmpty()) {
            log.warn("Blocked liability deletion {}: linked transactions exist", liabilityId);
            throw InvalidLiabilityStateException.liabilityDeletionBlocked(liabilityId);
        }

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
            Long userId,
            LiabilityType type,
            String search,
            boolean searchRegex,
            Pageable pageable) {

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
                responses =
                        responses.stream()
                                .filter(
                                        r ->
                                                org.openfinance.util.RegexSearchUtil.matches(
                                                        r.getName(), search, searchRegex))
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
        if (liabilityId == null) throw new IllegalArgumentException("Liability ID cannot be null");
        if (userId == null) throw new IllegalArgumentException("User ID cannot be null");

        // Fetch liability and verify ownership
        Liability liability =
                liabilityRepository
                        .findByIdAndUserId(liabilityId, userId)
                        .orElseThrow(
                                () -> LiabilityNotFoundException.byIdAndUser(liabilityId, userId));

        // Decrypt + validate balance/rate/minimum payment; empty schedule when insufficient.
        ScheduleInputs inputs = resolveScheduleInputs(liability, liabilityId, userId);
        if (inputs == null) {
            return new ArrayList<>();
        }

        // Two-phase support (Task 9): a DRAWN interest-only tranche whose window is still open
        // suppresses the principal during that period.
        InterestOnlyWindow window =
                resolveInterestOnlyWindow(
                        liabilityTrancheRepository.findByLiabilityIdAndUserId(liabilityId, userId));
        List<AmortizationScheduleEntry> schedule =
                buildSchedule(inputs, window, monthlyInsuranceOf(liability));

        log.info(
                "Amortization schedule calculated for liability {}: {} payments, total interest: {}",
                liabilityId,
                schedule.size(),
                totalInterestOf(schedule));

        return schedule;
    }

    /** Validated inputs of an amortization schedule computation. */
    private record ScheduleInputs(
            BigDecimal balance, BigDecimal minimumPayment, BigDecimal monthlyRate) {}

    /** Total interest of a generated schedule (sum of the per-row interest portions). */
    private static BigDecimal totalInterestOf(List<AmortizationScheduleEntry> schedule) {
        return schedule.stream()
                .map(AmortizationScheduleEntry::getInterestPortion)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /**
     * Builds the schedule rows, or an empty list when the minimum payment cannot cover the first
     * month's interest outside an interest-only window (avoids an infinite loop). Inside a window
     * the payment is the computed interest itself, so the check only applies once the schedule
     * amortizes; the row loop stops at the window end when the payment cannot cover interest.
     */
    private List<AmortizationScheduleEntry> buildSchedule(
            ScheduleInputs inputs, InterestOnlyWindow window, BigDecimal monthlyInsurance) {
        LocalDate firstPaymentDate = LocalDate.now();
        BigDecimal firstMonthInterest = inputs.balance().multiply(inputs.monthlyRate());
        if (!window.activeOn(firstPaymentDate)
                && inputs.minimumPayment().compareTo(firstMonthInterest) <= 0) {
            log.warn(
                    "Cannot calculate amortization schedule: minimum payment ({}) does not cover"
                            + " first month interest ({})",
                    inputs.minimumPayment(),
                    firstMonthInterest);
            return new ArrayList<>();
        }
        return generateScheduleRows(inputs, monthlyInsurance, window, firstPaymentDate);
    }

    /**
     * Decrypts and validates the fields an amortization schedule needs: balance, interest rate and
     * minimum payment (auto-calculated from principal and term when missing). Returns null, with a
     * WARN log explaining why, when the schedule cannot be computed.
     */
    private ScheduleInputs resolveScheduleInputs(
            Liability liability, Long liabilityId, Long userId) {
        BigDecimal currentBalance = currentDebt(liability);
        BigDecimal interestRate = decryptAmount(liability.getInterestRate());
        BigDecimal minimumPayment = decryptAmount(liability.getMinimumPayment());

        if (currentBalance.compareTo(BigDecimal.ZERO) <= 0) {
            log.warn("Cannot calculate amortization schedule: current balance is zero or negative");
            return null;
        }
        if (interestRate == null || interestRate.compareTo(BigDecimal.ZERO) < 0) {
            log.warn(
                    "Cannot calculate amortization schedule: interest rate is missing or negative");
            return null;
        }
        if (minimumPayment == null || minimumPayment.compareTo(BigDecimal.ZERO) <= 0) {
            minimumPayment = autoCalculatedMinimumPayment(liability, liabilityId, interestRate);
            if (minimumPayment == null) {
                log.warn(
                        "Cannot calculate amortization schedule: minimum payment is missing and"
                                + " cannot be auto-calculated");
                return null;
            }
        }

        // Calculate monthly interest rate (annual rate / 12 / 100)
        BigDecimal monthlyRate =
                interestRate.divide(
                        BigDecimal.valueOf(MONTHS_PER_YEAR * 100), SCALE, RoundingMode.HALF_UP);
        return new ScheduleInputs(currentBalance, minimumPayment, monthlyRate);
    }

    /** Resolved interest-only window of a liability's DRAWN interest-only tranches. */
    private record InterestOnlyWindow(LocalDate interestOnlyUntil, boolean openEnded) {

        /** Whether the window suppresses principal on the given payment date. */
        boolean activeOn(LocalDate date) {
            return openEnded || (interestOnlyUntil != null && !date.isAfter(interestOnlyUntil));
        }
    }

    /**
     * Resolves the effective interest-only window once from the tranches (same predicate as {@code
     * getRepaymentPreview}): open-ended when any qualifying tranche has no {@code
     * interestOnlyUntil}, otherwise the latest {@code interestOnlyUntil} among them.
     */
    private static InterestOnlyWindow resolveInterestOnlyWindow(List<LiabilityTranche> tranches) {
        LocalDate interestOnlyUntil = null;
        for (LiabilityTranche tranche : tranches) {
            if (tranche.getStatus() == TrancheStatus.DRAWN && tranche.isInterestOnly()) {
                if (tranche.getInterestOnlyUntil() == null) {
                    return new InterestOnlyWindow(null, true);
                }
                if (interestOnlyUntil == null
                        || tranche.getInterestOnlyUntil().isAfter(interestOnlyUntil)) {
                    interestOnlyUntil = tranche.getInterestOnlyUntil();
                }
            }
        }
        return new InterestOnlyWindow(interestOnlyUntil, false);
    }

    /**
     * Monthly insurance (principal × percentage / 1200) added to interest-only window payments when
     * set — same formula as the repayment preview; zero when either field is missing or not
     * positive.
     */
    private BigDecimal monthlyInsuranceOf(Liability liability) {
        BigDecimal insurancePercentage = decryptAmount(liability.getInsurancePercentage());
        BigDecimal principalAmount = decryptAmount(liability.getPrincipal());
        if (insurancePercentage == null
                || insurancePercentage.compareTo(BigDecimal.ZERO) <= 0
                || principalAmount == null
                || principalAmount.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }
        return principalAmount
                .multiply(insurancePercentage)
                .divide(BigDecimal.valueOf(MONTHS_PER_YEAR * 100), 2, RoundingMode.HALF_UP);
    }

    /**
     * Auto-calculates the missing minimum payment from the principal and term using the annuity
     * formula (plain division at 0% interest), or null when it cannot be derived.
     */
    private BigDecimal autoCalculatedMinimumPayment(
            Liability liability, Long liabilityId, BigDecimal interestRate) {
        BigDecimal principal = decryptAmount(liability.getPrincipal());
        if (principal == null
                || principal.compareTo(BigDecimal.ZERO) <= 0
                || liability.getEndDate() == null
                || liability.getStartDate() == null) {
            return null;
        }

        long totalMonths =
                ChronoUnit.MONTHS.between(
                        liability.getStartDate().withDayOfMonth(1),
                        liability.getEndDate().withDayOfMonth(1));
        if (totalMonths <= 0) {
            return null;
        }

        BigDecimal mRate =
                interestRate.divide(
                        BigDecimal.valueOf(MONTHS_PER_YEAR * 100), SCALE, RoundingMode.HALF_UP);
        if (mRate.compareTo(BigDecimal.ZERO) > 0) {
            try {
                BigDecimal onePlusRPowN = mRate.add(BigDecimal.ONE).pow((int) totalMonths);
                BigDecimal numerator = mRate.multiply(onePlusRPowN);
                BigDecimal denominator = onePlusRPowN.subtract(BigDecimal.ONE);
                BigDecimal minimumPayment =
                        principal.multiply(numerator).divide(denominator, 2, RoundingMode.HALF_UP);
                log.info(
                        "Calculated missing minimum payment for liability {}: {} over {} total"
                                + " months using principal {}",
                        liabilityId,
                        minimumPayment,
                        totalMonths,
                        principal);
                return minimumPayment;
            } catch (ArithmeticException e) {
                log.warn("Error calculating minimum payment: {}", e.getMessage());
                return null;
            }
        }
        // 0% interest rate
        return principal.divide(BigDecimal.valueOf(totalMonths), 2, RoundingMode.HALF_UP);
    }

    /**
     * Generates the schedule rows: while the interest-only window is active, payment = interest (+
     * monthly insurance) with principal 0 and a flat balance (Phase 1); afterwards, normal
     * principal &amp; interest rows until the balance is cleared or {@code
     * MAX_AMORTIZATION_PERIODS} is reached (Phase 2).
     */
    private List<AmortizationScheduleEntry> generateScheduleRows(
            ScheduleInputs inputs,
            BigDecimal monthlyInsurance,
            InterestOnlyWindow window,
            LocalDate firstPaymentDate) {
        List<AmortizationScheduleEntry> schedule = new ArrayList<>();
        BigDecimal remainingBalance = inputs.balance();
        LocalDate currentDate = firstPaymentDate;
        int paymentNumber = 1;

        BigDecimal cumulativePrincipal = BigDecimal.ZERO;
        BigDecimal cumulativeInterest = BigDecimal.ZERO;

        while (remainingBalance.compareTo(BigDecimal.ZERO) > 0
                && paymentNumber <= MAX_AMORTIZATION_PERIODS) {
            // Calculate interest for this period
            BigDecimal interestPortion =
                    remainingBalance
                            .multiply(inputs.monthlyRate())
                            .setScale(2, RoundingMode.HALF_UP);
            boolean interestOnlyRow = window.activeOn(currentDate);

            BigDecimal principalPortion;
            BigDecimal actualPayment;

            if (interestOnlyRow) {
                // Interest-only window (Phase 1): payment = interest (+ monthly insurance)
                principalPortion = BigDecimal.ZERO;
                actualPayment = interestPortion.add(monthlyInsurance);
            } else {
                // Phase 2: normal principal & interest row
                principalPortion = inputs.minimumPayment().subtract(interestPortion);

                if (principalPortion.compareTo(BigDecimal.ZERO) <= 0) {
                    log.warn(
                            "Stopping amortization schedule at payment {}: minimum payment ({})"
                                    + " does not cover interest ({})",
                            paymentNumber,
                            inputs.minimumPayment(),
                            interestPortion);
                    break;
                }

                // Adjust for final payment (don't overpay)
                if (principalPortion.compareTo(remainingBalance) > 0) {
                    principalPortion = remainingBalance;
                    remainingBalance = BigDecimal.ZERO;
                } else {
                    remainingBalance = remainingBalance.subtract(principalPortion);
                }

                // Actual payment amount for this period
                actualPayment = principalPortion.add(interestPortion);
            }

            // Update cumulative totals
            cumulativePrincipal = cumulativePrincipal.add(principalPortion);
            cumulativeInterest = cumulativeInterest.add(interestPortion);

            schedule.add(
                    AmortizationScheduleEntry.builder()
                            .paymentNumber(paymentNumber)
                            .paymentDate(currentDate)
                            .paymentAmount(actualPayment)
                            .principalPortion(principalPortion)
                            .interestPortion(interestPortion)
                            .remainingBalance(remainingBalance.max(BigDecimal.ZERO))
                            .cumulativePrincipal(cumulativePrincipal)
                            .cumulativeInterest(cumulativeInterest)
                            .interestOnlyPhase(interestOnlyRow)
                            .build());

            // Move to next month
            currentDate = currentDate.plusMonths(1);
            paymentNumber++;
        }
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

        if (liability.getRepresentedByAccountId() != null) {
            throw new InvalidTransactionException(
                    "Review payments and charges in the linked credit-card account; term-loan cost breakdown is not available for this balance source");
        }

        // Read fields (decryption handled by JPA AttributeConverter)
        String decryptedName = liability.getName();
        BigDecimal principal = decryptAmount(liability.getPrincipal());
        BigDecimal currentBalance = currentDebt(liability);
        BigDecimal interestRate = decryptAmount(liability.getInterestRate());
        BigDecimal insurancePercentage = decryptAmount(liability.getInsurancePercentage());
        BigDecimal additionalFees = decryptAmount(liability.getAdditionalFees());

        // --- Principal paid ---
        BigDecimal principalPaid = recordedPrincipalPaid(liability);

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
                        .map(
                                t ->
                                        t.getOriginalCurrency() != null
                                                        && t.getConversionRate() != null
                                                ? t.getOriginalAmount()
                                                : t.getAmount())
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
    // Disbursement & Tranches (Task 4)
    // ===========================

    /**
     * Disburses a tranche of a staged liability.
     *
     * <p>The routing target decides how money moves:
     *
     * <ul>
     *   <li><strong>toAccountId</strong> (bank paid into the user's account): the movement is
     *       delegated to {@link TransactionService#createTransaction} with {@code
     *       movementType=DISBURSEMENT}. The transaction-side sync hook is the <em>single owner</em>
     *       of the liability and account balance legs — this method must NOT adjust the liability
     *       balance itself, or the amount would be counted twice.
     *   <li><strong>directRealEstateId</strong> (bank paid the seller/property directly): the funds
     *       never touch a user account, so no Transaction row is created (spec §4
     *       DISBURSEMENT_DIRECT has no account leg). The liability balance is increased inline, the
     *       property's current value is bumped with a value-history entry, and tracking happens via
     *       the tranche (Drawdowns tab) plus the property history.
     * </ul>
     *
     * <p>In both cases the resolved tranche is marked DRAWN <em>before</em> any balance change, so
     * the sync-side clamp (liability balance ≤ SUM(drawnAmount of DRAWN tranches)) already counts
     * this drawdown.
     *
     * @param userId the ID of the user disbursing (for authorization)
     * @param liabilityId the ID of the liability being drawn
     * @param request the disbursement request (exactly one routing target required)
     * @return the liability after the disbursement was applied
     * @throws LiabilityNotFoundException if the liability does not belong to the user
     * @throws InvalidTransactionException if the account is not owned, its currency differs from
     *     the liability currency, or the tranche is not PLANNED
     * @throws InvalidLiabilityStateException if the liability balance exceeds the total drawn
     *     amount of its DRAWN tranches (reconcile first), or the amount exceeds the tranche's
     *     planned amount
     */
    @CacheEvict(
            value = {
                "dashboardSummary",
                "netWorthSummary",
                "networthAllocation",
                "portfolioPerformance",
                "borrowingCapacity"
            },
            allEntries = true)
    public LiabilityResponse disburse(Long userId, Long liabilityId, DisbursementRequest request) {
        Liability liability =
                liabilityRepository
                        .findByIdAndUserId(liabilityId, userId)
                        .orElseThrow(
                                () -> LiabilityNotFoundException.byIdAndUser(liabilityId, userId));

        if (liability.getRepresentedByAccountId() != null) {
            throw new InvalidTransactionException(
                    "Record borrowing and repayments in the linked credit-card account");
        }
        // Fail fast on a contradictory state (shared with the raw transaction path): the
        // balance must never exceed what the DRAWN tranches account for, otherwise this
        // drawdown would silently rely on untracked money.
        liabilityTrancheService.assertDisbursementAllowed(liability, userId);

        LiabilityTranche tranche = resolveTrancheForDisbursement(userId, liability, request);
        if (request.getAmount().compareTo(tranche.getPlannedAmount()) > 0) {
            throw InvalidLiabilityStateException.disbursementOverdraw(
                    request.getAmount(), tranche.getId(), tranche.getPlannedAmount());
        }
        org.openfinance.util.LoanPostingPolicy.validateDate(liability, request.getDate());
        tranche.setDirectDisbursement(request.getDirectRealEstateId() != null);
        tranche.setDrawnAmount(request.getAmount());
        tranche.setDrawnDate(request.getDate());
        if (request.getDirectRealEstateId() != null) {
            tranche.setRealEstateId(request.getDirectRealEstateId());
        }
        if (request.getNotes() != null && !request.getNotes().isBlank()) {
            tranche.setNotes(request.getNotes());
        }
        tranche.setStatus(TrancheStatus.DRAWN);
        liabilityTrancheRepository.save(tranche);

        if (request.getToAccountId() != null) {
            disburseToAccount(userId, liability, tranche, request);
        } else {
            disburseDirectly(userId, liability, tranche, request);
            invalidateSnapshotsFrom(userId, liability.getStartDate());
        }
        return toResponseWithDecryption(liability);
    }

    /**
     * Resolves which tranche a disbursement draws: the explicitly requested one (validated for
     * ownership, liability membership and PLANNED status), else the next PLANNED tranche by {@code
     * trancheNo}, else a freshly created single tranche planned for the requested amount.
     */
    private LiabilityTranche resolveTrancheForDisbursement(
            Long userId, Liability liability, DisbursementRequest request) {
        if (request.getTrancheId() != null) {
            LiabilityTranche tranche =
                    liabilityTrancheRepository
                            .findByIdAndUserId(request.getTrancheId(), userId)
                            .orElseThrow(
                                    () ->
                                            new ResourceNotFoundException(
                                                    String.format(
                                                            "Tranche with ID %d not found",
                                                            request.getTrancheId())));
            if (!tranche.getLiabilityId().equals(liability.getId())) {
                throw new InvalidTransactionException(
                        String.format(
                                "Tranche %d does not belong to liability %d",
                                tranche.getId(), liability.getId()));
            }
            if (tranche.getStatus() != TrancheStatus.PLANNED) {
                throw new InvalidTransactionException(
                        String.format(
                                "Tranche %d is not PLANNED (status: %s)",
                                tranche.getId(), tranche.getStatus()));
            }
            return tranche;
        }
        return liabilityTrancheRepository
                .findByLiabilityIdAndUserId(liability.getId(), userId)
                .stream()
                .filter(t -> t.getStatus() == TrancheStatus.PLANNED)
                .min(Comparator.comparing(LiabilityTranche::getTrancheNo))
                .orElseGet(
                        () ->
                                LiabilityTranche.builder()
                                        .userId(userId)
                                        .liabilityId(liability.getId())
                                        .trancheNo(nextTrancheNo(liability.getId(), userId))
                                        .plannedAmount(request.getAmount())
                                        .plannedDate(request.getDate())
                                        .status(TrancheStatus.PLANNED)
                                        .currency(liability.getCurrency())
                                        .build());
    }

    /** Bank-paid-my-account route: a single INCOME DISBURSEMENT transaction owns both legs. */
    private void disburseToAccount(
            Long userId,
            Liability liability,
            LiabilityTranche tranche,
            DisbursementRequest request) {
        Account account =
                accountRepository
                        .findByIdAndUserId(request.getToAccountId(), userId)
                        .orElseThrow(
                                () ->
                                        InvalidTransactionException.accountNotOwnedByUser(
                                                request.getToAccountId(), userId));
        if (account.getCurrency() != null
                && liability.getCurrency() != null
                && !account.getCurrency().equalsIgnoreCase(liability.getCurrency())) {
            throw InvalidTransactionException.currencyMismatch(
                    account.getCurrency(), liability.getCurrency());
        }
        TransactionRequest txRequest =
                TransactionRequest.builder()
                        .accountId(request.getToAccountId())
                        .type(TransactionType.INCOME)
                        .amount(request.getAmount())
                        .currency(liability.getCurrency())
                        .date(request.getDate())
                        .description("Disbursement T" + tranche.getTrancheNo())
                        .notes(request.getNotes())
                        .movementType(MovementType.DISBURSEMENT)
                        .liabilityId(liability.getId())
                        .trancheId(tranche.getId())
                        .build();
        transactionService.createTransaction(userId, txRequest);
    }

    /** Direct-to-property route: no account leg, no transaction row (spec §4). */
    private void disburseDirectly(
            Long userId,
            Liability liability,
            LiabilityTranche tranche,
            DisbursementRequest request) {
        RealEstateProperty property =
                realEstateRepository
                        .findByIdAndUserId(request.getDirectRealEstateId(), userId)
                        .orElseThrow(
                                () ->
                                        RealEstatePropertyNotFoundException.byIdAndUser(
                                                request.getDirectRealEstateId(), userId));
        if (property.getCurrency() != null
                && liability.getCurrency() != null
                && !property.getCurrency().equalsIgnoreCase(liability.getCurrency())) {
            throw InvalidTransactionException.currencyMismatch(
                    property.getCurrency(), liability.getCurrency());
        }

        if (property.getAssetId() == null || !property.isActive()) {
            throw new InvalidTransactionException("Direct funding requires an active property");
        }
        assetFinancingService.ensureDirectFinancing(
                userId, liability.getId(), property.getAssetId());

        BigDecimal current = currentDebt(liability);
        BigDecimal updated = (current == null ? BigDecimal.ZERO : current).add(request.getAmount());
        liability.setCurrentBalance(updated.toPlainString());
        liabilityTrancheService.reconcile(liability);
        liabilityRepository.save(liability);
        // The reconciler may override the delta-applied intermediate, so log the final
        // post-reconcile balance, never the intermediate.
        String finalBalance = liability.getCurrentBalance();

        if (property.getAcquisitionType() == org.openfinance.entity.AcquisitionType.PLANNED) {
            throw new InvalidTransactionException(
                    "Complete the property's acquisition details before disbursing directly to it");
        }
        // A direct draw supplies the latest funded valuation; the agreed purchase price stays
        // fixed.
        // Liability principal remains cumulative across the separate tranches.
        BigDecimal updatedValue = request.getAmount();
        property.setCurrentValue(updatedValue.toPlainString());
        BigDecimal purchasePrice = property.getPurchasePriceDecimal();
        BigDecimal updatedPurchase = purchasePrice == null ? BigDecimal.ZERO : purchasePrice;
        RealEstateProperty savedProperty = realEstateRepository.save(property);
        if (savedProperty.getAssetId() != null) {
            org.openfinance.entity.Asset asset =
                    backingAssetRepository
                            .findByIdAndUserId(savedProperty.getAssetId(), userId)
                            .orElseThrow();
            asset.setCurrentPrice(updatedValue);
            asset.setAcquisitionType(savedProperty.getAcquisitionType());
            asset.setPurchaseDate(savedProperty.getPurchaseDate());
            asset.setPurchasePrice(updatedPurchase);
            backingAssetRepository.save(asset);
        }

        realEstateValueHistoryRepository.save(
                RealEstateValueHistory.builder()
                        .propertyId(savedProperty.getId())
                        .sourceTrancheId(tranche.getId())
                        .userId(savedProperty.getUserId())
                        .effectiveDate(request.getDate())
                        .recordedValue(updatedValue.toPlainString())
                        .currency(savedProperty.getCurrency())
                        .currencyId(savedProperty.getCurrencyId())
                        .build());
        log.info(
                "Direct disbursement of {} applied to liability {} and property {}: liability "
                        + "balance {}, property value {}, purchase price {}",
                request.getAmount(),
                liability.getId(),
                savedProperty.getId(),
                finalBalance,
                updatedValue,
                updatedPurchase);
    }

    /**
     * Lists the tranches of a liability ordered by {@code trancheNo}.
     *
     * <p>Remaining principal is derived in bulk (one query for the tranches' REPAYMENT
     * transactions, one for their splits) instead of per-tranche, avoiding an N+1 on the listing
     * endpoint.
     *
     * @throws LiabilityNotFoundException if the liability does not belong to the user
     */
    @Transactional(readOnly = true)
    public List<LiabilityTrancheResponse> getTranches(Long userId, Long liabilityId) {
        liabilityRepository
                .findByIdAndUserId(liabilityId, userId)
                .orElseThrow(() -> LiabilityNotFoundException.byIdAndUser(liabilityId, userId));
        List<LiabilityTranche> tranches =
                liabilityTrancheRepository.findByLiabilityIdAndUserId(liabilityId, userId).stream()
                        .sorted(Comparator.comparing(LiabilityTranche::getTrancheNo))
                        .collect(Collectors.toList());
        Map<Long, BigDecimal> remaining =
                liabilityTrancheService.remainingByTrancheId(userId, tranches);
        return tranches.stream()
                .map(
                        tranche ->
                                toTrancheResponse(
                                        tranche,
                                        remaining.getOrDefault(tranche.getId(), BigDecimal.ZERO)))
                .collect(Collectors.toList());
    }

    /**
     * Creates a planned tranche on a liability.
     *
     * <p>{@code trancheNo} defaults to max existing + 1; {@code currency} always comes from the
     * liability (a provided value must match it). A non-null {@code realEstateId} must reference a
     * property owned by the user.
     *
     * @throws LiabilityNotFoundException if the liability does not belong to the user
     * @throws RealEstatePropertyNotFoundException if a non-null {@code realEstateId} does not
     *     reference a property owned by the user
     * @throws InvalidTransactionException on a duplicate tranche number or a currency mismatch
     */
    public LiabilityTrancheResponse createTranche(
            Long userId, Long liabilityId, LiabilityTrancheRequest request) {
        Liability liability =
                liabilityRepository
                        .findByIdAndUserId(liabilityId, userId)
                        .orElseThrow(
                                () -> LiabilityNotFoundException.byIdAndUser(liabilityId, userId));
        validateTrancheCurrency(request, liability);
        validateRealEstateOwnership(userId, request.getRealEstateId());

        Integer trancheNo =
                request.getTrancheNo() != null
                        ? request.getTrancheNo()
                        : nextTrancheNo(liabilityId, userId);
        if (liabilityTrancheRepository.existsByLiabilityIdAndUserIdAndTrancheNo(
                liabilityId, userId, trancheNo)) {
            throw new InvalidTransactionException(
                    String.format(
                            "Tranche number %d already exists for liability %d",
                            trancheNo, liabilityId));
        }

        LiabilityTranche tranche =
                LiabilityTranche.builder()
                        .userId(userId)
                        .liabilityId(liabilityId)
                        .trancheNo(trancheNo)
                        .plannedAmount(request.getPlannedAmount())
                        .plannedDate(request.getPlannedDate())
                        .fee(request.getFee())
                        .interestOnly(Boolean.TRUE.equals(request.getInterestOnly()))
                        .interestOnlyUntil(request.getInterestOnlyUntil())
                        .realEstateId(request.getRealEstateId())
                        .notes(request.getNotes())
                        .status(TrancheStatus.PLANNED)
                        .currency(liability.getCurrency())
                        .build();
        LiabilityTranche saved = liabilityTrancheRepository.save(tranche);
        log.info(
                "Created tranche T{} ({}) on liability {} for user {}",
                saved.getTrancheNo(),
                saved.getPlannedAmount(),
                liabilityId,
                userId);
        return toTrancheResponse(saved);
    }

    /**
     * Updates a tranche.
     *
     * <p>The update is partial: a {@code null} request field leaves the stored value unchanged and
     * an empty string clears a String field ({@code notes}). {@code realEstateId} can only be set
     * (never cleared) and must reference a property owned by the user.
     *
     * <p>PLANNED/CANCELLED tranches accept planned-field updates and PLANNED&#8596;CANCELLED status
     * transitions. DRAWN tranches are immutable except for {@code realEstateId} and {@code notes}.
     *
     * @throws ResourceNotFoundException if the tranche does not belong to the user
     * @throws RealEstatePropertyNotFoundException if a non-null {@code realEstateId} does not
     *     reference a property owned by the user
     * @throws InvalidTransactionException when a DRAWN tranche's planned fields/status change, on
     *     an illegal status transition, or on a currency mismatch
     */
    public LiabilityTrancheResponse updateTranche(
            Long userId, Long trancheId, LiabilityTrancheRequest request) {
        LiabilityTranche tranche =
                liabilityTrancheRepository
                        .findByIdAndUserId(trancheId, userId)
                        .orElseThrow(
                                () ->
                                        new ResourceNotFoundException(
                                                String.format(
                                                        "Tranche with ID %d not found",
                                                        trancheId)));
        if (request.getCurrency() != null
                && tranche.getCurrency() != null
                && !tranche.getCurrency().equalsIgnoreCase(request.getCurrency())) {
            throw InvalidTransactionException.currencyMismatch(
                    tranche.getCurrency(), request.getCurrency());
        }
        validateRealEstateOwnership(userId, request.getRealEstateId());

        if (tranche.getReversedDate() != null) {
            throw new InvalidTransactionException(
                    "A reversed draw is immutable; create a new tranche for a correction");
        }
        if (tranche.getStatus() == TrancheStatus.DRAWN) {
            updateDrawnTranche(tranche, request);
        } else {
            updatePlannedTranche(tranche, request);
        }
        LiabilityTranche saved = liabilityTrancheRepository.save(tranche);
        log.info(
                "Updated tranche {} of liability {}: status={}",
                trancheId,
                saved.getLiabilityId(),
                saved.getStatus());
        return toTrancheResponse(saved);
    }

    /**
     * Applies the non-null planned fields of the request onto a PLANNED/CANCELLED tranche; {@code
     * null} means "leave unchanged".
     */
    private void updatePlannedTranche(LiabilityTranche tranche, LiabilityTrancheRequest request) {
        if (request.getPlannedAmount() != null) {
            tranche.setPlannedAmount(request.getPlannedAmount());
        }
        if (request.getPlannedDate() != null) {
            tranche.setPlannedDate(request.getPlannedDate());
        }
        if (request.getFee() != null) {
            tranche.setFee(request.getFee());
        }
        if (request.getInterestOnly() != null) {
            tranche.setInterestOnly(request.getInterestOnly());
        }
        if (request.getInterestOnlyUntil() != null) {
            tranche.setInterestOnlyUntil(request.getInterestOnlyUntil());
        }
        if (request.getRealEstateId() != null) {
            tranche.setRealEstateId(request.getRealEstateId());
        }
        if (request.getNotes() != null) {
            tranche.setNotes(request.getNotes());
        }
        if (request.getStatus() != null && request.getStatus() != tranche.getStatus()) {
            if (request.getStatus() != TrancheStatus.PLANNED
                    && request.getStatus() != TrancheStatus.CANCELLED) {
                throw new InvalidTransactionException(
                        String.format(
                                "Illegal tranche status transition %s -> %s (only PLANNED and "
                                        + "CANCELLED are toggleable)",
                                tranche.getStatus(), request.getStatus()));
            }
            tranche.setStatus(request.getStatus());
        }
    }

    /**
     * Applies the mutable subset ({@code realEstateId}, {@code notes}) onto a DRAWN tranche; {@code
     * null} means "leave unchanged". Any non-null request value that would change a planned field
     * or the status is rejected (DRAWN tranches are immutable).
     */
    private void updateDrawnTranche(LiabilityTranche tranche, LiabilityTrancheRequest request) {
        if (request.getRealEstateId() != null
                && !Objects.equals(request.getRealEstateId(), tranche.getRealEstateId())) {
            throw new InvalidTransactionException(
                    "A drawn tranche's funding destination is immutable; use financing relationships or reverse the draw");
        }
        boolean plannedFieldsChanged =
                (request.getPlannedAmount() != null
                                && amountsDiffer(
                                        request.getPlannedAmount(), tranche.getPlannedAmount()))
                        || (request.getPlannedDate() != null
                                && !Objects.equals(
                                        request.getPlannedDate(), tranche.getPlannedDate()))
                        || (request.getFee() != null
                                && amountsDiffer(request.getFee(), tranche.getFee()))
                        || (request.getInterestOnly() != null
                                && request.getInterestOnly() != tranche.isInterestOnly())
                        || (request.getInterestOnlyUntil() != null
                                && !Objects.equals(
                                        request.getInterestOnlyUntil(),
                                        tranche.getInterestOnlyUntil()));
        if (plannedFieldsChanged
                || (request.getStatus() != null && request.getStatus() != TrancheStatus.DRAWN)) {
            throw new InvalidTransactionException(
                    String.format(
                            "Tranche %d is DRAWN and immutable except realEstateId and notes",
                            tranche.getId()));
        }
        if (request.getRealEstateId() != null) {
            tranche.setRealEstateId(request.getRealEstateId());
        }
        if (request.getNotes() != null) {
            tranche.setNotes(request.getNotes());
        }
    }

    /**
     * Ensures a non-null {@code realEstateId} references a property owned by the user, mirroring
     * the ownership check of the direct-disbursement route.
     *
     * @throws RealEstatePropertyNotFoundException if the property does not belong to the user
     */
    private void validateRealEstateOwnership(Long userId, Long realEstateId) {
        if (realEstateId == null) {
            return;
        }
        realEstateRepository
                .findByIdAndUserId(realEstateId, userId)
                .orElseThrow(
                        () ->
                                RealEstatePropertyNotFoundException.byIdAndUser(
                                        realEstateId, userId));
    }

    /** Null-safe scale-insensitive comparison of two monetary amounts. */
    private boolean amountsDiffer(BigDecimal a, BigDecimal b) {
        if (a == null && b == null) {
            return false;
        }
        if (a == null || b == null) {
            return true;
        }
        return a.compareTo(b) != 0;
    }

    private void validateTrancheCurrency(LiabilityTrancheRequest request, Liability liability) {
        if (request.getCurrency() != null
                && liability.getCurrency() != null
                && !liability.getCurrency().equalsIgnoreCase(request.getCurrency())) {
            throw InvalidTransactionException.currencyMismatch(
                    liability.getCurrency(), request.getCurrency());
        }
    }

    private Integer nextTrancheNo(Long liabilityId, Long userId) {
        return liabilityTrancheRepository.findByLiabilityIdAndUserId(liabilityId, userId).stream()
                        .map(LiabilityTranche::getTrancheNo)
                        .max(Integer::compareTo)
                        .orElse(0)
                + 1;
    }

    /**
     * Maps a tranche to its response, deriving {@code remaining} from the allocation ledger via
     * {@link LiabilityTrancheService#remainingOf}.
     */
    private LiabilityTrancheResponse toTrancheResponse(LiabilityTranche tranche) {
        return toTrancheResponse(tranche, liabilityTrancheService.remainingOf(tranche));
    }

    /**
     * Maps a tranche to its response with a pre-computed {@code remaining} (bulk derivation, see
     * {@link LiabilityTrancheService#remainingByTrancheId}).
     */
    private LiabilityTrancheResponse toTrancheResponse(
            LiabilityTranche tranche, BigDecimal remaining) {
        return LiabilityTrancheResponse.builder()
                .id(tranche.getId())
                .liabilityId(tranche.getLiabilityId())
                .trancheNo(tranche.getTrancheNo())
                .plannedAmount(tranche.getPlannedAmount())
                .drawnAmount(tranche.getDrawnAmount())
                .remaining(remaining)
                .plannedDate(tranche.getPlannedDate())
                .drawnDate(tranche.getDrawnDate())
                .fee(tranche.getFee())
                .interestOnly(tranche.isInterestOnly())
                .interestOnlyUntil(tranche.getInterestOnlyUntil())
                .status(tranche.getStatus())
                .realEstateId(tranche.getRealEstateId())
                .directDisbursement(tranche.isDirectDisbursement())
                .reversedDate(tranche.getReversedDate())
                .notes(tranche.getNotes())
                .currency(tranche.getCurrency())
                .build();
    }

    // ===========================
    // Repayment preview (Task 5)
    // ===========================

    /**
     * Previews how a repayment of {@code total} on {@code date} splits into interest, insurance and
     * principal components.
     *
     * <p>Monthly interest = currentBalance × interestRate / 1200 and monthly insurance = principal
     * × insurancePercentage / 1200, both rounded half-up to 2 decimals. When a {@code DRAWN}
     * tranche with {@code interestOnly=true} is active on the given date ({@code interestOnlyUntil}
     * null or not before the date), the principal component is zero; otherwise principal = total −
     * interest − insurance, floored at zero. Missing rate/insurance fields are treated as zero.
     *
     * @param userId the ID of the user requesting the preview (for authorization)
     * @param liabilityId the ID of the liability being repaid
     * @param total the total repayment amount
     * @param date the repayment date used for the interest-only window check
     * @return the repayment breakdown preview
     * @throws LiabilityNotFoundException if the liability does not belong to the user
     */
    @Transactional(readOnly = true)
    public RepaymentPreviewResponse getRepaymentPreview(
            Long userId, Long liabilityId, BigDecimal total, LocalDate date) {
        return getRepaymentPreview(userId, liabilityId, total, date, null);
    }

    /**
     * FX variant of the repayment preview (Task 9): when {@code inputCurrency} is provided and
     * differs from the liability's currency, the total is first converted into the liability
     * currency via {@link ExchangeRateService}; every returned component is then expressed in the
     * liability currency.
     *
     * @param userId the ID of the user requesting the preview (for authorization)
     * @param liabilityId the ID of the liability being repaid
     * @param total the total repayment amount, expressed in {@code inputCurrency} when provided
     * @param date the repayment date used for the interest-only window check
     * @param inputCurrency optional ISO 4217 code the {@code total} is entered in (null = liability
     *     currency)
     * @return the repayment breakdown preview in the liability currency
     * @throws LiabilityNotFoundException if the liability does not belong to the user
     */
    @Transactional(readOnly = true)
    public RepaymentPreviewResponse getRepaymentPreview(
            Long userId, Long liabilityId, BigDecimal total, LocalDate date, String inputCurrency) {
        log.debug(
                "Previewing repayment for liability {}: userId={}, total={}, date={}, inputCurrency={}",
                liabilityId,
                userId,
                total,
                date,
                inputCurrency);

        Liability liability =
                liabilityRepository
                        .findByIdAndUserId(liabilityId, userId)
                        .orElseThrow(
                                () -> LiabilityNotFoundException.byIdAndUser(liabilityId, userId));

        BigDecimal effectiveTotal = total;
        if (inputCurrency != null
                && !inputCurrency.isBlank()
                && liability.getCurrency() != null
                && !inputCurrency.equalsIgnoreCase(liability.getCurrency())) {
            try {
                effectiveTotal =
                        exchangeRateService
                                .convert(
                                        total,
                                        inputCurrency.toUpperCase(),
                                        liability.getCurrency(),
                                        date)
                                .setScale(2, RoundingMode.HALF_UP);
            } catch (IllegalStateException e) {
                // ExchangeRateService signals "no exchange rate available" with an
                // IllegalStateException; invalid-input IllegalArgumentExceptions propagate.
                log.warn(
                        "FX repayment preview for liability {} could not convert {} → {}: {}",
                        liabilityId,
                        inputCurrency,
                        liability.getCurrency(),
                        e.getMessage());
                throw InvalidTransactionException.exchangeRateUnavailable(
                        inputCurrency.toUpperCase(), liability.getCurrency());
            }
            log.debug(
                    "FX repayment preview: converted {} {} to {} {} for liability {}",
                    total,
                    inputCurrency,
                    effectiveTotal,
                    liability.getCurrency(),
                    liabilityId);
        }

        BigDecimal balance = orZero(currentDebt(liability));
        BigDecimal rate = orZero(decryptAmount(liability.getInterestRate()));
        BigDecimal principalAmt = orZero(decryptAmount(liability.getPrincipal()));
        BigDecimal insurancePct = orZero(decryptAmount(liability.getInsurancePercentage()));

        BigDecimal interest =
                balance.multiply(rate)
                        .divide(BigDecimal.valueOf(MONTHS_PER_YEAR * 100), 2, RoundingMode.HALF_UP);
        BigDecimal insurance =
                principalAmt
                        .multiply(insurancePct)
                        .divide(BigDecimal.valueOf(MONTHS_PER_YEAR * 100), 2, RoundingMode.HALF_UP);

        boolean interestOnly =
                isInterestOnlyWindowActive(
                        liabilityTrancheRepository.findByLiabilityIdAndUserId(liabilityId, userId),
                        date);

        BigDecimal charges = interest.add(insurance);
        if (effectiveTotal.compareTo(charges) < 0) {
            throw new InvalidTransactionException(
                    "Payment is below the estimated interest and insurance; enter the actual components manually");
        }
        BigDecimal principal = effectiveTotal.subtract(charges);
        if (interestOnly && principal.signum() > 0) {
            throw new InvalidTransactionException(
                    "Interest-only payment must equal estimated charges; enter an extra principal payment manually");
        }
        if (principal.compareTo(balance) > 0) {
            throw new InvalidTransactionException("Principal payment exceeds outstanding debt");
        }

        return RepaymentPreviewResponse.builder()
                .total(effectiveTotal)
                .principal(principal)
                .interest(interest)
                .insurance(insurance)
                .interestOnly(interestOnly)
                .build();
    }

    @CacheEvict(
            value = {
                "dashboardSummary",
                "netWorthSummary",
                "networthAllocation",
                "portfolioPerformance",
                "borrowingCapacity"
            },
            allEntries = true)
    public LiabilityTrancheResponse reverseDirectDraw(Long userId, Long trancheId, LocalDate date) {
        LiabilityTranche tranche =
                liabilityTrancheRepository
                        .findByIdAndUserId(trancheId, userId)
                        .orElseThrow(() -> new ResourceNotFoundException("Tranche not found"));
        if (!tranche.isDirectDisbursement()
                || tranche.getStatus() != TrancheStatus.DRAWN
                || tranche.getReversedDate() != null) {
            throw new InvalidTransactionException(
                    "Only an active direct draw can be reversed here");
        }
        if (date == null
                || date.isBefore(tranche.getDrawnDate())
                || date.isAfter(LocalDate.now())) {
            throw new InvalidTransactionException(
                    "Reversal date must fall between the draw date and today");
        }
        if (liabilityTrancheService.allocatedPrincipal(tranche).signum() > 0) {
            throw new InvalidTransactionException("Reverse this draw's principal repayments first");
        }
        Liability loan =
                liabilityRepository
                        .findByIdAndUserId(tranche.getLiabilityId(), userId)
                        .orElseThrow();
        BigDecimal outstanding =
                new BigDecimal(loan.getCurrentBalance()).subtract(tranche.getDrawnAmount());
        if (outstanding.signum() < 0)
            throw new InvalidTransactionException("Draw exceeds outstanding principal");
        loan.setCurrentBalance(outstanding.toPlainString());
        liabilityRepository.save(loan);
        tranche.setReversedDate(date);
        tranche.setStatus(TrancheStatus.CANCELLED);
        liabilityTrancheRepository.save(tranche);
        restoreDirectValuation(userId, tranche);
        invalidateSnapshotsFrom(userId, loan.getStartDate());
        return toTrancheResponse(tranche);
    }

    private void restoreDirectValuation(Long userId, LiabilityTranche reversed) {
        RealEstateProperty property =
                realEstateRepository
                        .findByIdAndUserId(reversed.getRealEstateId(), userId)
                        .orElseThrow();
        java.util.Set<Long> cancelled =
                liabilityTrancheRepository.findByUserId(userId).stream()
                        .filter(t -> t.getReversedDate() != null)
                        .map(LiabilityTranche::getId)
                        .collect(java.util.stream.Collectors.toSet());
        List<RealEstateValueHistory> history =
                realEstateValueHistoryRepository.findByUserId(userId).stream()
                        .filter(h -> property.getId().equals(h.getPropertyId()))
                        .toList();
        // Independent later appraisals/improvements remain authoritative. Only cancelled draw
        // snapshots disappear from today's valuation; their dated history remains available.
        org.openfinance.util.PropertyValuationHistory.Valuation valuation =
                org.openfinance.util.PropertyValuationHistory.current(
                        property,
                        history.stream()
                                .filter(
                                        h ->
                                                h.getSourceTrancheId() == null
                                                        || !cancelled.contains(
                                                                h.getSourceTrancheId()))
                                .toList());
        BigDecimal value =
                valuation.currency().equalsIgnoreCase(property.getCurrency())
                        ? valuation.amount()
                        : exchangeRateService.convert(
                                valuation.amount(), valuation.currency(), property.getCurrency());
        property.setCurrentValue(value.toPlainString());
        realEstateRepository.save(property);
        if (property.getAssetId() != null) {
            org.openfinance.entity.Asset backing =
                    backingAssetRepository
                            .findByIdAndUserId(property.getAssetId(), userId)
                            .orElseThrow();
            backing.setCurrentPrice(value);
            backingAssetRepository.save(backing);
        }
    }

    @Transactional(readOnly = true)
    public List<org.openfinance.dto.TransactionResponse> getPropertyLoanMovements(
            Long userId, Long propertyId) {
        RealEstateProperty property =
                realEstateRepository
                        .findByIdAndUserId(propertyId, userId)
                        .orElseThrow(
                                () ->
                                        RealEstatePropertyNotFoundException.byIdAndUser(
                                                propertyId, userId));
        java.util.Set<Long> loanIds = new java.util.HashSet<>();
        if (property.getMortgageId() != null) loanIds.add(property.getMortgageId());
        if (property.getAssetId() != null)
            assetFinancingService
                    .forAsset(userId, property.getAssetId())
                    .forEach(link -> loanIds.add(link.getLiabilityId()));
        liabilityTrancheRepository.findByUserId(userId).stream()
                .filter(t -> propertyId.equals(t.getRealEstateId()) && t.getDrawnAmount() != null)
                .forEach(t -> loanIds.add(t.getLiabilityId()));
        return loanIds.stream()
                .flatMap(id -> getLinkedTransactions(id, userId).stream())
                .sorted(
                        Comparator.comparing(org.openfinance.dto.TransactionResponse::getDate)
                                .reversed()
                                .thenComparing(org.openfinance.dto.TransactionResponse::getId))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<LiabilityTrancheResponse> getPropertyDrawdowns(Long userId, Long propertyId) {
        realEstateRepository
                .findByIdAndUserId(propertyId, userId)
                .orElseThrow(
                        () -> RealEstatePropertyNotFoundException.byIdAndUser(propertyId, userId));
        return liabilityTrancheRepository.findByUserId(userId).stream()
                .filter(t -> propertyId.equals(t.getRealEstateId()) && t.getDrawnAmount() != null)
                .sorted(
                        Comparator.comparing(LiabilityTranche::getDrawnDate)
                                .thenComparing(LiabilityTranche::getId))
                .map(this::toTrancheResponse)
                .toList();
    }

    private BigDecimal currentDebt(Liability liability) {
        if (liability.getRepresentedByAccountId() == null)
            return decryptAmount(liability.getCurrentBalance());
        return accountRepository
                .findByIdAndUserId(liability.getRepresentedByAccountId(), liability.getUserId())
                .orElseThrow()
                .getBalance()
                .negate()
                .max(BigDecimal.ZERO);
    }

    private void configureAccountSource(
            Long userId, Liability liability, LiabilityRequest request) {
        Long accountId = request.getRepresentedByAccountId();
        if (java.util.Objects.equals(accountId, liability.getRepresentedByAccountId())) return;
        if (liability.getId() != null && isBalanceLocked(liability)) {
            throw new InvalidTransactionException(
                    "A posted liability cannot change its balance source");
        }
        if (accountId != null) {
            Account account =
                    accountRepository
                            .findByIdAndUserId(accountId, userId)
                            .orElseThrow(() -> new ResourceNotFoundException("Account not found"));
            if (!Boolean.TRUE.equals(account.getIsActive())
                    || account.getType() != org.openfinance.entity.AccountType.CREDIT_CARD
                    || liability.getType() != LiabilityType.CREDIT_CARD
                    || !account.getCurrency().equalsIgnoreCase(request.getCurrency())
                    || account.getBalance()
                                    .negate()
                                    .max(BigDecimal.ZERO)
                                    .compareTo(request.getCurrentBalance())
                            != 0
                    || liabilityRepository.existsByRepresentedByAccountIdAndUserId(
                            accountId, userId)) {
                throw new InvalidTransactionException(
                        "Select an unlinked credit-card account with the same currency and outstanding balance");
            }
        }
        liability.setRepresentedByAccountId(accountId);
    }

    private void updatePrimaryProperty(Long userId, Long liabilityId, Long propertyId) {
        RealEstateProperty target =
                propertyId == null
                        ? null
                        : realEstateRepository
                                .findByIdAndUserId(propertyId, userId)
                                .orElseThrow(
                                        () ->
                                                RealEstatePropertyNotFoundException.byIdAndUser(
                                                        propertyId, userId));
        for (RealEstateProperty property : realEstateRepository.findByMortgageId(liabilityId)) {
            if (!userId.equals(property.getUserId())) continue;
            property.setMortgageId(null);
            property.setMortgage(null);
            realEstateRepository.save(property);
        }
        if (target != null) {
            target.setMortgageId(liabilityId);
            target.setMortgage(null);
            realEstateRepository.save(target);
        }
    }

    private boolean isBalanceLocked(Liability liability) {
        return liability.getRepresentedByAccountId() != null
                || !transactionRepository
                        .findByLiabilityIdAndUserId(liability.getId(), liability.getUserId())
                        .isEmpty()
                || liabilityTrancheRepository
                        .findByLiabilityIdAndUserId(liability.getId(), liability.getUserId())
                        .stream()
                        .anyMatch(t -> t.getDrawnAmount() != null);
    }

    private BigDecimal recordedPrincipalPaid(Liability liability) {
        BigDecimal openingPrincipal = liability.getOpeningPrincipal();
        BigDecimal openingBalance = liability.getOpeningBalance();
        BigDecimal paid = openingPrincipal.subtract(openingBalance).max(BigDecimal.ZERO);
        for (Transaction tx :
                transactionRepository.findByLiabilityIdAndUserId(
                        liability.getId(), liability.getUserId())) {
            if (tx.getMovementType() == MovementType.DISBURSEMENT) continue;
            paid = paid.add(tx.getPrincipalAmount());
        }
        return paid;
    }

    /** Null-safe BigDecimal accessor defaulting to zero. */
    private static BigDecimal orZero(BigDecimal value) {
        return value != null ? value : BigDecimal.ZERO;
    }

    /**
     * Window predicate shared by the repayment preview and the amortization schedule: a DRAWN
     * interest-only tranche whose window is open on the given date (no end date, or the date is not
     * past {@code interestOnlyUntil}).
     */
    private static boolean isInterestOnlyWindowActive(
            List<LiabilityTranche> tranches, LocalDate date) {
        return tranches.stream()
                .anyMatch(
                        t ->
                                t.getStatus() == TrancheStatus.DRAWN
                                        && t.isInterestOnly()
                                        && (t.getDrawnDate() == null
                                                || !date.isBefore(t.getDrawnDate()))
                                        && (t.getInterestOnlyUntil() == null
                                                || !date.isAfter(t.getInterestOnlyUntil())));
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
        BigDecimal decryptedCurrentBalance = currentDebt(liability);
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
        BigDecimal totalPaid = recordedPrincipalPaid(liability);
        BigDecimal funded = decryptedCurrentBalance.add(totalPaid);
        BigDecimal payoffPercentage =
                funded.compareTo(BigDecimal.ZERO) > 0
                        ? totalPaid
                                .divide(funded, 4, RoundingMode.HALF_UP)
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
                        .approvedAmount(
                                liability.getType() == LiabilityType.CREDIT_CARD
                                        ? liability.getCreditLimit()
                                        : decryptedPrincipal)
                        .creditLimit(liability.getCreditLimit())
                        .representedByAccountId(liability.getRepresentedByAccountId())
                        .fundedAmount(liability.getRepresentedByAccountId() == null ? funded : null)
                        .fundingStatus(
                                liability.getType() == LiabilityType.CREDIT_CARD
                                                && decryptedCurrentBalance.signum() == 0
                                        ? "NO_BALANCE"
                                        : funded.signum() == 0
                                                ? "UNDRAWN"
                                                : decryptedCurrentBalance.signum() == 0
                                                        ? "PAID"
                                                        : "ACTIVE")
                        .balanceLocked(isBalanceLocked(liability))
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
                        .principalPaid(
                                liability.getRepresentedByAccountId() == null
                                        ? principalPaid
                                        : null)
                        .createdAt(liability.getCreatedAt())
                        .updatedAt(liability.getUpdatedAt())
                        // Calculated fields
                        .totalPaid(liability.getRepresentedByAccountId() == null ? totalPaid : null)
                        .payoffPercentage(
                                liability.getRepresentedByAccountId() == null
                                        ? payoffPercentage
                                        : null)
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
        CurrencyConversionHelper.ConversionResult r =
                currencyConversionHelper.convert(
                        userId, nativeCurrency, nativeBalance, true, null, "liability");
        response.setBaseCurrency(r.baseCurrency());
        response.setBalanceInBaseCurrency(r.amountInBaseCurrency());
        response.setExchangeRate(r.exchangeRate());
        response.setIsConverted(r.converted());
        if (r.secondaryCurrency() != null) {
            response.setSecondaryCurrency(r.secondaryCurrency());
            response.setBalanceInSecondaryCurrency(r.amountInSecondaryCurrency());
            response.setSecondaryExchangeRate(r.secondaryExchangeRate());
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
