package org.openfinance.entity;

import static org.assertj.core.api.Assertions.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for Asset entity.
 *
 * <p>Tests physical asset-specific methods including depreciation calculation, condition
 * adjustment, warranty validation, and physical asset identification.
 *
 * <p>Requirements: Physical Asset Tracking - Asset depreciation, condition assessment, and warranty
 * tracking for physical assets.
 */
@DisplayName("Asset Entity Tests - Physical Asset Methods")
class AssetTest {

    // ========== DEPRECIATED VALUE TESTS ==========

    @Nested
    @DisplayName("getDepreciatedValue() Tests")
    class DepreciatedValueTests {

        @Test
        @DisplayName("Should calculate depreciated value using straight-line depreciation")
        void shouldCalculateDepreciatedValueCorrectly() {
            // Arrange - Vehicle purchased 2 years ago for $30,000 with 10 year useful life
            LocalDate purchaseDate = LocalDate.now().minusYears(2);
            Asset asset =
                    Asset.builder()
                            .type(AssetType.VEHICLE)
                            .quantity(BigDecimal.ONE)
                            .purchasePrice(new BigDecimal("30000.00"))
                            .currentPrice(new BigDecimal("25000.00"))
                            .purchaseDate(purchaseDate)
                            .usefulLifeYears(10)
                            .build();

            // Act
            BigDecimal depreciatedValue = asset.getDepreciatedValue();

            // Assert
            // Annual depreciation = 30000 / 10 = 3000
            // Total depreciation after ~2 years = ~6000
            // Depreciated value = ~24000 (allow small variance due to days calculation)
            BigDecimal minExpected = new BigDecimal("23990.00");
            BigDecimal maxExpected = new BigDecimal("24010.00");
            assertThat(depreciatedValue).isBetween(minExpected, maxExpected);
        }

        @Test
        @DisplayName("Should return salvage value (10%) when asset is fully depreciated")
        void shouldReturnSalvageValueWhenFullyDepreciated() {
            // Arrange - Electronics purchased 10 years ago with 5 year useful life
            LocalDate purchaseDate = LocalDate.now().minusYears(10);
            Asset asset =
                    Asset.builder()
                            .type(AssetType.ELECTRONICS)
                            .quantity(BigDecimal.ONE)
                            .purchasePrice(new BigDecimal("1000.00"))
                            .currentPrice(new BigDecimal("100.00"))
                            .purchaseDate(purchaseDate)
                            .usefulLifeYears(5)
                            .build();

            // Act
            BigDecimal depreciatedValue = asset.getDepreciatedValue();

            // Assert - Should return 10% salvage value
            BigDecimal expectedSalvageValue = new BigDecimal("100.00"); // 10% of 1000
            assertThat(depreciatedValue.setScale(2, RoundingMode.HALF_UP))
                    .isEqualByComparingTo(expectedSalvageValue);
        }

        @Test
        @DisplayName("Should never go below 10% salvage value")
        void shouldNeverGoBelowSalvageValue() {
            // Arrange - Furniture purchased 20 years ago with 7 year useful life
            LocalDate purchaseDate = LocalDate.now().minusYears(20);
            Asset asset =
                    Asset.builder()
                            .type(AssetType.FURNITURE)
                            .quantity(BigDecimal.ONE)
                            .purchasePrice(new BigDecimal("5000.00"))
                            .currentPrice(new BigDecimal("500.00"))
                            .purchaseDate(purchaseDate)
                            .usefulLifeYears(7)
                            .build();

            // Act
            BigDecimal depreciatedValue = asset.getDepreciatedValue();

            // Assert - Should still be at 10% salvage value
            BigDecimal expectedSalvageValue = new BigDecimal("500.00"); // 10% of 5000
            assertThat(depreciatedValue.setScale(2, RoundingMode.HALF_UP))
                    .isEqualByComparingTo(expectedSalvageValue);
        }

        @Test
        @DisplayName("Should return null for non-physical assets")
        void shouldReturnCurrentPriceForFinancialAssets() {
            // Arrange - Stock (non-physical asset)
            Asset asset =
                    Asset.builder()
                            .type(AssetType.STOCK)
                            .quantity(new BigDecimal("10.0"))
                            .purchasePrice(new BigDecimal("100.00"))
                            .currentPrice(new BigDecimal("150.00"))
                            .purchaseDate(LocalDate.now().minusYears(1))
                            .usefulLifeYears(null)
                            .build();

            // Act
            BigDecimal depreciatedValue = asset.getDepreciatedValue();

            // Assert - Non-physical assets do not depreciate; method returns null
            assertThat(depreciatedValue).isNull();
        }

        @Test
        @DisplayName("Should return null when usefulLifeYears is null")
        void shouldReturnCurrentPriceWhenUsefulLifeYearsIsNull() {
            // Arrange
            Asset asset =
                    Asset.builder()
                            .type(AssetType.VEHICLE)
                            .quantity(BigDecimal.ONE)
                            .purchasePrice(new BigDecimal("25000.00"))
                            .currentPrice(new BigDecimal("20000.00"))
                            .purchaseDate(LocalDate.now().minusYears(1))
                            .usefulLifeYears(null)
                            .build();

            // Act
            BigDecimal depreciatedValue = asset.getDepreciatedValue();

            // Assert - Cannot compute depreciation without useful life; method returns null
            assertThat(depreciatedValue).isNull();
        }

        @Test
        @DisplayName("Should return null when usefulLifeYears is zero")
        void shouldReturnCurrentPriceWhenUsefulLifeYearsIsZero() {
            // Arrange
            Asset asset =
                    Asset.builder()
                            .type(AssetType.ELECTRONICS)
                            .quantity(BigDecimal.ONE)
                            .purchasePrice(new BigDecimal("1500.00"))
                            .currentPrice(new BigDecimal("1200.00"))
                            .purchaseDate(LocalDate.now().minusMonths(6))
                            .usefulLifeYears(0)
                            .build();

            // Act
            BigDecimal depreciatedValue = asset.getDepreciatedValue();

            // Assert - Zero useful life is invalid; method returns null
            assertThat(depreciatedValue).isNull();
        }

        @Test
        @DisplayName("Should return null when usefulLifeYears is negative")
        void shouldReturnCurrentPriceWhenUsefulLifeYearsIsNegative() {
            // Arrange
            Asset asset =
                    Asset.builder()
                            .type(AssetType.FURNITURE)
                            .quantity(BigDecimal.ONE)
                            .purchasePrice(new BigDecimal("3000.00"))
                            .currentPrice(new BigDecimal("2500.00"))
                            .purchaseDate(LocalDate.now().minusMonths(3))
                            .usefulLifeYears(-5)
                            .build();

            // Act
            BigDecimal depreciatedValue = asset.getDepreciatedValue();

            // Assert - Negative useful life is invalid; method returns null
            assertThat(depreciatedValue).isNull();
        }

        @Test
        @DisplayName("Should handle fractional years correctly")
        void shouldHandleFractionalYearsCorrectly() {
            // Arrange - Asset purchased 6 months ago with 10 year useful life
            LocalDate purchaseDate = LocalDate.now().minusMonths(6);
            Asset asset =
                    Asset.builder()
                            .type(AssetType.VEHICLE)
                            .quantity(BigDecimal.ONE)
                            .purchasePrice(
                                    new BigDecimal("36500.00")) // 365 * 100 for easy calculation
                            .currentPrice(new BigDecimal("35000.00"))
                            .purchaseDate(purchaseDate)
                            .usefulLifeYears(10)
                            .build();

            // Act
            BigDecimal depreciatedValue = asset.getDepreciatedValue();

            // Assert
            // Annual depreciation = 36500 / 10 = 3650
            // Depreciation for ~0.5 years = ~1825
            // Depreciated value should be around 34675
            BigDecimal minExpected = new BigDecimal("34500.00");
            BigDecimal maxExpected = new BigDecimal("35000.00");
            assertThat(depreciatedValue).isBetween(minExpected, maxExpected);
        }

        @Test
        @DisplayName("Should handle assets purchased today")
        void shouldHandleAssetsPurchasedToday() {
            // Arrange
            Asset asset =
                    Asset.builder()
                            .type(AssetType.ELECTRONICS)
                            .quantity(BigDecimal.ONE)
                            .purchasePrice(new BigDecimal("2000.00"))
                            .currentPrice(new BigDecimal("2000.00"))
                            .purchaseDate(LocalDate.now())
                            .usefulLifeYears(5)
                            .build();

            // Act
            BigDecimal depreciatedValue = asset.getDepreciatedValue();

            // Assert - Minimal depreciation (0 days owned)
            BigDecimal expected = new BigDecimal("2000.00");
            assertThat(depreciatedValue.setScale(2, RoundingMode.HALF_UP))
                    .isEqualByComparingTo(expected);
        }

        @Test
        @DisplayName("Should calculate depreciation for multiple quantity assets")
        void shouldCalculateDepreciationForMultipleQuantityAssets() {
            // Arrange - 5 units purchased 1 year ago with 10 year useful life
            LocalDate purchaseDate = LocalDate.now().minusYears(1);
            Asset asset =
                    Asset.builder()
                            .type(AssetType.ELECTRONICS)
                            .quantity(new BigDecimal("5.0")) // 5 units
                            .purchasePrice(new BigDecimal("1000.00")) // per unit
                            .currentPrice(new BigDecimal("900.00"))
                            .purchaseDate(purchaseDate)
                            .usefulLifeYears(10)
                            .build();

            // Act
            BigDecimal depreciatedValue = asset.getDepreciatedValue();

            // Assert
            // Total cost = 5 * 1000 = 5000
            // Annual depreciation = 5000 / 10 = 500
            // Total depreciation after 1 year = 500
            // Depreciated value = 5000 - 500 = 4500
            BigDecimal expected = new BigDecimal("4500.00");
            assertThat(depreciatedValue.setScale(2, RoundingMode.HALF_UP))
                    .isEqualByComparingTo(expected);
        }
    }

    // ========== CONDITION ADJUSTED VALUE TESTS ==========

    @Nested
    @DisplayName("getConditionAdjustedValue() Tests")
    class ConditionAdjustedValueTests {

        @Test
        @DisplayName("Should apply NEW condition factor (0.95) to depreciated value")
        void shouldApplyNewConditionFactor() {
            // Arrange
            LocalDate purchaseDate = LocalDate.now().minusYears(1);
            Asset asset =
                    Asset.builder()
                            .type(AssetType.VEHICLE)
                            .quantity(BigDecimal.ONE)
                            .purchasePrice(new BigDecimal("30000.00"))
                            .currentPrice(new BigDecimal("28000.00"))
                            .purchaseDate(purchaseDate)
                            .usefulLifeYears(10)
                            .condition(AssetCondition.NEW)
                            .build();

            // Act
            BigDecimal conditionAdjustedValue = asset.getConditionAdjustedValue();

            // Assert
            // Depreciated value after 1 year = 30000 - (30000/10*1) = 27000
            // Condition adjusted = 27000 * 0.95 = 25650
            BigDecimal expected = new BigDecimal("25650.00");
            assertThat(conditionAdjustedValue.setScale(2, RoundingMode.HALF_UP))
                    .isEqualByComparingTo(expected);
        }

        @Test
        @DisplayName("Should apply EXCELLENT condition factor (0.82) to depreciated value")
        void shouldApplyExcellentConditionFactor() {
            // Arrange
            LocalDate purchaseDate = LocalDate.now().minusYears(2);
            Asset asset =
                    Asset.builder()
                            .type(AssetType.ELECTRONICS)
                            .quantity(BigDecimal.ONE)
                            .purchasePrice(new BigDecimal("1000.00"))
                            .currentPrice(new BigDecimal("800.00"))
                            .purchaseDate(purchaseDate)
                            .usefulLifeYears(5)
                            .condition(AssetCondition.EXCELLENT)
                            .build();

            // Act
            BigDecimal conditionAdjustedValue = asset.getConditionAdjustedValue();

            // Assert
            // Depreciated value after ~2 years = ~600
            // Condition adjusted = ~600 * 0.82 = ~492 (allow variance due to days)
            BigDecimal minExpected = new BigDecimal("490.00");
            BigDecimal maxExpected = new BigDecimal("493.00");
            assertThat(conditionAdjustedValue).isBetween(minExpected, maxExpected);
        }

        @Test
        @DisplayName("Should apply GOOD condition factor (0.62) to depreciated value")
        void shouldApplyGoodConditionFactor() {
            // Arrange
            LocalDate purchaseDate = LocalDate.now().minusYears(3);
            Asset asset =
                    Asset.builder()
                            .type(AssetType.FURNITURE)
                            .quantity(BigDecimal.ONE)
                            .purchasePrice(new BigDecimal("2000.00"))
                            .currentPrice(new BigDecimal("1500.00"))
                            .purchaseDate(purchaseDate)
                            .usefulLifeYears(10)
                            .condition(AssetCondition.GOOD)
                            .build();

            // Act
            BigDecimal conditionAdjustedValue = asset.getConditionAdjustedValue();

            // Assert
            // Depreciated value after ~3 years = ~1400
            // Condition adjusted = ~1400 * 0.62 = ~868 (allow variance due to days)
            BigDecimal minExpected = new BigDecimal("865.00");
            BigDecimal maxExpected = new BigDecimal("870.00");
            assertThat(conditionAdjustedValue).isBetween(minExpected, maxExpected);
        }

        @Test
        @DisplayName("Should apply FAIR condition factor (0.37) to depreciated value")
        void shouldApplyFairConditionFactor() {
            // Arrange
            LocalDate purchaseDate = LocalDate.now().minusYears(1);
            Asset asset =
                    Asset.builder()
                            .type(AssetType.VEHICLE)
                            .quantity(BigDecimal.ONE)
                            .purchasePrice(new BigDecimal("20000.00"))
                            .currentPrice(new BigDecimal("18000.00"))
                            .purchaseDate(purchaseDate)
                            .usefulLifeYears(15)
                            .condition(AssetCondition.FAIR)
                            .build();

            // Act
            BigDecimal conditionAdjustedValue = asset.getConditionAdjustedValue();

            // Assert
            // Depreciated value after 1 year = 20000 - (20000/15*1) ≈ 18666.67
            // Condition adjusted = 18666.67 * 0.37 ≈ 6906.67
            BigDecimal minExpected = new BigDecimal("6900.00");
            BigDecimal maxExpected = new BigDecimal("6910.00");
            assertThat(conditionAdjustedValue).isBetween(minExpected, maxExpected);
        }

        @Test
        @DisplayName("Should apply POOR condition factor (0.12) to depreciated value")
        void shouldApplyPoorConditionFactor() {
            // Arrange
            LocalDate purchaseDate = LocalDate.now().minusYears(5);
            Asset asset =
                    Asset.builder()
                            .type(AssetType.ELECTRONICS)
                            .quantity(BigDecimal.ONE)
                            .purchasePrice(new BigDecimal("1500.00"))
                            .currentPrice(new BigDecimal("500.00"))
                            .purchaseDate(purchaseDate)
                            .usefulLifeYears(5)
                            .condition(AssetCondition.POOR)
                            .build();

            // Act
            BigDecimal conditionAdjustedValue = asset.getConditionAdjustedValue();

            // Assert
            // Fully depreciated: salvage value = 1500 * 0.10 = 150
            // Condition adjusted = 150 * 0.12 = 18
            BigDecimal expected = new BigDecimal("18.00");
            assertThat(conditionAdjustedValue.setScale(2, RoundingMode.HALF_UP))
                    .isEqualByComparingTo(expected);
        }

        @Test
        @DisplayName("Should return depreciated value when condition is null")
        void shouldReturnDepreciatedValueWhenConditionIsNull() {
            // Arrange
            LocalDate purchaseDate = LocalDate.now().minusYears(1);
            Asset asset =
                    Asset.builder()
                            .type(AssetType.VEHICLE)
                            .quantity(BigDecimal.ONE)
                            .purchasePrice(new BigDecimal("25000.00"))
                            .currentPrice(new BigDecimal("23000.00"))
                            .purchaseDate(purchaseDate)
                            .usefulLifeYears(10)
                            .condition(null) // No condition set
                            .build();

            // Act
            BigDecimal conditionAdjustedValue = asset.getConditionAdjustedValue();
            BigDecimal depreciatedValue = asset.getDepreciatedValue();

            // Assert - Should return same as depreciated value
            assertThat(conditionAdjustedValue).isEqualByComparingTo(depreciatedValue);
        }
    }

    // ========== IS PHYSICAL TESTS ==========

    @Nested
    @DisplayName("isPhysical() Tests")
    class IsPhysicalTests {

        @Test
        @DisplayName("Should return true for VEHICLE type")
        void shouldReturnTrueForVehicle() {
            Asset asset = Asset.builder().type(AssetType.VEHICLE).build();
            assertThat(asset.isPhysical()).isTrue();
        }

        @Test
        @DisplayName("Should return true for JEWELRY type")
        void shouldReturnTrueForJewelry() {
            Asset asset = Asset.builder().type(AssetType.JEWELRY).build();
            assertThat(asset.isPhysical()).isTrue();
        }

        @Test
        @DisplayName("Should return true for COLLECTIBLE type")
        void shouldReturnTrueForCollectible() {
            Asset asset = Asset.builder().type(AssetType.COLLECTIBLE).build();
            assertThat(asset.isPhysical()).isTrue();
        }

        @Test
        @DisplayName("Should return true for ELECTRONICS type")
        void shouldReturnTrueForElectronics() {
            Asset asset = Asset.builder().type(AssetType.ELECTRONICS).build();
            assertThat(asset.isPhysical()).isTrue();
        }

        @Test
        @DisplayName("Should return true for FURNITURE type")
        void shouldReturnTrueForFurniture() {
            Asset asset = Asset.builder().type(AssetType.FURNITURE).build();
            assertThat(asset.isPhysical()).isTrue();
        }

        @Test
        @DisplayName("Should return false for STOCK type")
        void shouldReturnFalseForStock() {
            Asset asset = Asset.builder().type(AssetType.STOCK).build();
            assertThat(asset.isPhysical()).isFalse();
        }

        @Test
        @DisplayName("Should return false for ETF type")
        void shouldReturnFalseForEtf() {
            Asset asset = Asset.builder().type(AssetType.ETF).build();
            assertThat(asset.isPhysical()).isFalse();
        }

        @Test
        @DisplayName("Should return false for CRYPTO type")
        void shouldReturnFalseForCrypto() {
            Asset asset = Asset.builder().type(AssetType.CRYPTO).build();
            assertThat(asset.isPhysical()).isFalse();
        }

        @Test
        @DisplayName("Should return false for BOND type")
        void shouldReturnFalseForBond() {
            Asset asset = Asset.builder().type(AssetType.BOND).build();
            assertThat(asset.isPhysical()).isFalse();
        }

        @Test
        @DisplayName("Should return false when type is null")
        void shouldReturnFalseWhenTypeIsNull() {
            Asset asset = Asset.builder().type(null).build();
            assertThat(asset.isPhysical()).isFalse();
        }
    }

    // ========== IS WARRANTY VALID TESTS ==========

    @Nested
    @DisplayName("isWarrantyValid() Tests")
    class IsWarrantyValidTests {

        @Test
        @DisplayName("Should return true when warranty expiration is in the future")
        void shouldReturnTrueWhenWarrantyExpirationIsFuture() {
            LocalDate futureDate = LocalDate.now().plusYears(1);
            Asset asset =
                    Asset.builder()
                            .type(AssetType.ELECTRONICS)
                            .warrantyExpiration(futureDate)
                            .build();

            assertThat(asset.isWarrantyValid()).isTrue();
        }

        @Test
        @DisplayName("Should return false when warranty expiration is in the past")
        void shouldReturnFalseWhenWarrantyExpirationIsPast() {
            LocalDate pastDate = LocalDate.now().minusYears(1);
            Asset asset =
                    Asset.builder().type(AssetType.VEHICLE).warrantyExpiration(pastDate).build();

            assertThat(asset.isWarrantyValid()).isFalse();
        }

        @Test
        @DisplayName("Should return false when warranty expiration is today")
        void shouldReturnFalseWhenWarrantyExpirationIsToday() {
            LocalDate today = LocalDate.now();
            Asset asset =
                    Asset.builder().type(AssetType.FURNITURE).warrantyExpiration(today).build();

            // Warranty expired as of today (not after today)
            assertThat(asset.isWarrantyValid()).isFalse();
        }

        @Test
        @DisplayName("Should return false when warranty expiration is null")
        void shouldReturnFalseWhenWarrantyExpirationIsNull() {
            Asset asset =
                    Asset.builder().type(AssetType.ELECTRONICS).warrantyExpiration(null).build();

            assertThat(asset.isWarrantyValid()).isFalse();
        }

        @Test
        @DisplayName("Should return true when warranty expires tomorrow")
        void shouldReturnTrueWhenWarrantyExpiresTomorrow() {
            LocalDate tomorrow = LocalDate.now().plusDays(1);
            Asset asset =
                    Asset.builder().type(AssetType.VEHICLE).warrantyExpiration(tomorrow).build();

            assertThat(asset.isWarrantyValid()).isTrue();
        }
    }

    // ========== BUILDER PATTERN TESTS ==========

    @Nested
    @DisplayName("Builder Pattern Tests")
    class BuilderPatternTests {

        @Test
        @DisplayName("Should build asset with all physical fields")
        void shouldBuildAssetWithAllPhysicalFields() {
            // Arrange & Act
            Asset asset =
                    Asset.builder()
                            .id(1L)
                            .userId(100L)
                            .name("Tesla Model 3")
                            .type(AssetType.VEHICLE)
                            .quantity(BigDecimal.ONE)
                            .purchasePrice(new BigDecimal("45000.00"))
                            .currentPrice(new BigDecimal("40000.00"))
                            .currency("USD")
                            .purchaseDate(LocalDate.now().minusYears(1))
                            .serialNumber("5YJ3E1EA4JF000001")
                            .brand("Tesla")
                            .model("Model 3")
                            .condition(AssetCondition.EXCELLENT)
                            .warrantyExpiration(LocalDate.now().plusYears(2))
                            .usefulLifeYears(15)
                            .photoPath("assets/photos/tesla_model3.jpg")
                            .build();

            // Assert
            assertThat(asset.getId()).isEqualTo(1L);
            assertThat(asset.getUserId()).isEqualTo(100L);
            assertThat(asset.getName()).isEqualTo("Tesla Model 3");
            assertThat(asset.getType()).isEqualTo(AssetType.VEHICLE);
            assertThat(asset.getSerialNumber()).isEqualTo("5YJ3E1EA4JF000001");
            assertThat(asset.getBrand()).isEqualTo("Tesla");
            assertThat(asset.getModel()).isEqualTo("Model 3");
            assertThat(asset.getCondition()).isEqualTo(AssetCondition.EXCELLENT);
            assertThat(asset.getWarrantyExpiration()).isEqualTo(LocalDate.now().plusYears(2));
            assertThat(asset.getUsefulLifeYears()).isEqualTo(15);
            assertThat(asset.getPhotoPath()).isEqualTo("assets/photos/tesla_model3.jpg");
        }

        @Test
        @DisplayName("Should build financial asset without physical fields")
        void shouldBuildFinancialAssetWithoutPhysicalFields() {
            // Arrange & Act
            Asset asset =
                    Asset.builder()
                            .id(2L)
                            .userId(100L)
                            .name("Apple Inc.")
                            .type(AssetType.STOCK)
                            .symbol("AAPL")
                            .quantity(new BigDecimal("10.0"))
                            .purchasePrice(new BigDecimal("150.00"))
                            .currentPrice(new BigDecimal("175.00"))
                            .currency("USD")
                            .purchaseDate(LocalDate.now().minusMonths(6))
                            .build();

            // Assert
            assertThat(asset.getId()).isEqualTo(2L);
            assertThat(asset.getName()).isEqualTo("Apple Inc.");
            assertThat(asset.getType()).isEqualTo(AssetType.STOCK);
            assertThat(asset.getSymbol()).isEqualTo("AAPL");
            assertThat(asset.getSerialNumber()).isNull();
            assertThat(asset.getBrand()).isNull();
            assertThat(asset.getModel()).isNull();
            assertThat(asset.getCondition()).isNull();
            assertThat(asset.getWarrantyExpiration()).isNull();
            assertThat(asset.getUsefulLifeYears()).isNull();
            assertThat(asset.getPhotoPath()).isNull();
        }
    }
}
