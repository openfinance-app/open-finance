package org.openfinance.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.function.BiConsumer;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.openfinance.config.EncryptionProperties;
import org.openfinance.entity.Account;
import org.openfinance.entity.Asset;
import org.openfinance.entity.Budget;
import org.openfinance.entity.Category;
import org.openfinance.entity.Institution;
import org.openfinance.entity.Liability;
import org.openfinance.entity.Payee;
import org.openfinance.entity.RealEstateProperty;
import org.openfinance.entity.RecurringTransaction;
import org.openfinance.entity.Transaction;
import org.openfinance.entity.TransactionType;
import org.openfinance.entity.User;
import org.openfinance.repository.AccountRepository;
import org.openfinance.repository.AssetRepository;
import org.openfinance.repository.BudgetRepository;
import org.openfinance.repository.CategoryRepository;
import org.openfinance.repository.InstitutionRepository;
import org.openfinance.repository.LiabilityRepository;
import org.openfinance.repository.PayeeRepository;
import org.openfinance.repository.RealEstateRepository;
import org.openfinance.repository.RecurringTransactionRepository;
import org.openfinance.repository.TransactionRepository;
import org.openfinance.repository.UserRepository;
import org.openfinance.security.EncryptionContext;
import org.openfinance.security.EncryptionKeyCache;
import org.openfinance.security.KeyManagementService;
import org.openfinance.security.PasswordService;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
@DisplayName("Demo data must remain usable, private, and repeatable")
class DemoDataSeedingTest {
    private static final long USER_ID = 71L;
    private static final SecretKey KEY = new SecretKeySpec(new byte[32], "AES");
    @Mock private UserRepository userRepository;
    @Mock private CategoryRepository categoryRepository;
    @Mock private AccountRepository accountRepository;
    @Mock private AssetRepository assetRepository;
    @Mock private LiabilityRepository liabilityRepository;
    @Mock private TransactionRepository transactionRepository;
    @Mock private BudgetRepository budgetRepository;
    @Mock private PayeeRepository payeeRepository;
    @Mock private InstitutionRepository institutionRepository;
    @Mock private RecurringTransactionRepository recurringTransactionRepository;
    @Mock private RealEstateRepository realEstateRepository;
    @Mock private PasswordService passwordService;
    @Mock private KeyManagementService keyManagementService;
    @Mock private CategorySeeder categorySeeder;
    @Mock private InstitutionSeeder institutionSeeder;
    @Mock private EncryptionKeyCache encryptionKeyCache;
    @Mock private SearchTokenService searchTokenService;
    @Mock private DefaultCurrencyProvider defaultCurrencyProvider;
    @Mock private JdbcTemplate jdbcTemplate;
    @Spy private EncryptionProperties encryptionProperties = new EncryptionProperties();
    @InjectMocks private LiveDemoData liveDemo;
    @InjectMocks private TestDataSeeder testData;
    private final List<Account> accounts = new ArrayList<>();
    private final List<Asset> assets = new ArrayList<>();
    private final List<Liability> liabilities = new ArrayList<>();
    private final List<Transaction> transactions = new ArrayList<>();
    private final List<RecurringTransaction> recurring = new ArrayList<>();
    private final List<RealEstateProperty> properties = new ArrayList<>();
    private final List<Budget> budgets = new ArrayList<>();
    private final List<Payee> payees = new ArrayList<>();
    private final List<SecretKey> persistenceKeys = new ArrayList<>();

    @AfterEach
    void clearContext() {
        EncryptionContext.clear();
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void liveDemoCreatesLinkedFinancialHistoryInEitherEncryptionMode(boolean encrypted)
            throws Exception {
        prepareSeed();
        encryptionProperties.setEnabled(encrypted);
        liveDemo.run();

        assertFinancialData();
        assertThat(accounts).extracting(Account::getCurrency).contains("EUR", "USD");
        assertThat(assets)
                .anySatisfy(
                        asset -> {
                            assertThat(asset.getQuantity())
                                    .isBetween(BigDecimal.ZERO, BigDecimal.ONE);
                            assertThat(asset.getTotalValue()).isPositive();
                        });
        assertThat(transactions)
                .extracting(Transaction::getDate)
                .anyMatch(date -> date.isBefore(LocalDate.now().minusYears(1)));
        assertThat(persistenceKeys)
                .isNotEmpty()
                .allMatch(key -> encrypted ? KEY.equals(key) : key == null);
        assertThat(EncryptionContext.getKey()).isNull();
        if (encrypted) {
            verify(encryptionKeyCache).cacheKey(USER_ID, KEY);
            for (String type :
                    List.of("ACCOUNT", "TRANSACTION", "ASSET", "LIABILITY", "REAL_ESTATE")) {
                verify(searchTokenService, org.mockito.Mockito.atLeastOnce())
                        .indexEntity(eq(USER_ID), eq(type), any(Long.class), anyList(), eq(KEY));
            }
        } else {
            verify(keyManagementService, never()).deriveKey(any(), any());
            verifyNoInteractions(searchTokenService, encryptionKeyCache);
        }
    }

    @Test
    void liveDemoDoesNotOverwriteAnExistingUser() throws Exception {
        when(userRepository.findByUsername("demo"))
                .thenReturn(Optional.of(User.builder().id(USER_ID).build()));
        liveDemo.run();
        verify(userRepository, never()).save(any());
        verifyNoInteractions(accountRepository, categorySeeder, keyManagementService);
    }

    @Test
    void liveDemoClearsTheKeyWhenPersistenceFails() {
        prepareUser();
        doThrow(new IllegalStateException("database unavailable"))
                .when(institutionSeeder)
                .seedDefaultInstitutions();
        assertThatThrownBy(liveDemo::seedDemoData).hasMessage("database unavailable");
        assertThat(EncryptionContext.getKey()).isNull();
        verifyNoInteractions(accountRepository);
    }

    @Test
    void searchIndexFailureDoesNotDiscardTheDemoOrLeakItsKey() {
        prepareSeed();
        when(searchTokenService.deriveSearchKey(KEY))
                .thenThrow(new IllegalStateException("index unavailable"));
        liveDemo.seedDemoData();
        assertFinancialData();
        assertThat(EncryptionContext.getKey()).isNull();
    }

    @Test
    void testSeederIsOptInAndPreservesAnExistingDemo() throws Exception {
        testData.run();
        verifyNoInteractions(userRepository);
        ReflectionTestUtils.setField(testData, "seedEnabled", true);
        when(userRepository.findByUsername("demo"))
                .thenReturn(Optional.of(User.builder().id(USER_ID).build()));
        testData.run();
        verify(userRepository, never()).save(any());
        verifyNoInteractions(keyManagementService, accountRepository);
    }

    @Test
    void generatedSampleDataHasConsistentReferencesAndAffordableLoanPayments() throws Exception {
        prepareSeed();
        configureCounts(20);
        testData.run();
        assertFinancialData();
        assertThat(accounts).hasSize(20);
        assertThat(assets).hasSize(20);
        assertThat(properties).hasSize(20);
        assertThat(liabilities)
                .hasSize(20)
                .allSatisfy(
                        loan -> {
                            assertThat(new BigDecimal(loan.getCurrentBalance()))
                                    .isPositive()
                                    .isLessThan(new BigDecimal(loan.getPrincipal()));
                            assertThat(new BigDecimal(loan.getMinimumPayment())).isPositive();
                            assertThat(loan.getEndDate()).isAfter(loan.getStartDate());
                        });
        assertThat(recurring)
                .hasSize(20)
                .allSatisfy(
                        item -> {
                            Account account =
                                    accounts.stream()
                                            .filter(a -> a.getId().equals(item.getAccountId()))
                                            .findFirst()
                                            .orElseThrow();
                            assertThat(item.getCurrency()).isEqualTo(account.getCurrency());
                            assertThat(item.getNextOccurrence()).isAfterOrEqualTo(LocalDate.now());
                        });
    }

    @Test
    void samplePayeesArePrivateToTheDemoUser() throws Exception {
        prepareSeed();
        configureCounts(6);
        testData.run();
        assertThat(payees)
                .hasSize(6)
                .allSatisfy(
                        payee -> {
                            assertThat(payee.getIsSystem()).isFalse();
                            assertThat(payee.getUserId()).isEqualTo(USER_ID);
                            assertThat(payee.getDefaultCategory().getUserId()).isEqualTo(USER_ID);
                        });
    }

    @Test
    void sampleDataIsWrittenWithItsEncryptionKeyAndClearsItAfterwards() throws Exception {
        prepareSeed();
        configureCounts(6);
        testData.run();
        assertThat(persistenceKeys).isNotEmpty().containsOnly(KEY);
        assertThat(EncryptionContext.getKey()).isNull();
    }

    @Test
    void sampleSeederSupportsDisabledEncryption() throws Exception {
        prepareSeed();
        configureCounts(6);
        encryptionProperties.setEnabled(false);
        testData.run();
        assertThat(persistenceKeys).isNotEmpty().allMatch(key -> key == null);
        verify(keyManagementService, never()).deriveKey(any(), any());
        verifyNoInteractions(encryptionKeyCache);
    }

    @Test
    void sampleSeederClearsTheKeyWhenPersistenceFails() {
        prepareUser();
        doThrow(new IllegalStateException("write failed"))
                .when(institutionSeeder)
                .seedDefaultInstitutions();
        assertThatThrownBy(testData::seedTestData).hasMessage("write failed");
        assertThat(EncryptionContext.getKey()).isNull();
    }

    private void configureCounts(int count) {
        ReflectionTestUtils.setField(testData, "seedEnabled", true);
        for (String field :
                List.of(
                        "seedAccountsCount",
                        "seedAssetsCount",
                        "seedLiabilitiesCount",
                        "seedRealEstateCount",
                        "seedTransactionsCount",
                        "seedRecurringCount",
                        "seedBudgetsCount",
                        "seedPayeesCount")) {
            ReflectionTestUtils.setField(testData, field, count);
        }
    }

    private void prepareUser() {
        when(keyManagementService.generateSalt()).thenReturn(new byte[16]);
        when(passwordService.hashPassword("demo123")).thenReturn("hashed-password");
        when(defaultCurrencyProvider.getDefaultCurrency()).thenReturn("EUR");
        when(userRepository.save(any(User.class)))
                .thenAnswer(
                        invocation -> {
                            User user = invocation.getArgument(0);
                            assertThat(user.getPasswordHash()).isEqualTo("hashed-password");
                            assertThat(user.getMasterPasswordSalt())
                                    .isEqualTo(Base64.getEncoder().encodeToString(new byte[16]));
                            user.setId(USER_ID);
                            return user;
                        });
        lenient()
                .when(keyManagementService.deriveKey(any(char[].class), any(byte[].class)))
                .thenReturn(KEY);
    }

    private void prepareSeed() {
        prepareUser();
        when(categoryRepository.findByUserId(USER_ID))
                .thenReturn(
                        List.of(
                                Category.builder()
                                        .id(81L)
                                        .userId(USER_ID)
                                        .name("Groceries")
                                        .build()));
        lenient()
                .when(institutionRepository.findAll())
                .thenReturn(List.of(Institution.builder().id(91L).name("BNP Paribas").build()));
        captureBatch(accountRepository, accounts, Account::setId);
        captureBatch(assetRepository, assets, Asset::setId);
        captureBatch(liabilityRepository, liabilities, Liability::setId);
        captureBatch(transactionRepository, transactions, Transaction::setId);
        captureBatch(recurringTransactionRepository, recurring, RecurringTransaction::setId);
        captureBatch(realEstateRepository, properties, RealEstateProperty::setId);
        captureBatch(budgetRepository, budgets, Budget::setId);
        captureBatch(payeeRepository, payees, Payee::setId);
        lenient().when(accountRepository.findByUserId(USER_ID)).thenReturn(accounts);
        lenient().when(assetRepository.findByUserId(USER_ID)).thenReturn(assets);
        lenient().when(transactionRepository.findByUserId(USER_ID)).thenReturn(transactions);
        lenient()
                .when(liabilityRepository.findByUserIdOrderByCreatedAtDesc(USER_ID))
                .thenReturn(liabilities);
        lenient().when(realEstateRepository.findByUserId(USER_ID)).thenReturn(properties);
        lenient().when(searchTokenService.deriveSearchKey(KEY)).thenReturn(KEY);
    }

    private <T> void captureBatch(
            JpaRepository<T, Long> repository, List<T> saved, BiConsumer<T, Long> assignId) {
        lenient()
                .when(repository.saveAll(any()))
                .thenAnswer(
                        invocation -> {
                            Iterable<T> batch = invocation.getArgument(0);
                            persistenceKeys.add(EncryptionContext.getKey());
                            for (T entity : batch) {
                                assignId.accept(entity, 1000L + saved.size());
                                saved.add(entity);
                            }
                            return saved;
                        });
    }

    private void assertFinancialData() {
        assertThat(accounts)
                .isNotEmpty()
                .allSatisfy(account -> assertThat(account.getUserId()).isEqualTo(USER_ID));
        List<Long> accountIds = accounts.stream().map(Account::getId).toList();
        assertThat(transactions)
                .isNotEmpty()
                .allSatisfy(
                        transaction -> {
                            assertThat(transaction.getUserId()).isEqualTo(USER_ID);
                            assertThat(transaction.getAccountId()).isIn(accountIds);
                            assertThat(transaction.getAmount()).isPositive();
                            assertThat(transaction.getDate()).isNotNull();
                        });
        assertThat(transactions)
                .extracting(Transaction::getType)
                .contains(TransactionType.INCOME, TransactionType.EXPENSE);
        assertThat(assets)
                .isNotEmpty()
                .allSatisfy(asset -> assertThat(asset.getUserId()).isEqualTo(USER_ID));
        assertThat(properties)
                .isNotEmpty()
                .allSatisfy(property -> assertThat(property.getUserId()).isEqualTo(USER_ID));
        assertThat(budgets)
                .isNotEmpty()
                .allSatisfy(
                        budget -> {
                            assertThat(budget.getUserId()).isEqualTo(USER_ID);
                            assertThat(budget.getCategoryId()).isEqualTo(81L);
                            assertThat(new BigDecimal(budget.getAmount())).isPositive();
                            assertThat(budget.getEndDate()).isAfter(budget.getStartDate());
                        });
    }
}
