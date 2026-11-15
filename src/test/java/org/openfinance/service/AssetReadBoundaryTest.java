package org.openfinance.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.function.Consumer;
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
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.openfinance.config.EncryptionProperties;
import org.openfinance.dto.AmountCurrencyMode;
import org.openfinance.dto.AssetResponse;
import org.openfinance.dto.AssetSearchCriteria;
import org.openfinance.dto.AssetSummaryResponse;
import org.openfinance.entity.AcquisitionType;
import org.openfinance.entity.Asset;
import org.openfinance.entity.AssetType;
import org.openfinance.mapper.AssetMapper;
import org.openfinance.mapper.AssetMapperImpl;
import org.openfinance.repository.AccountRepository;
import org.openfinance.repository.AssetRepository;
import org.openfinance.repository.TransactionRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

@ExtendWith(MockitoExtension.class)
@DisplayName("Asset read boundaries preserve precision, ownership and ordering")
class AssetReadBoundaryTest {
    @Mock private AssetRepository assets;
    @Mock private AccountRepository accounts;
    @Mock private TransactionRepository transactions;
    @Mock private EncryptionProperties encryption;
    @Mock private CurrencyConversionHelper conversions;
    @Spy private AssetMapper mapper = new AssetMapperImpl();
    @InjectMocks private AssetService service;

    @BeforeEach
    void nativeCurrency() {
        lenient()
                .when(
                        conversions.convert(
                                eq(7L), eq("EUR"), any(), anyBoolean(), isNull(), eq("asset")))
                .thenAnswer(
                        call ->
                                new CurrencyConversionHelper.ConversionResult(
                                        "EUR", call.getArgument(2), null, false, null, null, null));
    }

    private Asset asset(long id) {
        return Asset.builder()
                .id(id)
                .userId(7L)
                .name(id == 1 ? "alpha" : "Zulu")
                .type(AssetType.STOCK)
                .quantity(BigDecimal.valueOf(id))
                .purchasePrice(new BigDecimal(id == 1 ? "10.01" : "20.02"))
                .currentPrice(new BigDecimal(id == 1 ? "20.02" : "50.05"))
                .currency("EUR")
                .acquisitionType(AcquisitionType.PURCHASE)
                .purchaseDate(LocalDate.of(2020 + (int) id, 1, 1))
                .createdAt(LocalDate.of(2020 + (int) id, 1, 1).atStartOfDay())
                .build();
    }

    static Stream<Arguments> sortOrders() {
        return Stream.of(
                        "id",
                        "quantity",
                        "purchasePrice",
                        "currentPrice",
                        "totalValue",
                        "valueInBaseCurrency",
                        "unrealizedGain",
                        "gainPercentage",
                        "name",
                        "purchaseDate",
                        "createdAt")
                .flatMap(
                        field ->
                                Stream.of(Sort.Direction.ASC, Sort.Direction.DESC)
                                        .map(direction -> Arguments.of(field, direction)));
    }

    @ParameterizedTest
    @MethodSource("sortOrders")
    void sortsDecryptedAndCalculatedValuesBeforePaginating(String field, Sort.Direction direction) {
        when(assets.findAll(any(Specification.class))).thenReturn(List.of(asset(2), asset(1)));
        AssetSearchCriteria criteria =
                AssetSearchCriteria.builder().valueMin(BigDecimal.ZERO).build();
        Page<AssetResponse> first =
                service.searchAssets(7L, criteria, PageRequest.of(0, 1, Sort.by(direction, field)));
        Page<AssetResponse> second =
                service.searchAssets(7L, criteria, PageRequest.of(1, 1, Sort.by(direction, field)));
        long firstId = direction == Sort.Direction.ASC ? 1L : 2L;
        assertThat(first.getContent()).extracting(AssetResponse::getId).containsExactly(firstId);
        assertThat(second.getContent())
                .extracting(AssetResponse::getId)
                .containsExactly(3L - firstId);
        assertThat(first.getTotalElements()).isEqualTo(2);
    }

    @Test
    void composesSortKeysAndUsesIdForStableTies() {
        Asset second = asset(2);
        second.setName("ALPHA");
        second.setCurrentPrice(asset(1).getCurrentPrice());
        Asset third = asset(1);
        third.setId(3L);
        when(assets.findAll(any(Specification.class))).thenReturn(List.of(third, second, asset(1)));
        Page<AssetResponse> page =
                service.searchAssets(
                        7L,
                        AssetSearchCriteria.builder().build(),
                        PageRequest.of(0, 10, Sort.by("name", "currentPrice", "purchasePrice")));
        assertThat(page.getContent()).extracting(AssetResponse::getId).containsExactly(1L, 3L, 2L);
    }

    @ParameterizedTest
    @ValueSource(strings = {"unknown", "unsorted"})
    void unsupportedOrderingDoesNotDiscardFilteredMatches(String field) {
        when(assets.findAll(any(Specification.class))).thenReturn(List.of(asset(2), asset(1)));
        Page<AssetResponse> page =
                service.searchAssets(
                        7L,
                        AssetSearchCriteria.builder().valueMin(BigDecimal.ZERO).build(),
                        PageRequest.of(
                                0,
                                10,
                                field.equals("unsorted") ? Sort.unsorted() : Sort.by(field)));
        assertThat(page.getContent()).extracting(AssetResponse::getId).containsExactly(2L, 1L);
    }

    @Test
    void paginationBeyondTheLastMatchKeepsTheTotalAndReturnsNoRows() {
        when(assets.findAll(any(Specification.class))).thenReturn(List.of(asset(1)));
        Page<AssetResponse> page =
                service.searchAssets(
                        7L,
                        AssetSearchCriteria.builder().keyword("alpha").build(),
                        PageRequest.of(3, 10));
        assertThat(page.getContent()).isEmpty();
        assertThat(page.getTotalElements()).isEqualTo(1);
    }

    @ParameterizedTest
    @ValueSource(strings = {"name", "symbol", "notes", "brand", "model", "serialNumber"})
    void matchesEachSearchableFieldWithoutLosingOtherMatches(String field) {
        Asset first = asset(1);
        switch (field) {
            case "name" -> first.setName("Unique Match");
            case "symbol" -> first.setSymbol("Unique Match");
            case "notes" -> first.setNotes("Unique Match");
            case "brand" -> first.setBrand("Unique Match");
            case "model" -> first.setModel("Unique Match");
            case "serialNumber" -> first.setSerialNumber("Unique Match");
            default -> throw new AssertionError(field);
        }
        when(assets.findAll(any(Specification.class))).thenReturn(List.of(asset(2), first));
        Page<AssetResponse> page =
                service.searchAssets(
                        7L,
                        AssetSearchCriteria.builder()
                                .keyword("unique.*match")
                                .keywordRegex(true)
                                .build(),
                        PageRequest.of(0, 10));
        assertThat(page.getContent()).extracting(AssetResponse::getId).containsExactly(1L);
    }

    @Test
    void exactNativeValueUsesDecimalEquality() {
        Asset first = asset(1);
        first.setCurrentPrice(new BigDecimal("9007199254740993.01"));
        when(assets.findAll(any(Specification.class))).thenReturn(List.of(asset(2), first));
        Page<AssetResponse> page =
                service.searchAssets(
                        7L,
                        AssetSearchCriteria.builder()
                                .valueExact(new BigDecimal("9007199254740993.010"))
                                .amountCurrencyMode(AmountCurrencyMode.NATIVE)
                                .build(),
                        PageRequest.of(0, 10));
        assertThat(page.getContent()).extracting(AssetResponse::getId).containsExactly(1L);
    }

    @Test
    void sparseSummariesKeepExactValuesAndRepresentMissingQuotesAsUnknown() {
        Asset priced = asset(1);
        priced.setQuantity(new BigDecimal("3.00000001"));
        priced.setCurrentPrice(new BigDecimal("9007199254740993.01"));
        Asset missingQuote = asset(2);
        missingQuote.setCurrentPrice(null);
        Asset locked = asset(3);
        locked.setQuantity(null);
        locked.setName(null);
        when(assets.findByUserId(7L)).thenReturn(List.of(priced, missingQuote, locked));
        List<AssetSummaryResponse> result = service.getAssetsSummary(7L);
        assertThat(result).hasSize(3);
        assertThat(result.get(0).getTotalValue())
                .isEqualByComparingTo("27021597854294971.5774099301");
        assertThat(result.get(0).getType()).isEqualTo("STOCK");
        assertThat(result.get(1).getTotalValue()).isNull();
        assertThat(result.get(2).getTotalValue()).isNull();
        assertThat(result.get(2).getName()).isNull();
        verifyNoInteractions(conversions, mapper);
    }

    @Test
    void portfolioTotalsExcludeSoldAndPlannedAssetsInEachCurrency() {
        Asset owned = asset(1);
        Asset sold = asset(2);
        sold.setSoldDate(LocalDate.of(2025, 1, 1));
        Asset planned = asset(3);
        planned.setAcquisitionType(AcquisitionType.PLANNED);
        Asset foreign = asset(1);
        foreign.setCurrency("USD");
        when(assets.findByUserId(7L)).thenReturn(List.of(owned, sold, planned, foreign));
        assertThat(service.getTotalValueByCurrency(7L))
                .containsOnlyKeys("EUR", "USD")
                .containsEntry("EUR", new BigDecimal("20.02"))
                .containsEntry("USD", new BigDecimal("20.02"));
        assertThat(service.getTotalCostByCurrency(7L))
                .containsOnlyKeys("EUR", "USD")
                .containsEntry("EUR", new BigDecimal("10.01"))
                .containsEntry("USD", new BigDecimal("10.01"));
    }

    static Stream<Arguments> invalidReads() {
        return Stream.<Consumer<AssetService>>of(
                        s -> s.getAssetsSummary(null),
                        s -> s.getAssetsByUserId(null),
                        s -> s.getAssetsByAccountId(null, 7L),
                        s -> s.getAssetsByAccountId(1L, null),
                        s -> s.getAssetsByType(null, AssetType.STOCK),
                        s -> s.getAssetsByType(7L, null),
                        s -> s.getTotalValueByCurrency(null),
                        s -> s.getTotalCostByCurrency(null),
                        s -> s.searchAssets(null, new AssetSearchCriteria(), PageRequest.of(0, 10)),
                        s -> s.searchAssets(7L, null, PageRequest.of(0, 10)),
                        s -> s.searchAssets(7L, new AssetSearchCriteria(), null))
                .map(Arguments::of);
    }

    @ParameterizedTest
    @MethodSource("invalidReads")
    void rejectsMissingReadScopeBeforeAccessingAnyPortfolio(Consumer<AssetService> read) {
        assertThatThrownBy(() -> read.accept(service)).isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(assets, accounts, transactions);
    }
}
