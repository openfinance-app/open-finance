package org.openfinance.controller;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.openfinance.dto.calculator.FreedomCalculatorRequest;
import org.openfinance.service.OperationHistoryService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/** Integration tests for FinancialFreedomController */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@WithMockUser
class FinancialFreedomControllerTest {

    @MockBean private OperationHistoryService operationHistoryService;

    @Autowired private MockMvc mockMvc;

    @Autowired private ObjectMapper objectMapper;

    private FreedomCalculatorRequest validRequest;

    @BeforeEach
    void setUp() {
        validRequest =
                FreedomCalculatorRequest.builder()
                        .currentSavings(new BigDecimal("50000"))
                        .monthlyExpenses(new BigDecimal("2500"))
                        .expectedAnnualReturn(new BigDecimal("7.0"))
                        .monthlyContribution(new BigDecimal("500"))
                        .withdrawalRate(new BigDecimal("4.0"))
                        .inflationRate(new BigDecimal("2.5"))
                        .adjustForInflation(false)
                        .projectionYears(30)
                        .build();
    }

    @Nested
    @DisplayName("POST /api/v1/calculator/financial-freedom/timeline")
    class TimelineEndpointTests {

        @Test
        @DisplayName("Should return 200 OK with valid request")
        void shouldReturnOkWithValidRequest() throws Exception {
            mockMvc.perform(
                            post("/api/v1/calculator/financial-freedom/timeline")
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(objectMapper.writeValueAsString(validRequest)))
                    .andExpect(status().isOk())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$.isAchievable").isBoolean())
                    .andExpect(jsonPath("$.yearsToFreedom").isNumber())
                    .andExpect(jsonPath("$.monthsToFreedom").isNumber())
                    .andExpect(jsonPath("$.targetSavingsAmount").isNumber())
                    .andExpect(jsonPath("$.currentProgress").isNumber())
                    .andExpect(jsonPath("$.progressPercentage").isNumber());
        }

        @Test
        @DisplayName("Should calculate correct timeline for achievable scenario")
        void shouldCalculateCorrectTimelineForAchievableScenario() throws Exception {
            mockMvc.perform(
                            post("/api/v1/calculator/financial-freedom/timeline")
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(objectMapper.writeValueAsString(validRequest)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.isAchievable").value(true))
                    .andExpect(jsonPath("$.yearsToFreedom").isNumber())
                    .andExpect(jsonPath("$.targetSavingsAmount").isNumber())
                    .andExpect(jsonPath("$.annualPassiveIncome").isNumber());
        }

        @Test
        @DisplayName("Should return achievable false when savings are insufficient")
        void shouldReturnNotAchievableWhenSavingsInsufficient() throws Exception {
            FreedomCalculatorRequest insufficientRequest =
                    FreedomCalculatorRequest.builder()
                            .currentSavings(new BigDecimal("1000"))
                            .monthlyExpenses(new BigDecimal("5000"))
                            .expectedAnnualReturn(new BigDecimal("3.0"))
                            .monthlyContribution(new BigDecimal("50"))
                            .withdrawalRate(new BigDecimal("4.0"))
                            .projectionYears(50)
                            .build();

            mockMvc.perform(
                            post("/api/v1/calculator/financial-freedom/timeline")
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(objectMapper.writeValueAsString(insufficientRequest)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.isAchievable").value(false));
        }

        @Test
        @DisplayName("Should return 400 for missing required fields")
        @SuppressWarnings("unchecked")
        void shouldReturnBadRequestForMissingFields() throws Exception {
            FreedomCalculatorRequest incompleteRequest =
                    FreedomCalculatorRequest.builder()
                            .currentSavings(new BigDecimal("50000"))
                            // Missing required fields
                            .build();

            mockMvc.perform(
                            post("/api/v1/calculator/financial-freedom/timeline")
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(objectMapper.writeValueAsString(incompleteRequest)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("Should return 400 for negative values")
        @SuppressWarnings("unchecked")
        void shouldReturnBadRequestForNegativeValues() throws Exception {
            FreedomCalculatorRequest negativeRequest =
                    FreedomCalculatorRequest.builder()
                            .currentSavings(new BigDecimal("-50000"))
                            .monthlyExpenses(new BigDecimal("2500"))
                            .expectedAnnualReturn(new BigDecimal("7.0"))
                            .monthlyContribution(new BigDecimal("500"))
                            .withdrawalRate(new BigDecimal("4.0"))
                            .projectionYears(30)
                            .build();

            mockMvc.perform(
                            post("/api/v1/calculator/financial-freedom/timeline")
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(objectMapper.writeValueAsString(negativeRequest)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("Should return projections array")
        void shouldReturnProjectionsArray() throws Exception {
            mockMvc.perform(
                            post("/api/v1/calculator/financial-freedom/timeline")
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(objectMapper.writeValueAsString(validRequest)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.projections").isArray())
                    .andExpect(jsonPath("$.projections", hasSize(greaterThan(0))))
                    .andExpect(jsonPath("$.projections[0].year").isNumber())
                    .andExpect(jsonPath("$.projections[0].endingBalance").isNumber())
                    .andExpect(jsonPath("$.projections[0].contributions").isNumber())
                    .andExpect(jsonPath("$.projections[0].investmentReturns").isNumber());
        }

        @Test
        @DisplayName("Should return sensitivity scenarios")
        void shouldReturnSensitivityScenarios() throws Exception {
            mockMvc.perform(
                            post("/api/v1/calculator/financial-freedom/timeline")
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(objectMapper.writeValueAsString(validRequest)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.sensitivityScenarios").isArray())
                    .andExpect(jsonPath("$.sensitivityScenarios", hasSize(greaterThan(0))));
        }
    }

    @Nested
    @DisplayName("POST /api/v1/calculator/financial-freedom/longevity")
    class LongevityEndpointTests {

        @Test
        @DisplayName("Should return 200 OK with valid request")
        void shouldReturnOkWithValidRequest() throws Exception {
            mockMvc.perform(
                            post("/api/v1/calculator/financial-freedom/longevity")
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(objectMapper.writeValueAsString(validRequest)))
                    .andExpect(status().isOk())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$.yearsUntilDepletion").isNumber())
                    .andExpect(jsonPath("$.isInfinite").isBoolean());
        }

        @Test
        @DisplayName("Should return isInfinite true when returns exceed expenses")
        void shouldReturnInfiniteWhenReturnsExceedExpenses() throws Exception {
            FreedomCalculatorRequest highReturnRequest =
                    FreedomCalculatorRequest.builder()
                            .currentSavings(new BigDecimal("1000000"))
                            .monthlyExpenses(new BigDecimal("1000"))
                            .expectedAnnualReturn(new BigDecimal("10.0"))
                            .monthlyContribution(BigDecimal.ZERO)
                            .withdrawalRate(new BigDecimal("4.0"))
                            .projectionYears(50)
                            .build();

            mockMvc.perform(
                            post("/api/v1/calculator/financial-freedom/longevity")
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(objectMapper.writeValueAsString(highReturnRequest)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.isInfinite").value(true));
        }

        @Test
        @DisplayName("Should calculate depletion year correctly")
        void shouldCalculateDepletionYearCorrectly() throws Exception {
            FreedomCalculatorRequest depletionRequest =
                    FreedomCalculatorRequest.builder()
                            .currentSavings(new BigDecimal("100000"))
                            .monthlyExpenses(new BigDecimal("2000"))
                            .expectedAnnualReturn(new BigDecimal("5.0"))
                            .monthlyContribution(BigDecimal.ZERO)
                            .withdrawalRate(new BigDecimal("4.0"))
                            .projectionYears(50)
                            .build();

            mockMvc.perform(
                            post("/api/v1/calculator/financial-freedom/longevity")
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(objectMapper.writeValueAsString(depletionRequest)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.isInfinite").value(false))
                    .andExpect(jsonPath("$.yearsUntilDepletion").isNumber())
                    .andExpect(jsonPath("$.depletionYear").isNumber())
                    .andExpect(jsonPath("$.depletionYear").value(greaterThan(0)));
        }
    }

    @Nested
    @DisplayName("GET /api/v1/calculator/financial-freedom/defaults")
    class DefaultsEndpointTests {

        @Test
        @DisplayName("Should return 200 OK")
        void shouldReturnOk() throws Exception {
            mockMvc.perform(get("/api/v1/calculator/financial-freedom/defaults"))
                    .andExpect(status().isOk())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON));
        }

        @Test
        @DisplayName("Should return default values")
        void shouldReturnDefaultValues() throws Exception {
            mockMvc.perform(get("/api/v1/calculator/financial-freedom/defaults"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.defaultWithdrawalRate").isNumber())
                    .andExpect(jsonPath("$.defaultInflationRate").isNumber())
                    .andExpect(jsonPath("$.defaultReturnRate").isNumber())
                    .andExpect(jsonPath("$.defaultProjectionYears").isNumber())
                    .andExpect(jsonPath("$.minimumWithdrawalRate").isNumber())
                    .andExpect(jsonPath("$.maximumWithdrawalRate").isNumber())
                    .andExpect(jsonPath("$.minimumReturnRate").isNumber())
                    .andExpect(jsonPath("$.maximumReturnRate").isNumber());
        }

        @Test
        @DisplayName("Should return reasonable default values")
        void shouldReturnReasonableDefaultValues() throws Exception {
            mockMvc.perform(get("/api/v1/calculator/financial-freedom/defaults"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.defaultWithdrawalRate").value(4.0))
                    .andExpect(jsonPath("$.defaultInflationRate").value(2.5))
                    .andExpect(jsonPath("$.defaultReturnRate").value(7.0))
                    .andExpect(jsonPath("$.defaultProjectionYears").value(30))
                    .andExpect(jsonPath("$.minimumWithdrawalRate").value(0.5))
                    .andExpect(jsonPath("$.maximumWithdrawalRate").value(10.0));
        }
    }

    @Nested
    @DisplayName("Edge Cases")
    class EdgeCaseTests {

        @Test
        @DisplayName("Should handle zero savings")
        void shouldHandleZeroSavings() throws Exception {
            FreedomCalculatorRequest zeroSavingsRequest =
                    FreedomCalculatorRequest.builder()
                            .currentSavings(BigDecimal.ZERO)
                            .monthlyExpenses(new BigDecimal("2000"))
                            .expectedAnnualReturn(new BigDecimal("7.0"))
                            .monthlyContribution(new BigDecimal("1000"))
                            .withdrawalRate(new BigDecimal("4.0"))
                            .projectionYears(50)
                            .build();

            mockMvc.perform(
                            post("/api/v1/calculator/financial-freedom/timeline")
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(objectMapper.writeValueAsString(zeroSavingsRequest)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.currentProgress").value(0));
        }

        @Test
        @DisplayName("Should handle zero monthly contribution")
        void shouldHandleZeroContribution() throws Exception {
            FreedomCalculatorRequest zeroContributionRequest =
                    FreedomCalculatorRequest.builder()
                            .currentSavings(new BigDecimal("500000"))
                            .monthlyExpenses(new BigDecimal("2000"))
                            .expectedAnnualReturn(new BigDecimal("7.0"))
                            .monthlyContribution(BigDecimal.ZERO)
                            .withdrawalRate(new BigDecimal("4.0"))
                            .projectionYears(30)
                            .build();

            mockMvc.perform(
                            post("/api/v1/calculator/financial-freedom/timeline")
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(
                                            objectMapper.writeValueAsString(
                                                    zeroContributionRequest)))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("Should handle extreme return rate")
        void shouldHandleExtremeReturnRate() throws Exception {
            FreedomCalculatorRequest extremeReturnRequest =
                    FreedomCalculatorRequest.builder()
                            .currentSavings(new BigDecimal("50000"))
                            .monthlyExpenses(new BigDecimal("2500"))
                            .expectedAnnualReturn(new BigDecimal("15.0"))
                            .monthlyContribution(new BigDecimal("500"))
                            .withdrawalRate(new BigDecimal("4.0"))
                            .projectionYears(30)
                            .build();

            mockMvc.perform(
                            post("/api/v1/calculator/financial-freedom/timeline")
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(objectMapper.writeValueAsString(extremeReturnRequest)))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("Should handle negative return rate")
        void shouldHandleNegativeReturnRate() throws Exception {
            FreedomCalculatorRequest negativeReturnRequest =
                    FreedomCalculatorRequest.builder()
                            .currentSavings(new BigDecimal("50000"))
                            .monthlyExpenses(new BigDecimal("2500"))
                            .expectedAnnualReturn(new BigDecimal("-2.0"))
                            .monthlyContribution(new BigDecimal("500"))
                            .withdrawalRate(new BigDecimal("4.0"))
                            .projectionYears(30)
                            .build();

            mockMvc.perform(
                            post("/api/v1/calculator/financial-freedom/timeline")
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(
                                            objectMapper.writeValueAsString(negativeReturnRequest)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.isAchievable").isBoolean());
        }

        @Test
        @DisplayName("Should handle very high withdrawal rate")
        void shouldHandleHighWithdrawalRate() throws Exception {
            FreedomCalculatorRequest highWithdrawalRequest =
                    FreedomCalculatorRequest.builder()
                            .currentSavings(new BigDecimal("500000"))
                            .monthlyExpenses(new BigDecimal("2500"))
                            .expectedAnnualReturn(new BigDecimal("7.0"))
                            .monthlyContribution(BigDecimal.ZERO)
                            .withdrawalRate(new BigDecimal("10.0"))
                            .projectionYears(30)
                            .build();

            mockMvc.perform(
                            post("/api/v1/calculator/financial-freedom/timeline")
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(
                                            objectMapper.writeValueAsString(highWithdrawalRequest)))
                    .andExpect(status().isOk());
        }
    }
}
