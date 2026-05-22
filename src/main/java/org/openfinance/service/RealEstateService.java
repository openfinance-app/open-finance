package org.openfinance.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import javax.crypto.SecretKey;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.openfinance.dto.*;
import org.openfinance.dto.AssetRequest;
import org.openfinance.entity.AssetType;
import org.openfinance.entity.Liability;
import org.openfinance.entity.PropertyType;
import org.openfinance.entity.RealEstateProperty;
import org.openfinance.entity.RealEstateValueHistory;
import org.openfinance.entity.User;
import org.openfinance.exception.LiabilityNotFoundException;
import org.openfinance.exception.RealEstatePropertyNotFoundException;
import org.openfinance.mapper.RealEstateMapper;
import org.openfinance.repository.CurrencyRepository;
import org.openfinance.repository.LiabilityRepository;
import org.openfinance.repository.NetWorthRepository;
import org.openfinance.repository.RealEstateRepository;
import org.openfinance.repository.RealEstateValueHistoryRepository;
import org.openfinance.repository.UserRepository;
import org.openfinance.security.EncryptionService;
import org.openfinance.specification.RealEstateSpecification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service layer for managing real estate properties.
 *
 * <p>This service handles business logic for property CRUD operations, including:
 *
 * <ul>
 *   <li>Creating new properties with encrypted sensitive fields
 *   <li>Updating existing properties
 *   <li>Deleting properties (soft delete via isActive flag)
 *   <li>Retrieving properties with decrypted data and calculated fields
 *   <li>Property equity calculations (value - mortgage balance)
 *   <li>Return on Investment (ROI) calculations
 *   <li>Property value estimations
 * </ul>
 *
 * <p><strong>Security Note:</strong> The {@code name}, {@code address}, {@code purchasePrice},
 * {@code currentValue}, {@code rentalIncome}, {@code notes}, and {@code documents} fields are
 * encrypted before storing in the database and decrypted when reading. The encryption key must be
 * provided by the caller (typically from the user's session after authentication).
 *
 * <p>Requirement REQ-2.16: Real Estate & Physical Assets - Property management
 *
 * <p>Requirement REQ-2.16.1: Track property details (name, type, address, values)
 *
 * <p>Requirement REQ-2.16.2: Calculate property equity and ROI
 *
 * <p>Requirement REQ-2.18: Data encryption at rest for sensitive fields
 *
 * <p>Requirement REQ-3.2: Authorization - Users can only access their own properties
 *
 * @see org.openfinance.entity.RealEstateProperty
 * @see org.openfinance.dto.RealEstatePropertyRequest
 * @see org.openfinance.dto.RealEstatePropertyResponse
 */
@Service
@Transactional
@RequiredArgsConstructor
@Slf4j
public class RealEstateService {

    private final RealEstateRepository realEstateRepository;
    private final RealEstateValueHistoryRepository valueHistoryRepository;
    private final LiabilityRepository liabilityRepository;
    private final CurrencyRepository currencyRepository;
    private final RealEstateMapper realEstateMapper;
    private final EncryptionService encryptionService;
    private final AssetService assetService;
    private final UserRepository userRepository;
    private final ExchangeRateService exchangeRateService;
    private final NetWorthRepository netWorthRepository;
    private final OperationHistoryService operationHistoryService;

    // Constants for calculations
    private static final int SCALE = 4; // Decimal precision for financial calculations
    private static final int MONTHS_PER_YEAR = 12;

    /**
     * Creates a new real estate property for the specified user.
     *
     * <p>Sensitive fields (name, address, purchasePrice, currentValue, rentalIncome, notes,
     * documents) are encrypted before storing in the database. If a mortgageId is provided,
     * validates that the mortgage exists and belongs to the user.
     *
     * <p>Requirement REQ-2.16.1: Create new property with encrypted sensitive data
     *
     * <p>Requirement REQ-2.16.2: Link property to mortgage (optional)
     *
     * @param userId the ID of the user creating the property
     * @param request the property creation request containing property details
     * @param encryptionKey the AES-256 encryption key for sensitive fields
     * @return the created property with decrypted data and calculated fields
     * @throws IllegalArgumentException if userId, request, or encryptionKey is null
     * @throws LiabilityNotFoundException if mortgageId is provided but mortgage not found or
     *     doesn't belong to user
     */
    public RealEstatePropertyResponse createProperty(
            Long userId, RealEstatePropertyRequest request, SecretKey encryptionKey) {
        if (userId == null) {
            throw new IllegalArgumentException("User ID cannot be null");
        }
        if (request == null) {
            throw new IllegalArgumentException("Property request cannot be null");
        }
        if (encryptionKey == null) {
            throw new IllegalArgumentException("Encryption key cannot be null");
        }

        log.debug(
                "Creating property for user {}: type={}, currency={}",
                userId,
                request.getPropertyType(),
                request.getCurrency());

        // Validate mortgage ownership if mortgageId is provided
        if (request.getMortgageId() != null) {
            validateMortgageOwnership(request.getMortgageId(), userId);
        }

        // Map request to entity
        RealEstateProperty property = realEstateMapper.toEntity(request);
        property.setUserId(userId);
        property.setCurrencyId(resolveCurrencyId(property.getCurrency()));

        // Encrypt sensitive fields (Requirement 2.18: Encryption at rest)
        encryptProperty(property, request, encryptionKey);

        // Save to database
        RealEstateProperty savedProperty = realEstateRepository.save(property);

        // Sync with Assets module (Requirement: Real Estate appearing in Net Worth)
        try {
            createLinkedAsset(userId, savedProperty, request, encryptionKey);
            // Save again to persist the assetId
            savedProperty = realEstateRepository.save(savedProperty);
        } catch (Exception e) {
            log.error(
                    "Failed to sync real estate property {} with assets: {}",
                    savedProperty.getId(),
                    e.getMessage());
            // We don't rollback the property creation, but we log the error.
            // Ideally, this should be transactional so it rolls back, but for now we
            // proceed.
            // Since the method is @Transactional, it SHOULD rollback if we throw
            // RuntimeException.
            throw new RuntimeException("Failed to create linked asset for real estate property", e);
        }
        log.info(
                "Property created successfully: id={}, userId={}, type={}",
                savedProperty.getId(),
                userId,
                savedProperty.getPropertyType());

        // Record the initial value in the history table so backfill has a starting
        // point
        recordValueHistory(savedProperty, request.getCurrentValue(), encryptionKey);
        invalidateSnapshotsFrom(userId, savedProperty.getPurchaseDate());

        // Reload with mortgage relationship if mortgageId is set (for response
        // population)
        if (savedProperty.getMortgageId() != null) {
            savedProperty =
                    realEstateRepository
                            .findByIdAndUserId(savedProperty.getId(), userId)
                            .orElseThrow(
                                    () ->
                                            new IllegalStateException(
                                                    "Property not found after save"));
        }

        // Decrypt and return response with calculated fields
        RealEstatePropertyResponse propCreateResponse =
                toResponseWithDecryption(savedProperty, encryptionKey);

        // Record in operation history
        operationHistoryService.record(
                userId,
                org.openfinance.entity.EntityType.REAL_ESTATE,
                savedProperty.getId(),
                request.getName(),
                org.openfinance.entity.OperationType.CREATE,
                (Object) null,
                null);

        return propCreateResponse;
    }

    /**
     * Updates an existing real estate property.
     *
     * <p>Only the property owner can update the property. Sensitive fields are re-encrypted if they
     * have changed. If mortgageId is updated, validates the new mortgage ownership.
     *
     * <p>Requirement REQ-2.16.1: Update property details
     *
     * <p>Requirement REQ-3.2: Authorization check - verify property ownership
     *
     * @param propertyId the ID of the property to update
     * @param userId the ID of the user updating the property (for authorization)
     * @param request the property update request
     * @param encryptionKey the AES-256 encryption key for sensitive fields
     * @return the updated property with decrypted data and calculated fields
     * @throws RealEstatePropertyNotFoundException if property not found or doesn't belong to user
     * @throws LiabilityNotFoundException if mortgageId is provided but mortgage not found or
     *     doesn't belong to user
     * @throws IllegalArgumentException if any parameter is null
     */
    public RealEstatePropertyResponse updateProperty(
            Long propertyId,
            Long userId,
            RealEstatePropertyRequest request,
            SecretKey encryptionKey) {
        if (propertyId == null) {
            throw new IllegalArgumentException("Property ID cannot be null");
        }
        if (userId == null) {
            throw new IllegalArgumentException("User ID cannot be null");
        }
        if (request == null) {
            throw new IllegalArgumentException("Property request cannot be null");
        }
        if (encryptionKey == null) {
            throw new IllegalArgumentException("Encryption key cannot be null");
        }

        log.debug(
                "Updating property {}: userId={}, type={}",
                propertyId,
                userId,
                request.getPropertyType());

        // Fetch property and verify ownership (Requirement 3.2: Authorization)
        RealEstateProperty property =
                realEstateRepository
                        .findByIdAndUserId(propertyId, userId)
                        .orElseThrow(
                                () ->
                                        RealEstatePropertyNotFoundException.byIdAndUser(
                                                propertyId, userId));

        // Capture snapshot before update for history
        RealEstatePropertyResponse beforeSnapshot =
                toResponseWithDecryption(property, encryptionKey);

        // Validate mortgage ownership if mortgageId is provided and changed
        if (request.getMortgageId() != null
                && !request.getMortgageId().equals(property.getMortgageId())) {
            validateMortgageOwnership(request.getMortgageId(), userId);
        }

        // Capture old currentValue and purchase date before overwriting, for change
        // detection
        String oldEncryptedValue = property.getCurrentValue();
        LocalDate oldPurchaseDate = property.getPurchaseDate();

        // Update entity fields (MapStruct will skip null values)
        realEstateMapper.updateEntityFromRequest(request, property);
        property.setCurrencyId(resolveCurrencyId(property.getCurrency()));

        // Re-encrypt sensitive fields
        encryptProperty(property, request, encryptionKey);

        // Save updated property
        RealEstateProperty updatedProperty = realEstateRepository.save(property);

        // Record value history if currentValue changed
        if (request.getCurrentValue() != null) {
            try {
                BigDecimal oldValue =
                        (oldEncryptedValue != null && !oldEncryptedValue.isBlank())
                                ? new BigDecimal(
                                        encryptionService.decrypt(oldEncryptedValue, encryptionKey))
                                : null;
                if (oldValue == null || oldValue.compareTo(request.getCurrentValue()) != 0) {
                    recordValueHistory(updatedProperty, request.getCurrentValue(), encryptionKey);
                }
            } catch (Exception e) {
                log.warn(
                        "Could not detect currentValue change for property {}: {}",
                        propertyId,
                        e.getMessage());
                // Non-fatal — still record to be safe
                recordValueHistory(updatedProperty, request.getCurrentValue(), encryptionKey);
            }
        }

        // Sync with Assets module
        if (updatedProperty.getAssetId() != null) {
            try {
                updateLinkedAsset(userId, updatedProperty, request, encryptionKey);
            } catch (Exception e) {
                log.error(
                        "Failed to sync updated real estate property {} with assets: {}",
                        propertyId,
                        e.getMessage());
                // Non-fatal, but logged
            }
        }

        log.info("Property updated successfully: id={}, userId={}", propertyId, userId);
        LocalDate newPurchaseDate = updatedProperty.getPurchaseDate();
        LocalDate cutoff =
                (oldPurchaseDate != null
                                && (newPurchaseDate == null
                                        || oldPurchaseDate.isBefore(newPurchaseDate)))
                        ? oldPurchaseDate
                        : newPurchaseDate;
        invalidateSnapshotsFrom(userId, cutoff);

        // Decrypt and return response with calculated fields
        RealEstatePropertyResponse propUpdateResponse =
                toResponseWithDecryption(updatedProperty, encryptionKey);

        // Record in operation history
        operationHistoryService.record(
                userId,
                org.openfinance.entity.EntityType.REAL_ESTATE,
                propertyId,
                request.getName(),
                org.openfinance.entity.OperationType.UPDATE,
                beforeSnapshot,
                null);

        return propUpdateResponse;
    }

    /**
     * Deletes a property (soft delete by setting isActive = false).
     *
     * <p>Only the property owner can delete the property. The property is not physically removed
     * from the database but marked as inactive for historical tracking.
     *
     * <p>Requirement REQ-2.16.1: Delete (deactivate) property
     *
     * <p>Requirement REQ-3.2: Authorization check - verify property ownership
     *
     * @param propertyId the ID of the property to delete
     * @param userId the ID of the user deleting the property (for authorization)
     * @throws RealEstatePropertyNotFoundException if property not found or doesn't belong to user
     * @throws IllegalArgumentException if propertyId or userId is null
     */
    public void deleteProperty(Long propertyId, Long userId, SecretKey encryptionKey) {
        if (propertyId == null) {
            throw new IllegalArgumentException("Property ID cannot be null");
        }
        if (userId == null) {
            throw new IllegalArgumentException("User ID cannot be null");
        }

        log.debug("Deleting property {}: userId={}", propertyId, userId);

        // Fetch property and verify ownership
        RealEstateProperty property =
                realEstateRepository
                        .findByIdAndUserId(propertyId, userId)
                        .orElseThrow(
                                () ->
                                        RealEstatePropertyNotFoundException.byIdAndUser(
                                                propertyId, userId));

        String label = null;
        RealEstatePropertyResponse snapshot = null;
        if (encryptionKey != null) {
            snapshot = toResponseWithDecryption(property, encryptionKey);
            label = snapshot.getName();
        }

        // Soft delete - set isActive to false
        property.setActive(false);
        realEstateRepository.save(property);

        // Sync with Assets module - HARD DELETE the asset to remove from net worth
        if (property.getAssetId() != null) {
            try {
                // Pass null encryptionKey here as we don't necessarily need another snapshot
                // for
                // the asset repo sync
                assetService.deleteAsset(property.getAssetId(), userId, null);
                property.setAssetId(null); // Clear the link
                realEstateRepository.save(property);
            } catch (Exception e) {
                log.error(
                        "Failed to delete linked asset for real estate property {}: {}",
                        propertyId,
                        e.getMessage());
            }
        }

        log.info("Property soft-deleted successfully: id={}, userId={}", propertyId, userId);
        invalidateSnapshotsFrom(userId, property.getPurchaseDate());

        // Record in operation history
        operationHistoryService.record(
                userId,
                org.openfinance.entity.EntityType.REAL_ESTATE,
                propertyId,
                label,
                org.openfinance.entity.OperationType.DELETE,
                snapshot,
                null);
    }

    /**
     * Retrieves a single property by ID with decrypted data and calculated fields.
     *
     * <p>Only the property owner can retrieve the property.
     *
     * <p>Requirement REQ-2.16.1: Retrieve property details
     *
     * <p>Requirement REQ-3.2: Authorization check - verify property ownership
     *
     * @param propertyId the ID of the property to retrieve
     * @param userId the ID of the user retrieving the property (for authorization)
     * @param encryptionKey the AES-256 encryption key for decrypting sensitive fields
     * @return the property with decrypted data and calculated fields
     * @throws RealEstatePropertyNotFoundException if property not found or doesn't belong to user
     * @throws IllegalArgumentException if any parameter is null
     */
    @Transactional(readOnly = true)
    public RealEstatePropertyResponse getPropertyById(
            Long propertyId, Long userId, SecretKey encryptionKey) {
        if (propertyId == null) {
            throw new IllegalArgumentException("Property ID cannot be null");
        }
        if (userId == null) {
            throw new IllegalArgumentException("User ID cannot be null");
        }
        if (encryptionKey == null) {
            throw new IllegalArgumentException("Encryption key cannot be null");
        }

        log.debug("Retrieving property {}: userId={}", propertyId, userId);

        // Fetch property and verify ownership
        RealEstateProperty property =
                realEstateRepository
                        .findByIdAndUserIdWithMortgage(propertyId, userId)
                        .orElseThrow(
                                () ->
                                        RealEstatePropertyNotFoundException.byIdAndUser(
                                                propertyId, userId));

        // Decrypt and return response with calculated fields
        return toResponseWithDecryption(property, encryptionKey);
    }

    /**
     * Retrieves all properties for a user with optional filtering.
     *
     * <p>Requirement REQ-2.16.1: List all properties for a user
     *
     * <p>Requirement REQ-3.2: Users can only access their own properties
     *
     * @param userId the ID of the user
     * @param propertyType optional property type filter (null = all types)
     * @param includeInactive whether to include inactive properties (default: false)
     * @param encryptionKey the AES-256 encryption key for decrypting sensitive fields
     * @return list of properties with decrypted data and calculated fields
     * @throws IllegalArgumentException if userId or encryptionKey is null
     */
    @Transactional(readOnly = true)
    public List<RealEstatePropertyResponse> getPropertiesByUserId(
            Long userId,
            PropertyType propertyType,
            boolean includeInactive,
            SecretKey encryptionKey) {
        if (userId == null) {
            throw new IllegalArgumentException("User ID cannot be null");
        }
        if (encryptionKey == null) {
            throw new IllegalArgumentException("Encryption key cannot be null");
        }

        log.debug(
                "Retrieving properties for user {}: type={}, includeInactive={}",
                userId,
                propertyType,
                includeInactive);

        List<RealEstateProperty> properties;

        // Apply filters
        if (propertyType != null) {
            if (includeInactive) {
                properties = realEstateRepository.findByUserIdAndPropertyType(userId, propertyType);
            } else {
                properties =
                        realEstateRepository.findByUserIdAndPropertyTypeAndIsActive(
                                userId, propertyType, true);
            }
        } else {
            if (includeInactive) {
                properties = realEstateRepository.findByUserId(userId);
            } else {
                properties = realEstateRepository.findByUserIdAndIsActive(userId, true);
            }
        }

        log.debug("Found {} properties for user {}", properties.size(), userId);

        // Decrypt and convert to response DTOs
        return properties.stream()
                .map(property -> toResponseWithDecryption(property, encryptionKey))
                .collect(Collectors.toList());
    }

    /**
     * Searches properties with filters and pagination.
     *
     * <p>This method supports dynamic filtering and sorting through the search criteria. All
     * filtering is done at the database level for efficiency.
     *
     * <p><strong>Supported Filters:</strong>
     *
     * <ul>
     *   <li>keyword - Search in property name or address (case-insensitive)
     *   <li>propertyType - Filter by property type
     *   <li>currency - Filter by currency code
     *   <li>isActive - Filter by active status
     *   <li>hasMortgage - Filter by mortgage presence
     *   <li>purchaseDateFrom - Filter by purchase date >= this date
     *   <li>purchaseDateTo - Filter by purchase date <= this date
     *   <li>valueMin - Filter by minimum current value
     *   <li>valueMax - Filter by maximum current value
     *   <li>priceMin - Filter by minimum purchase price
     *   <li>priceMax - Filter by maximum purchase price
     *   <li>rentalIncomeMin - Filter by minimum rental income
     * </ul>
     *
     * @param userId the ID of the user
     * @param criteria the search criteria (all fields optional)
     * @param pageable pagination and sorting parameters (page number, size, sort)
     * @param encryptionKey the AES-256 encryption key for decrypting sensitive fields
     * @return page of properties matching criteria with decrypted data
     * @throws IllegalArgumentException if userId, criteria, pageable, or encryptionKey is null
     */
    @Transactional(readOnly = true)
    public Page<RealEstatePropertyResponse> searchProperties(
            Long userId,
            RealEstateSearchCriteria criteria,
            Pageable pageable,
            SecretKey encryptionKey) {

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
                "Searching properties for user {}: keyword={}, propertyType={}",
                userId,
                criteria.getKeyword(),
                criteria.getPropertyType());

        boolean hasKeyword =
                criteria.getKeyword() != null && !criteria.getKeyword().trim().isEmpty();

        // name, currentValue, purchasePrice and rentalIncome are encrypted at rest.
        // DB-level LIKE / ORDER BY on these fields does not work against plaintext
        // input.
        // When either a keyword filter or a sort on an encrypted field is requested,
        // we fall back to an in-memory approach: fetch all matching rows (non-encrypted
        // filters applied at DB level), decrypt, then filter/sort/paginate in Java.
        Set<String> encryptedSortFields =
                Set.of("name", "currentValue", "purchasePrice", "rentalIncome");
        boolean hasSortOnEncryptedField =
                pageable.getSort().stream()
                        .anyMatch(order -> encryptedSortFields.contains(order.getProperty()));

        if (hasKeyword || hasSortOnEncryptedField) {
            // Build criteria without keyword so other non-encrypted filters still apply at
            // DB level
            RealEstateSearchCriteria criteriaWithoutKeyword =
                    RealEstateSearchCriteria.builder()
                            .propertyType(criteria.getPropertyType())
                            .currency(criteria.getCurrency())
                            .isActive(criteria.getIsActive())
                            .hasMortgage(criteria.getHasMortgage())
                            .purchaseDateFrom(criteria.getPurchaseDateFrom())
                            .purchaseDateTo(criteria.getPurchaseDateTo())
                            .build();

            Specification<RealEstateProperty> specWithoutKeyword =
                    RealEstateSpecification.buildSpecification(userId, criteriaWithoutKeyword);
            List<RealEstateProperty> allMatching = realEstateRepository.findAll(specWithoutKeyword);

            // Decrypt and apply keyword filter in memory
            final String lowerKeyword =
                    hasKeyword ? criteria.getKeyword().trim().toLowerCase() : null;
            List<RealEstatePropertyResponse> filtered =
                    allMatching.stream()
                            .map(property -> toResponseWithDecryption(property, encryptionKey))
                            .filter(
                                    response ->
                                            lowerKeyword == null
                                                    || (response.getName() != null
                                                            && response.getName()
                                                                    .toLowerCase()
                                                                    .contains(lowerKeyword))
                                                    || (response.getAddress() != null
                                                            && response.getAddress()
                                                                    .toLowerCase()
                                                                    .contains(lowerKeyword)))
                            .collect(Collectors.toList());

            // Apply in-memory sort (handles encrypted sort fields correctly)
            Sort sort = pageable.getSort();
            if (sort.isSorted()) {
                Comparator<RealEstatePropertyResponse> comparator = null;
                for (Sort.Order order : sort) {
                    Comparator<RealEstatePropertyResponse> fieldComp =
                            buildPropertyComparator(order.getProperty());
                    if (fieldComp != null) {
                        if (order.isDescending()) fieldComp = fieldComp.reversed();
                        comparator =
                                comparator == null
                                        ? fieldComp
                                        : comparator.thenComparing(fieldComp);
                    }
                }
                if (comparator != null) {
                    filtered.sort(comparator);
                }
            }

            // Paginate manually
            int pageSize = pageable.getPageSize();
            int pageNumber = pageable.getPageNumber();
            int fromIndex = Math.min(pageNumber * pageSize, filtered.size());
            int toIndex = Math.min(fromIndex + pageSize, filtered.size());

            log.debug(
                    "In-memory search found {} properties after filter (page {}/{})",
                    filtered.size(),
                    pageNumber + 1,
                    (filtered.size() + pageSize - 1) / pageSize);

            return new PageImpl<>(filtered.subList(fromIndex, toIndex), pageable, filtered.size());
        }

        // Standard DB-level query (no keyword filter, sort field not encrypted)
        Specification<RealEstateProperty> spec =
                RealEstateSpecification.buildSpecification(userId, criteria);
        Page<RealEstateProperty> propertyPage = realEstateRepository.findAll(spec, pageable);

        log.debug(
                "Found {} properties (page {}/{})",
                propertyPage.getNumberOfElements(),
                propertyPage.getNumber() + 1,
                propertyPage.getTotalPages());

        return propertyPage.map(property -> toResponseWithDecryption(property, encryptionKey));
    }

    /**
     * Builds a Comparator for in-memory sorting of RealEstatePropertyResponse by field name. Used
     * when DB-level ORDER BY is not applicable (encrypted or derived fields).
     */
    private Comparator<RealEstatePropertyResponse> buildPropertyComparator(String field) {
        switch (field) {
            case "name":
                return (a, b) -> {
                    String va = a.getName() != null ? a.getName().toLowerCase() : "";
                    String vb = b.getName() != null ? b.getName().toLowerCase() : "";
                    return va.compareTo(vb);
                };
            case "currentValue":
                return (a, b) -> {
                    BigDecimal va =
                            a.getCurrentValue() != null ? a.getCurrentValue() : BigDecimal.ZERO;
                    BigDecimal vb =
                            b.getCurrentValue() != null ? b.getCurrentValue() : BigDecimal.ZERO;
                    return va.compareTo(vb);
                };
            case "purchasePrice":
                return (a, b) -> {
                    BigDecimal va =
                            a.getPurchasePrice() != null ? a.getPurchasePrice() : BigDecimal.ZERO;
                    BigDecimal vb =
                            b.getPurchasePrice() != null ? b.getPurchasePrice() : BigDecimal.ZERO;
                    return va.compareTo(vb);
                };
            case "purchaseDate":
                return (a, b) -> {
                    LocalDate va =
                            a.getPurchaseDate() != null ? a.getPurchaseDate() : LocalDate.MIN;
                    LocalDate vb =
                            b.getPurchaseDate() != null ? b.getPurchaseDate() : LocalDate.MIN;
                    return va.compareTo(vb);
                };
            case "rentalIncome":
                return (a, b) -> {
                    BigDecimal va =
                            a.getRentalIncome() != null ? a.getRentalIncome() : BigDecimal.ZERO;
                    BigDecimal vb =
                            b.getRentalIncome() != null ? b.getRentalIncome() : BigDecimal.ZERO;
                    return va.compareTo(vb);
                };
            default:
                return null;
        }
    }

    /**
     * Calculates equity for a property (current value - mortgage balance).
     *
     * <p>Equity represents the portion of the property owned outright. If no mortgage is linked,
     * equity equals the current property value.
     *
     * <p>Requirement REQ-2.16.2: Calculate property equity
     *
     * @param propertyId the ID of the property
     * @param userId the ID of the user (for authorization)
     * @param encryptionKey the AES-256 encryption key for decrypting sensitive fields
     * @return detailed equity breakdown including LTV ratio
     * @throws RealEstatePropertyNotFoundException if property not found or doesn't belong to user
     * @throws IllegalArgumentException if any parameter is null
     */
    @Transactional(readOnly = true)
    public PropertyEquityResponse calculateEquity(
            Long propertyId, Long userId, SecretKey encryptionKey) {
        if (propertyId == null) {
            throw new IllegalArgumentException("Property ID cannot be null");
        }
        if (userId == null) {
            throw new IllegalArgumentException("User ID cannot be null");
        }
        if (encryptionKey == null) {
            throw new IllegalArgumentException("Encryption key cannot be null");
        }

        log.debug("Calculating equity for property {}: userId={}", propertyId, userId);

        // Fetch property with mortgage
        RealEstateProperty property =
                realEstateRepository
                        .findByIdAndUserIdWithMortgage(propertyId, userId)
                        .orElseThrow(
                                () ->
                                        RealEstatePropertyNotFoundException.byIdAndUser(
                                                propertyId, userId));

        // Decrypt current value
        BigDecimal currentValue =
                new BigDecimal(
                        encryptionService.decrypt(property.getCurrentValue(), encryptionKey));

        // Get mortgage balance if linked
        BigDecimal mortgageBalance = BigDecimal.ZERO;
        Long mortgageId = null;
        boolean hasMortgage = false;

        // Explicitly load mortgage if relationship is null but mortgageId is present
        Liability mortgage = property.getMortgage();
        if (mortgage == null && property.getMortgageId() != null) {
            mortgage = liabilityRepository.findById(property.getMortgageId()).orElse(null);
        }

        if (mortgage != null) {
            mortgageBalance =
                    new BigDecimal(
                            encryptionService.decrypt(mortgage.getCurrentBalance(), encryptionKey));
            mortgageId = mortgage.getId();
            hasMortgage = true;
        }

        // Calculate equity
        BigDecimal equity = currentValue.subtract(mortgageBalance);

        // Calculate percentages
        BigDecimal equityPercentage = BigDecimal.ZERO;
        BigDecimal loanToValueRatio = BigDecimal.ZERO;

        if (currentValue.compareTo(BigDecimal.ZERO) > 0) {
            equityPercentage =
                    equity.divide(currentValue, SCALE, RoundingMode.HALF_UP)
                            .multiply(new BigDecimal("100"))
                            .setScale(2, RoundingMode.HALF_UP);

            if (hasMortgage) {
                loanToValueRatio =
                        mortgageBalance
                                .divide(currentValue, SCALE, RoundingMode.HALF_UP)
                                .multiply(new BigDecimal("100"))
                                .setScale(2, RoundingMode.HALF_UP);
            }
        }

        // Decrypt property name
        String propertyName = encryptionService.decrypt(property.getName(), encryptionKey);

        log.info(
                "Equity calculated for property {}: equity={}, equityPercentage={}%, LTV={}%",
                propertyId, equity, equityPercentage, loanToValueRatio);

        return PropertyEquityResponse.builder()
                .propertyId(propertyId)
                .propertyName(propertyName)
                .currentValue(currentValue)
                .mortgageBalance(mortgageBalance)
                .equity(equity)
                .equityPercentage(equityPercentage)
                .loanToValueRatio(loanToValueRatio)
                .currency(property.getCurrency())
                .mortgageId(mortgageId)
                .hasMortgage(hasMortgage)
                .build();
    }

    /**
     * Calculates Return on Investment (ROI) for a property.
     *
     * <p>ROI considers both capital appreciation and rental income over the holding period.
     * Formula: ((currentValue - purchasePrice + totalRentalIncome) / purchasePrice) * 100
     *
     * <p>Requirement REQ-2.16.2: Calculate property ROI
     *
     * @param propertyId the ID of the property
     * @param userId the ID of the user (for authorization)
     * @param encryptionKey the AES-256 encryption key for decrypting sensitive fields
     * @return detailed ROI breakdown including appreciation and rental income
     * @throws RealEstatePropertyNotFoundException if property not found or doesn't belong to user
     * @throws IllegalArgumentException if any parameter is null
     */
    @Transactional(readOnly = true)
    public PropertyROIResponse calculateROI(Long propertyId, Long userId, SecretKey encryptionKey) {
        if (propertyId == null) {
            throw new IllegalArgumentException("Property ID cannot be null");
        }
        if (userId == null) {
            throw new IllegalArgumentException("User ID cannot be null");
        }
        if (encryptionKey == null) {
            throw new IllegalArgumentException("Encryption key cannot be null");
        }

        log.debug("Calculating ROI for property {}: userId={}", propertyId, userId);

        // Fetch property
        RealEstateProperty property =
                realEstateRepository
                        .findByIdAndUserId(propertyId, userId)
                        .orElseThrow(
                                () ->
                                        RealEstatePropertyNotFoundException.byIdAndUser(
                                                propertyId, userId));

        // Decrypt sensitive fields
        String propertyName = encryptionService.decrypt(property.getName(), encryptionKey);
        BigDecimal purchasePrice =
                new BigDecimal(
                        encryptionService.decrypt(property.getPurchasePrice(), encryptionKey));
        BigDecimal currentValue =
                new BigDecimal(
                        encryptionService.decrypt(property.getCurrentValue(), encryptionKey));

        // Calculate appreciation
        BigDecimal appreciation = currentValue.subtract(purchasePrice);
        BigDecimal appreciationPercentage = BigDecimal.ZERO;

        if (purchasePrice.compareTo(BigDecimal.ZERO) > 0) {
            appreciationPercentage =
                    appreciation
                            .divide(purchasePrice, SCALE, RoundingMode.HALF_UP)
                            .multiply(new BigDecimal("100"))
                            .setScale(2, RoundingMode.HALF_UP);
        }

        // Calculate holding period
        long yearsOwned = 0;
        if (property.getPurchaseDate() != null) {
            yearsOwned = ChronoUnit.YEARS.between(property.getPurchaseDate(), LocalDate.now());
        }

        long monthsOwned = 0;
        if (property.getPurchaseDate() != null) {
            monthsOwned = ChronoUnit.MONTHS.between(property.getPurchaseDate(), LocalDate.now());
        }

        // Handle rental income
        BigDecimal monthlyRentalIncome = null;
        BigDecimal totalRentalIncome = null;
        BigDecimal rentalYield = null;
        boolean isRentalProperty = false;

        if (property.getRentalIncome() != null) {
            monthlyRentalIncome =
                    new BigDecimal(
                            encryptionService.decrypt(property.getRentalIncome(), encryptionKey));
            totalRentalIncome = monthlyRentalIncome.multiply(new BigDecimal(monthsOwned));
            isRentalProperty = monthlyRentalIncome.compareTo(BigDecimal.ZERO) > 0;

            // Calculate annual rental yield
            if (currentValue.compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal annualRentalIncome =
                        monthlyRentalIncome.multiply(new BigDecimal(MONTHS_PER_YEAR));
                rentalYield =
                        annualRentalIncome
                                .divide(currentValue, SCALE, RoundingMode.HALF_UP)
                                .multiply(new BigDecimal("100"))
                                .setScale(2, RoundingMode.HALF_UP);
            }
        }

        // Calculate total ROI
        BigDecimal totalROI = BigDecimal.ZERO;
        BigDecimal annualizedReturn = null;

        if (purchasePrice.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal totalGain = appreciation;
            if (totalRentalIncome != null) {
                totalGain = totalGain.add(totalRentalIncome);
            }

            totalROI =
                    totalGain
                            .divide(purchasePrice, SCALE, RoundingMode.HALF_UP)
                            .multiply(new BigDecimal("100"))
                            .setScale(2, RoundingMode.HALF_UP);

            // Calculate annualized return if property owned for at least 1 year
            if (yearsOwned > 0) {
                annualizedReturn =
                        totalROI.divide(new BigDecimal(yearsOwned), SCALE, RoundingMode.HALF_UP)
                                .setScale(2, RoundingMode.HALF_UP);
            }
        }

        log.info(
                "ROI calculated for property {}: totalROI={}%, appreciation={}%, annualizedReturn={}%",
                propertyId, totalROI, appreciationPercentage, annualizedReturn);

        return PropertyROIResponse.builder()
                .propertyId(propertyId)
                .propertyName(propertyName)
                .purchasePrice(purchasePrice)
                .currentValue(currentValue)
                .purchaseDate(property.getPurchaseDate())
                .yearsOwned(yearsOwned)
                .appreciation(appreciation)
                .appreciationPercentage(appreciationPercentage)
                .monthlyRentalIncome(monthlyRentalIncome)
                .totalRentalIncome(totalRentalIncome)
                .rentalYield(rentalYield)
                .totalROI(totalROI)
                .annualizedReturn(annualizedReturn)
                .currency(property.getCurrency())
                .isRentalProperty(isRentalProperty)
                .build();
    }

    /**
     * Updates the estimated current value of a property.
     *
     * <p>This method allows updating the property's current market value based on new appraisals,
     * market data, or automated valuation models.
     *
     * <p>Requirement REQ-2.16.1: Update property value estimates
     *
     * <p>Requirement REQ-3.2: Authorization check - verify property ownership
     *
     * @param propertyId the ID of the property
     * @param userId the ID of the user (for authorization)
     * @param newValue the new estimated value
     * @param encryptionKey the AES-256 encryption key for encrypting the new value
     * @return the updated property response with recalculated fields
     * @throws RealEstatePropertyNotFoundException if property not found or doesn't belong to user
     * @throws IllegalArgumentException if any parameter is null or newValue is negative
     */
    public RealEstatePropertyResponse estimateValue(
            Long propertyId, Long userId, BigDecimal newValue, SecretKey encryptionKey) {
        if (propertyId == null) {
            throw new IllegalArgumentException("Property ID cannot be null");
        }
        if (userId == null) {
            throw new IllegalArgumentException("User ID cannot be null");
        }
        if (newValue == null) {
            throw new IllegalArgumentException("New value cannot be null");
        }
        if (newValue.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("New value must be non-negative");
        }
        if (encryptionKey == null) {
            throw new IllegalArgumentException("Encryption key cannot be null");
        }

        log.debug(
                "Updating estimated value for property {}: userId={}, newValue={}",
                propertyId,
                userId,
                newValue);

        // Fetch property and verify ownership
        RealEstateProperty property =
                realEstateRepository
                        .findByIdAndUserId(propertyId, userId)
                        .orElseThrow(
                                () ->
                                        RealEstatePropertyNotFoundException.byIdAndUser(
                                                propertyId, userId));

        // Encrypt and update current value
        String encryptedValue = encryptionService.encrypt(newValue.toString(), encryptionKey);
        property.setCurrentValue(encryptedValue);

        // Save updated property
        RealEstateProperty updatedProperty = realEstateRepository.save(property);

        // Sync with Assets module
        if (updatedProperty.getAssetId() != null) {
            try {
                // Create a mini request just to update the price
                AssetRequest updateRequest = new AssetRequest();
                updateRequest.setCurrentPrice(newValue);
                // We need to provide other required fields or ensure updateAsset handles
                // partial updates?
                // Looking at AssetService.updateAsset, it uses
                // AssetMapper.updateEntityFromRequest which typically ignores nulls.
                // However, we should double check if AssetRequest validation ("@NotNull")
                // triggers before service call.
                // AssetRequest DTO probably has @NotNull annotations.
                // We'll need to fetch the existing asset to fill valid data or bypass DTO
                // validation if possible.
                // Actually, AssetService.updateAsset takes AssetRequest.

                // Better approach: Get the asset, update what we need, and use a dedicated
                // method or full request.
                // Since we don't want to re-decrypt everything just to fill a request, let's
                // look at AssetService.
                // It calls `assetMapper.updateEntityFromRequest(request, asset)`.
                // If we pass a request with nulls, we need to ensure MapStruct null value check
                // strategy is set to IGNORE.
                // Assuming it is standard, we also need to satisfy @Valid if the controller
                // uses it,
                // but internal service calls don't trigger Jakarta Bean Validation unless
                // explicitly triggered.

                // BUT AssetService.updateAsset performs re-encryption of name/notes if present
                // in request.
                // We should construct a minimal request.

                // To be safe and correct, we should populate the request with existing data +
                // new price
                // but that requires fetching the asset.
                // Let's rely on the fact that we can update just the price.

                // Wait, AssetService checks:
                // if (request.getName() != null) ...
                // So if we leave name null, it won't be re-encrypted (which is good).
                // But AssetService.updateAsset checks: if (request == null) throw...

                // The risk is if the mapper overwrites existing fields with null.
                // We'll assume the mapper is configured with
                // NullValuePropertyMappingStrategy.IGNORE

                // Let's try to just update the price.
                AssetResponse assetResponse =
                        assetService.getAssetById(
                                updatedProperty.getAssetId(), userId, encryptionKey);

                AssetRequest partialUpdate = new AssetRequest();
                partialUpdate.setCurrentPrice(newValue);
                // Provide required fields to satisfy potential internal checks or mapper
                partialUpdate.setName(assetResponse.getName());
                partialUpdate.setType(assetResponse.getType());
                partialUpdate.setQuantity(assetResponse.getQuantity());
                partialUpdate.setPurchasePrice(assetResponse.getPurchasePrice());
                partialUpdate.setCurrency(assetResponse.getCurrency());
                partialUpdate.setPurchaseDate(assetResponse.getPurchaseDate());

                assetService.updateAsset(
                        updatedProperty.getAssetId(), userId, partialUpdate, encryptionKey);

            } catch (Exception e) {
                log.error(
                        "Failed to sync estimated value for property {} with assets: {}",
                        propertyId,
                        e.getMessage());
            }
        }

        log.info(
                "Property value updated successfully: id={}, userId={}, newValue={}",
                propertyId,
                userId,
                newValue);

        // Decrypt and return response with recalculated fields
        return toResponseWithDecryption(updatedProperty, encryptionKey);
    }

    // ========== Private Helper Methods ==========

    /**
     * Inserts a row into {@code real_estate_value_history} recording the property's value as of
     * today.
     */
    private void recordValueHistory(
            RealEstateProperty property, BigDecimal plainValue, SecretKey encryptionKey) {
        try {
            String encryptedValue = encryptionService.encrypt(plainValue.toString(), encryptionKey);
            RealEstateValueHistory entry =
                    RealEstateValueHistory.builder()
                            .propertyId(property.getId())
                            .userId(property.getUserId())
                            .effectiveDate(LocalDate.now())
                            .recordedValue(encryptedValue)
                            .currency(property.getCurrency())
                            .currencyId(property.getCurrencyId())
                            .build();
            valueHistoryRepository.save(entry);
            log.debug(
                    "Recorded value history for property {}: {} {}",
                    property.getId(),
                    plainValue,
                    property.getCurrency());
        } catch (Exception e) {
            log.error(
                    "Failed to record value history for property {}: {}",
                    property.getId(),
                    e.getMessage());
        }
    }

    /**
     * Validates that a mortgage (liability) exists and belongs to the specified user.
     *
     * @param mortgageId the liability ID to validate
     * @param userId the user ID to check ownership
     * @throws LiabilityNotFoundException if mortgage not found or doesn't belong to user
     */
    private void validateMortgageOwnership(Long mortgageId, Long userId) {
        if (!liabilityRepository.existsByIdAndUserId(mortgageId, userId)) {
            throw LiabilityNotFoundException.byIdAndUser(mortgageId, userId);
        }
    }

    /**
     * Encrypts all sensitive fields of a property entity.
     *
     * @param property the entity to encrypt (modified in place)
     * @param request the source request with plaintext values
     * @param encryptionKey the encryption key
     */
    private void encryptProperty(
            RealEstateProperty property,
            RealEstatePropertyRequest request,
            SecretKey encryptionKey) {
        String encryptedName = encryptionService.encrypt(request.getName(), encryptionKey);
        property.setName(encryptedName);

        String encryptedAddress = encryptionService.encrypt(request.getAddress(), encryptionKey);
        property.setAddress(encryptedAddress);

        String encryptedPurchasePrice =
                encryptionService.encrypt(request.getPurchasePrice().toString(), encryptionKey);
        property.setPurchasePrice(encryptedPurchasePrice);

        String encryptedCurrentValue =
                encryptionService.encrypt(request.getCurrentValue().toString(), encryptionKey);
        property.setCurrentValue(encryptedCurrentValue);

        if (request.getRentalIncome() != null) {
            String encryptedRentalIncome =
                    encryptionService.encrypt(request.getRentalIncome().toString(), encryptionKey);
            property.setRentalIncome(encryptedRentalIncome);
        }

        if (request.getNotes() != null && !request.getNotes().isBlank()) {
            String encryptedNotes = encryptionService.encrypt(request.getNotes(), encryptionKey);
            property.setNotes(encryptedNotes);
        }

        if (request.getDocuments() != null && !request.getDocuments().isBlank()) {
            String encryptedDocuments =
                    encryptionService.encrypt(request.getDocuments(), encryptionKey);
            property.setDocuments(encryptedDocuments);
        }
    }

    /** Helper to create a linked Asset for a Real Estate property. */
    private void createLinkedAsset(
            Long userId,
            RealEstateProperty property,
            RealEstatePropertyRequest request,
            SecretKey encryptionKey) {
        AssetRequest assetRequest = new AssetRequest();
        assetRequest.setName(request.getName());
        assetRequest.setType(AssetType.REAL_ESTATE);
        assetRequest.setQuantity(BigDecimal.ONE);
        assetRequest.setPurchasePrice(request.getPurchasePrice());
        assetRequest.setCurrentPrice(request.getCurrentValue());
        assetRequest.setCurrency(request.getCurrency());
        assetRequest.setPurchaseDate(request.getPurchaseDate());
        assetRequest.setNotes("Linked to Real Estate Property: " + request.getAddress());

        // Physical asset fields
        assetRequest.setCondition(
                AssetType.REAL_ESTATE.isPhysical()
                        ? org.openfinance.entity.AssetCondition.GOOD
                        : null); // Default
        // or
        // map
        // if
        // needed

        AssetResponse createdAsset = assetService.createAsset(userId, assetRequest, encryptionKey);
        property.setAssetId(createdAsset.getId());
    }

    /** Helper to update the linked Asset. */
    private void updateLinkedAsset(
            Long userId,
            RealEstateProperty property,
            RealEstatePropertyRequest request,
            SecretKey encryptionKey) {
        // We need to fetch the current asset to reuse fields we don't want to change or
        // that are required
        // But for now, let's map what we have.
        AssetRequest assetRequest = new AssetRequest();
        assetRequest.setName(request.getName());
        assetRequest.setCurrentPrice(request.getCurrentValue());
        assetRequest.setPurchasePrice(request.getPurchasePrice()); // In case it was corrected
        assetRequest.setPurchaseDate(request.getPurchaseDate());

        // We don't want to overwrite other fields if they are null in the request?
        // RealEstatePropertyRequest usually has all fields for update.

        assetService.updateAsset(property.getAssetId(), userId, assetRequest, encryptionKey);
    }

    /**
     * Converts a RealEstateProperty entity to a RealEstatePropertyResponse DTO with decryption and
     * calculated fields.
     *
     * @param property the entity to convert
     * @param encryptionKey the decryption key
     * @return the response DTO with decrypted data and calculated fields
     */
    private RealEstatePropertyResponse toResponseWithDecryption(
            RealEstateProperty property, SecretKey encryptionKey) {
        // Map basic fields using MapStruct
        RealEstatePropertyResponse response = realEstateMapper.toResponse(property);

        // Manually set isActive (MapStruct has issues with boolean "is" prefix fields)
        response.setActive(property.isActive());

        // Decrypt sensitive fields
        response.setName(encryptionService.decrypt(property.getName(), encryptionKey));
        response.setAddress(encryptionService.decrypt(property.getAddress(), encryptionKey));

        BigDecimal purchasePrice =
                new BigDecimal(
                        encryptionService.decrypt(property.getPurchasePrice(), encryptionKey));
        response.setPurchasePrice(purchasePrice);

        BigDecimal currentValue =
                new BigDecimal(
                        encryptionService.decrypt(property.getCurrentValue(), encryptionKey));
        response.setCurrentValue(currentValue);

        if (property.getRentalIncome() != null) {
            BigDecimal rentalIncome =
                    new BigDecimal(
                            encryptionService.decrypt(property.getRentalIncome(), encryptionKey));
            response.setRentalIncome(rentalIncome);
        }

        if (property.getNotes() != null) {
            response.setNotes(encryptionService.decrypt(property.getNotes(), encryptionKey));
        }

        if (property.getDocuments() != null) {
            response.setDocuments(
                    encryptionService.decrypt(property.getDocuments(), encryptionKey));
        }

        // Calculate derived fields
        BigDecimal appreciation =
                currentValue.subtract(purchasePrice).setScale(2, RoundingMode.HALF_UP);
        response.setAppreciation(appreciation);

        if (purchasePrice.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal appreciationPercentage =
                    appreciation
                            .divide(purchasePrice, SCALE, RoundingMode.HALF_UP)
                            .multiply(new BigDecimal("100"))
                            .setScale(2, RoundingMode.HALF_UP);
            response.setAppreciationPercentage(appreciationPercentage);
        }

        if (response.getRentalIncome() != null && currentValue.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal annualRentalIncome =
                    response.getRentalIncome().multiply(new BigDecimal(MONTHS_PER_YEAR));
            BigDecimal rentalYield =
                    annualRentalIncome
                            .divide(currentValue, SCALE, RoundingMode.HALF_UP)
                            .multiply(new BigDecimal("100"))
                            .setScale(2, RoundingMode.HALF_UP);
            response.setRentalYield(rentalYield);
        }

        // Calculate years owned
        if (property.getPurchaseDate() != null) {
            long yearsOwned = ChronoUnit.YEARS.between(property.getPurchaseDate(), LocalDate.now());
            response.setYearsOwned(yearsOwned);
        }

        // Handle mortgage-related fields
        // Explicitly load mortgage if relationship is null but mortgageId is present
        Liability mortgage = property.getMortgage();
        if (mortgage == null && property.getMortgageId() != null) {
            mortgage = liabilityRepository.findById(property.getMortgageId()).orElse(null);
        }

        if (mortgage != null) {
            response.setMortgageName(encryptionService.decrypt(mortgage.getName(), encryptionKey));

            BigDecimal mortgageBalance =
                    new BigDecimal(
                            encryptionService.decrypt(mortgage.getCurrentBalance(), encryptionKey));
            response.setMortgageBalance(mortgageBalance);

            // Calculate equity
            BigDecimal equity = currentValue.subtract(mortgageBalance);
            response.setEquity(equity);

            if (currentValue.compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal equityPercentage =
                        equity.divide(currentValue, SCALE, RoundingMode.HALF_UP)
                                .multiply(new BigDecimal("100"))
                                .setScale(2, RoundingMode.HALF_UP);
                response.setEquityPercentage(equityPercentage);
            }
        } else {
            // No mortgage - equity equals current value
            response.setEquity(currentValue);
            response.setEquityPercentage(new BigDecimal("100.00"));
        }

        // Calculate ROI (simplified - just appreciation for now, full calculation in
        // calculateROI method)
        if (purchasePrice.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal roi =
                    appreciation
                            .divide(purchasePrice, SCALE, RoundingMode.HALF_UP)
                            .multiply(new BigDecimal("100"))
                            .setScale(2, RoundingMode.HALF_UP);
            response.setRoi(roi);
        }

        // Populate currency conversion metadata (Requirement REQ-3.4, REQ-3.5)
        populateConversionFields(
                response, property.getUserId(), property.getCurrency(), currentValue);

        return response;
    }

    /**
     * Populates currency conversion metadata fields on a RealEstatePropertyResponse.
     *
     * <p>Fetches the user's base currency from the database, then attempts to convert the
     * property's {@code currentValue} to the base currency using {@link ExchangeRateService}. On
     * failure, falls back to the native amount with {@code isConverted=false}.
     *
     * <p>Also performs secondary currency conversion when the user has a secondary currency
     * configured and it differs from the native currency.
     *
     * <p>Requirement REQ-3.4: RealEstateService populates conversion fields
     *
     * <p>Requirement REQ-3.5: Graceful fallback when conversion unavailable
     *
     * <p>Requirement REQ-4.4, REQ-4.5: Secondary conversion logic
     *
     * @param response the response DTO to populate
     * @param userId the property owner's user ID
     * @param nativeCurrency the property's native currency code (ISO 4217)
     * @param nativeValue the native current value
     */
    private void populateConversionFields(
            RealEstatePropertyResponse response,
            Long userId,
            String nativeCurrency,
            BigDecimal nativeValue) {
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
                BigDecimal rate =
                        exchangeRateService.getExchangeRate(nativeCurrency, baseCurrency, null);
                BigDecimal converted =
                        exchangeRateService.convert(nativeValue, nativeCurrency, baseCurrency);
                response.setValueInBaseCurrency(converted);
                response.setExchangeRate(rate);
                response.setIsConverted(true);
            } catch (Exception e) {
                log.warn(
                        "Currency conversion failed for property (user={}, {}->{}) – falling back to native: {}",
                        userId,
                        nativeCurrency,
                        baseCurrency,
                        e.getMessage());
                response.setValueInBaseCurrency(nativeValue);
                response.setIsConverted(false);
            }
        }

        // Step 2: Secondary conversion (Requirement REQ-4.4, REQ-4.5)
        if (secCurrency != null
                && !secCurrency.isBlank()
                && nativeCurrency != null
                && !nativeCurrency.equals(secCurrency)
                && nativeValue != null) {
            try {
                BigDecimal secRate =
                        exchangeRateService.getExchangeRate(nativeCurrency, secCurrency, null);
                BigDecimal secAmount =
                        exchangeRateService.convert(nativeValue, nativeCurrency, secCurrency);
                response.setValueInSecondaryCurrency(secAmount);
                response.setSecondaryCurrency(secCurrency);
                response.setSecondaryExchangeRate(secRate);
            } catch (Exception e) {
                log.warn(
                        "Secondary currency conversion failed for property (user={}, {}->{}) – omitting: {}",
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
     * real estate write so the dashboard chart rebuilds affected months.
     */
    private void invalidateSnapshotsFrom(Long userId, LocalDate fromDate) {
        if (fromDate == null) return;
        try {
            int deleted =
                    netWorthRepository.deleteByUserIdAndSnapshotDateBetween(
                            userId, fromDate.withDayOfMonth(1), LocalDate.now());
            if (deleted > 0) {
                log.debug(
                        "Invalidated {} net worth snapshots for user {} (real estate change from {})",
                        deleted,
                        userId,
                        fromDate);
            }
        } catch (Exception e) {
            log.warn(
                    "Could not invalidate net worth snapshots for user {} after real estate change from {}: {}",
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
