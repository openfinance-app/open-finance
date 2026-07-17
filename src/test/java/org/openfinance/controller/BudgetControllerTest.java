package org.openfinance.controller;

import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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
import org.openfinance.dto.BudgetBulkCreateRequest;
import org.openfinance.dto.BudgetBulkCreateResponse;
import org.openfinance.dto.BudgetHistoryEntry;
import org.openfinance.dto.BudgetHistoryResponse;
import org.openfinance.dto.BudgetProgressResponse;
import org.openfinance.dto.BudgetRequest;
import org.openfinance.dto.BudgetResponse;
import org.openfinance.dto.BudgetSuggestion;
import org.openfinance.dto.BudgetSuggestionRequest;
import org.openfinance.dto.BudgetSummaryResponse;
import org.openfinance.entity.BudgetPeriod;
import org.openfinance.entity.CategoryType;
import org.openfinance.entity.User;
import org.openfinance.exception.BudgetNotFoundException;
import org.openfinance.exception.CategoryNotFoundException;
import org.openfinance.service.BudgetService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Unit tests for BudgetController.
 *
 * <p>Tests all 7 endpoints: - POST /api/v1/budgets - GET /api/v1/budgets - GET /api/v1/budgets/{id}
 * - PUT /api/v1/budgets/{id} - DELETE /api/v1/budgets/{id} - GET /api/v1/budgets/{id}/progress -
 * GET /api/v1/budgets/summary
 *
 * <p>Uses @WebMvcTest for controller-only testing with mocked BudgetService.
 */
@WebMvcTest(BudgetController.class)
@org.springframework.context.annotation.Import({
    org.openfinance.config.RateLimitConfig.class,
    org.openfinance.config.RateLimitInterceptor.class
})
@DisplayName("BudgetController Unit Tests")
class BudgetControllerTest {

    @Autowired private MockMvc mockMvc;

    @Autowired private ObjectMapper objectMapper;

    @MockBean private BudgetService budgetService;

    @MockBean private org.openfinance.service.JwtService jwtService;

    @MockBean private org.openfinance.repository.UserRepository userRepository;

    @MockBean private org.openfinance.security.EncryptionKeyCache encryptionKeyCache;

    private static final Long USER_ID = 1L;
    private static final Long BUDGET_ID = 1L;
    private static final Long CATEGORY_ID = 1L;
    private BudgetRequest testRequest;
    private BudgetResponse testResponse;

    // ========================================
    // Setup Methods
    // ========================================

    @BeforeEach
    void setUp() {
        // Create test request
        testRequest =
                BudgetRequest.builder()
                        .categoryId(CATEGORY_ID)
                        .amount(new BigDecimal("500.00"))
                        .currency("USD")
                        .period(BudgetPeriod.MONTHLY)
                        .startDate(LocalDate.of(2026, 2, 1))
                        .endDate(LocalDate.of(2026, 2, 28))
                        .rollover(false)
                        .notes("Monthly grocery budget")
                        .build();

        // Create test response
        testResponse =
                BudgetResponse.builder()
                        .id(BUDGET_ID)
                        .categoryId(CATEGORY_ID)
                        .categoryName("Groceries")
                        .categoryType(CategoryType.EXPENSE)
                        .amount(new BigDecimal("500.00"))
                        .currency("USD")
                        .period(BudgetPeriod.MONTHLY)
                        .startDate(LocalDate.of(2026, 2, 1))
                        .endDate(LocalDate.of(2026, 2, 28))
                        .rollover(false)
                        .notes("Monthly grocery budget")
                        .createdAt(LocalDateTime.now())
                        .updatedAt(LocalDateTime.now())
                        .build();
    }

    /** Creates an authentication object with a User principal for testing. */
    private Authentication createAuthentication(Long userId) {
        User user =
                User.builder()
                        .id(userId)
                        .username("testuser")
                        .email("test@example.com")
                        .passwordHash("hashed")
                        .masterPasswordSalt("salt")
                        .baseCurrency("USD")
                        .build();

        return new UsernamePasswordAuthenticationToken(
                user, null, Collections.singletonList(new SimpleGrantedAuthority("USER")));
    }

    // ========================================
    // POST /api/v1/budgets Tests
    // ========================================

    @Test
    @DisplayName("POST /budgets - should create budget successfully when valid request")
    void shouldCreateBudgetSuccessfullyWhenValidRequest() throws Exception {
        // Given
        when(budgetService.createBudget(any(BudgetRequest.class), eq(USER_ID)))
                .thenReturn(testResponse);

        // When & Then
        mockMvc.perform(
                        post("/api/v1/budgets")
                                .with(csrf())
                                .with(authentication(createAuthentication(USER_ID)))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(testRequest)))
                .andDo(print())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(BUDGET_ID))
                .andExpect(jsonPath("$.categoryId").value(CATEGORY_ID))
                .andExpect(jsonPath("$.categoryName").value("Groceries"))
                .andExpect(jsonPath("$.amount").value(500.00))
                .andExpect(jsonPath("$.currency").value("USD"))
                .andExpect(jsonPath("$.period").value("MONTHLY"))
                .andExpect(jsonPath("$.startDate").value("2026-02-01"))
                .andExpect(jsonPath("$.endDate").value("2026-02-28"))
                .andExpect(jsonPath("$.rollover").value(false))
                .andExpect(jsonPath("$.notes").value("Monthly grocery budget"));

        verify(budgetService).createBudget(any(BudgetRequest.class), eq(USER_ID));
    }

    @Test
    @DisplayName("POST /budgets - should return 400 when encryption key is missing")
    void shouldReturn400WhenEncryptionKeyIsMissing() throws Exception {
        // When & Then
        mockMvc.perform(
                        post("/api/v1/budgets")
                                .with(csrf())
                                .with(authentication(createAuthentication(USER_ID)))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(testRequest)))
                .andExpect(status().isInternalServerError());
    }

    @Test
    @DisplayName("POST /budgets - should return 400 when request validation fails")
    void shouldReturn400WhenRequestValidationFails() throws Exception {
        // Given - invalid request with null categoryId
        BudgetRequest invalidRequest =
                BudgetRequest.builder()
                        .categoryId(null)
                        .amount(new BigDecimal("500.00"))
                        .currency("USD")
                        .period(BudgetPeriod.MONTHLY)
                        .startDate(LocalDate.of(2026, 2, 1))
                        .endDate(LocalDate.of(2026, 2, 28))
                        .build();

        // When & Then
        mockMvc.perform(
                        post("/api/v1/budgets")
                                .with(csrf())
                                .with(authentication(createAuthentication(USER_ID)))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(invalidRequest)))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(budgetService);
    }

    @Test
    @DisplayName("POST /budgets - should return 404 when category not found")
    void shouldReturn404WhenCategoryNotFound() throws Exception {
        // Given
        when(budgetService.createBudget(any(BudgetRequest.class), eq(USER_ID)))
                .thenThrow(
                        new CategoryNotFoundException(
                                "Category not found with id: "
                                        + CATEGORY_ID
                                        + " for user: "
                                        + USER_ID));

        // When & Then
        mockMvc.perform(
                        post("/api/v1/budgets")
                                .with(csrf())
                                .with(authentication(createAuthentication(USER_ID)))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(testRequest)))
                .andExpect(status().isNotFound());

        verify(budgetService).createBudget(any(BudgetRequest.class), eq(USER_ID));
    }

    @Test
    @DisplayName("POST /budgets - should return 400 when duplicate budget exists")
    void shouldReturn400WhenDuplicateBudgetExists() throws Exception {
        // Given
        when(budgetService.createBudget(any(BudgetRequest.class), eq(USER_ID)))
                .thenThrow(
                        new IllegalStateException(
                                "Budget already exists for category "
                                        + CATEGORY_ID
                                        + " and period MONTHLY"));

        // When & Then
        mockMvc.perform(
                        post("/api/v1/budgets")
                                .with(csrf())
                                .with(authentication(createAuthentication(USER_ID)))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(testRequest)))
                .andExpect(status().isBadRequest());

        verify(budgetService).createBudget(any(BudgetRequest.class), eq(USER_ID));
    }

    @Test
    @DisplayName("POST /budgets - should return 401 when not authenticated")
    void shouldReturn401WhenNotAuthenticatedForCreate() throws Exception {
        // When & Then
        mockMvc.perform(
                        post("/api/v1/budgets")
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(testRequest)))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(budgetService);
    }

    // ========================================
    // GET /api/v1/budgets Tests
    // ========================================

    @Test
    @DisplayName("GET /budgets - should get all budgets successfully")
    void shouldGetAllBudgetsSuccessfully() throws Exception {
        // Given
        List<BudgetResponse> budgets =
                Arrays.asList(
                        testResponse,
                        BudgetResponse.builder()
                                .id(2L)
                                .categoryId(2L)
                                .categoryName("Dining Out")
                                .categoryType(CategoryType.EXPENSE)
                                .amount(new BigDecimal("300.00"))
                                .currency("USD")
                                .period(BudgetPeriod.MONTHLY)
                                .startDate(LocalDate.of(2026, 2, 1))
                                .endDate(LocalDate.of(2026, 2, 28))
                                .rollover(false)
                                .createdAt(LocalDateTime.now())
                                .updatedAt(LocalDateTime.now())
                                .build());

        when(budgetService.getBudgetsByUser(eq(USER_ID))).thenReturn(budgets);

        // When & Then
        mockMvc.perform(get("/api/v1/budgets").with(authentication(createAuthentication(USER_ID))))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].id").value(BUDGET_ID))
                .andExpect(jsonPath("$[0].categoryName").value("Groceries"))
                .andExpect(jsonPath("$[1].id").value(2))
                .andExpect(jsonPath("$[1].categoryName").value("Dining Out"));

        verify(budgetService).getBudgetsByUser(eq(USER_ID));
    }

    @Test
    @DisplayName("GET /budgets - should filter budgets by period")
    void shouldFilterBudgetsByPeriod() throws Exception {
        // Given
        List<BudgetResponse> monthlyBudgets = Collections.singletonList(testResponse);

        when(budgetService.getBudgetsByPeriod(eq(USER_ID), eq(BudgetPeriod.MONTHLY)))
                .thenReturn(monthlyBudgets);

        // When & Then
        mockMvc.perform(
                        get("/api/v1/budgets")
                                .with(authentication(createAuthentication(USER_ID)))
                                .param("period", "MONTHLY"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].period").value("MONTHLY"));

        verify(budgetService).getBudgetsByPeriod(eq(USER_ID), eq(BudgetPeriod.MONTHLY));
    }

    @Test
    @DisplayName("GET /budgets - should return 400 when encryption key missing")
    void shouldReturn400WhenEncryptionKeyMissingForGetAll() throws Exception {
        // When & Then
        mockMvc.perform(get("/api/v1/budgets").with(authentication(createAuthentication(USER_ID))))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("GET /budgets - should return 401 when not authenticated")
    void shouldReturn401WhenNotAuthenticatedForGetAll() throws Exception {
        // When & Then
        mockMvc.perform(get("/api/v1/budgets").header("X-Encryption-Session", "dummy"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(budgetService);
    }

    // ========================================
    // GET /api/v1/budgets/{id} Tests
    // ========================================

    @Test
    @DisplayName("GET /budgets/{id} - should get budget by id successfully")
    void shouldGetBudgetByIdSuccessfully() throws Exception {
        // Given
        when(budgetService.getBudgetById(eq(BUDGET_ID), eq(USER_ID))).thenReturn(testResponse);

        // When & Then
        mockMvc.perform(
                        get("/api/v1/budgets/{id}", BUDGET_ID)
                                .with(authentication(createAuthentication(USER_ID))))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(BUDGET_ID))
                .andExpect(jsonPath("$.categoryName").value("Groceries"))
                .andExpect(jsonPath("$.amount").value(500.00));

        verify(budgetService).getBudgetById(eq(BUDGET_ID), eq(USER_ID));
    }

    @Test
    @DisplayName("GET /budgets/{id} - should return 404 when budget not found")
    void shouldReturn404WhenBudgetNotFound() throws Exception {
        // Given
        when(budgetService.getBudgetById(eq(999L), eq(USER_ID)))
                .thenThrow(
                        new BudgetNotFoundException(
                                "Budget not found with id: 999 for user: " + USER_ID));

        // When & Then
        mockMvc.perform(
                        get("/api/v1/budgets/{id}", 999L)
                                .with(authentication(createAuthentication(USER_ID))))
                .andExpect(status().isNotFound());

        verify(budgetService).getBudgetById(eq(999L), eq(USER_ID));
    }

    @Test
    @DisplayName("GET /budgets/{id} - should return 400 when encryption key missing")
    void shouldReturn400WhenEncryptionKeyMissingForGetById() throws Exception {
        // When & Then
        mockMvc.perform(
                        get("/api/v1/budgets/{id}", BUDGET_ID)
                                .with(authentication(createAuthentication(USER_ID))))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("GET /budgets/{id} - should return 401 when not authenticated")
    void shouldReturn401WhenNotAuthenticatedForGetById() throws Exception {
        // When & Then
        mockMvc.perform(
                        get("/api/v1/budgets/{id}", BUDGET_ID)
                                .header("X-Encryption-Session", "dummy"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(budgetService);
    }

    // ========================================
    // PUT /api/v1/budgets/{id} Tests
    // ========================================

    @Test
    @DisplayName("PUT /budgets/{id} - should update budget successfully")
    void shouldUpdateBudgetSuccessfully() throws Exception {
        // Given
        BudgetResponse updatedResponse =
                BudgetResponse.builder()
                        .id(BUDGET_ID)
                        .categoryId(CATEGORY_ID)
                        .categoryName("Groceries")
                        .categoryType(CategoryType.EXPENSE)
                        .amount(new BigDecimal("600.00"))
                        .currency("USD")
                        .period(BudgetPeriod.MONTHLY)
                        .startDate(LocalDate.of(2026, 2, 1))
                        .endDate(LocalDate.of(2026, 2, 28))
                        .rollover(false)
                        .notes("Updated monthly grocery budget")
                        .createdAt(LocalDateTime.now())
                        .updatedAt(LocalDateTime.now())
                        .build();

        when(budgetService.updateBudget(eq(BUDGET_ID), any(BudgetRequest.class), eq(USER_ID)))
                .thenReturn(updatedResponse);

        BudgetRequest updateRequest =
                BudgetRequest.builder()
                        .categoryId(CATEGORY_ID)
                        .amount(new BigDecimal("600.00"))
                        .currency("USD")
                        .period(BudgetPeriod.MONTHLY)
                        .startDate(LocalDate.of(2026, 2, 1))
                        .endDate(LocalDate.of(2026, 2, 28))
                        .rollover(false)
                        .notes("Updated monthly grocery budget")
                        .build();

        // When & Then
        mockMvc.perform(
                        put("/api/v1/budgets/{id}", BUDGET_ID)
                                .with(csrf())
                                .with(authentication(createAuthentication(USER_ID)))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(updateRequest)))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(BUDGET_ID))
                .andExpect(jsonPath("$.amount").value(600.00))
                .andExpect(jsonPath("$.notes").value("Updated monthly grocery budget"));

        verify(budgetService).updateBudget(eq(BUDGET_ID), any(BudgetRequest.class), eq(USER_ID));
    }

    @Test
    @DisplayName("PUT /budgets/{id} - should return 404 when budget not found")
    void shouldReturn404WhenBudgetNotFoundForUpdate() throws Exception {
        // Given
        when(budgetService.updateBudget(eq(999L), any(BudgetRequest.class), eq(USER_ID)))
                .thenThrow(
                        new BudgetNotFoundException(
                                "Budget not found with id: 999 for user: " + USER_ID));

        // When & Then
        mockMvc.perform(
                        put("/api/v1/budgets/{id}", 999L)
                                .with(csrf())
                                .with(authentication(createAuthentication(USER_ID)))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(testRequest)))
                .andExpect(status().isNotFound());

        verify(budgetService).updateBudget(eq(999L), any(BudgetRequest.class), eq(USER_ID));
    }

    @Test
    @DisplayName("PUT /budgets/{id} - should return 400 when validation fails")
    void shouldReturn400WhenValidationFailsForUpdate() throws Exception {
        // Given - invalid request with negative amount
        BudgetRequest invalidRequest =
                BudgetRequest.builder()
                        .categoryId(CATEGORY_ID)
                        .amount(new BigDecimal("-100.00"))
                        .currency("USD")
                        .period(BudgetPeriod.MONTHLY)
                        .startDate(LocalDate.of(2026, 2, 1))
                        .endDate(LocalDate.of(2026, 2, 28))
                        .build();

        // When & Then
        mockMvc.perform(
                        put("/api/v1/budgets/{id}", BUDGET_ID)
                                .with(csrf())
                                .with(authentication(createAuthentication(USER_ID)))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(invalidRequest)))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(budgetService);
    }

    @Test
    @DisplayName("PUT /budgets/{id} - should return 401 when not authenticated")
    void shouldReturn401WhenNotAuthenticatedForUpdate() throws Exception {
        // When & Then
        mockMvc.perform(
                        put("/api/v1/budgets/{id}", BUDGET_ID)
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(testRequest)))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(budgetService);
    }

    // ========================================
    // DELETE /api/v1/budgets/{id} Tests
    // ========================================

    @Test
    @DisplayName("DELETE /budgets/{id} - should delete budget successfully")
    void shouldDeleteBudgetSuccessfully() throws Exception {
        // Given
        doNothing().when(budgetService).deleteBudget(eq(BUDGET_ID), eq(USER_ID));

        // When & Then
        mockMvc.perform(
                        delete("/api/v1/budgets/{id}", BUDGET_ID)
                                .with(csrf())
                                .with(authentication(createAuthentication(USER_ID))))
                .andDo(print())
                .andExpect(status().isNoContent());

        verify(budgetService).deleteBudget(eq(BUDGET_ID), eq(USER_ID));
    }

    @Test
    @DisplayName("DELETE /budgets/{id} - should return 404 when budget not found")
    void shouldReturn404WhenBudgetNotFoundForDelete() throws Exception {
        // Given
        doThrow(new BudgetNotFoundException("Budget not found with id: 999 for user: " + USER_ID))
                .when(budgetService)
                .deleteBudget(eq(999L), eq(USER_ID));

        // When & Then
        mockMvc.perform(
                        delete("/api/v1/budgets/{id}", 999L)
                                .with(csrf())
                                .with(authentication(createAuthentication(USER_ID))))
                .andExpect(status().isNotFound());

        verify(budgetService).deleteBudget(eq(999L), eq(USER_ID));
    }

    @Test
    @DisplayName("DELETE /budgets/{id} - should return 401 when not authenticated")
    void shouldReturn401WhenNotAuthenticatedForDelete() throws Exception {
        // When & Then
        mockMvc.perform(delete("/api/v1/budgets/{id}", BUDGET_ID).with(csrf()))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(budgetService);
    }

    // ========================================
    // GET /api/v1/budgets/{id}/progress Tests
    // ========================================

    @Test
    @DisplayName("GET /budgets/{id}/progress - should get budget progress successfully")
    void shouldGetBudgetProgressSuccessfully() throws Exception {
        // Given
        BudgetProgressResponse progressResponse =
                BudgetProgressResponse.builder()
                        .budgetId(BUDGET_ID)
                        .categoryName("Groceries")
                        .budgeted(new BigDecimal("500.00"))
                        .spent(new BigDecimal("350.25"))
                        .remaining(new BigDecimal("149.75"))
                        .percentageSpent(new BigDecimal("70.05"))
                        .currency("USD")
                        .period(BudgetPeriod.MONTHLY)
                        .startDate(LocalDate.of(2026, 2, 1))
                        .endDate(LocalDate.of(2026, 2, 28))
                        .daysRemaining(26)
                        .status("ON_TRACK")
                        .build();

        when(budgetService.calculateBudgetProgress(eq(BUDGET_ID), eq(USER_ID)))
                .thenReturn(progressResponse);

        // When & Then
        mockMvc.perform(
                        get("/api/v1/budgets/{id}/progress", BUDGET_ID)
                                .with(authentication(createAuthentication(USER_ID))))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.budgetId").value(BUDGET_ID))
                .andExpect(jsonPath("$.categoryName").value("Groceries"))
                .andExpect(jsonPath("$.budgeted").value(500.00))
                .andExpect(jsonPath("$.spent").value(350.25))
                .andExpect(jsonPath("$.remaining").value(149.75))
                .andExpect(jsonPath("$.percentageSpent").value(70.05))
                .andExpect(jsonPath("$.daysRemaining").value(26))
                .andExpect(jsonPath("$.status").value("ON_TRACK"));

        verify(budgetService).calculateBudgetProgress(eq(BUDGET_ID), eq(USER_ID));
    }

    @Test
    @DisplayName("GET /budgets/{id}/progress - should return 404 when budget not found")
    void shouldReturn404WhenBudgetNotFoundForProgress() throws Exception {
        // Given
        when(budgetService.calculateBudgetProgress(eq(999L), eq(USER_ID)))
                .thenThrow(
                        new BudgetNotFoundException(
                                "Budget not found with id: 999 for user: " + USER_ID));

        // When & Then
        mockMvc.perform(
                        get("/api/v1/budgets/{id}/progress", 999L)
                                .with(authentication(createAuthentication(USER_ID))))
                .andExpect(status().isNotFound());

        verify(budgetService).calculateBudgetProgress(eq(999L), eq(USER_ID));
    }

    @Test
    @DisplayName("GET /budgets/{id}/progress - should return 400 when encryption key missing")
    void shouldReturn400WhenEncryptionKeyMissingForProgress() throws Exception {
        // When & Then
        mockMvc.perform(
                        get("/api/v1/budgets/{id}/progress", BUDGET_ID)
                                .with(authentication(createAuthentication(USER_ID))))
                .andExpect(status().isInternalServerError());
    }

    @Test
    @DisplayName("GET /budgets/{id}/progress - should return 401 when not authenticated")
    void shouldReturn401WhenNotAuthenticatedForProgress() throws Exception {
        // When & Then
        mockMvc.perform(
                        get("/api/v1/budgets/{id}/progress", BUDGET_ID)
                                .header("X-Encryption-Session", "dummy"))
                .andExpect(status().isUnauthorized());
    }

    // ========================================
    // GET /api/v1/budgets/{id}/history Tests
    // ========================================

    @Test
    @DisplayName("GET /budgets/{id}/history - should get budget history successfully")
    void shouldGetBudgetHistorySuccessfully() throws Exception {
        // Given
        List<BudgetHistoryEntry> historyEntries =
                Arrays.asList(
                        BudgetHistoryEntry.builder()
                                .label("Feb 2026")
                                .periodStart(LocalDate.of(2026, 2, 1))
                                .periodEnd(LocalDate.of(2026, 2, 28))
                                .budgeted(new BigDecimal("500.00"))
                                .spent(new BigDecimal("350.25"))
                                .remaining(new BigDecimal("149.75"))
                                .percentageSpent(new BigDecimal("70.05"))
                                .status("ON_TRACK")
                                .build(),
                        BudgetHistoryEntry.builder()
                                .label("Mar 2026")
                                .periodStart(LocalDate.of(2026, 3, 1))
                                .periodEnd(LocalDate.of(2026, 3, 31))
                                .budgeted(new BigDecimal("500.00"))
                                .spent(new BigDecimal("450.00"))
                                .remaining(new BigDecimal("50.00"))
                                .percentageSpent(new BigDecimal("90.00"))
                                .status("WARNING")
                                .build());

        BudgetHistoryResponse historyResponse =
                BudgetHistoryResponse.builder()
                        .budgetId(BUDGET_ID)
                        .categoryName("Groceries")
                        .amount(new BigDecimal("500.00"))
                        .currency("USD")
                        .period(BudgetPeriod.MONTHLY)
                        .startDate(LocalDate.of(2026, 2, 1))
                        .endDate(LocalDate.of(2026, 2, 28))
                        .history(historyEntries)
                        .totalSpent(new BigDecimal("800.25"))
                        .totalBudgeted(new BigDecimal("1000.00"))
                        .build();

        when(budgetService.getBudgetHistory(eq(BUDGET_ID), eq(USER_ID)))
                .thenReturn(historyResponse);

        // When & Then
        mockMvc.perform(
                        get("/api/v1/budgets/{id}/history", BUDGET_ID)
                                .with(authentication(createAuthentication(USER_ID))))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.budgetId").value(BUDGET_ID))
                .andExpect(jsonPath("$.categoryName").value("Groceries"))
                .andExpect(jsonPath("$.amount").value(500.00))
                .andExpect(jsonPath("$.currency").value("USD"))
                .andExpect(jsonPath("$.period").value("MONTHLY"))
                .andExpect(jsonPath("$.startDate").value("2026-02-01"))
                .andExpect(jsonPath("$.endDate").value("2026-02-28"))
                .andExpect(jsonPath("$.history").isArray())
                .andExpect(jsonPath("$.history", hasSize(2)))
                .andExpect(jsonPath("$.history[0].label").value("Feb 2026"))
                .andExpect(jsonPath("$.history[0].budgeted").value(500.00))
                .andExpect(jsonPath("$.history[0].spent").value(350.25))
                .andExpect(jsonPath("$.history[0].remaining").value(149.75))
                .andExpect(jsonPath("$.history[0].percentageSpent").value(70.05))
                .andExpect(jsonPath("$.history[0].status").value("ON_TRACK"))
                .andExpect(jsonPath("$.history[1].label").value("Mar 2026"))
                .andExpect(jsonPath("$.history[1].status").value("WARNING"))
                .andExpect(jsonPath("$.totalSpent").value(800.25))
                .andExpect(jsonPath("$.totalBudgeted").value(1000.00));

        verify(budgetService).getBudgetHistory(eq(BUDGET_ID), eq(USER_ID));
    }

    @Test
    @DisplayName("GET /budgets/{id}/history - should return 404 when budget not found")
    void shouldReturn404WhenBudgetNotFoundForHistory() throws Exception {
        // Given
        when(budgetService.getBudgetHistory(eq(999L), eq(USER_ID)))
                .thenThrow(
                        new BudgetNotFoundException(
                                "Budget not found with id: 999 for user: " + USER_ID));

        // When & Then
        mockMvc.perform(
                        get("/api/v1/budgets/{id}/history", 999L)
                                .with(authentication(createAuthentication(USER_ID))))
                .andExpect(status().isNotFound());

        verify(budgetService).getBudgetHistory(eq(999L), eq(USER_ID));
    }

    @Test
    @DisplayName("GET /budgets/{id}/history - should return 400 when encryption key missing")
    void shouldReturn400WhenEncryptionKeyMissingForHistory() throws Exception {
        // When & Then
        mockMvc.perform(
                        get("/api/v1/budgets/{id}/history", BUDGET_ID)
                                .with(authentication(createAuthentication(USER_ID))))
                .andExpect(status().isInternalServerError());
    }

    @Test
    @DisplayName("GET /budgets/{id}/history - should return 401 when not authenticated")
    void shouldReturn401WhenNotAuthenticatedForHistory() throws Exception {
        // When & Then
        mockMvc.perform(
                        get("/api/v1/budgets/{id}/history", BUDGET_ID)
                                .header("X-Encryption-Session", "dummy"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(budgetService);
    }

    // ========================================
    // GET /api/v1/budgets/summary Tests
    // ========================================

    @Test
    @DisplayName("GET /budgets/summary - should get budget summary successfully")
    void shouldGetBudgetSummarySuccessfully() throws Exception {
        // Given
        BudgetSummaryResponse summaryResponse =
                BudgetSummaryResponse.builder()
                        .period(BudgetPeriod.MONTHLY)
                        .totalBudgets(3)
                        .activeBudgets(2)
                        .totalBudgeted(new BigDecimal("1500.00"))
                        .totalSpent(new BigDecimal("1050.00"))
                        .totalRemaining(new BigDecimal("450.00"))
                        .averageSpentPercentage(new BigDecimal("70.00"))
                        .currency("USD")
                        .budgets(Collections.emptyList())
                        .build();

        when(budgetService.getBudgetSummary(eq(USER_ID), eq(BudgetPeriod.MONTHLY)))
                .thenReturn(summaryResponse);

        // When & Then
        mockMvc.perform(
                        get("/api/v1/budgets/summary")
                                .with(authentication(createAuthentication(USER_ID)))
                                .param("period", "MONTHLY"))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.period").value("MONTHLY"))
                .andExpect(jsonPath("$.totalBudgets").value(3))
                .andExpect(jsonPath("$.activeBudgets").value(2))
                .andExpect(jsonPath("$.totalBudgeted").value(1500.00))
                .andExpect(jsonPath("$.totalSpent").value(1050.00))
                .andExpect(jsonPath("$.totalRemaining").value(450.00))
                .andExpect(jsonPath("$.averageSpentPercentage").value(70.00));

        verify(budgetService).getBudgetSummary(eq(USER_ID), eq(BudgetPeriod.MONTHLY));
    }

    @Test
    @DisplayName(
            "GET /budgets/summary - should return all budgets summary when period not specified")
    void shouldUseDefaultPeriodWhenNotSpecified() throws Exception {
        // Given
        BudgetSummaryResponse summaryResponse =
                BudgetSummaryResponse.builder()
                        .period(null)
                        .totalBudgets(0)
                        .activeBudgets(0)
                        .totalBudgeted(BigDecimal.ZERO)
                        .totalSpent(BigDecimal.ZERO)
                        .totalRemaining(BigDecimal.ZERO)
                        .averageSpentPercentage(BigDecimal.ZERO)
                        .currency("USD")
                        .budgets(Collections.emptyList())
                        .build();

        when(budgetService.getAllBudgetsSummary(eq(USER_ID))).thenReturn(summaryResponse);

        // When & Then
        mockMvc.perform(
                        get("/api/v1/budgets/summary")
                                .with(authentication(createAuthentication(USER_ID))))
                .andExpect(status().isOk());

        verify(budgetService).getAllBudgetsSummary(eq(USER_ID));
    }

    @Test
    @DisplayName("GET /budgets/summary - should return 400 when encryption key missing")
    void shouldReturn400WhenEncryptionKeyMissingForSummary() throws Exception {
        // When & Then
        mockMvc.perform(
                        get("/api/v1/budgets/summary")
                                .with(authentication(createAuthentication(USER_ID)))
                                .param("period", "MONTHLY"))
                .andExpect(status().isInternalServerError());
    }

    @Test
    @DisplayName("GET /budgets/summary - should return 401 when not authenticated")
    void shouldReturn401WhenNotAuthenticatedForSummary() throws Exception {
        // When & Then
        mockMvc.perform(
                        get("/api/v1/budgets/summary")
                                .header("X-Encryption-Session", "dummy")
                                .param("period", "MONTHLY"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(budgetService);
    }

    // ========================================
    // POST /api/v1/budgets/suggestions Tests
    // ========================================

    @Test
    void shouldGetBudgetSuggestionsSuccessfully() throws Exception {
        // Given
        BudgetSuggestionRequest request =
                BudgetSuggestionRequest.builder()
                        .period(BudgetPeriod.MONTHLY)
                        .lookbackMonths(6)
                        .currency("EUR")
                        .build();

        List<BudgetSuggestion> suggestions =
                Arrays.asList(
                        BudgetSuggestion.builder()
                                .categoryId(1L)
                                .categoryName("Groceries")
                                .suggestedAmount(new BigDecimal("500.00"))
                                .averageSpent(new BigDecimal("450.00"))
                                .transactionCount(10)
                                .period(BudgetPeriod.MONTHLY)
                                .currency("EUR")
                                .startDate(LocalDate.now().minusMonths(6))
                                .endDate(LocalDate.now())
                                .hasExistingBudget(false)
                                .build());

        when(budgetService.analyzeCategorySpending(
                        eq(USER_ID), eq(BudgetPeriod.MONTHLY), eq(6), eq(null)))
                .thenReturn(suggestions);

        // When & Then
        mockMvc.perform(
                        post("/api/v1/budgets/suggestions")
                                .with(csrf())
                                .with(authentication(createAuthentication(USER_ID)))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].categoryId").value(1))
                .andExpect(jsonPath("$[0].categoryName").value("Groceries"))
                .andExpect(jsonPath("$[0].suggestedAmount").value(500.00))
                .andExpect(jsonPath("$[0].currency").value("EUR"))
                .andExpect(jsonPath("$[0].hasExistingBudget").value(false));

        verify(budgetService)
                .analyzeCategorySpending(eq(USER_ID), eq(BudgetPeriod.MONTHLY), eq(6), eq(null));
    }

    @Test
    void shouldReturn400WhenEncryptionKeyMissingForSuggestions() throws Exception {
        // Given
        BudgetSuggestionRequest request =
                BudgetSuggestionRequest.builder()
                        .period(BudgetPeriod.MONTHLY)
                        .lookbackMonths(6)
                        .build();

        // When & Then
        mockMvc.perform(
                        post("/api/v1/budgets/suggestions")
                                .with(csrf())
                                .with(authentication(createAuthentication(USER_ID)))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());
    }

    @Test
    void shouldReturn400WhenRequestValidationFailsForSuggestions() throws Exception {
        // Given - invalid request with null period
        BudgetSuggestionRequest invalidRequest =
                BudgetSuggestionRequest.builder().period(null).lookbackMonths(6).build();

        // When & Then
        mockMvc.perform(
                        post("/api/v1/budgets/suggestions")
                                .with(csrf())
                                .with(authentication(createAuthentication(USER_ID)))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(invalidRequest)))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(budgetService);
    }

    @Test
    void shouldReturn401WhenNotAuthenticatedForSuggestions() throws Exception {
        // Given
        BudgetSuggestionRequest request =
                BudgetSuggestionRequest.builder()
                        .period(BudgetPeriod.MONTHLY)
                        .lookbackMonths(6)
                        .build();

        // When & Then
        mockMvc.perform(
                        post("/api/v1/budgets/suggestions")
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(budgetService);
    }

    // ========================================
    // POST /api/v1/budgets/bulk Tests
    // ========================================

    @Test
    void shouldBulkCreateBudgetsSuccessfully() throws Exception {
        // Given
        BudgetBulkCreateRequest request =
                BudgetBulkCreateRequest.builder().budgets(Arrays.asList(testRequest)).build();

        BudgetBulkCreateResponse response =
                BudgetBulkCreateResponse.builder()
                        .created(Arrays.asList(testResponse))
                        .successCount(1)
                        .skippedCount(0)
                        .errors(Collections.emptyList())
                        .build();

        when(budgetService.bulkCreateBudgets(eq(USER_ID), eq(request.getBudgets())))
                .thenReturn(response);

        // When & Then
        mockMvc.perform(
                        post("/api/v1/budgets/bulk")
                                .with(csrf())
                                .with(authentication(createAuthentication(USER_ID)))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                .andDo(print())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.created").isArray())
                .andExpect(jsonPath("$.created", hasSize(1)))
                .andExpect(jsonPath("$.successCount").value(1))
                .andExpect(jsonPath("$.skippedCount").value(0))
                .andExpect(jsonPath("$.errors").isEmpty());

        verify(budgetService).bulkCreateBudgets(eq(USER_ID), eq(request.getBudgets()));
    }

    @Test
    void shouldReturn400WhenEncryptionKeyMissingForBulkCreate() throws Exception {
        // Given
        BudgetBulkCreateRequest request =
                BudgetBulkCreateRequest.builder().budgets(Arrays.asList(testRequest)).build();

        // When & Then
        mockMvc.perform(
                        post("/api/v1/budgets/bulk")
                                .with(csrf())
                                .with(authentication(createAuthentication(USER_ID)))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isInternalServerError());
    }

    @Test
    void shouldReturn400WhenRequestValidationFailsForBulkCreate() throws Exception {
        // Given - invalid request with empty budgets list
        BudgetBulkCreateRequest invalidRequest =
                BudgetBulkCreateRequest.builder().budgets(Collections.emptyList()).build();

        // When & Then
        mockMvc.perform(
                        post("/api/v1/budgets/bulk")
                                .with(csrf())
                                .with(authentication(createAuthentication(USER_ID)))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(invalidRequest)))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(budgetService);
    }

    @Test
    void shouldReturn401WhenNotAuthenticatedForBulkCreate() throws Exception {
        // Given
        BudgetBulkCreateRequest request =
                BudgetBulkCreateRequest.builder().budgets(Arrays.asList(testRequest)).build();

        // When & Then
        mockMvc.perform(
                        post("/api/v1/budgets/bulk")
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(budgetService);
    }
}
