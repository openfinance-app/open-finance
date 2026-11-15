package org.openfinance.dto;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@DisplayName(
        "Search JSON distinguishes meaningful filters from display options and empty selections")
class SearchFilterContractTest {
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();

    @ParameterizedTest
    @ValueSource(
            strings = {
                "{\"keyword\":\"salary\"}",
                "{\"accountId\":1}",
                "{\"categoryId\":2}",
                "{\"type\":\"EXPENSE\"}",
                "{\"excludeTransfers\":true}",
                "{\"dateFrom\":\"2025-01-01\"}",
                "{\"dateTo\":\"2025-01-01\"}",
                "{\"amountMin\":0}",
                "{\"amountMax\":0}",
                "{\"amountExact\":0}",
                "{\"tags\":\"work\"}",
                "{\"tag\":\"work\"}",
                "{\"payee\":\"company\"}",
                "{\"isReconciled\":false}",
                "{\"isReconciled\":true}",
                "{\"noCategory\":true}",
                "{\"noPayee\":true}",
                "{\"realEstateId\":3}",
                "{\"assetId\":4}",
                "{\"liabilityId\":5}"
            })
    void transactionFilterCannotDisappearWhenItIsTheOnlyFilter(String json) throws Exception {
        assertThat(mapper.readValue(json, TransactionSearchCriteria.class).hasAnyCriteria())
                .isTrue();
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "{}",
                "{\"keywordRegex\":true}",
                "{\"amountCurrencyMode\":\"NATIVE\"}",
                "{\"includeSubcategories\":true}",
                "{\"payeeExact\":true}",
                "{\"excludeTransfers\":false,\"noCategory\":false,\"noPayee\":false}"
            })
    void transactionMatchOptionsAloneDoNotNarrowTheResult(String json) throws Exception {
        assertThat(mapper.readValue(json, TransactionSearchCriteria.class).hasAnyCriteria())
                .isFalse();
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "{\"keyword\":\"savings\"}", "{\"type\":\"SAVINGS\"}", "{\"currency\":\"EUR\"}",
                "{\"isActive\":false}", "{\"isActive\":true}", "{\"balanceMin\":0}",
                "{\"balanceMax\":0}", "{\"balanceExact\":0}", "{\"institution\":\"bank\"}",
                "{\"lowBalance\":true}"
            })
    void closedAccountsAndZeroBalancesAreRealFilters(String json) throws Exception {
        assertThat(mapper.readValue(json, AccountSearchCriteria.class).hasAnyCriteria()).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"lowBalance\":false}", "{\"keywordRegex\":true}"})
    void inactiveAccountOptionsDoNotNarrowTheResult(String json) throws Exception {
        assertThat(mapper.readValue(json, AccountSearchCriteria.class).hasAnyCriteria()).isFalse();
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "{\"keyword\":\"bike\"}",
                "{\"group\":\"PHYSICAL\"}",
                "{\"ownershipStatus\":\"CLOSED\"}",
                "{\"type\":\"VEHICLE\"}",
                "{\"accountId\":1}",
                "{\"currency\":\"USD\"}",
                "{\"symbol\":\"AAPL\"}",
                "{\"purchaseDateFrom\":\"2025-01-01\"}",
                "{\"purchaseDateTo\":\"2025-01-01\"}",
                "{\"valueMin\":0}",
                "{\"valueMax\":0}",
                "{\"valueExact\":0}"
            })
    void assetWorkspaceStatusAndValueFiltersAreRecognized(String json) throws Exception {
        assertThat(mapper.readValue(json, AssetSearchCriteria.class).hasAnyCriteria()).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"keywordRegex\":true}"})
    void assetMatchOptionsWithoutAValueAreNotFilters(String json) throws Exception {
        assertThat(mapper.readValue(json, AssetSearchCriteria.class).hasAnyCriteria()).isFalse();
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "{\"keyword\":\"home\"}",
                "{\"propertyType\":\"RESIDENTIAL\"}",
                "{\"currency\":\"EUR\"}",
                "{\"isActive\":false}",
                "{\"hasMortgage\":false}",
                "{\"purchaseDateFrom\":\"2025-01-01\"}",
                "{\"purchaseDateTo\":\"2025-01-01\"}",
                "{\"valueMin\":0}",
                "{\"valueMax\":0}",
                "{\"valueExact\":0}",
                "{\"priceMin\":0}",
                "{\"priceMax\":0}",
                "{\"rentalIncomeMin\":0}"
            })
    void mortgageFreePropertiesAndZeroRentalIncomeAreRealFilters(String json) throws Exception {
        assertThat(mapper.readValue(json, RealEstateSearchCriteria.class).hasAnyCriteria())
                .isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"keywordRegex\":true}"})
    void propertyMatchOptionsWithoutAValueAreNotFilters(String json) throws Exception {
        assertThat(mapper.readValue(json, RealEstateSearchCriteria.class).hasAnyCriteria())
                .isFalse();
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "{\"accountIds\":[1]}",
                "{\"categoryIds\":[2]}",
                "{\"minAmount\":0}",
                "{\"maxAmount\":0}",
                "{\"exactAmount\":0}",
                "{\"dateFrom\":\"2025-01-01\"}",
                "{\"dateTo\":\"2025-01-01\"}",
                "{\"tags\":[\"work\"]}",
                "{\"isReconciled\":false}",
                "{\"transactionType\":\"EXPENSE\"}"
            })
    void advancedSearchRecognizesEachTransactionFilterIndependently(String json) throws Exception {
        AdvancedSearchRequest request = mapper.readValue(json, AdvancedSearchRequest.class);
        assertThat(request.hasAdvancedFilters()).isTrue();
        assertThat(request.hasTransactionFilters()).isTrue();
        assertThat(request.hasAmountFilter()).isEqualTo(json.contains("Amount"));
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "{}",
                "{\"query\":\"salary\",\"limit\":10,\"regex\":true}",
                "{\"entityTypes\":[],\"accountIds\":[],\"categoryIds\":[],\"tags\":[]}"
            })
    void emptySelectionsDoNotActivateAdvancedFiltering(String json) throws Exception {
        AdvancedSearchRequest request = mapper.readValue(json, AdvancedSearchRequest.class);
        assertThat(request.hasAdvancedFilters()).isFalse();
        assertThat(request.hasTransactionFilters()).isFalse();
        assertThat(request.hasAmountFilter()).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {"ACCOUNT", "ASSET", "TRANSACTION"})
    void selectingAnEntityTypeIsNotATransactionFieldFilter(String type) throws Exception {
        AdvancedSearchRequest request =
                mapper.readValue(
                        "{\"entityTypes\":[\"" + type + "\"]}", AdvancedSearchRequest.class);
        assertThat(request.hasAdvancedFilters()).isTrue();
        assertThat(request.hasTransactionFilters()).isFalse();
    }
}
