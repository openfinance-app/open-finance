package org.openfinance.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

/**
 * Entity representing a user in the Open-Finance system.
 *
 * <p>Users have both a login password (hashed with BCrypt) and a master password that derives
 * encryption keys for securing sensitive financial data.
 *
 * <p>Requirement 1.1: User Entity - Core user fields for authentication and encryption
 */
@Entity
@Table(name = "users")
@Data
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Unique username for login authentication. Requirement 1.1.1: Username field with uniqueness
     * constraint
     */
    @NotNull(message = "Username cannot be null")
    @Size(min = 3, max = 50, message = "Username must be between 3 and 50 characters")
    @Column(unique = true, nullable = false, length = 50)
    private String username;

    /**
     * User's email address, must be unique across the system. Requirement 1.1.1: Email field with
     * validation
     */
    @NotNull(message = "Email cannot be null")
    @Email(message = "Email must be valid")
    @Column(unique = true, nullable = false, length = 255)
    private String email;

    /**
     * BCrypt hashed password for login authentication. This is NOT the master password used for
     * encryption. Requirement 1.1.1: Password hash storage
     */
    @ToString.Exclude
    @NotNull(message = "Password hash cannot be null")
    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    /**
     * Salt used with master password to derive encryption keys via PBKDF2. Stored as Base64-encoded
     * string. Requirement 1.1.1: Master password salt for key derivation
     */
    @ToString.Exclude
    @NotNull(message = "Master password salt cannot be null")
    @Column(name = "master_password_salt", nullable = false, length = 255)
    private String masterPasswordSalt;

    /**
     * User's preferred base currency for multi-currency conversion. ISO 4217 currency code (e.g.,
     * "USD", "EUR", "GBP"). Default value is "USD" if not specified. Requirement 6.2.13: Base
     * currency setting for user preferences
     */
    @NotNull(message = "Base currency cannot be null")
    @Size(min = 3, max = 10, message = "Base currency must be between 3 and 10 characters")
    @Column(name = "base_currency", nullable = false, length = 10)
    @Builder.Default
    private String baseCurrency = "USD";

    /**
     * User's optional secondary currency for side-by-side comparison in tooltips. ISO 4217 currency
     * code (e.g., "EUR", "JPY", "GBP"). Nullable — when unset, secondary currency display is
     * omitted from amount tooltips.
     *
     * <p>Requirement REQ-2.2: Secondary currency field
     */
    @Size(max = 3, message = "Secondary currency must be at most 3 characters")
    @Column(name = "secondary_currency", length = 3)
    private String secondaryCurrency;

    /**
     * User's profile image stored as a Base64-encoded data URL (e.g., "data:image/png;base64,...").
     * Nullable — when unset, a default avatar is displayed in the UI. Maximum size is enforced at
     * the API layer (2 MB after Base64 encoding).
     */
    @Column(name = "profile_image", columnDefinition = "TEXT")
    private String profileImage;

    /**
     * Timestamp when the user account was created. Automatically set by Hibernate on first insert.
     */
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /**
     * Timestamp when the user account was last updated. Automatically updated by Hibernate on any
     * modification.
     */
    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    /**
     * Number of consecutive failed login attempts since the last successful login. Reset to 0 on
     * successful authentication. Requirement TASK-15.1.8: Account lockout tracking.
     */
    @Min(value = 0)
    @Column(name = "failed_login_attempts", nullable = false)
    @Builder.Default
    private int failedLoginAttempts = 0;

    /**
     * Timestamp until which the account is locked. {@code null} means the account is not currently
     * locked. Requirement TASK-15.1.8: Account lockout - lock expiry time.
     */
    @Column(name = "locked_until")
    private LocalDateTime lockedUntil;

    /**
     * Timestamp of the most recent successful login. Used for security auditing and session
     * tracking. Requirement TASK-15.1.7: Security logging.
     */
    @Column(name = "last_login_at")
    private LocalDateTime lastLoginAt;

    /**
     * IP address from which the user last logged in successfully. Used for security auditing and
     * anomaly detection. Requirement TASK-15.1.7: Security logging.
     */
    @Column(name = "last_login_ip", length = 45)
    private String lastLoginIp;

    /**
     * Flag indicating whether the user has completed the initial onboarding flow. When false the
     * frontend redirects to /onboarding after the first login so the user can set their country,
     * currencies, language, date format, number format, and currency display preferences before
     * accessing the app.
     */
    @Column(name = "onboarding_complete", nullable = false)
    @Builder.Default
    private boolean onboardingComplete = false;

    /**
     * Returns {@code true} when the account is currently locked (i.e. {@code lockedUntil} is
     * non-null and still in the future).
     *
     * @return {@code true} if the account is locked
     */
    public boolean isAccountLocked() {
        return lockedUntil != null && lockedUntil.isAfter(LocalDateTime.now());
    }

    /**
     * Returns the authorities granted to the user.
     *
     * <p>Currently all users have ROLE_USER authority. In future sprints, role-based access control
     * (RBAC) can be implemented with additional roles.
     *
     * @return Collection of granted authorities (roles)
     */
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_USER"));
    }

    /**
     * Override toString to prevent logging sensitive data. Requirement: Security - Never log
     * password hashes or salts
     */
    @Override
    public String toString() {
        return "User{"
                + "id="
                + id
                + ", username='"
                + username
                + '\''
                + ", email='"
                + email
                + '\''
                + ", baseCurrency='"
                + baseCurrency
                + '\''
                + ", secondaryCurrency='"
                + secondaryCurrency
                + '\''
                + ", createdAt="
                + createdAt
                + ", updatedAt="
                + updatedAt
                + '}';
    }
}
