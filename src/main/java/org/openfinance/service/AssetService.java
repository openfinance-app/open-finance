package org.openfinance.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import javax.crypto.SecretKey;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.openfinance.config.EncryptionProperties;
import org.openfinance.dto.AssetRequest;
import org.openfinance.dto.AssetResponse;
import org.openfinance.dto.AssetSearchCriteria;
import org.openfinance.dto.AssetSummaryResponse;
import org.openfinance.entity.Account;
import org.openfinance.entity.Asset;
import org.openfinance.entity.AssetType;
import org.openfinance.exception.AccountNotFoundException;
import org.openfinance.exception.AssetNotFoundException;
import org.openfinance.exception.InvalidAssetStateException;
import org.openfinance.exception.InvalidTransactionException;
import org.openfinance.mapper.AssetMapper;
import org.openfinance.repository.AccountRepository;
import org.openfinance.repository.AssetRepository;
import org.openfinance.repository.CurrencyRepository;
import org.openfinance.repository.NetWorthRepository;
import org.openfinance.repository.UserRepository;
import org.openfinance.security.EncryptionContext;
import org.openfinance.security.EncryptionService;
import org.openfinance.specification.AssetSpecification;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Caching;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service layer for managing investment assets.
 *
 * <p>This service handles business logic for asset CRUD operations, including:
 *
 * <ul>
 *   <li>Creating new assets with encrypted sensitive fields (name, notes)
 *   <li>Updating existing assets (including price updates)
 *   <li>Deleting assets
 *   <li>Retrieving assets with decrypted data and calculated fields
 *   <li>Portfolio analytics (total value, cost, gains by type/currency)
 * </ul>
 *
 * <p><strong>Security Note:</strong> The {@code name} and {@code notes} fields are encrypted before
 * storing in the database and decrypted when reading. The encryption key must be provided by the
 * caller (typically from the user's session after authentication).
 *
 * <p>Requirement REQ-2.6: Asset Management - CRUD operations for financial assets
 *
 * <p>Requirement REQ-2.6.2: Track asset details (name, type, quantity, prices)
 *
 * <p>Requirement REQ-2.6.3: Calculate and display portfolio values and gains/losses
 *
 * <p>Requirement REQ-2.18: Data encryption at rest for sensitive fields
 *
 * <p>Requirement REQ-3.2: Authorization - Users can only access their own assets
 *
 * @see org.openfinance.entity.Asset
 * @see org.openfinance.dto.AssetRequest
 * @see org.openfinance.dto.AssetResponse
 */
@Service
@Transactional
@RequiredArgsConstructor
@Slf4j
public class AssetService {

    private final AssetRepository assetRepository;
    private final org.openfinance.repository.TransactionRepository transactionRepository;
    private final AccountRepository accountRepository;
    private final CurrencyRepository currencyRepository;
    private final AssetMapper assetMapper;
    private final EncryptionService encryptionService;
    private final UserRepository userRepository;
    private final ExchangeRateService exchangeRateService;
    private final NetWorthRepository netWorthRepository;
    private final OperationHistoryService operationHistoryService;
    private final SearchTokenService searchTokenService;
    private final AttachmentService attachmentService;
    private final DefaultCurrencyProvider defaultCurrencyProvider;
    private final EncryptionProperties encryptionProperties;
    private final CurrencyConversionHelper currencyConversionHelper;

    /**
     * Creates a new asset for the specified user.
     *
     * <p>The asset name and notes are encrypted before storing in the database. If an accountId is
     * provided, validates that the account exists and belongs to the user.
     *
     * <p>Requirement REQ-2.6.1: Create new asset with encrypted sensitive data
     *
     * <p>Requirement REQ-2.6.2: Link asset to an account (optional)
     *
     * @param userId the ID of the user creating the asset
     * @param request the asset creation request containing asset details
     * @param encryptionKey the AES-256 encryption key for sensitive fields
     * @return the created asset with decrypted data and calculated fields
     * @throws IllegalArgumentException if userId, request, or encryptionKey is null
     * @throws AccountNotFoundException if accountId is provided but account not found or doesn't
     *     belong to user
     */
    @Caching(
            evict = {
                @CacheEvict(
                        value = {
                            "dashboardSummary",
                            "netWorthSummary",
                            "assetAllocation",
                            "networthAllocation"
                        },
                        key = "#userId"),
                @CacheEvict(value = "portfolioPerformance", allEntries = true)
            })
    public AssetResponse createAsset(Long userId, AssetRequest request) {
        return createAssetInternal(userId, request, false);
    }

    public AssetResponse createPropertyAsset(Long userId, AssetRequest request) {
        return createAssetInternal(userId, request, true);
    }

    private AssetResponse createAssetInternal(
            Long userId, AssetRequest request, boolean propertyWrite) {
        if (!propertyWrite && request != null && request.getType() == AssetType.REAL_ESTATE) {
            throw new org.openfinance.exception.InvalidTransactionException(
                    "Manage real estate through its property record");
        }

        if (userId == null) {
            throw new IllegalArgumentException("User ID cannot be null");
        }
        if (request == null) {
            throw new IllegalArgumentException("Asset request cannot be null");
        }
        log.debug(
                "Creating asset for user {}: type={}, symbol={}",
                userId,
                request.getType(),
                request.getSymbol());

        // Validate account ownership if accountId is provided
        if (request.getAccountId() != null) {
            validateAccountOwnership(request.getAccountId(), userId);
        }

        // Map request to entity
        Asset asset = assetMapper.toEntity(request);
        if (asset.getAcquisitionType() == null)
            asset.setAcquisitionType(org.openfinance.entity.AcquisitionType.PURCHASE);
        asset.setUserId(userId);
        asset.setCurrencyId(resolveCurrencyId(asset.getCurrency()));

        // Default usefulLifeYears for depreciating physical assets
        if (asset.getUsefulLifeYears() == null
                && asset.getType() != null
                && asset.getType().isDepreciating()) {
            asset.setUsefulLifeYears(asset.getType().getDefaultUsefulLifeYears());
        }

        // Sensitive fields handled by JPA converter automatically

        // Set lastUpdated timestamp to now (initial price entry)
        asset.setLastUpdated(LocalDateTime.now());

        // Save to database
        Asset savedAsset = assetRepository.save(asset);
        indexAssetSearchTokens(savedAsset, request.getName());
        log.info(
                "Asset created successfully: id={}, userId={}, type={}, symbol={}",
                savedAsset.getId(),
                userId,
                savedAsset.getType(),
                savedAsset.getSymbol());
        invalidateSnapshotsFrom(userId, savedAsset.getPurchaseDate());

        // Manually load account if accountId is present (for accountName in response)
        if (savedAsset.getAccountId() != null) {
            Account account =
                    accountRepository
                            .findByIdAndUserId(savedAsset.getAccountId(), userId)
                            .orElse(null); // Account might have been deleted, so don't fail
            savedAsset.setAccount(account);
        }

        // Decrypt and return response with calculated fields
        AssetResponse assetCreateResponse = toResponseWithDecryption(savedAsset);

        // Record in operation history
        if (!propertyWrite) {
            operationHistoryService.record(
                    userId,
                    org.openfinance.entity.EntityType.ASSET,
                    savedAsset.getId(),
                    request.getName(),
                    org.openfinance.entity.OperationType.CREATE,
                    (Object) null,
                    null);
        }

        return assetCreateResponse;
    }

    /**
     * Updates an existing asset.
     *
     * <p>Only the asset owner can update the asset. Sensitive fields are re-encrypted if they have
     * changed. If the currentPrice is updated, the lastUpdated timestamp is automatically set to
     * the current time.
     *
     * <p>Requirement REQ-2.6.2: Update asset details
     *
     * <p>Requirement REQ-2.6.4: Update current price and track last updated time
     *
     * <p>Requirement REQ-3.2: Authorization check - verify asset ownership
     *
     * @param assetId the ID of the asset to update
     * @param userId the ID of the user updating the asset (for authorization)
     * @param request the asset update request
     * @param encryptionKey the AES-256 encryption key for sensitive fields
     * @return the updated asset with decrypted data and calculated fields
     * @throws AssetNotFoundException if asset not found or doesn't belong to user
     * @throws AccountNotFoundException if accountId is provided but account not found or doesn't
     *     belong to user
     * @throws IllegalArgumentException if any parameter is null
     */
    @Caching(
            evict = {
                @CacheEvict(
                        value = {
                            "dashboardSummary",
                            "netWorthSummary",
                            "assetAllocation",
                            "networthAllocation"
                        },
                        key = "#userId"),
                @CacheEvict(value = "portfolioPerformance", allEntries = true)
            })
    public AssetResponse updateAsset(Long assetId, Long userId, AssetRequest request) {
        return updateAssetInternal(assetId, userId, request, false);
    }

    public AssetResponse updatePropertyAsset(Long assetId, Long userId, AssetRequest request) {
        return updateAssetInternal(assetId, userId, request, true);
    }

    private AssetResponse updateAssetInternal(
            Long assetId, Long userId, AssetRequest request, boolean propertyWrite) {
        log.debug("Updating asset {}: userId={}", assetId, userId);

        if (assetId == null) {
            throw new IllegalArgumentException("Asset ID cannot be null");
        }
        if (userId == null) {
            throw new IllegalArgumentException("User ID cannot be null");
        }
        if (request == null) {
            throw new IllegalArgumentException("Asset request cannot be null");
        }
        // Fetch asset and verify ownership (Requirement 3.2: Authorization)
        Asset asset =
                assetRepository
                        .findByIdAndUserId(assetId, userId)
                        .orElseThrow(() -> AssetNotFoundException.byIdAndUser(assetId, userId));
        if (!propertyWrite
                && (asset.getType() == AssetType.REAL_ESTATE
                        || request.getType() == AssetType.REAL_ESTATE)) {
            throw new org.openfinance.exception.InvalidTransactionException(
                    "Manage real estate through its property record");
        }

        if (request.getCurrency() != null
                && !request.getCurrency().equalsIgnoreCase(asset.getCurrency())
                && !transactionRepository.findByAssetIdAndUserId(assetId, userId).isEmpty()) {
            throw new InvalidTransactionException(
                    "Reverse asset cost movements before correcting its currency");
        }
        // Capture snapshot before update for history
        AssetResponse beforeAssetSnapshot = toResponseWithDecryption(asset);

        // Store old price and purchase date to detect changes relevant to net worth
        // history
        BigDecimal oldPrice = asset.getCurrentPrice();
        LocalDate oldPurchaseDate = asset.getPurchaseDate();

        // Validate account ownership if accountId is provided
        if (request.getAccountId() != null) {
            validateAccountOwnership(request.getAccountId(), userId);
        }

        // Update fields from request (only non-null fields will be copied)
        assetMapper.updateEntityFromRequest(request, asset);
        asset.setCurrencyId(resolveCurrencyId(asset.getCurrency()));

        // Default usefulLifeYears for depreciating physical assets
        if (asset.getUsefulLifeYears() == null
                && asset.getType() != null
                && asset.getType().isDepreciating()) {
            asset.setUsefulLifeYears(asset.getType().getDefaultUsefulLifeYears());
        }

        // Sensitive fields handled by JPA converter — just set plain text from request
        if (request.getName() != null) asset.setName(request.getName());

        if (request.getNotes() != null && !request.getNotes().isBlank()) {
            asset.setNotes(request.getNotes());
        } else if (request.getNotes() != null) {
            // explicit empty notes -> clear stored value
            asset.setNotes(null);
        }

        // Physical asset fields — plain text, converter handles encryption
        if (request.getSerialNumber() != null && !request.getSerialNumber().isBlank()) {
            asset.setSerialNumber(request.getSerialNumber());
        } else if (request.getSerialNumber() != null) {
            asset.setSerialNumber(null);
        }

        if (request.getBrand() != null && !request.getBrand().isBlank()) {
            asset.setBrand(request.getBrand());
        } else if (request.getBrand() != null) {
            asset.setBrand(null);
        }

        if (request.getModel() != null && !request.getModel().isBlank()) {
            asset.setModel(request.getModel());
        } else if (request.getModel() != null) {
            asset.setModel(null);
        }

        // Update lastUpdated timestamp if price changed (Requirement 2.6.5)
        if (request.getCurrentPrice() != null
                && request.getCurrentPrice().compareTo(oldPrice) != 0) {
            asset.setLastUpdated(LocalDateTime.now());
            log.debug(
                    "Asset price updated: id={}, oldPrice={}, newPrice={}",
                    assetId,
                    oldPrice,
                    request.getCurrentPrice());
        }

        // Save changes
        Asset updatedAsset = assetRepository.save(asset);
        indexAssetSearchTokens(updatedAsset, request.getName());
        log.info("Asset updated successfully: id={}, userId={}", assetId, userId);
        // Invalidate snapshots from the earliest affected purchase date onward
        LocalDate newPurchaseDate = updatedAsset.getPurchaseDate();
        LocalDate cutoff =
                (oldPurchaseDate != null
                                && (newPurchaseDate == null
                                        || oldPurchaseDate.isBefore(newPurchaseDate)))
                        ? oldPurchaseDate
                        : newPurchaseDate;
        invalidateSnapshotsFrom(userId, cutoff);

        // Decrypt and return response with calculated fields
        AssetResponse assetUpdateResponse = toResponseWithDecryption(updatedAsset);

        // Record in operation history
        if (!propertyWrite) {
            operationHistoryService.record(
                    userId,
                    org.openfinance.entity.EntityType.ASSET,
                    assetId,
                    request.getName(),
                    org.openfinance.entity.OperationType.UPDATE,
                    beforeAssetSnapshot,
                    null);
        }

        return assetUpdateResponse;
    }

    /**
     * Applies a capitalized improvement to a physical asset's cost basis (spec §3.3).
     *
     * <p>Increases {@code currentPrice} by the improvement amount per owned unit (the full amount
     * when {@code quantity} is 1, otherwise {@code amount / quantity} rounded to 2 decimals
     * HALF_UP). Non-physical assets are rejected with {@link InvalidAssetStateException}.
     *
     * <p>Currency guard (Task 6/9): {@code movementCurrency} — the currency the user actually
     * moved, i.e. {@code originalCurrency} when a conversion was applied, else the transaction
     * currency — must match the asset's currency, mirroring the liability guard in {@code
     * TransactionService.applyLinkedMovements}. The caller passes the amount already expressed in
     * the asset's currency (the liability-currency-style total from the conversion fields).
     *
     * @param assetId the ID of the physical asset being improved
     * @param userId the owner's ID
     * @param amount the improvement amount in the asset's currency
     * @param movementDate the movement date (used for net worth snapshot invalidation)
     * @param movementCurrency the currency the movement is expressed in (must match the asset's)
     */
    public void applyCapitalImprovement(
            Long assetId,
            Long userId,
            BigDecimal amount,
            LocalDate movementDate,
            String movementCurrency) {
        Asset asset = findPhysicalAsset(assetId, userId);
        if (asset.getAcquisitionType() == org.openfinance.entity.AcquisitionType.PLANNED) {
            throw new InvalidTransactionException(
                    "Complete the asset's acquisition before capitalizing improvements");
        }
        assertImprovementCurrencyMatches(
                "asset", asset.getId(), asset.getCurrency(), movementCurrency);
        BigDecimal updated =
                asset.getCurrentPrice().add(improvementPerUnit(amount, asset.getQuantity()));
        asset.setCurrentPrice(updated);
        asset.setLastUpdated(LocalDateTime.now());
        assetRepository.save(asset);
        invalidateSnapshotsFrom(userId, movementDate);
        log.info(
                "Capital improvement of {} applied to asset {}: new unit price {}",
                amount,
                assetId,
                updated);
    }

    /**
     * Rejects an improvement whose movement currency does not match the improved instrument's
     * currency (Task 6 deferred minor — mirrors the liability currency guard).
     */
    private void assertImprovementCurrencyMatches(
            String instrumentType,
            Long instrumentId,
            String instrumentCurrency,
            String movementCurrency) {
        if (movementCurrency != null
                && instrumentCurrency != null
                && !instrumentCurrency.equalsIgnoreCase(movementCurrency)) {
            throw InvalidTransactionException.improvementCurrencyMismatch(
                    movementCurrency, instrumentCurrency, instrumentId, instrumentType);
        }
    }

    /**
     * Reverses a previously applied capitalized improvement on a physical asset, restoring the
     * prior cost basis (floored at zero).
     *
     * @param assetId the ID of the physical asset
     * @param userId the owner's ID
     * @param amount the original improvement amount
     * @param movementDate the original movement date (used for net worth snapshot invalidation)
     */
    public void reverseCapitalImprovement(
            Long assetId, Long userId, BigDecimal amount, LocalDate movementDate) {
        Asset asset = findPhysicalAsset(assetId, userId);
        BigDecimal updated =
                asset.getCurrentPrice()
                        .subtract(improvementPerUnit(amount, asset.getQuantity()))
                        .max(BigDecimal.ZERO);
        asset.setCurrentPrice(updated);
        asset.setLastUpdated(LocalDateTime.now());
        assetRepository.save(asset);
        invalidateSnapshotsFrom(userId, movementDate);
        log.info(
                "Capital improvement of {} reversed on asset {}: new unit price {}",
                amount,
                assetId,
                updated);
    }

    /**
     * Loads an asset by ID and user, rejecting non-physical assets (spec §3.3: improvements track
     * the cost basis of physical assets only).
     */
    private Asset findPhysicalAsset(Long assetId, Long userId) {
        Asset asset =
                assetRepository
                        .findByIdAndUserId(assetId, userId)
                        .orElseThrow(() -> AssetNotFoundException.byIdAndUser(assetId, userId));
        if (!asset.isPhysical()) {
            throw InvalidAssetStateException.improvementNotPhysical(
                    assetId, String.valueOf(asset.getType()));
        }
        return asset;
    }

    /**
     * Spreads an improvement amount over the owned units: the full amount when the quantity is 1,
     * otherwise {@code amount / quantity} rounded to 2 decimals HALF_UP.
     */
    private BigDecimal improvementPerUnit(BigDecimal amount, BigDecimal quantity) {
        if (quantity == null || quantity.compareTo(BigDecimal.ONE) == 0) {
            return amount;
        }
        return amount.divide(quantity, 2, RoundingMode.HALF_UP);
    }

    /**
     * Deletes an asset.
     *
     * <p>This is a hard delete, removing the asset from the database entirely. Only the asset owner
     * can delete the asset.
     *
     * <p>Requirement REQ-2.6.2: Delete asset
     *
     * <p>Requirement REQ-3.2: Authorization check - verify asset ownership
     *
     * @param assetId the ID of the asset to delete
     * @param userId the ID of the user deleting the asset (for authorization)
     * @param encryptionKey the user's encryption key (for history snapshot)
     * @throws AssetNotFoundException if asset not found or doesn't belong to user
     * @throws IllegalArgumentException if any parameter is null
     */
    @Caching(
            evict = {
                @CacheEvict(
                        value = {
                            "dashboardSummary",
                            "netWorthSummary",
                            "assetAllocation",
                            "networthAllocation"
                        },
                        key = "#userId"),
                @CacheEvict(value = "portfolioPerformance", allEntries = true)
            })
    public void deleteAsset(Long assetId, Long userId) {
        deleteAssetInternal(assetId, userId, false);
    }

    public void deletePropertyAsset(Long assetId, Long userId) {
        deleteAssetInternal(assetId, userId, true);
    }

    private void deleteAssetInternal(Long assetId, Long userId, boolean propertyWrite) {
        log.debug(
                "Deleting asset {}: userId={}, keyPresent={}",
                assetId,
                userId,
                EncryptionContext.getKey() != null);

        if (assetId == null) {
            throw new IllegalArgumentException("Asset ID cannot be null");
        }
        if (userId == null) {
            throw new IllegalArgumentException("User ID cannot be null");
        }

        // Fetch asset and verify ownership (Requirement 3.2: Authorization)
        Asset asset =
                assetRepository
                        .findByIdAndUserId(assetId, userId)
                        .orElseThrow(() -> AssetNotFoundException.byIdAndUser(assetId, userId));
        if (!propertyWrite && (asset.getType() == AssetType.REAL_ESTATE)) {
            throw new org.openfinance.exception.InvalidTransactionException(
                    "Manage real estate through its property record");
        }

        // Capture snapshot before delete for history (only if key provided)
        AssetResponse beforeDeleteSnapshot = null;
        String label = null;
        if (EncryptionContext.getKey() != null) {
            try {
                beforeDeleteSnapshot = toResponseWithDecryption(asset);
                label = beforeDeleteSnapshot.getName();
            } catch (Exception e) {
                log.warn("Failed to capture snapshot for history: {}", e.getMessage());
            }
        }

        // Hard delete
        LocalDate assetPurchaseDate = asset.getPurchaseDate();
        attachmentService.deleteEntityAttachments(
                org.openfinance.entity.EntityType.ASSET, assetId, userId);
        assetRepository.delete(asset);
        searchTokenService.removeEntity("ASSET", assetId);
        invalidateSnapshotsFrom(userId, assetPurchaseDate);

        log.info("Asset deleted successfully: id={}, userId={}", assetId, userId);

        // Record in operation history
        if (!propertyWrite) {
            operationHistoryService.record(
                    userId,
                    org.openfinance.entity.EntityType.ASSET,
                    assetId,
                    label != null ? label : "Asset " + assetId,
                    org.openfinance.entity.OperationType.DELETE,
                    beforeDeleteSnapshot,
                    null);
        }
    }

    /**
     * Retrieves a single asset by ID.
     *
     * <p>Only the asset owner can retrieve the asset. Sensitive fields are decrypted and calculated
     * fields (total value, gains) are populated.
     *
     * <p>Requirement REQ-2.6.1: Retrieve asset details
     *
     * <p>Requirement REQ-2.6.3: Display calculated fields (value, gains)
     *
     * <p>Requirement REQ-3.2: Authorization check - verify asset ownership
     *
     * @param assetId the ID of the asset to retrieve
     * @param userId the ID of the user retrieving the asset (for authorization)
     * @param encryptionKey the AES-256 encryption key for decrypting sensitive fields
     * @return the asset with decrypted data and calculated fields
     * @throws AssetNotFoundException if asset not found or doesn't belong to user
     * @throws IllegalArgumentException if any parameter is null
     */
    @Transactional(readOnly = true)
    public AssetResponse getAssetById(Long assetId, Long userId) {
        log.debug("Retrieving asset {}: userId={}", assetId, userId);

        if (assetId == null) {
            throw new IllegalArgumentException("Asset ID cannot be null");
        }
        if (userId == null) {
            throw new IllegalArgumentException("User ID cannot be null");
        }
        // Fetch asset and verify ownership (Requirement 3.2: Authorization)
        Asset asset =
                assetRepository
                        .findByIdAndUserId(assetId, userId)
                        .orElseThrow(() -> AssetNotFoundException.byIdAndUser(assetId, userId));

        // Decrypt and return response with calculated fields
        return toResponseWithDecryption(asset);
    }

    /**
     * Retrieves all assets for a user.
     *
     * <p>Returns all assets with decrypted data and calculated fields.
     *
     * <p>Requirement REQ-2.6.1: List all user assets
     *
     * <p>Requirement REQ-2.6.3: Display portfolio values
     *
     * @param userId the ID of the user
     * @param encryptionKey the AES-256 encryption key for decrypting sensitive fields
     * @return list of assets with decrypted data and calculated fields (may be empty)
     * @throws IllegalArgumentException if userId or encryptionKey is null
     */
    @Transactional(readOnly = true)
    public List<AssetResponse> getAssetsByUserId(Long userId) {
        log.debug("Retrieving all assets for user {}", userId);

        if (userId == null) {
            throw new IllegalArgumentException("User ID cannot be null");
        }
        // Fetch all assets for user
        List<Asset> assets = assetRepository.findByUserId(userId);

        log.debug("Found {} assets for user {}", assets.size(), userId);

        // Decrypt and map to responses with calculated fields
        return assets.stream()
                .map(asset -> toResponseWithDecryption(asset))
                .collect(Collectors.toList());
    }

    /**
     * Searches assets with filters and pagination.
     *
     * <p>This method supports dynamic filtering and sorting through the search criteria. All
     * filtering is done at the database level for efficiency.
     *
     * <p><strong>Supported Filters:</strong>
     *
     * <ul>
     *   <li>keyword - Search in asset name (case-insensitive)
     *   <li>type - Filter by asset type
     *   <li>accountId - Filter by account ID
     *   <li>currency - Filter by currency code
     *   <li>symbol - Filter by ticker symbol
     *   <li>purchaseDateFrom - Filter by purchase date >= this date
     *   <li>purchaseDateTo - Filter by purchase date <= this date
     *   <li>valueMin - Filter by minimum total value
     *   <li>valueMax - Filter by maximum total value
     * </ul>
     *
     * @param userId the ID of the user
     * @param criteria the search criteria (all fields optional)
     * @param pageable pagination and sorting parameters (page number, size, sort)
     * @param encryptionKey the AES-256 encryption key for decrypting sensitive fields
     * @return page of assets matching criteria with decrypted data
     * @throws IllegalArgumentException if userId, criteria, pageable, or encryptionKey is null
     */
    @Transactional(readOnly = true)
    public Page<AssetResponse> searchAssets(
            Long userId, AssetSearchCriteria criteria, Pageable pageable) {

        if (userId == null) {
            throw new IllegalArgumentException("User ID cannot be null");
        }
        if (criteria == null) {
            throw new IllegalArgumentException("Search criteria cannot be null");
        }
        if (pageable == null) {
            throw new IllegalArgumentException("Pageable cannot be null");
        }
        log.debug(
                "Searching assets for user {}: keyword={}, type={}, accountId={}",
                userId,
                criteria.getKeyword(),
                criteria.getType(),
                criteria.getAccountId());

        boolean hasKeyword =
                criteria.getKeyword() != null && !criteria.getKeyword().trim().isEmpty();
        boolean hasValueFilter = criteria.getValueMin() != null || criteria.getValueMax() != null;
        boolean sortInMemory =
                pageable.getSort().stream()
                        .anyMatch(
                                order -> IN_MEMORY_ASSET_SORT_FIELDS.contains(order.getProperty()));

        if (hasKeyword || hasValueFilter || sortInMemory) {
            // Name is encrypted and total value is quantity (also encrypted) * currentPrice, so
            // keyword/value filtering and value/name sorting cannot run at the DB level. Fetch all
            // assets matching the non-encrypted criteria, decrypt, then filter/sort/paginate here.
            Specification<Asset> spec = AssetSpecification.buildSpecification(userId, criteria);
            List<AssetResponse> results =
                    assetRepository.findAll(spec).stream()
                            .map(asset -> toResponseWithDecryption(asset))
                            .collect(Collectors.toList());

            if (hasKeyword) {
                String keyword = criteria.getKeyword();
                boolean keywordRegex = criteria.isKeywordRegex();
                results =
                        results.stream()
                                .filter(
                                        response ->
                                                org.openfinance.util.RegexSearchUtil.matches(
                                                        response.getName(), keyword, keywordRegex))
                                .collect(Collectors.toList());
            }

            // Value filters compare the effective value in the user's base currency so assets in
            // different currencies are comparable and match the displayed portfolio values.
            if (criteria.getValueMin() != null) {
                BigDecimal min = criteria.getValueMin();
                results =
                        results.stream()
                                .filter(res -> effectiveBaseValue(res).compareTo(min) >= 0)
                                .collect(Collectors.toList());
            }
            if (criteria.getValueMax() != null) {
                BigDecimal max = criteria.getValueMax();
                results =
                        results.stream()
                                .filter(res -> effectiveBaseValue(res).compareTo(max) <= 0)
                                .collect(Collectors.toList());
            }

            // Sort in-memory (name/value are encrypted or computed; other fields handled too).
            Comparator<AssetResponse> comparator = buildAssetComparator(pageable.getSort());
            if (comparator != null) {
                results = new ArrayList<>(results);
                results.sort(
                        comparator.thenComparing(
                                AssetResponse::getId,
                                Comparator.nullsLast(Comparator.naturalOrder())));
            }

            int start = (int) pageable.getOffset();
            int end = Math.min(start + pageable.getPageSize(), results.size());
            List<AssetResponse> pageContent =
                    start <= end ? results.subList(start, end) : List.of();

            log.debug(
                    "In-memory asset filter/sort: {} total, offset {}, size {} ({})",
                    results.size(),
                    start,
                    pageable.getPageSize(),
                    pageContent.size());

            return new PageImpl<>(new ArrayList<>(pageContent), pageable, results.size());
        }

        // Fast path: no encrypted-field filtering/sorting → push everything to the DB
        Specification<Asset> spec = AssetSpecification.buildSpecification(userId, criteria);

        // Execute paginated query
        Page<Asset> assetPage = assetRepository.findAll(spec, pageable);

        log.debug(
                "Found {} assets (page {}/{})",
                assetPage.getNumberOfElements(),
                assetPage.getNumber() + 1,
                assetPage.getTotalPages());

        // Decrypt and map to responses (preserving pagination metadata)
        return assetPage.map(asset -> toResponseWithDecryption(asset));
    }

    /**
     * Sort fields that must be handled in-memory because they are AES-encrypted ({@code name},
     * {@code quantity}, {@code purchasePrice}) or computed from encrypted fields ({@code
     * totalValue}, {@code valueInBaseCurrency}, {@code unrealizedGain}, {@code gainPercentage}).
     */
    private static final Set<String> IN_MEMORY_ASSET_SORT_FIELDS =
            Set.of(
                    "name",
                    "quantity",
                    "purchasePrice",
                    "totalValue",
                    "valueInBaseCurrency",
                    "unrealizedGain",
                    "gainPercentage");

    /**
     * Returns the asset's effective total value in the user's base currency, used for value
     * filtering and sorting. Falls back to the native total value when no conversion is available.
     */
    private static BigDecimal effectiveBaseValue(AssetResponse res) {
        return res.getValueInBaseCurrency() != null
                ? res.getValueInBaseCurrency()
                : res.getTotalValue();
    }

    /**
     * Builds an in-memory comparator from the requested sort orders. Supports value (compared in
     * base currency), {@code name} (case-insensitive), {@code unrealizedGain}, {@code purchaseDate}
     * and {@code createdAt}. Returns {@code null} when no supported sort order is present.
     */
    private static Comparator<AssetResponse> buildAssetComparator(Sort sort) {
        Comparator<AssetResponse> comparator = null;
        for (Sort.Order order : sort) {
            Comparator<AssetResponse> next;
            switch (order.getProperty()) {
                case "totalValue":
                case "valueInBaseCurrency":
                    next =
                            Comparator.comparing(
                                    AssetService::effectiveBaseValue,
                                    Comparator.nullsLast(Comparator.naturalOrder()));
                    break;
                case "unrealizedGain":
                    next =
                            Comparator.comparing(
                                    AssetResponse::getUnrealizedGain,
                                    Comparator.nullsLast(Comparator.naturalOrder()));
                    break;
                case "name":
                    next =
                            Comparator.<AssetResponse, String>comparing(
                                    res -> res.getName() == null ? "" : res.getName(),
                                    String.CASE_INSENSITIVE_ORDER);
                    break;
                case "purchaseDate":
                    next =
                            Comparator.comparing(
                                    AssetResponse::getPurchaseDate,
                                    Comparator.nullsLast(Comparator.naturalOrder()));
                    break;
                case "createdAt":
                    next =
                            Comparator.comparing(
                                    AssetResponse::getCreatedAt,
                                    Comparator.nullsLast(Comparator.naturalOrder()));
                    break;
                default:
                    continue;
            }
            if (order.isDescending()) {
                next = next.reversed();
            }
            comparator = (comparator == null) ? next : comparator.thenComparing(next);
        }
        return comparator;
    }

    /**
     * Retrieves all assets for a specific account.
     *
     * <p>Returns all assets linked to the specified account with decrypted data.
     *
     * <p>Requirement REQ-2.6.2: Retrieve assets by account
     *
     * @param accountId the ID of the account
     * @param userId the ID of the user (for authorization)
     * @param encryptionKey the AES-256 encryption key for decrypting sensitive fields
     * @return list of assets with decrypted data and calculated fields (may be empty)
     * @throws AccountNotFoundException if account not found or doesn't belong to user
     * @throws IllegalArgumentException if any parameter is null
     */
    @Transactional(readOnly = true)
    public List<AssetResponse> getAssetsByAccountId(Long accountId, Long userId) {
        log.debug("Retrieving assets for account {}: userId={}", accountId, userId);

        if (accountId == null) {
            throw new IllegalArgumentException("Account ID cannot be null");
        }
        if (userId == null) {
            throw new IllegalArgumentException("User ID cannot be null");
        }
        // Validate account ownership
        validateAccountOwnership(accountId, userId);

        // Fetch assets for account
        List<Asset> assets = assetRepository.findByUserIdAndAccountId(userId, accountId);

        log.debug("Found {} assets for account {} (userId={})", assets.size(), accountId, userId);

        // Decrypt and map to responses with calculated fields
        return assets.stream()
                .map(asset -> toResponseWithDecryption(asset))
                .collect(Collectors.toList());
    }

    /**
     * Retrieves all assets of a specific type for a user.
     *
     * <p>Useful for filtering portfolio by asset type (stocks, crypto, bonds, etc.).
     *
     * <p>Requirement REQ-2.6.3: Filter assets by type
     *
     * @param userId the ID of the user
     * @param type the asset type to filter by
     * @param encryptionKey the AES-256 encryption key for decrypting sensitive fields
     * @return list of assets of the specified type (may be empty)
     * @throws IllegalArgumentException if any parameter is null
     */
    @Transactional(readOnly = true)
    public List<AssetResponse> getAssetsByType(Long userId, AssetType type) {
        log.debug("Retrieving assets of type {} for user {}", type, userId);

        if (userId == null) {
            throw new IllegalArgumentException("User ID cannot be null");
        }
        if (type == null) {
            throw new IllegalArgumentException("Asset type cannot be null");
        }
        // Fetch assets by type
        List<Asset> assets = assetRepository.findByUserIdAndType(userId, type);

        log.debug("Found {} assets of type {} for user {}", assets.size(), type, userId);

        // Decrypt and map to responses with calculated fields
        return assets.stream()
                .map(asset -> toResponseWithDecryption(asset))
                .collect(Collectors.toList());
    }

    /**
     * Calculates total portfolio value grouped by currency.
     *
     * <p>Returns a map of currency code to total value in that currency. Iterates through all
     * assets to identify unique currencies and calculate totals.
     *
     * <p>Requirement REQ-2.6.3: Calculate total portfolio value
     *
     * <p>Requirement REQ-2.8: Multi-currency support
     *
     * @param userId the ID of the user
     * @return map of currency code to total asset value (may be empty)
     * @throws IllegalArgumentException if userId is null
     */
    @Transactional(readOnly = true)
    public Map<String, BigDecimal> getTotalValueByCurrency(Long userId) {
        log.debug("Calculating total asset value by currency for user {}", userId);

        if (userId == null) {
            throw new IllegalArgumentException("User ID cannot be null");
        }

        // Get all assets to determine currencies
        List<Asset> assets = assetRepository.findByUserId(userId);

        // Group by currency and sum values
        Map<String, BigDecimal> valuesByCurrency =
                assets.stream()
                        .collect(
                                Collectors.groupingBy(
                                        Asset::getCurrency,
                                        Collectors.reducing(
                                                BigDecimal.ZERO,
                                                Asset::getTotalValue,
                                                BigDecimal::add)));

        log.debug("Total asset value for user {}: {}", userId, valuesByCurrency);

        return valuesByCurrency;
    }

    /**
     * Calculates total portfolio cost basis grouped by currency.
     *
     * <p>Returns a map of currency code to total cost basis in that currency. Iterates through all
     * assets to identify unique currencies and calculate totals.
     *
     * <p>Requirement REQ-2.6.3: Calculate total cost basis
     *
     * @param userId the ID of the user
     * @return map of currency code to total cost basis (may be empty)
     * @throws IllegalArgumentException if userId is null
     */
    @Transactional(readOnly = true)
    public Map<String, BigDecimal> getTotalCostByCurrency(Long userId) {
        log.debug("Calculating total cost basis by currency for user {}", userId);

        if (userId == null) {
            throw new IllegalArgumentException("User ID cannot be null");
        }

        // Get all assets to determine currencies
        List<Asset> assets = assetRepository.findByUserId(userId);

        // Group by currency and sum costs
        Map<String, BigDecimal> costsByCurrency =
                assets.stream()
                        .collect(
                                Collectors.groupingBy(
                                        Asset::getCurrency,
                                        Collectors.reducing(
                                                BigDecimal.ZERO,
                                                Asset::getTotalCost,
                                                BigDecimal::add)));

        log.debug("Total cost basis for user {}: {}", userId, costsByCurrency);

        return costsByCurrency;
    }

    /**
     * Returns a lightweight summary list of assets for the given user.
     *
     * <p>This method is optimised for high-volume list use-cases where only the most essential
     * asset fields are required. It maps results to the smaller {@link AssetSummaryResponse}
     * projection, avoiding the full currency-conversion metadata, calculated portfolio fields, and
     * physical-asset field resolution that {@link #toResponseWithDecryption} performs.
     *
     * <p>Requirement TASK-14.1.3: Sparse fieldsets / summary projection.
     *
     * @param userId the ID of the user
     * @param encryptionKey the AES-256 encryption key for decrypting asset names
     * @return list of lightweight asset summaries (may be empty)
     * @throws IllegalArgumentException if userId or encryptionKey is null
     */
    @Transactional(readOnly = true)
    public List<AssetSummaryResponse> getAssetsSummary(Long userId) {
        log.debug("Retrieving asset summaries for user {}", userId);

        if (userId == null) {
            throw new IllegalArgumentException("User ID cannot be null");
        }
        List<Asset> assets = assetRepository.findByUserId(userId);

        log.debug("Found {} assets for summary (userId={})", assets.size(), userId);

        return assets.stream()
                .map(
                        asset -> {
                            // Name already decrypted by JPA converter
                            String decryptedName = asset.getName();
                            BigDecimal totalValue =
                                    (asset.getQuantity() != null && asset.getCurrentPrice() != null)
                                            ? asset.getQuantity().multiply(asset.getCurrentPrice())
                                            : null;
                            return AssetSummaryResponse.builder()
                                    .id(asset.getId())
                                    .name(decryptedName)
                                    .symbol(asset.getSymbol())
                                    .type(asset.getType() != null ? asset.getType().name() : null)
                                    .quantity(asset.getQuantity())
                                    .currency(asset.getCurrency())
                                    .currentPrice(asset.getCurrentPrice())
                                    .totalValue(totalValue)
                                    .build();
                        })
                .collect(Collectors.toList());
    }

    /**
     * Validates that the specified account exists and belongs to the user.
     *
     * @param accountId the account ID to validate
     * @param userId the user ID to check ownership
     * @throws AccountNotFoundException if account not found or doesn't belong to user
     */
    private void validateAccountOwnership(Long accountId, Long userId) {
        accountRepository
                .findByIdAndUserId(accountId, userId)
                .orElseThrow(() -> AccountNotFoundException.byIdAndUser(accountId, userId));
    }

    /**
     * Helper method to decrypt sensitive fields and map to response DTO.
     *
     * <p>The AssetMapper automatically populates calculated fields (totalValue, totalCost,
     * unrealizedGain, gainPercentage, holdingDays, depreciatedValue, conditionAdjustedValue,
     * isPhysical, isWarrantyValid) via its @AfterMapping method.
     *
     * @param asset the asset entity with encrypted fields
     * @param encryptionKey the encryption key for decryption
     * @return the asset response with decrypted fields and calculated values
     */
    private AssetResponse toResponseWithDecryption(Asset asset) {
        // Map to response first (mapper will populate calculated fields automatically)
        AssetResponse response = assetMapper.toResponse(asset);

        // Standardize: if no key, return response with encrypted/null fields
        if (!canReadSensitiveFields()) {
            return response;
        }

        // Fields already decrypted by JPA converter — just set them on response
        response.setName(asset.getName());
        response.setNotes(asset.getNotes());
        response.setSerialNumber(asset.getSerialNumber());
        response.setBrand(asset.getBrand());
        response.setModel(asset.getModel());

        // Account name already decrypted by JPA converter
        log.debug(
                "Asset account relationship: accountId={}, account={}",
                asset.getAccountId(),
                asset.getAccount());
        if (asset.getAccount() != null && asset.getAccount().getName() != null) {
            response.setAccountName(asset.getAccount().getName());
            log.debug("Account name set: {}", asset.getAccount().getName());
        } else if (asset.getAccountId() != null) {
            log.warn(
                    "Account relationship not loaded for asset id={}, accountId={}",
                    asset.getId(),
                    asset.getAccountId());
        }

        // Populate currency conversion metadata (Requirement REQ-3.2, REQ-3.5)
        populateConversionFields(
                response, asset.getUserId(), asset.getCurrency(), response.getTotalValue());

        return response;
    }

    /**
     * Populates currency conversion metadata fields on an AssetResponse.
     *
     * <p>Fetches the user's base currency from the database, then attempts to convert the asset's
     * {@code totalValue} to the base currency using {@link ExchangeRateService}. On failure, falls
     * back to the native amount with {@code isConverted=false}.
     *
     * <p>Also performs secondary currency conversion when the user has a secondary currency
     * configured and it differs from the native currency.
     *
     * <p>Requirement REQ-3.2: AssetService populates conversion fields
     *
     * <p>Requirement REQ-3.5: Graceful fallback when conversion unavailable
     *
     * <p>Requirement REQ-3.6: isConverted semantics
     *
     * <p>Requirement REQ-4.2, REQ-4.5: Secondary conversion logic
     *
     * @param response the response DTO to populate
     * @param userId the asset owner's user ID
     * @param nativeCurrency the asset's native currency code (ISO 4217)
     * @param nativeValue the native total value
     */
    private void populateConversionFields(
            AssetResponse response,
            Long userId,
            String nativeCurrency,
            java.math.BigDecimal nativeValue) {
        CurrencyConversionHelper.ConversionResult r =
                currencyConversionHelper.convert(
                        userId, nativeCurrency, nativeValue, true, null, "asset");
        response.setBaseCurrency(r.baseCurrency());
        response.setValueInBaseCurrency(r.amountInBaseCurrency());
        response.setExchangeRate(r.exchangeRate());
        response.setIsConverted(r.converted());
        if (r.secondaryCurrency() != null) {
            response.setSecondaryCurrency(r.secondaryCurrency());
            response.setValueInSecondaryCurrency(r.amountInSecondaryCurrency());
            response.setSecondaryExchangeRate(r.secondaryExchangeRate());
        }
    }

    /**
     * Resolves the user's base currency, delegating to {@link DefaultCurrencyProvider} (which falls
     * back to the application default when the user has no configured base currency).
     *
     * @param userId the user ID
     * @return the user's base currency or the application default
     */
    private String resolveBaseCurrency(Long userId) {
        return defaultCurrencyProvider.resolveForUser(userId);
    }

    /**
     * Invalidates net worth snapshots from {@code fromDate} onward (up to today). Called after any
     * asset write so the dashboard chart rebuilds affected months.
     */
    private void invalidateSnapshotsFrom(Long userId, LocalDate fromDate) {
        if (fromDate == null) return;
        try {
            int deleted =
                    netWorthRepository.deleteByUserIdAndSnapshotDateBetween(
                            userId, fromDate.withDayOfMonth(1), LocalDate.now());
            if (deleted > 0) {
                log.debug(
                        "Invalidated {} net worth snapshots for user {} (asset change from {})",
                        deleted,
                        userId,
                        fromDate);
            }
        } catch (Exception e) {
            log.warn(
                    "Could not invalidate net worth snapshots for user {} after asset change from {}: {}",
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

    private void indexAssetSearchTokens(Asset asset, String name) {
        try {
            SecretKey key = EncryptionContext.getKey();
            if (key == null) {
                return;
            }
            SecretKey searchKey = searchTokenService.deriveSearchKey(key);
            searchTokenService.indexEntity(
                    asset.getUserId(),
                    "ASSET",
                    asset.getId(),
                    java.util.List.<String[]>of(new String[] {"name", name}),
                    searchKey);
        } catch (Exception e) {
            log.warn("Failed to index asset {} search tokens: {}", asset.getId(), e.getMessage());
        }
    }

    private boolean canReadSensitiveFields() {
        return !encryptionProperties.isEnabled() || EncryptionContext.getKey() != null;
    }
}
