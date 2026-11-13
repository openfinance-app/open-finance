package org.openfinance.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.openfinance.dto.AdvancedSearchRequest;
import org.openfinance.dto.GlobalSearchResponse;
import org.openfinance.dto.SearchResultDto;
import org.openfinance.dto.SearchResultDto.SearchResultType;
import org.openfinance.entity.Account;
import org.openfinance.entity.AccountType;
import org.openfinance.entity.Asset;
import org.openfinance.entity.AssetType;
import org.openfinance.entity.Transaction;
import org.openfinance.entity.TransactionType;
import org.openfinance.repository.AccountRepository;
import org.openfinance.repository.AssetRepository;
import org.openfinance.repository.BudgetRepository;
import org.openfinance.repository.CategoryRepository;
import org.openfinance.repository.LiabilityRepository;
import org.openfinance.repository.RealEstateRepository;
import org.openfinance.repository.RecurringTransactionRepository;
import org.openfinance.repository.TransactionRepository;

@ExtendWith(MockitoExtension.class)
@DisplayName("Advanced search combines filters without losing boundary matches")
class SearchFiltersTest {
    private static final long USER_ID = 41L;
    private static final LocalDate FROM = LocalDate.of(2025, 1, 1);
    private static final LocalDate TO = LocalDate.of(2025, 12, 31);
    @Mock private TransactionRepository transactions;
    @Mock private AccountRepository accounts;
    @Mock private AssetRepository assets;
    @Mock private RealEstateRepository properties;
    @Mock private LiabilityRepository liabilities;
    @Mock private BudgetRepository budgets;
    @Mock private RecurringTransactionRepository recurring;
    @Mock private CategoryRepository categories;
    @Mock private SearchTokenService tokens;
    @Mock private CurrencyConversionHelper conversion;
    @InjectMocks private SearchService service;
    @Mock private SearchResultCurrencyEnricher currencyEnricher;

    @BeforeEach
    void nativeEuroAmounts() {
        lenient()
                .when(
                        conversion.convertTransaction(
                                any(), nullable(BigDecimal.class), nullable(String.class)))
                .thenAnswer(
                        invocation -> {
                            Transaction transaction = invocation.getArgument(0);
                            return new CurrencyConversionHelper.ConversionResult(
                                    "EUR", transaction.getAmount(), null, false, null, null, null);
                        });
        lenient()
                .when(
                        conversion.convert(
                                any(), any(), any(), anyBoolean(), nullable(Integer.class), any()))
                .thenAnswer(
                        invocation ->
                                new CurrencyConversionHelper.ConversionResult(
                                        "EUR",
                                        invocation.getArgument(2),
                                        null,
                                        false,
                                        null,
                                        null,
                                        null));
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "deleted",
                "account",
                "category",
                "uncategorized",
                "tooSmall",
                "tooLarge",
                "tooEarly",
                "tooLate",
                "tags",
                "untagged",
                "reconciled",
                "type",
                "query"
            })
    void everyTransactionFilterCanIndependentlyExcludeAMatch(String mismatch) {
        Transaction excluded = transaction(2L);
        switch (mismatch) {
            case "deleted" -> excluded.setIsDeleted(true);
            case "account" -> excluded.setAccountId(999L);
            case "category" -> excluded.setCategoryId(999L);
            case "uncategorized" -> excluded.setCategoryId(null);
            case "tooSmall" -> excluded.setAmount(new BigDecimal("9.999"));
            case "tooLarge" -> excluded.setAmount(new BigDecimal("20.001"));
            case "tooEarly" -> excluded.setDate(FROM.minusDays(1));
            case "tooLate" -> excluded.setDate(TO.plusDays(1));
            case "tags" -> excluded.setTags("work");
            case "untagged" -> excluded.setTags(null);
            case "reconciled" -> excluded.setIsReconciled(false);
            case "type" -> excluded.setType(TransactionType.INCOME);
            case "query" -> excluded.setDescription("Groceries");
            default -> throw new IllegalArgumentException(mismatch);
        }
        when(transactions.findByUserId(USER_ID)).thenReturn(List.of(excluded, transaction(1L)));

        GlobalSearchResponse response = service.advancedSearch(USER_ID, transactionRequest());

        assertThat(response.getResultsByType().get("TRANSACTION"))
                .extracting(SearchResultDto::getId)
                .containsExactly(1L);
        verifyNoInteractions(
                accounts, assets, properties, liabilities, budgets, recurring, categories, tokens);
    }

    @Test
    void amountAndDateBoundsAreInclusiveAndTransfersMatchTheirDestinationAccount() {
        Transaction destination = transaction(2L);
        destination.setAccountId(99L);
        destination.setToAccountId(7L);
        destination.setAmount(new BigDecimal("20.000"));
        destination.setDate(TO);
        when(transactions.findByUserId(USER_ID)).thenReturn(List.of(transaction(1L), destination));

        GlobalSearchResponse response = service.advancedSearch(USER_ID, transactionRequest());
        assertThat(response.getResultsByType().get("TRANSACTION"))
                .extracting(SearchResultDto::getId)
                .containsExactly(1L, 2L);
        assertThat(response.getCountsPerType()).containsEntry("TRANSACTION", 2);
    }

    @Test
    void absentAndEmptyFiltersIncludeUncategorizedAndUnreconciledTransactions() {
        Transaction transaction = transaction(1L);
        transaction.setCategoryId(null);
        transaction.setTags(null);
        transaction.setIsReconciled(false);
        when(transactions.findByUserId(USER_ID)).thenReturn(List.of(transaction));
        AdvancedSearchRequest request =
                AdvancedSearchRequest.builder()
                        .query("^rent$")
                        .regex(true)
                        .entityTypes(List.of(SearchResultType.TRANSACTION))
                        .accountIds(List.of())
                        .categoryIds(List.of())
                        .tags(List.of())
                        .build();

        assertThat(service.advancedSearch(USER_ID, request).getTotalResults()).isEqualTo(1);
        request.setAccountIds(null);
        request.setCategoryIds(null);
        request.setTags(null);
        assertThat(service.advancedSearch(USER_ID, request).getTotalResults()).isEqualTo(1);
        verifyNoInteractions(tokens);
    }

    @Test
    void accountSelectionAndTextMustBothMatch() {
        Account selected =
                Account.builder()
                        .id(7L)
                        .name("Savings")
                        .description("Rent reserve")
                        .type(AccountType.SAVINGS)
                        .balance(new BigDecimal("1500.01"))
                        .currency("EUR")
                        .build();
        Account wrongId =
                Account.builder().id(8L).name("Rent account").type(AccountType.CASH).build();
        Account wrongText =
                Account.builder()
                        .id(9L)
                        .name(null)
                        .description(null)
                        .type(AccountType.OTHER)
                        .build();
        when(accounts.findByUserIdAndIsActive(USER_ID, true))
                .thenReturn(List.of(wrongId, wrongText, selected));
        AdvancedSearchRequest request =
                AdvancedSearchRequest.builder()
                        .query("rent")
                        .entityTypes(List.of(SearchResultType.ACCOUNT))
                        .accountIds(List.of(7L, 9L))
                        .build();

        GlobalSearchResponse response = service.advancedSearch(USER_ID, request);
        assertThat(response.getTotalResults()).isEqualTo(1);
        SearchResultDto result = response.getResultsByType().get("ACCOUNT").getFirst();
        assertThat(result.getId()).isEqualTo(7L);
        assertThat(result.getAmount()).isEqualByComparingTo("1500.01");
        assertThat(result.getSnippet()).contains("Rent reserve");
        verifyNoInteractions(transactions, assets);
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "account",
                "unlinked",
                "tooSmall",
                "tooLarge",
                "tooEarly",
                "tooLate",
                "query"
            })
    void assetFiltersApplyToPurchaseDateAndTotalValue(String mismatch) {
        Asset excluded = asset(2L);
        switch (mismatch) {
            case "account" -> excluded.setAccountId(999L);
            case "unlinked" -> excluded.setAccountId(null);
            case "tooSmall" -> excluded.setCurrentPrice(new BigDecimal("9.999"));
            case "tooLarge" -> excluded.setCurrentPrice(new BigDecimal("20.001"));
            case "tooEarly" -> excluded.setPurchaseDate(FROM.minusDays(1));
            case "tooLate" -> excluded.setPurchaseDate(TO.plusDays(1));
            case "query" -> excluded.setSymbol("OTHER");
            default -> throw new IllegalArgumentException(mismatch);
        }
        when(assets.findByUserId(USER_ID)).thenReturn(List.of(excluded, asset(1L)));
        AdvancedSearchRequest request = assetRequest();

        GlobalSearchResponse response = service.advancedSearch(USER_ID, request);
        assertThat(response.getResultsByType().get("ASSET"))
                .extracting(SearchResultDto::getId)
                .containsExactly(1L);
        assertThat(response.getResultsByType().get("ASSET").getFirst().getAmount())
                .isEqualByComparingTo("30");
        verifyNoInteractions(transactions, accounts, tokens);
    }

    @Test
    void advancedAssetValueIncludesTheExactValuationRemainder() {
        Asset asset = asset(1L);
        asset.setValuationRemainder(new BigDecimal("0.00000001"));
        when(assets.findByUserId(USER_ID)).thenReturn(List.of(asset));

        GlobalSearchResponse response = service.advancedSearch(USER_ID, assetRequest());
        assertThat(response.getResultsByType().get("ASSET").getFirst().getAmount())
                .isEqualByComparingTo("30.00000001");
    }

    private AdvancedSearchRequest transactionRequest() {
        return AdvancedSearchRequest.builder()
                .query("rent")
                .entityTypes(List.of(SearchResultType.TRANSACTION))
                .accountIds(List.of(7L))
                .categoryIds(List.of(9L))
                .minAmount(new BigDecimal("10"))
                .maxAmount(new BigDecimal("20"))
                .dateFrom(FROM)
                .dateTo(TO)
                .tags(List.of("FAMILY", "personal"))
                .isReconciled(true)
                .transactionType(TransactionType.EXPENSE)
                .build();
    }

    private AdvancedSearchRequest assetRequest() {
        return AdvancedSearchRequest.builder()
                .query("rent")
                .entityTypes(List.of(SearchResultType.ASSET))
                .accountIds(List.of(7L))
                .minAmount(new BigDecimal("30"))
                .maxAmount(new BigDecimal("60"))
                .dateFrom(FROM)
                .dateTo(TO)
                .build();
    }

    private Transaction transaction(long id) {
        return Transaction.builder()
                .id(id)
                .userId(USER_ID)
                .accountId(7L)
                .categoryId(9L)
                .description("Rent")
                .amount(new BigDecimal("10.00"))
                .date(FROM)
                .currency("EUR")
                .tags("family,home")
                .isReconciled(true)
                .isDeleted(false)
                .type(TransactionType.EXPENSE)
                .build();
    }

    private Asset asset(long id) {
        return Asset.builder()
                .id(id)
                .userId(USER_ID)
                .accountId(7L)
                .name("Property fund")
                .symbol("RENT")
                .quantity(new BigDecimal("3"))
                .currentPrice(new BigDecimal("10"))
                .purchaseDate(FROM)
                .type(AssetType.ETF)
                .currency("EUR")
                .build();
    }
}
