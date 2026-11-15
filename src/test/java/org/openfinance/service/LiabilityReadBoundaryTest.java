package org.openfinance.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.openfinance.dto.LiabilityResponse;
import org.openfinance.dto.LiabilitySearchCriteria;
import org.openfinance.entity.Liability;
import org.openfinance.entity.LiabilityType;
import org.openfinance.entity.User;
import org.openfinance.repository.AccountRepository;
import org.openfinance.repository.CurrencyRepository;
import org.openfinance.repository.InstitutionRepository;
import org.openfinance.repository.LiabilityRepository;
import org.openfinance.repository.LiabilityTrancheRepository;
import org.openfinance.repository.NetWorthRepository;
import org.openfinance.repository.RealEstateRepository;
import org.openfinance.repository.TransactionRepository;
import org.openfinance.repository.UserRepository;
import org.openfinance.security.EncryptionService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

@ExtendWith(MockitoExtension.class)
@DisplayName(
        "Liability searches sort exact values before pagination and reject incomplete ownership scope")
class LiabilityReadBoundaryTest {
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

    @Mock private DefaultCurrencyProvider defaultCurrencyProvider;

    @Mock private TransactionRepository transactionRepository;

    @Mock private LiabilityTrancheRepository liabilityTrancheRepository;

    @Mock private LiabilityTrancheService liabilityTrancheService;

    @Mock private NetWorthService netWorthService;
    @Mock private NetWorthRepository netWorthRepository;
    @Mock private AccountRepository accountRepository;
    @Mock private InstitutionRepository institutionRepository;
    @Mock private AssetFinancingService assetFinancingService;

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
        org.openfinance.testutil.DefaultCurrencyProviderMocks.stub(
                defaultCurrencyProvider, userRepository);
        org.springframework.test.util.ReflectionTestUtils.setField(
                liabilityService,
                "currencyConversionHelper",
                new CurrencyConversionHelper(
                        userRepository, defaultCurrencyProvider, exchangeRateService));
    }

    private Liability loan(long id) {
        Liability result = new Liability();
        result.setId(id);
        result.setUserId(1L);
        result.setName(id == 1 ? "alpha" : "Zulu");
        result.setType(id == 1 ? LiabilityType.AUTO_LOAN : LiabilityType.MORTGAGE);
        result.setPrincipal("100.00" + id);
        result.setCurrentBalance("90.00" + id);
        result.setOpeningPrincipal(new BigDecimal("100.00" + id));
        result.setOpeningBalance(new BigDecimal("90.00" + id));
        result.setMinimumPayment("10.00" + id);
        result.setInterestRate(id == 1 ? "1" : "2");
        result.setCurrency("USD");
        result.setStartDate(LocalDate.of(2020 + (int) id, 1, 1));
        result.setEndDate(LocalDate.of(2030 + (int) id, 1, 1));
        result.setCreatedAt(LocalDate.of(2020 + (int) id, 1, 1).atStartOfDay());
        return result;
    }

    static Stream<Arguments> orders() {
        return Stream.of(
                        "name",
                        "currentBalance",
                        "principal",
                        "minimumPayment",
                        "interestRate",
                        "startDate",
                        "endDate",
                        "createdAt",
                        "type",
                        "id")
                .flatMap(
                        field ->
                                Stream.of(Sort.Direction.ASC, Sort.Direction.DESC)
                                        .map(direction -> Arguments.of(field, direction)));
    }

    @ParameterizedTest
    @MethodSource("orders")
    void sortsEverySupportedFieldBeforePagination(String field, Sort.Direction direction) {
        when(liabilityRepository.findAllByUserIdAndType(1L, null))
                .thenReturn(List.of(loan(2), loan(1)));
        LiabilitySearchCriteria criteria =
                LiabilitySearchCriteria.builder().balanceMin(BigDecimal.ZERO).build();
        Page<LiabilityResponse> first =
                liabilityService.getLiabilitiesWithFilters(
                        1L,
                        null,
                        null,
                        false,
                        criteria,
                        PageRequest.of(0, 1, Sort.by(direction, field)));
        Page<LiabilityResponse> second =
                liabilityService.getLiabilitiesWithFilters(
                        1L,
                        null,
                        null,
                        false,
                        criteria,
                        PageRequest.of(1, 1, Sort.by(direction, field)));
        long firstId = direction == Sort.Direction.ASC ? 1L : 2L;
        assertThat(first.getContent())
                .extracting(LiabilityResponse::getId)
                .containsExactly(firstId);
        assertThat(second.getContent())
                .extracting(LiabilityResponse::getId)
                .containsExactly(3L - firstId);
        assertThat(first.getTotalElements()).isEqualTo(2);
    }

    @Test
    void composesSortAndUsesStableIdsForTies() {
        Liability first = loan(1), second = loan(2), third = loan(1);
        third.setId(3L);
        second.setName("ALPHA");
        when(liabilityRepository.findAllByUserIdAndType(1L, null))
                .thenReturn(List.of(third, second, first));
        Page<LiabilityResponse> page =
                liabilityService.getLiabilitiesWithFilters(
                        1L,
                        null,
                        null,
                        false,
                        PageRequest.of(0, 10, Sort.by("unknown", "name", "principal")));
        assertThat(page.getContent())
                .extracting(LiabilityResponse::getId)
                .containsExactly(1L, 3L, 2L);
    }

    @ParameterizedTest
    @ValueSource(strings = {"unknown", "unsorted"})
    void ignoresUnsupportedSortWithoutLosingSearchResults(String field) {
        Liability first = loan(1);
        first.setNotes("Old loan");
        Liability second = loan(2);
        second.setNotes("New LOAN");
        when(liabilityRepository.findAllByUserIdAndType(1L, null))
                .thenReturn(List.of(second, first));
        Page<LiabilityResponse> page =
                liabilityService.getLiabilitiesWithFilters(
                        1L,
                        null,
                        "loan",
                        false,
                        PageRequest.of(
                                0,
                                10,
                                field.equals("unsorted") ? Sort.unsorted() : Sort.by(field)));
        assertThat(page.getContent()).extracting(LiabilityResponse::getId).containsExactly(2L, 1L);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "  "})
    void blankSearchDelegatesPaginationToTheDatabase(String search) {
        PageRequest request = PageRequest.of(0, 10);
        when(liabilityRepository.findByUserIdWithFilters(1L, LiabilityType.MORTGAGE, request))
                .thenReturn(
                        new org.springframework.data.domain.PageImpl<>(
                                List.of(loan(2)), request, 1));
        assertThat(
                        liabilityService
                                .getLiabilitiesWithFilters(
                                        1L, LiabilityType.MORTGAGE, search, false, request)
                                .getContent())
                .extracting(LiabilityResponse::getId)
                .containsExactly(2L);
        verify(liabilityRepository, never()).findAllByUserIdAndType(any(), any());
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "get-id",
                "get-user",
                "delete-id",
                "delete-user",
                "list-user",
                "type-user",
                "type-null",
                "search-user",
                "search-page",
                "total-user",
                "schedule-id",
                "schedule-user",
                "breakdown-id",
                "breakdown-user",
                "transactions-id",
                "transactions-user"
            })
    void missingOwnershipScopeCannotReadOrMutateLoans(String invalid) {
        assertThatThrownBy(
                        () -> {
                            switch (invalid) {
                                case "get-id" -> liabilityService.getLiabilityById(null, 1L);
                                case "get-user" -> liabilityService.getLiabilityById(1L, null);
                                case "delete-id" -> liabilityService.deleteLiability(null, 1L);
                                case "delete-user" -> liabilityService.deleteLiability(1L, null);
                                case "list-user" -> liabilityService.getLiabilitiesByUserId(null);
                                case "type-user" -> liabilityService.getLiabilitiesByType(
                                        null, LiabilityType.MORTGAGE);
                                case "type-null" -> liabilityService.getLiabilitiesByType(1L, null);
                                case "search-user" -> liabilityService.getLiabilitiesWithFilters(
                                        null, null, null, false, PageRequest.of(0, 10));
                                case "search-page" -> liabilityService.getLiabilitiesWithFilters(
                                        1L, null, null, false, null);
                                case "total-user" -> liabilityService.calculateTotalLiabilities(
                                        null);
                                case "schedule-id" -> liabilityService
                                        .calculateAmortizationSchedule(null, 1L);
                                case "schedule-user" -> liabilityService
                                        .calculateAmortizationSchedule(1L, null);
                                case "breakdown-id" -> liabilityService.getLiabilityBreakdown(
                                        null, 1L);
                                case "breakdown-user" -> liabilityService.getLiabilityBreakdown(
                                        1L, null);
                                case "transactions-id" -> liabilityService.getLinkedTransactions(
                                        null, 1L);
                                case "transactions-user" -> liabilityService.getLinkedTransactions(
                                        1L, null);
                            }
                        })
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(liabilityRepository, transactionRepository, accountRepository);
    }
}
