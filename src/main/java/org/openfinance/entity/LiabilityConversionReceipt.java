package org.openfinance.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Receipts survive undo so a delayed retry cannot reconvert an undone account. */
@Entity
@Table(
        name = "liability_conversion_receipts",
        uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "operation_id"}))
@Getter
@Setter
@NoArgsConstructor
public class LiabilityConversionReceipt {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "operation_id", nullable = false, length = 36)
    private String operationId;

    @Column(name = "request_hash", nullable = false, length = 64)
    private String requestHash;

    @Column(name = "account_id", nullable = false)
    private Long accountId;

    @Column(name = "liability_id", nullable = false)
    private Long liabilityId;
}
