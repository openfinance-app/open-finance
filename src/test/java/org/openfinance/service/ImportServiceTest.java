package org.openfinance.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.openfinance.dto.ImportParseResult;
import org.openfinance.dto.ImportedTransaction;
import org.openfinance.entity.Account;
import org.openfinance.entity.AccountType;
import org.openfinance.entity.ImportSession;
import org.openfinance.entity.ImportSession.ImportStatus;
import org.openfinance.entity.Transaction;
import org.openfinance.entity.TransactionType;
import org.openfinance.exception.ResourceNotFoundException;
import org.openfinance.repository.AccountRepository;
import org.openfinance.repository.CategoryRepository;
import org.openfinance.repository.ImportSessionRepository;
import org.openfinance.repository.TransactionRepository;
import org.openfinance.repository.UserRepository;
import org.openfinance.service.parser.CsvParser;
import org.openfinance.service.parser.OfxParser;
import org.openfinance.service.parser.QifParser;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Unit tests for ImportService.
 * 
 * <p>
 * Tests all major import functionality:
 * </p>
 * <ul>
 * <li>Starting import sessions</li>
 * <li>File parsing (QIF, OFX, QFX)</li>
 * <li>Duplicate detection</li>
 * <li>Category matching and suggestions</li>
 * <li>Transaction review</li>
 * <li>Import confirmation</li>
 * <li>Session cancellation</li>
 * </ul>
 * 
 * @author Open-Finance Development Team
 * @version 1.0
 * @since 2024-02-02
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ImportService Unit Tests")
class ImportServiceTest {

        @Mock
        private ImportSessionRepository importSessionRepository;

        @Mock
        private TransactionRepository transactionRepository;

        @Mock
        private AccountRepository accountRepository;

        @Mock
        private CategoryRepository categoryRepository;

        @Mock
        private UserRepository userRepository;

        @Mock
        private FileStorageService fileStorageService;

        @Mock
        private FileValidationService fileValidationService;

        @Mock
        private QifParser qifParser;

        @Mock
        private OfxParser ofxParser;

        @Mock
        private ObjectMapper objectMapper;

        @Mock
        private CsvParser csvParser;

        @Mock
        private AutoCategorizationService autoCategorizationService;

        @Mock
        private AccountService accountService;

        @Mock
        private TransactionRuleService transactionRuleService;

        @Mock
        private OperationHistoryService operationHistoryService;

        @InjectMocks
        private ImportService importService;

        private static final Long USER_ID = 123L;
        private static final Long ACCOUNT_ID = 1L;
        private static final String UPLOAD_ID = "550e8400-e29b-41d4-a716-446655440000";
        private static final String FILE_NAME = "transactions.qif";

        private Account testAccount;
        private ImportSession testSession;
        private List<ImportedTransaction> testTransactions;

        @BeforeEach
        void setUp() {
                // Setup test account
                testAccount = Account.builder()
                                .id(ACCOUNT_ID)
                                .userId(USER_ID)
                                .name("Test Account")
                                .type(AccountType.CHECKING)
                                .balance(new BigDecimal("1000.00"))
                                .currency("USD")
                                .isActive(true)
                                .build();

                // Setup test import session
                testSession = ImportSession.builder()
                                .id(1L)
                                .uploadId(UPLOAD_ID)
                                .userId(USER_ID)
                                .fileName(FILE_NAME)
                                .fileFormat("QIF")
                                .accountId(ACCOUNT_ID)
                                .status(ImportStatus.PENDING)
                                .totalTransactions(0)
                                .importedCount(0)
                                .skippedCount(0)
                                .errorCount(0)
                                .build();

                // Setup test transactions
                testTransactions = createTestTransactions();
        }

        // ========================================
        // Tests for startImport()
        // ========================================

        @Test
        @DisplayName("Should start import successfully with valid upload ID and account")
        void shouldStartImportSuccessfully() throws Exception {
                // Given
                when(fileStorageService.fileExists(UPLOAD_ID)).thenReturn(true);
                when(fileStorageService.getOriginalFileName(UPLOAD_ID)).thenReturn(FILE_NAME);
                when(accountRepository.findByIdAndUserId(ACCOUNT_ID, USER_ID))
                                .thenReturn(Optional.of(testAccount));
                when(importSessionRepository.save(any(ImportSession.class)))
                                .thenReturn(testSession);
                // Mock for parseFileAsync - it needs to find the session by ID
                when(importSessionRepository.findById(1L)).thenReturn(Optional.of(testSession));
                when(fileStorageService.getFileContent(UPLOAD_ID))
                                .thenReturn(new ByteArrayInputStream("QIF content".getBytes()));
                when(qifParser.parseFile(any(InputStream.class), anyString())).thenReturn(testTransactions);
                when(objectMapper.writeValueAsString(any())).thenReturn("{\"transactions\":[],\"count\":2}");

                // When
                ImportSession result = importService.startImport(UPLOAD_ID, USER_ID, ACCOUNT_ID);

                // Then
                assertThat(result).isNotNull();
                assertThat(result.getUploadId()).isEqualTo(UPLOAD_ID);
                assertThat(result.getUserId()).isEqualTo(USER_ID);
                assertThat(result.getAccountId()).isEqualTo(ACCOUNT_ID);
                assertThat(result.getFileName()).isEqualTo(FILE_NAME);
                assertThat(result.getFileFormat()).isEqualTo("QIF");
                // Note: Status will be PARSED because async method completes immediately in
                // tests
                // In production, @Async would make it PENDING initially

                // Verify interactions
                verify(fileStorageService).fileExists(UPLOAD_ID);
                verify(fileStorageService).getOriginalFileName(UPLOAD_ID);
                verify(accountRepository).findByIdAndUserId(ACCOUNT_ID, USER_ID);
                verify(importSessionRepository, atLeastOnce()).save(any(ImportSession.class));
        }

        @Test
        @DisplayName("Should start import without account ID")
        void shouldStartImportWithoutAccountId() throws Exception {
                // Given
                when(fileStorageService.fileExists(UPLOAD_ID)).thenReturn(true);
                when(fileStorageService.getOriginalFileName(UPLOAD_ID)).thenReturn(FILE_NAME);
                when(importSessionRepository.save(any(ImportSession.class)))
                                .thenReturn(testSession);
                // Mock for parseFileAsync - it needs to find the session by ID
                when(importSessionRepository.findById(1L)).thenReturn(Optional.of(testSession));
                when(fileStorageService.getFileContent(UPLOAD_ID))
                                .thenReturn(new ByteArrayInputStream("QIF content".getBytes()));
                when(qifParser.parseFile(any(InputStream.class), anyString())).thenReturn(testTransactions);
                when(objectMapper.writeValueAsString(any())).thenReturn("{\"transactions\":[],\"count\":2}");

                // When
                ImportSession result = importService.startImport(UPLOAD_ID, USER_ID, null);

                // Then
                assertThat(result).isNotNull();
                assertThat(result.getUploadId()).isEqualTo(UPLOAD_ID);
                assertThat(result.getUserId()).isEqualTo(USER_ID);

                // Verify account validation was NOT called
                verify(accountRepository, never()).findByIdAndUserId(anyLong(), anyLong());
        }

        @Test
        @DisplayName("Should throw ResourceNotFoundException when upload file not found")
        void shouldThrowExceptionWhenUploadNotFound() {
                // Given
                when(fileStorageService.fileExists(UPLOAD_ID)).thenReturn(false);

                // When & Then
                assertThatThrownBy(() -> importService.startImport(UPLOAD_ID, USER_ID, ACCOUNT_ID))
                                .isInstanceOf(ResourceNotFoundException.class)
                                .hasMessageContaining("Uploaded file not found");

                verify(importSessionRepository, never()).save(any());
        }

        @Test
        @DisplayName("Should throw ResourceNotFoundException when account not found")
        void shouldThrowExceptionWhenAccountNotFound() {
                // Given
                when(fileStorageService.fileExists(UPLOAD_ID)).thenReturn(true);
                when(fileStorageService.getOriginalFileName(UPLOAD_ID)).thenReturn(FILE_NAME);
                when(accountRepository.findByIdAndUserId(ACCOUNT_ID, USER_ID))
                                .thenReturn(Optional.empty());

                // When & Then
                assertThatThrownBy(() -> importService.startImport(UPLOAD_ID, USER_ID, ACCOUNT_ID))
                                .isInstanceOf(ResourceNotFoundException.class)
                                .hasMessageContaining("Account not found");

                verify(importSessionRepository, never()).save(any());
        }

        @Test
        @DisplayName("Should throw IllegalArgumentException when account is inactive")
        void shouldThrowExceptionWhenAccountIsInactive() {
                // Given
                testAccount.setIsActive(false);
                when(fileStorageService.fileExists(UPLOAD_ID)).thenReturn(true);
                when(fileStorageService.getOriginalFileName(UPLOAD_ID)).thenReturn(FILE_NAME);
                when(accountRepository.findByIdAndUserId(ACCOUNT_ID, USER_ID))
                                .thenReturn(Optional.of(testAccount));

                // When & Then
                assertThatThrownBy(() -> importService.startImport(UPLOAD_ID, USER_ID, ACCOUNT_ID))
                                .isInstanceOf(IllegalArgumentException.class)
                                .hasMessageContaining("Cannot import to inactive account");

                verify(importSessionRepository, never()).save(any());
        }

        @Test
        @DisplayName("Should detect QFX format as OFX")
        void shouldDetectQfxAsOfx() throws Exception {
                // Given
                String qfxFileName = "transactions.qfx";
                ImportSession qfxSession = ImportSession.builder()
                                .id(1L)
                                .uploadId(UPLOAD_ID)
                                .userId(USER_ID)
                                .fileName(qfxFileName)
                                .fileFormat("OFX") // Should convert QFX to OFX
                                .status(ImportStatus.PENDING)
                                .build();

                when(fileStorageService.fileExists(UPLOAD_ID)).thenReturn(true);
                when(fileStorageService.getOriginalFileName(UPLOAD_ID)).thenReturn(qfxFileName);
                when(importSessionRepository.save(any(ImportSession.class)))
                                .thenAnswer(invocation -> {
                                        ImportSession session = invocation.getArgument(0);
                                        session.setId(1L); // Simulate DB assigning ID
                                        assertThat(session.getFileFormat()).isEqualTo("OFX"); // QFX → OFX
                                        return session;
                                });
                when(importSessionRepository.findById(1L)).thenReturn(Optional.of(qfxSession));
                when(fileStorageService.getFileContent(UPLOAD_ID))
                                .thenReturn(new ByteArrayInputStream("OFX content".getBytes()));
                ImportParseResult ofxResult = ImportParseResult.builder().transactions(testTransactions)
                                .ledgerBalance(BigDecimal.ZERO).currency("USD").build();
                when(ofxParser.parseFileToResult(any(InputStream.class), anyString())).thenReturn(ofxResult);
                when(objectMapper.writeValueAsString(any())).thenReturn("{\"transactions\":[],\"count\":2}");

                // When
                ImportSession result = importService.startImport(UPLOAD_ID, USER_ID, null);

                // Then
                assertThat(result.getFileFormat()).isEqualTo("OFX");
                verify(importSessionRepository, atLeastOnce()).save(any(ImportSession.class));
        }

        // ========================================
        // Tests for parseFileAsync()
        // ========================================

        @Test
        @DisplayName("Should parse QIF file successfully")
        void shouldParseQifFileSuccessfully() throws Exception {
                // Given
                testSession.setStatus(ImportStatus.PARSING);
                InputStream mockStream = new ByteArrayInputStream("!Type:Bank\n".getBytes());

                when(importSessionRepository.findById(1L)).thenReturn(Optional.of(testSession));
                when(fileStorageService.getFileContent(UPLOAD_ID)).thenReturn(mockStream);
                when(qifParser.parseFile(any(InputStream.class), eq(FILE_NAME)))
                                .thenReturn(testTransactions);
                when(objectMapper.writeValueAsString(any())).thenReturn("{\"transactions\":[]}");
                when(importSessionRepository.save(any(ImportSession.class))).thenReturn(testSession);

                // When
                importService.parseFileAsync(1L);

                // Then
                verify(importSessionRepository, atLeast(2)).save(any(ImportSession.class));
                verify(qifParser).parseFile(any(InputStream.class), eq(FILE_NAME));
                verify(objectMapper).writeValueAsString(any());
        }

        @Test
        @DisplayName("Should parse OFX file successfully")
        void shouldParseOfxFileSuccessfully() throws Exception {
                // Given
                testSession.setFileFormat("OFX");
                testSession.setFileName("transactions.ofx");
                InputStream mockStream = new ByteArrayInputStream("<OFX>".getBytes());

                when(importSessionRepository.findById(1L)).thenReturn(Optional.of(testSession));
                when(fileStorageService.getFileContent(UPLOAD_ID)).thenReturn(mockStream);
                ImportParseResult ofxResult = ImportParseResult.builder().transactions(testTransactions)
                                .ledgerBalance(BigDecimal.ZERO).currency("USD").build();
                when(ofxParser.parseFileToResult(any(InputStream.class), anyString()))
                                .thenReturn(ofxResult);
                when(objectMapper.writeValueAsString(any())).thenReturn("{\"transactions\":[]}");
                when(importSessionRepository.save(any(ImportSession.class))).thenReturn(testSession);

                // When
                importService.parseFileAsync(1L);

                // Then
                verify(ofxParser).parseFileToResult(any(InputStream.class), anyString());
        }

        @Test
        @DisplayName("Should parse CSV file successfully")
        void shouldParseCsvFileSuccessfully() throws Exception {
                // Given
                testSession.setFileFormat("CSV");
                testSession.setFileName("transactions.csv");
                InputStream mockStream = new ByteArrayInputStream("Date,Payee,Amount\n2023-01-01,Test,10.0".getBytes());

                when(importSessionRepository.findById(1L)).thenReturn(Optional.of(testSession));
                when(fileStorageService.getFileContent(UPLOAD_ID)).thenReturn(mockStream);
                when(csvParser.parseFile(any(InputStream.class), anyString()))
                                .thenReturn(testTransactions);
                when(objectMapper.writeValueAsString(any())).thenReturn("{\"transactions\":[]}");
                when(importSessionRepository.save(any(ImportSession.class))).thenReturn(testSession);

                // When
                importService.parseFileAsync(1L);

                // Then
                verify(csvParser).parseFile(any(InputStream.class), anyString());
        }

        // ========================================
        // Tests for reviewTransactions()
        // ========================================

        @Test
        @DisplayName("Should retrieve transactions for review when session is ready")
        void shouldRetrieveTransactionsForReview() throws Exception {
                // Given
                testSession.setStatus(ImportStatus.PARSED);
                testSession.setMetadata("{\"transactions\":[]}");

                // Mock ObjectMapper to return Map first, then List via convertValue
                Map<String, Object> metadataMap = new HashMap<>();
                metadataMap.put("transactions", testTransactions);
                metadataMap.put("count", testTransactions.size());

                when(importSessionRepository.findById(1L)).thenReturn(Optional.of(testSession));
                when(objectMapper.readValue(anyString(), any(com.fasterxml.jackson.core.type.TypeReference.class)))
                                .thenReturn(metadataMap);
                when(objectMapper.convertValue(any(), any(com.fasterxml.jackson.core.type.TypeReference.class)))
                                .thenReturn(testTransactions);
                when(transactionRepository.findByAccountId(ACCOUNT_ID)).thenReturn(Collections.emptyList());
                when(categoryRepository.findByUserId(USER_ID)).thenReturn(Collections.emptyList());
                when(importSessionRepository.save(any(ImportSession.class))).thenReturn(testSession);

                // When
                List<ImportedTransaction> result = importService.reviewTransactions(1L, USER_ID);

                // Then
                assertThat(result).isNotNull();
                assertThat(result).hasSize(testTransactions.size());

                verify(importSessionRepository).findById(1L);
                verify(objectMapper, times(3)).readValue(anyString(),
                                any(com.fasterxml.jackson.core.type.TypeReference.class));
        }

        @Test
        @DisplayName("Should throw exception when session not ready for review")
        void shouldThrowExceptionWhenSessionNotReadyForReview() {
                // Given
                testSession.setStatus(ImportStatus.PENDING);
                when(importSessionRepository.findById(1L)).thenReturn(Optional.of(testSession));

                // When & Then
                assertThatThrownBy(() -> importService.reviewTransactions(1L, USER_ID))
                                .isInstanceOf(IllegalStateException.class)
                                .hasMessageContaining("Session is not ready for review");
        }

        @Test
        @DisplayName("Should throw exception when user does not own session")
        void shouldThrowExceptionWhenUserDoesNotOwnSession() {
                // Given
                testSession.setUserId(999L); // Different user
                when(importSessionRepository.findById(1L)).thenReturn(Optional.of(testSession));

                // When & Then
                assertThatThrownBy(() -> importService.reviewTransactions(1L, USER_ID))
                                .isInstanceOf(IllegalArgumentException.class)
                                .hasMessageContaining("User does not have access");
        }

        // ========================================
        // Tests for confirmImport()
        // ========================================

        @Test
        @DisplayName("Should confirm import successfully")
        void shouldConfirmImportSuccessfully() throws Exception {
                // Given
                testSession.setStatus(ImportStatus.PARSED);
                testSession.setMetadata("{\"transactions\":[]}");
                Map<String, Long> categoryMappings = new HashMap<>();
                categoryMappings.put("Groceries", 10L);

                // Mock ObjectMapper to return Map first, then List via convertValue
                Map<String, Object> metadataMap = new HashMap<>();
                metadataMap.put("transactions", testTransactions);
                metadataMap.put("count", testTransactions.size());

                when(importSessionRepository.findById(1L)).thenReturn(Optional.of(testSession));
                when(accountRepository.findByIdAndUserId(ACCOUNT_ID, USER_ID))
                                .thenReturn(Optional.of(testAccount));
                when(objectMapper.readValue(anyString(), any(com.fasterxml.jackson.core.type.TypeReference.class)))
                                .thenReturn(metadataMap);
                when(objectMapper.convertValue(any(), any(com.fasterxml.jackson.core.type.TypeReference.class)))
                                .thenReturn(testTransactions);
                when(transactionRepository.save(any(Transaction.class)))
                                .thenAnswer(invocation -> invocation.getArgument(0));
                when(importSessionRepository.save(any(ImportSession.class))).thenReturn(testSession);

                // When
                ImportSession result = importService.confirmImport(1L, USER_ID, ACCOUNT_ID, categoryMappings, true,
                                null);

                // Then
                assertThat(result).isNotNull();
                verify(importSessionRepository, atLeast(2)).save(any(ImportSession.class));
                verify(transactionRepository, times(testTransactions.size())).save(any(Transaction.class));
        }

        @Test
        @DisplayName("Should auto-create account when account ID is null")
        void shouldAutoCreateAccountWhenAccountIdIsNull() throws Exception {
                // Given
                testSession.setStatus(ImportStatus.PARSED);
                testSession.setAccountId(null);
                testSession.setSuggestedAccountName("My Bank Account");
                testSession.setMetadata("{\"transactions\":[]}");

                org.openfinance.dto.AccountResponse createdAccount = org.openfinance.dto.AccountResponse.builder()
                                .id(99L)
                                .build();

                Map<String, Object> metadataMap = new HashMap<>();
                metadataMap.put("transactions", testTransactions);
                metadataMap.put("count", testTransactions.size());

                when(importSessionRepository.findById(1L)).thenReturn(Optional.of(testSession));
                when(accountService.createAccount(eq(USER_ID), any(), any()))
                                .thenReturn(createdAccount);
                when(accountRepository.findByIdAndUserId(99L, USER_ID))
                                .thenReturn(Optional.of(testAccount));
                when(objectMapper.readValue(anyString(), any(com.fasterxml.jackson.core.type.TypeReference.class)))
                                .thenReturn(metadataMap);
                when(objectMapper.convertValue(any(), any(com.fasterxml.jackson.core.type.TypeReference.class)))
                                .thenReturn(testTransactions);
                when(transactionRepository.save(any(Transaction.class)))
                                .thenAnswer(invocation -> invocation.getArgument(0));
                when(importSessionRepository.save(any(ImportSession.class))).thenReturn(testSession);

                // When
                ImportSession result = importService.confirmImport(1L, USER_ID, null, new HashMap<>(), true, null);

                // Then
                assertThat(result).isNotNull();
                verify(accountService).createAccount(eq(USER_ID), any(), any());
        }

        @Test
        @DisplayName("Should throw exception when session cannot be confirmed")
        void shouldThrowExceptionWhenSessionCannotBeConfirmed() {
                // Given
                testSession.setStatus(ImportStatus.COMPLETED);
                when(importSessionRepository.findById(1L)).thenReturn(Optional.of(testSession));

                // When & Then
                assertThatThrownBy(
                                () -> importService.confirmImport(1L, USER_ID, ACCOUNT_ID, new HashMap<>(), true, null))
                                .isInstanceOf(IllegalStateException.class)
                                .hasMessageContaining("Session cannot be confirmed");
        }

        // ========================================
        // Tests for cancelImport()
        // ========================================

        @Test
        @DisplayName("Should handle IOException during parsing")
        void shouldHandleIOExceptionDuringParsing() throws IOException {
                // Given
                testSession.setStatus(ImportStatus.PARSING);
                when(importSessionRepository.findById(1L)).thenReturn(Optional.of(testSession));
                when(fileStorageService.getFileContent(UPLOAD_ID)).thenThrow(new IOException("File read error"));

                // When
                importService.parseFileAsync(1L);

                // Then - verify status was set to FAILED (called twice: once for initial
                // PARSING, once for FAILED)
                verify(importSessionRepository, times(2))
                                .save(argThat(session -> session.getStatus() == ImportStatus.FAILED ||
                                                session.getStatus() == ImportStatus.PARSING));
        }

        @Test
        @DisplayName("Should throw exception when session cannot be cancelled")
        void shouldThrowExceptionWhenSessionCannotBeCancelled() {
                // Given
                testSession.setStatus(ImportStatus.COMPLETED);
                when(importSessionRepository.findById(1L)).thenReturn(Optional.of(testSession));

                // When & Then
                assertThatThrownBy(() -> importService.cancelImport(1L, USER_ID))
                                .isInstanceOf(IllegalStateException.class)
                                .hasMessageContaining("Session cannot be cancelled");
        }

        // ========================================
        // Tests for getSession() and getUserSessions()
        // ========================================

        @Test
        @DisplayName("Should get session by ID")
        void shouldGetSessionById() {
                // Given
                when(importSessionRepository.findById(1L)).thenReturn(Optional.of(testSession));

                // When
                ImportSession result = importService.getSession(1L, USER_ID);

                // Then
                assertThat(result).isNotNull();
                assertThat(result.getId()).isEqualTo(1L);
                verify(importSessionRepository).findById(1L);
        }

        @Test
        @DisplayName("Should get all user sessions")
        void shouldGetAllUserSessions() {
                // Given
                List<ImportSession> sessions = Arrays.asList(testSession);
                when(importSessionRepository.findByUserIdOrderByCreatedAtDesc(USER_ID))
                                .thenReturn(sessions);

                // When
                List<ImportSession> result = importService.getUserSessions(USER_ID);

                // Then
                assertThat(result).isNotNull();
                assertThat(result).hasSize(1);
                assertThat(result.get(0).getId()).isEqualTo(1L);
                verify(importSessionRepository).findByUserIdOrderByCreatedAtDesc(USER_ID);
        }

        // ========================================
        // Helper Methods
        // ========================================

        private List<ImportedTransaction> createTestTransactions() {
                List<ImportedTransaction> transactions = new ArrayList<>();

                ImportedTransaction tx1 = ImportedTransaction.builder()
                                .transactionDate(LocalDate.now())
                                .payee("Test Merchant")
                                .amount(new BigDecimal("-50.00"))
                                .memo("Test purchase")
                                .category("Groceries")
                                .potentialDuplicate(false)
                                .validationErrors(new ArrayList<>())
                                .build();

                ImportedTransaction tx2 = ImportedTransaction.builder()
                                .transactionDate(LocalDate.now().minusDays(1))
                                .payee("Salary Inc")
                                .amount(new BigDecimal("2000.00"))
                                .memo("Monthly salary")
                                .category("Income")
                                .potentialDuplicate(false)
                                .validationErrors(new ArrayList<>())
                                .build();

                transactions.add(tx1);
                transactions.add(tx2);

                return transactions;
        }

        // ========================================
        // Tests for detectDuplicates() — all tiers
        // ========================================

        /**
         * Helper: build a minimal persisted Transaction for use as an "existing"
         * record.
         */
        private Transaction buildExistingTransaction(LocalDate date, BigDecimal amount,
                        String description, String externalReference) {
                return Transaction.builder()
                                .id(999L)
                                .userId(USER_ID)
                                .accountId(ACCOUNT_ID)
                                .date(date)
                                .amount(amount)
                                .currency("USD")
                                .type(TransactionType.EXPENSE)
                                .description(description)
                                .externalReference(externalReference)
                                .isDeleted(false)
                                .build();
        }

        /**
         * Helper: drive detectDuplicates via reviewTransactions() so we go through the
         * real service method without needing reflection.
         *
         * <p>
         * Uses {@code "QIF"} as the file format by default. Pass {@code "OFX"} to
         * activate Tier 0 (intra-session reference) and Tier 1 (DB exact-reference)
         * duplicate checks.
         * </p>
         */
        private List<ImportedTransaction> runReview(List<ImportedTransaction> parsed,
                        List<Transaction> existing) throws Exception {
                return runReview(parsed, existing, "QIF");
        }

        /**
         * Helper: drive detectDuplicates via reviewTransactions() with an explicit
         * file format so tests can activate OFX-only Tier 0/1 reference checks.
         */
        private List<ImportedTransaction> runReview(List<ImportedTransaction> parsed,
                        List<Transaction> existing, String fileFormat) throws Exception {
                testSession.setStatus(ImportStatus.PARSED);
                testSession.setFileFormat(fileFormat);
                testSession.setMetadata("{\"tx\":[]}");

                Map<String, Object> metadataMap = new HashMap<>();
                metadataMap.put("transactions", parsed);

                when(importSessionRepository.findById(1L)).thenReturn(Optional.of(testSession));
                when(objectMapper.readValue(anyString(), any(com.fasterxml.jackson.core.type.TypeReference.class)))
                                .thenReturn(metadataMap);
                when(objectMapper.convertValue(any(), any(com.fasterxml.jackson.core.type.TypeReference.class)))
                                .thenReturn(parsed);
                when(transactionRepository.findByAccountId(ACCOUNT_ID)).thenReturn(existing);
                when(categoryRepository.findByUserId(USER_ID)).thenReturn(Collections.emptyList());
                when(importSessionRepository.save(any(ImportSession.class))).thenReturn(testSession);

                return importService.reviewTransactions(1L, USER_ID);
        }

        @Test
        @DisplayName("Tier 2 — should flag fuzzy duplicate when date/amount/payee all match")
        void shouldFlagFuzzyDuplicate() throws Exception {
                // Given: existing tx with same payee/amount/date
                Transaction existing = buildExistingTransaction(
                                LocalDate.now(), new BigDecimal("50.0000"), "Test Merchant", null);

                ImportedTransaction incoming = ImportedTransaction.builder()
                                .transactionDate(LocalDate.now())
                                .payee("Test Merchant")
                                .amount(new BigDecimal("-50.00"))
                                .validationErrors(new ArrayList<>())
                                .build();

                // When
                List<ImportedTransaction> result = runReview(List.of(incoming), List.of(existing));

                // Then
                assertThat(result).hasSize(1);
                ImportedTransaction reviewed = result.get(0);
                assertThat(reviewed.isPotentialDuplicate()).isTrue();
                assertThat(reviewed.getValidationErrors())
                                .anyMatch(e -> e.startsWith("DUPLICATE:"));
        }

        @Test
        @DisplayName("Tier 2 — should NOT flag when amount differs despite same payee/date")
        void shouldNotFlagWhenAmountDiffers() throws Exception {
                // Given: existing tx with different amount
                Transaction existing = buildExistingTransaction(
                                LocalDate.now(), new BigDecimal("75.00"), "Test Merchant", null);

                ImportedTransaction incoming = ImportedTransaction.builder()
                                .transactionDate(LocalDate.now())
                                .payee("Test Merchant")
                                .amount(new BigDecimal("-50.00"))
                                .validationErrors(new ArrayList<>())
                                .build();

                // When
                List<ImportedTransaction> result = runReview(List.of(incoming), List.of(existing));

                // Then
                assertThat(result.get(0).isPotentialDuplicate()).isFalse();
                assertThat(result.get(0).getValidationErrors())
                                .noneMatch(e -> e.startsWith("DUPLICATE:"));
        }

        @Test
        @DisplayName("Tier 2 — BigDecimal scale: 50.00 and 50.0000 should match")
        void shouldMatchAmountsWithDifferentScales() throws Exception {
                // Given: existing stored with scale 4 (as Hibernate writes it)
                Transaction existing = buildExistingTransaction(
                                LocalDate.now(), new BigDecimal("50.0000"), "Grocery Store", null);

                ImportedTransaction incoming = ImportedTransaction.builder()
                                .transactionDate(LocalDate.now())
                                .payee("Grocery Store")
                                .amount(new BigDecimal("-50.00")) // scale 2 from OFX parser
                                .validationErrors(new ArrayList<>())
                                .build();

                // When
                List<ImportedTransaction> result = runReview(List.of(incoming), List.of(existing));

                // Then: should flag despite scale mismatch (compareTo, not equals)
                assertThat(result.get(0).isPotentialDuplicate()).isTrue();
        }

        @Test
        @DisplayName("Tier 1 — exact externalReference match should be authoritative (OFX only)")
        void shouldFlagExactReferenceMatchAsDuplicate() throws Exception {
                // Given: existing tx with a stored externalReference (FITID)
                Transaction existing = buildExistingTransaction(
                                LocalDate.now().minusMonths(1), new BigDecimal("99.99"),
                                "Old payee name", "FITID-20250115-001");

                // Incoming OFX tx shares the same FITID but has slightly different payee/date
                ImportedTransaction incoming = ImportedTransaction.builder()
                                .transactionDate(LocalDate.now()) // different date
                                .payee("Completely Different Payee") // different payee
                                .amount(new BigDecimal("-99.99"))
                                .referenceNumber("FITID-20250115-001") // same FITID
                                .validationErrors(new ArrayList<>())
                                .build();

                // When — use "OFX" format to activate Tier 1 reference matching
                List<ImportedTransaction> result = runReview(List.of(incoming), List.of(existing), "OFX");

                // Then: Tier 1 match — flagged even though fuzzy would have missed it
                assertThat(result.get(0).isPotentialDuplicate()).isTrue();
                assertThat(result.get(0).getValidationErrors())
                                .anyMatch(e -> e.startsWith("DUPLICATE:") && e.contains("FITID-20250115-001"));
        }

        @Test
        @DisplayName("Tier 0 — two OFX transactions in same batch sharing a referenceNumber should flag second as duplicate")
        void shouldFlagIntraSessionDuplicate() throws Exception {
                // Given: no existing DB transactions, but two imported OFX txs share a FITID
                ImportedTransaction first = ImportedTransaction.builder()
                                .transactionDate(LocalDate.now())
                                .payee("Merchant A")
                                .amount(new BigDecimal("-25.00"))
                                .referenceNumber("FITID-SAME-001")
                                .validationErrors(new ArrayList<>())
                                .build();

                ImportedTransaction second = ImportedTransaction.builder()
                                .transactionDate(LocalDate.now())
                                .payee("Merchant A")
                                .amount(new BigDecimal("-25.00"))
                                .referenceNumber("FITID-SAME-001") // same reference
                                .validationErrors(new ArrayList<>())
                                .build();

                // When — use "OFX" format to activate Tier 0 intra-session reference check
                List<ImportedTransaction> result = runReview(List.of(first, second), Collections.emptyList(), "OFX");

                // Then: first should be clean, second should be flagged
                assertThat(result.get(0).isPotentialDuplicate()).isFalse();
                assertThat(result.get(1).isPotentialDuplicate()).isTrue();
                assertThat(result.get(1).getValidationErrors())
                                .anyMatch(e -> e.startsWith("DUPLICATE:") && e.contains("FITID-SAME-001"));
        }

        @Test
        @DisplayName("OFX Tier 1+2 — different FITID but same payee/date/amount falls through to Tier 2 and is flagged once")
        void shouldNotFlagUniqueReferenceNumbers() throws Exception {
                // Given: existing OFX tx with a different FITID but same payee/date/amount
                Transaction existing = buildExistingTransaction(
                                LocalDate.now(), new BigDecimal("50.00"), "Same Payee", "FITID-OLD-001");

                ImportedTransaction incoming = ImportedTransaction.builder()
                                .transactionDate(LocalDate.now())
                                .payee("Same Payee")
                                .amount(new BigDecimal("-50.00"))
                                .referenceNumber("FITID-NEW-999") // different FITID → Tier 1 misses
                                .validationErrors(new ArrayList<>())
                                .build();

                // When — OFX format: Tier 1 finds no match, Tier 2 fires on payee+date+amount
                List<ImportedTransaction> result = runReview(List.of(incoming), List.of(existing), "OFX");

                // Then: Tier 2 catches the fuzzy match; exactly one DUPLICATE message
                assertThat(result.get(0).isPotentialDuplicate()).isTrue();
                assertThat(result.get(0).getValidationErrors()
                                .stream().filter(e -> e.startsWith("DUPLICATE:")).count())
                                .isEqualTo(1); // exactly one DUPLICATE message (not double-flagged)
        }

        // ========================================
        // Format-gating tests — Tier 0/1 must NOT fire for QIF/CSV
        // ========================================

        @Test
        @DisplayName("QIF — shared check number should NOT trigger Tier 0 intra-session duplicate")
        void qifSharedCheckNumberShouldNotTriggerTier0() throws Exception {
                // Given: two QIF transactions with the same check number (N field) but
                // genuinely different payee/date/amount — not duplicates.
                ImportedTransaction first = ImportedTransaction.builder()
                                .transactionDate(LocalDate.now().minusDays(10))
                                .payee("Hardware Store")
                                .amount(new BigDecimal("-200.00"))
                                .referenceNumber("1042") // cheque #1042 from first cheque book
                                .validationErrors(new ArrayList<>())
                                .build();

                ImportedTransaction second = ImportedTransaction.builder()
                                .transactionDate(LocalDate.now())
                                .payee("Grocery Store") // completely different payee/amount
                                .amount(new BigDecimal("-35.50"))
                                .referenceNumber("1042") // coincidentally same check number
                                .validationErrors(new ArrayList<>())
                                .build();

                // When — QIF format: Tier 0/1 must be skipped; Tier 2 won't match (different
                // payee+amount)
                List<ImportedTransaction> result = runReview(List.of(first, second), Collections.emptyList(), "QIF");

                // Then: neither transaction should be flagged as a duplicate
                assertThat(result.get(0).isPotentialDuplicate()).isFalse();
                assertThat(result.get(1).isPotentialDuplicate()).isFalse();
                assertThat(result.get(1).getValidationErrors())
                                .noneMatch(e -> e.startsWith("DUPLICATE:"));
        }

        @Test
        @DisplayName("QIF — check number matching existing externalReference should NOT trigger Tier 1")
        void qifCheckNumberShouldNotTriggerTier1() throws Exception {
                // Given: existing tx has externalReference = "1042" (an old OFX FITID that
                // happened to look like a check number), and incoming QIF tx has N = "1042".
                // These are NOT the same transaction — Tier 1 must not fire for QIF.
                Transaction existing = buildExistingTransaction(
                                LocalDate.now().minusMonths(3), new BigDecimal("999.00"),
                                "Old Vendor", "1042");

                ImportedTransaction incoming = ImportedTransaction.builder()
                                .transactionDate(LocalDate.now())
                                .payee("New Vendor") // completely different
                                .amount(new BigDecimal("-55.00")) // completely different
                                .referenceNumber("1042") // coincidental match with externalReference
                                .validationErrors(new ArrayList<>())
                                .build();

                // When — QIF format: Tier 1 must NOT fire; Tier 2 also won't match
                // (payee/amount differ)
                List<ImportedTransaction> result = runReview(List.of(incoming), List.of(existing), "QIF");

                // Then: should NOT be flagged (no Tier 1 match, no Tier 2 fuzzy match)
                assertThat(result.get(0).isPotentialDuplicate()).isFalse();
                assertThat(result.get(0).getValidationErrors())
                                .noneMatch(e -> e.startsWith("DUPLICATE:"));
        }

        @Test
        @DisplayName("QIF — actual fuzzy duplicate should still be caught by Tier 2 even when check number present")
        void qifFuzzyDuplicateShouldBeCaughtByTier2WhenCheckNumberPresent() throws Exception {
                // Given: QIF transaction with a check number that also matches an existing
                // transaction via fuzzy logic (same payee/date/amount). Tier 1 is disabled
                // for QIF but Tier 2 must still run and catch the real duplicate.
                Transaction existing = buildExistingTransaction(
                                LocalDate.now(), new BigDecimal("75.00"),
                                "Coffee Shop", null);

                ImportedTransaction incoming = ImportedTransaction.builder()
                                .transactionDate(LocalDate.now())
                                .payee("Coffee Shop")
                                .amount(new BigDecimal("-75.00"))
                                .referenceNumber("1099") // QIF check number present
                                .validationErrors(new ArrayList<>())
                                .build();

                // When — QIF format: Tier 1 skipped, but Tier 2 must fire and catch it
                List<ImportedTransaction> result = runReview(List.of(incoming), List.of(existing), "QIF");

                // Then: Tier 2 fuzzy match catches the duplicate even though a check number
                // exists
                assertThat(result.get(0).isPotentialDuplicate()).isTrue();
                assertThat(result.get(0).getValidationErrors())
                                .anyMatch(e -> e.startsWith("DUPLICATE:"));
        }

        @Test
        @DisplayName("Tier 2 — date ±1 day should still be flagged as duplicate")
        void shouldFlagDuplicateWithOneDayDateDifference() throws Exception {
                // Given: existing tx one day earlier
                Transaction existing = buildExistingTransaction(
                                LocalDate.now().minusDays(1), new BigDecimal("100.00"), "Supermarket", null);

                ImportedTransaction incoming = ImportedTransaction.builder()
                                .transactionDate(LocalDate.now())
                                .payee("Supermarket")
                                .amount(new BigDecimal("-100.00"))
                                .validationErrors(new ArrayList<>())
                                .build();

                // When
                List<ImportedTransaction> result = runReview(List.of(incoming), List.of(existing));

                // Then: within ±1 day window → flagged
                assertThat(result.get(0).isPotentialDuplicate()).isTrue();
        }

        @Test
        @DisplayName("Tier 2 — date 2 days apart should NOT be flagged as duplicate")
        void shouldNotFlagWhenDateTwoDaysApart() throws Exception {
                // Given: existing tx two days away (outside the window)
                Transaction existing = buildExistingTransaction(
                                LocalDate.now().minusDays(2), new BigDecimal("100.00"), "Supermarket", null);

                ImportedTransaction incoming = ImportedTransaction.builder()
                                .transactionDate(LocalDate.now())
                                .payee("Supermarket")
                                .amount(new BigDecimal("-100.00"))
                                .validationErrors(new ArrayList<>())
                                .build();

                // When
                List<ImportedTransaction> result = runReview(List.of(incoming), List.of(existing));

                // Then: outside ±1 day window → not flagged
                assertThat(result.get(0).isPotentialDuplicate()).isFalse();
        }
}
