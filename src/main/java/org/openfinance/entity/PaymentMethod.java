package org.openfinance.entity;

/**
 * Enumeration representing payment methods used for transactions.
 *
 * <p>This enum categorizes the different ways a transaction can be paid for or received:
 *
 * <ul>
 *   <li><strong>CASH</strong>: Physical currency
 *   <li><strong>CHEQUE</strong>: Physical cheque
 *   <li><strong>CREDIT_CARD</strong>: Credit card payment
 *   <li><strong>DEBIT_CARD</strong>: Debit card payment
 *   <li><strong>BANK_TRANSFER</strong>: Wire or ACH transfer
 *   <li><strong>DEPOSIT</strong>: Direct deposit
 *   <li><strong>STANDING_ORDER</strong>: Recurring automatic payment
 *   <li><strong>DIRECT_DEBIT</strong>: Authorized automatic debit
 *   <li><strong>ONLINE</strong>: Online payment (PayPal, etc.)
 *   <li><strong>OTHER</strong>: Other payment methods
 * </ul>
 *
 * @see Transaction
 * @since 1.0
 */
public enum PaymentMethod {
    /** Physical currency or cash payment. */
    CASH,

    /** Physical cheque payment. */
    CHEQUE,

    /** Credit card payment. */
    CREDIT_CARD,

    /** Debit card payment. */
    DEBIT_CARD,

    /** Bank wire transfer or ACH payment. */
    BANK_TRANSFER,

    /** Direct deposit (e.g., salary). */
    DEPOSIT,

    /** Standing order (recurring scheduled payment). */
    STANDING_ORDER,

    /** Direct debit (authorized automatic debit). */
    DIRECT_DEBIT,

    /** Online payment through third-party services (PayPal, Stripe, etc.). */
    ONLINE,

    /** Other payment methods not listed above. */
    OTHER
}
