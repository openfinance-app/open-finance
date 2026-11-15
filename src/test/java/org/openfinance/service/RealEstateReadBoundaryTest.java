package org.openfinance.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.openfinance.dto.AmountCurrencyMode;
import org.openfinance.dto.RealEstatePropertyResponse;
import org.openfinance.dto.RealEstateSearchCriteria;
import org.openfinance.entity.PropertyType;
import org.openfinance.entity.RealEstateProperty;
import org.openfinance.entity.User;
import org.openfinance.mapper.RealEstateMapper;
import org.openfinance.mapper.RealEstateMapperImpl;
import org.openfinance.repository.CurrencyRepository;
import org.openfinance.repository.LiabilityRepository;
import org.openfinance.repository.NetWorthRepository;
import org.openfinance.repository.RealEstateRepository;
import org.openfinance.repository.RealEstateValueHistoryRepository;
import org.openfinance.repository.UserRepository;
import org.openfinance.security.EncryptionService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

@ExtendWith(MockitoExtension.class)
@DisplayName("Property reads preserve decrypted sorting, exact bounds and ownership scope")
class RealEstateReadBoundaryTest {
    @Mock(lenient = true)
    private RealEstateRepository realEstateRepository;

    @Mock(lenient = true)
    private LiabilityRepository liabilityRepository;

    @Spy private RealEstateMapper realEstateMapper = new RealEstateMapperImpl();

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

    @Mock private AssetFinancingService assetFinancingService;
    @Mock private org.openfinance.repository.LiabilityTrancheRepository liabilityTrancheRepository;
    @Mock private org.openfinance.repository.TransactionRepository transactionRepository;

    @Mock
    private org.openfinance.repository.PropertyStatusHistoryRepository
            propertyStatusHistoryRepository;

    @InjectMocks private RealEstateService realEstateService;

    private LocalDate purchaseDate;
    private Long userId;
    private Long propertyId;

    @BeforeEach
    void setUp() {
        purchaseDate = LocalDate.of(2020, 1, 15);
        userId = 1L;
        propertyId = 1L;
        org.mockito.Mockito.lenient()
                .when(assetFinancingService.propertyDebt(any(), any(), any(), any()))
                .thenReturn(BigDecimal.ZERO);
        org.mockito.Mockito.lenient()
                .when(exchangeRateService.convert(any(), eq("USD"), eq("USD")))
                .thenAnswer(inv -> inv.getArgument(0));
        org.mockito.Mockito.lenient()
                .when(exchangeRateService.getExchangeRate(eq("USD"), eq("USD"), any()))
                .thenReturn(BigDecimal.ONE);

        User defaultUser = User.builder().baseCurrency("USD").build();
        org.mockito.Mockito.lenient()
                .when(userRepository.findById(userId))
                .thenReturn(Optional.of(defaultUser));
        org.openfinance.testutil.DefaultCurrencyProviderMocks.stub(
                defaultCurrencyProvider, userRepository);
        org.springframework.test.util.ReflectionTestUtils.setField(
                realEstateService,
                "currencyConversionHelper",
                new CurrencyConversionHelper(
                        userRepository, defaultCurrencyProvider, exchangeRateService));
    }

    private RealEstateProperty property(long id) {
        return RealEstateProperty.builder()
                .id(id)
                .userId(1L)
                .name(id == 1 ? "alpha" : "Zulu")
                .address("Rue " + id)
                .propertyType(PropertyType.RESIDENTIAL)
                .purchasePrice("100.00" + id)
                .currentValue("200.00" + id)
                .currency("USD")
                .rentalIncome("10.00" + id)
                .purchaseDate(LocalDate.of(2020 + (int) id, 1, 1))
                .createdAt(LocalDate.of(2020 + (int) id, 1, 1).atStartOfDay())
                .isActive(true)
                .build();
    }

    static Stream<Arguments> orders() {
        return Stream.of(
                        "name",
                        "currentValue",
                        "purchasePrice",
                        "rentalIncome",
                        "purchaseDate",
                        "createdAt",
                        "id")
                .flatMap(
                        field ->
                                Stream.of(Sort.Direction.ASC, Sort.Direction.DESC)
                                        .map(direction -> Arguments.of(field, direction)));
    }

    @ParameterizedTest
    @MethodSource("orders")
    void sortsDecryptedValuesBeforePagination(String field, Sort.Direction direction) {
        when(realEstateRepository.findAll(any(Specification.class)))
                .thenReturn(List.of(property(2), property(1)));
        RealEstateSearchCriteria criteria =
                RealEstateSearchCriteria.builder().valueMin(BigDecimal.ZERO).build();
        Page<RealEstatePropertyResponse> first =
                realEstateService.searchProperties(
                        1L, criteria, PageRequest.of(0, 1, Sort.by(direction, field)));
        Page<RealEstatePropertyResponse> second =
                realEstateService.searchProperties(
                        1L, criteria, PageRequest.of(1, 1, Sort.by(direction, field)));
        long firstId = direction == Sort.Direction.ASC ? 1L : 2L;
        assertThat(first.getContent())
                .extracting(RealEstatePropertyResponse::getId)
                .containsExactly(firstId);
        assertThat(second.getContent())
                .extracting(RealEstatePropertyResponse::getId)
                .containsExactly(3L - firstId);
        assertThat(first.getTotalElements()).isEqualTo(2);
    }

    @Test
    void secondarySortAndIdBreakTiesIndependentlyOfRepositoryOrder() {
        RealEstateProperty one = property(1), two = property(2), three = property(1);
        three.setId(3L);
        two.setName("ALPHA");
        when(realEstateRepository.findAll(any(Specification.class)))
                .thenReturn(List.of(three, two, one));
        Page<RealEstatePropertyResponse> result =
                realEstateService.searchProperties(
                        1L,
                        new RealEstateSearchCriteria(),
                        PageRequest.of(0, 10, Sort.by("unknown", "name", "currentValue")));
        assertThat(result.getContent())
                .extracting(RealEstatePropertyResponse::getId)
                .containsExactly(1L, 3L, 2L);
    }

    @ParameterizedTest
    @ValueSource(strings = {"unknown", "unsorted"})
    void anUnsupportedOrAbsentSortKeepsMatchesInRepositoryOrder(String field) {
        when(realEstateRepository.findAll(any(Specification.class)))
                .thenReturn(List.of(property(2), property(1)));
        Page<RealEstatePropertyResponse> result =
                realEstateService.searchProperties(
                        1L,
                        RealEstateSearchCriteria.builder().keyword("Rue").build(),
                        PageRequest.of(
                                0,
                                10,
                                field.equals("unsorted") ? Sort.unsorted() : Sort.by(field)));
        assertThat(result.getContent())
                .extracting(RealEstatePropertyResponse::getId)
                .containsExactly(2L, 1L);
    }

    @Test
    void outOfRangePageKeepsTheFilteredCount() {
        when(realEstateRepository.findAll(any(Specification.class)))
                .thenReturn(List.of(property(1)));
        Page<RealEstatePropertyResponse> result =
                realEstateService.searchProperties(
                        1L,
                        RealEstateSearchCriteria.builder().keyword("alpha").build(),
                        PageRequest.of(3, 1));
        assertThat(result.getContent()).isEmpty();
        assertThat(result.getTotalElements()).isEqualTo(1);
    }

    @ParameterizedTest
    @ValueSource(strings = {"name", "address", "notes"})
    void searchesEveryDecryptedTextFieldWithRegularExpressions(String field) {
        RealEstateProperty match = property(1);
        switch (field) {
            case "name" -> match.setName("Special matching home");
            case "address" -> match.setAddress("Special matching home");
            case "notes" -> match.setNotes("Special matching home");
        }
        when(realEstateRepository.findAll(any(Specification.class)))
                .thenReturn(List.of(property(2), match));
        Page<RealEstatePropertyResponse> result =
                realEstateService.searchProperties(
                        1L,
                        RealEstateSearchCriteria.builder()
                                .keyword("special.*home")
                                .keywordRegex(true)
                                .build(),
                        PageRequest.of(0, 1));
        assertThat(result.getContent())
                .extracting(RealEstatePropertyResponse::getId)
                .containsExactly(1L);
        assertThat(result.getTotalElements()).isEqualTo(1);
    }

    static Stream<Arguments> bounds() {
        return Stream.of(
                Arguments.of(
                        (Consumer<RealEstateSearchCriteria>)
                                c -> c.setValueMin(new BigDecimal("200.002")),
                        2L),
                Arguments.of(
                        (Consumer<RealEstateSearchCriteria>)
                                c -> c.setValueMax(new BigDecimal("200.001")),
                        1L),
                Arguments.of(
                        (Consumer<RealEstateSearchCriteria>)
                                c -> c.setValueExact(new BigDecimal("200.0010")),
                        1L),
                Arguments.of(
                        (Consumer<RealEstateSearchCriteria>)
                                c -> c.setPriceMin(new BigDecimal("100.002")),
                        2L),
                Arguments.of(
                        (Consumer<RealEstateSearchCriteria>)
                                c -> c.setPriceMax(new BigDecimal("100.001")),
                        1L),
                Arguments.of(
                        (Consumer<RealEstateSearchCriteria>)
                                c -> c.setRentalIncomeMin(new BigDecimal("10.002")),
                        2L));
    }

    @ParameterizedTest
    @MethodSource("bounds")
    void amountBoundsAreInclusiveAndAppliedBeforePagination(
            Consumer<RealEstateSearchCriteria> bound, long expected) {
        when(realEstateRepository.findAll(any(Specification.class)))
                .thenReturn(List.of(property(2), property(1)));
        RealEstateSearchCriteria criteria = new RealEstateSearchCriteria();
        bound.accept(criteria);
        Page<RealEstatePropertyResponse> result =
                realEstateService.searchProperties(1L, criteria, PageRequest.of(0, 1));
        assertThat(result.getContent())
                .extracting(RealEstatePropertyResponse::getId)
                .containsExactly(expected);
        assertThat(result.getTotalElements()).isEqualTo(1);
    }

    @ParameterizedTest
    @CsvSource({"NATIVE,1", "BASE,2"})
    void currencyModeChangesBothPriceSortAndBoundedMembership(
            AmountCurrencyMode mode, long expected) {
        RealEstateProperty foreign = property(1);
        foreign.setCurrency("EUR");
        when(exchangeRateService.getExchangeRate(eq("EUR"), eq("USD"), any()))
                .thenReturn(new BigDecimal("2"));
        when(realEstateRepository.findAll(any(Specification.class)))
                .thenReturn(List.of(property(2), foreign));
        Page<RealEstatePropertyResponse> result =
                realEstateService.searchProperties(
                        1L,
                        RealEstateSearchCriteria.builder()
                                .amountCurrencyMode(mode)
                                .priceMax(new BigDecimal("150"))
                                .build(),
                        PageRequest.of(0, 1, Sort.by("purchasePrice")));
        assertThat(result.getContent())
                .extracting(RealEstatePropertyResponse::getId)
                .containsExactly(expected);
        assertThat(result.getTotalElements()).isEqualTo(mode == AmountCurrencyMode.NATIVE ? 2 : 1);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   "})
    void blankKeywordUsesDatabasePaginationForUnencryptedOrdering(String keyword) {
        PageRequest pageable = PageRequest.of(0, 10, Sort.by("id"));
        when(realEstateRepository.findAll(any(Specification.class), eq(pageable)))
                .thenReturn(new PageImpl<>(List.of(property(1)), pageable, 1));
        assertThat(
                        realEstateService
                                .searchProperties(
                                        1L,
                                        RealEstateSearchCriteria.builder().keyword(keyword).build(),
                                        pageable)
                                .getContent())
                .extracting(RealEstatePropertyResponse::getId)
                .containsExactly(1L);
        verify(realEstateRepository, never()).findAll(any(Specification.class));
    }

    @Test
    void archivedAndSoldPropertiesAreAvailableOnlyWhenInactiveRecordsAreRequested() {
        RealEstateProperty sold = property(2);
        sold.setSoldDate(LocalDate.now());
        when(realEstateRepository.findByUserId(1L)).thenReturn(List.of(property(1), sold));
        when(realEstateRepository.findByUserIdAndIsActive(1L, true))
                .thenReturn(List.of(property(1), sold));
        assertThat(realEstateService.getPropertiesByUserId(1L, null, true))
                .extracting(RealEstatePropertyResponse::getId)
                .containsExactly(1L, 2L);
        assertThat(realEstateService.getPropertiesByUserId(1L, null, false))
                .extracting(RealEstatePropertyResponse::getId)
                .containsExactly(1L);
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "get-id",
                "get-user",
                "list-user",
                "search-user",
                "search-criteria",
                "search-page",
                "equity-id",
                "equity-user",
                "roi-id",
                "roi-user",
                "estimate-id",
                "estimate-user",
                "estimate-request"
            })
    void incompleteScopeOrRequestsCannotReadOrWritePropertyData(String invalid) {
        assertThatThrownBy(
                        () -> {
                            switch (invalid) {
                                case "get-id" -> realEstateService.getPropertyById(null, 1L);
                                case "get-user" -> realEstateService.getPropertyById(1L, null);
                                case "list-user" -> realEstateService.getPropertiesByUserId(
                                        null, null, false);
                                case "search-user" -> realEstateService.searchProperties(
                                        null,
                                        new RealEstateSearchCriteria(),
                                        PageRequest.of(0, 10));
                                case "search-criteria" -> realEstateService.searchProperties(
                                        1L, null, PageRequest.of(0, 10));
                                case "search-page" -> realEstateService.searchProperties(
                                        1L, new RealEstateSearchCriteria(), null);
                                case "equity-id" -> realEstateService.calculateEquity(null, 1L);
                                case "equity-user" -> realEstateService.calculateEquity(1L, null);
                                case "roi-id" -> realEstateService.calculateROI(null, 1L);
                                case "roi-user" -> realEstateService.calculateROI(1L, null);
                                case "estimate-id" -> realEstateService.estimateValue(
                                        null, 1L, null);
                                case "estimate-user" -> realEstateService.estimateValue(
                                        1L, null, null);
                                case "estimate-request" -> realEstateService.estimateValue(
                                        1L, 1L, null);
                            }
                        })
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(realEstateRepository, transactionRepository, assetService);
    }
}
