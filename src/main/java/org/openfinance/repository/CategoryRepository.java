package org.openfinance.repository;

import java.util.List;
import java.util.Optional;
import org.openfinance.entity.Category;
import org.openfinance.entity.CategoryType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * Spring Data JPA repository for {@link Category} entity operations.
 *
 * <p>Provides CRUD operations and custom queries for managing transaction categories. Supports
 * hierarchical category structures with parent-child relationships.
 *
 * <p><strong>Key Features:</strong>
 *
 * <ul>
 *   <li>User-scoped queries for data isolation
 *   <li>Type filtering (INCOME vs EXPENSE categories)
 *   <li>Hierarchical navigation (parent/subcategories)
 *   <li>System category management
 * </ul>
 *
 * <p><strong>Requirement REQ-2.10</strong>: Category management and organization
 *
 * @see Category
 * @since 1.0
 */
@Repository
public interface CategoryRepository extends JpaRepository<Category, Long> {

    /**
     * Finds all categories for a specific user.
     *
     * <p>Includes both user-created and system-provided categories. Results ordered by name
     * alphabetically.
     *
     * @param userId the user ID
     * @return list of user's categories, empty list if none found
     */
    @Query("SELECT c FROM Category c WHERE c.userId = :userId ORDER BY c.name ASC")
    List<Category> findByUserId(@Param("userId") Long userId);

    /**
     * Finds a category by ID and user ID.
     *
     * <p>Used for access control - ensures user can only access their own categories.
     *
     * <p>Requirement REQ-3.2: User-specific data isolation
     *
     * @param id the category ID
     * @param userId the user ID
     * @return Optional containing the category if found and owned by user, empty otherwise
     */
    @Query("SELECT c FROM Category c WHERE c.id = :id AND c.userId = :userId")
    Optional<Category> findByIdAndUserId(@Param("id") Long id, @Param("userId") Long userId);

    /**
     * Finds all categories of a specific type for a user.
     *
     * <p>Filter categories by INCOME or EXPENSE type. Results ordered by name alphabetically.
     *
     * <p>Requirement REQ-2.10.1: Category type filtering
     *
     * @param userId the user ID
     * @param type the category type (INCOME or EXPENSE)
     * @return list of categories of specified type, empty list if none found
     */
    @Query(
            "SELECT c FROM Category c WHERE c.userId = :userId AND c.type = :type ORDER BY c.name ASC")
    List<Category> findByUserIdAndType(
            @Param("userId") Long userId, @Param("type") CategoryType type);

    /**
     * Finds all root categories (no parent) for a user.
     *
     * <p>Root categories are top-level categories in the hierarchy. Results ordered by name
     * alphabetically.
     *
     * <p>Requirement REQ-2.10.2: Hierarchical category structure
     *
     * @param userId the user ID
     * @return list of root categories, empty list if none found
     */
    @Query(
            "SELECT c FROM Category c WHERE c.userId = :userId AND c.parentId IS NULL ORDER BY c.name ASC")
    List<Category> findRootCategoriesByUserId(@Param("userId") Long userId);

    /**
     * Finds all subcategories of a parent category.
     *
     * <p>Returns all direct children of the specified parent category. Results ordered by name
     * alphabetically.
     *
     * <p>Requirement REQ-2.10.2: Hierarchical category navigation
     *
     * @param parentId the parent category ID
     * @return list of subcategories, empty list if none found
     */
    @Query("SELECT c FROM Category c WHERE c.parentId = :parentId ORDER BY c.name ASC")
    List<Category> findByParentId(@Param("parentId") Long parentId);

    /**
     * Finds all subcategories of a parent category for a specific user.
     *
     * <p>Combines parent filtering with user ownership check. Results ordered by name
     * alphabetically.
     *
     * @param userId the user ID
     * @param parentId the parent category ID
     * @return list of subcategories, empty list if none found
     */
    @Query(
            "SELECT c FROM Category c WHERE c.userId = :userId AND c.parentId = :parentId ORDER BY c.name ASC")
    List<Category> findByUserIdAndParentId(
            @Param("userId") Long userId, @Param("parentId") Long parentId);

    /**
     * Finds all system-provided categories for a user.
     *
     * <p>System categories are default categories created on user registration. They cannot be
     * deleted but can be customized.
     *
     * @param userId the user ID
     * @return list of system categories, empty list if none found
     */
    @Query(
            "SELECT c FROM Category c WHERE c.userId = :userId AND c.isSystem = true ORDER BY c.name ASC")
    List<Category> findSystemCategoriesByUserId(@Param("userId") Long userId);

    /**
     * Finds all user-created (non-system) categories.
     *
     * <p>Returns only categories created by the user, excluding system defaults. Results ordered by
     * name alphabetically.
     *
     * @param userId the user ID
     * @return list of user-created categories, empty list if none found
     */
    @Query(
            "SELECT c FROM Category c WHERE c.userId = :userId AND c.isSystem = false ORDER BY c.name ASC")
    List<Category> findUserCreatedCategoriesByUserId(@Param("userId") Long userId);

    /**
     * Checks if a category with the given name exists for a user.
     *
     * <p>Used to prevent duplicate category names within a user's categories. Case-sensitive
     * comparison.
     *
     * @param userId the user ID
     * @param name the category name
     * @return true if category exists, false otherwise
     */
    @Query(
            "SELECT CASE WHEN COUNT(c) > 0 THEN true ELSE false END FROM Category c WHERE c.userId = :userId AND c.name = :name")
    boolean existsByUserIdAndName(@Param("userId") Long userId, @Param("name") String name);

    /**
     * Checks if a category has any subcategories.
     *
     * <p>Used to prevent deletion of parent categories that have children. Must delete children
     * first or reassign them.
     *
     * @param parentId the parent category ID
     * @return true if category has subcategories, false otherwise
     */
    @Query(
            "SELECT CASE WHEN COUNT(c) > 0 THEN true ELSE false END FROM Category c WHERE c.parentId = :parentId")
    boolean hasSubcategories(@Param("parentId") Long parentId);

    /**
     * Counts categories for a user.
     *
     * <p>Used for statistics and validation.
     *
     * @param userId the user ID
     * @return number of categories
     */
    @Query("SELECT COUNT(c) FROM Category c WHERE c.userId = :userId")
    Long countByUserId(@Param("userId") Long userId);

    /**
     * Counts categories of a specific type for a user.
     *
     * <p>Used for statistics (e.g., "You have 15 expense categories").
     *
     * @param userId the user ID
     * @param type the category type
     * @return number of categories of specified type
     */
    @Query("SELECT COUNT(c) FROM Category c WHERE c.userId = :userId AND c.type = :type")
    Long countByUserIdAndType(@Param("userId") Long userId, @Param("type") CategoryType type);

    /**
     * Counts the number of direct subcategories for a parent category.
     *
     * <p>Used for displaying subcategory count in UI.
     *
     * @param parentId the parent category ID
     * @return number of subcategories
     */
    @Query("SELECT COUNT(c) FROM Category c WHERE c.parentId = :parentId")
    int countSubcategories(@Param("parentId") Long parentId);

    /**
     * Finds all categories whose IDs are in the given collection.
     *
     * <p>Used for batch-loading categories to avoid N+1 query problems. Callers collect all needed
     * category IDs from a list of transactions, then issue a single query here instead of calling
     * {@link #findById} in a loop.
     *
     * <p>Requirement REQ-3.1: Performance – avoid per-row database round-trips
     *
     * @param ids collection of category IDs to fetch (must not be null or empty)
     * @return list of matching categories; order is not guaranteed
     */
    @Query("SELECT c FROM Category c WHERE c.id IN :ids")
    List<Category> findAllByIds(@Param("ids") java.util.Collection<Long> ids);
}
