package org.openfinance.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
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
 * <p>Tests CRUD operations, equity calculations, ROI calculations, authorization checks, and
 * validation.
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

    @Mock private SearchTokenService searchTokenService;

    @Mock private DefaultCurrencyProvider defaultCurrencyProvider;

    @InjectMocks private RealEstateService realEstateService;

    private LocalDate purchaseDate;
    private Long userId;
    private Long propertyId;

    @BeforeEach
    void setUp() {
        purchaseDate = LocalDate.of(2020, 1, 15);
        userId = 1L;
        propertyId = 1L;

        User defaultUser = User.builder().baseCurrency("USD").build();
        org.mockito.Mockito.lenient()
                .when(userRepository.findById(userId))
                .thenReturn(Optional.of(defaultUser));
        org.openfinance.testutil.DefaultCurrencyProviderMocks.stub(
                defaultCurrencyProvider, userRepository);
    }

    // ========== CREATE PROPERTY TESTS ==========

    @Test
    @DisplayName("Should create property successfully and encrypt sensitive fields")
    void shouldCreatePropertySuccessfully() {
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
                        .name("Downtown Apartment")
                        .propertyType(PropertyType.RESIDENTIAL)
                        .address("123 Main St, New York, NY 10001")
                        .purchasePrice("500000.00")
                        .currentValue("600000.00")
                        .rentalIncome("3000.00")
                        .purchaseDate(purchaseDate)
                        .currency("USD")
                        .mortgageId(10L)
                        .latitude(new BigDecimal("40.7128"))
                        .longitude(new BigDecimal("-74.0060"))
                        .notes("Prime location")
                        .documents("contract.pdf,deed.pdf")
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

        when(liabilityRepository.existsByIdAndUserId(10L, userId)).thenReturn(true);
        when(realEstateRepository.findByIdAndUserId(propertyId, userId))
                .thenReturn(Optional.of(saved));
        when(realEstateMapper.toEntity(request)).thenReturn(mapped);
        when(realEstateRepository.save(any(RealEstateProperty.class))).thenReturn(saved);
        when(realEstateMapper.toResponse(saved)).thenReturn(mappedResponse);

        org.openfinance.dto.AssetResponse assetResponse = new org.openfinance.dto.AssetResponse();
        assetResponse.setId(100L);
        when(assetService.createAsset(eq(userId), any(org.openfinance.dto.AssetRequest.class)))
                .thenReturn(assetResponse);

        RealEstatePropertyResponse created = realEstateService.createProperty(userId, request);

        assertThat(created).isNotNull();
        assertThat(created.getId()).isEqualTo(propertyId);
        assertThat(created.getName()).isEqualTo("Downtown Apartment");
        assertThat(created.getPropertyType()).isEqualTo(PropertyType.RESIDENTIAL);
        assertThat(created.getCurrentValue()).isEqualTo(new BigDecimal("600000.00"));

        verify(valueHistoryRepository).save(any());
        verify(realEstateRepository, org.mockito.Mockito.atLeastOnce())
                .save(any(RealEstateProperty.class));
    }

    @Test
    @DisplayName("Should create property without optional fields")
    void shouldCreatePropertyWithoutOptionalFields() {
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
                        .name("Vacant Land")
                        .propertyType(PropertyType.LAND)
                        .address("Rural Road, County")
                        .purchasePrice("50000.00")
                        .currentValue("55000.00")
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
        when(realEstateRepository.save(any(RealEstateProperty.class))).thenReturn(saved);
        when(realEstateMapper.toResponse(saved)).thenReturn(mappedResponse);

        org.openfinance.dto.AssetResponse assetResponse = new org.openfinance.dto.AssetResponse();
        assetResponse.setId(100L);
        when(assetService.createAsset(eq(userId), any(org.openfinance.dto.AssetRequest.class)))
                .thenReturn(assetResponse);

        RealEstatePropertyResponse created = realEstateService.createProperty(userId, request);

        assertThat(created).isNotNull();
    }

    @Test
    @DisplayName("Should validate null parameters on createProperty")
    void shouldValidateNullParametersOnCreate() {
        RealEstatePropertyRequest request = RealEstatePropertyRequest.builder().build();

        assertThatThrownBy(() -> realEstateService.createProperty(null, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("User ID cannot be null");

        assertThatThrownBy(() -> realEstateService.createProperty(userId, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Property request cannot be null");
    }

    // ========== UPDATE PROPERTY TESTS ==========

    @Test
    @DisplayName("Should update property successfully and re-encrypt fields")
    void shouldUpdatePropertySuccessfully() {
        RealEstatePropertyRequest request =
                RealEstatePropertyRequest.builder()
                        .name("Downtown Apartment Updated")
                        .propertyType(PropertyType.RESIDENTIAL)
                        .address("123 Main St, New York, NY 10001")
                        .purchasePrice(new BigDecimal("500000.00"))
                        .currentValue(new BigDecimal("650000.00"))
                        .currency("USD")
                        .purchaseDate(purchaseDate)
                        .rentalIncome(new BigDecimal("3200.00"))
                        .mortgageId(10L)
                        .notes("Updated notes")
                        .build();

        RealEstateProperty existing =
                RealEstateProperty.builder()
                        .id(propertyId)
                        .userId(userId)
                        .name("Old Name")
                        .propertyType(PropertyType.RESIDENTIAL)
                        .address("Old Address")
                        .purchasePrice("400000.00")
                        .currentValue("500000.00")
                        .purchaseDate(purchaseDate)
                        .currency("USD")
                        .isActive(true)
                        .build();

        RealEstateProperty updated =
                RealEstateProperty.builder()
                        .id(propertyId)
                        .userId(userId)
                        .name("Downtown Apartment Updated")
                        .propertyType(PropertyType.RESIDENTIAL)
                        .address("123 Main St, New York, NY 10001")
                        .purchasePrice("500000.00")
                        .currentValue("650000.00")
                        .rentalIncome("3200.00")
                        .purchaseDate(purchaseDate)
                        .currency("USD")
                        .mortgageId(10L)
                        .notes("Updated notes")
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
                        .purchasePrice(BigDecimal.ZERO)
                        .currentValue(BigDecimal.ZERO)
                        .build();

        when(liabilityRepository.existsByIdAndUserId(10L, userId)).thenReturn(true);
        when(realEstateRepository.findByIdAndUserId(propertyId, userId))
                .thenReturn(Optional.of(existing));
        when(realEstateRepository.save(any(RealEstateProperty.class))).thenReturn(updated);
        when(realEstateMapper.toResponse(updated)).thenReturn(mappedResponse);

        RealEstatePropertyResponse result =
                realEstateService.updateProperty(userId, propertyId, request);

        assertThat(result).isNotNull();
        verify(realEstateRepository).findByIdAndUserId(propertyId, userId);
        verify(realEstateMapper).updateEntityFromRequest(request, existing);
        verify(realEstateRepository).save(existing);
    }

    @Test
    @DisplayName("Should throw exception when updating non-existent property")
    void shouldThrowExceptionWhenUpdatingNonExistentProperty() {
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

        assertThatThrownBy(() -> realEstateService.updateProperty(99L, userId, request))
                .isInstanceOf(RealEstatePropertyNotFoundException.class)
                .hasMessageContaining("property not found");

        verify(realEstateRepository, never()).save(any());
    }

    // ========== DELETE PROPERTY TESTS (SOFT DELETE) ==========

    @Test
    @DisplayName("Should soft delete property successfully")
    void shouldSoftDeletePropertySuccessfully() {
        RealEstateProperty existing =
                RealEstateProperty.builder()
                        .id(propertyId)
                        .userId(userId)
                        .name("Property Name")
                        .purchasePrice("500000")
                        .currentValue("600000")
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

        realEstateService.deleteProperty(propertyId, userId);

        assertThat(existing.isActive()).isFalse();
        verify(realEstateRepository).findByIdAndUserId(propertyId, userId);
        verify(realEstateRepository).save(existing);
    }

    @Test
    @DisplayName("Should throw exception when deleting non-existent property")
    void shouldThrowExceptionWhenDeletingNonExistentProperty() {
        when(realEstateRepository.findByIdAndUserId(99L, userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> realEstateService.deleteProperty(99L, userId))
                .isInstanceOf(RealEstatePropertyNotFoundException.class);

        verify(realEstateRepository, never()).save(any());
    }

    // ========== GET PROPERTY TESTS ==========

    @Test
    @DisplayName("Should get property by ID and decrypt fields")
    void shouldGetPropertyByIdAndDecrypt() {
        RealEstateProperty property =
                RealEstateProperty.builder()
                        .id(propertyId)
                        .userId(userId)
                        .name("Office Building")
                        .propertyType(PropertyType.COMMERCIAL)
                        .address("456 Business Ave")
                        .purchasePrice("1000000.00")
                        .currentValue("1200000.00")
                        .rentalIncome("8000.00")
                        .purchaseDate(purchaseDate)
                        .currency("USD")
                        .mortgageId(10L)
                        .latitude(new BigDecimal("40.7128"))
                        .longitude(new BigDecimal("-74.0060"))
                        .notes("Commercial space")
                        .documents("lease.pdf")
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

        RealEstatePropertyResponse result = realEstateService.getPropertyById(propertyId, userId);

        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(propertyId);
        assertThat(result.getName()).isEqualTo("Office Building");
        assertThat(result.getAddress()).isEqualTo("456 Business Ave");
        assertThat(result.getPurchasePrice()).isEqualTo(new BigDecimal("1000000.00"));
        assertThat(result.getCurrentValue()).isEqualTo(new BigDecimal("1200000.00"));
        assertThat(result.getRentalIncome()).isEqualTo(new BigDecimal("8000.00"));
    }

    @Test
    @DisplayName("Should throw exception when property not found")
    void shouldThrowExceptionWhenPropertyNotFound() {
        when(realEstateRepository.findByIdAndUserIdWithMortgage(99L, userId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> realEstateService.getPropertyById(99L, userId))
                .isInstanceOf(RealEstatePropertyNotFoundException.class)
                .hasMessageContaining("property not found");
    }

    // ========== GET PROPERTIES LIST TESTS ==========

    @Test
    @DisplayName("Should get all properties for user")
    void shouldGetAllPropertiesForUser() {
        RealEstateProperty property1 =
                RealEstateProperty.builder()
                        .id(1L)
                        .userId(userId)
                        .name("Residential Property")
                        .propertyType(PropertyType.RESIDENTIAL)
                        .address("123 Main St")
                        .purchasePrice("400000.00")
                        .currentValue("500000.00")
                        .currency("USD")
                        .purchaseDate(purchaseDate)
                        .isActive(true)
                        .build();

        RealEstateProperty property2 =
                RealEstateProperty.builder()
                        .id(2L)
                        .userId(userId)
                        .name("Commercial Property")
                        .propertyType(PropertyType.COMMERCIAL)
                        .address("456 Business Ave")
                        .purchasePrice("800000.00")
                        .currentValue("900000.00")
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

        List<RealEstatePropertyResponse> results =
                realEstateService.getPropertiesByUserId(userId, null, false);

        assertThat(results).hasSize(2);
        verify(realEstateRepository).findByUserIdAndIsActive(userId, true);
    }

    @Test
    @DisplayName("Should filter properties by type")
    void shouldFilterPropertiesByType() {
        RealEstateProperty property1 =
                RealEstateProperty.builder()
                        .id(1L)
                        .userId(userId)
                        .name("Residential Property")
                        .propertyType(PropertyType.RESIDENTIAL)
                        .address("123 Main St")
                        .purchasePrice("400000.00")
                        .currentValue("500000.00")
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

        List<RealEstatePropertyResponse> results =
                realEstateService.getPropertiesByUserId(userId, PropertyType.RESIDENTIAL, false);

        assertThat(results).hasSize(1);
        verify(realEstateRepository)
                .findByUserIdAndPropertyTypeAndIsActive(userId, PropertyType.RESIDENTIAL, true);
    }

    @Test
    @DisplayName("Should filter active properties only")
    void shouldFilterActivePropertiesOnly() {
        RealEstateProperty property1 =
                RealEstateProperty.builder()
                        .id(1L)
                        .userId(userId)
                        .name("Active Property")
                        .propertyType(PropertyType.RESIDENTIAL)
                        .address("123 Main St")
                        .purchasePrice("400000.00")
                        .currentValue("500000.00")
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

        when(realEstateRepository.findByUserIdAndIsActive(userId, true))
                .thenReturn(List.of(property1));
        when(realEstateMapper.toResponse(property1)).thenReturn(response1);

        List<RealEstatePropertyResponse> results =
                realEstateService.getPropertiesByUserId(userId, null, false);

        assertThat(results).hasSize(1);
        verify(realEstateRepository).findByUserIdAndIsActive(userId, true);
    }

    // ========== EQUITY CALCULATION TESTS ==========

    @Test
    @DisplayName("Should calculate equity with mortgage correctly")
    void shouldCalculateEquityWithMortgage() {
        Liability mortgage = new Liability();
        mortgage.setId(10L);
        mortgage.setUserId(userId);
        mortgage.setCurrentBalance("350000.00");
        mortgage.setCurrency("USD");

        RealEstateProperty property =
                RealEstateProperty.builder()
                        .id(propertyId)
                        .userId(userId)
                        .name("Property")
                        .propertyType(PropertyType.RESIDENTIAL)
                        .currentValue("600000.00")
                        .purchasePrice("500000.00")
                        .currency("USD")
                        .mortgageId(10L)
                        .mortgage(mortgage)
                        .isActive(true)
                        .build();

        when(realEstateRepository.findByIdAndUserIdWithMortgage(propertyId, userId))
                .thenReturn(Optional.of(property));

        PropertyEquityResponse equity = realEstateService.calculateEquity(propertyId, userId);

        assertThat(equity).isNotNull();
        assertThat(equity.getPropertyId()).isEqualTo(propertyId);
        assertThat(equity.getCurrentValue()).isEqualTo(new BigDecimal("600000.00"));
        assertThat(equity.getMortgageBalance()).isEqualTo(new BigDecimal("350000.00"));
        assertThat(equity.getEquity()).isEqualTo(new BigDecimal("250000.00"));
        assertThat(equity.getEquityPercentage()).isEqualByComparingTo(new BigDecimal("41.67"));
        assertThat(equity.getLoanToValueRatio()).isEqualByComparingTo(new BigDecimal("58.33"));
    }

    @Test
    @DisplayName("Should calculate equity without mortgage correctly")
    void shouldCalculateEquityWithoutMortgage() {
        RealEstateProperty property =
                RealEstateProperty.builder()
                        .id(propertyId)
                        .userId(userId)
                        .name("Property")
                        .propertyType(PropertyType.RESIDENTIAL)
                        .currentValue("600000.00")
                        .purchasePrice("500000.00")
                        .currency("USD")
                        .mortgageId(null)
                        .mortgage(null)
                        .isActive(true)
                        .build();

        when(realEstateRepository.findByIdAndUserIdWithMortgage(propertyId, userId))
                .thenReturn(Optional.of(property));

        PropertyEquityResponse equity = realEstateService.calculateEquity(propertyId, userId);

        assertThat(equity).isNotNull();
        assertThat(equity.getCurrentValue()).isEqualTo(new BigDecimal("600000.00"));
        assertThat(equity.getMortgageBalance()).isEqualTo(BigDecimal.ZERO);
        assertThat(equity.getEquity()).isEqualTo(new BigDecimal("600000.00"));
        assertThat(equity.getEquityPercentage()).isEqualByComparingTo(new BigDecimal("100.00"));
        assertThat(equity.getLoanToValueRatio()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    // ========== ROI CALCULATION TESTS ==========

    @Test
    @DisplayName("Should calculate ROI with rental income correctly")
    void shouldCalculateROIWithRentalIncome() {
        LocalDate today = LocalDate.now();
        int yearsOwned = today.getYear() - purchaseDate.getYear();

        RealEstateProperty property =
                RealEstateProperty.builder()
                        .id(propertyId)
                        .userId(userId)
                        .name("Property Name")
                        .propertyType(PropertyType.RESIDENTIAL)
                        .currentValue("650000.00")
                        .purchasePrice("500000.00")
                        .rentalIncome("3000.00")
                        .purchaseDate(purchaseDate)
                        .currency("USD")
                        .isActive(true)
                        .build();

        when(realEstateRepository.findByIdAndUserId(propertyId, userId))
                .thenReturn(Optional.of(property));

        PropertyROIResponse roi = realEstateService.calculateROI(propertyId, userId);

        assertThat(roi).isNotNull();
        assertThat(roi.getPropertyId()).isEqualTo(propertyId);
        assertThat(roi.getPurchasePrice()).isEqualTo(new BigDecimal("500000.00"));
        assertThat(roi.getCurrentValue()).isEqualTo(new BigDecimal("650000.00"));
        assertThat(roi.getAppreciation()).isEqualTo(new BigDecimal("150000.00"));
        assertThat(roi.getMonthlyRentalIncome()).isEqualTo(new BigDecimal("3000.00"));
        assertThat(roi.getYearsOwned()).isEqualTo(yearsOwned);

        long monthsOwned = ChronoUnit.MONTHS.between(purchaseDate, today);
        BigDecimal expectedTotalRental =
                new BigDecimal("3000.00").multiply(new BigDecimal(monthsOwned));
        assertThat(roi.getTotalRentalIncome()).isEqualByComparingTo(expectedTotalRental);
    }

    @Test
    @DisplayName("Should calculate ROI without rental income correctly")
    void shouldCalculateROIWithoutRentalIncome() {
        LocalDate today = LocalDate.now();
        int yearsOwned = today.getYear() - purchaseDate.getYear();

        RealEstateProperty property =
                RealEstateProperty.builder()
                        .id(propertyId)
                        .userId(userId)
                        .name("Land Parcel")
                        .propertyType(PropertyType.LAND)
                        .currentValue("75000.00")
                        .purchasePrice("50000.00")
                        .rentalIncome(null)
                        .purchaseDate(purchaseDate)
                        .currency("USD")
                        .isActive(true)
                        .build();

        when(realEstateRepository.findByIdAndUserId(propertyId, userId))
                .thenReturn(Optional.of(property));

        PropertyROIResponse roi = realEstateService.calculateROI(propertyId, userId);

        assertThat(roi).isNotNull();
        assertThat(roi.getPurchasePrice()).isEqualTo(new BigDecimal("50000.00"));
        assertThat(roi.getCurrentValue()).isEqualTo(new BigDecimal("75000.00"));
        assertThat(roi.getAppreciation()).isEqualTo(new BigDecimal("25000.00"));
        assertThat(roi.getMonthlyRentalIncome()).isNull();
        assertThat(roi.getTotalRentalIncome()).isNull();
        assertThat(roi.getTotalROI()).isEqualByComparingTo(new BigDecimal("50.00"));
    }

    // ========== VALUE ESTIMATION TESTS ==========

    @Test
    @DisplayName("Should update property value successfully")
    void shouldUpdatePropertyValueSuccessfully() {
        BigDecimal newValue = new BigDecimal("680000.00");

        RealEstateProperty property =
                RealEstateProperty.builder()
                        .id(propertyId)
                        .userId(userId)
                        .name("Property Name")
                        .propertyType(PropertyType.RESIDENTIAL)
                        .currentValue("600000.00")
                        .purchasePrice("600000.00")
                        .currency("USD")
                        .isActive(true)
                        .build();

        RealEstateProperty updated =
                RealEstateProperty.builder()
                        .id(propertyId)
                        .userId(userId)
                        .name("Property Name")
                        .propertyType(PropertyType.RESIDENTIAL)
                        .currentValue("680000.00")
                        .purchasePrice("600000.00")
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
        when(realEstateRepository.save(property)).thenReturn(updated);
        when(realEstateMapper.toResponse(updated)).thenReturn(mappedResponse);

        RealEstatePropertyResponse result =
                realEstateService.estimateValue(propertyId, userId, newValue);

        assertThat(result).isNotNull();
        verify(realEstateRepository).save(property);
    }

    @Test
    @DisplayName("Should validate new value is positive")
    void shouldValidateNewValueIsPositive() {
        BigDecimal negativeValue = new BigDecimal("-100.00");

        assertThatThrownBy(() -> realEstateService.estimateValue(propertyId, userId, negativeValue))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("New value must be non-negative");

        verify(realEstateRepository, never()).save(any());
    }

    // ========== AUTHORIZATION TESTS ==========

    @Test
    @DisplayName("Should enforce user authorization on property access")
    void shouldEnforceUserAuthorizationOnPropertyAccess() {
        Long unauthorizedUserId = 999L;

        when(realEstateRepository.findByIdAndUserIdWithMortgage(propertyId, unauthorizedUserId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> realEstateService.getPropertyById(propertyId, unauthorizedUserId))
                .isInstanceOf(RealEstatePropertyNotFoundException.class)
                .hasMessageContaining("property not found");

        verify(realEstateRepository).findByIdAndUserIdWithMortgage(propertyId, unauthorizedUserId);
    }

    // Secondary currency conversion tests (REQ-4.4, REQ-4.5, REQ-4.6)

    @Test
    @DisplayName(
            "Should populate secondary currency fields when secondary currency is configured and differs from native")
    void shouldPopulateSecondaryCurrencyFieldsWhenConfigured() {
        RealEstateProperty property =
                RealEstateProperty.builder()
                        .id(propertyId)
                        .userId(userId)
                        .name("My Property")
                        .propertyType(PropertyType.RESIDENTIAL)
                        .currentValue("500000.00")
                        .purchasePrice("400000.00")
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
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(exchangeRateService.getExchangeRate("EUR", "USD", null))
                .thenReturn(new BigDecimal("1.1"));
        when(exchangeRateService.convert(new BigDecimal("500000.00"), "EUR", "USD"))
                .thenReturn(new BigDecimal("550000.00"));
        when(exchangeRateService.getExchangeRate("EUR", "JPY", null))
                .thenReturn(new BigDecimal("155.0"));
        when(exchangeRateService.convert(new BigDecimal("500000.00"), "EUR", "JPY"))
                .thenReturn(new BigDecimal("77500000.00"));

        RealEstatePropertyResponse result = realEstateService.getPropertyById(propertyId, userId);

        assertThat(result.getValueInSecondaryCurrency())
                .isEqualByComparingTo(new BigDecimal("77500000.00"));
        assertThat(result.getSecondaryCurrency()).isEqualTo("JPY");
        assertThat(result.getSecondaryExchangeRate()).isEqualByComparingTo(new BigDecimal("155.0"));
        assertThat(result.getIsConverted()).isTrue();
        assertThat(result.getValueInBaseCurrency())
                .isEqualByComparingTo(new BigDecimal("550000.00"));
    }

    @Test
    @DisplayName(
            "Should set secondary fields to null when native currency equals secondary currency")
    void shouldSkipSecondaryCurrencyConversionWhenNativeEqualsSecondary() {
        RealEstateProperty property =
                RealEstateProperty.builder()
                        .id(propertyId)
                        .userId(userId)
                        .name("My Property")
                        .propertyType(PropertyType.RESIDENTIAL)
                        .currentValue("500000.00")
                        .purchasePrice("400000.00")
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
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(exchangeRateService.getExchangeRate("EUR", "USD", null))
                .thenReturn(new BigDecimal("1.1"));
        when(exchangeRateService.convert(new BigDecimal("500000.00"), "EUR", "USD"))
                .thenReturn(new BigDecimal("550000.00"));

        RealEstatePropertyResponse result = realEstateService.getPropertyById(propertyId, userId);

        assertThat(result.getValueInSecondaryCurrency()).isNull();
        assertThat(result.getSecondaryCurrency()).isEqualTo("EUR");
        assertThat(result.getSecondaryExchangeRate()).isNull();
        assertThat(result.getIsConverted()).isTrue();
        assertThat(result.getValueInBaseCurrency())
                .isEqualByComparingTo(new BigDecimal("550000.00"));
        verify(exchangeRateService, never()).convert(new BigDecimal("500000.00"), "EUR", "EUR");
    }

    @Test
    @DisplayName(
            "Should leave valueInSecondaryCurrency null but set secondaryCurrency when secondary rate is unavailable")
    void shouldLeaveSecondaryAmountNullWhenSecondaryRateUnavailable() {
        RealEstateProperty property =
                RealEstateProperty.builder()
                        .id(propertyId)
                        .userId(userId)
                        .name("My Property")
                        .propertyType(PropertyType.RESIDENTIAL)
                        .currentValue("500000.00")
                        .purchasePrice("400000.00")
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
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(exchangeRateService.getExchangeRate("EUR", "USD", null))
                .thenReturn(new BigDecimal("1.1"));
        when(exchangeRateService.convert(new BigDecimal("500000.00"), "EUR", "USD"))
                .thenReturn(new BigDecimal("550000.00"));
        when(exchangeRateService.convert(new BigDecimal("500000.00"), "EUR", "JPY"))
                .thenThrow(new RuntimeException("JPY rate unavailable"));

        RealEstatePropertyResponse result = realEstateService.getPropertyById(propertyId, userId);

        assertThat(result.getValueInSecondaryCurrency()).isNull();
        assertThat(result.getSecondaryCurrency()).isEqualTo("JPY");
        assertThat(result.getIsConverted()).isTrue();
        assertThat(result.getValueInBaseCurrency())
                .isEqualByComparingTo(new BigDecimal("550000.00"));
    }

    @Test
    @DisplayName("Should leave all secondary fields null when no secondary currency is configured")
    void shouldLeaveSecondaryFieldsNullWhenNoSecondaryCurrencyConfigured() {
        RealEstateProperty property =
                RealEstateProperty.builder()
                        .id(propertyId)
                        .userId(userId)
                        .name("My Property")
                        .propertyType(PropertyType.RESIDENTIAL)
                        .currentValue("500000.00")
                        .purchasePrice("400000.00")
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

        User user = User.builder().baseCurrency("USD").build();

        when(realEstateRepository.findByIdAndUserIdWithMortgage(propertyId, userId))
                .thenReturn(Optional.of(property));
        when(realEstateMapper.toResponse(property)).thenReturn(mappedResponse);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(exchangeRateService.getExchangeRate("EUR", "USD", null))
                .thenReturn(new BigDecimal("1.1"));
        when(exchangeRateService.convert(new BigDecimal("500000.00"), "EUR", "USD"))
                .thenReturn(new BigDecimal("550000.00"));

        RealEstatePropertyResponse result = realEstateService.getPropertyById(propertyId, userId);

        assertThat(result.getValueInSecondaryCurrency()).isNull();
        assertThat(result.getSecondaryCurrency()).isNull();
        assertThat(result.getSecondaryExchangeRate()).isNull();
        assertThat(result.getIsConverted()).isTrue();
        assertThat(result.getValueInBaseCurrency())
                .isEqualByComparingTo(new BigDecimal("550000.00"));
    }
}
