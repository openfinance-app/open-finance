package org.openfinance.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import javax.crypto.SecretKey;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.openfinance.dto.AssetRequest;
import org.openfinance.dto.AssetResponse;
import org.openfinance.dto.AssetSearchCriteria;
import org.openfinance.dto.AssetSummaryResponse;
import org.openfinance.entity.Account;
import org.openfinance.entity.Asset;
import org.openfinance.entity.AssetType;
import org.openfinance.entity.User;
import org.openfinance.exception.AccountNotFoundException;
import org.openfinance.exception.AssetNotFoundException;
import org.openfinance.mapper.AssetMapper;
import org.openfinance.repository.AccountRepository;
import org.openfinance.repository.AssetRepository;
import org.openfinance.repository.CurrencyRepository;
import org.openfinance.repository.NetWorthRepository;
import org.openfinance.repository.UserRepository;
import org.openfinance.security.EncryptionService;
import org.openfinance.specification.AssetSpecification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
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
    private final AccountRepository accountRepository;
    private final CurrencyRepository currencyRepository;
    private final AssetMapper assetMapper;
    private final EncryptionService encryptionService;
    private final UserRepository userRepository;
    private final ExchangeRateService exchangeRateService;
    private final NetWorthRepository netWorthRepository;
    private final OperationHistoryService operationHistoryService;

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
    public AssetResponse createAsset(Long userId, AssetRequest request, SecretKey encryptionKey) {
        if (userId == null) {
            throw new IllegalArgumentException("User ID cannot be null");
        }
        if (request == null) {
            throw new IllegalArgumentException("Asset request cannot be null");
        }
        if (encryptionKey == null) {
            throw new IllegalArgumentException("Encryption key cannot be null");
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
        asset.setUserId(userId);
        asset.setCurrencyId(resolveCurrencyId(asset.getCurrency()));

        // Encrypt sensitive fields (Requirement 2.18: Encryption at rest)
        String encryptedName = encryptionService.encrypt(request.getName(), encryptionKey);
        asset.setName(encryptedName);

        if (request.getNotes() != null && !request.getNotes().isBlank()) {
            String encryptedNotes = encryptionService.encrypt(request.getNotes(), encryptionKey);
            asset.setNotes(encryptedNotes);
        }

        // Encrypt physical asset fields if present
        if (request.getSerialNumber() != null && !request.getSerialNumber().isBlank()) {
            String encryptedSerialNumber =
                    encryptionService.encrypt(request.getSerialNumber(), encryptionKey);
            asset.setSerialNumber(encryptedSerialNumber);
        }

        if (request.getBrand() != null && !request.getBrand().isBlank()) {
            String encryptedBrand = encryptionService.encrypt(request.getBrand(), encryptionKey);
            asset.setBrand(encryptedBrand);
        }

        if (request.getModel() != null && !request.getModel().isBlank()) {
            String encryptedModel = encryptionService.encrypt(request.getModel(), encryptionKey);
            asset.setModel(encryptedModel);
        }

        // Set lastUpdated timestamp to now (initial price entry)
        asset.setLastUpdated(LocalDateTime.now());

        // Save to database
        Asset savedAsset = assetRepository.save(asset);
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
        AssetResponse assetCreateResponse = toResponseWithDecryption(savedAsset, encryptionKey);

        // Record in operation history
        operationHistoryService.record(
                userId,
                org.openfinance.entity.EntityType.ASSET,
                savedAsset.getId(),
                request.getName(),
                org.openfinance.entity.OperationType.CREATE,
                (Object) null,
                null);

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
    public AssetResponse updateAsset(
            Long assetId, Long userId, AssetRequest request, SecretKey encryptionKey) {
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
        if (encryptionKey == null) {
            throw new IllegalArgumentException("Encryption key cannot be null");
        }

        // Fetch asset and verify ownership (Requirement 3.2: Authorization)
        Asset asset =
                assetRepository
                        .findByIdAndUserId(assetId, userId)
                        .orElseThrow(() -> AssetNotFoundException.byIdAndUser(assetId, userId));

        // Capture snapshot before update for history
        AssetResponse beforeAssetSnapshot = toResponseWithDecryption(asset, encryptionKey);

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

        // Re-encrypt sensitive fields (always re-encrypt the provided plaintext values)
        String encryptedName = encryptionService.encrypt(request.getName(), encryptionKey);
        asset.setName(encryptedName);

        if (request.getNotes() != null && !request.getNotes().isBlank()) {
            String encryptedNotes = encryptionService.encrypt(request.getNotes(), encryptionKey);
            asset.setNotes(encryptedNotes);
        } else if (request.getNotes() != null) {
            // explicit empty notes -> clear stored value
            asset.setNotes(null);
        }

        // Re-encrypt physical asset fields if provided
        if (request.getSerialNumber() != null && !request.getSerialNumber().isBlank()) {
            String encryptedSerialNumber =
                    encryptionService.encrypt(request.getSerialNumber(), encryptionKey);
            asset.setSerialNumber(encryptedSerialNumber);
        } else if (request.getSerialNumber() != null) {
            asset.setSerialNumber(null);
        }

        if (request.getBrand() != null && !request.getBrand().isBlank()) {
            String encryptedBrand = encryptionService.encrypt(request.getBrand(), encryptionKey);
            asset.setBrand(encryptedBrand);
        } else if (request.getBrand() != null) {
            asset.setBrand(null);
        }

        if (request.getModel() != null && !request.getModel().isBlank()) {
            String encryptedModel = encryptionService.encrypt(request.getModel(), encryptionKey);
            asset.setModel(encryptedModel);
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
        AssetResponse assetUpdateResponse = toResponseWithDecryption(updatedAsset, encryptionKey);

        // Record in operation history
        operationHistoryService.record(
                userId,
                org.openfinance.entity.EntityType.ASSET,
                assetId,
                request.getName(),
                org.openfinance.entity.OperationType.UPDATE,
                beforeAssetSnapshot,
                null);

        return assetUpdateResponse;
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
    public void deleteAsset(Long assetId, Long userId, SecretKey encryptionKey) {
        log.debug(
                "Deleting asset {}: userId={}, keyPresent={}",
                assetId,
                userId,
                encryptionKey != null);

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

        // Capture snapshot before delete for history (only if key provided)
        AssetResponse beforeDeleteSnapshot = null;
        String label = null;
        if (encryptionKey != null) {
            try {
                beforeDeleteSnapshot = toResponseWithDecryption(asset, encryptionKey);
                label = beforeDeleteSnapshot.getName();
            } catch (Exception e) {
                log.warn("Failed to capture snapshot for history: {}", e.getMessage());
            }
        }

        // Hard delete
        LocalDate assetPurchaseDate = asset.getPurchaseDate();
        assetRepository.delete(asset);
        invalidateSnapshotsFrom(userId, assetPurchaseDate);

        log.info("Asset deleted successfully: id={}, userId={}", assetId, userId);

        // Record in operation history
        operationHistoryService.record(
                userId,
                org.openfinance.entity.EntityType.ASSET,
                assetId,
                label != null ? label : "Asset " + assetId,
                org.openfinance.entity.OperationType.DELETE,
                beforeDeleteSnapshot,
                null);
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
    public AssetResponse getAssetById(Long assetId, Long userId, SecretKey encryptionKey) {
        log.debug("Retrieving asset {}: userId={}", assetId, userId);

        if (assetId == null) {
            throw new IllegalArgumentException("Asset ID cannot be null");
        }
        if (userId == null) {
            throw new IllegalArgumentException("User ID cannot be null");
        }
        if (encryptionKey == null) {
            throw new IllegalArgumentException("Encryption key cannot be null");
        }

        // Fetch asset and verify ownership (Requirement 3.2: Authorization)
        Asset asset =
                assetRepository
                        .findByIdAndUserId(assetId, userId)
                        .orElseThrow(() -> AssetNotFoundException.byIdAndUser(assetId, userId));

        // Decrypt and return response with calculated fields
        return toResponseWithDecryption(asset, encryptionKey);
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
    public List<AssetResponse> getAssetsByUserId(Long userId, SecretKey encryptionKey) {
        log.debug("Retrieving all assets for user {}", userId);

        if (userId == null) {
            throw new IllegalArgumentException("User ID cannot be null");
        }
        if (encryptionKey == null) {
            throw new IllegalArgumentException("Encryption key cannot be null");
        }

        // Fetch all assets for user
        List<Asset> assets = assetRepository.findByUserId(userId);

        log.debug("Found {} assets for user {}", assets.size(), userId);

        // Decrypt and map to responses with calculated fields
        return assets.stream()
                .map(asset -> toResponseWithDecryption(asset, encryptionKey))
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
            Long userId, AssetSearchCriteria criteria, Pageable pageable, SecretKey encryptionKey) {

        if (userId == null) {
            throw new IllegalArgumentException("User ID cannot be null");
        }
        if (criteria == null) {
            throw new IllegalArgumentException("Search criteria cannot be null");
        }
        if (pageable == null) {
            throw new IllegalArgumentException("Pageable cannot be null");
        }
        if (encryptionKey == null) {
            throw new IllegalArgumentException("Encryption key cannot be null");
        }

        log.debug(
                "Searching assets for user {}: keyword={}, type={}, accountId={}",
                userId,
                criteria.getKeyword(),
                criteria.getType(),
                criteria.getAccountId());

        boolean hasKeyword =
                criteria.getKeyword() != null && !criteria.getKeyword().trim().isEmpty();

        if (hasKeyword) {
            // Since the name field is encrypted at rest, LIKE queries on the DB cannot
            // match decrypted keywords. Fetch all assets matching non-keyword criteria,
            // decrypt them, then filter by keyword in memory before applying pagination.
            AssetSearchCriteria criteriaWithoutKeyword =
                    AssetSearchCriteria.builder()
                            .type(criteria.getType())
                            .accountId(criteria.getAccountId())
                            .currency(criteria.getCurrency())
                            .symbol(criteria.getSymbol())
                            .purchaseDateFrom(criteria.getPurchaseDateFrom())
                            .purchaseDateTo(criteria.getPurchaseDateTo())
                            .valueMin(criteria.getValueMin())
                            .valueMax(criteria.getValueMax())
                            .build();

            Specification<Asset> specWithoutKeyword =
                    AssetSpecification.buildSpecification(userId, criteriaWithoutKeyword);
            List<Asset> allMatching = assetRepository.findAll(specWithoutKeyword);

            String lowerKeyword = criteria.getKeyword().trim().toLowerCase();
            List<AssetResponse> filtered =
                    allMatching.stream()
                            .map(asset -> toResponseWithDecryption(asset, encryptionKey))
                            .filter(
                                    response ->
                                            response.getName() != null
                                                    && response.getName()
                                                            .toLowerCase()
                                                            .contains(lowerKeyword))
                            .collect(Collectors.toList());

            // Apply sorting from pageable
            // (already returned in natural order from DB; client can re-sort)
            int pageSize = pageable.getPageSize();
            int pageNumber = pageable.getPageNumber();
            int fromIndex = Math.min(pageNumber * pageSize, filtered.size());
            int toIndex = Math.min(fromIndex + pageSize, filtered.size());
            List<AssetResponse> pageContent = filtered.subList(fromIndex, toIndex);

            log.debug(
                    "Keyword search found {} assets after decryption filter (page {}/{})",
                    filtered.size(),
                    pageNumber + 1,
                    (filtered.size() + pageSize - 1) / pageSize);

            return new PageImpl<>(pageContent, pageable, filtered.size());
        }

        // Build dynamic specification (no keyword – all other filters applied at DB
        // level)
        Specification<Asset> spec = AssetSpecification.buildSpecification(userId, criteria);

        // Execute paginated query
        Page<Asset> assetPage = assetRepository.findAll(spec, pageable);

        log.debug(
                "Found {} assets (page {}/{})",
                assetPage.getNumberOfElements(),
                assetPage.getNumber() + 1,
                assetPage.getTotalPages());

        // Decrypt and map to responses (preserving pagination metadata)
        return assetPage.map(asset -> toResponseWithDecryption(asset, encryptionKey));
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
    public List<AssetResponse> getAssetsByAccountId(
            Long accountId, Long userId, SecretKey encryptionKey) {
        log.debug("Retrieving assets for account {}: userId={}", accountId, userId);

        if (accountId == null) {
            throw new IllegalArgumentException("Account ID cannot be null");
        }
        if (userId == null) {
            throw new IllegalArgumentException("User ID cannot be null");
        }
        if (encryptionKey == null) {
            throw new IllegalArgumentException("Encryption key cannot be null");
        }

        // Validate account ownership
        validateAccountOwnership(accountId, userId);

        // Fetch assets for account
        List<Asset> assets = assetRepository.findByUserIdAndAccountId(userId, accountId);

        log.debug("Found {} assets for account {} (userId={})", assets.size(), accountId, userId);

        // Decrypt and map to responses with calculated fields
        return assets.stream()
                .map(asset -> toResponseWithDecryption(asset, encryptionKey))
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
    public List<AssetResponse> getAssetsByType(
            Long userId, AssetType type, SecretKey encryptionKey) {
        log.debug("Retrieving assets of type {} for user {}", type, userId);

        if (userId == null) {
            throw new IllegalArgumentException("User ID cannot be null");
        }
        if (type == null) {
            throw new IllegalArgumentException("Asset type cannot be null");
        }
        if (encryptionKey == null) {
            throw new IllegalArgumentException("Encryption key cannot be null");
        }

        // Fetch assets by type
        List<Asset> assets = assetRepository.findByUserIdAndType(userId, type);

        log.debug("Found {} assets of type {} for user {}", assets.size(), type, userId);

        // Decrypt and map to responses with calculated fields
        return assets.stream()
                .map(asset -> toResponseWithDecryption(asset, encryptionKey))
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
    public List<AssetSummaryResponse> getAssetsSummary(Long userId, SecretKey encryptionKey) {
        log.debug("Retrieving asset summaries for user {}", userId);

        if (userId == null) {
            throw new IllegalArgumentException("User ID cannot be null");
        }
        if (encryptionKey == null) {
            throw new IllegalArgumentException("Encryption key cannot be null");
        }

        List<Asset> assets = assetRepository.findByUserId(userId);

        log.debug("Found {} assets for summary (userId={})", assets.size(), userId);

        return assets.stream()
                .map(
                        asset -> {
                            String decryptedName;
                            try {
                                decryptedName =
                                        encryptionService.decrypt(asset.getName(), encryptionKey);
                            } catch (Exception e) {
                                log.error(
                                        "Failed to decrypt asset name for id={}", asset.getId(), e);
                                decryptedName = "Unknown Asset";
                            }
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
    private AssetResponse toResponseWithDecryption(Asset asset, SecretKey encryptionKey) {
        // Map to response first (mapper will populate calculated fields automatically)
        AssetResponse response = assetMapper.toResponse(asset);

        // Standardize: if no key, return response with encrypted/null fields
        if (encryptionKey == null) {
            return response;
        }

        // Decrypt sensitive fields and set on response (NOT on entity)
        try {
            String decryptedName = encryptionService.decrypt(asset.getName(), encryptionKey);
            response.setName(decryptedName);
        } catch (Exception e) {
            log.warn("Failed to decrypt asset name for id={}: {}", asset.getId(), e.getMessage());
        }

        if (asset.getNotes() != null && !asset.getNotes().isBlank()) {
            try {
                String decryptedNotes = encryptionService.decrypt(asset.getNotes(), encryptionKey);
                response.setNotes(decryptedNotes);
            } catch (Exception e) {
                log.warn("Failed to decrypt asset notes for id={}", asset.getId());
            }
        }

        // Decrypt physical asset fields if present
        if (asset.getSerialNumber() != null && !asset.getSerialNumber().isBlank()) {
            try {
                String decryptedSerialNumber =
                        encryptionService.decrypt(asset.getSerialNumber(), encryptionKey);
                response.setSerialNumber(decryptedSerialNumber);
            } catch (Exception e) {
                log.warn("Failed to decrypt serial number for asset id={}", asset.getId());
            }
        }

        if (asset.getBrand() != null && !asset.getBrand().isBlank()) {
            try {
                String decryptedBrand = encryptionService.decrypt(asset.getBrand(), encryptionKey);
                response.setBrand(decryptedBrand);
            } catch (Exception e) {
                log.warn("Failed to decrypt brand for asset id={}", asset.getId());
            }
        }

        if (asset.getModel() != null && !asset.getModel().isBlank()) {
            try {
                String decryptedModel = encryptionService.decrypt(asset.getModel(), encryptionKey);
                response.setModel(decryptedModel);
            } catch (Exception e) {
                log.warn("Failed to decrypt model for asset id={}", asset.getId());
            }
        }

        // Decrypt and set account name if asset has an account
        log.debug(
                "Asset account relationship: accountId={}, account={}",
                asset.getAccountId(),
                asset.getAccount());
        if (asset.getAccount() != null && asset.getAccount().getName() != null) {
            log.debug("Decrypting account name: encrypted={}", asset.getAccount().getName());
            try {
                String decryptedAccountName =
                        encryptionService.decrypt(asset.getAccount().getName(), encryptionKey);
                response.setAccountName(decryptedAccountName);
                log.debug("Account name decrypted and set: {}", decryptedAccountName);
            } catch (Exception e) {
                log.warn("Failed to decrypt account name for asset id={}", asset.getId());
            }
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
        User user = userId != null ? userRepository.findById(userId).orElse(null) : null;
        String baseCurrency =
                user != null && user.getBaseCurrency() != null && !user.getBaseCurrency().isBlank()
                        ? user.getBaseCurrency()
                        : "USD";
        String secCurrency = user != null ? user.getSecondaryCurrency() : null;
        response.setBaseCurrency(baseCurrency);

        // Step 1: Base conversion
        boolean needsConversion = nativeCurrency != null && !nativeCurrency.equals(baseCurrency);
        if (!needsConversion || nativeValue == null) {
            response.setValueInBaseCurrency(nativeValue);
            response.setIsConverted(false);
        } else {
            try {
                java.math.BigDecimal rate =
                        exchangeRateService.getExchangeRate(nativeCurrency, baseCurrency, null);
                java.math.BigDecimal converted =
                        exchangeRateService.convert(nativeValue, nativeCurrency, baseCurrency);
                response.setValueInBaseCurrency(converted);
                response.setExchangeRate(rate);
                response.setIsConverted(true);
            } catch (Exception e) {
                log.warn(
                        "Currency conversion failed for asset (user={}, {}->{}) – falling back to native: {}",
                        userId,
                        nativeCurrency,
                        baseCurrency,
                        e.getMessage());
                response.setValueInBaseCurrency(nativeValue);
                response.setIsConverted(false);
            }
        }

        // Step 2: Secondary conversion (Requirement REQ-4.2, REQ-4.5)
        if (secCurrency != null
                && !secCurrency.isBlank()
                && nativeCurrency != null
                && !nativeCurrency.equals(secCurrency)
                && nativeValue != null) {
            try {
                java.math.BigDecimal secRate =
                        exchangeRateService.getExchangeRate(nativeCurrency, secCurrency, null);
                java.math.BigDecimal secAmount =
                        exchangeRateService.convert(nativeValue, nativeCurrency, secCurrency);
                response.setValueInSecondaryCurrency(secAmount);
                response.setSecondaryCurrency(secCurrency);
                response.setSecondaryExchangeRate(secRate);
            } catch (Exception e) {
                log.warn(
                        "Secondary currency conversion failed for asset (user={}, {}->{}) – omitting: {}",
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
     * Resolves the user's base currency, defaulting to "USD" if not configured.
     *
     * @param userId the user ID
     * @return the user's base currency or "USD" as fallback
     */
    private String resolveBaseCurrency(Long userId) {
        if (userId == null) {
            return "USD";
        }
        return userRepository
                .findById(userId)
                .map(User::getBaseCurrency)
                .filter(bc -> bc != null && !bc.isBlank())
                .orElse("USD");
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
}
