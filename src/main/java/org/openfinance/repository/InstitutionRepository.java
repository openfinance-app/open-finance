package org.openfinance.repository;

import java.util.List;
import org.openfinance.entity.Institution;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * Repository for Institution entity operations.
 *
 * <p>Requirements: REQ-2.6.1.3 - Predefined Financial Institutions
 */
@Repository
public interface InstitutionRepository extends JpaRepository<Institution, Long> {

    /** Find all institutions ordered by country and name. */
    List<Institution> findAllByOrderByCountryAscNameAsc();

    /** Find all institutions visible to a specific user (system + user's own custom). */
    @Query(
            "SELECT i FROM Institution i WHERE i.isSystem = true OR i.userId = :userId ORDER BY i.country, i.name")
    List<Institution> findAllByUser(@Param("userId") Long userId);

    /** Find institutions by country code. */
    List<Institution> findByCountryOrderByNameAsc(String country);

    /** Find institutions by country visible to a specific user. */
    @Query(
            "SELECT i FROM Institution i WHERE i.country = :country AND (i.isSystem = true OR i.userId = :userId) ORDER BY i.name")
    List<Institution> findByCountryAndUser(
            @Param("country") String country, @Param("userId") Long userId);

    /** Find system institutions (default EU banks). */
    List<Institution> findByIsSystemTrueOrderByCountryAscNameAsc();

    /** Find custom (user-created) institutions. */
    List<Institution> findByIsSystemFalseOrderByNameAsc();

    /** Find custom institutions created by a specific user. */
    List<Institution> findByIsSystemFalseAndUserIdOrderByNameAsc(Long userId);

    /** Search institutions by name (case-insensitive contains). */
    @Query(
            "SELECT i FROM Institution i WHERE LOWER(i.name) LIKE LOWER(CONCAT('%', :name, '%')) ORDER BY i.country, i.name")
    List<Institution> searchByName(@Param("name") String name);

    /** Search institutions by name visible to a specific user. */
    @Query(
            "SELECT i FROM Institution i WHERE LOWER(i.name) LIKE LOWER(CONCAT('%', :name, '%')) AND (i.isSystem = true OR i.userId = :userId) ORDER BY i.country, i.name")
    List<Institution> searchByNameAndUser(@Param("name") String name, @Param("userId") Long userId);

    /** Find institutions by country and search term. */
    @Query(
            "SELECT i FROM Institution i WHERE i.country = :country AND LOWER(i.name) LIKE LOWER(CONCAT('%', :name, '%')) ORDER BY i.name")
    List<Institution> findByCountryAndSearchTerm(
            @Param("country") String country, @Param("name") String name);

    /** Find institution by BIC code. */
    Institution findByBic(String bic);

    /** Check if institution is in use by any accounts. */
    @Query("SELECT COUNT(a) > 0 FROM Account a WHERE a.institution.id = :institutionId")
    boolean isInUse(@Param("institutionId") Long institutionId);

    /** Find distinct country codes from institutions. */
    @Query(
            "SELECT DISTINCT i.country FROM Institution i WHERE i.country IS NOT NULL ORDER BY i.country")
    List<String> findDistinctCountries();

    /** Find distinct country codes from institutions visible to a specific user. */
    @Query(
            "SELECT DISTINCT i.country FROM Institution i WHERE i.country IS NOT NULL AND (i.isSystem = true OR i.userId = :userId) ORDER BY i.country")
    List<String> findDistinctCountriesByUser(@Param("userId") Long userId);
}
