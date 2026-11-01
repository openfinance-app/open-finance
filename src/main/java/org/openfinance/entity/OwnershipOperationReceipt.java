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

/** Retained across undo so retrying an old HTTP request cannot repeat a cash movement. */
@Entity
@Table(
        name = "ownership_operation_receipts",
        uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "operation_id"}))
@Getter
@Setter
@NoArgsConstructor
public class OwnershipOperationReceipt {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "operation_id", nullable = false, length = 36)
    private String operationId;

    @Column(name = "request_hash", nullable = false, length = 64)
    private String requestHash;

    @Column(name = "result_id", nullable = false)
    private Long resultId;
}
