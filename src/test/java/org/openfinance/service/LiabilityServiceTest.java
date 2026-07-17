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
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.openfinance.dto.AmortizationScheduleEntry;
import org.openfinance.dto.LiabilityRequest;
import org.openfinance.dto.LiabilityResponse;
import org.openfinance.entity.Liability;
import org.openfinance.entity.LiabilityType;
import org.openfinance.entity.User;
import org.openfinance.exception.LiabilityNotFoundException;
import org.openfinance.repository.CurrencyRepository;
import org.openfinance.repository.LiabilityRepository;
import org.openfinance.repository.RealEstateRepository;
import org.openfinance.repository.UserRepository;
import org.openfinance.security.EncryptionService;

/** Unit tests for LiabilityService. Tests CRUD operations, calculations, and authorization. */
@ExtendWith(MockitoExtension.class)
class LiabilityServiceTest {

    @Mock private LiabilityRepository liabilityRepository;

    @Mock private EncryptionService encryptionService;

    @Mock(lenient = true)
    private UserRepository userRepository;

    @Mock(lenient = true)
    private ExchangeRateService exchangeRateService;

    @Mock private RealEstateRepository realEstateRepository;

    @Mock private OperationHistoryService operationHistoryService;

    @Mock private CurrencyRepository currencyRepository;

    @Mock private SearchTokenService searchTokenService;

    @InjectMocks private LiabilityService liabilityService;

    private Long testUserId;

    @BeforeEach
    void setUp() {
        testUserId = 1L;

        // Lenient stub: return a USD user so populateConversionFields never NPEs.
        User defaultUser = User.builder().baseCurrency("USD").build();
        org.mockito.Mockito.lenient()
                .when(userRepository.findById(testUserId))
                .thenReturn(Optional.of(defaultUser));
    }

    // ============ Helper Methods ============

    private LiabilityRequest createValidRequest() {
        LiabilityRequest request = new LiabilityRequest();
        request.setName("Home Mortgage");
        request.setType(LiabilityType.MORTGAGE);
        request.setPrincipal(new BigDecimal("300000.00"));
        request.setCurrentBalance(new BigDecimal("250000.00"));
        request.setInterestRate(new BigDecimal("3.5"));
        request.setStartDate(LocalDate.now().minusYears(2));
        request.setEndDate(LocalDate.now().plusYears(28));
        request.setMinimumPayment(new BigDecimal("1500.00"));
        request.setCurrency("USD");
        request.setNotes("Fixed rate mortgage");
        return request;
    }

    private Liability createLiabilityEntity(Long id, Long userId) {
        Liability liability = new Liability();
        liability.setId(id);
        liability.setUserId(userId);
        liability.setName("Home Mortgage");
        liability.setType(LiabilityType.MORTGAGE);
        liability.setPrincipal("300000.00");
        liability.setCurrentBalance("250000.00");
        liability.setInterestRate("3.5");
        liability.setStartDate(LocalDate.now().minusYears(2));
        liability.setEndDate(LocalDate.now().plusYears(28));
        liability.setMinimumPayment("1500.00");
        liability.setCurrency("USD");
        liability.setNotes("Fixed rate mortgage");
        return liability;
    }

    // ============ Create Liability Tests ============

    @Test
    void shouldCreateLiability_WhenValidRequest() {
        // Given
        LiabilityRequest request = createValidRequest();

        when(liabilityRepository.save(any(Liability.class)))
                .thenAnswer(
                        invocation -> {
                            Liability saved = invocation.getArgument(0);
                            saved.setId(100L);
                            return saved;
                        });

        // When
        LiabilityResponse response = liabilityService.createLiability(testUserId, request);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.getId()).isEqualTo(100L);
        assertThat(response.getName()).isEqualTo("Home Mortgage");
        assertThat(response.getType()).isEqualTo(LiabilityType.MORTGAGE);
        assertThat(response.getPrincipal()).isEqualByComparingTo("300000.00");
        assertThat(response.getCurrentBalance()).isEqualByComparingTo("250000.00");
        assertThat(response.getInterestRate()).isEqualByComparingTo("3.5");
        assertThat(response.getCurrency()).isEqualTo("USD");

        verify(liabilityRepository).save(any(Liability.class));
    }

    @Test
    void shouldThrowException_WhenUserIdIsNull() {
        // Given
        LiabilityRequest request = createValidRequest();

        // When/Then
        assertThatThrownBy(() -> liabilityService.createLiability(null, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("User ID cannot be null");
    }

    @Test
    void shouldThrowException_WhenRequestIsNull() {
        // When/Then
        assertThatThrownBy(() -> liabilityService.createLiability(testUserId, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Liability request cannot be null");
    }

    @Test
    void shouldCreateLiability_WithOptionalFieldsNull() {
        // Given
        LiabilityRequest request = new LiabilityRequest();
        request.setName("Simple Loan");
        request.setType(LiabilityType.LOAN);
        request.setPrincipal(new BigDecimal("5000.00"));
        request.setCurrentBalance(new BigDecimal("5000.00"));
        request.setStartDate(LocalDate.now());
        request.setCurrency("USD");
        // No interestRate, endDate, minimumPayment, notes

        when(liabilityRepository.save(any(Liability.class)))
                .thenAnswer(
                        invocation -> {
                            Liability saved = invocation.getArgument(0);
                            saved.setId(101L);
                            return saved;
                        });

        // When
        LiabilityResponse response = liabilityService.createLiability(testUserId, request);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.getName()).isEqualTo("Simple Loan");
        assertThat(response.getInterestRate()).isNull();
        assertThat(response.getEndDate()).isNull();
        assertThat(response.getMinimumPayment()).isNull();
        assertThat(response.getNotes()).isNull();
    }

    // ============ Get Liability Tests ============

    @Test
    void shouldGetLiabilityById_WhenExists() {
        // Given
        Long liabilityId = 100L;
        Liability liability = createLiabilityEntity(liabilityId, testUserId);

        when(liabilityRepository.findByIdAndUserId(liabilityId, testUserId))
                .thenReturn(Optional.of(liability));

        // When
        LiabilityResponse response = liabilityService.getLiabilityById(liabilityId, testUserId);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.getId()).isEqualTo(liabilityId);
        assertThat(response.getName()).isEqualTo("Home Mortgage");
        assertThat(response.getPrincipal()).isEqualByComparingTo("300000.00");

        verify(liabilityRepository).findByIdAndUserId(liabilityId, testUserId);
    }

    @Test
    void shouldThrowException_WhenLiabilityNotFound() {
        // Given
        Long liabilityId = 999L;
        when(liabilityRepository.findByIdAndUserId(liabilityId, testUserId))
                .thenReturn(Optional.empty());

        // When/Then
        assertThatThrownBy(() -> liabilityService.getLiabilityById(liabilityId, testUserId))
                .isInstanceOf(LiabilityNotFoundException.class);
    }

    @Test
    void shouldThrowException_WhenUserTriesToAccessAnotherUsersLiability() {
        // Given
        Long liabilityId = 100L;
        Long otherUserId = 2L;

        when(liabilityRepository.findByIdAndUserId(liabilityId, otherUserId))
                .thenReturn(Optional.empty());

        // When/Then
        assertThatThrownBy(() -> liabilityService.getLiabilityById(liabilityId, otherUserId))
                .isInstanceOf(LiabilityNotFoundException.class);

        verify(liabilityRepository).findByIdAndUserId(liabilityId, otherUserId);
    }

    @Test
    void shouldGetLiabilitiesByUserId() {
        // Given
        Liability liability1 = createLiabilityEntity(100L, testUserId);
        Liability liability2 = createLiabilityEntity(101L, testUserId);

        when(liabilityRepository.findByUserIdOrderByCreatedAtDesc(testUserId))
                .thenReturn(Arrays.asList(liability1, liability2));

        // When
        List<LiabilityResponse> responses = liabilityService.getLiabilitiesByUserId(testUserId);

        // Then
        assertThat(responses).hasSize(2);
        assertThat(responses.get(0).getId()).isEqualTo(100L);
        assertThat(responses.get(1).getId()).isEqualTo(101L);

        verify(liabilityRepository).findByUserIdOrderByCreatedAtDesc(testUserId);
    }

    @Test
    void shouldReturnEmptyList_WhenUserHasNoLiabilities() {
        // Given
        when(liabilityRepository.findByUserIdOrderByCreatedAtDesc(testUserId))
                .thenReturn(List.of());

        // When
        List<LiabilityResponse> responses = liabilityService.getLiabilitiesByUserId(testUserId);

        // Then
        assertThat(responses).isEmpty();
    }

    // ============ Update Liability Tests ============

    @Test
    void shouldUpdateLiability_WhenValid() {
        // Given
        Long liabilityId = 100L;
        Liability existingLiability = createLiabilityEntity(liabilityId, testUserId);

        LiabilityRequest updateRequest = createValidRequest();
        updateRequest.setName("Updated Mortgage");
        updateRequest.setCurrentBalance(new BigDecimal("240000.00"));

        when(liabilityRepository.findByIdAndUserId(liabilityId, testUserId))
                .thenReturn(Optional.of(existingLiability));
        when(liabilityRepository.save(any(Liability.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // When
        LiabilityResponse response =
                liabilityService.updateLiability(liabilityId, testUserId, updateRequest);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.getName()).isEqualTo("Updated Mortgage");
        assertThat(response.getCurrentBalance()).isEqualByComparingTo("240000.00");

        verify(liabilityRepository).save(any(Liability.class));
    }

    @Test
    void shouldThrowException_WhenUpdatingNonExistentLiability() {
        // Given
        Long liabilityId = 999L;
        LiabilityRequest request = createValidRequest();

        when(liabilityRepository.findByIdAndUserId(liabilityId, testUserId))
                .thenReturn(Optional.empty());

        // When/Then
        assertThatThrownBy(() -> liabilityService.updateLiability(liabilityId, testUserId, request))
                .isInstanceOf(LiabilityNotFoundException.class);
    }

    // ============ Delete Liability Tests ============

    @Test
    void shouldDeleteLiability_WhenExists() {
        // Given
        Long liabilityId = 100L;
        Liability liability = createLiabilityEntity(liabilityId, testUserId);
        when(liabilityRepository.findByIdAndUserId(liabilityId, testUserId))
                .thenReturn(Optional.of(liability));

        // When
        liabilityService.deleteLiability(liabilityId, testUserId);

        // Then
        verify(liabilityRepository).findByIdAndUserId(liabilityId, testUserId);
        verify(liabilityRepository).delete(liability);
    }

    @Test
    void shouldThrowException_WhenDeletingNonExistentLiability() {
        // Given
        Long liabilityId = 999L;
        when(liabilityRepository.findByIdAndUserId(liabilityId, testUserId))
                .thenReturn(Optional.empty());

        // When/Then
        assertThatThrownBy(() -> liabilityService.deleteLiability(liabilityId, testUserId))
                .isInstanceOf(LiabilityNotFoundException.class);

        verify(liabilityRepository, never()).delete(any(Liability.class));
    }

    // ============ Get Liabilities By Type Tests ============

    @Test
    void shouldGetLiabilitiesByType() {
        // Given
        Liability mortgage1 = createLiabilityEntity(100L, testUserId);
        Liability mortgage2 = createLiabilityEntity(101L, testUserId);

        when(liabilityRepository.findByUserIdAndTypeOrderByCreatedAtDesc(
                        testUserId, LiabilityType.MORTGAGE))
                .thenReturn(Arrays.asList(mortgage1, mortgage2));

        // When
        List<LiabilityResponse> responses =
                liabilityService.getLiabilitiesByType(testUserId, LiabilityType.MORTGAGE);

        // Then
        assertThat(responses).hasSize(2);
        assertThat(responses).allMatch(r -> r.getType() == LiabilityType.MORTGAGE);
    }

    // ============ Calculate Total Liabilities Tests ============

    @Test
    void shouldCalculateTotalLiabilities_ByCurrency() {
        // Given
        Liability usdLiability1 = createLiabilityEntity(100L, testUserId);
        usdLiability1.setCurrentBalance("50000.00");
        usdLiability1.setCurrency("USD");

        Liability usdLiability2 = createLiabilityEntity(101L, testUserId);
        usdLiability2.setCurrentBalance("30000.00");
        usdLiability2.setCurrency("USD");

        Liability eurLiability = createLiabilityEntity(102L, testUserId);
        eurLiability.setCurrentBalance("20000.00");
        eurLiability.setCurrency("EUR");

        when(liabilityRepository.findByUserIdOrderByCreatedAtDesc(testUserId))
                .thenReturn(Arrays.asList(usdLiability1, usdLiability2, eurLiability));

        // When
        Map<String, BigDecimal> totals = liabilityService.calculateTotalLiabilities(testUserId);

        // Then
        assertThat(totals).hasSize(2);
        assertThat(totals.get("USD")).isEqualByComparingTo("80000.00");
        assertThat(totals.get("EUR")).isEqualByComparingTo("20000.00");
    }

    @Test
    void shouldReturnEmptyMap_WhenNoLiabilities() {
        // Given
        when(liabilityRepository.findByUserIdOrderByCreatedAtDesc(testUserId))
                .thenReturn(List.of());

        // When
        Map<String, BigDecimal> totals = liabilityService.calculateTotalLiabilities(testUserId);

        // Then
        assertThat(totals).isEmpty();
    }

    // ============ Calculate Amortization Schedule Tests ============

    @Test
    void shouldCalculateAmortizationSchedule_ForStandardLoan() {
        // Given
        Long liabilityId = 100L;
        Liability liability = createLiabilityEntity(liabilityId, testUserId);
        liability.setCurrentBalance("10000.00");
        liability.setInterestRate("12.0"); // 12% annual = 1% monthly
        liability.setMinimumPayment("500.00");

        when(liabilityRepository.findByIdAndUserId(liabilityId, testUserId))
                .thenReturn(Optional.of(liability));

        // When
        List<AmortizationScheduleEntry> schedule =
                liabilityService.calculateAmortizationSchedule(liabilityId, testUserId);

        // Then
        assertThat(schedule).isNotEmpty();

        // First payment
        AmortizationScheduleEntry firstPayment = schedule.get(0);
        assertThat(firstPayment.getPaymentNumber()).isEqualTo(1);
        assertThat(firstPayment.getPaymentAmount()).isEqualByComparingTo("500.00");
        assertThat(firstPayment.getInterestPortion())
                .isEqualByComparingTo("100.00"); // 10000 * 0.01
        assertThat(firstPayment.getPrincipalPortion()).isEqualByComparingTo("400.00"); // 500 - 100
        assertThat(firstPayment.getRemainingBalance())
                .isEqualByComparingTo("9600.00"); // 10000 - 400

        // Last payment should have zero or near-zero balance
        AmortizationScheduleEntry lastPayment = schedule.get(schedule.size() - 1);
        assertThat(lastPayment.getRemainingBalance()).isLessThanOrEqualTo(new BigDecimal("1.00"));
    }

    @Test
    void shouldReturnEmptySchedule_WhenPaymentDoesNotCoverInterest() {
        // Given
        Long liabilityId = 100L;
        Liability liability = createLiabilityEntity(liabilityId, testUserId);
        liability.setCurrentBalance("100000.00");
        liability.setInterestRate("12.0"); // Monthly interest = $1000
        liability.setMinimumPayment("500.00"); // Payment < interest

        when(liabilityRepository.findByIdAndUserId(liabilityId, testUserId))
                .thenReturn(Optional.of(liability));

        // When
        List<AmortizationScheduleEntry> schedule =
                liabilityService.calculateAmortizationSchedule(liabilityId, testUserId);

        // Then
        assertThat(schedule).isEmpty();
    }

    @Test
    void shouldCalculateAmortizationSchedule_WithZeroInterest() {
        // Given
        Long liabilityId = 100L;
        Liability liability = createLiabilityEntity(liabilityId, testUserId);
        liability.setCurrentBalance("5000.00");
        liability.setInterestRate("0.0");
        liability.setMinimumPayment("500.00");

        when(liabilityRepository.findByIdAndUserId(liabilityId, testUserId))
                .thenReturn(Optional.of(liability));

        // When
        List<AmortizationScheduleEntry> schedule =
                liabilityService.calculateAmortizationSchedule(liabilityId, testUserId);

        // Then
        assertThat(schedule).hasSize(10); // 5000 / 500 = 10 payments

        // All payments should have zero interest
        assertThat(schedule)
                .allMatch(entry -> entry.getInterestPortion().compareTo(BigDecimal.ZERO) == 0);

        // Last payment
        AmortizationScheduleEntry lastPayment = schedule.get(9);
        assertThat(lastPayment.getRemainingBalance()).isEqualByComparingTo("0.00");
    }

    // ============ Calculate Total Interest Tests ============

    @Test
    void shouldCalculateTotalInterest() {
        // Given
        Long liabilityId = 100L;
        Liability liability = createLiabilityEntity(liabilityId, testUserId);
        liability.setCurrentBalance("10000.00");
        liability.setInterestRate("12.0");
        liability.setMinimumPayment("500.00");

        when(liabilityRepository.findByIdAndUserId(liabilityId, testUserId))
                .thenReturn(Optional.of(liability));

        // When
        BigDecimal totalInterest = liabilityService.calculateTotalInterest(liabilityId, testUserId);

        // Then
        assertThat(totalInterest).isGreaterThan(BigDecimal.ZERO);
        // Total interest should be less than principal for reasonable loans
        assertThat(totalInterest).isLessThan(new BigDecimal("10000.00"));
    }

    @Test
    void shouldReturnNull_WhenScheduleIsEmpty() {
        // Given
        Long liabilityId = 100L;
        Liability liability = createLiabilityEntity(liabilityId, testUserId);
        liability.setCurrentBalance("100000.00");
        liability.setInterestRate("12.0");
        liability.setMinimumPayment("500.00"); // Too low - won't generate schedule

        when(liabilityRepository.findByIdAndUserId(liabilityId, testUserId))
                .thenReturn(Optional.of(liability));

        // When
        BigDecimal totalInterest = liabilityService.calculateTotalInterest(liabilityId, testUserId);

        // Then - When schedule is empty, returns null
        assertThat(totalInterest).isNull();
    }

    // Secondary currency conversion tests (REQ-4.3, REQ-4.5, REQ-4.6)

    @Test
    void shouldPopulateSecondaryCurrencyFieldsWhenConfigured() {
        // Given — native=EUR, base=USD, secondary=JPY
        Long liabilityId = 200L;
        Liability liability = createLiabilityEntity(liabilityId, testUserId);
        liability.setCurrency("EUR");

        User user = User.builder().baseCurrency("USD").secondaryCurrency("JPY").build();

        when(liabilityRepository.findByIdAndUserId(liabilityId, testUserId))
                .thenReturn(Optional.of(liability));
        when(userRepository.findById(testUserId)).thenReturn(Optional.of(user));
        when(exchangeRateService.getExchangeRate("EUR", "USD", null))
                .thenReturn(new BigDecimal("1.1"));
        when(exchangeRateService.convert(any(BigDecimal.class), eq("EUR"), eq("USD")))
                .thenReturn(new BigDecimal("275000.00"));
        when(exchangeRateService.getExchangeRate("EUR", "JPY", null))
                .thenReturn(new BigDecimal("155.0"));
        when(exchangeRateService.convert(any(BigDecimal.class), eq("EUR"), eq("JPY")))
                .thenReturn(new BigDecimal("38750000.00"));

        // When
        LiabilityResponse result = liabilityService.getLiabilityById(liabilityId, testUserId);

        // Then — Requirement REQ-4.3: secondary fields populated
        assertThat(result.getBalanceInSecondaryCurrency())
                .isEqualByComparingTo(new BigDecimal("38750000.00"));
        assertThat(result.getSecondaryCurrency()).isEqualTo("JPY");
        assertThat(result.getSecondaryExchangeRate()).isEqualByComparingTo(new BigDecimal("155.0"));
        // Base conversion also works
        assertThat(result.getIsConverted()).isTrue();
        assertThat(result.getBalanceInBaseCurrency())
                .isEqualByComparingTo(new BigDecimal("275000.00"));
    }

    @Test
    void shouldSkipSecondaryCurrencyConversionWhenNativeEqualsSecondary() {
        // Given — native=EUR, base=USD, secondary=EUR (same as native)
        Long liabilityId = 201L;
        Liability liability = createLiabilityEntity(liabilityId, testUserId);
        liability.setCurrency("EUR");

        User user = User.builder().baseCurrency("USD").secondaryCurrency("EUR").build();

        when(liabilityRepository.findByIdAndUserId(liabilityId, testUserId))
                .thenReturn(Optional.of(liability));
        when(userRepository.findById(testUserId)).thenReturn(Optional.of(user));
        when(exchangeRateService.getExchangeRate("EUR", "USD", null))
                .thenReturn(new BigDecimal("1.1"));
        when(exchangeRateService.convert(any(BigDecimal.class), eq("EUR"), eq("USD")))
                .thenReturn(new BigDecimal("275000.00"));

        // When
        LiabilityResponse result = liabilityService.getLiabilityById(liabilityId, testUserId);

        // Then — Requirement REQ-4.6: no redundant conversion when native equals
        // secondary
        assertThat(result.getBalanceInSecondaryCurrency()).isNull();
        assertThat(result.getSecondaryCurrency()).isEqualTo("EUR");
        assertThat(result.getSecondaryExchangeRate()).isNull();
        // Base conversion is unaffected
        assertThat(result.getIsConverted()).isTrue();
        assertThat(result.getBalanceInBaseCurrency())
                .isEqualByComparingTo(new BigDecimal("275000.00"));
        // Verify exchange rate service was NOT called for secondary conversion
        verify(exchangeRateService, never()).convert(any(BigDecimal.class), eq("EUR"), eq("EUR"));
    }

    @Test
    void shouldLeaveSecondaryAmountNullWhenSecondaryRateUnavailable() {
        // Given — native=EUR, base=USD, secondary=JPY — JPY rate throws
        Long liabilityId = 202L;
        Liability liability = createLiabilityEntity(liabilityId, testUserId);
        liability.setCurrency("EUR");

        User user = User.builder().baseCurrency("USD").secondaryCurrency("JPY").build();

        when(liabilityRepository.findByIdAndUserId(liabilityId, testUserId))
                .thenReturn(Optional.of(liability));
        when(userRepository.findById(testUserId)).thenReturn(Optional.of(user));
        when(exchangeRateService.getExchangeRate("EUR", "USD", null))
                .thenReturn(new BigDecimal("1.1"));
        when(exchangeRateService.convert(any(BigDecimal.class), eq("EUR"), eq("USD")))
                .thenReturn(new BigDecimal("275000.00"));
        when(exchangeRateService.convert(any(BigDecimal.class), eq("EUR"), eq("JPY")))
                .thenThrow(new RuntimeException("JPY rate unavailable"));

        // When — must not throw
        LiabilityResponse result = liabilityService.getLiabilityById(liabilityId, testUserId);

        // Then — Requirement REQ-4.5: graceful fallback; amount null, currency code
        // still set
        assertThat(result.getBalanceInSecondaryCurrency()).isNull();
        assertThat(result.getSecondaryCurrency()).isEqualTo("JPY");
        // Base conversion is unaffected
        assertThat(result.getIsConverted()).isTrue();
        assertThat(result.getBalanceInBaseCurrency())
                .isEqualByComparingTo(new BigDecimal("275000.00"));
    }

    @Test
    void shouldLeaveSecondaryFieldsNullWhenNoSecondaryCurrencyConfigured() {
        // Given — native=EUR, base=USD, secondary=null
        Long liabilityId = 203L;
        Liability liability = createLiabilityEntity(liabilityId, testUserId);
        liability.setCurrency("EUR");

        User user =
                User.builder().baseCurrency("USD").build(); // secondaryCurrency is null by default

        when(liabilityRepository.findByIdAndUserId(liabilityId, testUserId))
                .thenReturn(Optional.of(liability));
        when(userRepository.findById(testUserId)).thenReturn(Optional.of(user));
        when(exchangeRateService.getExchangeRate("EUR", "USD", null))
                .thenReturn(new BigDecimal("1.1"));
        when(exchangeRateService.convert(any(BigDecimal.class), eq("EUR"), eq("USD")))
                .thenReturn(new BigDecimal("275000.00"));

        // When
        LiabilityResponse result = liabilityService.getLiabilityById(liabilityId, testUserId);

        // Then — Requirement REQ-4.3: all secondary fields must be null
        assertThat(result.getBalanceInSecondaryCurrency()).isNull();
        assertThat(result.getSecondaryCurrency()).isNull();
        assertThat(result.getSecondaryExchangeRate()).isNull();
        // Base conversion works normally
        assertThat(result.getIsConverted()).isTrue();
        assertThat(result.getBalanceInBaseCurrency())
                .isEqualByComparingTo(new BigDecimal("275000.00"));
    }
}
