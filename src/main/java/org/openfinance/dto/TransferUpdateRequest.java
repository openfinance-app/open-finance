package org.openfinance.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.openfinance.validation.ValidCurrency;

/**
 * Data Transfer Object for updating an existing transfer transaction.
 *
 * <p>This DTO is used for PUT operations to update both sides of a transfer atomically. A transfer
 * consists of two linked transactions (source EXPENSE and destination INCOME) that share a common
 * transferId. This update ensures both transactions remain synchronized.
 *
 * <p>Requirement REQ-2.4.1.4: Transfer transaction updates
 *
 * @see org.openfinance.entity.Transaction
 * @see TransactionRequest
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransferUpdateRequest {

    /**
     * ID of the source account (money leaving this account).
     *
     * <p>This replaces the original source account. The old source account balance will be reversed
     * and the new source account will be debited.
     */
    @NotNull(message = "{transfer.from.account.required}")
    private Long fromAccountId;

    /**
     * ID of the destination account (money entering this account).
     *
     * <p>This replaces the original destination account. The old destination account balance will
     * be reversed and the new destination account will be credited.
     */
    @NotNull(message = "{transfer.to.account.required}")
    private Long toAccountId;

    /**
     * Updated amount for the transfer.
     *
     * <p>Must be positive. This amount will be subtracted from the source account and added to the
     * destination account.
     */
    @NotNull(message = "{transfer.amount.required}")
    @DecimalMin(value = "0.01", message = "{transfer.amount.min}")
    @Digits(integer = 15, fraction = 4, message = "{transfer.amount.digits}")
    private BigDecimal amount;

    /**
     * Currency code in ISO 4217 format (e.g., "USD", "EUR", "GBP").
     *
     * <p>Should match the source account's currency.
     *
     * <p>Requirement REQ-2.8: Multi-currency support
     */
    @NotBlank(message = "{account.currency.required}")
    @ValidCurrency
    private String currency;

    /**
     * Updated date for both transactions in the transfer.
     *
     * <p>Both sides of the transfer will have the same date.
     */
    @NotNull(message = "{transfer.date.required}")
    private LocalDate date;

    /**
     * Updated description for the transfer (optional, encrypted).
     *
     * <p>Examples: "Transfer to savings", "Monthly investment contribution"
     */
    @Size(max = 255, message = "{transfer.description.max}")
    private String description;

    /**
     * Updated notes about the transfer (optional, encrypted).
     *
     * <p>Additional details about the transfer purpose or context.
     */
    @Size(max = 1000, message = "{transfer.notes.max}")
    private String notes;

    /**
     * Updated payee name (optional).
     *
     * <p>For transfers, this might be the destination account holder or purpose.
     */
    @Size(max = 100, message = "{transfer.payee.max}")
    private String payee;

    /**
     * Updated tags for organization (optional).
     *
     * <p>Examples: "monthly-transfer", "investment", "savings"
     */
    @Size(max = 500, message = "{transfer.tags.max}")
    private String tags;

    /**
     * Flag indicating whether the transfer transactions have been reconciled.
     *
     * <p>Defaults to false. Both sides of the transfer will have the same reconciliation status.
     */
    @Builder.Default private Boolean isReconciled = false;
}
