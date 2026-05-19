package org.openfinance.repository;

import java.util.List;
import java.util.Optional;
import org.openfinance.entity.Liability;
import org.openfinance.entity.LiabilityType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/** Repository for Liability entity Task 6.1.3: Create LiabilityRepository */
@Repository
public interface LiabilityRepository extends JpaRepository<Liability, Long> {

    /** Find all liabilities for a specific user */
    @Query("SELECT l FROM Liability l WHERE l.userId = :userId ORDER BY l.createdAt DESC")
    List<Liability> findByUserIdOrderByCreatedAtDesc(@Param("userId") Long userId);

    /** Find a liability by ID and user ID (for authorization) */
    Optional<Liability> findByIdAndUserId(Long id, Long userId);

    /** Find liabilities by type for a specific user */
    @Query(
            "SELECT l FROM Liability l WHERE l.userId = :userId AND l.type = :type ORDER BY l.createdAt DESC")
    List<Liability> findByUserIdAndTypeOrderByCreatedAtDesc(
            @Param("userId") Long userId, @Param("type") LiabilityType type);

    /** Count liabilities for a user */
    long countByUserId(Long userId);

    /** Check if a liability exists for a user */
    boolean existsByIdAndUserId(Long id, Long userId);

    /** Delete a liability by ID and user ID (for authorization) */
    void deleteByIdAndUserId(Long id, Long userId);

    /**
     * Find liabilities with pagination and optional filtering by type. The {@code search} parameter
     * is intentionally not applied in SQL because the name field is AES-256-encrypted; text search
     * is handled in the service layer after decryption.
     */
    @Query(
            "SELECT l FROM Liability l WHERE l.userId = :userId "
                    + "AND (:type IS NULL OR l.type = :type)")
    Page<Liability> findByUserIdWithFilters(
            @Param("userId") Long userId, @Param("type") LiabilityType type, Pageable pageable);

    /**
     * Fetch all liabilities for a user filtered only by type, without pagination. Used by the
     * service layer for in-memory search / sort on encrypted fields.
     */
    @Query(
            "SELECT l FROM Liability l WHERE l.userId = :userId "
                    + "AND (:type IS NULL OR l.type = :type) "
                    + "ORDER BY l.createdAt DESC")
    List<Liability> findAllByUserIdAndType(
            @Param("userId") Long userId, @Param("type") LiabilityType type);
}
