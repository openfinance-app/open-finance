package org.openfinance.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
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
import org.openfinance.dto.AccountRequest;
import org.openfinance.dto.AccountResponse;
import org.openfinance.dto.ImportedTransaction;
import org.openfinance.dto.TransactionRequest;
import org.openfinance.entity.Account;
import org.openfinance.entity.AccountType;
import org.openfinance.entity.ImportSession;
import org.openfinance.entity.ImportSession.ImportStatus;
import org.openfinance.entity.Payee;
import org.openfinance.entity.Transaction;
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
@DisplayName("ImportService Multi-Account Tests")
class ImportServiceMultiAccountTest {

    private static final Long USER_ID = 123L;

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
    }

    @Test
    @DisplayName(
            "Should import CSV transactions into multiple accounts using parsed account metadata")
    void shouldImportCsvTransactionsIntoMultipleAccounts() throws Exception {
        ImportedTransaction checkingTransaction =
                ImportedTransaction.builder()
                        .transactionDate(LocalDate.of(2024, 1, 10))
                        .payee("Employer")
                        .amount(new BigDecimal("2500.00"))
                        .accountName("Checking")
                        .accountNumber("CHK-001")
                        .currency("USD")
                        .validationErrors(new ArrayList<>())
                        .build();
        ImportedTransaction savingsTransaction =
                ImportedTransaction.builder()
                        .transactionDate(LocalDate.of(2024, 1, 11))
                        .payee("Interest")
                        .amount(new BigDecimal("5.25"))
                        .accountName("Savings")
                        .accountNumber("SAV-002")
                        .currency("USD")
                        .validationErrors(new ArrayList<>())
                        .build();

        ImportSession session =
                buildSession(1L, "CSV", List.of(checkingTransaction, savingsTransaction));
        Account checkingAccount =
                Account.builder()
                        .id(201L)
                        .userId(USER_ID)
                        .name("enc-checking")
                        .type(AccountType.CHECKING)
                        .currency("USD")
                        .isActive(true)
                        .build();
        Account savingsAccount =
                Account.builder()
                        .id(202L)
                        .userId(USER_ID)
                        .name("enc-savings")
                        .type(AccountType.SAVINGS)
                        .currency("USD")
                        .isActive(true)
                        .build();
        List<Account> existingAccounts = new ArrayList<>();
        AtomicLong savedTransactionIds = new AtomicLong(500L);

        when(importSessionRepository.findById(1L)).thenReturn(Optional.of(session));
        when(importSessionRepository.save(any(ImportSession.class)))
                .thenAnswer(invocation -> invocation.getArgument(0, ImportSession.class));
        when(accountRepository.findByUserId(USER_ID)).thenReturn(existingAccounts);
        when(accountService.createAccount(eq(USER_ID), any(AccountRequest.class)))
                .thenReturn(
                        AccountResponse.builder().id(201L).build(),
                        AccountResponse.builder().id(202L).build());
        when(accountRepository.findById(201L)).thenReturn(Optional.of(checkingAccount));
        when(accountRepository.findById(202L)).thenReturn(Optional.of(savingsAccount));
        when(transactionRepository.save(any(Transaction.class)))
                .thenAnswer(
                        invocation -> {
                            Transaction transaction = invocation.getArgument(0, Transaction.class);
                            transaction.setId(savedTransactionIds.incrementAndGet());
                            return transaction;
                        });

        ImportSession result = importService.confirmImport(1L, USER_ID, null, Map.of(), true);

        ArgumentCaptor<AccountRequest> accountRequests =
                ArgumentCaptor.forClass(AccountRequest.class);
        verify(accountService, times(2)).createAccount(eq(USER_ID), accountRequests.capture());
        assertThat(accountRequests.getAllValues())
                .extracting(AccountRequest::getName)
                .containsExactly("Checking", "Savings");
        assertThat(accountRequests.getAllValues())
                .extracting(AccountRequest::getAccountNumber)
                .containsExactly("CHK-001", "SAV-002");
        verify(transactionRepository)
                .save(
                        argThat(
                                (Transaction transaction) ->
                                        transaction.getAccountId().equals(201L)
                                                && transaction
                                                        .getDescription()
                                                        .equals("Employer")));
        verify(transactionRepository)
                .save(
                        argThat(
                                (Transaction transaction) ->
                                        transaction.getAccountId().equals(202L)
                                                && transaction
                                                        .getDescription()
                                                        .equals("Interest")));
        verify(transactionService, times(2))
                .syncTransactionFts(any(Transaction.class), any(), any());
        verify(accountService, times(2)).recalculateBalance(anyLong(), eq(USER_ID));
        verify(netWorthRepository)
                .deleteByUserIdAndSnapshotDateBefore(eq(USER_ID), eq(LocalDate.of(2024, 1, 11)));
        assertThat(result.getStatus()).isEqualTo(ImportStatus.COMPLETED);
        assertThat(result.getImportedCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("Should import QIF transfers across parsed source and destination accounts")
    void shouldImportQifTransfersAcrossMultipleAccounts() throws Exception {
        ImportedTransaction transferTransaction =
                ImportedTransaction.builder()
                        .transactionDate(LocalDate.of(2024, 1, 15))
                        .payee("Transfer to savings")
                        .memo("Automatic transfer")
                        .amount(new BigDecimal("-500.00"))
                        .accountName("Checking")
                        .toAccountName("Savings")
                        .transfer(true)
                        .category("Transfer")
                        .currency("USD")
                        .validationErrors(new ArrayList<>())
                        .build();

        ImportSession session = buildSession(2L, "QIF", List.of(transferTransaction));
        Account checkingAccount =
                Account.builder()
                        .id(301L)
                        .userId(USER_ID)
                        .name("enc-checking")
                        .type(AccountType.CHECKING)
                        .currency("USD")
                        .isActive(true)
                        .build();
        Account savingsAccount =
                Account.builder()
                        .id(302L)
                        .userId(USER_ID)
                        .name("enc-savings")
                        .type(AccountType.SAVINGS)
                        .currency("USD")
                        .isActive(true)
                        .build();
        List<Account> existingAccounts = new ArrayList<>();

        when(importSessionRepository.findById(2L)).thenReturn(Optional.of(session));
        when(importSessionRepository.save(any(ImportSession.class)))
                .thenAnswer(invocation -> invocation.getArgument(0, ImportSession.class));
        when(accountRepository.findByUserId(USER_ID)).thenReturn(existingAccounts);
        when(accountService.createAccount(eq(USER_ID), any(AccountRequest.class)))
                .thenReturn(
                        AccountResponse.builder().id(301L).build(),
                        AccountResponse.builder().id(302L).build());
        when(accountRepository.findById(301L)).thenReturn(Optional.of(checkingAccount));
        when(accountRepository.findById(302L)).thenReturn(Optional.of(savingsAccount));

        ImportSession result = importService.confirmImport(2L, USER_ID, null, Map.of(), true);

        verify(transactionService)
                .createTransfer(
                        eq(USER_ID), argThat(request -> matchesTransfer(request, 301L, 302L)));
        verify(transactionRepository, never()).save(any(Transaction.class));
        verify(accountService, times(2)).recalculateBalance(anyLong(), eq(USER_ID));
        assertThat(result.getStatus()).isEqualTo(ImportStatus.COMPLETED);
        assertThat(result.getImportedCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("Should deduplicate paired QIF transfer entries into one transfer")
    void shouldDeduplicatePairedQifTransferEntriesIntoOneTransfer() throws Exception {
        ImportedTransaction outgoingTransfer =
                ImportedTransaction.builder()
                        .transactionDate(LocalDate.of(2024, 1, 8))
                        .payee("Transfer to Savings")
                        .memo("Automatic transfer")
                        .amount(new BigDecimal("-500.00"))
                        .accountName("Checking Account")
                        .toAccountName("Savings Account")
                        .transfer(true)
                        .category("Transfer")
                        .referenceNumber("CHK-QIF-002")
                        .currency("USD")
                        .validationErrors(new ArrayList<>())
                        .build();
        ImportedTransaction incomingTransfer =
                ImportedTransaction.builder()
                        .transactionDate(LocalDate.of(2024, 1, 8))
                        .payee("Transfer from Checking")
                        .memo("Automatic transfer")
                        .amount(new BigDecimal("500.00"))
                        .accountName("Savings Account")
                        .toAccountName("Checking Account")
                        .transfer(true)
                        .category("Transfer")
                        .referenceNumber("SAV-QIF-001")
                        .currency("USD")
                        .validationErrors(new ArrayList<>())
                        .build();

        ImportSession session =
                buildSession(3L, "QIF", List.of(outgoingTransfer, incomingTransfer));
        Account checkingAccount =
                Account.builder()
                        .id(401L)
                        .userId(USER_ID)
                        .name("enc-checking")
                        .type(AccountType.CHECKING)
                        .currency("USD")
                        .isActive(true)
                        .build();
        Account savingsAccount =
                Account.builder()
                        .id(402L)
                        .userId(USER_ID)
                        .name("enc-savings")
                        .type(AccountType.SAVINGS)
                        .currency("USD")
                        .isActive(true)
                        .build();
        List<Account> existingAccounts = new ArrayList<>();

        when(importSessionRepository.findById(3L)).thenReturn(Optional.of(session));
        when(importSessionRepository.save(any(ImportSession.class)))
                .thenAnswer(invocation -> invocation.getArgument(0, ImportSession.class));
        when(accountRepository.findByUserId(USER_ID)).thenReturn(existingAccounts);
        when(accountService.createAccount(eq(USER_ID), any(AccountRequest.class)))
                .thenReturn(
                        AccountResponse.builder().id(401L).build(),
                        AccountResponse.builder().id(402L).build());
        when(accountRepository.findById(401L)).thenReturn(Optional.of(checkingAccount));
        when(accountRepository.findById(402L)).thenReturn(Optional.of(savingsAccount));

        ImportSession result = importService.confirmImport(3L, USER_ID, null, Map.of(), true);

        verify(transactionService, times(1))
                .createTransfer(
                        eq(USER_ID), argThat(request -> matchesTransfer(request, 401L, 402L)));
        verify(transactionRepository, never()).save(any(Transaction.class));
        assertThat(result.getImportedCount()).isEqualTo(1);
        assertThat(result.getSkippedCount()).isZero();
    }

    @Test
    @DisplayName("Should deduplicate paired QIF transfer entries dated one day apart")
    void shouldDeduplicatePairedQifTransferEntriesDatedOneDayApart() throws Exception {
        ImportedTransaction outgoingTransfer =
                ImportedTransaction.builder()
                        .transactionDate(LocalDate.of(2026, 1, 26))
                        .payee("FACTURE CARTE")
                        .amount(new BigDecimal("-1527.48"))
                        .accountName("Checking Account")
                        .toAccountName("Epargne BOA")
                        .transfer(true)
                        .category("Transfer")
                        .currency("EUR")
                        .validationErrors(new ArrayList<>())
                        .build();
        ImportedTransaction incomingTransfer =
                ImportedTransaction.builder()
                        .transactionDate(LocalDate.of(2026, 1, 27))
                        .payee("FACTURE CARTE")
                        .amount(new BigDecimal("1527.48"))
                        .accountName("Epargne BOA")
                        .toAccountName("Checking Account")
                        .transfer(true)
                        .category("Transfer")
                        .currency("XOF")
                        .validationErrors(new ArrayList<>())
                        .build();

        ImportSession session =
                buildSession(8L, "QIF", List.of(outgoingTransfer, incomingTransfer));
        Account checkingAccount =
                Account.builder()
                        .id(901L)
                        .userId(USER_ID)
                        .name("enc-checking")
                        .type(AccountType.CHECKING)
                        .currency("EUR")
                        .isActive(true)
                        .build();
        Account savingsAccount =
                Account.builder()
                        .id(902L)
                        .userId(USER_ID)
                        .name("enc-savings")
                        .type(AccountType.SAVINGS)
                        .currency("XOF")
                        .isActive(true)
                        .build();
        List<Account> existingAccounts = new ArrayList<>();

        when(importSessionRepository.findById(8L)).thenReturn(Optional.of(session));
        when(importSessionRepository.save(any(ImportSession.class)))
                .thenAnswer(invocation -> invocation.getArgument(0, ImportSession.class));
        when(accountRepository.findByUserId(USER_ID)).thenReturn(existingAccounts);
        when(accountService.createAccount(eq(USER_ID), any(AccountRequest.class)))
                .thenAnswer(
                        invocation -> {
                            AccountRequest request = invocation.getArgument(1);
                            Long id = "Checking Account".equals(request.getName()) ? 901L : 902L;
                            return AccountResponse.builder().id(id).build();
                        });
        when(accountRepository.findById(901L)).thenReturn(Optional.of(checkingAccount));
        when(accountRepository.findById(902L)).thenReturn(Optional.of(savingsAccount));

        ImportSession result = importService.confirmImport(8L, USER_ID, null, Map.of(), true);

        verify(transactionService, times(1))
                .createTransfer(
                        eq(USER_ID),
                        argThat(
                                request ->
                                        request.getAccountId().equals(901L)
                                                && request.getToAccountId().equals(902L)
                                                && request.getAmount()
                                                                .compareTo(
                                                                        new BigDecimal("1527.4800"))
                                                        == 0));
        assertThat(result.getImportedCount()).isEqualTo(1);
        assertThat(result.getSkippedCount()).isZero();
    }

    @Test
    @DisplayName("Should deduplicate ungrouped QIF transfer sides with different native amounts")
    void shouldDeduplicateUngroupedQifTransferSidesWithDifferentNativeAmounts() throws Exception {
        ImportedTransaction eurOutgoing =
                ImportedTransaction.builder()
                        .transactionDate(LocalDate.of(2025, 4, 22))
                        .payee("Retrait a cotonou (WU)")
                        .amount(new BigDecimal("-153.55"))
                        .accountName("Checking Account")
                        .toAccountName("Porteefeuille FCFA")
                        .transfer(true)
                        .category("Transfer")
                        .currency("EUR")
                        .validationErrors(new ArrayList<>())
                        .build();
        ImportedTransaction xofIncoming =
                ImportedTransaction.builder()
                        .transactionDate(LocalDate.of(2025, 4, 22))
                        .payee("Retrait a cotonou (WU)")
                        .amount(new BigDecimal("100722.1439"))
                        .accountName("Porteefeuille FCFA")
                        .toAccountName("Checking Account")
                        .transfer(true)
                        .category("Transfer")
                        .currency("XOF")
                        .validationErrors(new ArrayList<>())
                        .build();

        ImportSession session = buildSession(9L, "QIF", List.of(eurOutgoing, xofIncoming));
        Account checkingAccount =
                Account.builder()
                        .id(1001L)
                        .userId(USER_ID)
                        .name("enc-checking")
                        .type(AccountType.CHECKING)
                        .currency("EUR")
                        .isActive(true)
                        .build();
        Account fcfaAccount =
                Account.builder()
                        .id(1002L)
                        .userId(USER_ID)
                        .name("enc-fcfa")
                        .type(AccountType.CHECKING)
                        .currency("XOF")
                        .isActive(true)
                        .build();
        List<Account> existingAccounts = new ArrayList<>();

        when(importSessionRepository.findById(9L)).thenReturn(Optional.of(session));
        when(importSessionRepository.save(any(ImportSession.class)))
                .thenAnswer(invocation -> invocation.getArgument(0, ImportSession.class));
        when(accountRepository.findByUserId(USER_ID)).thenReturn(existingAccounts);
        when(accountService.createAccount(eq(USER_ID), any(AccountRequest.class)))
                .thenAnswer(
                        invocation -> {
                            AccountRequest request = invocation.getArgument(1);
                            Long id = "Checking Account".equals(request.getName()) ? 1001L : 1002L;
                            return AccountResponse.builder().id(id).build();
                        });
        when(accountRepository.findById(1001L)).thenReturn(Optional.of(checkingAccount));
        when(accountRepository.findById(1002L)).thenReturn(Optional.of(fcfaAccount));

        ImportSession result = importService.confirmImport(9L, USER_ID, null, Map.of(), true);

        verify(transactionService, times(1))
                .createTransfer(
                        eq(USER_ID),
                        argThat(
                                request ->
                                        request.getAccountId().equals(1001L)
                                                && request.getToAccountId().equals(1002L)
                                                && request.getAmount()
                                                                .compareTo(
                                                                        new BigDecimal("153.5500"))
                                                        == 0
                                                && "EUR".equals(request.getCurrency())));
        assertThat(result.getImportedCount()).isEqualTo(1);
        assertThat(result.getSkippedCount()).isZero();
    }

    @Test
    @DisplayName("Should use signed QIF transfer amount to determine transfer direction")
    void shouldUseSignedQifTransferAmountToDetermineTransferDirection() throws Exception {
        ImportedTransaction incomingSideFirst =
                ImportedTransaction.builder()
                        .transactionDate(LocalDate.of(2024, 1, 8))
                        .payee("Transfer from Checking")
                        .memo("Automatic transfer")
                        .amount(new BigDecimal("500.00"))
                        .accountName("Savings Account")
                        .toAccountName("Checking Account")
                        .transfer(true)
                        .category("Transfer")
                        .currency("USD")
                        .validationErrors(new ArrayList<>())
                        .build();
        ImportedTransaction outgoingSideSecond =
                ImportedTransaction.builder()
                        .transactionDate(LocalDate.of(2024, 1, 8))
                        .payee("Transfer to Savings")
                        .memo("Automatic transfer")
                        .amount(new BigDecimal("-500.00"))
                        .accountName("Checking Account")
                        .toAccountName("Savings Account")
                        .transfer(true)
                        .category("Transfer")
                        .currency("USD")
                        .validationErrors(new ArrayList<>())
                        .build();

        ImportSession session =
                buildSession(6L, "QIF", List.of(incomingSideFirst, outgoingSideSecond));
        Account checkingAccount =
                Account.builder()
                        .id(701L)
                        .userId(USER_ID)
                        .name("enc-checking")
                        .type(AccountType.CHECKING)
                        .currency("USD")
                        .isActive(true)
                        .build();
        Account savingsAccount =
                Account.builder()
                        .id(702L)
                        .userId(USER_ID)
                        .name("enc-savings")
                        .type(AccountType.SAVINGS)
                        .currency("USD")
                        .isActive(true)
                        .build();
        List<Account> existingAccounts = new ArrayList<>();

        when(importSessionRepository.findById(6L)).thenReturn(Optional.of(session));
        when(importSessionRepository.save(any(ImportSession.class)))
                .thenAnswer(invocation -> invocation.getArgument(0, ImportSession.class));
        when(accountRepository.findByUserId(USER_ID)).thenReturn(existingAccounts);
        when(accountService.createAccount(eq(USER_ID), any(AccountRequest.class)))
                .thenAnswer(
                        invocation -> {
                            AccountRequest request = invocation.getArgument(1);
                            Long id = "Checking Account".equals(request.getName()) ? 701L : 702L;
                            return AccountResponse.builder().id(id).build();
                        });
        when(accountRepository.findById(701L)).thenReturn(Optional.of(checkingAccount));
        when(accountRepository.findById(702L)).thenReturn(Optional.of(savingsAccount));

        ImportSession result = importService.confirmImport(6L, USER_ID, null, Map.of(), true);

        verify(transactionService, times(1))
                .createTransfer(
                        eq(USER_ID), argThat(request -> matchesTransfer(request, 701L, 702L)));
        assertThat(result.getImportedCount()).isEqualTo(1);
        assertThat(result.getSkippedCount()).isZero();
    }

    @Test
    @DisplayName("Should preserve repeated same-day QIF transfers with same amount")
    void shouldPreserveRepeatedSameDayQifTransfersWithSameAmount() throws Exception {
        ImportedTransaction firstOutgoing =
                ImportedTransaction.builder()
                        .transactionDate(LocalDate.of(2026, 2, 2))
                        .payee("Transfer one")
                        .amount(new BigDecimal("-1527.48"))
                        .accountName("Checking Account")
                        .toAccountName("Savings Account")
                        .transfer(true)
                        .category("Transfer")
                        .currency("USD")
                        .validationErrors(new ArrayList<>())
                        .build();
        ImportedTransaction secondOutgoing =
                ImportedTransaction.builder()
                        .transactionDate(LocalDate.of(2026, 2, 2))
                        .payee("Transfer two")
                        .amount(new BigDecimal("-1527.48"))
                        .accountName("Checking Account")
                        .toAccountName("Savings Account")
                        .transfer(true)
                        .category("Transfer")
                        .currency("USD")
                        .validationErrors(new ArrayList<>())
                        .build();
        ImportedTransaction firstIncoming =
                ImportedTransaction.builder()
                        .transactionDate(LocalDate.of(2026, 2, 2))
                        .payee("Transfer one")
                        .amount(new BigDecimal("1527.48"))
                        .accountName("Savings Account")
                        .toAccountName("Checking Account")
                        .transfer(true)
                        .category("Transfer")
                        .currency("USD")
                        .validationErrors(new ArrayList<>())
                        .build();
        ImportedTransaction secondIncoming =
                ImportedTransaction.builder()
                        .transactionDate(LocalDate.of(2026, 2, 2))
                        .payee("Transfer two")
                        .amount(new BigDecimal("1527.48"))
                        .accountName("Savings Account")
                        .toAccountName("Checking Account")
                        .transfer(true)
                        .category("Transfer")
                        .currency("USD")
                        .validationErrors(new ArrayList<>())
                        .build();

        ImportSession session =
                buildSession(
                        7L,
                        "QIF",
                        List.of(firstOutgoing, secondOutgoing, firstIncoming, secondIncoming));
        Account checkingAccount =
                Account.builder()
                        .id(801L)
                        .userId(USER_ID)
                        .name("enc-checking")
                        .type(AccountType.CHECKING)
                        .currency("USD")
                        .isActive(true)
                        .build();
        Account savingsAccount =
                Account.builder()
                        .id(802L)
                        .userId(USER_ID)
                        .name("enc-savings")
                        .type(AccountType.SAVINGS)
                        .currency("USD")
                        .isActive(true)
                        .build();
        List<Account> existingAccounts = new ArrayList<>();

        when(importSessionRepository.findById(7L)).thenReturn(Optional.of(session));
        when(importSessionRepository.save(any(ImportSession.class)))
                .thenAnswer(invocation -> invocation.getArgument(0, ImportSession.class));
        when(accountRepository.findByUserId(USER_ID)).thenReturn(existingAccounts);
        when(accountService.createAccount(eq(USER_ID), any(AccountRequest.class)))
                .thenReturn(
                        AccountResponse.builder().id(801L).build(),
                        AccountResponse.builder().id(802L).build());
        when(accountRepository.findById(801L)).thenReturn(Optional.of(checkingAccount));
        when(accountRepository.findById(802L)).thenReturn(Optional.of(savingsAccount));

        ImportSession result = importService.confirmImport(7L, USER_ID, null, Map.of(), true);

        verify(transactionService, times(2))
                .createTransfer(
                        eq(USER_ID),
                        argThat(
                                request ->
                                        request.getAccountId().equals(801L)
                                                && request.getToAccountId().equals(802L)
                                                && request.getAmount()
                                                                .compareTo(
                                                                        new BigDecimal("1527.4800"))
                                                        == 0));
        assertThat(result.getImportedCount()).isEqualTo(2);
        assertThat(result.getSkippedCount()).isZero();
    }

    @Test
    @DisplayName("Should deduplicate Skrooge transfer entries with different side amounts")
    void shouldDeduplicateSkroogeTransferEntriesWithDifferentSideAmounts() throws Exception {
        ImportedTransaction euroOut =
                ImportedTransaction.builder()
                        .transactionDate(LocalDate.of(2024, 11, 13))
                        .payee("MOONPAY")
                        .memo("Crypto purchase")
                        .amount(new BigDecimal("-200.56"))
                        .accountName("Checking Account")
                        .toAccountName("Crypto SOL")
                        .transfer(true)
                        .transferGroupKey("csv:transfer:5")
                        .category("Transfert")
                        .referenceNumber("1479")
                        .currency("EUR")
                        .validationErrors(new ArrayList<>())
                        .build();
        ImportedTransaction solIn =
                ImportedTransaction.builder()
                        .transactionDate(LocalDate.of(2024, 11, 13))
                        .payee("MOONPAY")
                        .memo("Crypto purchase")
                        .amount(new BigDecimal("0.96680000"))
                        .accountName("Crypto SOL")
                        .toAccountName("Checking Account")
                        .transfer(true)
                        .transferGroupKey("csv:transfer:5")
                        .category("Transfert")
                        .referenceNumber("1503")
                        .currency("SOL")
                        .validationErrors(new ArrayList<>())
                        .build();

        ImportSession session = buildSession(4L, "CSV", List.of(euroOut, solIn));
        Account checkingAccount =
                Account.builder()
                        .id(501L)
                        .userId(USER_ID)
                        .name("enc-checking")
                        .type(AccountType.CHECKING)
                        .currency("EUR")
                        .isActive(true)
                        .build();
        Account solAccount =
                Account.builder()
                        .id(502L)
                        .userId(USER_ID)
                        .name("enc-sol")
                        .type(AccountType.CHECKING)
                        .currency("SOL")
                        .isActive(true)
                        .build();
        List<Account> existingAccounts = new ArrayList<>();

        when(importSessionRepository.findById(4L)).thenReturn(Optional.of(session));
        when(importSessionRepository.save(any(ImportSession.class)))
                .thenAnswer(invocation -> invocation.getArgument(0, ImportSession.class));
        when(accountRepository.findByUserId(USER_ID)).thenReturn(existingAccounts);
        when(accountService.createAccount(eq(USER_ID), any(AccountRequest.class)))
                .thenReturn(
                        AccountResponse.builder().id(501L).build(),
                        AccountResponse.builder().id(502L).build());
        when(accountRepository.findById(501L)).thenReturn(Optional.of(checkingAccount));
        when(accountRepository.findById(502L)).thenReturn(Optional.of(solAccount));

        ImportSession result = importService.confirmImport(4L, USER_ID, null, Map.of(), true);

        verify(transactionService, times(1))
                .createTransfer(
                        eq(USER_ID),
                        argThat(
                                request ->
                                        request.getAccountId().equals(501L)
                                                && request.getToAccountId().equals(502L)
                                                && request.getAmount()
                                                                .compareTo(
                                                                        new BigDecimal("200.5600"))
                                                        == 0));
        verify(transactionRepository, never()).save(any(Transaction.class));
        assertThat(result.getImportedCount()).isEqualTo(1);
        assertThat(result.getSkippedCount()).isZero();
    }

    @Test
    @DisplayName(
            "Should preserve source crypto account currency when transfer pair points back to it")
    void shouldPreserveCryptoAccountCurrencyWhenTransferPairPointsBackToIt() throws Exception {
        ImportedTransaction bchOut =
                ImportedTransaction.builder()
                        .transactionDate(LocalDate.of(2025, 7, 20))
                        .payee("Transfert")
                        .amount(new BigDecimal("-1.02000000"))
                        .accountName("Crypto BCH")
                        .toAccountName("BTC")
                        .transfer(true)
                        .transferGroupKey("csv:transfer:58")
                        .category("Transfert")
                        .referenceNumber("2301")
                        .currency("BCH")
                        .validationErrors(new ArrayList<>())
                        .build();
        ImportedTransaction btcIn =
                ImportedTransaction.builder()
                        .transactionDate(LocalDate.of(2025, 7, 20))
                        .payee("Transfert")
                        .amount(new BigDecimal("0.00453915"))
                        .accountName("BTC")
                        .toAccountName("Crypto BCH")
                        .transfer(true)
                        .transferGroupKey("csv:transfer:58")
                        .category("Transfert")
                        .referenceNumber("2302")
                        .currency("BTC")
                        .validationErrors(new ArrayList<>())
                        .build();

        ImportSession session = buildSession(5L, "CSV", List.of(bchOut, btcIn));
        Account bchAccount =
                Account.builder()
                        .id(601L)
                        .userId(USER_ID)
                        .name("enc-bch")
                        .type(AccountType.CHECKING)
                        .currency("BCH")
                        .isActive(true)
                        .build();
        Account btcAccount =
                Account.builder()
                        .id(602L)
                        .userId(USER_ID)
                        .name("enc-btc")
                        .type(AccountType.CHECKING)
                        .currency("BTC")
                        .isActive(true)
                        .build();
        List<Account> existingAccounts = new ArrayList<>();

        when(importSessionRepository.findById(5L)).thenReturn(Optional.of(session));
        when(importSessionRepository.save(any(ImportSession.class)))
                .thenAnswer(invocation -> invocation.getArgument(0, ImportSession.class));
        when(accountRepository.findByUserId(USER_ID)).thenReturn(existingAccounts);
        when(accountService.createAccount(eq(USER_ID), any(AccountRequest.class)))
                .thenReturn(
                        AccountResponse.builder().id(601L).build(),
                        AccountResponse.builder().id(602L).build());
        when(accountRepository.findById(601L)).thenReturn(Optional.of(bchAccount));
        when(accountRepository.findById(602L)).thenReturn(Optional.of(btcAccount));

        ImportSession result = importService.confirmImport(5L, USER_ID, null, Map.of(), true);

        ArgumentCaptor<AccountRequest> accountRequests =
                ArgumentCaptor.forClass(AccountRequest.class);
        verify(accountService, times(2)).createAccount(eq(USER_ID), accountRequests.capture());
        assertThat(accountRequests.getAllValues())
                .extracting(AccountRequest::getName)
                .containsExactly("Crypto BCH", "BTC");
        assertThat(accountRequests.getAllValues())
                .extracting(AccountRequest::getCurrency)
                .containsExactly("BCH", "BTC");
        verify(transactionService, times(1))
                .createTransfer(
                        eq(USER_ID),
                        argThat(
                                request ->
                                        request.getAccountId().equals(601L)
                                                && request.getToAccountId().equals(602L)
                                                && "BCH".equals(request.getCurrency())));
        assertThat(result.getImportedCount()).isEqualTo(1);
        assertThat(result.getSkippedCount()).isZero();
    }

    private ImportSession buildSession(
            Long sessionId, String fileFormat, List<ImportedTransaction> transactions)
            throws Exception {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("transactions", transactions);
        metadata.put("count", transactions.size());
        metadata.put("ledgerBalance", BigDecimal.ZERO);
        metadata.put("fileCurrency", "USD");

        return ImportSession.builder()
                .id(sessionId)
                .uploadId("upload-" + sessionId)
                .userId(USER_ID)
                .fileName("multi-account." + fileFormat.toLowerCase())
                .fileFormat(fileFormat)
                .status(ImportStatus.PARSED)
                .metadata(objectMapper.writeValueAsString(metadata))
                .build();
    }

    private boolean matchesTransfer(
            TransactionRequest request, Long fromAccountId, Long toAccountId) {
        return request.getAccountId().equals(fromAccountId)
                && request.getToAccountId().equals(toAccountId)
                && request.getAmount().compareTo(new BigDecimal("500.0000")) == 0;
    }
}
