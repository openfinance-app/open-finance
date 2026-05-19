package org.openfinance.repository;

import java.util.Optional;
import org.openfinance.entity.UserSettings;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * JPA repository interface for {@link UserSettings} entity.
 *
 * <p>Provides CRUD operations and custom query methods for user settings.
 *
 * <p>Key methods:
 *
 * <ul>
 *   <li>{@link #findByUserId(Long)} - Find settings by user ID
 *   <li>{@link #existsByUserId(Long)} - Check if settings exist for a user
 *   <li>{@link #deleteByUserId(Long)} - Delete settings by user ID
 * </ul>
 *
 * <p>Requirement REQ-6.3: User Settings & Preferences
 *
 * @see UserSettings
 * @author Open-Finance Development Team
 * @version 1.0
 * @since 2026-02-02
 */
@Repository
public interface UserSettingsRepository extends JpaRepository<UserSettings, Long> {

    /**
     * Finds user settings by user ID.
     *
     * <p>Returns settings for the specified user if they exist. If the user has no settings yet,
     * returns an empty Optional.
     *
     * @param userId ID of the user
     * @return Optional containing UserSettings if found, empty otherwise
     */
    Optional<UserSettings> findByUserId(Long userId);

    /**
     * Checks if settings exist for the specified user.
     *
     * <p>Useful for determining whether to create default settings or update existing settings.
     *
     * @param userId ID of the user
     * @return true if settings exist, false otherwise
     */
    boolean existsByUserId(Long userId);

    /**
     * Deletes settings for the specified user.
     *
     * <p>Note: This is typically not needed as settings are deleted automatically when the user is
     * deleted (due to CASCADE delete on the foreign key).
     *
     * @param userId ID of the user whose settings should be deleted
     * @return number of records deleted (0 or 1)
     */
    int deleteByUserId(Long userId);
}
