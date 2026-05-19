package org.openfinance.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;
import lombok.*;

/**
 * Entity representing user display and locale preferences.
 *
 * <p>Stores personalized settings for each user including:
 *
 * <ul>
 *   <li>Theme preference (dark/light mode)
 *   <li>Date format display preference
 *   <li>Number format display preference
 *   <li>Language/locale preference
 *   <li>Timezone preference
 * </ul>
 *
 * <p>Has a one-to-one relationship with {@link User} entity. When a user is deleted, their settings
 * are automatically deleted (CASCADE).
 *
 * <p>Default values are set for all preferences to ensure a consistent user experience even before
 * the user customizes their settings.
 *
 * <p>Requirement REQ-6.3: User Settings & Preferences
 *
 * @see User
 * @author Open-Finance Development Team
 * @version 1.0
 * @since 2026-02-02
 */
@Entity
@Table(
        name = "user_settings",
        indexes = {@Index(name = "idx_user_settings_user_id", columnList = "user_id")})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString(onlyExplicitlyIncluded = true)
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class UserSettings {

    /** Primary key for user settings. */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @ToString.Include
    @EqualsAndHashCode.Include
    private Long id;

    /**
     * Reference to the user who owns these settings. One-to-one relationship with User entity.
     * CASCADE delete: when user is deleted, settings are also deleted.
     */
    @OneToOne
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    @NotNull(message = "User is required")
    private User user;

    /** Theme preference: "dark" or "light". Defaults to "dark" for better eye comfort. */
    @Column(name = "theme", length = 10, nullable = false)
    @Builder.Default
    @ToString.Include
    private String theme = "dark";

    /**
     * Date format preference. Options: "MM/DD/YYYY" (US), "DD/MM/YYYY" (European), "YYYY-MM-DD"
     * (ISO). Defaults to "MM/DD/YYYY".
     */
    @Column(name = "date_format", length = 20, nullable = false)
    @Builder.Default
    @ToString.Include
    private String dateFormat = "MM/DD/YYYY";

    /**
     * Number format preference. Options: "1,234.56" (US/UK), "1.234,56" (European), "1 234,56"
     * (French). Defaults to "1,234.56".
     */
    @Column(name = "number_format", length = 20, nullable = false)
    @Builder.Default
    @ToString.Include
    private String numberFormat = "1,234.56";

    /**
     * Language/locale preference. ISO 639-1 language codes: "en", "fr", "es", "de", etc. Defaults
     * to "en" (English).
     */
    @Column(name = "language", length = 10, nullable = false)
    @Builder.Default
    @ToString.Include
    private String language = "en";

    /**
     * Timezone preference. IANA timezone identifiers: "America/New_York", "Europe/Paris",
     * "Asia/Tokyo", etc. Defaults to "UTC".
     */
    @Column(name = "timezone", length = 50, nullable = false)
    @Builder.Default
    @ToString.Include
    private String timezone = "UTC";

    /**
     * Country preference for tool localisation. ISO 3166-1 alpha-2 country codes: "FR", "US", "GB",
     * "DE", etc. Controls country-specific defaults in financial tools (e.g. notary fees, rental
     * tax regimes) and availability of France-only tools. Defaults to "FR".
     */
    @Column(name = "country", length = 2, nullable = false)
    @Builder.Default
    @ToString.Include
    private String country = "FR";

    /**
     * Amount display mode preference. Controls how monetary amounts are rendered in the UI.
     *
     * <ul>
     *   <li>"base" — show amount in base currency (default)
     *   <li>"native" — show amount in native currency
     *   <li>"both" — show both currencies inline
     * </ul>
     */
    @Column(name = "amount_display_mode", length = 10, nullable = false)
    @Builder.Default
    @ToString.Include
    private String amountDisplayMode = "base";

    /** Timestamp when settings were created. Set automatically on entity creation. */
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /** Timestamp when settings were last updated. Updated automatically on entity modification. */
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    /** JPA lifecycle callback: sets creation and update timestamps before persisting. */
    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    /** JPA lifecycle callback: updates the modification timestamp before updating. */
    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
