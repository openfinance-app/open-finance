package org.openfinance.service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.openfinance.dto.CategoryRequest;
import org.openfinance.dto.CategoryResponse;
import org.openfinance.dto.CategoryTreeNode;
import org.openfinance.entity.Category;
import org.openfinance.entity.CategoryType;
import org.openfinance.exception.CategoryNotFoundException;
import org.openfinance.exception.InvalidCategoryException;
import org.openfinance.mapper.CategoryMapper;
import org.openfinance.repository.CategoryRepository;
import org.openfinance.repository.TransactionRepository;
import org.openfinance.security.EncryptionContext;
import org.openfinance.security.EncryptionService;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service layer for managing transaction categories.
 *
 * <p>
 * This service handles business logic for category CRUD operations, including:
 *
 * <ul>
 * <li>Creating new categories with encrypted names (user categories only)
 * <li>Updating existing categories
 * <li>Deleting categories (only if not used and not system category)
 * <li>Retrieving categories with decrypted data
 * <li>Validating parent-child relationships
 * </ul>
 *
 * <p>
 * <strong>Security Note:</strong> The {@code name} field is encrypted for
 * user-created
 * categories but NOT for system categories (created by CategorySeeder). The
 * encryption key must be
 * provided by the caller for user categories.
 *
 * <p>
 * Requirement REQ-2.4: Category Management - CRUD operations for transaction
 * categories
 *
 * <p>
 * Requirement REQ-2.18: Data encryption at rest for sensitive fields
 *
 * <p>
 * Requirement REQ-3.2: Authorization - Users can only access their own
 * categories
 *
 * @see org.openfinance.entity.Category
 * @see org.openfinance.dto.CategoryRequest
 * @see org.openfinance.dto.CategoryResponse
 */
@Service
@Transactional
@RequiredArgsConstructor
@Slf4j
public class CategoryService {

    private final CategoryRepository categoryRepository;
    private final TransactionRepository transactionRepository;
    private final CategoryMapper categoryMapper;
    private final EncryptionService encryptionService;
    private final MessageSource messageSource;
    private final OperationHistoryService operationHistoryService;

    /**
     * Creates a new category for the specified user.
     *
     * <p>
     * The category name is encrypted before storing in the database (user
     * categories only). The
     * encryption key must be derived from the user's master password.
     *
     * <p>
     * Validates parent-child relationships:
     *
     * <ul>
     * <li>Parent must exist and belong to the same user
     * <li>Parent and child must have the same CategoryType
     * </ul>
     *
     * <p>
     * Requirement REQ-2.4.1: Create new category with encrypted sensitive data
     *
     * @param userId        the ID of the user creating the category
     * @param request       the category creation request containing category
     *                      details
     * @param encryptionKey the AES-256 encryption key for sensitive fields
     * @return the created category with decrypted data
     * @throws IllegalArgumentException  if userId, request, or encryptionKey is
     *                                   null
     * @throws CategoryNotFoundException if parent category does not exist
     * @throws InvalidCategoryException  if parent-child validation fails
     */
    public CategoryResponse createCategory(
            Long userId, CategoryRequest request) {
        if (userId == null) {
            throw new IllegalArgumentException("User ID cannot be null");
        }
        if (request == null) {
            throw new IllegalArgumentException("Category request cannot be null");
        }
        log.debug(
                "Creating category for user {}: name={}, type={}",
                userId,
                request.getName(),
                request.getType());

        // Validate parent-child relationship if parent is specified
        if (request.getParentId() != null) {
            validateParentCategory(userId, request.getParentId(), request.getType());
        }

        // Bug #5 fix: prevent duplicate names within the user's categories
        checkDuplicateName(userId, request.getName(), null);

        // Map request to entity
        Category category = categoryMapper.toEntity(request);
        category.setUserId(userId);

        // Save to database (name is automatically encrypted by JPA converter)
        Category savedCategory = categoryRepository.save(category);
        log.info(
                "Category created successfully: id={}, userId={}, type={}",
                savedCategory.getId(),
                userId,
                savedCategory.getType());

        // Decrypt and return response
        return toResponseWithDecryption(savedCategory);
    }

    /**
     * Updates an existing category.
     *
     * <p>
     * Only non-system categories can be updated. System categories (isSystem=true)
     * are
     * read-only.
     *
     * <p>
     * Requirement REQ-2.4.2: Update category information
     *
     * @param userId        the ID of the user updating the category
     * @param categoryId    the ID of the category to update
     * @param request       the category update request
     * @param encryptionKey the AES-256 encryption key for sensitive fields
     * @return the updated category with decrypted data
     * @throws CategoryNotFoundException if category does not exist or does not
     *                                   belong to the user
     * @throws InvalidCategoryException  if attempting to update a system category
     *                                   or validation
     *                                   fails
     */
    public CategoryResponse updateCategory(
            Long userId, Long categoryId, CategoryRequest request) {
        if (userId == null) {
            throw new IllegalArgumentException("User ID cannot be null");
        }
        if (categoryId == null) {
            throw new IllegalArgumentException("Category ID cannot be null");
        }
        if (request == null) {
            throw new IllegalArgumentException("Category request cannot be null");
        }
        log.debug("Updating category: id={}, userId={}", categoryId, userId);

        // Fetch existing category
        Category existingCategory = categoryRepository
                .findByIdAndUserId(categoryId, userId)
                .orElseThrow(() -> new CategoryNotFoundException(categoryId));

        // Prevent updating system categories
        if (existingCategory.getIsSystem()) {
            throw new InvalidCategoryException("System categories cannot be updated");
        }

        // Validate parent-child relationship if parent is being changed
        if (request.getParentId() != null
                && !request.getParentId().equals(existingCategory.getParentId())) {
            validateParentCategory(userId, request.getParentId(), request.getType());

            // Prevent circular reference
            if (request.getParentId().equals(categoryId)) {
                throw new InvalidCategoryException("Category cannot be its own parent");
            }
        }

        // Bug #5 fix: prevent duplicate names within the user's categories (excluding
        // self)
        if (request.getName() != null && !request.getName().isBlank()) {
            checkDuplicateName(userId, request.getName(), categoryId);
        }

        // Update fields
        categoryMapper.updateEntityFromRequest(request, existingCategory);

        // Save and return
        Category updatedCategory = categoryRepository.save(existingCategory);
        log.info("Category updated successfully: id={}", updatedCategory.getId());

        return toResponseWithDecryption(updatedCategory);
    }

    /**
     * Deletes a category.
     *
     * <p>
     * Requirement REQ-2.4.1: Delete category
     *
     * <p>
     * Requirement REQ-2.4.2: Prevent deleting system categories, categories with
     * subcategories,
     * or categories used by transactions
     *
     * @param userId     the ID of the user requesting the deletion
     * @param categoryId the ID of the category to delete
     * @throws CategoryNotFoundException if category does not exist or does not
     *                                   belong to the user
     * @throws InvalidCategoryException  if category cannot be deleted (system
     *                                   category, has
     *                                   subcategories, or used by transactions)
     */
    @Transactional
    public void deleteCategory(Long userId, Long categoryId) {
        if (userId == null) {
            throw new IllegalArgumentException("User ID cannot be null");
        }
        if (categoryId == null) {
            throw new IllegalArgumentException("Category ID cannot be null");
        }
        log.debug(
                "Deleting category: id={}, userId={}, keyPresent={}",
                categoryId,
                userId,
                EncryptionContext.getKey() != null);

        // Fetch existing category
        Category category = categoryRepository
                .findByIdAndUserId(categoryId, userId)
                .orElseThrow(() -> new CategoryNotFoundException(categoryId));

        // Prevent deleting system categories
        if (category.getIsSystem()) {
            throw new InvalidCategoryException("System categories cannot be deleted");
        }

        // Prevent deleting categories with subcategories
        if (categoryRepository.hasSubcategories(categoryId)) {
            throw new InvalidCategoryException(
                    "Cannot delete category with subcategories. Delete subcategories first.");
        }

        // Requirement REQ-2.4.2: Prevent deleting categories that are used by
        // transactions
        Long txCount = transactionRepository.countByCategoryId(categoryId);
        if (txCount != null && txCount > 0) {
            throw new InvalidCategoryException(
                    "Cannot delete category that is assigned to "
                            + txCount
                            + " transaction(s). Reassign or delete the transactions first.");
        }

        // Capture snapshot before delete for history (only if key provided)
        CategoryResponse beforeDeleteSnapshot = null;
        String decryptedName = null;
        if (EncryptionContext.getKey() != null) {
            try {
                beforeDeleteSnapshot = toResponseWithDecryption(category);
                decryptedName = beforeDeleteSnapshot.getName();
            } catch (Exception e) {
                log.warn("Failed to capture snapshot for history: {}", e.getMessage());
            }
        }

        // Delete category
        categoryRepository.delete(category);
        log.info("Category deleted successfully: id={}", categoryId);

        // Record in operation history (if not suppressed)
        operationHistoryService.record(
                userId,
                org.openfinance.entity.EntityType.CATEGORY,
                categoryId,
                decryptedName != null ? decryptedName : "Category " + categoryId,
                org.openfinance.entity.OperationType.DELETE,
                beforeDeleteSnapshot,
                null);
    }

    /**
     * Retrieves a category by ID for a specific user, decrypting sensitive fields
     * and localizing
     * system category names.
     *
     * <p>
     * Requirement REQ-2.4.1: Get category by ID
     *
     * <p>
     * Requirement REQ-2.18: Data encryption at rest
     *
     * @param userId        the ID of the user requesting the category
     * @param categoryId    the ID of the category to retrieve
     * @param encryptionKey the AES-256 encryption key for decrypting sensitive
     *                      fields
     * @return the category response with decrypted data
     * @throws CategoryNotFoundException if category does not exist or does not
     *                                   belong to the user
     */
    @Transactional(readOnly = true)
    public CategoryResponse getCategoryById(Long userId, Long categoryId) {
        return getCategoryById(userId, categoryId, Locale.ENGLISH);
    }

    /**
     * Retrieves a category by ID for a specific user, decrypting sensitive fields
     * and localizing
     * system category names.
     *
     * @param userId        the ID of the user requesting the category
     * @param categoryId    the ID of the category to retrieve
     * @param encryptionKey the AES-256 encryption key for decrypting sensitive
     *                      fields
     * @param locale        the locale for localized category names
     * @return the category response with decrypted and localized data
     * @throws CategoryNotFoundException if category does not exist or does not
     *                                   belong to the user
     */
    @Transactional(readOnly = true)
    public CategoryResponse getCategoryById(
            Long userId, Long categoryId, Locale locale) {
        if (userId == null) {
            throw new IllegalArgumentException("User ID cannot be null");
        }
        if (categoryId == null) {
            throw new IllegalArgumentException("Category ID cannot be null");
        }

        log.debug(
                "Fetching category: id={}, userId={}, locale={}, keyPresent={}",
                categoryId,
                userId,
                locale,
                EncryptionContext.getKey() != null);

        Category category = categoryRepository
                .findByIdAndUserId(categoryId, userId)
                .orElseThrow(() -> new CategoryNotFoundException(categoryId));

        if (EncryptionContext.getKey() == null && !category.getIsSystem()) {
            throw new IllegalArgumentException("Encryption key required for user categories");
        }

        return toResponseWithDecryption(category, locale);
    }

    /**
     * Retrieves all categories for a user.
     *
     * <p>
     * Requirement REQ-2.4.1: List all user categories
     *
     * @param userId        the ID of the user
     * @param encryptionKey the AES-256 encryption key for decrypting sensitive
     *                      fields
     * @return list of categories with decrypted data
     */
    @Transactional(readOnly = true)
    public List<CategoryResponse> getAllCategories(Long userId) {
        return getAllCategories(userId, Locale.ENGLISH);
    }

    /**
     * Retrieves all categories for a user with localized system category names.
     *
     * @param userId        the ID of the user
     * @param encryptionKey the AES-256 encryption key for decrypting sensitive
     *                      fields
     * @param locale        the locale for localized category names
     * @return list of categories with decrypted and localized data
     */
    @Transactional(readOnly = true)
    public List<CategoryResponse> getAllCategories(
            Long userId, Locale locale) {
        if (userId == null) {
            throw new IllegalArgumentException("User ID cannot be null");
        }

        log.debug(
                "Fetching categories for user {}, locale={}, keyPresent={}",
                userId,
                locale,
                EncryptionContext.getKey() != null);

        List<Category> categories = categoryRepository.findByUserId(userId);

        if (EncryptionContext.getKey() == null) {
            log.info(
                    "Encryption key missing for user {}; returning only system categories", userId);
            return categories.stream()
                    .filter(Category::getIsSystem)
                    .map(category -> toResponseWithDecryption(category, locale))
                    .collect(Collectors.toList());
        }

        return categories.stream()
                .map(category -> toResponseWithDecryption(category, locale))
                .collect(Collectors.toList());
    }

    /**
     * Retrieves categories by type (INCOME or EXPENSE).
     *
     * <p>
     * Requirement REQ-2.4.1: Filter categories by type
     *
     * @param userId        the ID of the user
     * @param type          the category type to filter by
     * @param encryptionKey the AES-256 encryption key for decrypting sensitive
     *                      fields
     * @return list of categories matching the type
     */
    @Transactional(readOnly = true)
    public List<CategoryResponse> getCategoriesByType(
            Long userId, CategoryType type) {
        return getCategoriesByType(userId, type, Locale.ENGLISH);
    }

    /**
     * Retrieves categories by type (INCOME or EXPENSE) with localized system
     * category names.
     *
     * @param userId        the ID of the user
     * @param type          the category type to filter by
     * @param encryptionKey the AES-256 encryption key for decrypting sensitive
     *                      fields
     * @param locale        the locale for localized category names
     * @return list of categories matching the type with localized data
     */
    @Transactional(readOnly = true)
    public List<CategoryResponse> getCategoriesByType(
            Long userId, CategoryType type, Locale locale) {
        if (userId == null) {
            throw new IllegalArgumentException("User ID cannot be null");
        }
        if (type == null) {
            throw new IllegalArgumentException("Category type cannot be null");
        }

        log.debug(
                "Fetching categories for user {}, type={}, locale={}, keyPresent={}",
                userId,
                type,
                locale,
                EncryptionContext.getKey() != null);

        List<Category> categories = categoryRepository.findByUserIdAndType(userId, type);

        if (EncryptionContext.getKey() == null) {
            return categories.stream()
                    .filter(Category::getIsSystem)
                    .map(category -> toResponseWithDecryption(category, locale))
                    .collect(Collectors.toList());
        }

        return categories.stream()
                .map(category -> toResponseWithDecryption(category, locale))
                .collect(Collectors.toList());
    }

    /**
     * Retrieves all root categories (categories without a parent).
     *
     * <p>
     * Requirement REQ-2.4.1: List root categories
     *
     * @param userId        the ID of the user
     * @param encryptionKey the AES-256 encryption key for decrypting sensitive
     *                      fields
     * @return list of root categories
     */
    @Transactional(readOnly = true)
    public List<CategoryResponse> getRootCategories(Long userId) {
        if (userId == null) {
            throw new IllegalArgumentException("User ID cannot be null");
        }
        log.debug("Fetching root categories for user {}", userId);

        List<Category> categories = categoryRepository.findRootCategoriesByUserId(userId);

        return categories.stream()
                .map(category -> toResponseWithDecryption(category))
                .collect(Collectors.toList());
    }

    /**
     * Retrieves subcategories of a parent category.
     *
     * <p>
     * Requirement REQ-2.4.1: List subcategories
     *
     * @param userId        the ID of the user
     * @param parentId      the ID of the parent category
     * @param encryptionKey the AES-256 encryption key for decrypting sensitive
     *                      fields
     * @return list of subcategories
     */
    @Transactional(readOnly = true)
    public List<CategoryResponse> getSubcategories(
            Long userId, Long parentId) {
        if (userId == null) {
            throw new IllegalArgumentException("User ID cannot be null");
        }
        if (parentId == null) {
            throw new IllegalArgumentException("Parent ID cannot be null");
        }
        log.debug("Fetching subcategories for user {}, parentId={}", userId, parentId);

        List<Category> categories = categoryRepository.findByUserIdAndParentId(userId, parentId);

        return categories.stream()
                .map(category -> toResponseWithDecryption(category))
                .collect(Collectors.toList());
    }

    /**
     * Retrieves hierarchical category tree for a user.
     *
     * <p>
     * Returns categories organized in a tree structure with parent categories
     * containing their
     * subcategories. This is used by the category management page to display
     * hierarchical category
     * organization.
     *
     * <p>
     * Requirements: REQ-CAT-3.1 - Hierarchical category tree display
     *
     * @param userId        the ID of the user
     * @param encryptionKey the AES-256 encryption key for decrypting sensitive
     *                      fields
     * @return list of root categories with their subcategories nested
     */
    @Transactional(readOnly = true)
    public List<CategoryTreeNode> getCategoryTree(Long userId) {
        return getCategoryTree(userId, Locale.ENGLISH);
    }

    /**
     * Retrieves hierarchical category tree for a user with locale-aware names.
     *
     * @param userId        the ID of the user
     * @param encryptionKey the AES-256 encryption key for decrypting sensitive
     *                      fields
     * @param locale        the display locale for resolving system category names
     * @return list of root categories with their subcategories nested
     */
    @Transactional(readOnly = true)
    public List<CategoryTreeNode> getCategoryTree(
            Long userId, Locale locale) {
        if (userId == null) {
            throw new IllegalArgumentException("User ID cannot be null");
        }
        log.debug(
                "Building category tree for user {}, keyPresent={}", userId, EncryptionContext.getKey() != null);

        // Fetch all categories for the user
        final List<Category> categoriesForTree;

        if (EncryptionContext.getKey() == null) {
            categoriesForTree = categoryRepository.findByUserId(userId).stream()
                    .filter(Category::getIsSystem)
                    .collect(Collectors.toList());
        } else {
            categoriesForTree = categoryRepository.findByUserId(userId);
        }

        // Build tree starting from root categories (those without parent)
        List<CategoryTreeNode> rootNodes = categoriesForTree.stream()
                .filter(c -> c.getParentId() == null)
                .sorted(
                        (c1, c2) -> {
                            // Sort by type first (INCOME before EXPENSE), then by name
                            int typeCompare = c1.getType().compareTo(c2.getType());
                            if (typeCompare != 0)
                                return typeCompare;
                            String name1 = c1.getIsSystem()
                                    ? resolveDisplayName(c1, c1.getName(), locale)
                                    : decryptName(c1);
                            String name2 = c2.getIsSystem()
                                    ? resolveDisplayName(c2, c2.getName(), locale)
                                    : decryptName(c2);
                            return name1.compareToIgnoreCase(name2);
                        })
                .map(c -> buildTreeNode(c, categoriesForTree, locale))
                .collect(Collectors.toList());

        return rootNodes;
    }

    /** Recursively builds a tree node from a category entity. */
    private CategoryTreeNode buildTreeNode(
            Category category,
            List<Category> allCategories,
            Locale locale) {
        // Find subcategories
        List<CategoryTreeNode> children = allCategories.stream()
                .filter(c -> category.getId().equals(c.getParentId()))
                .sorted(
                        (c1, c2) -> {
                            String plainName1 = c1.getIsSystem()
                                    ? c1.getName()
                                    : decryptName(c1);
                            String plainName2 = c2.getIsSystem()
                                    ? c2.getName()
                                    : decryptName(c2);
                            String name1 = resolveDisplayName(c1, plainName1, locale);
                            String name2 = resolveDisplayName(c2, plainName2, locale);
                            return name1.compareToIgnoreCase(name2);
                        })
                .map(c -> buildTreeNode(c, allCategories, locale))
                .collect(Collectors.toList());

        // Get decrypted/localized name
        String plainName = category.getIsSystem() ? category.getName() : decryptName(category);
        String name = resolveDisplayName(category, plainName, locale);

        // Get transaction count
        Long transactionCount = transactionRepository.countByCategoryId(category.getId());

        // Get total amount (computed in Java — SQL SUM cannot operate on encrypted
        // amounts)
        List<org.openfinance.entity.Transaction> categoryTransactions = transactionRepository
                .findByCategoryId(category.getId());
        java.math.BigDecimal totalAmount = categoryTransactions.stream()
                .filter(t -> t.getAmount() != null)
                .map(org.openfinance.entity.Transaction::getAmount)
                .reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add);

        // Bug #2 fix: roll up child transaction counts and amounts so parent totals
        // include all nested subcategory transactions (not just direct ones)
        long rollupCount = (transactionCount != null ? transactionCount : 0L)
                + children.stream()
                        .mapToLong(
                                c -> c.getTransactionCount() != null
                                        ? c.getTransactionCount()
                                        : 0L)
                        .sum();
        BigDecimal rollupAmount = (totalAmount != null ? totalAmount : BigDecimal.ZERO)
                .add(
                        children.stream()
                                .map(CategoryTreeNode::getTotalAmount)
                                .filter(Objects::nonNull)
                                .reduce(BigDecimal.ZERO, BigDecimal::add));

        return CategoryTreeNode.builder()
                .id(category.getId())
                .name(name)
                .type(category.getType())
                .icon(category.getIcon())
                .color(category.getColor())
                .mccCode(category.getMccCode())
                .parentId(category.getParentId())
                .subcategories(children)
                .transactionCount(rollupCount)
                .totalAmount(rollupAmount)
                .isSystem(category.getIsSystem())
                .build();
    }

    /** Returns the category name (already decrypted by JPA converter). */
    private String decryptName(Category category) {
        return category.getName();
    }

    /**
     * Checks that no other category with the same name (case-insensitive) exists
     * for the user.
     *
     * <p>
     * Because user-category names are encrypted at rest, this check decrypts all
     * existing names
     * and compares them in-memory. System-category names are stored in plain text.
     *
     * <p>
     * Bug #5 fix: prevent duplicate category names.
     *
     * @param userId        the owning user
     * @param requestedName the plain-text name being requested
     * @param excludeId     optional ID of the category being updated (to exclude
     *                      itself from the check)
     * @param encryptionKey the user's encryption key
     * @throws InvalidCategoryException if a duplicate name already exists
     */
    private void checkDuplicateName(
            Long userId, String requestedName, Long excludeId) {
        String normalised = requestedName.trim().toLowerCase();
        List<Category> existing = categoryRepository.findByUserId(userId);
        for (Category cat : existing) {
            if (excludeId != null && cat.getId().equals(excludeId))
                continue;
            String plainName = cat.getIsSystem() ? cat.getName() : decryptName(cat);
            if (plainName != null && plainName.trim().toLowerCase().equals(normalised)) {
                throw new InvalidCategoryException(
                        "A category named '" + requestedName + "' already exists.");
            }
        }
    }

    /**
     * Validates that a parent category exists, belongs to the user, and has
     * matching type.
     *
     * @param userId    the ID of the user
     * @param parentId  the ID of the parent category
     * @param childType the type of the child category
     * @throws CategoryNotFoundException if parent does not exist
     * @throws InvalidCategoryException  if parent-child validation fails
     */
    private void validateParentCategory(Long userId, Long parentId, CategoryType childType) {
        Category parent = categoryRepository
                .findByIdAndUserId(parentId, userId)
                .orElseThrow(() -> new CategoryNotFoundException(parentId));

        // Parent and child must have the same type
        if (parent.getType() != childType) {
            throw new InvalidCategoryException(
                    String.format(
                            "Parent category type (%s) must match child category type (%s)",
                            parent.getType(), childType));
        }
    }

    /**
     * Resolves the display name for a category in the requested locale.
     *
     * <p>
     * For system categories with a {@code nameKey}, the name is resolved via {@link
     * MessageSource} with the English name as the fallback. For user-created
     * categories, the stored
     * name (already decrypted by the caller) is returned unchanged.
     *
     * @param category  the category entity
     * @param plainName the already-decrypted (or plain-text system) name
     * @param locale    the desired display locale
     * @return localized display name
     */
    private String resolveDisplayName(Category category, String plainName, Locale locale) {
        if (Boolean.TRUE.equals(category.getIsSystem())
                && category.getNameKey() != null
                && !category.getNameKey().isBlank()) {
            return messageSource.getMessage(category.getNameKey(), null, plainName, locale);
        }
        return plainName;
    }

    /**
     * Converts a Category entity to CategoryResponse with decryption.
     *
     * <p>
     * System categories are NOT encrypted, so decryption is only applied to user
     * categories.
     *
     * @param category      the category entity
     * @param encryptionKey the decryption key
     * @return the response DTO with decrypted data
     */
    private CategoryResponse toResponseWithDecryption(Category category) {
        return toResponseWithDecryption(category, Locale.ENGLISH);
    }

    /**
     * Converts a Category entity to CategoryResponse with decryption and
     * locale-aware name
     * resolution.
     *
     * <p>
     * System categories are NOT encrypted, so decryption is only applied to user
     * categories.
     * System category names are resolved via {@link MessageSource} using the given
     * locale.
     *
     * @param category      the category entity
     * @param encryptionKey the decryption key
     * @param locale        the locale for resolving system category names
     * @return the response DTO with decrypted/localized data
     */
    private CategoryResponse toResponseWithDecryption(
            Category category, Locale locale) {
        CategoryResponse response = categoryMapper.toResponse(category);

        // Resolve name: system categories use MessageSource; user categories are
        // already decrypted by JPA converter
        if (category.getIsSystem()) {
            response.setName(resolveDisplayName(category, category.getName(), locale));
        } else {
            response.setName(category.getName());
        }

        // Set parent name if parent exists
        if (category.getParentId() != null) {
            categoryRepository
                    .findById(category.getParentId())
                    .ifPresent(
                            parent -> {
                                if (parent.getIsSystem()) {
                                    response.setParentName(
                                            resolveDisplayName(parent, parent.getName(), locale));
                                } else {
                                    response.setParentName(parent.getName());
                                }
                            });
        }

        // Set subcategory count
        int subcategoryCount = categoryRepository.countSubcategories(category.getId());
        response.setSubcategoryCount(subcategoryCount);

        return response;
    }
}
