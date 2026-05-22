package org.openfinance.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.openfinance.dto.PropertyEquityResponse;
import org.openfinance.dto.PropertyROIResponse;
import org.openfinance.dto.RealEstatePropertyRequest;
import org.openfinance.dto.RealEstatePropertyResponse;
import org.openfinance.entity.Liability;
import org.openfinance.entity.PropertyType;
import org.openfinance.entity.RealEstateProperty;
import org.openfinance.entity.User;
import org.openfinance.exception.RealEstatePropertyNotFoundException;
import org.openfinance.mapper.RealEstateMapper;
import org.openfinance.repository.CurrencyRepository;
import org.openfinance.repository.LiabilityRepository;
import org.openfinance.repository.NetWorthRepository;
import org.openfinance.repository.RealEstateRepository;
import org.openfinance.repository.RealEstateValueHistoryRepository;
import org.openfinance.repository.UserRepository;
import org.openfinance.security.EncryptionService;

/**
 * Unit tests for RealEstateService.
 *
 * <p>Tests CRUD operations, encryption/decryption, equity calculations, ROI calculations,
 * authorization checks, and validation.
 *
 * <p>Requirements: REQ-2.16.1 (Property Management), REQ-2.16.2 (Equity and ROI Calculations)
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("RealEstateService Unit Tests")
class RealEstateServiceTest {

    @Mock(lenient = true)
    private RealEstateRepository realEstateRepository;

    @Mock(lenient = true)
    private LiabilityRepository liabilityRepository;

    @Mock(lenient = true)
    private RealEstateMapper realEstateMapper;

    @Mock(lenient = true)
    private EncryptionService encryptionService;

    @Mock(lenient = true)
    private AssetService assetService;

    @Mock(lenient = true)
    private UserRepository userRepository;

    @Mock(lenient = true)
    private RealEstateValueHistoryRepository valueHistoryRepository;

    @Mock(lenient = true)
    private ExchangeRateService exchangeRateService;

    @Mock(lenient = true)
    private NetWorthRepository netWorthRepository;

    @Mock private OperationHistoryService operationHistoryService;

    @Mock private CurrencyRepository currencyRepository;

    @InjectMocks private RealEstateService realEstateService;

    private SecretKey testKey;
    private LocalDate purchaseDate;
    private Long userId;
    private Long propertyId;

    @BeforeEach
    void setUp() {
        byte[] keyBytes = new byte[16];
        for (int i = 0; i < keyBytes.length; i++) keyBytes[i] = (byte) i;
        testKey = new SecretKeySpec(keyBytes, "AES");
        purchaseDate = LocalDate.of(2020, 1, 15);
        userId = 1L;
        propertyId = 1L;

        // Lenient stub: return a USD user so populateConversionFields never NPEs.
        // Tests that specifically verify conversion can override this stub.
        User defaultUser = User.builder().baseCurrency("USD").build();
        org.mockito.Mockito.lenient()
                .when(userRepository.findById(userId))
                .thenReturn(Optional.of(defaultUser));
    }

    // ========== CREATE PROPERTY TESTS ==========

    @Test
    @DisplayName("Should create property successfully and encrypt sensitive fields")
    void shouldCreatePropertySuccessfully() {
        // Arrange
        RealEstatePropertyRequest request =
                RealEstatePropertyRequest.builder()
                        .name("Downtown Apartment")
                        .propertyType(PropertyType.RESIDENTIAL)
                        .address("123 Main St, New York, NY 10001")
                        .purchasePrice(new BigDecimal("500000.00"))
                        .currentValue(new BigDecimal("600000.00"))
                        .currency("USD")
                        .purchaseDate(purchaseDate)
                        .rentalIncome(new BigDecimal("3000.00"))
                        .mortgageId(10L)
                        .latitude(new BigDecimal("40.7128"))
                        .longitude(new BigDecimal("-74.0060"))
                        .notes("Prime location")
                        .documents("contract.pdf,deed.pdf")
                        .build();

        RealEstateProperty mapped =
                RealEstateProperty.builder()
                        .propertyType(PropertyType.RESIDENTIAL)
                        .purchaseDate(purchaseDate)
                        .currency("USD")
                        .mortgageId(10L)
                        .latitude(new BigDecimal("40.7128"))
                        .longitude(new BigDecimal("-74.0060"))
                        .isActive(true)
                        .build();

        RealEstateProperty saved =
                RealEstateProperty.builder()
                        .id(propertyId)
                        .userId(userId)
                        .name("enc-name")
                        .propertyType(PropertyType.RESIDENTIAL)
                        .address("enc-address")
                        .purchasePrice("enc-purchase-price")
                        .currentValue("enc-current-value")
                        .rentalIncome("enc-rental-income")
                        .purchaseDate(purchaseDate)
                        .currency("USD")
                        .mortgageId(10L)
                        .latitude(new BigDecimal("40.7128"))
                        .longitude(new BigDecimal("-74.0060"))
                        .notes("enc-notes")
                        .documents("enc-documents")
                        .isActive(true)
                        .createdAt(LocalDateTime.now())
                        .updatedAt(LocalDateTime.now())
                        .build();

        RealEstatePropertyResponse mappedResponse =
                RealEstatePropertyResponse.builder()
                        .id(propertyId)
                        .userId(userId)
                        .propertyType(PropertyType.RESIDENTIAL)
                        .currency("USD")
                        .purchaseDate(purchaseDate)
                        .mortgageId(10L)
                        .latitude(new BigDecimal("40.7128"))
                        .longitude(new BigDecimal("-74.0060"))
                        .isActive(true)
                        .build();

        // Mock mortgage validation
        Liability mortgage = new Liability();
        mortgage.setId(10L);
        mortgage.setUserId(userId);
        when(liabilityRepository.existsByIdAndUserId(10L, userId)).thenReturn(true);

        // Mock mortgage reload after property save (for response population)
        when(realEstateRepository.findByIdAndUserId(propertyId, userId))
                .thenReturn(Optional.of(saved));

        when(realEstateMapper.toEntity(request)).thenReturn(mapped);
        when(encryptionService.encrypt("Downtown Apartment", testKey)).thenReturn("enc-name");
        when(encryptionService.encrypt("123 Main St, New York, NY 10001", testKey))
                .thenReturn("enc-address");
        when(encryptionService.encrypt("500000.00", testKey)).thenReturn("enc-purchase-price");
        when(encryptionService.encrypt("600000.00", testKey)).thenReturn("enc-current-value");
        when(encryptionService.encrypt("3000.00", testKey)).thenReturn("enc-rental-income");
        when(encryptionService.encrypt("Prime location", testKey)).thenReturn("enc-notes");
        when(encryptionService.encrypt("contract.pdf,deed.pdf", testKey))
                .thenReturn("enc-documents");
        when(realEstateRepository.save(any(RealEstateProperty.class))).thenReturn(saved);
        when(realEstateMapper.toResponse(saved)).thenReturn(mappedResponse);

        // Mock decryption calls in toResponseWithDecryption
        when(encryptionService.decrypt("enc-name", testKey)).thenReturn("Downtown Apartment");
        when(encryptionService.decrypt("enc-address", testKey))
                .thenReturn("123 Main St, New York, NY 10001");
        when(encryptionService.decrypt("enc-purchase-price", testKey)).thenReturn("500000.00");
        when(encryptionService.decrypt("enc-current-value", testKey)).thenReturn("600000.00");
        when(encryptionService.decrypt("enc-rental-income", testKey)).thenReturn("3000.00");
        when(encryptionService.decrypt("enc-notes", testKey)).thenReturn("Prime location");
        when(encryptionService.decrypt("enc-documents", testKey))
                .thenReturn("contract.pdf,deed.pdf");

        org.openfinance.dto.AssetResponse assetResponse = new org.openfinance.dto.AssetResponse();
        assetResponse.setId(100L);
        when(assetService.createAsset(
                        eq(userId), any(org.openfinance.dto.AssetRequest.class), eq(testKey)))
                .thenReturn(assetResponse);

        // Act
        RealEstatePropertyResponse created =
                realEstateService.createProperty(userId, request, testKey);

        // Assert
        assertThat(created).isNotNull();
        assertThat(created.getId()).isEqualTo(propertyId);
        assertThat(created.getName()).isEqualTo("Downtown Apartment");
        assertThat(created.getPropertyType()).isEqualTo(PropertyType.RESIDENTIAL);
        assertThat(created.getCurrentValue()).isEqualTo(new BigDecimal("600000.00"));

        // Verify all encryptions happened
        verify(encryptionService).encrypt("Downtown Apartment", testKey);
        verify(valueHistoryRepository).save(any());
        verify(encryptionService).encrypt("123 Main St, New York, NY 10001", testKey);
        verify(encryptionService).encrypt("500000.00", testKey);
        verify(encryptionService, org.mockito.Mockito.atLeastOnce()).encrypt("600000.00", testKey);
        verify(encryptionService).encrypt("3000.00", testKey);
        verify(encryptionService).encrypt("Prime location", testKey);
        verify(encryptionService).encrypt("contract.pdf,deed.pdf", testKey);
        verify(realEstateRepository, org.mockito.Mockito.atLeastOnce())
                .save(any(RealEstateProperty.class));
    }

    @Test
    @DisplayName("Should create property without optional fields")
    void shouldCreatePropertyWithoutOptionalFields() {
        // Arrange
        RealEstatePropertyRequest request =
                RealEstatePropertyRequest.builder()
                        .name("Vacant Land")
                        .propertyType(PropertyType.LAND)
                        .address("Rural Road, County")
                        .purchasePrice(new BigDecimal("50000.00"))
                        .currentValue(new BigDecimal("55000.00"))
                        .currency("USD")
                        .purchaseDate(purchaseDate)
                        .build();

        RealEstateProperty mapped =
                RealEstateProperty.builder()
                        .propertyType(PropertyType.LAND)
                        .purchaseDate(purchaseDate)
                        .currency("USD")
                        .isActive(true)
                        .build();

        RealEstateProperty saved =
                RealEstateProperty.builder()
                        .id(propertyId)
                        .userId(userId)
                        .name("enc-name")
                        .propertyType(PropertyType.LAND)
                        .address("enc-address")
                        .purchasePrice("enc-purchase-price")
                        .currentValue("enc-current-value")
                        .purchaseDate(purchaseDate)
                        .currency("USD")
                        .isActive(true)
                        .createdAt(LocalDateTime.now())
                        .updatedAt(LocalDateTime.now())
                        .build();

        RealEstatePropertyResponse mappedResponse =
                RealEstatePropertyResponse.builder()
                        .id(propertyId)
                        .userId(userId)
                        .propertyType(PropertyType.LAND)
                        .currency("USD")
                        .purchaseDate(purchaseDate)
                        .isActive(true)
                        .build();

        when(realEstateMapper.toEntity(request)).thenReturn(mapped);
        when(encryptionService.encrypt("Vacant Land", testKey)).thenReturn("enc-name");
        when(encryptionService.encrypt("Rural Road, County", testKey)).thenReturn("enc-address");
        when(encryptionService.encrypt("50000.00", testKey)).thenReturn("enc-purchase-price");
        when(encryptionService.encrypt("55000.00", testKey)).thenReturn("enc-current-value");
        when(realEstateRepository.save(any(RealEstateProperty.class))).thenReturn(saved);
        when(realEstateMapper.toResponse(saved)).thenReturn(mappedResponse);

        // Mock decryption
        when(encryptionService.decrypt("enc-name", testKey)).thenReturn("Vacant Land");
        when(encryptionService.decrypt("enc-address", testKey)).thenReturn("Rural Road, County");
        when(encryptionService.decrypt("enc-purchase-price", testKey)).thenReturn("50000.00");
        when(encryptionService.decrypt("enc-current-value", testKey)).thenReturn("55000.00");

        org.openfinance.dto.AssetResponse assetResponse = new org.openfinance.dto.AssetResponse();
        assetResponse.setId(100L);
        when(assetService.createAsset(
                        eq(userId), any(org.openfinance.dto.AssetRequest.class), eq(testKey)))
                .thenReturn(assetResponse);

        // Act
        RealEstatePropertyResponse created =
                realEstateService.createProperty(userId, request, testKey);

        // Assert
        assertThat(created).isNotNull();
        verify(encryptionService, never()).encrypt(eq(""), any());
        verify(encryptionService, times(5))
                .encrypt(any(String.class), eq(testKey)); // 4 required fields, but
        // currentValue encrypted
        // twice (prop + history)
    }

    @Test
    @DisplayName("Should validate null parameters on createProperty")
    void shouldValidateNullParametersOnCreate() {
        RealEstatePropertyRequest request = RealEstatePropertyRequest.builder().build();

        assertThatThrownBy(() -> realEstateService.createProperty(null, request, testKey))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("User ID cannot be null");

        assertThatThrownBy(() -> realEstateService.createProperty(userId, null, testKey))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Property request cannot be null");

        assertThatThrownBy(() -> realEstateService.createProperty(userId, request, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Encryption key cannot be null");
    }

    // ========== UPDATE PROPERTY TESTS ==========

    @Test
    @DisplayName("Should update property successfully and re-encrypt fields")
    void shouldUpdatePropertySuccessfully() {
        // Arrange
        RealEstatePropertyRequest request =
                RealEstatePropertyRequest.builder()
                        .name("Downtown Apartment Updated")
                        .propertyType(PropertyType.RESIDENTIAL)
                        .address("123 Main St, New York, NY 10001")
                        .purchasePrice(new BigDecimal("500000.00"))
                        .currentValue(new BigDecimal("650000.00")) // Updated value
                        .currency("USD")
                        .purchaseDate(purchaseDate)
                        .rentalIncome(new BigDecimal("3200.00")) // Updated rental
                        .mortgageId(10L)
                        .notes("Updated notes")
                        .build();

        RealEstateProperty existing =
                RealEstateProperty.builder()
                        .id(propertyId)
                        .userId(userId)
                        .name("enc-old-name")
                        .propertyType(PropertyType.RESIDENTIAL)
                        .address("enc-old-address")
                        .purchasePrice("enc-old-purchase-price")
                        .currentValue("enc-old-current-value")
                        .purchaseDate(purchaseDate)
                        .currency("USD")
                        .isActive(true)
                        .build();

        RealEstateProperty updated =
                RealEstateProperty.builder()
                        .id(propertyId)
                        .userId(userId)
                        .name("enc-new-name")
                        .propertyType(PropertyType.RESIDENTIAL)
                        .address("enc-new-address")
                        .purchasePrice("enc-new-purchase-price")
                        .currentValue("enc-new-current-value")
                        .rentalIncome("enc-new-rental-income")
                        .purchaseDate(purchaseDate)
                        .currency("USD")
                        .mortgageId(10L)
                        .notes("enc-new-notes")
                        .isActive(true)
                        .updatedAt(LocalDateTime.now())
                        .build();

        RealEstatePropertyResponse mappedResponse =
                RealEstatePropertyResponse.builder()
                        .id(propertyId)
                        .userId(userId)
                        .propertyType(PropertyType.RESIDENTIAL)
                        .currency("USD")
                        .purchaseDate(purchaseDate)
                        .mortgageId(10L)
                        .isActive(true)
                        .purchasePrice(BigDecimal.ZERO) // Will be set by decryption
                        .currentValue(BigDecimal.ZERO) // Will be set by decryption
                        .build();

        // Mock mortgage validation
        Liability mortgage = new Liability();
        mortgage.setId(10L);
        mortgage.setUserId(userId);
        when(liabilityRepository.existsByIdAndUserId(10L, userId)).thenReturn(true);

        when(realEstateRepository.findByIdAndUserId(propertyId, userId))
                .thenReturn(Optional.of(existing));
        when(encryptionService.encrypt("Downtown Apartment Updated", testKey))
                .thenReturn("enc-new-name");
        when(encryptionService.encrypt("123 Main St, New York, NY 10001", testKey))
                .thenReturn("enc-new-address");
        when(encryptionService.encrypt("500000.00", testKey)).thenReturn("enc-new-purchase-price");
        when(encryptionService.encrypt("650000.00", testKey)).thenReturn("enc-new-current-value");
        when(encryptionService.encrypt("3200.00", testKey)).thenReturn("enc-new-rental-income");
        when(encryptionService.encrypt("Updated notes", testKey)).thenReturn("enc-new-notes");
        when(realEstateRepository.save(any(RealEstateProperty.class))).thenReturn(updated);
        when(realEstateMapper.toResponse(updated)).thenReturn(mappedResponse);

        // Mock decryption for old values (before snapshot)
        when(encryptionService.decrypt(eq("enc-old-name"), eq(testKey))).thenReturn("Old Name");
        when(encryptionService.decrypt(eq("enc-old-address"), eq(testKey)))
                .thenReturn("Old Address");
        when(encryptionService.decrypt(eq("enc-old-purchase-price"), eq(testKey)))
                .thenReturn("400000.00");
        when(encryptionService.decrypt(eq("enc-old-current-value"), eq(testKey)))
                .thenReturn("500000.00");

        // Mock decryption for new values
        when(encryptionService.decrypt(eq("enc-new-name"), eq(testKey)))
                .thenReturn("Downtown Apartment Updated");
        when(encryptionService.decrypt(eq("enc-new-address"), eq(testKey)))
                .thenReturn("123 Main St, New York, NY 10001");
        when(encryptionService.decrypt(eq("enc-new-purchase-price"), eq(testKey)))
                .thenReturn("500000.00");
        when(encryptionService.decrypt(eq("enc-new-current-value"), eq(testKey)))
                .thenReturn("650000.00");
        when(encryptionService.decrypt(eq("enc-new-rental-income"), eq(testKey)))
                .thenReturn("3200.00");
        when(encryptionService.decrypt(eq("enc-new-notes"), eq(testKey)))
                .thenReturn("Updated notes");
        // Handle null fields
        when(encryptionService.decrypt(eq(null), eq(testKey))).thenReturn(null);

        // Act
        RealEstatePropertyResponse result =
                realEstateService.updateProperty(userId, propertyId, request, testKey);

        // Assert
        assertThat(result).isNotNull();
        verify(realEstateRepository).findByIdAndUserId(propertyId, userId);
        verify(realEstateMapper).updateEntityFromRequest(request, existing);
        verify(realEstateRepository).save(existing);
    }

    @Test
    @DisplayName("Should throw exception when updating non-existent property")
    void shouldThrowExceptionWhenUpdatingNonExistentProperty() {
        // Arrange
        RealEstatePropertyRequest request =
                RealEstatePropertyRequest.builder()
                        .name("Downtown Apartment")
                        .propertyType(PropertyType.RESIDENTIAL)
                        .address("123 Main St")
                        .purchasePrice(new BigDecimal("500000.00"))
                        .currentValue(new BigDecimal("600000.00"))
                        .currency("USD")
                        .purchaseDate(purchaseDate)
                        .build();

        when(realEstateRepository.findByIdAndUserId(99L, userId)).thenReturn(Optional.empty());

        // Act & Assert
        assertThatThrownBy(() -> realEstateService.updateProperty(userId, 99L, request, testKey))
                .isInstanceOf(RealEstatePropertyNotFoundException.class)
                .hasMessageContaining("property not found");

        verify(realEstateRepository, never()).save(any());
    }

    // ========== DELETE PROPERTY TESTS (SOFT DELETE) ==========

    @Test
    @DisplayName("Should soft delete property successfully")
    void shouldSoftDeletePropertySuccessfully() {
        // Arrange
        RealEstateProperty existing =
                RealEstateProperty.builder()
                        .id(propertyId)
                        .userId(userId)
                        .name("enc-name")
                        .purchasePrice("enc-500000")
                        .currentValue("enc-600000")
                        .isActive(true)
                        .build();

        RealEstatePropertyResponse mappedResponse =
                RealEstatePropertyResponse.builder()
                        .id(propertyId)
                        .userId(userId)
                        .name("Property Name")
                        .isActive(true)
                        .build();

        when(realEstateRepository.findByIdAndUserId(propertyId, userId))
                .thenReturn(Optional.of(existing));
        when(realEstateMapper.toResponse(existing)).thenReturn(mappedResponse);
        when(encryptionService.decrypt(eq("enc-name"), eq(testKey))).thenReturn("Property Name");
        when(encryptionService.decrypt(eq("enc-500000"), eq(testKey))).thenReturn("500000");
        when(encryptionService.decrypt(eq("enc-600000"), eq(testKey))).thenReturn("600000");

        // Act
        realEstateService.deleteProperty(userId, propertyId, testKey);

        // Assert
        assertThat(existing.isActive()).isFalse();
        verify(realEstateRepository).findByIdAndUserId(propertyId, userId);
        verify(realEstateRepository).save(existing);
    }

    @Test
    @DisplayName("Should throw exception when deleting non-existent property")
    void shouldThrowExceptionWhenDeletingNonExistentProperty() {
        // Arrange
        when(realEstateRepository.findByIdAndUserId(99L, userId)).thenReturn(Optional.empty());

        // Act & Assert
        assertThatThrownBy(() -> realEstateService.deleteProperty(userId, 99L, testKey))
                .isInstanceOf(RealEstatePropertyNotFoundException.class);

        verify(realEstateRepository, never()).save(any());
    }

    // ========== GET PROPERTY TESTS ==========

    @Test
    @DisplayName("Should get property by ID and decrypt fields")
    void shouldGetPropertyByIdAndDecrypt() {
        // Arrange
        RealEstateProperty property =
                RealEstateProperty.builder()
                        .id(propertyId)
                        .userId(userId)
                        .name("enc-name")
                        .propertyType(PropertyType.COMMERCIAL)
                        .address("enc-address")
                        .purchasePrice("enc-purchase-price")
                        .currentValue("enc-current-value")
                        .rentalIncome("enc-rental-income")
                        .purchaseDate(purchaseDate)
                        .currency("USD")
                        .mortgageId(10L)
                        .latitude(new BigDecimal("40.7128"))
                        .longitude(new BigDecimal("-74.0060"))
                        .notes("enc-notes")
                        .documents("enc-documents")
                        .isActive(true)
                        .build();

        RealEstatePropertyResponse mappedResponse =
                RealEstatePropertyResponse.builder()
                        .id(propertyId)
                        .userId(userId)
                        .propertyType(PropertyType.COMMERCIAL)
                        .currency("USD")
                        .purchaseDate(purchaseDate)
                        .mortgageId(10L)
                        .latitude(new BigDecimal("40.7128"))
                        .longitude(new BigDecimal("-74.0060"))
                        .isActive(true)
                        .build();

        when(realEstateRepository.findByIdAndUserIdWithMortgage(propertyId, userId))
                .thenReturn(Optional.of(property));
        when(realEstateMapper.toResponse(property)).thenReturn(mappedResponse);
        when(encryptionService.decrypt("enc-name", testKey)).thenReturn("Office Building");
        when(encryptionService.decrypt("enc-address", testKey)).thenReturn("456 Business Ave");
        when(encryptionService.decrypt("enc-purchase-price", testKey)).thenReturn("1000000.00");
        when(encryptionService.decrypt("enc-current-value", testKey)).thenReturn("1200000.00");
        when(encryptionService.decrypt("enc-rental-income", testKey)).thenReturn("8000.00");
        when(encryptionService.decrypt("enc-notes", testKey)).thenReturn("Commercial space");
        when(encryptionService.decrypt("enc-documents", testKey)).thenReturn("lease.pdf");

        // Act
        RealEstatePropertyResponse result =
                realEstateService.getPropertyById(userId, propertyId, testKey);

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(propertyId);
        assertThat(result.getName()).isEqualTo("Office Building");
        assertThat(result.getAddress()).isEqualTo("456 Business Ave");
        assertThat(result.getPurchasePrice()).isEqualTo(new BigDecimal("1000000.00"));
        assertThat(result.getCurrentValue()).isEqualTo(new BigDecimal("1200000.00"));
        assertThat(result.getRentalIncome()).isEqualTo(new BigDecimal("8000.00"));

        verify(encryptionService, times(7)).decrypt(any(String.class), eq(testKey));
    }

    @Test
    @DisplayName("Should throw exception when property not found")
    void shouldThrowExceptionWhenPropertyNotFound() {
        // Arrange
        when(realEstateRepository.findByIdAndUserIdWithMortgage(99L, userId))
                .thenReturn(Optional.empty());

        // Act & Assert
        assertThatThrownBy(() -> realEstateService.getPropertyById(userId, 99L, testKey))
                .isInstanceOf(RealEstatePropertyNotFoundException.class)
                .hasMessageContaining("property not found");
    }

    // ========== GET PROPERTIES LIST TESTS ==========

    @Test
    @DisplayName("Should get all properties for user")
    void shouldGetAllPropertiesForUser() {
        // Arrange
        RealEstateProperty property1 =
                RealEstateProperty.builder()
                        .id(1L)
                        .userId(userId)
                        .name("enc-name1")
                        .propertyType(PropertyType.RESIDENTIAL)
                        .address("enc-address1")
                        .purchasePrice("enc-price1")
                        .currentValue("enc-value1")
                        .currency("USD")
                        .purchaseDate(purchaseDate)
                        .isActive(true)
                        .build();

        RealEstateProperty property2 =
                RealEstateProperty.builder()
                        .id(2L)
                        .userId(userId)
                        .name("enc-name2")
                        .propertyType(PropertyType.COMMERCIAL)
                        .address("enc-address2")
                        .purchasePrice("enc-price2")
                        .currentValue("enc-value2")
                        .currency("USD")
                        .purchaseDate(purchaseDate)
                        .isActive(true)
                        .build();

        RealEstatePropertyResponse response1 =
                RealEstatePropertyResponse.builder()
                        .id(1L)
                        .userId(userId)
                        .propertyType(PropertyType.RESIDENTIAL)
                        .currency("USD")
                        .purchaseDate(purchaseDate)
                        .isActive(true)
                        .build();

        RealEstatePropertyResponse response2 =
                RealEstatePropertyResponse.builder()
                        .id(2L)
                        .userId(userId)
                        .propertyType(PropertyType.COMMERCIAL)
                        .currency("USD")
                        .purchaseDate(purchaseDate)
                        .isActive(true)
                        .build();

        when(realEstateRepository.findByUserIdAndIsActive(userId, true))
                .thenReturn(List.of(property1, property2));
        when(realEstateMapper.toResponse(property1)).thenReturn(response1);
        when(realEstateMapper.toResponse(property2)).thenReturn(response2);

        // Mock decryption for property1
        when(encryptionService.decrypt("enc-name1", testKey)).thenReturn("Residential Property");
        when(encryptionService.decrypt("enc-address1", testKey)).thenReturn("123 Main St");
        when(encryptionService.decrypt("enc-price1", testKey)).thenReturn("400000.00");
        when(encryptionService.decrypt("enc-value1", testKey)).thenReturn("500000.00");

        // Mock decryption for property2
        when(encryptionService.decrypt("enc-name2", testKey)).thenReturn("Commercial Property");
        when(encryptionService.decrypt("enc-address2", testKey)).thenReturn("456 Business Ave");
        when(encryptionService.decrypt("enc-price2", testKey)).thenReturn("800000.00");
        when(encryptionService.decrypt("enc-value2", testKey)).thenReturn("900000.00");

        // Act
        List<RealEstatePropertyResponse> results =
                realEstateService.getPropertiesByUserId(userId, null, false, testKey);

        // Assert
        assertThat(results).hasSize(2);
        verify(realEstateRepository).findByUserIdAndIsActive(userId, true);
    }

    @Test
    @DisplayName("Should filter properties by type")
    void shouldFilterPropertiesByType() {
        // Arrange
        RealEstateProperty property1 =
                RealEstateProperty.builder()
                        .id(1L)
                        .userId(userId)
                        .name("enc-name1")
                        .propertyType(PropertyType.RESIDENTIAL)
                        .address("enc-address1")
                        .purchasePrice("enc-price1")
                        .currentValue("enc-value1")
                        .currency("USD")
                        .purchaseDate(purchaseDate)
                        .isActive(true)
                        .build();

        RealEstatePropertyResponse response1 =
                RealEstatePropertyResponse.builder()
                        .id(1L)
                        .userId(userId)
                        .propertyType(PropertyType.RESIDENTIAL)
                        .currency("USD")
                        .purchaseDate(purchaseDate)
                        .isActive(true)
                        .build();

        when(realEstateRepository.findByUserIdAndPropertyTypeAndIsActive(
                        userId, PropertyType.RESIDENTIAL, true))
                .thenReturn(List.of(property1));
        when(realEstateMapper.toResponse(property1)).thenReturn(response1);

        // Mock decryption for property1
        when(encryptionService.decrypt("enc-name1", testKey)).thenReturn("Residential Property");
        when(encryptionService.decrypt("enc-address1", testKey)).thenReturn("123 Main St");
        when(encryptionService.decrypt("enc-price1", testKey)).thenReturn("400000.00");
        when(encryptionService.decrypt("enc-value1", testKey)).thenReturn("500000.00");

        // Act
        List<RealEstatePropertyResponse> results =
                realEstateService.getPropertiesByUserId(
                        userId, PropertyType.RESIDENTIAL, false, testKey);

        // Assert
        assertThat(results).hasSize(1);
        verify(realEstateRepository)
                .findByUserIdAndPropertyTypeAndIsActive(userId, PropertyType.RESIDENTIAL, true);
    }

    @Test
    @DisplayName("Should filter active properties only")
    void shouldFilterActivePropertiesOnly() {
        // Arrange
        RealEstateProperty property1 =
                RealEstateProperty.builder()
                        .id(1L)
                        .userId(userId)
                        .name("enc-name1")
                        .propertyType(PropertyType.RESIDENTIAL)
                        .address("enc-address1")
                        .purchasePrice("enc-price1")
                        .currentValue("enc-value1")
                        .currency("USD")
                        .purchaseDate(purchaseDate)
                        .isActive(true)
                        .build();

        RealEstatePropertyResponse response1 =
                RealEstatePropertyResponse.builder()
                        .id(1L)
                        .userId(userId)
                        .propertyType(PropertyType.RESIDENTIAL)
                        .currency("USD")
                        .purchaseDate(purchaseDate)
                        .isActive(true)
                        .build();

        // Test with includeInactive=false (should call findByUserIdAndIsActive)
        when(realEstateRepository.findByUserIdAndIsActive(userId, true))
                .thenReturn(List.of(property1));
        when(realEstateMapper.toResponse(property1)).thenReturn(response1);

        // Mock decryption for property1
        when(encryptionService.decrypt("enc-name1", testKey)).thenReturn("Active Property");
        when(encryptionService.decrypt("enc-address1", testKey)).thenReturn("123 Main St");
        when(encryptionService.decrypt("enc-price1", testKey)).thenReturn("400000.00");
        when(encryptionService.decrypt("enc-value1", testKey)).thenReturn("500000.00");

        // Act - passing includeInactive=false to filter only active properties
        List<RealEstatePropertyResponse> results =
                realEstateService.getPropertiesByUserId(userId, null, false, testKey);

        // Assert
        assertThat(results).hasSize(1);
        verify(realEstateRepository).findByUserIdAndIsActive(userId, true);
    }

    // ========== EQUITY CALCULATION TESTS ==========

    @Test
    @DisplayName("Should calculate equity with mortgage correctly")
    void shouldCalculateEquityWithMortgage() {
        // Arrange
        Liability mortgage = new Liability();
        mortgage.setId(10L);
        mortgage.setUserId(userId);
        mortgage.setCurrentBalance("enc-mortgage-balance");
        mortgage.setCurrency("USD");

        RealEstateProperty property =
                RealEstateProperty.builder()
                        .id(propertyId)
                        .userId(userId)
                        .name("enc-name")
                        .propertyType(PropertyType.RESIDENTIAL)
                        .currentValue("enc-current-value")
                        .purchasePrice("enc-purchase-price")
                        .currency("USD")
                        .mortgageId(10L)
                        .mortgage(mortgage)
                        .isActive(true)
                        .build();

        when(realEstateRepository.findByIdAndUserIdWithMortgage(propertyId, userId))
                .thenReturn(Optional.of(property));
        when(encryptionService.decrypt("enc-current-value", testKey)).thenReturn("600000.00");
        when(encryptionService.decrypt("enc-purchase-price", testKey)).thenReturn("500000.00");
        when(encryptionService.decrypt("enc-mortgage-balance", testKey)).thenReturn("350000.00");

        // Act
        PropertyEquityResponse equity =
                realEstateService.calculateEquity(userId, propertyId, testKey);

        // Assert
        assertThat(equity).isNotNull();
        assertThat(equity.getPropertyId()).isEqualTo(propertyId);
        assertThat(equity.getCurrentValue()).isEqualTo(new BigDecimal("600000.00"));
        assertThat(equity.getMortgageBalance()).isEqualTo(new BigDecimal("350000.00"));
        assertThat(equity.getEquity()).isEqualTo(new BigDecimal("250000.00")); // 600000 - 350000
        assertThat(equity.getEquityPercentage())
                .isEqualByComparingTo(new BigDecimal("41.67")); // (250000/600000)*100
        assertThat(equity.getLoanToValueRatio())
                .isEqualByComparingTo(new BigDecimal("58.33")); // (350000/600000)*100
    }

    @Test
    @DisplayName("Should calculate equity without mortgage correctly")
    void shouldCalculateEquityWithoutMortgage() {
        // Arrange
        RealEstateProperty property =
                RealEstateProperty.builder()
                        .id(propertyId)
                        .userId(userId)
                        .name("enc-name")
                        .propertyType(PropertyType.RESIDENTIAL)
                        .currentValue("enc-current-value")
                        .purchasePrice("enc-purchase-price")
                        .currency("USD")
                        .mortgageId(null) // No mortgage
                        .mortgage(null)
                        .isActive(true)
                        .build();

        when(realEstateRepository.findByIdAndUserIdWithMortgage(propertyId, userId))
                .thenReturn(Optional.of(property));
        when(encryptionService.decrypt("enc-current-value", testKey)).thenReturn("600000.00");
        when(encryptionService.decrypt("enc-purchase-price", testKey)).thenReturn("500000.00");

        // Act
        PropertyEquityResponse equity =
                realEstateService.calculateEquity(userId, propertyId, testKey);

        // Assert
        assertThat(equity).isNotNull();
        assertThat(equity.getCurrentValue()).isEqualTo(new BigDecimal("600000.00"));
        assertThat(equity.getMortgageBalance()).isEqualTo(BigDecimal.ZERO);
        assertThat(equity.getEquity())
                .isEqualTo(new BigDecimal("600000.00")); // Full value = equity
        assertThat(equity.getEquityPercentage()).isEqualByComparingTo(new BigDecimal("100.00"));
        assertThat(equity.getLoanToValueRatio()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    // ========== ROI CALCULATION TESTS ==========

    @Test
    @DisplayName("Should calculate ROI with rental income correctly")
    void shouldCalculateROIWithRentalIncome() {
        // Arrange
        LocalDate today = LocalDate.now();
        int yearsOwned = today.getYear() - purchaseDate.getYear();

        RealEstateProperty property =
                RealEstateProperty.builder()
                        .id(propertyId)
                        .userId(userId)
                        .name("enc-name")
                        .propertyType(PropertyType.RESIDENTIAL)
                        .currentValue("enc-current-value")
                        .purchasePrice("enc-purchase-price")
                        .rentalIncome("enc-rental-income")
                        .purchaseDate(purchaseDate)
                        .currency("USD")
                        .isActive(true)
                        .build();

        when(realEstateRepository.findByIdAndUserId(propertyId, userId))
                .thenReturn(Optional.of(property));
        when(encryptionService.decrypt("enc-name", testKey)).thenReturn("Property Name");
        when(encryptionService.decrypt("enc-current-value", testKey)).thenReturn("650000.00");
        when(encryptionService.decrypt("enc-purchase-price", testKey)).thenReturn("500000.00");
        when(encryptionService.decrypt("enc-rental-income", testKey)).thenReturn("3000.00");

        // Act
        PropertyROIResponse roi = realEstateService.calculateROI(userId, propertyId, testKey);

        // Assert
        assertThat(roi).isNotNull();
        assertThat(roi.getPropertyId()).isEqualTo(propertyId);
        assertThat(roi.getPurchasePrice()).isEqualTo(new BigDecimal("500000.00"));
        assertThat(roi.getCurrentValue()).isEqualTo(new BigDecimal("650000.00"));
        assertThat(roi.getAppreciation()).isEqualTo(new BigDecimal("150000.00")); // 650000 - 500000
        assertThat(roi.getMonthlyRentalIncome()).isEqualTo(new BigDecimal("3000.00"));
        assertThat(roi.getYearsOwned()).isEqualTo(yearsOwned);

        long monthsOwned = ChronoUnit.MONTHS.between(purchaseDate, today);

        // Total rental income = 3000 * monthsOwned
        BigDecimal expectedTotalRental =
                new BigDecimal("3000.00").multiply(new BigDecimal(monthsOwned));
        assertThat(roi.getTotalRentalIncome()).isEqualByComparingTo(expectedTotalRental);
    }

    @Test
    @DisplayName("Should calculate ROI without rental income correctly")
    void shouldCalculateROIWithoutRentalIncome() {
        // Arrange
        LocalDate today = LocalDate.now();
        int yearsOwned = today.getYear() - purchaseDate.getYear();

        RealEstateProperty property =
                RealEstateProperty.builder()
                        .id(propertyId)
                        .userId(userId)
                        .name("enc-name")
                        .propertyType(PropertyType.LAND)
                        .currentValue("enc-current-value")
                        .purchasePrice("enc-purchase-price")
                        .rentalIncome(null) // No rental income
                        .purchaseDate(purchaseDate)
                        .currency("USD")
                        .isActive(true)
                        .build();

        when(realEstateRepository.findByIdAndUserId(propertyId, userId))
                .thenReturn(Optional.of(property));
        when(encryptionService.decrypt("enc-name", testKey)).thenReturn("Land Parcel");
        when(encryptionService.decrypt("enc-current-value", testKey)).thenReturn("75000.00");
        when(encryptionService.decrypt("enc-purchase-price", testKey)).thenReturn("50000.00");

        // Act
        PropertyROIResponse roi = realEstateService.calculateROI(userId, propertyId, testKey);

        // Assert
        assertThat(roi).isNotNull();
        assertThat(roi.getPurchasePrice()).isEqualTo(new BigDecimal("50000.00"));
        assertThat(roi.getCurrentValue()).isEqualTo(new BigDecimal("75000.00"));
        assertThat(roi.getAppreciation()).isEqualTo(new BigDecimal("25000.00"));
        assertThat(roi.getMonthlyRentalIncome()).isNull();
        assertThat(roi.getTotalRentalIncome()).isNull();
        // TotalROI = ((currentValue - purchasePrice + totalRental) / purchasePrice) *
        // 100
        // = ((75000 - 50000 + 0) / 50000) * 100 = 50.00%
        assertThat(roi.getTotalROI()).isEqualByComparingTo(new BigDecimal("50.00"));
    }

    // ========== VALUE ESTIMATION TESTS ==========

    @Test
    @DisplayName("Should update property value successfully")
    void shouldUpdatePropertyValueSuccessfully() {
        // Arrange
        BigDecimal newValue = new BigDecimal("680000.00");

        RealEstateProperty property =
                RealEstateProperty.builder()
                        .id(propertyId)
                        .userId(userId)
                        .name("enc-name")
                        .propertyType(PropertyType.RESIDENTIAL)
                        .currentValue("enc-old-value")
                        .purchasePrice("enc-purchase-price")
                        .currency("USD")
                        .isActive(true)
                        .build();

        RealEstateProperty updated =
                RealEstateProperty.builder()
                        .id(propertyId)
                        .userId(userId)
                        .name("enc-name")
                        .propertyType(PropertyType.RESIDENTIAL)
                        .currentValue("enc-new-value")
                        .purchasePrice("enc-purchase-price")
                        .currency("USD")
                        .isActive(true)
                        .updatedAt(LocalDateTime.now())
                        .build();

        RealEstatePropertyResponse mappedResponse =
                RealEstatePropertyResponse.builder()
                        .id(propertyId)
                        .userId(userId)
                        .propertyType(PropertyType.RESIDENTIAL)
                        .currency("USD")
                        .isActive(true)
                        .build();

        when(realEstateRepository.findByIdAndUserId(propertyId, userId))
                .thenReturn(Optional.of(property));
        when(encryptionService.encrypt("680000.00", testKey)).thenReturn("enc-new-value");
        when(realEstateRepository.save(property)).thenReturn(updated);
        when(realEstateMapper.toResponse(updated)).thenReturn(mappedResponse);
        when(encryptionService.decrypt("enc-name", testKey)).thenReturn("Property Name");
        when(encryptionService.decrypt("enc-purchase-price", testKey)).thenReturn("600000.00");
        when(encryptionService.decrypt("enc-new-value", testKey)).thenReturn("680000.00");

        // Act
        RealEstatePropertyResponse result =
                realEstateService.estimateValue(userId, propertyId, newValue, testKey);

        // Assert
        assertThat(result).isNotNull();
        verify(encryptionService).encrypt("680000.00", testKey);
        verify(realEstateRepository).save(property);
    }

    @Test
    @DisplayName("Should validate new value is positive")
    void shouldValidateNewValueIsPositive() {
        // Arrange
        BigDecimal negativeValue = new BigDecimal("-100.00");

        // Act & Assert
        assertThatThrownBy(
                        () ->
                                realEstateService.estimateValue(
                                        userId, propertyId, negativeValue, testKey))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("New value must be non-negative");

        verify(realEstateRepository, never()).save(any());
    }

    // ========== AUTHORIZATION TESTS ==========

    @Test
    @DisplayName("Should enforce user authorization on property access")
    void shouldEnforceUserAuthorizationOnPropertyAccess() {
        // Arrange
        Long unauthorizedUserId = 999L;

        when(realEstateRepository.findByIdAndUserIdWithMortgage(propertyId, unauthorizedUserId))
                .thenReturn(Optional.empty());

        // Act & Assert
        assertThatThrownBy(
                        () ->
                                realEstateService.getPropertyById(
                                        propertyId, unauthorizedUserId, testKey))
                .isInstanceOf(RealEstatePropertyNotFoundException.class)
                .hasMessageContaining("property not found");

        // Verify the authorization check used correct user ID
        verify(realEstateRepository).findByIdAndUserIdWithMortgage(propertyId, unauthorizedUserId);
    }

    // Secondary currency conversion tests (REQ-4.4, REQ-4.5, REQ-4.6)

    @Test
    @DisplayName(
            "Should populate secondary currency fields when secondary currency is configured and differs from native")
    void shouldPopulateSecondaryCurrencyFieldsWhenConfigured() {
        // Arrange — native=EUR, base=USD, secondary=JPY
        RealEstateProperty property =
                RealEstateProperty.builder()
                        .id(propertyId)
                        .userId(userId)
                        .name("enc-name")
                        .propertyType(PropertyType.RESIDENTIAL)
                        .currentValue("enc-current-value")
                        .purchasePrice("enc-purchase-price")
                        .currency("EUR")
                        .isActive(true)
                        .build();

        RealEstatePropertyResponse mappedResponse =
                RealEstatePropertyResponse.builder()
                        .id(propertyId)
                        .userId(userId)
                        .propertyType(PropertyType.RESIDENTIAL)
                        .currency("EUR")
                        .isActive(true)
                        .build();

        User user = User.builder().baseCurrency("USD").secondaryCurrency("JPY").build();

        when(realEstateRepository.findByIdAndUserIdWithMortgage(propertyId, userId))
                .thenReturn(Optional.of(property));
        when(realEstateMapper.toResponse(property)).thenReturn(mappedResponse);
        when(encryptionService.decrypt("enc-name", testKey)).thenReturn("My Property");
        when(encryptionService.decrypt("enc-purchase-price", testKey)).thenReturn("400000.00");
        when(encryptionService.decrypt("enc-current-value", testKey)).thenReturn("500000.00");
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(exchangeRateService.getExchangeRate("EUR", "USD", null))
                .thenReturn(new BigDecimal("1.1"));
        when(exchangeRateService.convert(new BigDecimal("500000.00"), "EUR", "USD"))
                .thenReturn(new BigDecimal("550000.00"));
        when(exchangeRateService.getExchangeRate("EUR", "JPY", null))
                .thenReturn(new BigDecimal("155.0"));
        when(exchangeRateService.convert(new BigDecimal("500000.00"), "EUR", "JPY"))
                .thenReturn(new BigDecimal("77500000.00"));

        // Act
        RealEstatePropertyResponse result =
                realEstateService.getPropertyById(propertyId, userId, testKey);

        // Assert — Requirement REQ-4.4: secondary fields populated
        assertThat(result.getValueInSecondaryCurrency())
                .isEqualByComparingTo(new BigDecimal("77500000.00"));
        assertThat(result.getSecondaryCurrency()).isEqualTo("JPY");
        assertThat(result.getSecondaryExchangeRate()).isEqualByComparingTo(new BigDecimal("155.0"));
        // Base conversion also works
        assertThat(result.getIsConverted()).isTrue();
        assertThat(result.getValueInBaseCurrency())
                .isEqualByComparingTo(new BigDecimal("550000.00"));
    }

    @Test
    @DisplayName(
            "Should set secondary fields to null when native currency equals secondary currency")
    void shouldSkipSecondaryCurrencyConversionWhenNativeEqualsSecondary() {
        // Arrange — native=EUR, base=USD, secondary=EUR (same as native)
        RealEstateProperty property =
                RealEstateProperty.builder()
                        .id(propertyId)
                        .userId(userId)
                        .name("enc-name")
                        .propertyType(PropertyType.RESIDENTIAL)
                        .currentValue("enc-current-value")
                        .purchasePrice("enc-purchase-price")
                        .currency("EUR")
                        .isActive(true)
                        .build();

        RealEstatePropertyResponse mappedResponse =
                RealEstatePropertyResponse.builder()
                        .id(propertyId)
                        .userId(userId)
                        .propertyType(PropertyType.RESIDENTIAL)
                        .currency("EUR")
                        .isActive(true)
                        .build();

        User user = User.builder().baseCurrency("USD").secondaryCurrency("EUR").build();

        when(realEstateRepository.findByIdAndUserIdWithMortgage(propertyId, userId))
                .thenReturn(Optional.of(property));
        when(realEstateMapper.toResponse(property)).thenReturn(mappedResponse);
        when(encryptionService.decrypt("enc-name", testKey)).thenReturn("My Property");
        when(encryptionService.decrypt("enc-purchase-price", testKey)).thenReturn("400000.00");
        when(encryptionService.decrypt("enc-current-value", testKey)).thenReturn("500000.00");
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(exchangeRateService.getExchangeRate("EUR", "USD", null))
                .thenReturn(new BigDecimal("1.1"));
        when(exchangeRateService.convert(new BigDecimal("500000.00"), "EUR", "USD"))
                .thenReturn(new BigDecimal("550000.00"));

        // Act
        RealEstatePropertyResponse result =
                realEstateService.getPropertyById(propertyId, userId, testKey);

        // Assert — Requirement REQ-4.6: no redundant conversion when native equals
        // secondary
        assertThat(result.getValueInSecondaryCurrency()).isNull();
        assertThat(result.getSecondaryCurrency()).isEqualTo("EUR");
        assertThat(result.getSecondaryExchangeRate()).isNull();
        // Base conversion is unaffected
        assertThat(result.getIsConverted()).isTrue();
        assertThat(result.getValueInBaseCurrency())
                .isEqualByComparingTo(new BigDecimal("550000.00"));
        // Verify exchange rate service was NOT called for secondary conversion
        verify(exchangeRateService, never()).convert(new BigDecimal("500000.00"), "EUR", "EUR");
    }

    @Test
    @DisplayName(
            "Should leave valueInSecondaryCurrency null but set secondaryCurrency when secondary rate is unavailable")
    void shouldLeaveSecondaryAmountNullWhenSecondaryRateUnavailable() {
        // Arrange — native=EUR, base=USD, secondary=JPY — JPY rate throws
        RealEstateProperty property =
                RealEstateProperty.builder()
                        .id(propertyId)
                        .userId(userId)
                        .name("enc-name")
                        .propertyType(PropertyType.RESIDENTIAL)
                        .currentValue("enc-current-value")
                        .purchasePrice("enc-purchase-price")
                        .currency("EUR")
                        .isActive(true)
                        .build();

        RealEstatePropertyResponse mappedResponse =
                RealEstatePropertyResponse.builder()
                        .id(propertyId)
                        .userId(userId)
                        .propertyType(PropertyType.RESIDENTIAL)
                        .currency("EUR")
                        .isActive(true)
                        .build();

        User user = User.builder().baseCurrency("USD").secondaryCurrency("JPY").build();

        when(realEstateRepository.findByIdAndUserIdWithMortgage(propertyId, userId))
                .thenReturn(Optional.of(property));
        when(realEstateMapper.toResponse(property)).thenReturn(mappedResponse);
        when(encryptionService.decrypt("enc-name", testKey)).thenReturn("My Property");
        when(encryptionService.decrypt("enc-purchase-price", testKey)).thenReturn("400000.00");
        when(encryptionService.decrypt("enc-current-value", testKey)).thenReturn("500000.00");
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(exchangeRateService.getExchangeRate("EUR", "USD", null))
                .thenReturn(new BigDecimal("1.1"));
        when(exchangeRateService.convert(new BigDecimal("500000.00"), "EUR", "USD"))
                .thenReturn(new BigDecimal("550000.00"));
        when(exchangeRateService.convert(new BigDecimal("500000.00"), "EUR", "JPY"))
                .thenThrow(new RuntimeException("JPY rate unavailable"));

        // Act — must not throw
        RealEstatePropertyResponse result =
                realEstateService.getPropertyById(propertyId, userId, testKey);

        // Assert — Requirement REQ-4.5: graceful fallback; amount null, currency code
        // still set
        assertThat(result.getValueInSecondaryCurrency()).isNull();
        assertThat(result.getSecondaryCurrency()).isEqualTo("JPY");
        // Base conversion is unaffected
        assertThat(result.getIsConverted()).isTrue();
        assertThat(result.getValueInBaseCurrency())
                .isEqualByComparingTo(new BigDecimal("550000.00"));
    }

    @Test
    @DisplayName("Should leave all secondary fields null when no secondary currency is configured")
    void shouldLeaveSecondaryFieldsNullWhenNoSecondaryCurrencyConfigured() {
        // Arrange — native=EUR, base=USD, secondary=null
        RealEstateProperty property =
                RealEstateProperty.builder()
                        .id(propertyId)
                        .userId(userId)
                        .name("enc-name")
                        .propertyType(PropertyType.RESIDENTIAL)
                        .currentValue("enc-current-value")
                        .purchasePrice("enc-purchase-price")
                        .currency("EUR")
                        .isActive(true)
                        .build();

        RealEstatePropertyResponse mappedResponse =
                RealEstatePropertyResponse.builder()
                        .id(propertyId)
                        .userId(userId)
                        .propertyType(PropertyType.RESIDENTIAL)
                        .currency("EUR")
                        .isActive(true)
                        .build();

        User user =
                User.builder().baseCurrency("USD").build(); // secondaryCurrency is null by default

        when(realEstateRepository.findByIdAndUserIdWithMortgage(propertyId, userId))
                .thenReturn(Optional.of(property));
        when(realEstateMapper.toResponse(property)).thenReturn(mappedResponse);
        when(encryptionService.decrypt("enc-name", testKey)).thenReturn("My Property");
        when(encryptionService.decrypt("enc-purchase-price", testKey)).thenReturn("400000.00");
        when(encryptionService.decrypt("enc-current-value", testKey)).thenReturn("500000.00");
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(exchangeRateService.getExchangeRate("EUR", "USD", null))
                .thenReturn(new BigDecimal("1.1"));
        when(exchangeRateService.convert(new BigDecimal("500000.00"), "EUR", "USD"))
                .thenReturn(new BigDecimal("550000.00"));

        // Act
        RealEstatePropertyResponse result =
                realEstateService.getPropertyById(propertyId, userId, testKey);

        // Assert — Requirement REQ-4.4: all secondary fields must be null
        assertThat(result.getValueInSecondaryCurrency()).isNull();
        assertThat(result.getSecondaryCurrency()).isNull();
        assertThat(result.getSecondaryExchangeRate()).isNull();
        // Base conversion works normally
        assertThat(result.getIsConverted()).isTrue();
        assertThat(result.getValueInBaseCurrency())
                .isEqualByComparingTo(new BigDecimal("550000.00"));
    }
}
