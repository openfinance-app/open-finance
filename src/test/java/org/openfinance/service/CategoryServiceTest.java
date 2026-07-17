package org.openfinance.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.openfinance.config.EncryptionProperties;
import org.openfinance.dto.CategoryRequest;
import org.openfinance.dto.CategoryResponse;
import org.openfinance.dto.CategoryTreeNode;
import org.openfinance.entity.Category;
import org.openfinance.entity.CategoryType;
import org.openfinance.entity.Transaction;
import org.openfinance.exception.CategoryNotFoundException;
import org.openfinance.exception.InvalidCategoryException;
import org.openfinance.mapper.CategoryMapper;
import org.openfinance.repository.CategoryRepository;
import org.openfinance.repository.TransactionRepository;
import org.openfinance.security.EncryptionContext;
import org.openfinance.security.EncryptionService;
import org.springframework.context.MessageSource;

/**
 * Unit tests for {@link CategoryService} covering CRUD operations and category tree building.
 *
 * <p>Requirement REQ-2.4: Category Management - CRUD operations for transaction categories
 *
 * <p>Requirement REQ-CAT-3.1: Hierarchical category tree display
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("CategoryService Unit Tests")
class CategoryServiceTest {

    @Mock private CategoryRepository categoryRepository;

    @Mock private TransactionRepository transactionRepository;

    @Mock private CategoryMapper categoryMapper;

    @Mock private EncryptionService encryptionService;

    @Mock private MessageSource messageSource;

    @Mock private OperationHistoryService operationHistoryService;

    @Mock private EncryptionProperties encryptionProperties;

    @InjectMocks private CategoryService categoryService;

    private static final Long USER_ID = 1L;
    private static final Long CATEGORY_ID = 10L;

    @BeforeEach
    void setUp() {
        EncryptionContext.setKey(new SecretKeySpec(new byte[32], "AES"));
        when(encryptionProperties.isEnabled()).thenReturn(true);
    }

    @AfterEach
    void tearDown() {
        EncryptionContext.clear();
    }

    // ============================================================
    // Helpers
    // ============================================================

    /** Builds a user (non-system) Category entity. */
    private Category userCategoryFixture(
            Long id, Long userId, String name, CategoryType type, Long parentId) {
        return Category.builder()
                .id(id)
                .userId(userId)
                .name(name)
                .type(type)
                .parentId(parentId)
                .icon("icon")
                .color("#fff")
                .mccCode("5411")
                .isSystem(false)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
    }

    /**
     * Builds a system Category entity with plain-text name (system categories are NOT encrypted).
     */
    private Category systemCategoryFixture(
            Long id, Long userId, String plainName, CategoryType type) {
        return Category.builder()
                .id(id)
                .userId(userId)
                .name(plainName)
                .type(type)
                .parentId(null)
                .icon("icon")
                .color("#000")
                .isSystem(true)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
    }

    private CategoryRequest buildRequest(String name, CategoryType type, Long parentId) {
        return CategoryRequest.builder()
                .name(name)
                .type(type)
                .parentId(parentId)
                .icon("icon")
                .color("#abc")
                .build();
    }

    private CategoryResponse buildResponse(Long id, String name, CategoryType type, Long parentId) {
        CategoryResponse resp = new CategoryResponse();
        resp.setId(id);
        resp.setName(name);
        resp.setType(type);
        resp.setParentId(parentId);
        resp.setIsSystem(false);
        resp.setSubcategoryCount(0);
        return resp;
    }

    // ============================================================
    // createCategory tests
    // ============================================================

    @Test
    @DisplayName("Should create a user category successfully with encryption")
    void shouldCreateCategorySuccessfully() {
        // Arrange
        CategoryRequest request = buildRequest("Groceries", CategoryType.EXPENSE, null);
        Category mapped =
                userCategoryFixture(null, USER_ID, "Groceries", CategoryType.EXPENSE, null);
        Category saved =
                userCategoryFixture(CATEGORY_ID, USER_ID, "Groceries", CategoryType.EXPENSE, null);
        CategoryResponse expected =
                buildResponse(CATEGORY_ID, "Groceries", CategoryType.EXPENSE, null);

        when(categoryMapper.toEntity(request)).thenReturn(mapped);
        when(categoryRepository.save(any(Category.class))).thenReturn(saved);
        when(categoryMapper.toResponse(saved)).thenReturn(expected);
        when(categoryRepository.countSubcategories(CATEGORY_ID)).thenReturn(0);

        // Act
        CategoryResponse result = categoryService.createCategory(USER_ID, request);

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(CATEGORY_ID);
        assertThat(result.getName()).isEqualTo("Groceries");
        verify(categoryRepository).save(any(Category.class));
    }

    @Test
    @DisplayName("Should create a subcategory with valid parent relationship")
    void shouldCreateSubcategoryWithValidParent() {
        // Arrange
        Long parentId = 5L;
        CategoryRequest request = buildRequest("Supermarket", CategoryType.EXPENSE, parentId);
        Category parent =
                userCategoryFixture(parentId, USER_ID, "Shopping", CategoryType.EXPENSE, null);
        Category mapped =
                userCategoryFixture(null, USER_ID, "Supermarket", CategoryType.EXPENSE, parentId);
        Category saved =
                userCategoryFixture(20L, USER_ID, "Supermarket", CategoryType.EXPENSE, parentId);
        CategoryResponse expected =
                buildResponse(20L, "Supermarket", CategoryType.EXPENSE, parentId);

        when(categoryRepository.findByIdAndUserId(parentId, USER_ID))
                .thenReturn(Optional.of(parent));
        when(categoryMapper.toEntity(request)).thenReturn(mapped);
        when(categoryRepository.save(any(Category.class))).thenReturn(saved);
        when(categoryMapper.toResponse(saved)).thenReturn(expected);
        when(categoryRepository.countSubcategories(20L)).thenReturn(0);
        when(categoryRepository.findById(parentId)).thenReturn(Optional.of(parent));

        // Act
        CategoryResponse result = categoryService.createCategory(USER_ID, request);

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.getParentId()).isEqualTo(parentId);
        verify(categoryRepository).findByIdAndUserId(parentId, USER_ID);
    }

    @Test
    @DisplayName("Should throw CategoryNotFoundException when parent does not exist on create")
    void shouldThrowWhenParentNotFoundOnCreate() {
        // Arrange
        Long parentId = 999L;
        CategoryRequest request = buildRequest("Supermarket", CategoryType.EXPENSE, parentId);
        when(categoryRepository.findByIdAndUserId(parentId, USER_ID)).thenReturn(Optional.empty());

        // Act & Assert
        assertThatThrownBy(() -> categoryService.createCategory(USER_ID, request))
                .isInstanceOf(CategoryNotFoundException.class);
    }

    @Test
    @DisplayName("Should allow a child whose type differs from its parent on create")
    void shouldAllowParentTypeMismatchOnCreate() {
        // Arrange — imported taxonomies (e.g. Skrooge) mix income/expense in one tree.
        Long parentId = 5L;
        CategoryRequest request = buildRequest("SubIncome", CategoryType.INCOME, parentId);
        Category parent =
                userCategoryFixture(parentId, USER_ID, "Shopping", CategoryType.EXPENSE, null);
        Category mapped =
                userCategoryFixture(null, USER_ID, "SubIncome", CategoryType.INCOME, parentId);
        Category savedEntity =
                userCategoryFixture(21L, USER_ID, "SubIncome", CategoryType.INCOME, parentId);
        CategoryResponse expected = buildResponse(21L, "SubIncome", CategoryType.INCOME, parentId);

        when(categoryRepository.findByIdAndUserId(parentId, USER_ID))
                .thenReturn(Optional.of(parent));
        when(categoryRepository.findByUserId(USER_ID)).thenReturn(List.of(parent));
        when(categoryMapper.toEntity(request)).thenReturn(mapped);
        when(categoryRepository.save(mapped)).thenReturn(savedEntity);
        when(categoryMapper.toResponse(savedEntity)).thenReturn(expected);
        when(categoryRepository.countSubcategories(21L)).thenReturn(0);

        // Act
        CategoryResponse result = categoryService.createCategory(USER_ID, request);

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.getType()).isEqualTo(CategoryType.INCOME);
        assertThat(result.getParentId()).isEqualTo(parentId);
    }

    @Test
    @DisplayName("Should allow the same leaf name under a different parent on create")
    void shouldAllowDuplicateLeafNameUnderDifferentParent() {
        // Arrange — "Informatique" (root) and "Loisirs:Informatique" (child) may coexist.
        Long parentId = 7L;
        CategoryRequest request = buildRequest("Informatique", CategoryType.EXPENSE, parentId);
        Category parent =
                userCategoryFixture(parentId, USER_ID, "Loisirs", CategoryType.EXPENSE, null);
        Category rootDuplicate =
                userCategoryFixture(3L, USER_ID, "Informatique", CategoryType.EXPENSE, null);
        Category mapped =
                userCategoryFixture(null, USER_ID, "Informatique", CategoryType.EXPENSE, parentId);
        Category savedEntity =
                userCategoryFixture(30L, USER_ID, "Informatique", CategoryType.EXPENSE, parentId);
        CategoryResponse expected =
                buildResponse(30L, "Informatique", CategoryType.EXPENSE, parentId);

        when(categoryRepository.findByIdAndUserId(parentId, USER_ID))
                .thenReturn(Optional.of(parent));
        when(categoryRepository.findByUserId(USER_ID)).thenReturn(List.of(parent, rootDuplicate));
        when(categoryMapper.toEntity(request)).thenReturn(mapped);
        when(categoryRepository.save(mapped)).thenReturn(savedEntity);
        when(categoryMapper.toResponse(savedEntity)).thenReturn(expected);
        when(categoryRepository.countSubcategories(30L)).thenReturn(0);

        // Act
        CategoryResponse result = categoryService.createCategory(USER_ID, request);

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.getParentId()).isEqualTo(parentId);
    }

    @Test
    @DisplayName(
            "Should throw InvalidCategoryException for a duplicate name under the same parent on create")
    void shouldRejectDuplicateLeafNameUnderSameParent() {
        // Arrange
        Long parentId = 7L;
        CategoryRequest request = buildRequest("Informatique", CategoryType.EXPENSE, parentId);
        Category parent =
                userCategoryFixture(parentId, USER_ID, "Loisirs", CategoryType.EXPENSE, null);
        Category sibling =
                userCategoryFixture(8L, USER_ID, "Informatique", CategoryType.EXPENSE, parentId);

        when(categoryRepository.findByIdAndUserId(parentId, USER_ID))
                .thenReturn(Optional.of(parent));
        when(categoryRepository.findByUserId(USER_ID)).thenReturn(List.of(parent, sibling));

        // Act & Assert
        assertThatThrownBy(() -> categoryService.createCategory(USER_ID, request))
                .isInstanceOf(InvalidCategoryException.class)
                .hasMessageContaining("already exists");
    }

    @Test
    @DisplayName("Should throw IllegalArgumentException when userId is null on create")
    void shouldThrowWhenCreateUserIdNull() {
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        categoryService.createCategory(
                                null, buildRequest("Name", CategoryType.EXPENSE, null)));
    }

    @Test
    @DisplayName("Should throw IllegalArgumentException when request is null on create")
    void shouldThrowWhenCreateRequestNull() {
        assertThrows(
                IllegalArgumentException.class,
                () -> categoryService.createCategory(USER_ID, null));
    }

    // ============================================================
    // updateCategory tests
    // ============================================================

    @Test
    @DisplayName("Should update a user category successfully")
    void shouldUpdateCategorySuccessfully() {
        // Arrange
        CategoryRequest request = buildRequest("Updated Name", CategoryType.EXPENSE, null);
        Category existing =
                userCategoryFixture(CATEGORY_ID, USER_ID, "OldName", CategoryType.EXPENSE, null);
        Category saved =
                userCategoryFixture(
                        CATEGORY_ID, USER_ID, "Updated Name", CategoryType.EXPENSE, null);
        CategoryResponse expected =
                buildResponse(CATEGORY_ID, "Updated Name", CategoryType.EXPENSE, null);

        when(categoryRepository.findByIdAndUserId(CATEGORY_ID, USER_ID))
                .thenReturn(Optional.of(existing));
        doNothing().when(categoryMapper).updateEntityFromRequest(request, existing);
        when(categoryRepository.save(existing)).thenReturn(saved);
        when(categoryMapper.toResponse(saved)).thenReturn(expected);
        when(categoryRepository.countSubcategories(CATEGORY_ID)).thenReturn(0);

        // Act
        CategoryResponse result = categoryService.updateCategory(USER_ID, CATEGORY_ID, request);

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.getName()).isEqualTo("Updated Name");
        verify(categoryRepository).save(existing);
    }

    @Test
    @DisplayName("Should throw InvalidCategoryException when updating a system category")
    void shouldThrowWhenUpdatingSystemCategory() {
        // Arrange
        CategoryRequest request = buildRequest("New Name", CategoryType.EXPENSE, null);
        Category systemCat =
                systemCategoryFixture(CATEGORY_ID, USER_ID, "Groceries", CategoryType.EXPENSE);
        when(categoryRepository.findByIdAndUserId(CATEGORY_ID, USER_ID))
                .thenReturn(Optional.of(systemCat));

        // Act & Assert
        assertThatThrownBy(() -> categoryService.updateCategory(USER_ID, CATEGORY_ID, request))
                .isInstanceOf(InvalidCategoryException.class)
                .hasMessageContaining("System categories cannot be updated");
    }

    @Test
    @DisplayName("Should throw CategoryNotFoundException when updating non-existent category")
    void shouldThrowWhenUpdateCategoryNotFound() {
        // Arrange
        when(categoryRepository.findByIdAndUserId(999L, USER_ID)).thenReturn(Optional.empty());

        // Act & Assert
        assertThatThrownBy(
                        () ->
                                categoryService.updateCategory(
                                        USER_ID,
                                        999L,
                                        buildRequest("Name", CategoryType.EXPENSE, null)))
                .isInstanceOf(CategoryNotFoundException.class);
    }

    @Test
    @DisplayName(
            "Should throw InvalidCategoryException when setting category as its own parent on update")
    void shouldThrowWhenCategoryIsOwnParentOnUpdate() {
        // Arrange
        CategoryRequest request = buildRequest("Name", CategoryType.EXPENSE, CATEGORY_ID);
        Category existing =
                userCategoryFixture(CATEGORY_ID, USER_ID, "Name", CategoryType.EXPENSE, null);
        when(categoryRepository.findByIdAndUserId(CATEGORY_ID, USER_ID))
                .thenReturn(Optional.of(existing));
        // Also mock parent lookup (it will try to validate the parent)
        when(categoryRepository.findByIdAndUserId(CATEGORY_ID, USER_ID))
                .thenReturn(Optional.of(existing));

        // Act & Assert — circular reference: trying to set self as parent
        assertThatThrownBy(() -> categoryService.updateCategory(USER_ID, CATEGORY_ID, request))
                .isInstanceOf(InvalidCategoryException.class)
                .hasMessageContaining("cannot be its own parent");
    }

    @Test
    @DisplayName("Should throw IllegalArgumentException when userId is null on update")
    void shouldThrowWhenUpdateUserIdNull() {
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        categoryService.updateCategory(
                                null,
                                CATEGORY_ID,
                                buildRequest("Name", CategoryType.EXPENSE, null)));
    }

    @Test
    @DisplayName("Should throw IllegalArgumentException when categoryId is null on update")
    void shouldThrowWhenUpdateCategoryIdNull() {
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        categoryService.updateCategory(
                                USER_ID, null, buildRequest("Name", CategoryType.EXPENSE, null)));
    }

    @Test
    @DisplayName("Should throw IllegalArgumentException when request is null on update")
    void shouldThrowWhenUpdateRequestNull() {
        assertThrows(
                IllegalArgumentException.class,
                () -> categoryService.updateCategory(USER_ID, CATEGORY_ID, null));
    }

    // ============================================================
    // deleteCategory tests
    // ============================================================

    @Test
    @DisplayName("Should delete a user category successfully")
    void shouldDeleteCategorySuccessfully() {
        // Arrange
        Category existing =
                userCategoryFixture(CATEGORY_ID, USER_ID, "Name", CategoryType.EXPENSE, null);
        when(categoryRepository.findByIdAndUserId(CATEGORY_ID, USER_ID))
                .thenReturn(Optional.of(existing));
        when(categoryRepository.hasSubcategories(CATEGORY_ID)).thenReturn(false);
        doNothing().when(categoryRepository).delete(existing);

        // Act
        categoryService.deleteCategory(USER_ID, CATEGORY_ID);

        // Assert
        verify(categoryRepository).delete(existing);
    }

    @Test
    @DisplayName("Should throw InvalidCategoryException when deleting a system category")
    void shouldThrowWhenDeletingSystemCategory() {
        // Arrange
        Category systemCat =
                systemCategoryFixture(CATEGORY_ID, USER_ID, "Groceries", CategoryType.EXPENSE);
        when(categoryRepository.findByIdAndUserId(CATEGORY_ID, USER_ID))
                .thenReturn(Optional.of(systemCat));

        // Act & Assert
        assertThatThrownBy(() -> categoryService.deleteCategory(USER_ID, CATEGORY_ID))
                .isInstanceOf(InvalidCategoryException.class)
                .hasMessageContaining("System categories cannot be deleted");
        verify(categoryRepository, never()).delete(any());
    }

    @Test
    @DisplayName("Should throw InvalidCategoryException when deleting category with subcategories")
    void shouldThrowWhenDeletingCategoryWithSubcategories() {
        // Arrange
        Category existing =
                userCategoryFixture(CATEGORY_ID, USER_ID, "Shopping", CategoryType.EXPENSE, null);
        when(categoryRepository.findByIdAndUserId(CATEGORY_ID, USER_ID))
                .thenReturn(Optional.of(existing));
        when(categoryRepository.hasSubcategories(CATEGORY_ID)).thenReturn(true);

        // Act & Assert
        assertThatThrownBy(() -> categoryService.deleteCategory(USER_ID, CATEGORY_ID))
                .isInstanceOf(InvalidCategoryException.class)
                .hasMessageContaining("subcategories");
        verify(categoryRepository, never()).delete(any());
    }

    @Test
    @DisplayName("Should throw CategoryNotFoundException when deleting non-existent category")
    void shouldThrowWhenDeleteCategoryNotFound() {
        // Arrange
        when(categoryRepository.findByIdAndUserId(999L, USER_ID)).thenReturn(Optional.empty());

        // Act & Assert
        assertThatThrownBy(() -> categoryService.deleteCategory(USER_ID, 999L))
                .isInstanceOf(CategoryNotFoundException.class);
    }

    @Test
    @DisplayName("Should throw IllegalArgumentException when userId is null on delete")
    void shouldThrowWhenDeleteUserIdNull() {
        assertThrows(
                IllegalArgumentException.class,
                () -> categoryService.deleteCategory(null, CATEGORY_ID));
    }

    @Test
    @DisplayName("Should throw IllegalArgumentException when categoryId is null on delete")
    void shouldThrowWhenDeleteCategoryIdNull() {
        assertThrows(
                IllegalArgumentException.class,
                () -> categoryService.deleteCategory(USER_ID, null));
    }

    // ============================================================
    // getCategoryById tests
    // ============================================================

    @Test
    @DisplayName("Should get user category by ID with decryption")
    void shouldGetCategoryByIdSuccessfully() {
        // Arrange
        Category cat =
                userCategoryFixture(CATEGORY_ID, USER_ID, "Groceries", CategoryType.EXPENSE, null);
        CategoryResponse expected =
                buildResponse(CATEGORY_ID, "Groceries", CategoryType.EXPENSE, null);

        when(categoryRepository.findByIdAndUserId(CATEGORY_ID, USER_ID))
                .thenReturn(Optional.of(cat));
        when(categoryMapper.toResponse(cat)).thenReturn(expected);
        when(categoryRepository.countSubcategories(CATEGORY_ID)).thenReturn(0);

        // Act
        CategoryResponse result = categoryService.getCategoryById(USER_ID, CATEGORY_ID);

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(CATEGORY_ID);
    }

    @Test
    @DisplayName("Should get system category by ID without decryption")
    void shouldGetSystemCategoryByIdWithoutDecryption() {
        // Arrange
        Category systemCat =
                systemCategoryFixture(CATEGORY_ID, USER_ID, "Groceries", CategoryType.EXPENSE);
        CategoryResponse expected =
                buildResponse(CATEGORY_ID, "Groceries", CategoryType.EXPENSE, null);
        expected.setIsSystem(true);

        when(categoryRepository.findByIdAndUserId(CATEGORY_ID, USER_ID))
                .thenReturn(Optional.of(systemCat));
        when(categoryMapper.toResponse(systemCat)).thenReturn(expected);
        when(categoryRepository.countSubcategories(CATEGORY_ID)).thenReturn(0);

        // Act
        CategoryResponse result = categoryService.getCategoryById(USER_ID, CATEGORY_ID);

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.getName()).isEqualTo("Groceries");
        // System categories should NOT be decrypted
        verify(encryptionService, never()).decrypt(anyString(), any());
    }

    @Test
    @DisplayName("Should get user category without encryption key when encryption is disabled")
    void shouldGetUserCategoryWithoutEncryptionKeyWhenEncryptionDisabled() {
        EncryptionContext.clear();
        when(encryptionProperties.isEnabled()).thenReturn(false);
        Category category =
                userCategoryFixture(
                        CATEGORY_ID, USER_ID, "Plain Groceries", CategoryType.EXPENSE, null);
        CategoryResponse response =
                buildResponse(CATEGORY_ID, "Plain Groceries", CategoryType.EXPENSE, null);

        when(categoryRepository.findByIdAndUserId(CATEGORY_ID, USER_ID))
                .thenReturn(Optional.of(category));
        when(categoryMapper.toResponse(category)).thenReturn(response);
        when(categoryRepository.countSubcategories(CATEGORY_ID)).thenReturn(0);

        CategoryResponse result = categoryService.getCategoryById(USER_ID, CATEGORY_ID);

        assertThat(result.getName()).isEqualTo("Plain Groceries");
    }

    @Test
    @DisplayName("Should throw CategoryNotFoundException when category not found by ID")
    void shouldThrowWhenGetByIdNotFound() {
        // Arrange
        when(categoryRepository.findByIdAndUserId(999L, USER_ID)).thenReturn(Optional.empty());

        // Act & Assert
        assertThatThrownBy(() -> categoryService.getCategoryById(USER_ID, 999L))
                .isInstanceOf(CategoryNotFoundException.class);
    }

    @Test
    @DisplayName("Should throw IllegalArgumentException when null parameters on getCategoryById")
    void shouldThrowWhenGetByIdNullParams() {
        assertThrows(
                IllegalArgumentException.class,
                () -> categoryService.getCategoryById(null, CATEGORY_ID, Locale.ENGLISH));
        assertThrows(
                IllegalArgumentException.class,
                () -> categoryService.getCategoryById(USER_ID, null, Locale.ENGLISH));
    }

    // ============================================================
    // getAllCategories tests
    // ============================================================

    @Test
    @DisplayName("Should return all categories with decryption for user categories")
    void shouldGetAllCategoriesSuccessfully() {
        // Arrange
        Category userCat =
                userCategoryFixture(1L, USER_ID, "Groceries", CategoryType.EXPENSE, null);
        Category sysCat = systemCategoryFixture(2L, USER_ID, "Salary", CategoryType.INCOME);
        CategoryResponse userResp = buildResponse(1L, "Groceries", CategoryType.EXPENSE, null);
        CategoryResponse sysResp = buildResponse(2L, "Salary", CategoryType.INCOME, null);
        sysResp.setIsSystem(true);

        when(categoryRepository.findByUserId(USER_ID)).thenReturn(List.of(userCat, sysCat));
        when(categoryMapper.toResponse(userCat)).thenReturn(userResp);
        when(categoryMapper.toResponse(sysCat)).thenReturn(sysResp);
        when(categoryRepository.countSubcategories(anyLong())).thenReturn(0);

        // Act
        List<CategoryResponse> results = categoryService.getAllCategories(USER_ID);

        // Assert
        assertThat(results).hasSize(2);
        verify(categoryRepository).findByUserId(USER_ID);
    }

    @Test
    @DisplayName("Should return user categories without encryption key when encryption is disabled")
    void shouldGetAllCategoriesWithoutEncryptionKeyWhenEncryptionDisabled() {
        EncryptionContext.clear();
        when(encryptionProperties.isEnabled()).thenReturn(false);
        Category userCat =
                userCategoryFixture(1L, USER_ID, "Plain Groceries", CategoryType.EXPENSE, null);
        Category sysCat = systemCategoryFixture(2L, USER_ID, "Salary", CategoryType.INCOME);
        CategoryResponse userResp =
                buildResponse(1L, "Plain Groceries", CategoryType.EXPENSE, null);
        CategoryResponse sysResp = buildResponse(2L, "Salary", CategoryType.INCOME, null);
        sysResp.setIsSystem(true);

        when(categoryRepository.findByUserId(USER_ID)).thenReturn(List.of(userCat, sysCat));
        when(categoryMapper.toResponse(userCat)).thenReturn(userResp);
        when(categoryMapper.toResponse(sysCat)).thenReturn(sysResp);
        when(categoryRepository.countSubcategories(anyLong())).thenReturn(0);

        List<CategoryResponse> results = categoryService.getAllCategories(USER_ID);

        assertThat(results)
                .extracting(CategoryResponse::getName)
                .containsExactly("Plain Groceries", "Salary");
    }

    @Test
    @DisplayName("Should return empty list when user has no categories")
    void shouldReturnEmptyListWhenNoCategories() {
        // Arrange
        when(categoryRepository.findByUserId(USER_ID)).thenReturn(Collections.emptyList());

        // Act
        List<CategoryResponse> results = categoryService.getAllCategories(USER_ID);

        // Assert
        assertThat(results).isEmpty();
    }

    @Test
    @DisplayName("Should throw IllegalArgumentException when userId is null on getAllCategories")
    void shouldThrowWhenGetAllCategoriesUserIdNull() {
        assertThrows(IllegalArgumentException.class, () -> categoryService.getAllCategories(null));
    }

    // ============================================================
    // getCategoriesByType tests
    // ============================================================

    @Test
    @DisplayName("Should filter categories by type EXPENSE")
    void shouldGetCategoriesByTypeExpense() {
        // Arrange
        Category cat = userCategoryFixture(1L, USER_ID, "Food", CategoryType.EXPENSE, null);
        CategoryResponse resp = buildResponse(1L, "Food", CategoryType.EXPENSE, null);

        when(categoryRepository.findByUserIdAndType(USER_ID, CategoryType.EXPENSE))
                .thenReturn(List.of(cat));
        when(categoryMapper.toResponse(cat)).thenReturn(resp);
        when(categoryRepository.countSubcategories(1L)).thenReturn(0);

        // Act
        List<CategoryResponse> results =
                categoryService.getCategoriesByType(USER_ID, CategoryType.EXPENSE);

        // Assert
        assertThat(results).hasSize(1);
        assertThat(results.get(0).getType()).isEqualTo(CategoryType.EXPENSE);
        verify(categoryRepository).findByUserIdAndType(USER_ID, CategoryType.EXPENSE);
    }

    @Test
    @DisplayName(
            "Should return typed user categories without encryption key when encryption is disabled")
    void shouldGetCategoriesByTypeWithoutEncryptionKeyWhenEncryptionDisabled() {
        EncryptionContext.clear();
        when(encryptionProperties.isEnabled()).thenReturn(false);
        Category cat = userCategoryFixture(1L, USER_ID, "Plain Food", CategoryType.EXPENSE, null);
        CategoryResponse resp = buildResponse(1L, "Plain Food", CategoryType.EXPENSE, null);

        when(categoryRepository.findByUserIdAndType(USER_ID, CategoryType.EXPENSE))
                .thenReturn(List.of(cat));
        when(categoryMapper.toResponse(cat)).thenReturn(resp);
        when(categoryRepository.countSubcategories(1L)).thenReturn(0);

        List<CategoryResponse> results =
                categoryService.getCategoriesByType(USER_ID, CategoryType.EXPENSE);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).getName()).isEqualTo("Plain Food");
    }

    @Test
    @DisplayName("Should throw IllegalArgumentException when userId is null on getCategoriesByType")
    void shouldThrowWhenGetCategoriesByTypeUserIdNull() {
        assertThrows(
                IllegalArgumentException.class,
                () -> categoryService.getCategoriesByType(null, CategoryType.EXPENSE));
    }

    // ============================================================
    // getRootCategories tests
    // ============================================================

    @Test
    @DisplayName("Should return only root categories")
    void shouldGetRootCategoriesSuccessfully() {
        // Arrange
        Category root = systemCategoryFixture(1L, USER_ID, "Shopping", CategoryType.EXPENSE);
        CategoryResponse resp = buildResponse(1L, "Shopping", CategoryType.EXPENSE, null);
        resp.setIsSystem(true);

        when(categoryRepository.findRootCategoriesByUserId(USER_ID)).thenReturn(List.of(root));
        when(categoryMapper.toResponse(root)).thenReturn(resp);
        when(categoryRepository.countSubcategories(1L)).thenReturn(3);

        // Act
        List<CategoryResponse> results = categoryService.getRootCategories(USER_ID);

        // Assert
        assertThat(results).hasSize(1);
        verify(categoryRepository).findRootCategoriesByUserId(USER_ID);
    }

    // ============================================================
    // getSubcategories tests
    // ============================================================

    @Test
    @DisplayName("Should return subcategories for a given parent")
    void shouldGetSubcategoriesSuccessfully() {
        // Arrange
        Long parentId = 5L;
        Category sub =
                userCategoryFixture(20L, USER_ID, "Supermarket", CategoryType.EXPENSE, parentId);
        CategoryResponse resp = buildResponse(20L, "Supermarket", CategoryType.EXPENSE, parentId);

        when(categoryRepository.findByUserIdAndParentId(USER_ID, parentId))
                .thenReturn(List.of(sub));
        when(categoryMapper.toResponse(sub)).thenReturn(resp);
        when(categoryRepository.countSubcategories(20L)).thenReturn(0);
        when(categoryRepository.findById(parentId))
                .thenReturn(Optional.empty()); // no parent name needed

        // Act
        List<CategoryResponse> results = categoryService.getSubcategories(USER_ID, parentId);

        // Assert
        assertThat(results).hasSize(1);
        verify(categoryRepository).findByUserIdAndParentId(USER_ID, parentId);
    }

    @Test
    @DisplayName("Should throw IllegalArgumentException when parentId is null on getSubcategories")
    void shouldThrowWhenGetSubcategoriesNullParentId() {
        assertThrows(
                IllegalArgumentException.class,
                () -> categoryService.getSubcategories(USER_ID, null));
    }

    // ============================================================
    // getCategoryTree tests
    // ============================================================

    @Test
    @DisplayName("Should build category tree with root categories and subcategories")
    void shouldBuildCategoryTreeSuccessfully() {
        // Arrange: parent (Shopping/EXPENSE) with one child (Groceries/EXPENSE)
        Category parent = systemCategoryFixture(1L, USER_ID, "Shopping", CategoryType.EXPENSE);
        Category child = systemCategoryFixture(2L, USER_ID, "Groceries", CategoryType.EXPENSE);
        child =
                Category.builder()
                        .id(2L)
                        .userId(USER_ID)
                        .name("Groceries")
                        .type(CategoryType.EXPENSE)
                        .parentId(1L)
                        .icon("icon")
                        .color("#000")
                        .isSystem(true)
                        .createdAt(LocalDateTime.now())
                        .updatedAt(LocalDateTime.now())
                        .build();

        when(categoryRepository.findByUserId(USER_ID)).thenReturn(List.of(parent, child));
        when(transactionRepository.countByCategoryId(1L)).thenReturn(5L);
        when(transactionRepository.countByCategoryId(2L)).thenReturn(3L);
        when(transactionRepository.findByCategoryId(1L))
                .thenReturn(
                        List.of(Transaction.builder().amount(BigDecimal.valueOf(500.0)).build()));
        when(transactionRepository.findByCategoryId(2L))
                .thenReturn(
                        List.of(Transaction.builder().amount(BigDecimal.valueOf(300.0)).build()));

        // Act
        List<CategoryTreeNode> tree = categoryService.getCategoryTree(USER_ID);

        // Assert
        assertThat(tree).hasSize(1); // Only root: "Shopping"
        CategoryTreeNode root = tree.get(0);
        assertThat(root.getId()).isEqualTo(1L);
        assertThat(root.getName()).isEqualTo("Shopping");
        // Bug #2 fix: parent transaction count rolls up children counts (5 direct + 3
        // from Groceries = 8)
        assertThat(root.getTransactionCount()).isEqualTo(8L);
        assertThat(root.getSubcategories()).hasSize(1);
        assertThat(root.getSubcategories().get(0).getName()).isEqualTo("Groceries");
    }

    @Test
    @DisplayName("Should decrypt user category names in tree but not system category names")
    void shouldDecryptUserCategoryNamesInTree() {
        // Arrange: user category (not system) - name is stored encrypted
        Category userCat =
                userCategoryFixture(3L, USER_ID, "My Category", CategoryType.INCOME, null);

        when(categoryRepository.findByUserId(USER_ID)).thenReturn(List.of(userCat));
        when(transactionRepository.countByCategoryId(3L)).thenReturn(0L);
        when(transactionRepository.findByCategoryId(3L)).thenReturn(Collections.emptyList());

        // Act
        List<CategoryTreeNode> tree = categoryService.getCategoryTree(USER_ID);

        // Assert
        assertThat(tree).hasSize(1);
        assertThat(tree.get(0).getName()).isEqualTo("My Category");
    }

    @Test
    @DisplayName(
            "Should include user categories in tree without encryption key when encryption is disabled")
    void shouldIncludeUserCategoriesInTreeWithoutEncryptionKeyWhenEncryptionDisabled() {
        EncryptionContext.clear();
        when(encryptionProperties.isEnabled()).thenReturn(false);
        Category userCat =
                userCategoryFixture(3L, USER_ID, "Plain Category", CategoryType.INCOME, null);

        when(categoryRepository.findByUserId(USER_ID)).thenReturn(List.of(userCat));
        when(transactionRepository.countByCategoryId(3L)).thenReturn(0L);
        when(transactionRepository.findByCategoryId(3L)).thenReturn(Collections.emptyList());

        List<CategoryTreeNode> tree = categoryService.getCategoryTree(USER_ID);

        assertThat(tree).hasSize(1);
        assertThat(tree.get(0).getName()).isEqualTo("Plain Category");
    }

    @Test
    @DisplayName("Should return empty tree when user has no categories")
    void shouldReturnEmptyTreeForUserWithNoCategories() {
        // Arrange
        when(categoryRepository.findByUserId(USER_ID)).thenReturn(Collections.emptyList());

        // Act
        List<CategoryTreeNode> tree = categoryService.getCategoryTree(USER_ID);

        // Assert
        assertThat(tree).isEmpty();
    }

    @Test
    @DisplayName("Should sort root categories by type then name in tree")
    void shouldSortRootCategoriesInTree() {
        // Arrange: two root categories - INCOME and EXPENSE
        // CategoryType enum ordinal: INCOME=0, EXPENSE=1, so INCOME sorts before
        // EXPENSE
        Category income = systemCategoryFixture(1L, USER_ID, "Salary", CategoryType.INCOME);
        Category expense = systemCategoryFixture(2L, USER_ID, "Zara", CategoryType.EXPENSE);

        when(categoryRepository.findByUserId(USER_ID)).thenReturn(List.of(expense, income));
        when(transactionRepository.countByCategoryId(anyLong())).thenReturn(0L);
        when(transactionRepository.findByCategoryId(anyLong())).thenReturn(Collections.emptyList());

        // Act
        List<CategoryTreeNode> tree = categoryService.getCategoryTree(USER_ID);

        // Assert — INCOME comes before EXPENSE (enum ordinal order: INCOME=0,
        // EXPENSE=1)
        assertThat(tree).hasSize(2);
        assertThat(tree.get(0).getType()).isEqualTo(CategoryType.INCOME);
        assertThat(tree.get(1).getType()).isEqualTo(CategoryType.EXPENSE);
    }

    @Test
    @DisplayName("Should include transactionCount and totalAmount in tree nodes")
    void shouldIncludeTransactionStatsInTreeNodes() {
        // Arrange
        Category cat = systemCategoryFixture(1L, USER_ID, "Food", CategoryType.EXPENSE);
        when(categoryRepository.findByUserId(USER_ID)).thenReturn(List.of(cat));
        when(transactionRepository.countByCategoryId(1L)).thenReturn(10L);
        when(transactionRepository.findByCategoryId(1L))
                .thenReturn(
                        List.of(Transaction.builder().amount(BigDecimal.valueOf(1234.56)).build()));

        // Act
        List<CategoryTreeNode> tree = categoryService.getCategoryTree(USER_ID);

        // Assert
        assertThat(tree).hasSize(1);
        assertThat(tree.get(0).getTransactionCount()).isEqualTo(10L);
        assertThat(tree.get(0).getTotalAmount()).isEqualByComparingTo(BigDecimal.valueOf(1234.56));
    }

    @Test
    @DisplayName("Should throw IllegalArgumentException when userId is null on getCategoryTree")
    void shouldThrowWhenGetCategoryTreeUserIdNull() {
        assertThrows(IllegalArgumentException.class, () -> categoryService.getCategoryTree(null));
    }

    @Test
    @DisplayName("Should build multi-level tree with correct parent-child nesting")
    void shouldBuildMultiLevelTree() {
        // Arrange: Shopping → Groceries → Fresh Produce (3 levels)
        Category lvl0 = systemCategoryFixture(1L, USER_ID, "Shopping", CategoryType.EXPENSE);
        Category lvl1 =
                Category.builder()
                        .id(2L)
                        .userId(USER_ID)
                        .name("Groceries")
                        .type(CategoryType.EXPENSE)
                        .parentId(1L)
                        .icon("ic")
                        .color("#000")
                        .isSystem(true)
                        .createdAt(LocalDateTime.now())
                        .updatedAt(LocalDateTime.now())
                        .build();
        Category lvl2 =
                Category.builder()
                        .id(3L)
                        .userId(USER_ID)
                        .name("Fresh Produce")
                        .type(CategoryType.EXPENSE)
                        .parentId(2L)
                        .icon("ic")
                        .color("#000")
                        .isSystem(true)
                        .createdAt(LocalDateTime.now())
                        .updatedAt(LocalDateTime.now())
                        .build();

        when(categoryRepository.findByUserId(USER_ID)).thenReturn(List.of(lvl0, lvl1, lvl2));
        when(transactionRepository.countByCategoryId(anyLong())).thenReturn(0L);
        when(transactionRepository.findByCategoryId(anyLong())).thenReturn(Collections.emptyList());

        // Act
        List<CategoryTreeNode> tree = categoryService.getCategoryTree(USER_ID);

        // Assert
        assertThat(tree).hasSize(1);
        CategoryTreeNode root = tree.get(0);
        assertThat(root.getSubcategories()).hasSize(1);
        CategoryTreeNode mid = root.getSubcategories().get(0);
        assertThat(mid.getSubcategories()).hasSize(1);
        assertThat(mid.getSubcategories().get(0).getName()).isEqualTo("Fresh Produce");
    }

    // ============================================================
    // Locale Resolution Tests (i18n)
    // ============================================================

    @Test
    @DisplayName("Should resolve system category name in English locale")
    void shouldResolveSystemCategoryNameInEnglish() {
        // Arrange
        Category systemCat =
                systemCategoryFixture(CATEGORY_ID, USER_ID, "Groceries", CategoryType.EXPENSE);
        systemCat.setNameKey("category.groceries");
        CategoryResponse expected =
                buildResponse(CATEGORY_ID, "Groceries", CategoryType.EXPENSE, null);
        expected.setIsSystem(true);

        when(categoryRepository.findByIdAndUserId(CATEGORY_ID, USER_ID))
                .thenReturn(Optional.of(systemCat));
        when(categoryMapper.toResponse(systemCat)).thenReturn(expected);
        when(categoryRepository.countSubcategories(CATEGORY_ID)).thenReturn(0);
        when(messageSource.getMessage(
                        eq("category.groceries"), any(), eq("Groceries"), eq(Locale.ENGLISH)))
                .thenReturn("Groceries");

        // Act
        CategoryResponse result =
                categoryService.getCategoryById(USER_ID, CATEGORY_ID, Locale.ENGLISH);

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.getName()).isEqualTo("Groceries");
        verify(messageSource)
                .getMessage(eq("category.groceries"), any(), eq("Groceries"), eq(Locale.ENGLISH));
        verify(encryptionService, never()).decrypt(anyString(), any());
    }

    @Test
    @DisplayName("Should resolve system category name in French locale")
    void shouldResolveSystemCategoryNameInFrench() {
        // Arrange
        Category systemCat =
                systemCategoryFixture(CATEGORY_ID, USER_ID, "Groceries", CategoryType.EXPENSE);
        systemCat.setNameKey("category.groceries");
        CategoryResponse expected =
                buildResponse(CATEGORY_ID, "Épicerie", CategoryType.EXPENSE, null);
        expected.setIsSystem(true);

        when(categoryRepository.findByIdAndUserId(CATEGORY_ID, USER_ID))
                .thenReturn(Optional.of(systemCat));
        when(categoryMapper.toResponse(systemCat)).thenReturn(expected);
        when(categoryRepository.countSubcategories(CATEGORY_ID)).thenReturn(0);
        when(messageSource.getMessage(
                        eq("category.groceries"), any(), eq("Groceries"), eq(Locale.FRENCH)))
                .thenReturn("Épicerie");

        // Act
        CategoryResponse result =
                categoryService.getCategoryById(USER_ID, CATEGORY_ID, Locale.FRENCH);

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.getName()).isEqualTo("Épicerie");
        verify(messageSource)
                .getMessage(eq("category.groceries"), any(), eq("Groceries"), eq(Locale.FRENCH));
        verify(encryptionService, never()).decrypt(anyString(), any());
    }

    @Test
    @DisplayName("Should use fallback name when message key is not found")
    void shouldUseFallbackWhenMessageKeyNotFound() {
        // Arrange
        Category systemCat =
                systemCategoryFixture(
                        CATEGORY_ID, USER_ID, "Custom Category", CategoryType.EXPENSE);
        systemCat.setNameKey("category.unknown");
        CategoryResponse expected =
                buildResponse(CATEGORY_ID, "Custom Category", CategoryType.EXPENSE, null);
        expected.setIsSystem(true);

        when(categoryRepository.findByIdAndUserId(CATEGORY_ID, USER_ID))
                .thenReturn(Optional.of(systemCat));
        when(categoryMapper.toResponse(systemCat)).thenReturn(expected);
        when(categoryRepository.countSubcategories(CATEGORY_ID)).thenReturn(0);
        when(messageSource.getMessage(
                        eq("category.unknown"), any(), eq("Custom Category"), eq(Locale.ENGLISH)))
                .thenReturn("Custom Category");

        // Act
        CategoryResponse result =
                categoryService.getCategoryById(USER_ID, CATEGORY_ID, Locale.ENGLISH);

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.getName()).isEqualTo("Custom Category");
    }

    @Test
    @DisplayName("Should decrypt user category name regardless of locale")
    void shouldDecryptUserCategoryNameRegardlessOfLocale() {
        // Arrange - user category (not system)
        Category userCat =
                userCategoryFixture(
                        CATEGORY_ID, USER_ID, "My Category", CategoryType.EXPENSE, null);
        CategoryResponse expected =
                buildResponse(CATEGORY_ID, "My Category", CategoryType.EXPENSE, null);

        when(categoryRepository.findByIdAndUserId(CATEGORY_ID, USER_ID))
                .thenReturn(Optional.of(userCat));
        when(categoryMapper.toResponse(userCat)).thenReturn(expected);
        when(categoryRepository.countSubcategories(CATEGORY_ID)).thenReturn(0);

        // Act - using French locale
        CategoryResponse result =
                categoryService.getCategoryById(USER_ID, CATEGORY_ID, Locale.FRENCH);

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.getName()).isEqualTo("My Category");
        verify(messageSource, never())
                .getMessage(anyString(), any(), anyString(), any(Locale.class));
    }

    @Test
    @DisplayName("Should resolve all system category names in French for getAllCategories")
    void shouldResolveAllSystemCategoryNamesInFrench() {
        // Arrange
        Category systemCat1 = systemCategoryFixture(1L, USER_ID, "Groceries", CategoryType.EXPENSE);
        systemCat1.setNameKey("category.groceries");
        Category systemCat2 = systemCategoryFixture(2L, USER_ID, "Salary", CategoryType.INCOME);
        systemCat2.setNameKey("category.salary");

        CategoryResponse resp1 = buildResponse(1L, "Épicerie", CategoryType.EXPENSE, null);
        resp1.setIsSystem(true);
        CategoryResponse resp2 = buildResponse(2L, "Salaire", CategoryType.INCOME, null);
        resp2.setIsSystem(true);

        when(categoryRepository.findByUserId(USER_ID)).thenReturn(List.of(systemCat1, systemCat2));
        when(categoryMapper.toResponse(systemCat1)).thenReturn(resp1);
        when(categoryMapper.toResponse(systemCat2)).thenReturn(resp2);
        when(categoryRepository.countSubcategories(anyLong())).thenReturn(0);
        when(messageSource.getMessage(
                        eq("category.groceries"), any(), eq("Groceries"), eq(Locale.FRENCH)))
                .thenReturn("Épicerie");
        when(messageSource.getMessage(
                        eq("category.salary"), any(), eq("Salary"), eq(Locale.FRENCH)))
                .thenReturn("Salaire");

        // Act
        List<CategoryResponse> results = categoryService.getAllCategories(USER_ID, Locale.FRENCH);

        // Assert
        assertThat(results).hasSize(2);
        verify(messageSource)
                .getMessage(eq("category.groceries"), any(), eq("Groceries"), eq(Locale.FRENCH));
        verify(messageSource)
                .getMessage(eq("category.salary"), any(), eq("Salary"), eq(Locale.FRENCH));
    }

    @Test
    @DisplayName("Should build category tree with French localized names")
    void shouldBuildCategoryTreeWithFrenchLocalizedNames() {
        // Arrange
        Category parent = systemCategoryFixture(1L, USER_ID, "Shopping", CategoryType.EXPENSE);
        parent.setNameKey("category.shopping");
        Category child = systemCategoryFixture(2L, USER_ID, "Groceries", CategoryType.EXPENSE);
        child.setNameKey("category.groceries");
        child =
                Category.builder()
                        .id(2L)
                        .userId(USER_ID)
                        .name("Groceries")
                        .nameKey("category.groceries")
                        .type(CategoryType.EXPENSE)
                        .parentId(1L)
                        .icon("icon")
                        .color("#000")
                        .isSystem(true)
                        .createdAt(LocalDateTime.now())
                        .updatedAt(LocalDateTime.now())
                        .build();

        when(categoryRepository.findByUserId(USER_ID)).thenReturn(List.of(parent, child));
        when(transactionRepository.countByCategoryId(anyLong())).thenReturn(0L);
        when(transactionRepository.findByCategoryId(anyLong())).thenReturn(Collections.emptyList());
        when(messageSource.getMessage(
                        eq("category.shopping"), any(), eq("Shopping"), eq(Locale.FRENCH)))
                .thenReturn("Achats");
        when(messageSource.getMessage(
                        eq("category.groceries"), any(), eq("Groceries"), eq(Locale.FRENCH)))
                .thenReturn("Épicerie");

        // Act
        List<CategoryTreeNode> tree = categoryService.getCategoryTree(USER_ID, Locale.FRENCH);

        // Assert
        assertThat(tree).hasSize(1);
        CategoryTreeNode root = tree.get(0);
        assertThat(root.getName()).isEqualTo("Achats");
        assertThat(root.getSubcategories()).hasSize(1);
        assertThat(root.getSubcategories().get(0).getName()).isEqualTo("Épicerie");
        verify(messageSource)
                .getMessage(eq("category.shopping"), any(), eq("Shopping"), eq(Locale.FRENCH));
        verify(messageSource)
                .getMessage(eq("category.groceries"), any(), eq("Groceries"), eq(Locale.FRENCH));
    }
}
