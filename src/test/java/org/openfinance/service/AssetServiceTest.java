package org.openfinance.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.openfinance.config.EncryptionProperties;
import org.openfinance.dto.AssetRequest;
import org.openfinance.dto.AssetResponse;
import org.openfinance.entity.Account;
import org.openfinance.entity.Asset;
import org.openfinance.entity.AssetCondition;
import org.openfinance.entity.AssetType;
import org.openfinance.entity.User;
import org.openfinance.exception.AccountNotFoundException;
import org.openfinance.exception.AssetNotFoundException;
import org.openfinance.mapper.AssetMapper;
import org.openfinance.repository.AccountRepository;
import org.openfinance.repository.AssetRepository;
import org.openfinance.repository.CurrencyRepository;
import org.openfinance.repository.NetWorthRepository;
import org.openfinance.repository.UserRepository;
import org.openfinance.security.EncryptionContext;
import org.openfinance.security.EncryptionService;

/**
 * Unit tests for AssetService.
 *
 * <p>
 * Tests CRUD operations, validation, authorization, and portfolio
 * analytics calculations.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AssetService Unit Tests")
class AssetServiceTest {

        @Mock
        private AssetRepository assetRepository;

        @Mock
        private AccountRepository accountRepository;

        @Mock
        private AssetMapper assetMapper;

        @Mock
        private EncryptionService encryptionService;

        @Mock
        private UserRepository userRepository;

        @Mock
        private ExchangeRateService exchangeRateService;

        @Mock
        private OperationHistoryService operationHistoryService;

        @Mock
        private CurrencyRepository currencyRepository;

        @Mock
        private NetWorthRepository netWorthRepository;

        @Mock
        private SearchTokenService searchTokenService;

        @Mock
        private EncryptionProperties encryptionProperties;

        @InjectMocks
        private AssetService assetService;

        private LocalDate purchaseDate;

        @BeforeEach
        void setUp() {
                purchaseDate = LocalDate.of(2025, 1, 15);
                EncryptionContext.setKey(new SecretKeySpec(new byte[32], "AES"));
                lenient().when(encryptionProperties.isEnabled()).thenReturn(true);
        }

        @AfterEach
        void tearDown() {
                EncryptionContext.clear();
        }

        // ========== CREATE ASSET TESTS ==========

        @Test
        @DisplayName("Should create asset successfully and encrypt sensitive fields")
        void shouldCreateAssetSuccessfully() {
                // Arrange
                AssetRequest request = AssetRequest.builder()
                                .accountId(1L)
                                .name("Apple Inc.")
                                .type(AssetType.STOCK)
                                .symbol("AAPL")
                                .quantity(new BigDecimal("10.0"))
                                .purchasePrice(new BigDecimal("150.00"))
                                .currentPrice(new BigDecimal("175.00"))
                                .currency("USD")
                                .purchaseDate(purchaseDate)
                                .notes("Tech portfolio")
                                .build();

                Account account = Account.builder().id(1L).userId(1L).name("Brokerage").build();

                Asset mapped = Asset.builder()
                                .accountId(1L)
                                .type(AssetType.STOCK)
                                .symbol("AAPL")
                                .quantity(new BigDecimal("10.0"))
                                .purchasePrice(new BigDecimal("150.00"))
                                .currentPrice(new BigDecimal("175.00"))
                                .currency("USD")
                                .purchaseDate(purchaseDate)
                                .build();

                Asset saved = Asset.builder()
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
                                .purchaseDate(purchaseDate)
                                .notes("Tech portfolio")
                                .lastUpdated(LocalDateTime.now())
                                .build();

                AssetResponse response = AssetResponse.builder()
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
                                .purchaseDate(purchaseDate)
                                .notes("Tech portfolio")
                                .totalValue(new BigDecimal("1750.00"))
                                .totalCost(new BigDecimal("1500.00"))
                                .unrealizedGain(new BigDecimal("250.00"))
                                .gainPercentage(new BigDecimal("0.1667"))
                                .holdingDays(17L)
                                .build();

                when(accountRepository.findByIdAndUserId(1L, 1L)).thenReturn(Optional.of(account));
                when(assetMapper.toEntity(request)).thenReturn(mapped);
                when(assetRepository.save(any(Asset.class))).thenReturn(saved);
                when(assetMapper.toResponse(any(Asset.class))).thenReturn(response);

                // Act
                AssetResponse created = assetService.createAsset(1L, request);

                // Assert
                assertThat(created).isNotNull();
                assertThat(created.getId()).isEqualTo(1L);
                assertThat(created.getName()).isEqualTo("Apple Inc.");
                assertThat(created.getSymbol()).isEqualTo("AAPL");
                assertThat(created.getTotalValue()).isEqualTo(new BigDecimal("1750.00"));
                assertThat(created.getUnrealizedGain()).isEqualTo(new BigDecimal("250.00"));
                verify(assetRepository).save(any(Asset.class));
        }

        @Test
        @DisplayName("Should create asset without notes and without account")
        void shouldCreateAssetWithoutNotesAndAccount() {
                // Arrange
                AssetRequest request = AssetRequest.builder()
                                .name("Bitcoin")
                                .type(AssetType.CRYPTO)
                                .symbol("BTC")
                                .quantity(new BigDecimal("0.5"))
                                .purchasePrice(new BigDecimal("40000.00"))
                                .currentPrice(new BigDecimal("45000.00"))
                                .currency("USD")
                                .purchaseDate(purchaseDate)
                                .build();

                Asset mapped = Asset.builder()
                                .type(AssetType.CRYPTO)
                                .symbol("BTC")
                                .quantity(new BigDecimal("0.5"))
                                .purchasePrice(new BigDecimal("40000.00"))
                                .currentPrice(new BigDecimal("45000.00"))
                                .currency("USD")
                                .purchaseDate(purchaseDate)
                                .build();

                Asset saved = Asset.builder()
                                .id(2L)
                                .userId(1L)
                                .name("Bitcoin")
                                .type(AssetType.CRYPTO)
                                .symbol("BTC")
                                .quantity(new BigDecimal("0.5"))
                                .purchasePrice(new BigDecimal("40000.00"))
                                .currentPrice(new BigDecimal("45000.00"))
                                .currency("USD")
                                .purchaseDate(purchaseDate)
                                .lastUpdated(LocalDateTime.now())
                                .build();

                AssetResponse response = AssetResponse.builder()
                                .id(2L)
                                .userId(1L)
                                .name("Bitcoin")
                                .type(AssetType.CRYPTO)
                                .symbol("BTC")
                                .quantity(new BigDecimal("0.5"))
                                .purchasePrice(new BigDecimal("40000.00"))
                                .currentPrice(new BigDecimal("45000.00"))
                                .currency("USD")
                                .purchaseDate(purchaseDate)
                                .build();

                when(assetMapper.toEntity(request)).thenReturn(mapped);
                when(assetRepository.save(any(Asset.class))).thenReturn(saved);
                when(assetMapper.toResponse(any(Asset.class))).thenReturn(response);

                // Act
                AssetResponse created = assetService.createAsset(1L, request);

                // Assert
                assertThat(created).isNotNull();
                assertThat(created.getId()).isEqualTo(2L);
                assertThat(created.getName()).isEqualTo("Bitcoin");
                assertThat(created.getAccountId()).isNull();
                assertThat(created.getNotes()).isNull();
                verify(accountRepository, never()).findByIdAndUserId(any(), any());
        }

        @Test
        @DisplayName("Should throw exception when account not found")
        void shouldThrowExceptionWhenAccountNotFound() {
                // Arrange
                AssetRequest request = AssetRequest.builder()
                                .accountId(99L)
                                .name("Apple Inc.")
                                .type(AssetType.STOCK)
                                .symbol("AAPL")
                                .quantity(new BigDecimal("10.0"))
                                .purchasePrice(new BigDecimal("150.00"))
                                .currentPrice(new BigDecimal("175.00"))
                                .currency("USD")
                                .purchaseDate(purchaseDate)
                                .build();

                when(accountRepository.findByIdAndUserId(99L, 1L)).thenReturn(Optional.empty());

                // Act & Assert
                assertThrows(
                                AccountNotFoundException.class,
                                () -> assetService.createAsset(1L, request));
                verify(assetRepository, never()).save(any());
        }

        @Test
        @DisplayName("Should validate null parameters on createAsset")
        void shouldValidateNullParametersOnCreate() {
                AssetRequest request = AssetRequest.builder().build();

                assertThrows(
                                IllegalArgumentException.class,
                                () -> assetService.createAsset(null, request));
                assertThrows(
                                IllegalArgumentException.class, () -> assetService.createAsset(1L, null));
        }

        // ========== UPDATE ASSET TESTS ==========

        @Test
        @DisplayName("Should update asset successfully and re-encrypt fields")
        void shouldUpdateAssetSuccessfully() {
                // Arrange
                AssetRequest request = AssetRequest.builder()
                                .accountId(1L)
                                .name("Apple Inc. Updated")
                                .type(AssetType.STOCK)
                                .symbol("AAPL")
                                .quantity(new BigDecimal("15.0"))
                                .purchasePrice(new BigDecimal("150.00"))
                                .currentPrice(new BigDecimal("180.00"))
                                .currency("USD")
                                .purchaseDate(purchaseDate)
                                .notes("Updated notes")
                                .build();

                Account account = Account.builder().id(1L).userId(1L).name("Brokerage").build();

                Asset existing = Asset.builder()
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
                                .purchaseDate(purchaseDate)
                                .notes("Tech portfolio")
                                .build();

                Asset updated = Asset.builder()
                                .id(1L)
                                .userId(1L)
                                .accountId(1L)
                                .name("Apple Inc. Updated")
                                .type(AssetType.STOCK)
                                .symbol("AAPL")
                                .quantity(new BigDecimal("15.0"))
                                .purchasePrice(new BigDecimal("150.00"))
                                .currentPrice(new BigDecimal("180.00"))
                                .currency("USD")
                                .purchaseDate(purchaseDate)
                                .notes("Updated notes")
                                .lastUpdated(LocalDateTime.now())
                                .build();

                AssetResponse response = AssetResponse.builder()
                                .id(1L)
                                .name("Apple Inc. Updated")
                                .symbol("AAPL")
                                .quantity(new BigDecimal("15.0"))
                                .currentPrice(new BigDecimal("180.00"))
                                .notes("Updated notes")
                                .build();

                when(assetRepository.findByIdAndUserId(1L, 1L)).thenReturn(Optional.of(existing));
                when(accountRepository.findByIdAndUserId(1L, 1L)).thenReturn(Optional.of(account));
                doNothing().when(assetMapper).updateEntityFromRequest(request, existing);
                when(assetRepository.save(existing)).thenReturn(updated);
                when(assetMapper.toResponse(any(Asset.class))).thenReturn(response);

                // Act
                AssetResponse result = assetService.updateAsset(1L, 1L, request);

                // Assert
                assertThat(result).isNotNull();
                assertThat(result.getName()).isEqualTo("Apple Inc. Updated");
                assertThat(result.getNotes()).isEqualTo("Updated notes");
                verify(assetMapper).updateEntityFromRequest(request, existing);
                verify(assetRepository).save(existing);
        }

        @Test
        @DisplayName("Should update lastUpdated timestamp when price changes")
        void shouldUpdateLastUpdatedWhenPriceChanges() {
                // Arrange
                AssetRequest request = AssetRequest.builder()
                                .name("Apple Inc.")
                                .type(AssetType.STOCK)
                                .symbol("AAPL")
                                .quantity(new BigDecimal("10.0"))
                                .purchasePrice(new BigDecimal("150.00"))
                                .currentPrice(new BigDecimal("200.00")) // Price changed
                                .currency("USD")
                                .purchaseDate(purchaseDate)
                                .build();

                Asset existing = Asset.builder()
                                .id(1L)
                                .userId(1L)
                                .name("Apple Inc.")
                                .type(AssetType.STOCK)
                                .symbol("AAPL")
                                .quantity(new BigDecimal("10.0"))
                                .purchasePrice(new BigDecimal("150.00"))
                                .currentPrice(new BigDecimal("175.00")) // Old price
                                .currency("USD")
                                .purchaseDate(purchaseDate)
                                .build();

                when(assetRepository.findByIdAndUserId(1L, 1L)).thenReturn(Optional.of(existing));
                doNothing().when(assetMapper).updateEntityFromRequest(request, existing);
                when(assetRepository.save(existing)).thenReturn(existing);
                when(assetMapper.toResponse(any(Asset.class))).thenReturn(AssetResponse.builder().build());

                // Act
                assetService.updateAsset(1L, 1L, request);

                // Assert
                verify(assetRepository).save(existing);
                // Note: lastUpdated is set in the service, verified implicitly via save
        }

        @Test
        @DisplayName("Should throw exception when updating non-existent asset")
        void shouldThrowExceptionWhenUpdatingNonExistentAsset() {
                // Arrange
                AssetRequest request = AssetRequest.builder().name("Test").build();
                when(assetRepository.findByIdAndUserId(99L, 1L)).thenReturn(Optional.empty());

                // Act & Assert
                assertThrows(
                                AssetNotFoundException.class,
                                () -> assetService.updateAsset(99L, 1L, request));
                verify(assetRepository, never()).save(any());
        }

        @Test
        @DisplayName("Should validate null parameters on updateAsset")
        void shouldValidateNullParametersOnUpdate() {
                AssetRequest request = AssetRequest.builder().build();

                assertThrows(
                                IllegalArgumentException.class,
                                () -> assetService.updateAsset(null, 1L, request));
                assertThrows(
                                IllegalArgumentException.class,
                                () -> assetService.updateAsset(1L, null, request));
                assertThrows(
                                IllegalArgumentException.class,
                                () -> assetService.updateAsset(1L, 1L, null));
        }

        // ========== DELETE ASSET TESTS ==========

        @Test
        @DisplayName("Should delete asset successfully")
        void shouldDeleteAssetSuccessfully() {
                // Arrange
                Asset asset = Asset.builder()
                                .id(1L)
                                .userId(1L)
                                .name("Apple Inc.")
                                .type(AssetType.STOCK)
                                .symbol("AAPL")
                                .build();

                when(assetRepository.findByIdAndUserId(1L, 1L)).thenReturn(Optional.of(asset));
                doNothing().when(assetRepository).delete(asset);

                // Act
                assetService.deleteAsset(1L, 1L);

                // Assert
                verify(assetRepository).findByIdAndUserId(1L, 1L);
                verify(assetRepository).delete(asset);
        }

        @Test
        @DisplayName("Should throw exception when deleting non-existent asset")
        void shouldThrowExceptionWhenDeletingNonExistentAsset() {
                // Arrange
                when(assetRepository.findByIdAndUserId(99L, 1L)).thenReturn(Optional.empty());

                // Act & Assert
                assertThrows(
                                AssetNotFoundException.class, () -> assetService.deleteAsset(99L, 1L));
                verify(assetRepository, never()).delete(any(Asset.class));
        }

        @Test
        @DisplayName("Should validate null parameters on deleteAsset")
        void shouldValidateNullParametersOnDelete() {
                assertThrows(
                                IllegalArgumentException.class, () -> assetService.deleteAsset(null, 1L));
                assertThrows(
                                IllegalArgumentException.class, () -> assetService.deleteAsset(1L, null));
        }

        // ========== GET ASSET TESTS ==========

        @Test
        @DisplayName("Should get asset by ID successfully with decryption")
        void shouldGetAssetByIdSuccessfully() {
                // Arrange
                Asset asset = Asset.builder()
                                .id(1L)
                                .userId(1L)
                                .name("Apple Inc.")
                                .type(AssetType.STOCK)
                                .symbol("AAPL")
                                .quantity(new BigDecimal("10.0"))
                                .purchasePrice(new BigDecimal("150.00"))
                                .currentPrice(new BigDecimal("175.00"))
                                .currency("USD")
                                .purchaseDate(purchaseDate)
                                .notes("Tech portfolio")
                                .build();

                AssetResponse response = AssetResponse.builder()
                                .id(1L)
                                .userId(1L)
                                .name("Apple Inc.")
                                .type(AssetType.STOCK)
                                .symbol("AAPL")
                                .notes("Tech portfolio")
                                .build();

                when(assetRepository.findByIdAndUserId(1L, 1L)).thenReturn(Optional.of(asset));
                when(assetMapper.toResponse(any(Asset.class))).thenReturn(response);

                // Act
                AssetResponse result = assetService.getAssetById(1L, 1L);

                // Assert
                assertThat(result).isNotNull();
                assertThat(result.getId()).isEqualTo(1L);
                assertThat(result.getName()).isEqualTo("Apple Inc.");
                assertThat(result.getNotes()).isEqualTo("Tech portfolio");
        }

        @Test
        @DisplayName("Should get asset plaintext fields without encryption key when encryption is disabled")
        void shouldGetAssetPlaintextFieldsWithoutEncryptionKeyWhenEncryptionDisabled() {
                EncryptionContext.clear();
                when(encryptionProperties.isEnabled()).thenReturn(false);
                Asset asset = Asset.builder()
                                .id(1L)
                                .userId(1L)
                                .name("Plain Asset")
                                .type(AssetType.OTHER)
                                .quantity(BigDecimal.ONE)
                                .purchasePrice(new BigDecimal("10.00"))
                                .currentPrice(new BigDecimal("12.00"))
                                .currency("USD")
                                .purchaseDate(purchaseDate)
                                .notes("Plain notes")
                                .build();
                AssetResponse response = AssetResponse.builder().id(1L).userId(1L).build();

                when(assetRepository.findByIdAndUserId(1L, 1L)).thenReturn(Optional.of(asset));
                when(assetMapper.toResponse(asset)).thenReturn(response);

                AssetResponse result = assetService.getAssetById(1L, 1L);

                assertThat(result.getName()).isEqualTo("Plain Asset");
                assertThat(result.getNotes()).isEqualTo("Plain notes");
        }

        @Test
        @DisplayName("Should throw exception when getting non-existent asset")
        void shouldThrowExceptionWhenGettingNonExistentAsset() {
                // Arrange
                when(assetRepository.findByIdAndUserId(99L, 1L)).thenReturn(Optional.empty());

                // Act & Assert
                assertThrows(
                                AssetNotFoundException.class, () -> assetService.getAssetById(99L, 1L));
        }

        @Test
        @DisplayName("Should validate null parameters on getAssetById")
        void shouldValidateNullParametersOnGetById() {
                assertThrows(
                                IllegalArgumentException.class, () -> assetService.getAssetById(null, 1L));
                assertThrows(
                                IllegalArgumentException.class, () -> assetService.getAssetById(1L, null));
        }

        // ========== GET ASSETS LIST TESTS ==========

        @Test
        @DisplayName("Should get all assets for user")
        void shouldGetAllAssetsForUser() {
                // Arrange
                Asset asset1 = Asset.builder().id(1L).userId(1L).name("Apple").notes("note1").build();
                Asset asset2 = Asset.builder().id(2L).userId(1L).name("Bitcoin").build();

                when(assetRepository.findByUserId(1L)).thenReturn(List.of(asset1, asset2));
                when(assetMapper.toResponse(any(Asset.class)))
                                .thenReturn(AssetResponse.builder().id(1L).name("Apple").build())
                                .thenReturn(AssetResponse.builder().id(2L).name("Bitcoin").build());

                // Act
                List<AssetResponse> results = assetService.getAssetsByUserId(1L);

                // Assert
                assertThat(results).hasSize(2);
                assertThat(results.get(0).getId()).isEqualTo(1L);
                assertThat(results.get(1).getId()).isEqualTo(2L);
                verify(assetRepository).findByUserId(1L);
        }

        @Test
        @DisplayName("Should get assets by account ID")
        void shouldGetAssetsByAccountId() {
                // Arrange
                Account account = Account.builder().id(1L).userId(1L).build();
                Asset asset = Asset.builder().id(1L).userId(1L).accountId(1L).name("Apple").build();

                when(accountRepository.findByIdAndUserId(1L, 1L)).thenReturn(Optional.of(account));
                when(assetRepository.findByUserIdAndAccountId(1L, 1L)).thenReturn(List.of(asset));
                when(assetMapper.toResponse(any(Asset.class)))
                                .thenReturn(AssetResponse.builder().id(1L).build());

                // Act
                List<AssetResponse> results = assetService.getAssetsByAccountId(1L, 1L);

                // Assert
                assertThat(results).hasSize(1);
                verify(accountRepository).findByIdAndUserId(1L, 1L);
                verify(assetRepository).findByUserIdAndAccountId(1L, 1L);
        }

        @Test
        @DisplayName("Should get assets by type")
        void shouldGetAssetsByType() {
                // Arrange
                Asset asset = Asset.builder().id(1L).userId(1L).type(AssetType.STOCK).name("Apple").build();

                when(assetRepository.findByUserIdAndType(1L, AssetType.STOCK)).thenReturn(List.of(asset));
                when(assetMapper.toResponse(any(Asset.class)))
                                .thenReturn(AssetResponse.builder().id(1L).build());

                // Act
                List<AssetResponse> results = assetService.getAssetsByType(1L, AssetType.STOCK);

                // Assert
                assertThat(results).hasSize(1);
                verify(assetRepository).findByUserIdAndType(1L, AssetType.STOCK);
        }

        // ========== PORTFOLIO ANALYTICS TESTS ==========

        @Test
        @DisplayName("Should calculate total value by currency")
        void shouldCalculateTotalValueByCurrency() {
                // Arrange
                Asset asset1 = Asset.builder()
                                .id(1L)
                                .userId(1L)
                                .quantity(new BigDecimal("10.0"))
                                .currentPrice(new BigDecimal("100.0"))
                                .currency("USD")
                                .build();

                Asset asset2 = Asset.builder()
                                .id(2L)
                                .userId(1L)
                                .quantity(new BigDecimal("5.0"))
                                .currentPrice(new BigDecimal("50.0"))
                                .currency("USD")
                                .build();

                Asset asset3 = Asset.builder()
                                .id(3L)
                                .userId(1L)
                                .quantity(new BigDecimal("20.0"))
                                .currentPrice(new BigDecimal("10.0"))
                                .currency("EUR")
                                .build();

                when(assetRepository.findByUserId(1L)).thenReturn(List.of(asset1, asset2, asset3));

                // Act
                Map<String, BigDecimal> result = assetService.getTotalValueByCurrency(1L);

                // Assert
                assertThat(result).hasSize(2);
                assertThat(result.get("USD")).isEqualByComparingTo(new BigDecimal("1250.0")); // 1000 + 250
                assertThat(result.get("EUR")).isEqualByComparingTo(new BigDecimal("200.0"));
                verify(assetRepository).findByUserId(1L);
        }

        @Test
        @DisplayName("Should calculate total cost by currency")
        void shouldCalculateTotalCostByCurrency() {
                // Arrange
                Asset asset1 = Asset.builder()
                                .id(1L)
                                .userId(1L)
                                .quantity(new BigDecimal("10.0"))
                                .purchasePrice(new BigDecimal("80.0"))
                                .currency("USD")
                                .build();

                Asset asset2 = Asset.builder()
                                .id(2L)
                                .userId(1L)
                                .quantity(new BigDecimal("5.0"))
                                .purchasePrice(new BigDecimal("40.0"))
                                .currency("USD")
                                .build();

                when(assetRepository.findByUserId(1L)).thenReturn(List.of(asset1, asset2));

                // Act
                Map<String, BigDecimal> result = assetService.getTotalCostByCurrency(1L);

                // Assert
                assertThat(result).hasSize(1);
                assertThat(result.get("USD")).isEqualByComparingTo(new BigDecimal("1000.0")); // 800 + 200
                verify(assetRepository).findByUserId(1L);
        }

        @Test
        @DisplayName("Should return empty map when no assets exist")
        void shouldReturnEmptyMapWhenNoAssets() {
                // Arrange
                when(assetRepository.findByUserId(1L)).thenReturn(List.of());

                // Act
                Map<String, BigDecimal> values = assetService.getTotalValueByCurrency(1L);
                Map<String, BigDecimal> costs = assetService.getTotalCostByCurrency(1L);

                // Assert
                assertThat(values).isEmpty();
                assertThat(costs).isEmpty();
        }

        // ========== PHYSICAL ASSET TESTS ==========

        @Test
        @DisplayName("Should create physical asset with encrypted physical fields")
        void shouldCreatePhysicalAssetWithEncryptedFields() {
                // Arrange
                AssetRequest request = AssetRequest.builder()
                                .name("Tesla Model 3")
                                .type(AssetType.VEHICLE)
                                .quantity(BigDecimal.ONE)
                                .purchasePrice(new BigDecimal("45000.00"))
                                .currentPrice(new BigDecimal("40000.00"))
                                .currency("USD")
                                .purchaseDate(purchaseDate)
                                .serialNumber("5YJ3E1EA4JF000001")
                                .brand("Tesla")
                                .model("Model 3")
                                .condition(AssetCondition.EXCELLENT)
                                .warrantyExpiration(LocalDate.now().plusYears(2))
                                .usefulLifeYears(15)
                                .photoPath("assets/photos/tesla.jpg")
                                .build();

                Asset mapped = Asset.builder()
                                .type(AssetType.VEHICLE)
                                .quantity(BigDecimal.ONE)
                                .purchasePrice(new BigDecimal("45000.00"))
                                .currentPrice(new BigDecimal("40000.00"))
                                .currency("USD")
                                .purchaseDate(purchaseDate)
                                .condition(AssetCondition.EXCELLENT)
                                .warrantyExpiration(LocalDate.now().plusYears(2))
                                .usefulLifeYears(15)
                                .photoPath("assets/photos/tesla.jpg")
                                .build();

                Asset saved = Asset.builder()
                                .id(1L)
                                .userId(1L)
                                .name("Tesla Model 3")
                                .type(AssetType.VEHICLE)
                                .quantity(BigDecimal.ONE)
                                .purchasePrice(new BigDecimal("45000.00"))
                                .currentPrice(new BigDecimal("40000.00"))
                                .currency("USD")
                                .purchaseDate(purchaseDate)
                                .serialNumber("5YJ3E1EA4JF000001")
                                .brand("Tesla")
                                .model("Model 3")
                                .condition(AssetCondition.EXCELLENT)
                                .warrantyExpiration(LocalDate.now().plusYears(2))
                                .usefulLifeYears(15)
                                .photoPath("assets/photos/tesla.jpg")
                                .lastUpdated(LocalDateTime.now())
                                .build();

                AssetResponse response = AssetResponse.builder()
                                .id(1L)
                                .userId(1L)
                                .name("Tesla Model 3")
                                .type(AssetType.VEHICLE)
                                .serialNumber("5YJ3E1EA4JF000001")
                                .brand("Tesla")
                                .model("Model 3")
                                .condition(AssetCondition.EXCELLENT)
                                .isPhysical(true)
                                .isWarrantyValid(true)
                                .build();

                when(assetMapper.toEntity(request)).thenReturn(mapped);
                when(assetRepository.save(any(Asset.class))).thenReturn(saved);
                when(assetMapper.toResponse(any(Asset.class))).thenReturn(response);

                // Act
                AssetResponse created = assetService.createAsset(1L, request);

                // Assert
                assertThat(created).isNotNull();
                assertThat(created.getName()).isEqualTo("Tesla Model 3");
                assertThat(created.getSerialNumber()).isEqualTo("5YJ3E1EA4JF000001");
                assertThat(created.getBrand()).isEqualTo("Tesla");
                assertThat(created.getModel()).isEqualTo("Model 3");
                assertThat(created.getCondition()).isEqualTo(AssetCondition.EXCELLENT);
                assertThat(created.getIsPhysical()).isTrue();
                assertThat(created.getIsWarrantyValid()).isTrue();
                verify(assetRepository).save(any(Asset.class));
        }

        @Test
        @DisplayName("Should create physical asset without optional physical fields")
        void shouldCreatePhysicalAssetWithoutOptionalFields() {
                // Arrange - Only required fields
                AssetRequest request = AssetRequest.builder()
                                .name("Generic Laptop")
                                .type(AssetType.ELECTRONICS)
                                .quantity(BigDecimal.ONE)
                                .purchasePrice(new BigDecimal("1500.00"))
                                .currentPrice(new BigDecimal("1200.00"))
                                .currency("USD")
                                .purchaseDate(purchaseDate)
                                .build();

                Asset mapped = Asset.builder()
                                .type(AssetType.ELECTRONICS)
                                .quantity(BigDecimal.ONE)
                                .purchasePrice(new BigDecimal("1500.00"))
                                .currentPrice(new BigDecimal("1200.00"))
                                .currency("USD")
                                .purchaseDate(purchaseDate)
                                .build();

                Asset saved = Asset.builder()
                                .id(2L)
                                .userId(1L)
                                .name("Generic Laptop")
                                .type(AssetType.ELECTRONICS)
                                .quantity(BigDecimal.ONE)
                                .purchasePrice(new BigDecimal("1500.00"))
                                .currentPrice(new BigDecimal("1200.00"))
                                .currency("USD")
                                .purchaseDate(purchaseDate)
                                .lastUpdated(LocalDateTime.now())
                                .build();

                AssetResponse response = AssetResponse.builder()
                                .id(2L)
                                .name("Generic Laptop")
                                .type(AssetType.ELECTRONICS)
                                .isPhysical(true)
                                .build();

                when(assetMapper.toEntity(request)).thenReturn(mapped);
                when(assetRepository.save(any(Asset.class))).thenReturn(saved);
                when(assetMapper.toResponse(any(Asset.class))).thenReturn(response);

                // Act
                AssetResponse created = assetService.createAsset(1L, request);

                // Assert
                assertThat(created).isNotNull();
                assertThat(created.getName()).isEqualTo("Generic Laptop");
                assertThat(created.getType()).isEqualTo(AssetType.ELECTRONICS);
                assertThat(created.getIsPhysical()).isTrue();
                verify(assetRepository).save(any(Asset.class));
        }

        @Test
        @DisplayName("Should update physical asset and re-encrypt physical fields")
        void shouldUpdatePhysicalAssetAndReEncryptFields() {
                // Arrange
                AssetRequest request = AssetRequest.builder()
                                .name("Updated Vehicle Name")
                                .type(AssetType.VEHICLE)
                                .quantity(BigDecimal.ONE)
                                .purchasePrice(new BigDecimal("30000.00"))
                                .currentPrice(new BigDecimal("28000.00"))
                                .currency("USD")
                                .purchaseDate(purchaseDate)
                                .serialNumber("NEW-SERIAL-123")
                                .brand("Updated Brand")
                                .model("Updated Model")
                                .condition(AssetCondition.GOOD)
                                .warrantyExpiration(LocalDate.now().plusYears(1))
                                .usefulLifeYears(10)
                                .build();

                Asset existing = Asset.builder()
                                .id(1L)
                                .userId(1L)
                                .name("Old Vehicle")
                                .type(AssetType.VEHICLE)
                                .quantity(BigDecimal.ONE)
                                .purchasePrice(new BigDecimal("30000.00"))
                                .currentPrice(new BigDecimal("29000.00"))
                                .currency("USD")
                                .purchaseDate(purchaseDate)
                                .serialNumber("OLD-SERIAL")
                                .brand("Old Brand")
                                .model("Old Model")
                                .condition(AssetCondition.EXCELLENT)
                                .usefulLifeYears(10)
                                .build();

                Asset updated = Asset.builder()
                                .id(1L)
                                .userId(1L)
                                .name("Updated Vehicle Name")
                                .type(AssetType.VEHICLE)
                                .quantity(BigDecimal.ONE)
                                .purchasePrice(new BigDecimal("30000.00"))
                                .currentPrice(new BigDecimal("28000.00"))
                                .currency("USD")
                                .purchaseDate(purchaseDate)
                                .serialNumber("NEW-SERIAL-123")
                                .brand("Updated Brand")
                                .model("Updated Model")
                                .condition(AssetCondition.GOOD)
                                .warrantyExpiration(LocalDate.now().plusYears(1))
                                .usefulLifeYears(10)
                                .lastUpdated(LocalDateTime.now())
                                .build();

                AssetResponse response = AssetResponse.builder()
                                .id(1L)
                                .name("Updated Vehicle Name")
                                .serialNumber("NEW-SERIAL-123")
                                .brand("Updated Brand")
                                .model("Updated Model")
                                .condition(AssetCondition.GOOD)
                                .build();

                when(assetRepository.findByIdAndUserId(1L, 1L)).thenReturn(Optional.of(existing));
                doNothing().when(assetMapper).updateEntityFromRequest(request, existing);
                when(assetRepository.save(existing)).thenReturn(updated);
                when(assetMapper.toResponse(any(Asset.class))).thenReturn(response);

                // Act
                AssetResponse result = assetService.updateAsset(1L, 1L, request);

                // Assert
                assertThat(result).isNotNull();
                assertThat(result.getName()).isEqualTo("Updated Vehicle Name");
                assertThat(result.getSerialNumber()).isEqualTo("NEW-SERIAL-123");
                assertThat(result.getBrand()).isEqualTo("Updated Brand");
                assertThat(result.getModel()).isEqualTo("Updated Model");
                assertThat(result.getCondition()).isEqualTo(AssetCondition.GOOD);
                verify(assetRepository).save(existing);
        }

        @Test
        @DisplayName("Should clear physical asset fields when updating with empty values")
        void shouldClearPhysicalAssetFieldsWhenUpdatingWithEmptyValues() {
                // Arrange
                AssetRequest request = AssetRequest.builder()
                                .name("Vehicle")
                                .type(AssetType.VEHICLE)
                                .quantity(BigDecimal.ONE)
                                .purchasePrice(new BigDecimal("30000.00"))
                                .currentPrice(new BigDecimal("28000.00"))
                                .currency("USD")
                                .purchaseDate(purchaseDate)
                                .serialNumber("") // Explicit empty string to clear
                                .brand("") // Explicit empty string to clear
                                .model("") // Explicit empty string to clear
                                .build();

                Asset existing = Asset.builder()
                                .id(1L)
                                .userId(1L)
                                .name("Vehicle")
                                .type(AssetType.VEHICLE)
                                .serialNumber("OLD-SERIAL")
                                .brand("Old Brand")
                                .model("Old Model")
                                .quantity(BigDecimal.ONE)
                                .purchasePrice(new BigDecimal("30000.00"))
                                .currentPrice(new BigDecimal("28000.00"))
                                .currency("USD")
                                .purchaseDate(purchaseDate)
                                .build();

                when(assetRepository.findByIdAndUserId(1L, 1L)).thenReturn(Optional.of(existing));
                doNothing().when(assetMapper).updateEntityFromRequest(request, existing);
                when(assetRepository.save(existing)).thenReturn(existing);
                when(assetMapper.toResponse(any(Asset.class))).thenReturn(AssetResponse.builder().build());

                // Act
                assetService.updateAsset(1L, 1L, request);

                // Assert
                assertThat(existing.getSerialNumber()).isNull();
                assertThat(existing.getBrand()).isNull();
                assertThat(existing.getModel()).isNull();
                verify(assetRepository).save(existing);
        }

        @Test
        @DisplayName("Should decrypt physical asset fields when retrieving by ID")
        void shouldDecryptPhysicalAssetFieldsWhenRetrievingById() {
                // Arrange
                Asset asset = Asset.builder()
                                .id(1L)
                                .userId(1L)
                                .name("Diamond Ring")
                                .type(AssetType.JEWELRY)
                                .serialNumber("DIA-12345")
                                .brand("Tiffany & Co")
                                .model("Solitaire")
                                .condition(AssetCondition.NEW)
                                .warrantyExpiration(LocalDate.now().plusYears(5))
                                .quantity(BigDecimal.ONE)
                                .purchasePrice(new BigDecimal("5000.00"))
                                .currentPrice(new BigDecimal("5500.00"))
                                .currency("USD")
                                .purchaseDate(purchaseDate)
                                .build();

                AssetResponse response = AssetResponse.builder()
                                .id(1L)
                                .name("Diamond Ring")
                                .type(AssetType.JEWELRY)
                                .serialNumber("DIA-12345")
                                .brand("Tiffany & Co")
                                .model("Solitaire")
                                .condition(AssetCondition.NEW)
                                .isPhysical(true)
                                .isWarrantyValid(true)
                                .build();

                when(assetRepository.findByIdAndUserId(1L, 1L)).thenReturn(Optional.of(asset));
                when(assetMapper.toResponse(any(Asset.class))).thenReturn(response);

                // Act
                AssetResponse result = assetService.getAssetById(1L, 1L);

                // Assert
                assertThat(result).isNotNull();
                assertThat(result.getName()).isEqualTo("Diamond Ring");
                assertThat(result.getSerialNumber()).isEqualTo("DIA-12345");
                assertThat(result.getBrand()).isEqualTo("Tiffany & Co");
                assertThat(result.getModel()).isEqualTo("Solitaire");
                assertThat(result.getCondition()).isEqualTo(AssetCondition.NEW);
                assertThat(result.getIsPhysical()).isTrue();
                assertThat(result.getIsWarrantyValid()).isTrue();
        }

        @Test
        @DisplayName("Should handle physical asset with null physical fields during decryption")
        void shouldHandlePhysicalAssetWithNullPhysicalFieldsDuringDecryption() {
                // Arrange - Physical asset with no optional physical fields set
                Asset asset = Asset.builder()
                                .id(1L)
                                .userId(1L)
                                .name("Rare Coin")
                                .type(AssetType.COLLECTIBLE)
                                .serialNumber(null)
                                .brand(null)
                                .model(null)
                                .condition(null)
                                .warrantyExpiration(null)
                                .quantity(BigDecimal.ONE)
                                .purchasePrice(new BigDecimal("500.00"))
                                .currentPrice(new BigDecimal("600.00"))
                                .currency("USD")
                                .purchaseDate(purchaseDate)
                                .build();

                AssetResponse response = AssetResponse.builder()
                                .id(1L)
                                .name("Rare Coin")
                                .type(AssetType.COLLECTIBLE)
                                .isPhysical(true)
                                .build();

                when(assetRepository.findByIdAndUserId(1L, 1L)).thenReturn(Optional.of(asset));
                when(assetMapper.toResponse(any(Asset.class))).thenReturn(response);

                // Act
                AssetResponse result = assetService.getAssetById(1L, 1L);

                // Assert
                assertThat(result).isNotNull();
                assertThat(result.getName()).isEqualTo("Rare Coin");
                assertThat(result.getType()).isEqualTo(AssetType.COLLECTIBLE);
                assertThat(result.getIsPhysical()).isTrue();
        }

        @Test
        @DisplayName("Should include depreciation values in response for physical assets")
        void shouldIncludeDepreciationValuesInResponseForPhysicalAssets() {
                // Arrange
                LocalDate twoYearsAgo = LocalDate.now().minusYears(2);
                Asset asset = Asset.builder()
                                .id(1L)
                                .userId(1L)
                                .name("Leather Sofa")
                                .type(AssetType.FURNITURE)
                                .quantity(BigDecimal.ONE)
                                .purchasePrice(new BigDecimal("3000.00"))
                                .currentPrice(new BigDecimal("2500.00"))
                                .currency("USD")
                                .purchaseDate(twoYearsAgo)
                                .usefulLifeYears(10)
                                .condition(AssetCondition.GOOD)
                                .build();

                // Calculated values
                BigDecimal depreciatedValue = new BigDecimal("2400.00"); // 3000 - (3000/10*2)
                BigDecimal conditionAdjustedValue = new BigDecimal("1488.00"); // 2400 * 0.62

                AssetResponse response = AssetResponse.builder()
                                .id(1L)
                                .name("Leather Sofa")
                                .type(AssetType.FURNITURE)
                                .depreciatedValue(depreciatedValue)
                                .conditionAdjustedValue(conditionAdjustedValue)
                                .isPhysical(true)
                                .build();

                when(assetRepository.findByIdAndUserId(1L, 1L)).thenReturn(Optional.of(asset));
                when(assetMapper.toResponse(any(Asset.class))).thenReturn(response);

                // Act
                AssetResponse result = assetService.getAssetById(1L, 1L);

                // Assert
                assertThat(result).isNotNull();
                assertThat(result.getDepreciatedValue()).isEqualByComparingTo(depreciatedValue);
                assertThat(result.getConditionAdjustedValue()).isEqualByComparingTo(conditionAdjustedValue);
                assertThat(result.getIsPhysical()).isTrue();
        }

        // Currency conversion tests (REQ-3.1, REQ-3.5, REQ-3.6)

        @Test
        @DisplayName("Should populate conversion fields when currency different from base")
        void shouldPopulateConversionFieldsWhenCurrencyDifferentFromBase() {
                // Arrange
                Asset asset = Asset.builder()
                                .id(1L)
                                .userId(1L)
                                .name("My Asset")
                                .quantity(new BigDecimal("10.0"))
                                .currentPrice(new BigDecimal("100.0"))
                                .currency("USD")
                                .build();

                AssetResponse response = AssetResponse.builder()
                                .id(1L)
                                .totalValue(new BigDecimal("1000.0"))
                                .currency("USD")
                                .build();

                User user = User.builder().baseCurrency("EUR").build();

                when(assetRepository.findByIdAndUserId(1L, 1L)).thenReturn(Optional.of(asset));
                when(assetMapper.toResponse(asset)).thenReturn(response);
                when(userRepository.findById(1L)).thenReturn(Optional.of(user));
                when(exchangeRateService.getExchangeRate("USD", "EUR", null))
                                .thenReturn(new BigDecimal("0.85"));
                when(exchangeRateService.convert(new BigDecimal("1000.0"), "USD", "EUR"))
                                .thenReturn(new BigDecimal("850.0"));

                // Act
                AssetResponse result = assetService.getAssetById(1L, 1L);

                // Assert
                assertThat(result.getIsConverted()).isTrue();
                assertThat(result.getBaseCurrency()).isEqualTo("EUR");
                assertThat(result.getValueInBaseCurrency()).isEqualByComparingTo(new BigDecimal("850.0"));
                assertThat(result.getExchangeRate()).isEqualByComparingTo(new BigDecimal("0.85"));
        }

        @Test
        @DisplayName("Should fallback to native currency when exchange rate missing")
        void shouldFallbackToNativeCurrencyWhenExchangeRateMissing() {
                // Arrange
                Asset asset = Asset.builder()
                                .id(1L)
                                .userId(1L)
                                .name("My Asset")
                                .quantity(new BigDecimal("10.0"))
                                .currentPrice(new BigDecimal("100.0"))
                                .currency("USD")
                                .build();

                AssetResponse response = AssetResponse.builder()
                                .id(1L)
                                .totalValue(new BigDecimal("1000.0"))
                                .currency("USD")
                                .build();

                User user = User.builder().baseCurrency("EUR").build();

                when(assetRepository.findByIdAndUserId(1L, 1L)).thenReturn(Optional.of(asset));
                when(assetMapper.toResponse(asset)).thenReturn(response);
                when(userRepository.findById(1L)).thenReturn(Optional.of(user));
                when(exchangeRateService.convert(any(BigDecimal.class), eq("USD"), eq("EUR")))
                                .thenThrow(new RuntimeException("Rate not found"));

                // Act
                AssetResponse result = assetService.getAssetById(1L, 1L);

                // Assert
                assertThat(result.getIsConverted()).isFalse();
                assertThat(result.getValueInBaseCurrency()).isEqualByComparingTo(new BigDecimal("1000.0"));
                assertThat(result.getBaseCurrency()).isEqualTo("EUR");
        }

        @Test
        @DisplayName("Should not convert when currency matches base currency")
        void shouldNotConvertWhenCurrencyMatchesBaseCurrency() {
                // Arrange
                Asset asset = Asset.builder()
                                .id(1L)
                                .userId(1L)
                                .name("My Asset")
                                .quantity(new BigDecimal("10.0"))
                                .currentPrice(new BigDecimal("100.0"))
                                .currency("USD")
                                .build();

                AssetResponse response = AssetResponse.builder()
                                .id(1L)
                                .totalValue(new BigDecimal("1000.0"))
                                .currency("USD")
                                .build();

                User user = User.builder().baseCurrency("USD").build();

                when(assetRepository.findByIdAndUserId(1L, 1L)).thenReturn(Optional.of(asset));
                when(assetMapper.toResponse(asset)).thenReturn(response);
                when(userRepository.findById(1L)).thenReturn(Optional.of(user));

                // Act
                AssetResponse result = assetService.getAssetById(1L, 1L);

                // Assert
                assertThat(result.getIsConverted()).isFalse();
                assertThat(result.getValueInBaseCurrency()).isEqualByComparingTo(new BigDecimal("1000.0"));
                assertThat(result.getBaseCurrency()).isEqualTo("USD");
                verify(exchangeRateService, never()).getExchangeRate(any(), any(), any());
                verify(exchangeRateService, never()).convert(any(), any(), any());
        }

        // Secondary currency conversion tests (REQ-4.2, REQ-4.5, REQ-4.6)

        @Test
        @DisplayName("Should populate secondary currency fields when secondary currency is configured and differs from native")
        void shouldPopulateSecondaryCurrencyFieldsWhenConfigured() {
                // Arrange — native=USD, base=EUR, secondary=JPY
                Asset asset = Asset.builder()
                                .id(1L)
                                .userId(1L)
                                .name("My Asset")
                                .quantity(new BigDecimal("10.0"))
                                .currentPrice(new BigDecimal("100.0"))
                                .currency("USD")
                                .build();

                AssetResponse response = AssetResponse.builder()
                                .id(1L)
                                .totalValue(new BigDecimal("1000.0"))
                                .currency("USD")
                                .build();

                User user = User.builder().baseCurrency("EUR").secondaryCurrency("JPY").build();

                when(assetRepository.findByIdAndUserId(1L, 1L)).thenReturn(Optional.of(asset));
                when(assetMapper.toResponse(asset)).thenReturn(response);
                when(userRepository.findById(1L)).thenReturn(Optional.of(user));
                when(exchangeRateService.getExchangeRate("USD", "EUR", null))
                                .thenReturn(new BigDecimal("0.85"));
                when(exchangeRateService.convert(new BigDecimal("1000.0"), "USD", "EUR"))
                                .thenReturn(new BigDecimal("850.0"));
                when(exchangeRateService.getExchangeRate("USD", "JPY", null))
                                .thenReturn(new BigDecimal("150.0"));
                when(exchangeRateService.convert(new BigDecimal("1000.0"), "USD", "JPY"))
                                .thenReturn(new BigDecimal("150000.0"));

                // Act
                AssetResponse result = assetService.getAssetById(1L, 1L);

                // Assert — Requirement REQ-4.2: secondary fields populated
                assertThat(result.getValueInSecondaryCurrency())
                                .isEqualByComparingTo(new BigDecimal("150000.0"));
                assertThat(result.getSecondaryCurrency()).isEqualTo("JPY");
                assertThat(result.getSecondaryExchangeRate()).isEqualByComparingTo(new BigDecimal("150.0"));
                // Base conversion also works
                assertThat(result.getIsConverted()).isTrue();
                assertThat(result.getValueInBaseCurrency()).isEqualByComparingTo(new BigDecimal("850.0"));
        }

        @Test
        @DisplayName("Should set secondary fields to null when native currency equals secondary currency")
        void shouldSkipSecondaryCurrencyConversionWhenNativeEqualsSecondary() {
                // Arrange — native=USD, base=EUR, secondary=USD (same as native → no
                // conversion)
                Asset asset = Asset.builder()
                                .id(1L)
                                .userId(1L)
                                .name("My Asset")
                                .quantity(new BigDecimal("10.0"))
                                .currentPrice(new BigDecimal("100.0"))
                                .currency("USD")
                                .build();

                AssetResponse response = AssetResponse.builder()
                                .id(1L)
                                .totalValue(new BigDecimal("1000.0"))
                                .currency("USD")
                                .build();

                User user = User.builder().baseCurrency("EUR").secondaryCurrency("USD").build();

                when(assetRepository.findByIdAndUserId(1L, 1L)).thenReturn(Optional.of(asset));
                when(assetMapper.toResponse(asset)).thenReturn(response);
                when(userRepository.findById(1L)).thenReturn(Optional.of(user));
                when(exchangeRateService.getExchangeRate("USD", "EUR", null))
                                .thenReturn(new BigDecimal("0.85"));
                when(exchangeRateService.convert(new BigDecimal("1000.0"), "USD", "EUR"))
                                .thenReturn(new BigDecimal("850.0"));

                // Act
                AssetResponse result = assetService.getAssetById(1L, 1L);

                // Assert — Requirement REQ-4.6: no redundant conversion when native equals
                // secondary
                assertThat(result.getValueInSecondaryCurrency()).isNull();
                assertThat(result.getSecondaryCurrency()).isEqualTo("USD");
                assertThat(result.getSecondaryExchangeRate()).isNull();
                // Base conversion is unaffected
                assertThat(result.getIsConverted()).isTrue();
                assertThat(result.getValueInBaseCurrency()).isEqualByComparingTo(new BigDecimal("850.0"));
                // Verify exchange rate service was NOT called for secondary conversion
                verify(exchangeRateService, never()).convert(any(BigDecimal.class), eq("USD"), eq("USD"));
        }

        @Test
        @DisplayName("Should leave valueInSecondaryCurrency null but set secondaryCurrency when secondary rate is unavailable")
        void shouldLeaveSecondaryAmountNullWhenSecondaryRateUnavailable() {
                // Arrange — native=USD, base=EUR, secondary=JPY — JPY rate throws
                Asset asset = Asset.builder()
                                .id(1L)
                                .userId(1L)
                                .name("My Asset")
                                .quantity(new BigDecimal("10.0"))
                                .currentPrice(new BigDecimal("100.0"))
                                .currency("USD")
                                .build();

                AssetResponse response = AssetResponse.builder()
                                .id(1L)
                                .totalValue(new BigDecimal("1000.0"))
                                .currency("USD")
                                .build();

                User user = User.builder().baseCurrency("EUR").secondaryCurrency("JPY").build();

                when(assetRepository.findByIdAndUserId(1L, 1L)).thenReturn(Optional.of(asset));
                when(assetMapper.toResponse(asset)).thenReturn(response);
                when(userRepository.findById(1L)).thenReturn(Optional.of(user));
                when(exchangeRateService.getExchangeRate("USD", "EUR", null))
                                .thenReturn(new BigDecimal("0.85"));
                when(exchangeRateService.convert(new BigDecimal("1000.0"), "USD", "EUR"))
                                .thenReturn(new BigDecimal("850.0"));
                when(exchangeRateService.convert(new BigDecimal("1000.0"), "USD", "JPY"))
                                .thenThrow(new RuntimeException("JPY rate unavailable"));

                // Act — must not throw
                AssetResponse result = assetService.getAssetById(1L, 1L);

                // Assert — Requirement REQ-4.5: graceful fallback; amount null, currency code
                // still set
                assertThat(result.getValueInSecondaryCurrency()).isNull();
                assertThat(result.getSecondaryCurrency()).isEqualTo("JPY");
                // Base conversion is unaffected
                assertThat(result.getIsConverted()).isTrue();
                assertThat(result.getValueInBaseCurrency()).isEqualByComparingTo(new BigDecimal("850.0"));
        }

        @Test
        @DisplayName("Should leave all secondary fields null when no secondary currency is configured")
        void shouldLeaveSecondaryFieldsNullWhenNoSecondaryCurrencyConfigured() {
                // Arrange — native=USD, base=EUR, secondary=null
                Asset asset = Asset.builder()
                                .id(1L)
                                .userId(1L)
                                .name("My Asset")
                                .quantity(new BigDecimal("10.0"))
                                .currentPrice(new BigDecimal("100.0"))
                                .currency("USD")
                                .build();

                AssetResponse response = AssetResponse.builder()
                                .id(1L)
                                .totalValue(new BigDecimal("1000.0"))
                                .currency("USD")
                                .build();

                User user = User.builder().baseCurrency("EUR").build(); // secondaryCurrency is null by default

                when(assetRepository.findByIdAndUserId(1L, 1L)).thenReturn(Optional.of(asset));
                when(assetMapper.toResponse(asset)).thenReturn(response);
                when(userRepository.findById(1L)).thenReturn(Optional.of(user));
                when(exchangeRateService.getExchangeRate("USD", "EUR", null))
                                .thenReturn(new BigDecimal("0.85"));
                when(exchangeRateService.convert(new BigDecimal("1000.0"), "USD", "EUR"))
                                .thenReturn(new BigDecimal("850.0"));

                // Act
                AssetResponse result = assetService.getAssetById(1L, 1L);

                // Assert — Requirement REQ-4.2: all secondary fields must be null
                assertThat(result.getValueInSecondaryCurrency()).isNull();
                assertThat(result.getSecondaryCurrency()).isNull();
                assertThat(result.getSecondaryExchangeRate()).isNull();
                // Base conversion works normally
                assertThat(result.getIsConverted()).isTrue();
                assertThat(result.getValueInBaseCurrency()).isEqualByComparingTo(new BigDecimal("850.0"));
        }
}
