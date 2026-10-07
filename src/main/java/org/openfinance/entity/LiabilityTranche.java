package org.openfinance.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.math.BigDecimal;
import java.time.LocalDate;
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
import org.openfinance.converter.EncryptedBigDecimalConverter;
import org.openfinance.converter.EncryptedStringConverter;

/**
 * Entity representing a single tranche (planned drawdown) of a liability such as a construction
 * loan or a mortgage released in stages.
 *
 * <p>Monetary amounts and notes use the owner's encryption key.
 */
@Entity
@Table(
        name = "liability_tranches",
        uniqueConstraints =
                @UniqueConstraint(
                        name = "uq_liability_tranche_no",
                        columnNames = {"liability_id", "tranche_no"}),
        indexes = {
            @Index(name = "idx_liability_tranche_user_id", columnList = "user_id"),
            @Index(name = "idx_liability_tranche_liability_id", columnList = "liability_id"),
            @Index(name = "idx_liability_tranche_status", columnList = "status")
        })
@Getter
@Setter
@ToString(onlyExplicitlyIncluded = true)
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class LiabilityTranche {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @ToString.Include
    @EqualsAndHashCode.Include
    private Long id;

    @NotNull(message = "{liabilityTranche.userId.notnull}")
    @Column(name = "user_id", nullable = false)
    private Long userId;

    @NotNull(message = "{liabilityTranche.liabilityId.notnull}")
    @Column(name = "liability_id", nullable = false)
    private Long liabilityId;

    @NotNull(message = "{liabilityTranche.trancheNo.notnull}")
    @Column(name = "tranche_no", nullable = false)
    private Integer trancheNo;

    @NotNull(message = "{liabilityTranche.plannedAmount.notnull}")
    @Convert(converter = EncryptedBigDecimalConverter.class)
    @Column(name = "planned_amount", columnDefinition = "TEXT", nullable = false)
    private BigDecimal plannedAmount;

    @Convert(converter = EncryptedBigDecimalConverter.class)
    @Column(name = "drawn_amount", columnDefinition = "TEXT")
    private BigDecimal drawnAmount;

    @Column(name = "planned_date")
    private LocalDate plannedDate;

    @Column(name = "drawn_date")
    private LocalDate drawnDate;

    @Column(name = "direct_disbursement", nullable = false)
    private boolean directDisbursement;

    @Column(name = "reversed_date")
    private LocalDate reversedDate;

    @Convert(converter = EncryptedBigDecimalConverter.class)
    @Column(name = "fee", columnDefinition = "TEXT")
    private BigDecimal fee;

    @Builder.Default
    @Column(name = "interest_only", nullable = false)
    private boolean interestOnly = false;

    @Column(name = "interest_only_until")
    private LocalDate interestOnlyUntil;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private TrancheStatus status = TrancheStatus.PLANNED;

    @Column(name = "real_estate_id")
    private Long realEstateId;

    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    @NotNull(message = "{liabilityTranche.currency.notnull}")
    @Pattern(regexp = "^[A-Z]{3,10}$", message = "{liabilityTranche.currency.pattern}")
    @Column(name = "currency", nullable = false, length = 10)
    private String currency;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
