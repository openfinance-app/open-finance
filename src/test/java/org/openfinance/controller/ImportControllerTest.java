package org.openfinance.controller;

import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.openfinance.dto.ImportConfirmRequest;
import org.openfinance.dto.ImportProcessRequest;
import org.openfinance.dto.ImportedTransaction;
import org.openfinance.entity.ImportSession;
import org.openfinance.entity.ImportSession.ImportStatus;
import org.openfinance.entity.User;
import org.openfinance.exception.ResourceNotFoundException;
import org.openfinance.service.ImportService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Unit tests for ImportController.
 *
 * <p>
 * Tests all 6 endpoints: - POST /api/v1/import/process - GET
 * /api/v1/import/sessions/{id} - GET
 * /api/v1/import/sessions/{id}/review - POST
 * /api/v1/import/sessions/{id}/confirm - POST
 * /api/v1/import/sessions/{id}/cancel - GET /api/v1/import/sessions
 *
 * <p>
 * Uses @WebMvcTest for controller-only testing with mocked ImportService.
 */
@WebMvcTest(ImportController.class)
@org.springframework.context.annotation.Import({
                org.openfinance.config.RateLimitConfig.class,
                org.openfinance.config.RateLimitInterceptor.class
})
@DisplayName("ImportController Unit Tests")
class ImportControllerTest {

        @Autowired
        private MockMvc mockMvc;

        @Autowired
        private ObjectMapper objectMapper;

        @MockBean
        private ImportService importService;

        @MockBean
        private org.openfinance.service.JwtService jwtService;

        @MockBean
        private org.openfinance.repository.UserRepository userRepository;

        @MockBean
        private org.openfinance.security.EncryptionKeyCache encryptionKeyCache;

        private static final Long USER_ID = 123L;
        private static final String UPLOAD_ID = "550e8400-e29b-41d4-a716-446655440000";
        private static final Long SESSION_ID = 42L;
        private static final Long ACCOUNT_ID = 1L;

        private static final String TEST_ENCRYPTION_SESSION = "test-encryption-session";

        // ========================================
        // Setup Methods
        // ========================================

        @BeforeEach
        void setUp() {
                // Mock user ID extraction is handled by createAuthentication() helper
                javax.crypto.SecretKey key = new javax.crypto.spec.SecretKeySpec(new byte[32], "AES");
                when(encryptionKeyCache.getKeyBySessionToken(TEST_ENCRYPTION_SESSION))
                                .thenReturn(Optional.of(key));
                when(encryptionKeyCache.getUserIdBySessionToken(TEST_ENCRYPTION_SESSION))
                                .thenReturn(Optional.of(USER_ID));
        }

        /** Creates an authentication object with a User principal for testing. */
        private Authentication createAuthentication(Long userId) {
                User user = User.builder()
                                .id(userId)
                                .username("alice")
                                .email("alice@example.com")
                                .passwordHash("hashed")
                                .masterPasswordSalt("salt")
                                .baseCurrency("USD")
                                .build();

                return new UsernamePasswordAuthenticationToken(
                                user, null, Collections.singletonList(new SimpleGrantedAuthority("USER")));
        }

        // ========================================
        // POST /api/v1/import/process Tests
        // ========================================

        @Test
        @DisplayName("POST /process - should process import successfully when valid request")
        void shouldProcessImportSuccessfullyWhenValidRequest() throws Exception {
                // Given
                ImportProcessRequest request = ImportProcessRequest.builder().uploadId(UPLOAD_ID).accountId(ACCOUNT_ID)
                                .build();

                ImportSession expectedSession = createImportSession(SESSION_ID, ImportStatus.PARSED, 50);

                when(importService.startImport(eq(UPLOAD_ID), eq(USER_ID), eq(ACCOUNT_ID), any()))
                                .thenReturn(expectedSession);

                // When & Then
                mockMvc.perform(
                                post("/api/v1/import/process")
                                                .with(csrf())
                                                .with(authentication(createAuthentication(USER_ID)))
                                                .contentType(MediaType.APPLICATION_JSON)
                                                .content(objectMapper.writeValueAsString(request)))
                                .andDo(print())
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.id").value(SESSION_ID))
                                .andExpect(jsonPath("$.uploadId").value(UPLOAD_ID))
                                .andExpect(jsonPath("$.userId").value(USER_ID))
                                .andExpect(jsonPath("$.status").value("PARSED"))
                                .andExpect(jsonPath("$.totalTransactions").value(50))
                                .andExpect(jsonPath("$.importedCount").value(0))
                                .andExpect(jsonPath("$.skippedCount").value(0))
                                .andExpect(jsonPath("$.errorCount").value(0));

                verify(importService).startImport(eq(UPLOAD_ID), eq(USER_ID), eq(ACCOUNT_ID), any());
        }

        @Test
        @DisplayName("POST /process - should process import without accountId when not provided")
        void shouldProcessImportWithoutAccountIdWhenNotProvided() throws Exception {
                // Given
                ImportProcessRequest request = ImportProcessRequest.builder().uploadId(UPLOAD_ID).accountId(null)
                                .build();

                ImportSession expectedSession = createImportSession(SESSION_ID, ImportStatus.PARSED, 25);
                expectedSession.setAccountId(null);

                when(importService.startImport(eq(UPLOAD_ID), eq(USER_ID), isNull(), any()))
                                .thenReturn(expectedSession);

                // When & Then
                mockMvc.perform(
                                post("/api/v1/import/process")
                                                .with(csrf())
                                                .with(authentication(createAuthentication(USER_ID)))
                                                .contentType(MediaType.APPLICATION_JSON)
                                                .content(objectMapper.writeValueAsString(request)))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.id").value(SESSION_ID))
                                .andExpect(jsonPath("$.accountId").doesNotExist());

                verify(importService).startImport(eq(UPLOAD_ID), eq(USER_ID), isNull(), any());
        }

        @Test
        @DisplayName("POST /process - should return 400 when uploadId is missing")
        void shouldReturn400WhenUploadIdIsMissing() throws Exception {
                // Given
                ImportProcessRequest request = ImportProcessRequest.builder().uploadId("").accountId(ACCOUNT_ID)
                                .build();

                // When & Then
                mockMvc.perform(
                                post("/api/v1/import/process")
                                                .with(csrf())
                                                .with(authentication(createAuthentication(USER_ID)))
                                                .contentType(MediaType.APPLICATION_JSON)
                                                .content(objectMapper.writeValueAsString(request)))
                                .andExpect(status().isBadRequest());

                verify(importService, never()).startImport(anyString(), anyLong(), anyLong(), any());
        }

        @Test
        @DisplayName("POST /process - should return 404 when upload file not found")
        void shouldReturn404WhenUploadFileNotFound() throws Exception {
                // Given
                ImportProcessRequest request = ImportProcessRequest.builder().uploadId(UPLOAD_ID).accountId(ACCOUNT_ID)
                                .build();

                when(importService.startImport(eq(UPLOAD_ID), eq(USER_ID), eq(ACCOUNT_ID), any()))
                                .thenThrow(new ResourceNotFoundException("Uploaded file not found: " + UPLOAD_ID));

                // When & Then
                mockMvc.perform(
                                post("/api/v1/import/process")
                                                .with(csrf())
                                                .with(authentication(createAuthentication(USER_ID)))
                                                .contentType(MediaType.APPLICATION_JSON)
                                                .content(objectMapper.writeValueAsString(request)))
                                .andExpect(status().isNotFound());

                verify(importService).startImport(eq(UPLOAD_ID), eq(USER_ID), eq(ACCOUNT_ID), any());
        }

        @Test
        @DisplayName("POST /process - should return 404 when account not found")
        void shouldReturn404WhenAccountNotFound() throws Exception {
                // Given
                ImportProcessRequest request = ImportProcessRequest.builder().uploadId(UPLOAD_ID).accountId(999L)
                                .build();

                when(importService.startImport(eq(UPLOAD_ID), eq(USER_ID), eq(999L), any()))
                                .thenThrow(new ResourceNotFoundException("Account not found: 999"));

                // When & Then
                mockMvc.perform(
                                post("/api/v1/import/process")
                                                .with(csrf())
                                                .with(authentication(createAuthentication(USER_ID)))
                                                .contentType(MediaType.APPLICATION_JSON)
                                                .content(objectMapper.writeValueAsString(request)))
                                .andExpect(status().isNotFound());

                verify(importService).startImport(eq(UPLOAD_ID), eq(USER_ID), eq(999L), any());
        }

        @Test
        @DisplayName("POST /process - should return 400 when account is inactive")
        void shouldReturn400WhenAccountIsInactive() throws Exception {
                // Given
                ImportProcessRequest request = ImportProcessRequest.builder().uploadId(UPLOAD_ID).accountId(ACCOUNT_ID)
                                .build();

                when(importService.startImport(eq(UPLOAD_ID), eq(USER_ID), eq(ACCOUNT_ID), any()))
                                .thenThrow(
                                                new IllegalArgumentException(
                                                                "Cannot import to inactive account: " + ACCOUNT_ID));

                // When & Then
                mockMvc.perform(
                                post("/api/v1/import/process")
                                                .with(csrf())
                                                .with(authentication(createAuthentication(USER_ID)))
                                                .contentType(MediaType.APPLICATION_JSON)
                                                .content(objectMapper.writeValueAsString(request)))
                                .andExpect(status().isBadRequest());

                verify(importService).startImport(eq(UPLOAD_ID), eq(USER_ID), eq(ACCOUNT_ID), any());
        }

        @Test
        @DisplayName("POST /process - should return 401 when not authenticated")
        void shouldReturn401WhenNotAuthenticatedForProcessImport() throws Exception {
                // Given
                ImportProcessRequest request = ImportProcessRequest.builder().uploadId(UPLOAD_ID).accountId(ACCOUNT_ID)
                                .build();

                // When & Then
                mockMvc.perform(
                                post("/api/v1/import/process")
                                                .with(csrf())
                                                .contentType(MediaType.APPLICATION_JSON)
                                                .content(objectMapper.writeValueAsString(request)))
                                .andExpect(status().isUnauthorized());

                verify(importService, never()).startImport(anyString(), anyLong(), anyLong(), any());
        }

        // ========================================
        // GET /api/v1/import/sessions/{id} Tests
        // ========================================

        @Test
        @DisplayName("GET /sessions/{id} - should get session successfully when session exists")
        void shouldGetSessionSuccessfullyWhenSessionExists() throws Exception {
                // Given
                ImportSession expectedSession = createImportSession(SESSION_ID, ImportStatus.PARSED, 50);

                when(importService.getSession(SESSION_ID, USER_ID)).thenReturn(expectedSession);

                // When & Then
                mockMvc.perform(
                                get("/api/v1/import/sessions/{id}", SESSION_ID)
                                                .with(authentication(createAuthentication(USER_ID))))
                                .andDo(print())
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.id").value(SESSION_ID))
                                .andExpect(jsonPath("$.userId").value(USER_ID))
                                .andExpect(jsonPath("$.status").value("PARSED"))
                                .andExpect(jsonPath("$.fileName").value("transactions.qif"));

                verify(importService).getSession(SESSION_ID, USER_ID);
        }

        @Test
        @DisplayName("GET /sessions/{id} - should return 404 when session not found")
        void shouldReturn404WhenSessionNotFound() throws Exception {
                // Given
                when(importService.getSession(999L, USER_ID))
                                .thenThrow(new ResourceNotFoundException("Import session not found: 999"));

                // When & Then
                mockMvc.perform(
                                get("/api/v1/import/sessions/{id}", 999L)
                                                .with(authentication(createAuthentication(USER_ID))))
                                .andExpect(status().isNotFound());

                verify(importService).getSession(999L, USER_ID);
        }

        @Test
        @DisplayName("GET /sessions/{id} - should return 403 when session belongs to different user")
        void shouldReturn403WhenSessionBelongsToDifferentUser() throws Exception {
                // Given
                when(importService.getSession(SESSION_ID, USER_ID))
                                .thenThrow(new SecurityException("Access denied"));

                // When & Then
                mockMvc.perform(
                                get("/api/v1/import/sessions/{id}", SESSION_ID)
                                                .with(authentication(createAuthentication(USER_ID))))
                                .andExpect(status().isForbidden());

                verify(importService).getSession(SESSION_ID, USER_ID);
        }

        @Test
        @DisplayName("GET /sessions/{id} - should return 401 when not authenticated")
        void shouldReturn401WhenNotAuthenticatedForGetSession() throws Exception {
                // When & Then
                mockMvc.perform(get("/api/v1/import/sessions/{id}", SESSION_ID))
                                .andExpect(status().isUnauthorized());

                verify(importService, never()).getSession(anyLong(), anyLong());
        }

        // ========================================
        // GET /api/v1/import/sessions/{id}/review Tests
        // ========================================

        @Test
        @DisplayName("GET /sessions/{id}/review - should review transactions successfully when session is ready")
        void shouldReviewTransactionsSuccessfullyWhenSessionIsReady() throws Exception {
                // Given
                List<ImportedTransaction> transactions = Arrays.asList(
                                createImportedTransaction("Walmart", new BigDecimal("-45.67"), "Groceries"),
                                createImportedTransaction(
                                                "Employer Inc", new BigDecimal("2500.00"), "Salary"));

                when(importService.reviewTransactions(SESSION_ID, USER_ID)).thenReturn(transactions);

                // When & Then
                mockMvc.perform(
                                get("/api/v1/import/sessions/{id}/review", SESSION_ID)
                                                .with(authentication(createAuthentication(USER_ID))))
                                .andDo(print())
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$").isArray())
                                .andExpect(jsonPath("$", hasSize(2)))
                                .andExpect(jsonPath("$[0].payee").value("Walmart"))
                                .andExpect(jsonPath("$[0].amount").value(-45.67))
                                .andExpect(jsonPath("$[0].category").value("Groceries"))
                                .andExpect(jsonPath("$[1].payee").value("Employer Inc"))
                                .andExpect(jsonPath("$[1].amount").value(2500.00))
                                .andExpect(jsonPath("$[1].category").value("Salary"));

                verify(importService).reviewTransactions(SESSION_ID, USER_ID);
        }

        @Test
        @DisplayName("GET /sessions/{id}/review - should include validation errors and duplicate flags")
        void shouldIncludeValidationErrorsAndDuplicateFlags() throws Exception {
                // Given
                ImportedTransaction transaction = createImportedTransaction("Store", new BigDecimal("-100.00"),
                                "Shopping");
                transaction.setValidationErrors(
                                Arrays.asList(
                                                "DUPLICATE: Similar transaction found on 2026-01-15 with 90% payee match",
                                                "CATEGORY_SUGGESTION: Matched 'Shopping' with 95% confidence to category ID 15"));
                transaction.setPotentialDuplicate(true);

                when(importService.reviewTransactions(SESSION_ID, USER_ID))
                                .thenReturn(Collections.singletonList(transaction));

                // When & Then
                mockMvc.perform(
                                get("/api/v1/import/sessions/{id}/review", SESSION_ID)
                                                .with(authentication(createAuthentication(USER_ID))))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$[0].validationErrors").isArray())
                                .andExpect(jsonPath("$[0].validationErrors", hasSize(2)))
                                .andExpect(jsonPath("$[0].validationErrors[0]").value(containsString("DUPLICATE:")))
                                .andExpect(
                                                jsonPath("$[0].validationErrors[1]")
                                                                .value(containsString("CATEGORY_SUGGESTION:")))
                                .andExpect(jsonPath("$[0].potentialDuplicate").value(true));

                verify(importService).reviewTransactions(SESSION_ID, USER_ID);
        }

        @Test
        @DisplayName("GET /sessions/{id}/review - should return 404 when session not found")
        void shouldReturn404WhenSessionNotFoundForReview() throws Exception {
                // Given
                when(importService.reviewTransactions(999L, USER_ID))
                                .thenThrow(new ResourceNotFoundException("Import session not found: 999"));

                // When & Then
                mockMvc.perform(
                                get("/api/v1/import/sessions/{id}/review", 999L)
                                                .with(authentication(createAuthentication(USER_ID))))
                                .andExpect(status().isNotFound());

                verify(importService).reviewTransactions(999L, USER_ID);
        }

        @Test
        @DisplayName("GET /sessions/{id}/review - should return 400 when session not ready for review")
        void shouldReturn400WhenSessionNotReadyForReview() throws Exception {
                // Given
                when(importService.reviewTransactions(SESSION_ID, USER_ID))
                                .thenThrow(new IllegalStateException("Session status is PENDING, cannot review"));

                // When & Then
                mockMvc.perform(
                                get("/api/v1/import/sessions/{id}/review", SESSION_ID)
                                                .with(authentication(createAuthentication(USER_ID))))
                                .andExpect(status().isBadRequest());

                verify(importService).reviewTransactions(SESSION_ID, USER_ID);
        }

        @Test
        @DisplayName("GET /sessions/{id}/review - should return 401 when not authenticated")
        void shouldReturn401WhenNotAuthenticatedForReview() throws Exception {
                // When & Then
                mockMvc.perform(get("/api/v1/import/sessions/{id}/review", SESSION_ID))
                                .andExpect(status().isUnauthorized());

                verify(importService, never()).reviewTransactions(anyLong(), anyLong());
        }

        // ========================================
        // POST /api/v1/import/sessions/{id}/confirm Tests
        // ========================================

        @Test
        @DisplayName("POST /sessions/{id}/confirm - should confirm import successfully with valid request")
        void shouldConfirmImportSuccessfullyWithValidRequest() throws Exception {
                // Given
                Map<String, Long> categoryMappings = new HashMap<>();
                categoryMappings.put("Groceries", 15L);
                categoryMappings.put("Salary", 3L);

                ImportConfirmRequest request = ImportConfirmRequest.builder()
                                .accountId(ACCOUNT_ID)
                                .categoryMappings(categoryMappings)
                                .skipDuplicates(true)
                                .build();

                ImportSession importingSession = createImportSession(SESSION_ID, ImportStatus.IMPORTING, 50);

                when(importService.startConfirmImport(any(), any(), any(), any(), anyBoolean()))
                                .thenReturn(importingSession);

                // When & Then
                mockMvc.perform(
                                post("/api/v1/import/sessions/{id}/confirm", SESSION_ID)
                                                .with(csrf())
                                                .with(authentication(createAuthentication(USER_ID)))
                                                .header("X-Encryption-Session", TEST_ENCRYPTION_SESSION)
                                                .contentType(MediaType.APPLICATION_JSON)
                                                .content(objectMapper.writeValueAsString(request)))
                                .andDo(print())
                                .andExpect(status().isAccepted())
                                .andExpect(jsonPath("$.id").value(SESSION_ID))
                                .andExpect(jsonPath("$.status").value("IMPORTING"));

                verify(importService).startConfirmImport(any(), any(), any(), any(), anyBoolean());
        }

        @Test
        @DisplayName("POST /sessions/{id}/confirm - should confirm import without skipping duplicates")
        void shouldConfirmImportWithoutSkippingDuplicates() throws Exception {
                // Given
                ImportConfirmRequest request = ImportConfirmRequest.builder()
                                .accountId(ACCOUNT_ID)
                                .categoryMappings(new HashMap<>())
                                .skipDuplicates(false)
                                .build();

                ImportSession importingSession = createImportSession(SESSION_ID, ImportStatus.IMPORTING, 50);

                when(importService.startConfirmImport(any(), any(), any(), any(), anyBoolean()))
                                .thenReturn(importingSession);

                // When & Then
                mockMvc.perform(
                                post("/api/v1/import/sessions/{id}/confirm", SESSION_ID)
                                                .with(csrf())
                                                .with(authentication(createAuthentication(USER_ID)))
                                                .header("X-Encryption-Session", TEST_ENCRYPTION_SESSION)
                                                .contentType(MediaType.APPLICATION_JSON)
                                                .content(objectMapper.writeValueAsString(request)))
                                .andExpect(status().isAccepted())
                                .andExpect(jsonPath("$.status").value("IMPORTING"));

                verify(importService).startConfirmImport(any(), any(), any(), any(), anyBoolean());
        }

        @Test
        @DisplayName("POST /sessions/{id}/confirm - should succeed when accountId is null (auto-create)")
        void shouldReturn400WhenAccountIdIsMissingForConfirm() throws Exception {
                // accountId is now optional — null triggers auto-create on the backend.
                // Given
                ImportConfirmRequest request = ImportConfirmRequest.builder()
                                .accountId(null)
                                .categoryMappings(new HashMap<>())
                                .skipDuplicates(true)
                                .build();

                ImportSession importingSession = createImportSession(SESSION_ID, ImportStatus.IMPORTING, 10);

                when(importService.startConfirmImport(any(), any(), any(), any(), anyBoolean()))
                                .thenReturn(importingSession);

                // When & Then
                mockMvc.perform(
                                post("/api/v1/import/sessions/{id}/confirm", SESSION_ID)
                                                .with(csrf())
                                                .with(authentication(createAuthentication(USER_ID)))
                                                .header("X-Encryption-Session", TEST_ENCRYPTION_SESSION)
                                                .contentType(MediaType.APPLICATION_JSON)
                                                .content(objectMapper.writeValueAsString(request)))
                                .andExpect(status().isAccepted());

                verify(importService).startConfirmImport(any(), any(), isNull(), any(), anyBoolean());
        }

        @Test
        @DisplayName("POST /sessions/{id}/confirm - should return 404 when session not found")
        void shouldReturn404WhenSessionNotFoundForConfirm() throws Exception {
                // Given
                ImportConfirmRequest request = ImportConfirmRequest.builder()
                                .accountId(ACCOUNT_ID)
                                .categoryMappings(new HashMap<>())
                                .skipDuplicates(true)
                                .build();

                when(importService.startConfirmImport(any(), any(), any(), any(), anyBoolean()))
                                .thenThrow(new ResourceNotFoundException("Import session not found: 999"));

                // When & Then
                mockMvc.perform(
                                post("/api/v1/import/sessions/{id}/confirm", 999L)
                                                .with(csrf())
                                                .with(authentication(createAuthentication(USER_ID)))
                                                .header("X-Encryption-Session", TEST_ENCRYPTION_SESSION)
                                                .contentType(MediaType.APPLICATION_JSON)
                                                .content(objectMapper.writeValueAsString(request)))
                                .andExpect(status().isNotFound());

                verify(importService).startConfirmImport(any(), any(), any(), any(), anyBoolean());
        }

        @Test
        @DisplayName("POST /sessions/{id}/confirm - should return 400 when session cannot be confirmed")
        void shouldReturn400WhenSessionCannotBeConfirmed() throws Exception {
                // Given
                ImportConfirmRequest request = ImportConfirmRequest.builder()
                                .accountId(ACCOUNT_ID)
                                .categoryMappings(new HashMap<>())
                                .skipDuplicates(true)
                                .build();

                when(importService.startConfirmImport(any(), any(), any(), any(), anyBoolean()))
                                .thenThrow(
                                                new IllegalStateException(
                                                                "Session status is COMPLETED, cannot confirm again"));

                // When & Then
                mockMvc.perform(
                                post("/api/v1/import/sessions/{id}/confirm", SESSION_ID)
                                                .with(csrf())
                                                .with(authentication(createAuthentication(USER_ID)))
                                                .header("X-Encryption-Session", TEST_ENCRYPTION_SESSION)
                                                .contentType(MediaType.APPLICATION_JSON)
                                                .content(objectMapper.writeValueAsString(request)))
                                .andExpect(status().isBadRequest());

                verify(importService).startConfirmImport(any(), any(), any(), any(), anyBoolean());
        }

        @Test
        @DisplayName("POST /sessions/{id}/confirm - should return 401 when not authenticated")
        void shouldReturn401WhenNotAuthenticatedForConfirm() throws Exception {
                // Given
                ImportConfirmRequest request = ImportConfirmRequest.builder()
                                .accountId(ACCOUNT_ID)
                                .categoryMappings(new HashMap<>())
                                .skipDuplicates(true)
                                .build();

                // When & Then
                mockMvc.perform(
                                post("/api/v1/import/sessions/{id}/confirm", SESSION_ID)
                                                .with(csrf())
                                                .contentType(MediaType.APPLICATION_JSON)
                                                .content(objectMapper.writeValueAsString(request)))
                                .andExpect(status().isUnauthorized());

                verify(importService, never())
                                .startConfirmImport(any(), any(), any(), any(), anyBoolean());
        }

        // ========================================
        // POST /api/v1/import/sessions/{id}/cancel Tests
        // ========================================

        @Test
        @DisplayName("POST /sessions/{id}/cancel - should cancel import successfully when session is cancellable")
        void shouldCancelImportSuccessfullyWhenSessionIsCancellable() throws Exception {
                // Given
                ImportSession cancelledSession = createImportSession(SESSION_ID, ImportStatus.CANCELLED, 50);
                cancelledSession.setCompletedAt(LocalDateTime.now());

                when(importService.cancelImport(SESSION_ID, USER_ID)).thenReturn(cancelledSession);

                // When & Then
                mockMvc.perform(
                                post("/api/v1/import/sessions/{id}/cancel", SESSION_ID)
                                                .with(csrf())
                                                .with(authentication(createAuthentication(USER_ID))))
                                .andDo(print())
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.id").value(SESSION_ID))
                                .andExpect(jsonPath("$.status").value("CANCELLED"))
                                .andExpect(jsonPath("$.completedAt").exists());

                verify(importService).cancelImport(SESSION_ID, USER_ID);
        }

        @Test
        @DisplayName("POST /sessions/{id}/cancel - should return 404 when session not found")
        void shouldReturn404WhenSessionNotFoundForCancel() throws Exception {
                // Given
                when(importService.cancelImport(999L, USER_ID))
                                .thenThrow(new ResourceNotFoundException("Import session not found: 999"));

                // When & Then
                mockMvc.perform(
                                post("/api/v1/import/sessions/{id}/cancel", 999L)
                                                .with(csrf())
                                                .with(authentication(createAuthentication(USER_ID))))
                                .andExpect(status().isNotFound());

                verify(importService).cancelImport(999L, USER_ID);
        }

        @Test
        @DisplayName("POST /sessions/{id}/cancel - should return 400 when session cannot be cancelled")
        void shouldReturn400WhenSessionCannotBeCancelled() throws Exception {
                // Given
                when(importService.cancelImport(SESSION_ID, USER_ID))
                                .thenThrow(new IllegalStateException("Session status is COMPLETED, cannot cancel"));

                // When & Then
                mockMvc.perform(
                                post("/api/v1/import/sessions/{id}/cancel", SESSION_ID)
                                                .with(csrf())
                                                .with(authentication(createAuthentication(USER_ID))))
                                .andExpect(status().isBadRequest());

                verify(importService).cancelImport(SESSION_ID, USER_ID);
        }

        @Test
        @DisplayName("POST /sessions/{id}/cancel - should return 401 when not authenticated")
        void shouldReturn401WhenNotAuthenticatedForCancel() throws Exception {
                // When & Then
                mockMvc.perform(post("/api/v1/import/sessions/{id}/cancel", SESSION_ID).with(csrf()))
                                .andExpect(status().isUnauthorized());

                verify(importService, never()).cancelImport(anyLong(), anyLong());
        }

        // ========================================
        // GET /api/v1/import/sessions Tests
        // ========================================

        @Test
        @DisplayName("GET /sessions - should get all user sessions successfully")
        void shouldGetAllUserSessionsSuccessfully() throws Exception {
                // Given
                List<ImportSession> sessions = Arrays.asList(
                                createImportSession(42L, ImportStatus.COMPLETED, 50),
                                createImportSession(38L, ImportStatus.PARSED, 120),
                                createImportSession(35L, ImportStatus.CANCELLED, 30));

                when(importService.getUserSessions(USER_ID)).thenReturn(sessions);

                // When & Then
                mockMvc.perform(
                                get("/api/v1/import/sessions")
                                                .with(authentication(createAuthentication(USER_ID))))
                                .andDo(print())
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$").isArray())
                                .andExpect(jsonPath("$", hasSize(3)))
                                .andExpect(jsonPath("$[0].id").value(42))
                                .andExpect(jsonPath("$[0].status").value("COMPLETED"))
                                .andExpect(jsonPath("$[1].id").value(38))
                                .andExpect(jsonPath("$[1].status").value("PARSED"))
                                .andExpect(jsonPath("$[2].id").value(35))
                                .andExpect(jsonPath("$[2].status").value("CANCELLED"));

                verify(importService).getUserSessions(USER_ID);
        }

        @Test
        @DisplayName("GET /sessions - should return empty list when user has no sessions")
        void shouldReturnEmptyListWhenUserHasNoSessions() throws Exception {
                // Given
                when(importService.getUserSessions(USER_ID)).thenReturn(Collections.emptyList());

                // When & Then
                mockMvc.perform(
                                get("/api/v1/import/sessions")
                                                .with(authentication(createAuthentication(USER_ID))))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$").isArray())
                                .andExpect(jsonPath("$", hasSize(0)));

                verify(importService).getUserSessions(USER_ID);
        }

        @Test
        @DisplayName("GET /sessions - should return 401 when not authenticated")
        void shouldReturn401WhenNotAuthenticatedForGetUserSessions() throws Exception {
                // When & Then
                mockMvc.perform(get("/api/v1/import/sessions")).andExpect(status().isUnauthorized());

                verify(importService, never()).getUserSessions(anyLong());
        }

        // ========================================
        // Helper Methods
        // ========================================

        /** Creates a mock ImportSession for testing. */
        private ImportSession createImportSession(Long id, ImportStatus status, int totalTransactions) {
                return ImportSession.builder()
                                .id(id)
                                .uploadId(UPLOAD_ID)
                                .userId(USER_ID)
                                .fileName("transactions.qif")
                                .fileFormat("QIF")
                                .accountId(ACCOUNT_ID)
                                .status(status)
                                .totalTransactions(totalTransactions)
                                .importedCount(0)
                                .skippedCount(0)
                                .errorCount(0)
                                .duplicateCount(0)
                                .metadata("{}")
                                .createdAt(LocalDateTime.now().minusHours(1))
                                .updatedAt(LocalDateTime.now())
                                .build();
        }

        /** Creates a mock ImportedTransaction for testing. */
        private ImportedTransaction createImportedTransaction(
                        String payee, BigDecimal amount, String category) {
                return ImportedTransaction.builder()
                                .transactionDate(LocalDate.of(2026, 1, 15))
                                .payee(payee)
                                .amount(amount)
                                .category(category)
                                .memo("Test transaction")
                                .clearedStatus("uncleared")
                                .validationErrors(new ArrayList<>())
                                .potentialDuplicate(false)
                                .sourceFileName("transactions.qif")
                                .build();
        }
}
