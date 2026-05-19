package org.openfinance.dto;

import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Lightweight Data Transfer Object for account list responses.
 *
 * <p>This is a sparse-fieldset / summary projection of {@link AccountResponse} intended for
 * high-volume list endpoints where the full payload is unnecessary. Only the most commonly
 * displayed fields are included, reducing serialization overhead and network transfer size.
 *
 * <p>Returned when the caller passes {@code ?summary=true} on {@code GET /api/v1/accounts}.
 *
 * <p>Fields included:
 *
 * <ul>
 *   <li>{@code id} – unique account identifier
 *   <li>{@code name} – decrypted account name
 *   <li>{@code type} – account type as string (CHECKING, SAVINGS, etc.)
 *   <li>{@code currency} – ISO 4217 currency code
 *   <li>{@code balance} – current account balance
 *   <li>{@code isActive} – whether the account is active
 *   <li>{@code institutionName} – institution name, may be null
 * </ul>
 *
 * <p>Requirement TASK-14.1.3: Sparse fieldsets for optimised API response times.
 *
 * <p>Requirement REQ-3.1: API response optimization - sparse fieldsets.
 *
 * @see AccountResponse
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AccountSummaryResponse {

    /** Unique identifier of the account. */
    private Long id;

    /** Name of the account (decrypted). */
    private String name;

    /**
     * Type of the account as a string (CHECKING, SAVINGS, CREDIT_CARD, INVESTMENT, CASH, OTHER).
     */
    private String type;

    /** Currency code in ISO 4217 format (e.g., "USD", "EUR"). */
    private String currency;

    /**
     * Current balance of the account.
     *
     * <p>Uses {@link BigDecimal} to avoid floating-point precision issues in financial
     * calculations.
     */
    private BigDecimal balance;

    /** Flag indicating whether the account is currently active. */
    private boolean isActive;

    /**
     * Name of the financial institution associated with this account.
     *
     * <p>May be {@code null} when no institution is linked.
     */
    private String institutionName;
}
