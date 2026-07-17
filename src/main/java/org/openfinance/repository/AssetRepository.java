package org.openfinance.repository;

import java.util.List;
import java.util.Optional;
import org.openfinance.entity.Asset;
import org.openfinance.entity.AssetType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * Repository interface for Asset entity operations.
 *
 * <p>Provides database access methods for asset management including filtering by user, account,
 * asset type, and symbol.
 *
 * <p>Requirement REQ-2.6: Asset Management - CRUD operations for financial assets
 */
@Repository
public interface AssetRepository
        extends JpaRepository<Asset, Long>, JpaSpecificationExecutor<Asset> {

    /**
     * Finds all assets belonging to a specific user.
     *
     * <p>Requirement REQ-2.6.1: Users can view all their assets
     *
     * @param userId ID of the user
     * @return List of assets owned by the user (may be empty)
     */
    @EntityGraph(attributePaths = {"account"})
    List<Asset> findByUserId(Long userId);

    /**
     * Finds all assets in a specific account.
     *
     * <p>Useful for displaying assets grouped by brokerage account or portfolio.
     *
     * <p>Requirement REQ-2.6.2: Assets can be linked to accounts
     *
     * @param accountId ID of the account
     * @return List of assets in the account (may be empty)
     */
    List<Asset> findByAccountId(Long accountId);

    /**
     * Finds all assets of a specific type for a user.
     *
     * <p>Useful for filtering portfolio by asset class (e.g., show only STOCK assets).
     *
     * <p>Requirement REQ-2.6: Asset type categorization and filtering
     *
     * @param userId ID of the user
     * @param type Asset type to filter by
     * @return List of assets matching the type (may be empty)
     */
    @EntityGraph(attributePaths = {"account"})
    List<Asset> findByUserIdAndType(Long userId, AssetType type);

    /**
     * Finds all assets with a specific symbol for a user.
     *
     * <p>Used to check for duplicate holdings or aggregate positions across accounts.
     *
     * <p>Requirement REQ-2.6.4: Symbol-based asset tracking
     *
     * @param userId ID of the user
     * @param symbol Ticker symbol (e.g., "AAPL", "BTC-USD")
     * @return List of assets with the symbol (may be empty)
     */
    List<Asset> findByUserIdAndSymbol(Long userId, String symbol);

    /**
     * Finds an asset by ID, ensuring it belongs to the specified user.
     *
     * <p>This method provides authorization check at the repository level, preventing users from
     * accessing assets they don't own.
     *
     * <p>Requirement REQ-3.2: Authorization - Users can only access their own data
     *
     * @param id Asset ID
     * @param userId User ID (for ownership verification)
     * @return Optional containing the asset if found and owned by user, empty otherwise
     */
    @EntityGraph(attributePaths = {"account"})
    Optional<Asset> findByIdAndUserId(Long id, Long userId);

    /**
     * Finds all assets in a specific account belonging to a user.
     *
     * <p>Combines account and user filtering for secure asset retrieval.
     *
     * @param userId ID of the user
     * @param accountId ID of the account
     * @return List of assets in the user's account (may be empty)
     */
    @EntityGraph(attributePaths = {"account"})
    List<Asset> findByUserIdAndAccountId(Long userId, Long accountId);

    /**
     * Counts the total number of assets for a user.
     *
     * <p>Useful for displaying summary statistics or enforcing asset limits.
     *
     * @param userId User ID
     * @return Count of assets owned by the user
     */
    long countByUserId(Long userId);

    /**
     * Counts assets of a specific type for a user.
     *
     * <p>Used for portfolio composition analysis.
     *
     * @param userId User ID
     * @param type Asset type
     * @return Count of assets of the specified type
     */
    long countByUserIdAndType(Long userId, AssetType type);

    /**
     * Finds assets with symbols that need price updates.
     *
     * <p>Returns assets that have symbols and support real-time data. Used by market data update
     * services.
     *
     * <p>Requirement REQ-2.6.4: Market data integration for price updates
     *
     * @param userId User ID
     * @return List of assets with symbols (may be empty)
     */
    @Query(
            "SELECT a FROM Asset a WHERE a.userId = :userId AND a.symbol IS NOT NULL AND a.symbol != ''")
    List<Asset> findAssetsWithSymbols(@Param("userId") Long userId);

    /**
     * Finds assets with last price update before a specific date/time.
     *
     * <p>Used for stale quote notifications.
     *
     * @param userId User ID
     * @param threshold DateTime threshold
     * @return List of assets with stale quotes
     */
    @EntityGraph(attributePaths = {"account"})
    List<Asset> findByUserIdAndLastUpdatedBefore(Long userId, java.time.LocalDateTime threshold);
}
