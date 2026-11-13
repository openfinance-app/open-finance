package org.openfinance.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.NullSource;
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
import org.openfinance.entity.Budget;
import org.openfinance.entity.BudgetPeriod;
import org.openfinance.entity.Category;
import org.openfinance.entity.Institution;
import org.openfinance.entity.Liability;
import org.openfinance.entity.LiabilityType;
import org.openfinance.entity.RealEstateProperty;
import org.openfinance.entity.RecurringFrequency;
import org.openfinance.entity.RecurringTransaction;
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
import org.openfinance.security.EncryptionContext;

@ExtendWith(MockitoExtension.class)
@DisplayName("Search result integrity, filtering and encryption boundaries")
class SearchServiceTest {
    private static final long USER_ID = 41L;
    private static final LocalDate DATE = LocalDate.of(2025, 6, 15);
    @Mock private TransactionRepository transactions;
    @Mock private AccountRepository accounts;
    @Mock private AssetRepository assets;
    @Mock private RealEstateRepository properties;
    @Mock private LiabilityRepository liabilities;
    @Mock private BudgetRepository budgets;
    @Mock private RecurringTransactionRepository recurring;
    @Mock private CategoryRepository categories;
    @Mock private SearchTokenService tokens;
    @Mock private SearchResultCurrencyEnricher currencyEnricher;
    @InjectMocks private SearchService service;

    @AfterEach
    void clearEncryptionContext() {
        EncryptionContext.clear();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t\n"})
    void rejectsBlankQueriesBeforeReadingAnyData(String query) {
        assertThatThrownBy(() -> service.globalSearch(USER_ID, query, 10, false))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(
                        () ->
                                service.advancedSearch(
                                        USER_ID,
                                        AdvancedSearchRequest.builder().query(query).build()))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(
                transactions,
                accounts,
                assets,
                properties,
                liabilities,
                budgets,
                recurring,
                categories,
                tokens);
    }

    @Test
    void rejectsMissingAdvancedRequest() {
        assertThatThrownBy(() -> service.advancedSearch(USER_ID, null))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(transactions);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(ints = {-1, 0, 101})
    void invalidServiceLimitsUseTheDocumentedDefault(Integer limit) {
        GlobalSearchResponse global = service.globalSearch(USER_ID, "trip", limit, false);
        GlobalSearchResponse advanced =
                service.advancedSearch(
                        USER_ID,
                        AdvancedSearchRequest.builder().query("trip").limit(limit).build());
        for (GlobalSearchResponse response : List.of(global, advanced)) {
            assertThat(response.getLimit()).isEqualTo(50);
            assertThat(response.getTotalResults()).isZero();
            assertThat(response.getResultsByType()).isEmpty();
            assertThat(response.getCountsPerType()).isEmpty();
            assertThat(response.getHasMore()).isFalse();
        }
    }

    @Test
    void groupsAllEntityTypesWithoutLosingAmountsOrNavigationMetadata() {
        seedEveryEntityType();
        GlobalSearchResponse response = service.globalSearch(USER_ID, "TRIP", 20, false);

        assertThat(response.getQuery()).isEqualTo("TRIP");
        assertThat(response.getTotalResults()).isEqualTo(8);
        assertThat(response.getCountsPerType())
                .hasSize(8)
                .allSatisfy(
                        (type, count) -> {
                            assertThat(count).isEqualTo(1);
                            assertThat(response.getResultsByType().get(type)).hasSize(count);
                        });
        assertThat(response.getHasMore()).isFalse();
        assertThat(response.getExecutionTimeMs()).isNotNegative();
        assertThat(result(response, SearchResultType.TRANSACTION).getTags())
                .containsExactly("travel", "family");
        assertThat(result(response, SearchResultType.TRANSACTION).getDate()).isEqualTo(DATE);
        assertThat(result(response, SearchResultType.ACCOUNT).getAmount())
                .isEqualByComparingTo("1234.56");
        assertThat(result(response, SearchResultType.ASSET).getAmount())
                .isEqualByComparingTo("37.035");
        assertThat(result(response, SearchResultType.REAL_ESTATE).getAmount())
                .isEqualByComparingTo("150000.01");
        assertThat(result(response, SearchResultType.LIABILITY).getAmount())
                .isEqualByComparingTo("300.12");
        assertThat(result(response, SearchResultType.BUDGET).getTitle()).isEqualTo("Trip savings");
        assertThat(result(response, SearchResultType.BUDGET).getAmount())
                .isEqualByComparingTo("250.50");
        assertThat(result(response, SearchResultType.RECURRING_TRANSACTION).getDate())
                .isEqualTo(DATE);
        assertThat(result(response, SearchResultType.CATEGORY).getIcon()).isEqualTo("Tag");
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void limitAppliesAcrossEntityTypesAndCountsDescribeOnlyReturnedResults(boolean advanced) {
        when(transactions.findByUserId(USER_ID)).thenReturn(List.of(transaction(1L)));
        if (advanced) {
            when(accounts.findByUserIdAndIsActive(USER_ID, true)).thenReturn(List.of(account()));
        } else {
            when(accounts.findByUserIdWithInstitution(USER_ID)).thenReturn(List.of(account()));
        }
        GlobalSearchResponse response = search("trip", 1, advanced);

        assertThat(response.getTotalResults()).isEqualTo(1);
        assertThat(response.getResultsByType().values().stream().mapToInt(List::size).sum())
                .isEqualTo(1);
        assertThat(response.getCountsPerType().values().stream().mapToInt(Integer::intValue).sum())
                .isEqualTo(1);
        assertThat(response.getHasMore()).isTrue();
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void fullPageDoesNotClaimMoreResultsUnlessAnotherMatchExists(boolean advanced) {
        when(transactions.findByUserId(USER_ID)).thenReturn(List.of(transaction(1L)));
        GlobalSearchResponse response = search("trip", 1, advanced);
        assertThat(response.getTotalResults()).isEqualTo(1);
        assertThat(response.getHasMore()).isFalse();
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void additionalMatchInOneTypeSetsHasMore(boolean advanced) {
        when(transactions.findByUserId(USER_ID))
                .thenReturn(List.of(transaction(1L), transaction(2L)));
        GlobalSearchResponse response = search("trip", 1, advanced);
        assertThat(response.getTotalResults()).isEqualTo(1);
        assertThat(response.getHasMore()).isTrue();
    }

    @Test
    void matchesPayeeNotesAndTagsButNeverReturnsDeletedTransactions() {
        Transaction payee = transaction(1L);
        payee.setDescription(null);
        payee.setTags(null);
        payee.setPayee("Trip agency");
        Transaction notes = transaction(2L);
        notes.setDescription("Plane tickets");
        notes.setNotes("Trip booking");
        Transaction tags = transaction(3L);
        tags.setDescription("Hotel");
        tags.setTags("summer,trip");
        Transaction deleted = transaction(4L);
        deleted.setIsDeleted(true);
        Transaction unrelated = transaction(5L);
        unrelated.setDescription("Groceries");
        when(transactions.findByUserId(USER_ID))
                .thenReturn(List.of(deleted, unrelated, payee, notes, tags));

        GlobalSearchResponse response = service.globalSearch(USER_ID, "trip", 10, false);
        assertThat(response.getResultsByType().get("TRANSACTION"))
                .extracting(SearchResultDto::getId)
                .containsExactly(1L, 2L, 3L);
        assertThat(response.getResultsByType().get("TRANSACTION").getFirst().getTitle())
                .isEqualTo("No description");
        assertThat(response.getResultsByType().get("TRANSACTION").getFirst().getTags()).isEmpty();
        verifyNoInteractions(tokens);
    }

    @ParameterizedTest
    @ValueSource(strings = {"number", "institution", "description"})
    void accountSearchIncludesFieldsBeyondItsName(String field) {
        Account account = account();
        account.setName("Checking");
        switch (field) {
            case "number" -> account.setAccountNumber("TRIP-1234");
            case "institution" -> account.setInstitution(
                    Institution.builder().name("Trip Bank").build());
            case "description" -> account.setDescription("Trip expenses");
            default -> throw new IllegalArgumentException(field);
        }
        when(accounts.findByUserIdWithInstitution(USER_ID)).thenReturn(List.of(account));
        assertThat(
                        result(
                                        service.globalSearch(USER_ID, "trip", 10, false),
                                        SearchResultType.ACCOUNT)
                                .getId())
                .isEqualTo(account.getId());
    }

    @Test
    void malformedRecordsAndFailedRepositoriesDoNotHideOtherResults() {
        Asset brokenAsset = asset();
        brokenAsset.setQuantity(null);
        RealEstateProperty brokenProperty = property();
        brokenProperty.setCurrentValue("invalid");
        when(assets.findByUserId(USER_ID)).thenReturn(List.of(brokenAsset, asset()));
        when(properties.findByUserId(USER_ID)).thenReturn(List.of(brokenProperty, property()));
        when(liabilities.findByUserIdOrderByCreatedAtDesc(USER_ID))
                .thenThrow(new IllegalStateException("Unavailable"));
        when(accounts.findByUserIdWithInstitution(USER_ID)).thenReturn(List.of(account()));

        GlobalSearchResponse response = service.globalSearch(USER_ID, "trip", 20, false);
        assertThat(response.getTotalResults()).isEqualTo(3);
        assertThat(response.getResultsByType()).containsOnlyKeys("ACCOUNT", "ASSET", "REAL_ESTATE");
    }

    @Test
    void inactivePropertyDoesNotAppearEvenWhenItsAddressMatches() {
        RealEstateProperty inactive = property();
        inactive.setActive(false);
        RealEstateProperty active = property();
        active.setName("Cabin");
        active.setAddress("Trip Street");
        when(properties.findByUserId(USER_ID)).thenReturn(List.of(inactive, active));

        GlobalSearchResponse response = service.globalSearch(USER_ID, "trip", 10, false);
        assertThat(response.getTotalResults()).isEqualTo(1);
        assertThat(result(response, SearchResultType.REAL_ESTATE).getTitle()).isEqualTo("Cabin");
    }

    @Test
    void propagatesTheRequestKeyToAsyncSearchAndPreservesTheCallerContext() {
        SecretKey key = new SecretKeySpec(new byte[32], "AES");
        SecretKey searchKey = new SecretKeySpec(new byte[32], "HmacSHA256");
        EncryptionContext.setKey(key);
        when(tokens.deriveSearchKey(key)).thenReturn(searchKey);
        when(tokens.search(eq(USER_ID), eq("TRANSACTION"), eq("trip"), eq(searchKey), anyInt()))
                .thenReturn(List.of());
        when(transactions.findByUserId(USER_ID))
                .thenAnswer(
                        call -> {
                            assertThat(EncryptionContext.getKey()).isSameAs(key);
                            return List.of(transaction(1L));
                        });
        when(accounts.findByUserIdWithInstitution(USER_ID))
                .thenAnswer(
                        call -> {
                            assertThat(EncryptionContext.getKey()).isSameAs(key);
                            return List.of(account());
                        });

        assertThat(service.globalSearch(USER_ID, "trip", 10, false).getTotalResults()).isEqualTo(2);
        assertThat(EncryptionContext.getKey()).isSameAs(key);
        verify(tokens).deriveSearchKey(key);
    }

    @Test
    void indexedSearchSkipsMissingAndDeletedRecords() {
        SecretKey key = new SecretKeySpec(new byte[32], "AES");
        EncryptionContext.setKey(key);
        when(tokens.deriveSearchKey(key)).thenReturn(key);
        when(tokens.search(eq(USER_ID), eq("TRANSACTION"), eq("trip"), eq(key), anyInt()))
                .thenReturn(List.of(1L, 2L, 3L));
        Transaction deleted = transaction(2L);
        deleted.setIsDeleted(true);
        when(transactions.findById(1L)).thenReturn(Optional.of(transaction(1L)));
        when(transactions.findById(2L)).thenReturn(Optional.of(deleted));
        when(transactions.findById(3L)).thenReturn(Optional.empty());

        GlobalSearchResponse response = service.globalSearch(USER_ID, "trip", 10, false);
        assertThat(response.getResultsByType().get("TRANSACTION"))
                .extracting(SearchResultDto::getId)
                .containsExactly(1L);
    }

    @Test
    void regexBypassesTheTokenIndexAndInvalidPatternsReturnNoMatches() {
        EncryptionContext.setKey(new SecretKeySpec(new byte[32], "AES"));
        when(transactions.findByUserId(USER_ID)).thenReturn(List.of(transaction(1L)));

        assertThat(service.globalSearch(USER_ID, "^TR.P", 10, true).getTotalResults()).isEqualTo(1);
        assertThat(service.globalSearch(USER_ID, "[", 10, true).getTotalResults()).isZero();
        verifyNoInteractions(tokens);
    }

    @Test
    void snippetsRetainMatchContextInLongNotes() {
        Transaction transaction = transaction(1L);
        transaction.setDescription("Receipt");
        transaction.setNotes("before ".repeat(30) + "trip confirmation" + " after".repeat(30));
        when(transactions.findByUserId(USER_ID)).thenReturn(List.of(transaction));

        SearchResultDto result =
                result(
                        service.globalSearch(USER_ID, "trip", 10, false),
                        SearchResultType.TRANSACTION);
        assertThat(result.getSnippet())
                .contains("trip confirmation")
                .startsWith("...")
                .endsWith("...");
        assertThat(result.getSnippet().length()).isLessThan(transaction.getNotes().length());
    }

    private GlobalSearchResponse search(String query, int limit, boolean advanced) {
        return advanced
                ? service.advancedSearch(
                        USER_ID, AdvancedSearchRequest.builder().query(query).limit(limit).build())
                : service.globalSearch(USER_ID, query, limit, false);
    }

    private SearchResultDto result(GlobalSearchResponse response, SearchResultType type) {
        assertThat(response.getResultsByType()).containsKey(type.name());
        assertThat(response.getResultsByType().get(type.name())).hasSize(1);
        return response.getResultsByType().get(type.name()).getFirst();
    }

    private void seedEveryEntityType() {
        Category category =
                Category.builder()
                        .id(8L)
                        .userId(USER_ID)
                        .name("Trip savings")
                        .color("#123456")
                        .build();
        when(transactions.findByUserId(USER_ID)).thenReturn(List.of(transaction(1L)));
        when(accounts.findByUserIdWithInstitution(USER_ID)).thenReturn(List.of(account()));
        when(assets.findByUserId(USER_ID)).thenReturn(List.of(asset()));
        when(properties.findByUserId(USER_ID)).thenReturn(List.of(property()));
        when(liabilities.findByUserIdOrderByCreatedAtDesc(USER_ID))
                .thenReturn(List.of(liability()));
        when(budgets.findByUserId(USER_ID))
                .thenReturn(
                        List.of(
                                Budget.builder()
                                        .id(6L)
                                        .userId(USER_ID)
                                        .category(category)
                                        .amount("250.50")
                                        .currency("EUR")
                                        .period(BudgetPeriod.MONTHLY)
                                        .build()));
        when(recurring.findByUserId(USER_ID))
                .thenReturn(
                        List.of(
                                RecurringTransaction.builder()
                                        .id(7L)
                                        .userId(USER_ID)
                                        .description("Trip savings")
                                        .type(TransactionType.EXPENSE)
                                        .amount(new BigDecimal("10.50"))
                                        .currency("EUR")
                                        .frequency(RecurringFrequency.MONTHLY)
                                        .nextOccurrence(DATE)
                                        .build()));
        when(categories.findByUserId(USER_ID)).thenReturn(List.of(category));
    }

    private Liability liability() {
        Liability liability = new Liability();
        liability.setId(5L);
        liability.setUserId(USER_ID);
        liability.setName("Trip loan");
        liability.setType(LiabilityType.PERSONAL_LOAN);
        liability.setCurrentBalance("300.12");
        liability.setCurrency("EUR");
        liability.setStartDate(DATE);
        return liability;
    }

    private Transaction transaction(long id) {
        return Transaction.builder()
                .id(id)
                .userId(USER_ID)
                .accountId(2L)
                .description("Trip payment")
                .amount(new BigDecimal("25.50"))
                .type(TransactionType.EXPENSE)
                .date(DATE)
                .currency("EUR")
                .tags("travel,family")
                .isDeleted(false)
                .build();
    }

    private Account account() {
        return Account.builder()
                .id(2L)
                .userId(USER_ID)
                .name("Trip account")
                .type(AccountType.CHECKING)
                .balance(new BigDecimal("1234.56"))
                .currency("EUR")
                .build();
    }

    private Asset asset() {
        return Asset.builder()
                .id(3L)
                .userId(USER_ID)
                .name("Trip fund")
                .symbol("TRIP")
                .type(AssetType.ETF)
                .quantity(new BigDecimal("3"))
                .currentPrice(new BigDecimal("12.345"))
                .currency("EUR")
                .purchaseDate(DATE)
                .build();
    }

    private RealEstateProperty property() {
        return RealEstateProperty.builder()
                .id(4L)
                .userId(USER_ID)
                .name("Trip cabin")
                .address("1 Lake Street")
                .currentValue("150000.01")
                .currency("EUR")
                .purchaseDate(DATE)
                .isActive(true)
                .build();
    }
}
