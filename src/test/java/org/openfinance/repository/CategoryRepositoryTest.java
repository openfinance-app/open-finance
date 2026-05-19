package org.openfinance.repository;

import static org.assertj.core.api.Assertions.*;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.openfinance.entity.Category;
import org.openfinance.entity.CategoryType;
import org.openfinance.entity.User;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.TestPropertySource;

/**
 * Integration tests for CategoryRepository.
 *
 * <p>Uses @DataJpaTest which configures an in-memory H2 database and provides transactional test
 * execution with rollback.
 *
 * <p>Tests cover:
 *
 * <ul>
 *   <li>CRUD operations
 *   <li>User isolation queries
 *   <li>Type filtering (INCOME vs EXPENSE)
 *   <li>Hierarchical queries (parent/subcategories)
 *   <li>System vs user-created categories
 *   <li>Count and existence checks
 * </ul>
 *
 * <p>Requirement REQ-2.10: Category management and organization
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.ANY)
@TestPropertySource(
        properties = {
            "spring.flyway.enabled=false",
            "spring.jpa.hibernate.ddl-auto=create-drop",
            "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect"
        })
@DisplayName("CategoryRepository Integration Tests")
class CategoryRepositoryTest {

    @Autowired private CategoryRepository categoryRepository;

    @Autowired private UserRepository userRepository;

    private User testUser1;
    private User testUser2;
    private Category incomeCategory;
    private Category expenseCategory;
    private Category parentCategory;
    private Category subcategory;

    @BeforeEach
    void setUp() {
        // Clear repositories before each test
        categoryRepository.deleteAll();
        userRepository.deleteAll();

        // Create test users
        testUser1 =
                User.builder()
                        .username("testuser1")
                        .email("user1@example.com")
                        .passwordHash("$2a$10$hashedPasswordExample123456789")
                        .masterPasswordSalt("base64EncodedSaltExample==")
                        .build();
        testUser1 = userRepository.save(testUser1);

        testUser2 =
                User.builder()
                        .username("testuser2")
                        .email("user2@example.com")
                        .passwordHash("$2a$10$hashedPasswordExample987654321")
                        .masterPasswordSalt("base64EncodedSaltExample22==")
                        .build();
        testUser2 = userRepository.save(testUser2);

        // Create test categories
        incomeCategory =
                Category.builder()
                        .userId(testUser1.getId())
                        .name("Salary")
                        .type(CategoryType.INCOME)
                        .icon("💰")
                        .color("#10B981")
                        .isSystem(true)
                        .build();

        expenseCategory =
                Category.builder()
                        .userId(testUser1.getId())
                        .name("Groceries")
                        .type(CategoryType.EXPENSE)
                        .icon("🛒")
                        .color("#EF4444")
                        .isSystem(false)
                        .build();

        parentCategory =
                Category.builder()
                        .userId(testUser1.getId())
                        .name("Shopping")
                        .type(CategoryType.EXPENSE)
                        .icon("🛍️")
                        .color("#8B5CF6")
                        .parentId(null) // Root category
                        .isSystem(false)
                        .build();

        subcategory =
                Category.builder()
                        .userId(testUser1.getId())
                        .name("Electronics")
                        .type(CategoryType.EXPENSE)
                        .icon("📱")
                        .color("#3B82F6")
                        .isSystem(false)
                        .build();
    }

    // === CRUD Operation Tests ===

    @Test
    @DisplayName("Should save category and generate ID")
    void shouldSaveCategoryAndGenerateId() {
        // When
        Category savedCategory = categoryRepository.save(incomeCategory);

        // Then
        assertThat(savedCategory).isNotNull();
        assertThat(savedCategory.getId()).isNotNull();
        assertThat(savedCategory.getUserId()).isEqualTo(testUser1.getId());
        assertThat(savedCategory.getName()).isEqualTo("Salary");
        assertThat(savedCategory.getType()).isEqualTo(CategoryType.INCOME);
        assertThat(savedCategory.getIcon()).isEqualTo("💰");
        assertThat(savedCategory.getColor()).isEqualTo("#10B981");
        assertThat(savedCategory.getIsSystem()).isTrue();
        assertThat(savedCategory.getCreatedAt()).isNotNull();
    }

    @Test
    @DisplayName("Should find category by ID")
    void shouldFindCategoryById() {
        // Given
        Category savedCategory = categoryRepository.save(incomeCategory);

        // When
        Optional<Category> found = categoryRepository.findById(savedCategory.getId());

        // Then
        assertThat(found).isPresent();
        assertThat(found.get().getId()).isEqualTo(savedCategory.getId());
        assertThat(found.get().getName()).isEqualTo("Salary");
    }

    @Test
    @DisplayName("Should update category")
    void shouldUpdateCategory() {
        // Given
        Category savedCategory = categoryRepository.save(incomeCategory);

        // When
        savedCategory.setName("Updated Salary");
        savedCategory.setColor("#FF0000");
        Category updatedCategory = categoryRepository.save(savedCategory);

        // Then
        assertThat(updatedCategory.getName()).isEqualTo("Updated Salary");
        assertThat(updatedCategory.getColor()).isEqualTo("#FF0000");
        assertThat(updatedCategory.getUpdatedAt()).isNotNull();
    }

    @Test
    @DisplayName("Should delete category")
    void shouldDeleteCategory() {
        // Given
        Category savedCategory = categoryRepository.save(incomeCategory);
        Long categoryId = savedCategory.getId();

        // When
        categoryRepository.delete(savedCategory);

        // Then
        Optional<Category> found = categoryRepository.findById(categoryId);
        assertThat(found).isEmpty();
    }

    // === User Isolation Tests ===

    @Test
    @DisplayName("Should find all categories by user ID")
    void shouldFindAllCategoriesByUserId() {
        // Given
        categoryRepository.save(incomeCategory);
        categoryRepository.save(expenseCategory);

        Category user2Category =
                Category.builder()
                        .userId(testUser2.getId())
                        .name("User2 Category")
                        .type(CategoryType.INCOME)
                        .isSystem(false)
                        .build();
        categoryRepository.save(user2Category);

        // When
        List<Category> user1Categories = categoryRepository.findByUserId(testUser1.getId());
        List<Category> user2Categories = categoryRepository.findByUserId(testUser2.getId());

        // Then
        assertThat(user1Categories).hasSize(2);
        assertThat(user1Categories)
                .extracting(Category::getName)
                .containsExactlyInAnyOrder("Salary", "Groceries");

        assertThat(user2Categories).hasSize(1);
        assertThat(user2Categories).extracting(Category::getName).containsExactly("User2 Category");
    }

    @Test
    @DisplayName("Should find category by ID and user ID")
    void shouldFindCategoryByIdAndUserId() {
        // Given
        Category savedCategory = categoryRepository.save(incomeCategory);

        // When
        Optional<Category> found =
                categoryRepository.findByIdAndUserId(savedCategory.getId(), testUser1.getId());
        Optional<Category> notFound =
                categoryRepository.findByIdAndUserId(savedCategory.getId(), testUser2.getId());

        // Then
        assertThat(found).isPresent();
        assertThat(found.get().getName()).isEqualTo("Salary");
        assertThat(notFound).isEmpty(); // Different user cannot access
    }

    // === Type Filtering Tests ===

    @Test
    @DisplayName("Should find categories by user ID and type")
    void shouldFindCategoriesByUserIdAndType() {
        // Given
        categoryRepository.save(incomeCategory);
        categoryRepository.save(expenseCategory);
        categoryRepository.save(parentCategory);

        // When
        List<Category> incomeCategories =
                categoryRepository.findByUserIdAndType(testUser1.getId(), CategoryType.INCOME);
        List<Category> expenseCategories =
                categoryRepository.findByUserIdAndType(testUser1.getId(), CategoryType.EXPENSE);

        // Then
        assertThat(incomeCategories).hasSize(1);
        assertThat(incomeCategories.get(0).getName()).isEqualTo("Salary");

        assertThat(expenseCategories).hasSize(2);
        assertThat(expenseCategories)
                .extracting(Category::getName)
                .containsExactlyInAnyOrder("Groceries", "Shopping");
    }

    @Test
    @DisplayName("Should count categories by user ID and type")
    void shouldCountCategoriesByUserIdAndType() {
        // Given
        categoryRepository.save(incomeCategory);
        categoryRepository.save(expenseCategory);
        categoryRepository.save(parentCategory);

        // When
        Long incomeCount =
                categoryRepository.countByUserIdAndType(testUser1.getId(), CategoryType.INCOME);
        Long expenseCount =
                categoryRepository.countByUserIdAndType(testUser1.getId(), CategoryType.EXPENSE);

        // Then
        assertThat(incomeCount).isEqualTo(1L);
        assertThat(expenseCount).isEqualTo(2L);
    }

    // === Hierarchical Structure Tests ===

    @Test
    @DisplayName("Should find root categories (no parent)")
    void shouldFindRootCategories() {
        // Given - Save parent first to get its ID
        Category savedParent = categoryRepository.save(parentCategory);

        // Set parent ID on subcategory and save
        subcategory.setParentId(savedParent.getId());
        categoryRepository.save(subcategory);

        // Save a root category
        categoryRepository.save(incomeCategory);

        // When
        List<Category> rootCategories =
                categoryRepository.findRootCategoriesByUserId(testUser1.getId());

        // Then
        assertThat(rootCategories).hasSize(2);
        assertThat(rootCategories)
                .extracting(Category::getName)
                .containsExactlyInAnyOrder("Shopping", "Salary");
        assertThat(rootCategories).allMatch(c -> c.getParentId() == null);
    }

    @Test
    @DisplayName("Should find subcategories by parent ID")
    void shouldFindSubcategoriesByParentId() {
        // Given - Save parent first
        Category savedParent = categoryRepository.save(parentCategory);

        // Create and save subcategories
        subcategory.setParentId(savedParent.getId());
        Category savedSubcategory1 = categoryRepository.save(subcategory);

        Category subcategory2 =
                Category.builder()
                        .userId(testUser1.getId())
                        .name("Clothing")
                        .type(CategoryType.EXPENSE)
                        .parentId(savedParent.getId())
                        .isSystem(false)
                        .build();
        categoryRepository.save(subcategory2);

        // When
        List<Category> subcategories = categoryRepository.findByParentId(savedParent.getId());

        // Then
        assertThat(subcategories).hasSize(2);
        assertThat(subcategories)
                .extracting(Category::getName)
                .containsExactlyInAnyOrder("Electronics", "Clothing");
        assertThat(subcategories).allMatch(c -> c.getParentId().equals(savedParent.getId()));
    }

    @Test
    @DisplayName("Should find subcategories by user ID and parent ID")
    void shouldFindSubcategoriesByUserIdAndParentId() {
        // Given
        Category savedParent = categoryRepository.save(parentCategory);

        subcategory.setParentId(savedParent.getId());
        categoryRepository.save(subcategory);

        // When
        List<Category> subcategories =
                categoryRepository.findByUserIdAndParentId(testUser1.getId(), savedParent.getId());

        // Then
        assertThat(subcategories).hasSize(1);
        assertThat(subcategories.get(0).getName()).isEqualTo("Electronics");
    }

    @Test
    @DisplayName("Should check if category has subcategories")
    void shouldCheckIfCategoryHasSubcategories() {
        // Given
        Category savedParent = categoryRepository.save(parentCategory);
        Category savedIncome = categoryRepository.save(incomeCategory);

        subcategory.setParentId(savedParent.getId());
        categoryRepository.save(subcategory);

        // When
        boolean parentHasChildren = categoryRepository.hasSubcategories(savedParent.getId());
        boolean incomeHasChildren = categoryRepository.hasSubcategories(savedIncome.getId());

        // Then
        assertThat(parentHasChildren).isTrue();
        assertThat(incomeHasChildren).isFalse();
    }

    // === System vs User-Created Categories Tests ===

    @Test
    @DisplayName("Should find system categories")
    void shouldFindSystemCategories() {
        // Given
        categoryRepository.save(incomeCategory); // isSystem = true
        categoryRepository.save(expenseCategory); // isSystem = false

        // When
        List<Category> systemCategories =
                categoryRepository.findSystemCategoriesByUserId(testUser1.getId());

        // Then
        assertThat(systemCategories).hasSize(1);
        assertThat(systemCategories.get(0).getName()).isEqualTo("Salary");
        assertThat(systemCategories.get(0).getIsSystem()).isTrue();
    }

    @Test
    @DisplayName("Should find user-created categories")
    void shouldFindUserCreatedCategories() {
        // Given
        categoryRepository.save(incomeCategory); // isSystem = true
        categoryRepository.save(expenseCategory); // isSystem = false
        categoryRepository.save(parentCategory); // isSystem = false

        // When
        List<Category> userCategories =
                categoryRepository.findUserCreatedCategoriesByUserId(testUser1.getId());

        // Then
        assertThat(userCategories).hasSize(2);
        assertThat(userCategories)
                .extracting(Category::getName)
                .containsExactlyInAnyOrder("Groceries", "Shopping");
        assertThat(userCategories).allMatch(c -> !c.getIsSystem());
    }

    // === Existence and Count Tests ===

    @Test
    @DisplayName("Should check if category name exists for user")
    void shouldCheckIfCategoryNameExistsForUser() {
        // Given
        categoryRepository.save(incomeCategory);

        // When
        boolean exists = categoryRepository.existsByUserIdAndName(testUser1.getId(), "Salary");
        boolean notExists =
                categoryRepository.existsByUserIdAndName(testUser1.getId(), "NonExistent");
        boolean otherUserNotExists =
                categoryRepository.existsByUserIdAndName(testUser2.getId(), "Salary");

        // Then
        assertThat(exists).isTrue();
        assertThat(notExists).isFalse();
        assertThat(otherUserNotExists).isFalse(); // Different user
    }

    @Test
    @DisplayName("Should count categories by user ID")
    void shouldCountCategoriesByUserId() {
        // Given
        categoryRepository.save(incomeCategory);
        categoryRepository.save(expenseCategory);
        categoryRepository.save(parentCategory);

        // When
        Long count = categoryRepository.countByUserId(testUser1.getId());

        // Then
        assertThat(count).isEqualTo(3L);
    }

    @Test
    @DisplayName("Should return zero count for user with no categories")
    void shouldReturnZeroCountForUserWithNoCategories() {
        // When
        Long count = categoryRepository.countByUserId(testUser2.getId());

        // Then
        assertThat(count).isEqualTo(0L);
    }

    @Test
    @DisplayName("Should return empty list when finding categories for user with none")
    void shouldReturnEmptyListForUserWithNoCategories() {
        // When
        List<Category> categories = categoryRepository.findByUserId(testUser2.getId());

        // Then
        assertThat(categories).isEmpty();
    }
}
