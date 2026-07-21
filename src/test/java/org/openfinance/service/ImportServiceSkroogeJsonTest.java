package org.openfinance.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.openfinance.dto.AccountResponse;
import org.openfinance.dto.ImportedTransaction;
import org.openfinance.dto.SkroogeImportMetadata;
import org.openfinance.dto.SkroogeImportParseResult;
import org.openfinance.dto.TransactionRequest;
import org.openfinance.entity.Account;
import org.openfinance.entity.AccountType;
import org.openfinance.entity.Category;
import org.openfinance.entity.CategoryType;
import org.openfinance.entity.ImportSession;
import org.openfinance.entity.ImportSession.ImportStatus;
import org.openfinance.entity.Institution;
import org.openfinance.entity.Payee;
import org.openfinance.entity.Transaction;
import org.openfinance.entity.TransactionType;
import org.openfinance.repository.AccountRepository;
import org.openfinance.repository.CategoryRepository;
import org.openfinance.repository.CurrencyRepository;
import org.openfinance.repository.ImportSessionRepository;
import org.openfinance.repository.InstitutionRepository;
import org.openfinance.repository.NetWorthRepository;
import org.openfinance.repository.PayeeRepository;
import org.openfinance.repository.TransactionRepository;
import org.openfinance.repository.UserRepository;
import org.openfinance.repository.UserSettingsRepository;
import org.openfinance.service.parser.CsvParser;
import org.openfinance.service.parser.OfxParser;
import org.openfinance.service.parser.QifParser;
import org.openfinance.service.parser.SkroogeJsonParser;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.MessageSource;

@ExtendWith(MockitoExtension.class)
@DisplayName("ImportService Skrooge JSON Tests")
class ImportServiceSkroogeJsonTest {

    private static final Long USER_ID = 123L;
    private static final String UPLOAD_ID = "skrooge-upload";

    @Mock private ImportSessionRepository importSessionRepository;
    @Mock private TransactionRepository transactionRepository;
    @Mock private AccountRepository accountRepository;
    @Mock private CategoryRepository categoryRepository;
    @Mock private UserRepository userRepository;
    @Mock private FileStorageService fileStorageService;
    @Mock private QifParser qifParser;
    @Mock private OfxParser ofxParser;
    @Mock private CsvParser csvParser;
    @Mock private SkroogeJsonParser skroogeJsonParser;
    @Mock private AutoCategorizationService autoCategorizationService;
    @Mock private AccountService accountService;
    @Mock private TransactionRuleService transactionRuleService;
    @Mock private TransactionService transactionService;
    @Mock private ExchangeRateService exchangeRateService;
    @Mock private TransactionSplitService transactionSplitService;
    @Mock private NetWorthRepository netWorthRepository;
    @Mock private AICategorizationService aiCategorizationService;
    @Mock private MessageSource messageSource;
    @Mock private InstitutionRepository institutionRepository;
    @Mock private PayeeRepository payeeRepository;
    @Mock private CurrencyRepository currencyRepository;

    @Mock private UserSettingsRepository userSettingsRepository;
    @Mock private ObjectProvider<ImportConfirmationExecutor> importConfirmationExecutor;

    @Mock private DefaultCurrencyProvider defaultCurrencyProvider;

    private ImportService importService;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());

        importService =
                new ImportService(
                        importSessionRepository,
                        transactionRepository,
                        accountRepository,
                        categoryRepository,
                        userRepository,
                        fileStorageService,
                        qifParser,
                        ofxParser,
                        csvParser,
                        skroogeJsonParser,
                        objectMapper,
                        autoCategorizationService,
                        accountService,
                        transactionRuleService,
                        transactionService,
                        exchangeRateService,
                        transactionSplitService,
                        netWorthRepository,
                        aiCategorizationService,
                        messageSource,
                        institutionRepository,
                        currencyRepository,
                        payeeRepository,
                        defaultCurrencyProvider,
                        importConfirmationExecutor,
                        userSettingsRepository);

        // Lenient stubs for payee/currency resolution (used by convertToTransaction)
        lenient()
                .when(payeeRepository.save(any(Payee.class)))
                .thenAnswer(
                        invocation -> {
                            Payee p = invocation.getArgument(0);
                            p.setId(999L);
                            return p;
                        });

        org.openfinance.testutil.DefaultCurrencyProviderMocks.stub(
                defaultCurrencyProvider, userRepository);
    }

    @Test
    @DisplayName("Should parse JSON imports and preserve Skrooge metadata in session metadata")
    void shouldParseJsonImportsAndPreserveMetadata() throws Exception {
        ImportSession session =
                ImportSession.builder()
                        .id(1L)
                        .uploadId(UPLOAD_ID)
                        .userId(USER_ID)
                        .fileName("my_export.json")
                        .fileFormat("JSON")
                        .status(ImportStatus.PENDING)
                        .build();

        List<ImportedTransaction> transactions =
                List.of(
                        ImportedTransaction.builder()
                                .transactionDate(LocalDate.of(2024, 1, 10))
                                .payee("Local Market")
                                .amount(new BigDecimal("-45.00"))
                                .validationErrors(new ArrayList<>())
                                .build());

        SkroogeImportMetadata metadata = buildMetadata();
        SkroogeImportParseResult parseResult =
                SkroogeImportParseResult.builder()
                        .transactions(transactions)
                        .skroogeMetadata(metadata)
                        .currency("EUR")
                        .build();

        when(importSessionRepository.findById(1L)).thenReturn(Optional.of(session));
        when(fileStorageService.getFileContent(UPLOAD_ID))
                .thenReturn(new ByteArrayInputStream("{}".getBytes()));
        when(skroogeJsonParser.parseFile(any(InputStream.class), eq("my_export.json")))
                .thenReturn(parseResult);
        when(importSessionRepository.save(any(ImportSession.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        importService.parseFileAsync(1L);

        assertThat(session.getStatus()).isEqualTo(ImportStatus.PARSED);
        assertThat(session.getMetadata()).contains("skroogeMetadata");
        assertThat(session.getMetadata()).contains("EUR");
        verify(skroogeJsonParser).parseFile(any(InputStream.class), eq("my_export.json"));
    }

    @Test
    @DisplayName("Should confirm Skrooge JSON imports with account creation, transfers, and splits")
    void shouldConfirmSkroogeJsonImportsWithTransfersAndSplits() throws Exception {
        SkroogeImportMetadata metadata = buildMetadata();
        List<ImportedTransaction> transactions = buildImportedTransactions();
        Map<String, Object> sessionMetadata = new HashMap<>();
        sessionMetadata.put("transactions", transactions);
        sessionMetadata.put("count", transactions.size());
        sessionMetadata.put("ledgerBalance", BigDecimal.ZERO);
        sessionMetadata.put("fileCurrency", "EUR");
        sessionMetadata.put("skroogeMetadata", metadata);

        ImportSession session =
                ImportSession.builder()
                        .id(1L)
                        .uploadId(UPLOAD_ID)
                        .userId(USER_ID)
                        .fileName("my_export.json")
                        .fileFormat("JSON")
                        .status(ImportStatus.PARSED)
                        .metadata(objectMapper.writeValueAsString(sessionMetadata))
                        .build();

        Institution institution =
                Institution.builder().id(301L).name("Demo Bank").isSystem(false).build();
        Account checkingAccount =
                Account.builder()
                        .id(101L)
                        .userId(USER_ID)
                        .name("encrypted-checking")
                        .currency("EUR")
                        .type(AccountType.CHECKING)
                        .isActive(true)
                        .build();
        Account savingsAccount =
                Account.builder()
                        .id(102L)
                        .userId(USER_ID)
                        .name("encrypted-savings")
                        .currency("EUR")
                        .type(AccountType.SAVINGS)
                        .isActive(true)
                        .build();

        AtomicLong categoryIdSequence = new AtomicLong(400L);
        List<Category> storedCategories = new ArrayList<>();
        AtomicLong transactionIdSequence = new AtomicLong(500L);

        when(importSessionRepository.findById(1L)).thenReturn(Optional.of(session));
        when(importSessionRepository.save(any(ImportSession.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(institutionRepository.findAll()).thenReturn(List.of());
        when(institutionRepository.save(any(Institution.class))).thenReturn(institution);
        when(accountRepository.findByUserId(USER_ID)).thenReturn(new ArrayList<>());
        when(accountService.createAccount(eq(USER_ID), any()))
                .thenReturn(
                        AccountResponse.builder().id(101L).build(),
                        AccountResponse.builder().id(102L).build());
        when(accountRepository.findById(101L)).thenReturn(Optional.of(checkingAccount));
        when(accountRepository.findById(102L)).thenReturn(Optional.of(savingsAccount));
        when(categoryRepository.findByUserId(USER_ID))
                .thenAnswer(invocation -> new ArrayList<>(storedCategories));
        when(categoryRepository.save(any(Category.class)))
                .thenAnswer(
                        invocation -> {
                            Category category = invocation.getArgument(0);
                            category.setId(categoryIdSequence.incrementAndGet());
                            storedCategories.add(category);
                            return category;
                        });
        when(transactionRepository.save(any(Transaction.class)))
                .thenAnswer(
                        invocation -> {
                            Transaction transaction = invocation.getArgument(0);
                            transaction.setId(transactionIdSequence.incrementAndGet());
                            return transaction;
                        });

        ImportSession result = importService.confirmImport(1L, USER_ID, null, Map.of(), true);

        assertThat(result.getStatus()).isEqualTo(ImportStatus.COMPLETED);
        assertThat(result.getImportedCount()).isEqualTo(2);
        assertThat(result.getSkippedCount()).isZero();
        verify(institutionRepository, times(2)).save(any(Institution.class));
        verify(accountService, times(2)).createAccount(eq(USER_ID), any());
        verify(categoryRepository, times(2)).save(any(Category.class));
        ArgumentCaptor<Transaction> transactionCaptor = ArgumentCaptor.forClass(Transaction.class);
        verify(transactionRepository, times(3)).save(transactionCaptor.capture());
        List<Transaction> transferTransactions =
                transactionCaptor.getAllValues().stream()
                        .filter(transaction -> transaction.getTransferId() != null)
                        .toList();
        assertThat(transferTransactions).hasSize(2);
        assertThat(transferTransactions)
                .extracting(Transaction::getAmount)
                .containsExactly(new BigDecimal("200.0000"), new BigDecimal("200.0000"));
        assertThat(transferTransactions)
                .extracting(Transaction::getCurrency)
                .containsExactly("EUR", "EUR");
        assertThat(transferTransactions)
                .extracting(Transaction::getAccountId)
                .containsExactly(101L, 102L);
        assertThat(transferTransactions)
                .extracting(Transaction::getToAccountId)
                .containsExactly(102L, 101L);
        assertThat(transferTransactions)
                .extracting(Transaction::getTransferId)
                .containsOnly(transferTransactions.get(0).getTransferId());
        verify(transactionSplitService, times(1)).saveSplits(anyLong(), any());
        verify(transactionService, times(0))
                .createTransfer(eq(USER_ID), any(TransactionRequest.class));
        verify(transactionService, times(1))
                .syncTransactionFts(
                        any(Transaction.class), eq("Local Market"), eq("Weekly groceries"));
        verify(accountService, times(2)).recalculateBalance(anyLong(), eq(USER_ID));
        verify(netWorthRepository, times(1))
                .deleteByUserIdAndSnapshotDateBefore(eq(USER_ID), eq(LocalDate.of(2024, 1, 12)));
    }

    @Test
    @DisplayName(
            "Should create institution with fallback name when Skrooge institution name is blank")
    void shouldCreateInstitutionWithFallbackNameWhenNameIsBlank() throws Exception {
        SkroogeImportMetadata metadata =
                SkroogeImportMetadata.builder()
                        .institutions(
                                List.of(
                                        SkroogeImportMetadata.SkroogeInstitution.builder()
                                                .sourceId(99L)
                                                .name("   ")
                                                .build()))
                        .accounts(
                                List.of(
                                        SkroogeImportMetadata.SkroogeAccount.builder()
                                                .sourceId(30L)
                                                .sourceInstitutionId(99L)
                                                .name("Wallet")
                                                .currency("EUR")
                                                .accountType(AccountType.CASH)
                                                .openingBalance(BigDecimal.ZERO)
                                                .openingDate(LocalDate.of(2024, 1, 1))
                                                .active(true)
                                                .build()))
                        .categories(List.of())
                        .build();

        ImportedTransaction transaction =
                ImportedTransaction.builder()
                        .transactionDate(LocalDate.of(2024, 1, 10))
                        .payee("Cash Deposit")
                        .amount(new BigDecimal("25.00"))
                        .memo("Pocket cash")
                        .sourceAccountId(30L)
                        .accountName("Wallet")
                        .currency("EUR")
                        .referenceNumber("skrooge:operation:blank-institution")
                        .validationErrors(new ArrayList<>())
                        .build();

        ImportSession session = buildSession(metadata, List.of(transaction));
        Account walletAccount =
                Account.builder()
                        .id(103L)
                        .userId(USER_ID)
                        .name("encrypted-wallet")
                        .currency("EUR")
                        .type(AccountType.CASH)
                        .isActive(true)
                        .build();

        when(importSessionRepository.findById(1L)).thenReturn(Optional.of(session));
        when(importSessionRepository.save(any(ImportSession.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        Institution fallbackInstitution =
                Institution.builder().id(401L).name("Institution 99").isSystem(false).build();
        when(institutionRepository.findAll()).thenReturn(List.of());
        when(institutionRepository.save(any(Institution.class))).thenReturn(fallbackInstitution);
        when(accountRepository.findByUserId(USER_ID)).thenReturn(new ArrayList<>());
        when(accountService.createAccount(eq(USER_ID), any()))
                .thenReturn(AccountResponse.builder().id(103L).build());
        when(accountRepository.findById(103L)).thenReturn(Optional.of(walletAccount));
        when(categoryRepository.findByUserId(USER_ID)).thenReturn(new ArrayList<>());
        when(transactionRepository.save(any(Transaction.class)))
                .thenAnswer(
                        invocation -> {
                            Transaction saved = invocation.getArgument(0);
                            saved.setId(701L);
                            return saved;
                        });

        ImportSession result = importService.confirmImport(1L, USER_ID, null, Map.of(), true);

        ArgumentCaptor<Institution> institutionCaptor = ArgumentCaptor.forClass(Institution.class);
        verify(institutionRepository).save(institutionCaptor.capture());
        assertThat(institutionCaptor.getValue().getName()).isEqualTo("Institution 99");
        assertThat(result.getStatus()).isEqualTo(ImportStatus.COMPLETED);
        assertThat(result.getImportedCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("Should preserve Skrooge closed account status when creating accounts")
    void shouldPreserveSkroogeClosedAccountStatusWhenCreatingAccounts() throws Exception {
        SkroogeImportMetadata metadata =
                SkroogeImportMetadata.builder()
                        .institutions(
                                List.of(
                                        SkroogeImportMetadata.SkroogeInstitution.builder()
                                                .sourceId(1L)
                                                .name("Demo Bank")
                                                .build()))
                        .accounts(
                                List.of(
                                        SkroogeImportMetadata.SkroogeAccount.builder()
                                                .sourceId(30L)
                                                .sourceInstitutionId(1L)
                                                .name("Closed Wallet")
                                                .currency("EUR")
                                                .accountType(AccountType.CASH)
                                                .openingBalance(BigDecimal.ZERO)
                                                .openingDate(LocalDate.of(2024, 1, 1))
                                                .active(false)
                                                .build()))
                        .categories(List.of())
                        .build();

        ImportedTransaction transaction =
                ImportedTransaction.builder()
                        .transactionDate(LocalDate.of(2024, 1, 10))
                        .payee("Adjustment")
                        .amount(new BigDecimal("25.00"))
                        .sourceAccountId(30L)
                        .accountName("Closed Wallet")
                        .currency("EUR")
                        .referenceNumber("skrooge:operation:closed-wallet")
                        .validationErrors(new ArrayList<>())
                        .build();

        ImportSession session = buildSession(metadata, List.of(transaction));
        Institution institution =
                Institution.builder().id(301L).name("Demo Bank").isSystem(false).build();
        Account closedWallet =
                Account.builder()
                        .id(103L)
                        .userId(USER_ID)
                        .name("encrypted-closed-wallet")
                        .currency("EUR")
                        .type(AccountType.CASH)
                        .isActive(true)
                        .build();

        when(importSessionRepository.findById(1L)).thenReturn(Optional.of(session));
        when(importSessionRepository.save(any(ImportSession.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(institutionRepository.findAll()).thenReturn(List.of());
        when(institutionRepository.save(any(Institution.class))).thenReturn(institution);
        when(accountRepository.findByUserId(USER_ID)).thenReturn(new ArrayList<>());
        when(accountService.createAccount(eq(USER_ID), any()))
                .thenReturn(AccountResponse.builder().id(103L).build());
        when(accountRepository.findById(103L)).thenReturn(Optional.of(closedWallet));
        when(accountRepository.save(any(Account.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(categoryRepository.findByUserId(USER_ID)).thenReturn(new ArrayList<>());
        when(transactionRepository.save(any(Transaction.class)))
                .thenAnswer(
                        invocation -> {
                            Transaction saved = invocation.getArgument(0);
                            saved.setId(901L);
                            return saved;
                        });

        ImportSession result = importService.confirmImport(1L, USER_ID, null, Map.of(), true);

        assertThat(result.getStatus()).isEqualTo(ImportStatus.COMPLETED);
        verify(accountRepository)
                .save(
                        argThat(
                                account ->
                                        account.getId().equals(103L)
                                                && Boolean.FALSE.equals(account.getIsActive())));
    }

    @Test
    @DisplayName("Should convert Skrooge transaction currency to account currency")
    void shouldConvertSkroogeTransactionCurrencyToAccountCurrency() throws Exception {
        SkroogeImportMetadata metadata =
                SkroogeImportMetadata.builder()
                        .institutions(
                                List.of(
                                        SkroogeImportMetadata.SkroogeInstitution.builder()
                                                .sourceId(1L)
                                                .name("Demo Bank")
                                                .build()))
                        .accounts(
                                List.of(
                                        SkroogeImportMetadata.SkroogeAccount.builder()
                                                .sourceId(30L)
                                                .sourceInstitutionId(1L)
                                                .name("XOF Wallet")
                                                .currency("XOF")
                                                .accountType(AccountType.CASH)
                                                .openingBalance(BigDecimal.ZERO)
                                                .openingDate(LocalDate.of(2024, 1, 1))
                                                .active(true)
                                                .build()))
                        .categories(List.of())
                        .build();

        ImportedTransaction transaction =
                ImportedTransaction.builder()
                        .transactionDate(LocalDate.of(2025, 8, 19))
                        .payee("Transfer fee")
                        .amount(new BigDecimal("-2.99"))
                        .sourceAccountId(30L)
                        .accountName("XOF Wallet")
                        .currency("EUR")
                        .referenceNumber("skrooge:operation:xof-fee")
                        .validationErrors(new ArrayList<>())
                        .build();

        ImportSession session = buildSession(metadata, List.of(transaction));
        Institution institution =
                Institution.builder().id(301L).name("Demo Bank").isSystem(false).build();
        Account xofWallet =
                Account.builder()
                        .id(103L)
                        .userId(USER_ID)
                        .name("encrypted-xof-wallet")
                        .currency("XOF")
                        .type(AccountType.CASH)
                        .isActive(true)
                        .build();

        when(importSessionRepository.findById(1L)).thenReturn(Optional.of(session));
        when(importSessionRepository.save(any(ImportSession.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(institutionRepository.findAll()).thenReturn(List.of());
        when(institutionRepository.save(any(Institution.class))).thenReturn(institution);
        when(accountRepository.findByUserId(USER_ID)).thenReturn(new ArrayList<>());
        when(accountService.createAccount(eq(USER_ID), any()))
                .thenReturn(AccountResponse.builder().id(103L).build());
        when(accountRepository.findById(103L)).thenReturn(Optional.of(xofWallet));
        when(categoryRepository.findByUserId(USER_ID)).thenReturn(new ArrayList<>());
        when(exchangeRateService.convert(
                        eq(new BigDecimal("2.99")),
                        eq("EUR"),
                        eq("XOF"),
                        eq(LocalDate.of(2025, 8, 19))))
                .thenReturn(new BigDecimal("1960.12345678"));
        when(transactionRepository.save(any(Transaction.class)))
                .thenAnswer(
                        invocation -> {
                            Transaction saved = invocation.getArgument(0);
                            saved.setId(902L);
                            return saved;
                        });

        importService.confirmImport(1L, USER_ID, null, Map.of(), true);

        ArgumentCaptor<Transaction> transactionCaptor = ArgumentCaptor.forClass(Transaction.class);
        verify(transactionRepository).save(transactionCaptor.capture());
        Transaction savedTransaction = transactionCaptor.getValue();
        assertThat(savedTransaction.getCurrency()).isEqualTo("XOF");
        assertThat(savedTransaction.getAmount()).isEqualByComparingTo(new BigDecimal("1960.1235"));
        assertThat(savedTransaction.getType()).isEqualTo(TransactionType.EXPENSE);
    }

    @Test
    @DisplayName("Should truncate long Skrooge payees and memos to transaction limits")
    void shouldTruncateLongSkroogePayeesAndMemosToTransactionLimits() throws Exception {
        SkroogeImportMetadata metadata = buildMetadataWithoutCategories();
        String longPayee = "P".repeat(320);
        String longMemo = "M".repeat(1205);
        ImportedTransaction longTextTransaction =
                ImportedTransaction.builder()
                        .transactionDate(LocalDate.of(2024, 1, 15))
                        .payee(longPayee)
                        .amount(new BigDecimal("-19.99"))
                        .memo(longMemo)
                        .sourceAccountId(10L)
                        .accountName("Checking")
                        .currency("EUR")
                        .referenceNumber("skrooge:operation:long-text")
                        .validationErrors(new ArrayList<>())
                        .build();

        ImportSession session = buildSession(metadata, List.of(longTextTransaction));
        Institution institution =
                Institution.builder().id(301L).name("Demo Bank").isSystem(false).build();
        Account checkingAccount =
                Account.builder()
                        .id(101L)
                        .userId(USER_ID)
                        .name("encrypted-checking")
                        .currency("EUR")
                        .type(AccountType.CHECKING)
                        .isActive(true)
                        .build();

        when(importSessionRepository.findById(1L)).thenReturn(Optional.of(session));
        when(importSessionRepository.save(any(ImportSession.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(institutionRepository.findAll()).thenReturn(List.of());
        when(institutionRepository.save(any(Institution.class))).thenReturn(institution);
        when(accountRepository.findByUserId(USER_ID)).thenReturn(new ArrayList<>());
        when(accountService.createAccount(eq(USER_ID), any()))
                .thenReturn(
                        AccountResponse.builder().id(101L).build(),
                        AccountResponse.builder().id(102L).build());
        when(accountRepository.findById(101L)).thenReturn(Optional.of(checkingAccount));
        when(accountRepository.findById(102L))
                .thenReturn(
                        Optional.of(
                                Account.builder()
                                        .id(102L)
                                        .userId(USER_ID)
                                        .name("encrypted-savings")
                                        .currency("EUR")
                                        .type(AccountType.SAVINGS)
                                        .isActive(true)
                                        .build()));
        when(categoryRepository.findByUserId(USER_ID)).thenReturn(new ArrayList<>());
        when(transactionRepository.save(argThat(transaction -> transaction.getId() == null)))
                .thenAnswer(
                        invocation -> {
                            Transaction saved = invocation.getArgument(0);
                            saved.setId(801L);
                            return saved;
                        });

        ImportSession result = importService.confirmImport(1L, USER_ID, null, Map.of(), true);

        ArgumentCaptor<Transaction> transactionCaptor = ArgumentCaptor.forClass(Transaction.class);
        verify(transactionRepository).save(transactionCaptor.capture());
        Transaction savedTransaction = transactionCaptor.getValue();
        assertThat(savedTransaction.getPayee()).hasSize(100);
        assertThat(savedTransaction.getDescription()).hasSize(255);
        assertThat(savedTransaction.getNotes()).hasSize(1000);
        assertThat(result.getImportedCount()).isEqualTo(1);
        assertThat(result.getSkippedCount()).isZero();
    }

    @Test
    @DisplayName("Should skip tiny Skrooge amounts without failing the import session")
    void shouldSkipTinySkroogeAmountsWithoutFailingImportSession() throws Exception {
        SkroogeImportMetadata metadata = buildMetadataWithoutCategories();
        ImportedTransaction tinyAmountTransaction =
                ImportedTransaction.builder()
                        .transactionDate(LocalDate.of(2024, 1, 16))
                        .payee("Dust adjustment")
                        .amount(new BigDecimal("0.00001"))
                        .memo("Residual currency dust")
                        .sourceAccountId(10L)
                        .accountName("Checking")
                        .currency("EUR")
                        .referenceNumber("skrooge:operation:tiny-amount")
                        .validationErrors(new ArrayList<>())
                        .build();

        ImportSession session = buildSession(metadata, List.of(tinyAmountTransaction));
        Institution institution =
                Institution.builder().id(301L).name("Demo Bank").isSystem(false).build();
        Account checkingAccount =
                Account.builder()
                        .id(101L)
                        .userId(USER_ID)
                        .name("encrypted-checking")
                        .currency("EUR")
                        .type(AccountType.CHECKING)
                        .isActive(true)
                        .build();

        when(importSessionRepository.findById(1L)).thenReturn(Optional.of(session));
        when(importSessionRepository.save(any(ImportSession.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(institutionRepository.findAll()).thenReturn(List.of());
        when(institutionRepository.save(any(Institution.class))).thenReturn(institution);
        when(accountRepository.findByUserId(USER_ID)).thenReturn(new ArrayList<>());
        when(accountService.createAccount(eq(USER_ID), any()))
                .thenReturn(
                        AccountResponse.builder().id(101L).build(),
                        AccountResponse.builder().id(102L).build());
        when(accountRepository.findById(101L)).thenReturn(Optional.of(checkingAccount));
        when(accountRepository.findById(102L))
                .thenReturn(
                        Optional.of(
                                Account.builder()
                                        .id(102L)
                                        .userId(USER_ID)
                                        .name("encrypted-savings")
                                        .currency("EUR")
                                        .type(AccountType.SAVINGS)
                                        .isActive(true)
                                        .build()));
        when(categoryRepository.findByUserId(USER_ID)).thenReturn(new ArrayList<>());

        ImportSession result = importService.confirmImport(1L, USER_ID, null, Map.of(), true);

        verify(transactionRepository, times(0)).save(any(Transaction.class));
        assertThat(result.getStatus()).isEqualTo(ImportStatus.COMPLETED);
        assertThat(result.getImportedCount()).isZero();
        assertThat(result.getSkippedCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("Should import crypto-scale Skrooge amounts at the supported minimum boundary")
    void shouldImportCryptoScaleSkroogeAmountAtSupportedMinimumBoundary() throws Exception {
        SkroogeImportMetadata metadata = buildMetadataWithoutCategories();
        ImportedTransaction cryptoAmountTransaction =
                ImportedTransaction.builder()
                        .transactionDate(LocalDate.of(2024, 1, 16))
                        .payee("Crypto dust")
                        .amount(new BigDecimal("0.0001"))
                        .memo("Small crypto quantity")
                        .sourceAccountId(10L)
                        .accountName("Checking")
                        .currency("EUR")
                        .referenceNumber("skrooge:operation:crypto-boundary")
                        .validationErrors(new ArrayList<>())
                        .build();

        ImportSession session = buildSession(metadata, List.of(cryptoAmountTransaction));
        Institution institution =
                Institution.builder().id(301L).name("Demo Bank").isSystem(false).build();
        Account checkingAccount =
                Account.builder()
                        .id(101L)
                        .userId(USER_ID)
                        .name("encrypted-checking")
                        .currency("EUR")
                        .type(AccountType.CHECKING)
                        .isActive(true)
                        .build();

        when(importSessionRepository.findById(1L)).thenReturn(Optional.of(session));
        when(importSessionRepository.save(any(ImportSession.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(institutionRepository.findAll()).thenReturn(List.of());
        when(institutionRepository.save(any(Institution.class))).thenReturn(institution);
        when(accountRepository.findByUserId(USER_ID)).thenReturn(new ArrayList<>());
        when(accountService.createAccount(eq(USER_ID), any()))
                .thenReturn(
                        AccountResponse.builder().id(101L).build(),
                        AccountResponse.builder().id(102L).build());
        when(accountRepository.findById(101L)).thenReturn(Optional.of(checkingAccount));
        when(accountRepository.findById(102L))
                .thenReturn(
                        Optional.of(
                                Account.builder()
                                        .id(102L)
                                        .userId(USER_ID)
                                        .name("encrypted-savings")
                                        .currency("EUR")
                                        .type(AccountType.SAVINGS)
                                        .isActive(true)
                                        .build()));
        when(categoryRepository.findByUserId(USER_ID)).thenReturn(new ArrayList<>());
        when(transactionRepository.save(any(Transaction.class)))
                .thenAnswer(
                        invocation -> {
                            Transaction saved = invocation.getArgument(0);
                            saved.setId(903L);
                            return saved;
                        });

        ImportSession result = importService.confirmImport(1L, USER_ID, null, Map.of(), true);

        ArgumentCaptor<Transaction> transactionCaptor = ArgumentCaptor.forClass(Transaction.class);
        verify(transactionRepository).save(transactionCaptor.capture());
        assertThat(transactionCaptor.getValue().getAmount())
                .isEqualByComparingTo(new BigDecimal("0.0001"));
        assertThat(result.getStatus()).isEqualTo(ImportStatus.COMPLETED);
        assertThat(result.getImportedCount()).isEqualTo(1);
        assertThat(result.getSkippedCount()).isZero();
    }

    @Test
    @DisplayName("Should use Skrooge balance delta when historical exchange rate is unavailable")
    void shouldUseSkroogeBalanceDeltaWhenHistoricalExchangeRateIsUnavailable() throws Exception {
        SkroogeImportMetadata metadata = buildFcfaMetadataWithoutCategories();
        LocalDate transactionDate = LocalDate.of(2025, 8, 19);
        ImportedTransaction eurFeeTransaction =
                ImportedTransaction.builder()
                        .transactionDate(transactionDate)
                        .payee("Bank fee")
                        .amount(new BigDecimal("-2.99"))
                        .sourceAccountBalanceDelta(new BigDecimal("-1959.50"))
                        .memo("Card fee")
                        .sourceAccountId(10L)
                        .accountName("FCFA Savings")
                        .currency("EUR")
                        .referenceNumber("skrooge:operation:2249")
                        .validationErrors(new ArrayList<>())
                        .build();

        ImportSession session = buildSession(metadata, List.of(eurFeeTransaction));
        Institution institution =
                Institution.builder().id(301L).name("Demo Bank").isSystem(false).build();
        Account fcfaAccount =
                Account.builder()
                        .id(101L)
                        .userId(USER_ID)
                        .name("encrypted-fcfa-savings")
                        .currency("XOF")
                        .type(AccountType.SAVINGS)
                        .isActive(true)
                        .build();

        when(importSessionRepository.findById(1L)).thenReturn(Optional.of(session));
        when(importSessionRepository.save(any(ImportSession.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(institutionRepository.findAll()).thenReturn(List.of());
        when(institutionRepository.save(any(Institution.class))).thenReturn(institution);
        when(accountRepository.findByUserId(USER_ID)).thenReturn(new ArrayList<>());
        when(accountService.createAccount(eq(USER_ID), any()))
                .thenReturn(AccountResponse.builder().id(101L).build());
        when(accountRepository.findById(101L)).thenReturn(Optional.of(fcfaAccount));
        when(categoryRepository.findByUserId(USER_ID)).thenReturn(new ArrayList<>());
        when(exchangeRateService.convert(
                        eq(new BigDecimal("2.99")), eq("EUR"), eq("XOF"), eq(transactionDate)))
                .thenThrow(
                        new IllegalArgumentException(
                                "No exchange rate available for EUR → XOF on 2025-08-19"));
        when(transactionRepository.save(any(Transaction.class)))
                .thenAnswer(
                        invocation -> {
                            Transaction saved = invocation.getArgument(0);
                            saved.setId(904L);
                            return saved;
                        });

        ImportSession result = importService.confirmImport(1L, USER_ID, null, Map.of(), true);

        ArgumentCaptor<Transaction> transactionCaptor = ArgumentCaptor.forClass(Transaction.class);
        verify(transactionRepository).save(transactionCaptor.capture());
        Transaction savedTransaction = transactionCaptor.getValue();
        assertThat(savedTransaction.getCurrency()).isEqualTo("XOF");
        assertThat(savedTransaction.getAmount()).isEqualByComparingTo(new BigDecimal("1959.5000"));
        assertThat(savedTransaction.getType()).isEqualTo(TransactionType.EXPENSE);
        assertThat(result.getStatus()).isEqualTo(ImportStatus.COMPLETED);
        assertThat(result.getImportedCount()).isEqualTo(1);
        assertThat(result.getSkippedCount()).isZero();
    }

    @Test
    @DisplayName("Should not save a partial Skrooge transfer when one paired side is invalid")
    void shouldNotSavePartialSkroogeTransferWhenOnePairedSideIsInvalid() throws Exception {
        SkroogeImportMetadata metadata = buildMetadataWithoutCategories();
        ImportedTransaction transferOut =
                ImportedTransaction.builder()
                        .transactionDate(LocalDate.of(2024, 1, 12))
                        .payee("Transfer Desk")
                        .amount(new BigDecimal("-200.00"))
                        .memo("Move to savings")
                        .sourceAccountId(10L)
                        .toAccountSourceId(20L)
                        .accountName("Checking")
                        .toAccountName("Savings")
                        .currency("EUR")
                        .transfer(true)
                        .transferGroupKey("skrooge:transfer-group:invalid-pair")
                        .referenceNumber("skrooge:operation:transfer-out")
                        .validationErrors(new ArrayList<>())
                        .build();
        ImportedTransaction invalidTransferIn =
                ImportedTransaction.builder()
                        .transactionDate(LocalDate.of(2024, 1, 12))
                        .payee("Transfer Desk")
                        .amount(new BigDecimal("0.00001"))
                        .memo("Invalid dust side")
                        .sourceAccountId(20L)
                        .toAccountSourceId(10L)
                        .accountName("Savings")
                        .toAccountName("Checking")
                        .currency("EUR")
                        .transfer(true)
                        .transferGroupKey("skrooge:transfer-group:invalid-pair")
                        .referenceNumber("skrooge:operation:transfer-in")
                        .validationErrors(new ArrayList<>())
                        .build();

        ImportSession session = buildSession(metadata, List.of(transferOut, invalidTransferIn));
        Institution institution =
                Institution.builder().id(301L).name("Demo Bank").isSystem(false).build();
        Account checkingAccount =
                Account.builder()
                        .id(101L)
                        .userId(USER_ID)
                        .name("encrypted-checking")
                        .currency("EUR")
                        .type(AccountType.CHECKING)
                        .isActive(true)
                        .build();
        Account savingsAccount =
                Account.builder()
                        .id(102L)
                        .userId(USER_ID)
                        .name("encrypted-savings")
                        .currency("EUR")
                        .type(AccountType.SAVINGS)
                        .isActive(true)
                        .build();

        when(importSessionRepository.findById(1L)).thenReturn(Optional.of(session));
        when(importSessionRepository.save(any(ImportSession.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(institutionRepository.findAll()).thenReturn(List.of());
        when(institutionRepository.save(any(Institution.class))).thenReturn(institution);
        when(accountRepository.findByUserId(USER_ID)).thenReturn(new ArrayList<>());
        when(accountService.createAccount(eq(USER_ID), any()))
                .thenReturn(
                        AccountResponse.builder().id(101L).build(),
                        AccountResponse.builder().id(102L).build());
        when(accountRepository.findById(101L)).thenReturn(Optional.of(checkingAccount));
        when(accountRepository.findById(102L)).thenReturn(Optional.of(savingsAccount));
        when(categoryRepository.findByUserId(USER_ID)).thenReturn(new ArrayList<>());

        ImportSession result = importService.confirmImport(1L, USER_ID, null, Map.of(), true);

        verify(transactionRepository, times(0)).save(any(Transaction.class));
        verify(transactionService, times(0))
                .syncTransactionFts(any(Transaction.class), any(), any());
        assertThat(result.getStatus()).isEqualTo(ImportStatus.COMPLETED);
        assertThat(result.getImportedCount()).isZero();
        assertThat(result.getSkippedCount()).isEqualTo(1);
    }

    private ImportSession buildSession(
            SkroogeImportMetadata metadata, List<ImportedTransaction> transactions)
            throws Exception {
        Map<String, Object> sessionMetadata = new HashMap<>();
        sessionMetadata.put("transactions", transactions);
        sessionMetadata.put("count", transactions.size());
        sessionMetadata.put("ledgerBalance", BigDecimal.ZERO);
        sessionMetadata.put("fileCurrency", "EUR");
        sessionMetadata.put("skroogeMetadata", metadata);

        return ImportSession.builder()
                .id(1L)
                .uploadId(UPLOAD_ID)
                .userId(USER_ID)
                .fileName("my_export.json")
                .fileFormat("JSON")
                .status(ImportStatus.PARSED)
                .metadata(objectMapper.writeValueAsString(sessionMetadata))
                .build();
    }

    private SkroogeImportMetadata buildMetadata() {
        return SkroogeImportMetadata.builder()
                .institutions(
                        List.of(
                                SkroogeImportMetadata.SkroogeInstitution.builder()
                                        .sourceId(1L)
                                        .name("Demo Bank")
                                        .logo("bank-logo")
                                        .build(),
                                SkroogeImportMetadata.SkroogeInstitution.builder()
                                        .sourceId(99L)
                                        .name("   ")
                                        .build()))
                .accounts(
                        List.of(
                                SkroogeImportMetadata.SkroogeAccount.builder()
                                        .sourceId(10L)
                                        .sourceInstitutionId(1L)
                                        .name("Checking")
                                        .accountNumber("CHK-001")
                                        .currency("EUR")
                                        .accountType(AccountType.CHECKING)
                                        .openingBalance(new BigDecimal("1000.00"))
                                        .openingDate(LocalDate.of(2024, 1, 1))
                                        .active(true)
                                        .build(),
                                SkroogeImportMetadata.SkroogeAccount.builder()
                                        .sourceId(20L)
                                        .sourceInstitutionId(1L)
                                        .name("Savings")
                                        .accountNumber("SAV-001")
                                        .currency("EUR")
                                        .accountType(AccountType.SAVINGS)
                                        .openingBalance(BigDecimal.ZERO)
                                        .openingDate(LocalDate.of(2024, 1, 1))
                                        .active(true)
                                        .build()))
                .categories(
                        List.of(
                                SkroogeImportMetadata.SkroogeCategory.builder()
                                        .sourceId(100L)
                                        .name("Food")
                                        .fullName("Food")
                                        .type(CategoryType.EXPENSE)
                                        .build(),
                                SkroogeImportMetadata.SkroogeCategory.builder()
                                        .sourceId(101L)
                                        .parentSourceId(100L)
                                        .name("Groceries")
                                        .fullName("Food:Groceries")
                                        .type(CategoryType.EXPENSE)
                                        .build()))
                .build();
    }

    private SkroogeImportMetadata buildMetadataWithoutCategories() {
        return SkroogeImportMetadata.builder()
                .institutions(
                        List.of(
                                SkroogeImportMetadata.SkroogeInstitution.builder()
                                        .sourceId(1L)
                                        .name("Demo Bank")
                                        .logo("bank-logo")
                                        .build(),
                                SkroogeImportMetadata.SkroogeInstitution.builder()
                                        .sourceId(99L)
                                        .name("   ")
                                        .build()))
                .accounts(
                        List.of(
                                SkroogeImportMetadata.SkroogeAccount.builder()
                                        .sourceId(10L)
                                        .sourceInstitutionId(1L)
                                        .name("Checking")
                                        .accountNumber("CHK-001")
                                        .currency("EUR")
                                        .accountType(AccountType.CHECKING)
                                        .openingBalance(new BigDecimal("1000.00"))
                                        .openingDate(LocalDate.of(2024, 1, 1))
                                        .active(true)
                                        .build(),
                                SkroogeImportMetadata.SkroogeAccount.builder()
                                        .sourceId(20L)
                                        .sourceInstitutionId(1L)
                                        .name("Savings")
                                        .accountNumber("SAV-001")
                                        .currency("EUR")
                                        .accountType(AccountType.SAVINGS)
                                        .openingBalance(BigDecimal.ZERO)
                                        .openingDate(LocalDate.of(2024, 1, 1))
                                        .active(true)
                                        .build()))
                .categories(List.of())
                .build();
    }

    private SkroogeImportMetadata buildFcfaMetadataWithoutCategories() {
        return SkroogeImportMetadata.builder()
                .institutions(
                        List.of(
                                SkroogeImportMetadata.SkroogeInstitution.builder()
                                        .sourceId(1L)
                                        .name("Demo Bank")
                                        .logo("bank-logo")
                                        .build()))
                .accounts(
                        List.of(
                                SkroogeImportMetadata.SkroogeAccount.builder()
                                        .sourceId(10L)
                                        .sourceInstitutionId(1L)
                                        .name("FCFA Savings")
                                        .accountNumber("SAV-FCFA")
                                        .currency("XOF")
                                        .accountType(AccountType.SAVINGS)
                                        .openingBalance(new BigDecimal("4950.00"))
                                        .openingDate(LocalDate.of(2025, 8, 19))
                                        .active(true)
                                        .build()))
                .categories(List.of())
                .build();
    }

    private List<ImportedTransaction> buildImportedTransactions() {
        ImportedTransaction splitExpense =
                ImportedTransaction.builder()
                        .transactionDate(LocalDate.of(2024, 1, 10))
                        .payee("Local Market")
                        .amount(new BigDecimal("-45.00"))
                        .memo("Weekly groceries")
                        .sourceAccountId(10L)
                        .accountName("Checking")
                        .currency("EUR")
                        .referenceNumber("skrooge:operation:1")
                        .splits(
                                List.of(
                                        ImportedTransaction.SplitEntry.builder()
                                                .category("Food:Groceries")
                                                .sourceCategoryId(101L)
                                                .memo("Fruit")
                                                .amount(new BigDecimal("30.00"))
                                                .build(),
                                        ImportedTransaction.SplitEntry.builder()
                                                .category("Food:Groceries")
                                                .sourceCategoryId(101L)
                                                .memo("Bread")
                                                .amount(new BigDecimal("15.00"))
                                                .build()))
                        .validationErrors(new ArrayList<>())
                        .build();

        ImportedTransaction transferOut =
                ImportedTransaction.builder()
                        .transactionDate(LocalDate.of(2024, 1, 12))
                        .payee("Transfer Desk")
                        .amount(new BigDecimal("-200.00"))
                        .memo("Move to savings")
                        .sourceAccountId(10L)
                        .toAccountSourceId(20L)
                        .accountName("Checking")
                        .toAccountName("Savings")
                        .currency("EUR")
                        .transfer(true)
                        .transferGroupKey("skrooge:transfer-group:999")
                        .referenceNumber("skrooge:operation:2")
                        .validationErrors(new ArrayList<>())
                        .build();

        ImportedTransaction transferIn =
                ImportedTransaction.builder()
                        .transactionDate(LocalDate.of(2024, 1, 12))
                        .payee("Transfer Desk")
                        .amount(new BigDecimal("200.00"))
                        .memo("Move from checking")
                        .sourceAccountId(20L)
                        .toAccountSourceId(10L)
                        .accountName("Savings")
                        .toAccountName("Checking")
                        .currency("EUR")
                        .transfer(true)
                        .transferGroupKey("skrooge:transfer-group:999")
                        .referenceNumber("skrooge:operation:3")
                        .validationErrors(new ArrayList<>())
                        .build();

        return List.of(splitExpense, transferOut, transferIn);
    }
}
