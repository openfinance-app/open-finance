package org.openfinance.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

/**
 * Entity representing a currency in the Open-Finance system.
 *
 * <p>Currencies follow ISO 4217 standard with 3-letter codes (e.g., USD, EUR, GBP). This entity
 * stores supported currencies for multi-currency financial tracking.
 *
 * <p>Requirement REQ-2.8: Multi-Currency Support - Users can track finances in multiple currencies
 * with automatic conversion to base currency.
 *
 * @author Open-Finance Team
 * @version 1.0
 * @since Sprint 6
 */
@Entity
@Table(
        name = "currencies",
        uniqueConstraints = {@UniqueConstraint(name = "uk_currency_code", columnNames = "code")})
@Getter
@Setter
@ToString
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class Currency {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * ISO 4217 currency code (3 letters). Examples: USD, EUR, GBP, JPY, BTC Requirement REQ-2.8.1:
     * Support standard currency codes
     */
    @NotBlank(message = "Currency code cannot be blank")
    @Pattern(regexp = "^[A-Z]{3,10}$", message = "Currency code must be 3 to 10 uppercase letters")
    @Size(min = 3, max = 10, message = "Currency code must be between 3 and 10 characters")
    @Column(name = "code", nullable = false, length = 10, unique = true)
    @EqualsAndHashCode.Include
    private String code;

    /** Full name of the currency. Examples: US Dollar, Euro, British Pound, Bitcoin */
    @NotBlank(message = "Currency name cannot be blank")
    @Size(max = 100, message = "Currency name cannot exceed 100 characters")
    @Column(name = "name", nullable = false, length = 100)
    private String name;

    /** Currency symbol for display. Examples: $, €, £, ¥, ₿ */
    @NotBlank(message = "Currency symbol cannot be blank")
    @Size(max = 10, message = "Currency symbol cannot exceed 10 characters")
    @Column(name = "symbol", nullable = false, length = 10)
    private String symbol;

    /**
     * Whether this currency is actively supported. Disabled currencies cannot be used for new
     * accounts/transactions.
     */
    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private Boolean isActive = true;

    /**
     * i18n message key for currency name translation. Used for system currencies to resolve
     * localized display name. Format: "currency.{code}" (e.g., "currency.usd", "currency.eur")
     *
     * @since Phase 3.2 of i18n & Localization feature
     */
    @Size(max = 100, message = "Currency name key cannot exceed 100 characters")
    @Column(name = "name_key", length = 100)
    private String nameKey;

    /** Timestamp when this currency was added to the system. */
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /** Timestamp of the last update to this currency record. */
    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
