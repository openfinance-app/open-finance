package org.openfinance.repository;

import java.util.List;
import java.util.Optional;
import org.openfinance.entity.Account;
import org.openfinance.entity.AccountType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * Repository interface for Account entity operations.
 *
 * <p>Provides database access methods for account management including filtering by user, account
 * type, and active status.
 *
 * <p>Requirement REQ-2.2: Account Management - CRUD operations for financial accounts
 */
@Repository
public interface AccountRepository
        extends JpaRepository<Account, Long>, JpaSpecificationExecutor<Account> {

    /**
     * Finds all accounts belonging to a specific user.
     *
     * <p>Requirement REQ-2.2.1: Users can view all their accounts
     *
     * @param userId ID of the user
     * @return List of accounts owned by the user (may be empty)
     */
    List<Account> findByUserId(Long userId);

    /**
     * Finds all active accounts for a specific user.
     *
     * <p>Excludes soft-deleted (inactive) accounts from results.
     *
     * <p>Requirement REQ-2.2.4: Soft delete - only show active accounts in UI
     *
     * @param userId ID of the user
     * @param isActive Flag indicating active status (typically true)
     * @return List of active accounts owned by the user (may be empty)
     */
    List<Account> findByUserIdAndIsActive(Long userId, Boolean isActive);

    /**
     * Finds all accounts of a specific type for a user.
     *
     * <p>Useful for filtering accounts by category (e.g., show only CHECKING accounts).
     *
     * <p>Requirement REQ-2.2.2: Account type categorization and filtering
     *
     * @param userId ID of the user
     * @param type Account type to filter by
     * @return List of accounts matching the type (may be empty)
     */
    List<Account> findByUserIdAndType(Long userId, AccountType type);

    /**
     * Finds an account by ID, ensuring it belongs to the specified user.
     *
     * <p>This method provides authorization check at the repository level, preventing users from
     * accessing accounts they don't own.
     *
     * <p>Requirement REQ-3.2: Authorization - Users can only access their own data
     *
     * @param id Account ID
     * @param userId User ID (for ownership verification)
     * @return Optional containing the account if found and owned by user, empty otherwise
     */
    Optional<Account> findByIdAndUserId(Long id, Long userId);

    /**
     * Checks if an account with the given name already exists for the user.
     *
     * <p>Used to enforce unique account names per user to prevent confusion.
     *
     * <p><strong>Note:</strong> This query checks encrypted names, so it may not prevent duplicate
     * plain-text names if encryption is implemented. Consider implementing application-level checks
     * in AccountService.
     *
     * @param userId User ID
     * @param name Account name (will be encrypted when stored)
     * @return true if an account with this name exists for the user, false otherwise
     */
    boolean existsByUserIdAndName(Long userId, String name);

    /**
     * Counts the total number of accounts for a user.
     *
     * <p>Useful for enforcing account limits or displaying summary statistics.
     *
     * @param userId User ID
     * @return Count of accounts owned by the user
     */
    long countByUserId(Long userId);

    /**
     * Counts active accounts for a user.
     *
     * <p>Excludes soft-deleted accounts from the count.
     *
     * @param userId User ID
     * @param isActive Active status flag (typically true)
     * @return Count of active accounts
     */
    long countByUserIdAndIsActive(Long userId, Boolean isActive);

    /**
     * Finds an account by account number for a specific user.
     *
     * <p>Used during transaction import to match transactions to accounts based on the account
     * number provided in the imported file.
     *
     * @param userId ID of the user
     * @param accountNumber Account number to search for
     * @return Optional containing the account if found, empty otherwise
     */
    Optional<Account> findByUserIdAndAccountNumber(Long userId, String accountNumber);

    /**
     * Finds all accounts whose IDs are in the given set.
     *
     * <p>Used for batch-loading accounts to avoid N+1 query problems. Callers collect all needed
     * account IDs from a list of entities, then issue a single query here instead of calling {@link
     * #findById} in a loop.
     *
     * <p>Requirement REQ-3.1: Performance – avoid per-row database round-trips
     *
     * @param ids collection of account IDs to fetch (must not be null or empty)
     * @return list of matching accounts; order is not guaranteed
     */
    @Query("SELECT a FROM Account a WHERE a.id IN :ids")
    List<Account> findAllByIds(@Param("ids") java.util.Collection<Long> ids);
}
