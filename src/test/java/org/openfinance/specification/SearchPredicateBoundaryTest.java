package org.openfinance.specification;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.openfinance.dto.AccountSearchCriteria;
import org.openfinance.dto.AssetGroup;
import org.openfinance.dto.AssetSearchCriteria;
import org.openfinance.dto.RealEstateSearchCriteria;
import org.openfinance.dto.TransactionSearchCriteria;
import org.openfinance.entity.Account;
import org.openfinance.entity.AccountType;
import org.openfinance.entity.Asset;
import org.openfinance.entity.AssetType;
import org.openfinance.entity.Institution;
import org.openfinance.entity.PropertyType;
import org.openfinance.entity.RealEstateProperty;
import org.openfinance.entity.Transaction;
import org.openfinance.entity.TransactionType;
import org.openfinance.entity.User;
import org.openfinance.repository.AccountRepository;
import org.openfinance.repository.AssetRepository;
import org.openfinance.repository.InstitutionRepository;
import org.openfinance.repository.RealEstateRepository;
import org.openfinance.repository.TransactionRepository;
import org.openfinance.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@DisplayName("Database search predicates preserve ownership and combine optional filters")
class SearchPredicateBoundaryTest {
    @Autowired UserRepository users;
    @Autowired AccountRepository accounts;
    @Autowired AssetRepository assets;
    @Autowired InstitutionRepository institutions;
    @Autowired RealEstateRepository properties;
    @Autowired TransactionRepository transactions;
    private Long userId;
    private Account account;
    private static final LocalDate DATE = LocalDate.of(2025, 5, 10);

    @BeforeEach
    void setUp() {
        String name = "pred-" + UUID.randomUUID().toString().substring(0, 8);
        userId =
                users.save(
                                User.builder()
                                        .username(name)
                                        .email(name + "@example.test")
                                        .passwordHash("hash")
                                        .masterPasswordSalt("salt")
                                        .baseCurrency("EUR")
                                        .build())
                        .getId();
        account = accounts.save(account("Main", AccountType.CHECKING, "EUR", true));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" "})
    void blankAccountFiltersDoNotDropUnbankedOrClosedAccounts(String blank) {
        Account closed = accounts.save(account("Closed", AccountType.SAVINGS, "USD", false));
        Account deleted = account("Deleted", AccountType.SAVINGS, "USD", false);
        deleted.setDeletedDate(DATE);
        accounts.save(deleted);
        AccountSearchCriteria criteria =
                AccountSearchCriteria.builder().currency(blank).institution(blank).build();
        assertThat(accounts.findAll(AccountSpecification.buildSpecification(userId, criteria)))
                .extracting(Account::getId)
                .containsExactlyInAnyOrder(account.getId(), closed.getId());
    }

    @Test
    void accountTypeCurrencyStatusAndInstitutionAllConstrainTheResult() {
        Institution bank =
                institutions.save(
                        Institution.builder().name("BANK OF ITALY").userId(userId).build());
        account.setInstitution(bank);
        accounts.save(account);
        accounts.save(account("Wrong type", AccountType.SAVINGS, "EUR", true));
        accounts.save(account("Wrong currency", AccountType.CHECKING, "USD", true));
        accounts.save(account("Closed", AccountType.CHECKING, "EUR", false));
        AccountSearchCriteria criteria =
                AccountSearchCriteria.builder()
                        .type(AccountType.CHECKING)
                        .currency("EUR")
                        .isActive(true)
                        .institution("ITALY")
                        .build();
        Locale previous = Locale.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"));
            assertThat(accounts.findAll(AccountSpecification.buildSpecification(userId, criteria)))
                    .extracting(Account::getId)
                    .containsExactly(account.getId());
        } finally {
            Locale.setDefault(previous);
        }
        criteria.setIsActive(false);
        assertThat(accounts.findAll(AccountSpecification.buildSpecification(userId, criteria)))
                .isEmpty();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" "})
    void blankAssetCurrencyAndSymbolKeepAllOwnedTypes(String blank) {
        Asset stock = assets.save(asset(AssetType.STOCK, "IBM", "EUR", DATE));
        Asset car = assets.save(asset(AssetType.VEHICLE, null, "USD", DATE));
        AssetSearchCriteria criteria =
                AssetSearchCriteria.builder().currency(blank).symbol(blank).build();
        assertThat(assets.findAll(AssetSpecification.buildSpecification(userId, criteria)))
                .extracting(Asset::getId)
                .containsExactlyInAnyOrder(stock.getId(), car.getId());
    }

    @ParameterizedTest
    @ValueSource(strings = {"PHYSICAL", "FINANCIAL"})
    void workspaceGroupsExcludePropertyAndTheOppositeAssetKind(String group) {
        Asset stock = assets.save(asset(AssetType.STOCK, "IBM", "EUR", DATE));
        Asset car = assets.save(asset(AssetType.VEHICLE, null, "EUR", DATE));
        assets.save(asset(AssetType.REAL_ESTATE, null, "EUR", DATE));
        AssetSearchCriteria criteria =
                AssetSearchCriteria.builder().group(AssetGroup.valueOf(group)).build();
        assertThat(assets.findAll(AssetSpecification.buildSpecification(userId, criteria)))
                .extracting(Asset::getId)
                .containsExactly(group.equals("PHYSICAL") ? car.getId() : stock.getId());
    }

    @Test
    void assetFiltersIncludeBothPurchaseDateBoundariesAndIgnoreServerLocale() {
        Asset match = assets.save(asset(AssetType.STOCK, "IBM", "EUR", DATE));
        assets.save(asset(AssetType.STOCK, "IBM", "EUR", DATE.minusDays(1)));
        assets.save(asset(AssetType.STOCK, "IBM", "USD", DATE));
        AssetSearchCriteria criteria =
                AssetSearchCriteria.builder()
                        .accountId(account.getId())
                        .type(AssetType.STOCK)
                        .currency("EUR")
                        .symbol("IB")
                        .purchaseDateFrom(DATE)
                        .purchaseDateTo(DATE)
                        .build();
        Locale previous = Locale.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"));
            assertThat(assets.findAll(AssetSpecification.buildSpecification(userId, criteria)))
                    .extracting(Asset::getId)
                    .containsExactly(match.getId());
        } finally {
            Locale.setDefault(previous);
        }
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" "})
    void blankTransactionTextFiltersPreserveRowsButStillExcludeDeletedHistory(String blank) {
        Transaction match = transactions.save(transaction("Store", "food", false));
        transactions.save(transaction("Deleted", "food", true));
        TransactionSearchCriteria criteria =
                TransactionSearchCriteria.builder().keyword(blank).payee(blank).tags(blank).build();
        assertThat(
                        transactions.findAll(
                                TransactionSpecification.buildSpecification(
                                        userId, criteria, null)))
                .extracting(Transaction::getId)
                .containsExactly(match.getId());
    }

    @ParameterizedTest
    @ValueSource(strings = {"keyword", "payee", "tags"})
    void transactionTextSearchDoesNotDependOnTheServersTurkishLocale(String field) {
        Transaction match = transactions.save(transaction("IKEA", "INVESTMENT", false));
        transactions.save(transaction("Other shop", "food", false));
        TransactionSearchCriteria criteria = new TransactionSearchCriteria();
        switch (field) {
            case "keyword" -> criteria.setKeyword("IKEA");
            case "payee" -> criteria.setPayee("IKEA");
            case "tags" -> criteria.setTags("INVESTMENT");
            default -> throw new AssertionError(field);
        }
        Locale previous = Locale.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"));
            assertThat(
                            transactions.findAll(
                                    TransactionSpecification.buildSpecification(
                                            userId, criteria, null)))
                    .extracting(Transaction::getId)
                    .containsExactly(match.getId());
        } finally {
            Locale.setDefault(previous);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"null", "empty", "whitespace"})
    void missingPayeeFilterFindsAllFormsOfAbsence(String missing) {
        Transaction match =
                transactions.save(
                        transaction(
                                missing.equals("null") ? null : missing.equals("empty") ? "" : " ",
                                null,
                                false));
        transactions.save(transaction("Known", "food", false));
        TransactionSearchCriteria criteria =
                TransactionSearchCriteria.builder()
                        .noPayee(true)
                        .noCategory(true)
                        .accountId(account.getId())
                        .isReconciled(false)
                        .build();
        assertThat(
                        transactions.findAll(
                                TransactionSpecification.buildSpecification(
                                        userId, criteria, null)))
                .extracting(Transaction::getId)
                .containsExactly(match.getId());
    }

    @Test
    void prefilteredSplitMatchesAreNotDiscardedByParentTextOrCategory() {
        Transaction match = transactions.save(transaction("Parent", "parent-tag", false));
        transactions.save(transaction("Other", null, false));
        TransactionSearchCriteria criteria =
                TransactionSearchCriteria.builder()
                        .keyword("split keyword")
                        .categoryId(-99L)
                        .payee("split payee")
                        .tags("split tag")
                        .noCategory(true)
                        .noPayee(true)
                        .build();
        assertThat(
                        transactions.findAll(
                                TransactionSpecification.buildSpecification(
                                        userId, criteria, List.of(match.getId()))))
                .extracting(Transaction::getId)
                .containsExactly(match.getId());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" "})
    void blankPropertyTextFiltersDoNotExcludeAnOwnedProperty(String blank) {
        RealEstateProperty match = properties.save(property("Cabin", "Paris"));
        RealEstateSearchCriteria criteria =
                RealEstateSearchCriteria.builder().keyword(blank).currency(blank).build();
        assertThat(properties.findAll(RealEstateSpecification.buildSpecification(userId, criteria)))
                .extracting(RealEstateProperty::getId)
                .containsExactly(match.getId());
    }

    @ParameterizedTest
    @ValueSource(strings = {"name", "address"})
    void propertyTextSearchUsesStableCaseFolding(String field) {
        RealEstateProperty match =
                properties.save(
                        property(
                                field.equals("name") ? "ITALIAN CABIN" : "Cabin",
                                field.equals("address") ? "ITALY" : "Paris"));
        RealEstateSearchCriteria criteria =
                RealEstateSearchCriteria.builder()
                        .keyword("ITA")
                        .propertyType(PropertyType.RESIDENTIAL)
                        .currency("EUR")
                        .hasMortgage(false)
                        .purchaseDateFrom(DATE)
                        .purchaseDateTo(DATE)
                        .build();
        Locale previous = Locale.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"));
            assertThat(
                            properties.findAll(
                                    RealEstateSpecification.buildSpecification(userId, criteria)))
                    .extracting(RealEstateProperty::getId)
                    .containsExactly(match.getId());
        } finally {
            Locale.setDefault(previous);
        }
    }

    private Account account(String name, AccountType type, String currency, boolean active) {
        return Account.builder()
                .userId(userId)
                .name(name)
                .type(type)
                .currency(currency)
                .balance(BigDecimal.ZERO)
                .isActive(active)
                .build();
    }

    private Asset asset(AssetType type, String symbol, String currency, LocalDate date) {
        return Asset.builder()
                .userId(userId)
                .accountId(account.getId())
                .name("Asset")
                .type(type)
                .symbol(symbol)
                .currency(currency)
                .purchaseDate(date)
                .quantity(BigDecimal.ONE)
                .purchasePrice(BigDecimal.TEN)
                .currentPrice(BigDecimal.TEN)
                .build();
    }

    private Transaction transaction(String payee, String tags, boolean deleted) {
        return Transaction.builder()
                .userId(userId)
                .accountId(account.getId())
                .date(DATE)
                .type(TransactionType.EXPENSE)
                .amount(BigDecimal.TEN)
                .currency("EUR")
                .payee(payee)
                .tags(tags)
                .isDeleted(deleted)
                .isReconciled(false)
                .build();
    }

    private RealEstateProperty property(String name, String address) {
        return RealEstateProperty.builder()
                .userId(userId)
                .name(name)
                .address(address)
                .propertyType(PropertyType.RESIDENTIAL)
                .currency("EUR")
                .purchaseDate(DATE)
                .purchasePrice("100")
                .currentValue("100")
                .build();
    }
}
