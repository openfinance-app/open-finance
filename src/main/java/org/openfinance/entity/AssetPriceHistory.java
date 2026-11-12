package org.openfinance.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.openfinance.converter.EncryptedBigDecimalConverter;

/** A dated, per-unit valuation, in its recorded quote currency. */
@Entity
@Table(
        name = "asset_price_history",
        uniqueConstraints = @UniqueConstraint(columnNames = {"asset_id", "price_date"}))
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AssetPriceHistory {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "asset_id", nullable = false)
    private Long assetId;

    @Column(name = "price_date", nullable = false, length = 10)
    private LocalDate priceDate;

    @Convert(converter = EncryptedBigDecimalConverter.class)
    @Column(nullable = false, length = 512)
    private BigDecimal price;

    @Column(nullable = false, length = 10)
    private String currency;

    @Column(nullable = false, length = 30)
    private String source;
}
