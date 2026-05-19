package org.openfinance.repository;

import java.util.List;
import org.openfinance.entity.Category;
import org.openfinance.entity.Payee;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * Repository for Payee entity operations.
 *
 * <p>Requirements: Payee Management Feature
 */
@Repository
public interface PayeeRepository extends JpaRepository<Payee, Long> {

    /** Find all payees ordered by name. */
    List<Payee> findAllByOrderByNameAsc();

    /** Find all payees visible to a specific user (system + user's own custom). */
    @Query("SELECT p FROM Payee p WHERE p.isSystem = true OR p.userId = :userId ORDER BY p.name")
    List<Payee> findAllByUser(@Param("userId") Long userId);

    /** Find all active payees ordered by name. */
    List<Payee> findByIsActiveTrueOrderByNameAsc();

    /** Find system payees (default merchants/providers). */
    List<Payee> findByIsSystemTrueOrderByNameAsc();

    /** Find custom (user-created) payees. */
    List<Payee> findByIsSystemFalseOrderByNameAsc();

    /** Find custom payees created by a specific user. */
    List<Payee> findByIsSystemFalseAndUserIdOrderByNameAsc(Long userId);

    /** Find payees by default category. */
    List<Payee> findByDefaultCategoryOrderByNameAsc(Category defaultCategory);

    /** Find payees by default category and active status. */
    List<Payee> findByDefaultCategoryAndIsActiveTrueOrderByNameAsc(Category defaultCategory);

    /** Find payees by default category ID. */
    @Query("SELECT p FROM Payee p WHERE p.defaultCategory.id = :categoryId ORDER BY p.name")
    List<Payee> findByDefaultCategoryId(@Param("categoryId") Long categoryId);

    /** Find payees by default category ID and active status. */
    @Query(
            "SELECT p FROM Payee p WHERE p.defaultCategory.id = :categoryId AND p.isActive = true ORDER BY p.name")
    List<Payee> findByDefaultCategoryIdAndIsActiveTrue(@Param("categoryId") Long categoryId);

    /** Find payees by default category ID visible to a specific user and active. */
    @Query(
            "SELECT p FROM Payee p WHERE p.defaultCategory.id = :categoryId AND p.isActive = true AND (p.isSystem = true OR p.userId = :userId) ORDER BY p.name")
    List<Payee> findByDefaultCategoryIdAndIsActiveTrueAndUser(
            @Param("categoryId") Long categoryId, @Param("userId") Long userId);

    /** Search payees by name (case-insensitive contains). */
    @Query(
            "SELECT p FROM Payee p WHERE LOWER(p.name) LIKE LOWER(CONCAT('%', :name, '%')) AND p.isActive = true ORDER BY p.name")
    List<Payee> searchByName(@Param("name") String name);

    /** Search payees by name visible to a specific user. */
    @Query(
            "SELECT p FROM Payee p WHERE LOWER(p.name) LIKE LOWER(CONCAT('%', :name, '%')) AND p.isActive = true AND (p.isSystem = true OR p.userId = :userId) ORDER BY p.name")
    List<Payee> searchByNameAndUser(@Param("name") String name, @Param("userId") Long userId);

    /** Search payees by name including inactive (for management). */
    @Query(
            "SELECT p FROM Payee p WHERE LOWER(p.name) LIKE LOWER(CONCAT('%', :name, '%')) ORDER BY p.name")
    List<Payee> searchAllByName(@Param("name") String name);

    /** Find payee by exact name (case-insensitive). */
    Payee findByNameIgnoreCase(String name);

    /**
     * Find payee by exact name and user (case-insensitive). Matches system payees or payees owned
     * by the given user.
     */
    @Query(
            "SELECT p FROM Payee p WHERE LOWER(p.name) = LOWER(:name) AND (p.isSystem = true OR p.userId = :userId)")
    Payee findByNameIgnoreCaseAndUser(@Param("name") String name, @Param("userId") Long userId);

    /** Check if payee exists by name. */
    boolean existsByNameIgnoreCase(String name);

    /** Check if payee exists by name for a specific user (system or user's own). */
    @Query(
            "SELECT COUNT(p) > 0 FROM Payee p WHERE LOWER(p.name) = LOWER(:name) AND (p.isSystem = true OR p.userId = :userId)")
    boolean existsByNameIgnoreCaseAndUser(@Param("name") String name, @Param("userId") Long userId);

    /** Find distinct default category names from payees. */
    @Query(
            "SELECT DISTINCT p.defaultCategory.name FROM Payee p WHERE p.defaultCategory IS NOT NULL ORDER BY p.defaultCategory.name")
    List<String> findDistinctCategoryNames();

    /** Find distinct default categories from payees. */
    @Query(
            "SELECT DISTINCT p.defaultCategory FROM Payee p WHERE p.defaultCategory IS NOT NULL ORDER BY p.defaultCategory.name")
    List<Category> findDistinctCategories();

    /** Find payees that are system default and inactive (hidden by user). */
    List<Payee> findByIsSystemTrueAndIsActiveFalseOrderByNameAsc();

    /** Find active payees (both system and custom). */
    @Query("SELECT p FROM Payee p WHERE p.isActive = true ORDER BY p.isSystem DESC, p.name")
    List<Payee> findAllActiveOrderBySystemFirst();

    /** Find active payees visible to a specific user. */
    @Query(
            "SELECT p FROM Payee p WHERE p.isActive = true AND (p.isSystem = true OR p.userId = :userId) ORDER BY p.isSystem DESC, p.name")
    List<Payee> findAllActiveByUserOrderBySystemFirst(@Param("userId") Long userId);
}
