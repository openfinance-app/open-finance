package org.openfinance.mapper;

import static org.assertj.core.api.Assertions.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;
import org.openfinance.dto.AssetRequest;
import org.openfinance.dto.AssetResponse;
import org.openfinance.entity.Asset;
import org.openfinance.entity.AssetCondition;
import org.openfinance.entity.AssetType;

/**
 * Unit tests for AssetMapper.
 *
 * <p>Tests mapping between Asset entity and DTOs (AssetRequest/AssetResponse), including physical
 * asset fields and calculated fields.
 *
 * <p>Requirement REQ-2.6: Asset Management - DTO mapping
 */
@DisplayName("AssetMapper Tests")
class AssetMapperTest {

    private final AssetMapper mapper = Mappers.getMapper(AssetMapper.class);

    // === Financial Asset Mapping Tests ===

    @Nested
    @DisplayName("Financial Asset Mapping")
    class FinancialAssetMapping {

        @Test
        @DisplayName("Should map AssetRequest to Asset entity (create)")
        void shouldMapRequestToEntity() {
            // Given
            AssetRequest request =
                    AssetRequest.builder()
                            .accountId(1L)
                            .name("Apple Inc.")
                            .type(AssetType.STOCK)
                            .symbol("AAPL")
                            .quantity(new BigDecimal("10.0"))
                            .purchasePrice(new BigDecimal("150.00"))
                            .currentPrice(new BigDecimal("175.00"))
                            .currency("USD")
                            .purchaseDate(LocalDate.of(2025, 1, 15))
                            .notes("Tech portfolio")
                            .build();

            // When
            Asset entity = mapper.toEntity(request);

            // Then
            assertThat(entity).isNotNull();
            assertThat(entity.getId()).isNull(); // Not set by mapper
            assertThat(entity.getUserId()).isNull(); // Not set by mapper
            assertThat(entity.getAccountId()).isEqualTo(1L);
            assertThat(entity.getName()).isEqualTo("Apple Inc.");
            assertThat(entity.getType()).isEqualTo(AssetType.STOCK);
            assertThat(entity.getSymbol()).isEqualTo("AAPL");
            assertThat(entity.getQuantity()).isEqualByComparingTo(new BigDecimal("10.0"));
            assertThat(entity.getPurchasePrice()).isEqualByComparingTo(new BigDecimal("150.00"));
            assertThat(entity.getCurrentPrice()).isEqualByComparingTo(new BigDecimal("175.00"));
            assertThat(entity.getCurrency()).isEqualTo("USD");
            assertThat(entity.getPurchaseDate()).isEqualTo(LocalDate.of(2025, 1, 15));
            assertThat(entity.getNotes()).isEqualTo("Tech portfolio");
        }

        @Test
        @DisplayName("Should map Asset entity to AssetResponse (read)")
        void shouldMapEntityToResponse() {
            // Given
            Asset entity =
                    Asset.builder()
                            .id(1L)
                            .userId(1L)
                            .accountId(1L)
                            .name("Apple Inc.")
                            .type(AssetType.STOCK)
                            .symbol("AAPL")
                            .quantity(new BigDecimal("10.0"))
                            .purchasePrice(new BigDecimal("150.00"))
                            .currentPrice(new BigDecimal("175.00"))
                            .currency("USD")
                            .purchaseDate(LocalDate.of(2025, 1, 15))
                            .notes("Tech portfolio")
                            .lastUpdated(LocalDateTime.of(2026, 2, 1, 14, 30))
                            .createdAt(LocalDateTime.of(2026, 2, 1, 14, 30))
                            .build();

            // When
            AssetResponse response = mapper.toResponse(entity);

            // Then
            assertThat(response).isNotNull();
            assertThat(response.getId()).isEqualTo(1L);
            assertThat(response.getUserId()).isEqualTo(1L);
            assertThat(response.getAccountId()).isEqualTo(1L);
            assertThat(response.getName()).isEqualTo("Apple Inc.");
            assertThat(response.getType()).isEqualTo(AssetType.STOCK);
            assertThat(response.getSymbol()).isEqualTo("AAPL");
            assertThat(response.getQuantity()).isEqualByComparingTo(new BigDecimal("10.0"));
            assertThat(response.getPurchasePrice()).isEqualByComparingTo(new BigDecimal("150.00"));
            assertThat(response.getCurrentPrice()).isEqualByComparingTo(new BigDecimal("175.00"));
            assertThat(response.getCurrency()).isEqualTo("USD");
            assertThat(response.getPurchaseDate()).isEqualTo(LocalDate.of(2025, 1, 15));
            assertThat(response.getNotes()).isEqualTo("Tech portfolio");
            assertThat(response.getLastUpdated()).isEqualTo(LocalDateTime.of(2026, 2, 1, 14, 30));
            assertThat(response.getCreatedAt()).isEqualTo(LocalDateTime.of(2026, 2, 1, 14, 30));
        }

        @Test
        @DisplayName("Should populate calculated fields in AssetResponse")
        void shouldPopulateCalculatedFields() {
            // Given
            Asset entity =
                    Asset.builder()
                            .id(1L)
                            .userId(1L)
                            .type(AssetType.STOCK)
                            .symbol("AAPL")
                            .quantity(new BigDecimal("10.0"))
                            .purchasePrice(new BigDecimal("150.00"))
                            .currentPrice(new BigDecimal("175.00"))
                            .currency("USD")
                            .purchaseDate(LocalDate.of(2025, 1, 15))
                            .build();

            // When
            AssetResponse response = mapper.toResponse(entity);

            // Then - calculated fields from Asset entity methods
            assertThat(response.getTotalValue())
                    .isEqualByComparingTo(new BigDecimal("1750.00")); // 10 * 175
            assertThat(response.getTotalCost())
                    .isEqualByComparingTo(new BigDecimal("1500.00")); // 10 * 150
            assertThat(response.getUnrealizedGain())
                    .isEqualByComparingTo(new BigDecimal("250.00")); // 1750 - 1500
            assertThat(response.getGainPercentage())
                    .isEqualByComparingTo(new BigDecimal("0.1667")); // (175-150)/150
            assertThat(response.getHoldingDays()).isGreaterThan(0); // Days since 2025-01-15
        }

        @Test
        @DisplayName("Should update existing entity from AssetRequest")
        void shouldUpdateEntityFromRequest() {
            // Given - existing entity
            Asset existingEntity =
                    Asset.builder()
                            .id(1L)
                            .userId(1L)
                            .accountId(1L)
                            .name("Apple Inc.")
                            .type(AssetType.STOCK)
                            .symbol("AAPL")
                            .quantity(new BigDecimal("10.0"))
                            .purchasePrice(new BigDecimal("150.00"))
                            .currentPrice(new BigDecimal("175.00"))
                            .currency("USD")
                            .purchaseDate(LocalDate.of(2025, 1, 15))
                            .notes("Tech portfolio")
                            .createdAt(LocalDateTime.of(2026, 1, 1, 0, 0))
                            .build();

            // Given - update request
            AssetRequest updateRequest =
                    AssetRequest.builder()
                            .accountId(1L)
                            .name("Apple Inc. (Updated)")
                            .type(AssetType.STOCK)
                            .symbol("AAPL")
                            .quantity(new BigDecimal("15.0")) // Changed
                            .purchasePrice(new BigDecimal("150.00"))
                            .currentPrice(new BigDecimal("180.00")) // Changed
                            .currency("USD")
                            .purchaseDate(LocalDate.of(2025, 1, 15))
                            .notes("Updated notes") // Changed
                            .build();

            // When
            mapper.updateEntityFromRequest(updateRequest, existingEntity);

            // Then
            assertThat(existingEntity.getId()).isEqualTo(1L); // Preserved
            assertThat(existingEntity.getUserId()).isEqualTo(1L); // Preserved
            assertThat(existingEntity.getCreatedAt())
                    .isEqualTo(LocalDateTime.of(2026, 1, 1, 0, 0)); // Preserved
            assertThat(existingEntity.getName()).isEqualTo("Apple Inc. (Updated)"); // Updated
            assertThat(existingEntity.getQuantity())
                    .isEqualByComparingTo(new BigDecimal("15.0")); // Updated
            assertThat(existingEntity.getCurrentPrice())
                    .isEqualByComparingTo(new BigDecimal("180.00")); // Updated
            assertThat(existingEntity.getNotes()).isEqualTo("Updated notes"); // Updated
        }

        @Test
        @DisplayName("Should handle null optional fields in AssetRequest")
        void shouldHandleNullOptionalFields() {
            // Given
            AssetRequest request =
                    AssetRequest.builder()
                            .accountId(null) // Optional
                            .name("Bitcoin")
                            .type(AssetType.CRYPTO)
                            .symbol(null) // Optional
                            .quantity(new BigDecimal("1.0"))
                            .purchasePrice(new BigDecimal("40000.00"))
                            .currentPrice(new BigDecimal("45000.00"))
                            .currency("USD")
                            .purchaseDate(LocalDate.now())
                            .notes(null) // Optional
                            .build();

            // When
            Asset entity = mapper.toEntity(request);

            // Then
            assertThat(entity).isNotNull();
            assertThat(entity.getAccountId()).isNull();
            assertThat(entity.getSymbol()).isNull();
            assertThat(entity.getNotes()).isNull();
            assertThat(entity.getName()).isEqualTo("Bitcoin");
            assertThat(entity.getType()).isEqualTo(AssetType.CRYPTO);
        }
    }

    // === Physical Asset Mapping Tests ===

    @Nested
    @DisplayName("Physical Asset Mapping")
    class PhysicalAssetMapping {

        @Test
        @DisplayName("Should map physical asset fields from AssetRequest to entity")
        void shouldMapPhysicalAssetFieldsToEntity() {
            // Given
            AssetRequest request =
                    AssetRequest.builder()
                            .accountId(null)
                            .name("Tesla Model 3")
                            .type(AssetType.VEHICLE)
                            .serialNumber("5YJ3E1EB9KF123456")
                            .brand("Tesla")
                            .model("Model 3 Long Range")
                            .condition(AssetCondition.EXCELLENT)
                            .warrantyExpiration(LocalDate.of(2028, 6, 1))
                            .usefulLifeYears(15)
                            .photoPath("/photos/tesla-model3.jpg")
                            .quantity(new BigDecimal("1.0"))
                            .purchasePrice(new BigDecimal("50000.00"))
                            .currentPrice(new BigDecimal("45000.00"))
                            .currency("USD")
                            .purchaseDate(LocalDate.of(2023, 6, 1))
                            .notes("Personal vehicle")
                            .build();

            // When
            Asset entity = mapper.toEntity(request);

            // Then - verify physical asset fields
            assertThat(entity.getSerialNumber()).isEqualTo("5YJ3E1EB9KF123456");
            assertThat(entity.getBrand()).isEqualTo("Tesla");
            assertThat(entity.getModel()).isEqualTo("Model 3 Long Range");
            assertThat(entity.getCondition()).isEqualTo(AssetCondition.EXCELLENT);
            assertThat(entity.getWarrantyExpiration()).isEqualTo(LocalDate.of(2028, 6, 1));
            assertThat(entity.getUsefulLifeYears()).isEqualTo(15);
            assertThat(entity.getPhotoPath()).isEqualTo("/photos/tesla-model3.jpg");
        }

        @Test
        @DisplayName("Should map physical asset fields from entity to AssetResponse")
        void shouldMapPhysicalAssetFieldsToResponse() {
            // Given
            Asset entity =
                    Asset.builder()
                            .id(1L)
                            .userId(1L)
                            .name("iPhone 14 Pro")
                            .type(AssetType.ELECTRONICS)
                            .serialNumber("DLXV9ABC1DEF")
                            .brand("Apple")
                            .model("iPhone 14 Pro 256GB")
                            .condition(AssetCondition.GOOD)
                            .warrantyExpiration(LocalDate.of(2024, 9, 1))
                            .usefulLifeYears(5)
                            .photoPath("/photos/iphone14.jpg")
                            .quantity(new BigDecimal("1.0"))
                            .purchasePrice(new BigDecimal("1099.00"))
                            .currentPrice(new BigDecimal("800.00"))
                            .currency("USD")
                            .purchaseDate(LocalDate.of(2023, 9, 1))
                            .build();

            // When
            AssetResponse response = mapper.toResponse(entity);

            // Then - verify physical asset fields
            assertThat(response.getSerialNumber()).isEqualTo("DLXV9ABC1DEF");
            assertThat(response.getBrand()).isEqualTo("Apple");
            assertThat(response.getModel()).isEqualTo("iPhone 14 Pro 256GB");
            assertThat(response.getCondition()).isEqualTo(AssetCondition.GOOD);
            assertThat(response.getWarrantyExpiration()).isEqualTo(LocalDate.of(2024, 9, 1));
            assertThat(response.getUsefulLifeYears()).isEqualTo(5);
            assertThat(response.getPhotoPath()).isEqualTo("/photos/iphone14.jpg");
        }

        @Test
        @DisplayName("Should populate physical asset calculated fields in AssetResponse")
        void shouldPopulatePhysicalAssetCalculatedFields() {
            // Given - Electronics purchased 2 years ago with 5-year useful life
            Asset entity =
                    Asset.builder()
                            .id(1L)
                            .userId(1L)
                            .name("MacBook Pro")
                            .type(AssetType.ELECTRONICS)
                            .condition(AssetCondition.GOOD)
                            .usefulLifeYears(5)
                            .warrantyExpiration(LocalDate.now().plusMonths(6)) // Still valid
                            .quantity(new BigDecimal("1.0"))
                            .purchasePrice(new BigDecimal("2500.00"))
                            .currentPrice(new BigDecimal("2500.00"))
                            .currency("USD")
                            .purchaseDate(LocalDate.now().minusYears(2))
                            .build();

            // When
            AssetResponse response = mapper.toResponse(entity);

            // Then - verify calculated physical asset fields
            assertThat(response.getIsPhysical()).isTrue(); // ELECTRONICS is physical type
            assertThat(response.getIsWarrantyValid()).isTrue(); // Not yet expired
            assertThat(response.getDepreciatedValue()).isNotNull(); // Depreciation calculated
            assertThat(response.getDepreciatedValue())
                    .isLessThan(entity.getCurrentPrice()); // Depreciated
            assertThat(response.getConditionAdjustedValue())
                    .isNotNull(); // Condition adjustment applied
            assertThat(response.getConditionAdjustedValue())
                    .isLessThan(response.getDepreciatedValue()); // Further reduced
        }

        @Test
        @DisplayName("Should handle null physical asset fields")
        void shouldHandleNullPhysicalAssetFields() {
            // Given - physical asset with no optional physical fields
            AssetRequest request =
                    AssetRequest.builder()
                            .name("Gold Coin Collection")
                            .type(AssetType.COLLECTIBLE)
                            .serialNumber(null) // Optional
                            .brand(null) // Optional
                            .model(null) // Optional
                            .condition(null) // Optional
                            .warrantyExpiration(null) // Optional
                            .usefulLifeYears(null) // Optional
                            .photoPath(null) // Optional
                            .quantity(new BigDecimal("10.0"))
                            .purchasePrice(new BigDecimal("1800.00"))
                            .currentPrice(new BigDecimal("2000.00"))
                            .currency("USD")
                            .purchaseDate(LocalDate.now())
                            .build();

            // When
            Asset entity = mapper.toEntity(request);

            // Then
            assertThat(entity).isNotNull();
            assertThat(entity.getSerialNumber()).isNull();
            assertThat(entity.getBrand()).isNull();
            assertThat(entity.getModel()).isNull();
            assertThat(entity.getCondition()).isNull();
            assertThat(entity.getWarrantyExpiration()).isNull();
            assertThat(entity.getUsefulLifeYears()).isNull();
            assertThat(entity.getPhotoPath()).isNull();
        }

        @Test
        @DisplayName("Should update physical asset fields from AssetRequest")
        void shouldUpdatePhysicalAssetFields() {
            // Given - existing entity
            Asset existingEntity =
                    Asset.builder()
                            .id(1L)
                            .userId(1L)
                            .name("Vintage Watch")
                            .type(AssetType.JEWELRY)
                            .serialNumber("ABC123")
                            .brand("Rolex")
                            .model("Submariner")
                            .condition(AssetCondition.GOOD)
                            .warrantyExpiration(LocalDate.of(2025, 12, 31))
                            .usefulLifeYears(50)
                            .photoPath("/photos/old.jpg")
                            .quantity(new BigDecimal("1.0"))
                            .purchasePrice(new BigDecimal("10000.00"))
                            .currentPrice(new BigDecimal("15000.00"))
                            .currency("USD")
                            .purchaseDate(LocalDate.of(2020, 1, 1))
                            .build();

            // Given - update request
            AssetRequest updateRequest =
                    AssetRequest.builder()
                            .name("Vintage Watch")
                            .type(AssetType.JEWELRY)
                            .serialNumber("ABC123")
                            .brand("Rolex")
                            .model("Submariner")
                            .condition(AssetCondition.EXCELLENT) // Upgraded condition
                            .warrantyExpiration(LocalDate.of(2026, 12, 31)) // Extended warranty
                            .usefulLifeYears(50)
                            .photoPath("/photos/new.jpg") // New photo
                            .quantity(new BigDecimal("1.0"))
                            .purchasePrice(new BigDecimal("10000.00"))
                            .currentPrice(new BigDecimal("18000.00")) // Appreciated
                            .currency("USD")
                            .purchaseDate(LocalDate.of(2020, 1, 1))
                            .build();

            // When
            mapper.updateEntityFromRequest(updateRequest, existingEntity);

            // Then - physical fields updated
            assertThat(existingEntity.getCondition()).isEqualTo(AssetCondition.EXCELLENT);
            assertThat(existingEntity.getWarrantyExpiration())
                    .isEqualTo(LocalDate.of(2026, 12, 31));
            assertThat(existingEntity.getPhotoPath()).isEqualTo("/photos/new.jpg");
            assertThat(existingEntity.getCurrentPrice())
                    .isEqualByComparingTo(new BigDecimal("18000.00"));
        }

        @Test
        @DisplayName("Should map all five physical asset types correctly")
        void shouldMapAllPhysicalAssetTypes() {
            // Test all physical asset types
            AssetType[] physicalTypes = {
                AssetType.VEHICLE,
                AssetType.JEWELRY,
                AssetType.COLLECTIBLE,
                AssetType.ELECTRONICS,
                AssetType.FURNITURE
            };

            for (AssetType type : physicalTypes) {
                // Given
                AssetRequest request =
                        AssetRequest.builder()
                                .name("Test " + type.name())
                                .type(type)
                                .serialNumber("SN" + type.name())
                                .brand("Brand")
                                .model("Model")
                                .condition(AssetCondition.GOOD)
                                .quantity(new BigDecimal("1.0"))
                                .purchasePrice(new BigDecimal("1000.00"))
                                .currentPrice(new BigDecimal("900.00"))
                                .currency("USD")
                                .purchaseDate(LocalDate.now())
                                .build();

                // When
                Asset entity = mapper.toEntity(request);

                // Then
                assertThat(entity.getType()).isEqualTo(type);
                assertThat(entity.getSerialNumber()).isEqualTo("SN" + type.name());
            }
        }

        @Test
        @DisplayName("Should map all five asset conditions correctly")
        void shouldMapAllAssetConditions() {
            // Test all asset conditions
            AssetCondition[] conditions = {
                AssetCondition.NEW,
                AssetCondition.EXCELLENT,
                AssetCondition.GOOD,
                AssetCondition.FAIR,
                AssetCondition.POOR
            };

            for (AssetCondition condition : conditions) {
                // Given
                AssetRequest request =
                        AssetRequest.builder()
                                .name("Test Asset")
                                .type(AssetType.ELECTRONICS)
                                .condition(condition)
                                .quantity(new BigDecimal("1.0"))
                                .purchasePrice(new BigDecimal("1000.00"))
                                .currentPrice(new BigDecimal("1000.00"))
                                .currency("USD")
                                .purchaseDate(LocalDate.now())
                                .build();

                // When
                Asset entity = mapper.toEntity(request);
                AssetResponse response = mapper.toResponse(entity);

                // Then
                assertThat(entity.getCondition()).isEqualTo(condition);
                assertThat(response.getCondition()).isEqualTo(condition);
            }
        }

        @Test
        @DisplayName("Should not map physical asset fields for financial assets")
        void shouldNotMapPhysicalFieldsForFinancialAssets() {
            // Given - financial asset (STOCK) with physical fields erroneously provided
            AssetRequest request =
                    AssetRequest.builder()
                            .name("Apple Inc.")
                            .type(AssetType.STOCK)
                            .symbol("AAPL")
                            .serialNumber("THIS_SHOULD_NOT_BE_HERE") // Invalid for stocks
                            .brand("Apple")
                            .model("iPhone")
                            .condition(AssetCondition.NEW)
                            .quantity(new BigDecimal("10.0"))
                            .purchasePrice(new BigDecimal("150.00"))
                            .currentPrice(new BigDecimal("175.00"))
                            .currency("USD")
                            .purchaseDate(LocalDate.now())
                            .build();

            // When
            Asset entity = mapper.toEntity(request);
            AssetResponse response = mapper.toResponse(entity);

            // Then - physical fields are mapped (mapper doesn't validate business logic)
            // but isPhysical should return false
            assertThat(entity.getType()).isEqualTo(AssetType.STOCK);
            assertThat(response.getIsPhysical()).isFalse(); // STOCK is not a physical type
        }
    }

    // === Edge Case Tests ===

    @Nested
    @DisplayName("Edge Cases")
    class EdgeCases {

        @Test
        @DisplayName("Should handle zero quantity")
        void shouldHandleZeroQuantity() {
            // Given
            AssetRequest request =
                    AssetRequest.builder()
                            .name("Test Asset")
                            .type(AssetType.STOCK)
                            .quantity(BigDecimal.ZERO)
                            .purchasePrice(new BigDecimal("100.00"))
                            .currentPrice(new BigDecimal("100.00"))
                            .currency("USD")
                            .purchaseDate(LocalDate.now())
                            .build();

            // When
            Asset entity = mapper.toEntity(request);
            AssetResponse response = mapper.toResponse(entity);

            // Then
            assertThat(entity.getQuantity()).isEqualByComparingTo(BigDecimal.ZERO);
            assertThat(response.getTotalValue()).isEqualByComparingTo(BigDecimal.ZERO);
            assertThat(response.getTotalCost()).isEqualByComparingTo(BigDecimal.ZERO);
        }

        @Test
        @DisplayName("Should handle fractional quantities (crypto)")
        void shouldHandleFractionalQuantities() {
            // Given
            AssetRequest request =
                    AssetRequest.builder()
                            .name("Bitcoin")
                            .type(AssetType.CRYPTO)
                            .quantity(new BigDecimal("0.12345678"))
                            .purchasePrice(new BigDecimal("40000.00"))
                            .currentPrice(new BigDecimal("45000.00"))
                            .currency("USD")
                            .purchaseDate(LocalDate.now())
                            .build();

            // When
            Asset entity = mapper.toEntity(request);

            // Then
            assertThat(entity.getQuantity()).isEqualByComparingTo(new BigDecimal("0.12345678"));
        }

        @Test
        @DisplayName("Should handle large quantities")
        void shouldHandleLargeQuantities() {
            // Given
            AssetRequest request =
                    AssetRequest.builder()
                            .name("Penny Stock")
                            .type(AssetType.STOCK)
                            .quantity(new BigDecimal("1000000.00"))
                            .purchasePrice(new BigDecimal("0.01"))
                            .currentPrice(new BigDecimal("0.02"))
                            .currency("USD")
                            .purchaseDate(LocalDate.now())
                            .build();

            // When
            Asset entity = mapper.toEntity(request);
            AssetResponse response = mapper.toResponse(entity);

            // Then
            assertThat(entity.getQuantity()).isEqualByComparingTo(new BigDecimal("1000000.00"));
            assertThat(response.getTotalValue()).isEqualByComparingTo(new BigDecimal("20000.00"));
        }

        @Test
        @DisplayName("Should handle past purchase date")
        void shouldHandlePastPurchaseDate() {
            // Given
            AssetRequest request =
                    AssetRequest.builder()
                            .name("Old Investment")
                            .type(AssetType.STOCK)
                            .quantity(new BigDecimal("10.0"))
                            .purchasePrice(new BigDecimal("50.00"))
                            .currentPrice(new BigDecimal("100.00"))
                            .currency("USD")
                            .purchaseDate(LocalDate.of(2010, 1, 1))
                            .build();

            // When
            Asset entity = mapper.toEntity(request);
            AssetResponse response = mapper.toResponse(entity);

            // Then
            assertThat(entity.getPurchaseDate()).isEqualTo(LocalDate.of(2010, 1, 1));
            assertThat(response.getHoldingDays()).isGreaterThan(5000); // Over 13 years
        }

        @Test
        @DisplayName("Should handle expired warranty")
        void shouldHandleExpiredWarranty() {
            // Given
            Asset entity =
                    Asset.builder()
                            .id(1L)
                            .userId(1L)
                            .name("Old Laptop")
                            .type(AssetType.ELECTRONICS)
                            .warrantyExpiration(LocalDate.now().minusYears(2)) // Expired
                            .quantity(new BigDecimal("1.0"))
                            .purchasePrice(new BigDecimal("1000.00"))
                            .currentPrice(new BigDecimal("500.00"))
                            .currency("USD")
                            .purchaseDate(LocalDate.now().minusYears(5))
                            .build();

            // When
            AssetResponse response = mapper.toResponse(entity);

            // Then
            assertThat(response.getWarrantyExpiration()).isEqualTo(LocalDate.now().minusYears(2));
            assertThat(response.getIsWarrantyValid()).isFalse(); // Expired
        }
    }
}
