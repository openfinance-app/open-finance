package org.openfinance.entity;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

/**
 * Records a point-in-time snapshot of a real estate property's estimated value.
 *
 * <p>A row is inserted whenever {@code RealEstateProperty.currentValue} is changed (create or
 * update). The backfill algorithm uses these rows to reconstruct the property's value at any past
 * date: it picks the latest entry whose {@code effectiveDate} is on or before the target snapshot
 * date.
 *
 * <p>The {@code value} column stores the encrypted value in the same AES-256-GCM format used by
 * {@code RealEstateProperty.currentValue}.
 */
@Entity
@Table(
        name = "real_estate_value_history",
        indexes = {
            @Index(
                    name = "idx_re_value_history_property_date",
                    columnList = "property_id, effective_date")
        })
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class RealEstateValueHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "property_id", nullable = false)
    private Long propertyId;

    /** Reference to the property entity (lazy-loaded). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "property_id", insertable = false, updatable = false)
    private RealEstateProperty property;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    /** Date from which this value is considered effective (usually today). */
    @Column(name = "effective_date", nullable = false)
    private LocalDate effectiveDate;

    /** Encrypted property value (AES-256-GCM, same format as RealEstateProperty.currentValue). */
    @Column(name = "recorded_value", nullable = false, length = 500)
    private String recordedValue;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency;

    /** FK to the currencies table for referential integrity. */
    @Column(name = "currency_id")
    private Long currencyId;

    /** Reference to the currency entity (lazy-loaded). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "currency_id", insertable = false, updatable = false)
    private Currency currencyEntity;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
