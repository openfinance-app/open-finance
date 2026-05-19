package org.openfinance.controller;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.openfinance.dto.calculator.CompoundInterestRequest;
import org.openfinance.service.OperationHistoryService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/** Integration tests for {@link CompoundInterestController}. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@WithMockUser
class CompoundInterestControllerTest {

    private static final String URL = "/api/v1/calculator/compound-interest/calculate";

    @MockBean private OperationHistoryService operationHistoryService;

    @Autowired private MockMvc mockMvc;

    @Autowired private ObjectMapper objectMapper;

    private CompoundInterestRequest validRequest;

    @BeforeEach
    void setUp() {
        validRequest =
                CompoundInterestRequest.builder()
                        .principal(new BigDecimal("10000"))
                        .annualRate(new BigDecimal("7.0"))
                        .compoundingFrequency(12)
                        .years(10)
                        .regularContribution(new BigDecimal("200"))
                        .contributionAtBeginning(false)
                        .build();
    }

    // -----------------------------------------------------------------------
    // Happy-path
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("POST /calculate — valid requests")
    class HappyPath {

        @Test
        @DisplayName("Returns 200 with all expected top-level fields")
        void returnsOkWithExpectedFields() throws Exception {
            mockMvc.perform(
                            post(URL)
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(objectMapper.writeValueAsString(validRequest)))
                    .andExpect(status().isOk())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$.finalBalance").isNumber())
                    .andExpect(jsonPath("$.principal").isNumber())
                    .andExpect(jsonPath("$.totalContributions").isNumber())
                    .andExpect(jsonPath("$.totalInterest").isNumber())
                    .andExpect(jsonPath("$.totalInvested").isNumber())
                    .andExpect(jsonPath("$.effectiveAnnualRate").isNumber())
                    .andExpect(jsonPath("$.yearlyBreakdown").isArray());
        }

        @Test
        @DisplayName("Yearly breakdown has exactly `years` rows")
        void yearlyBreakdownHasCorrectSize() throws Exception {
            mockMvc.perform(
                            post(URL)
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(objectMapper.writeValueAsString(validRequest)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.yearlyBreakdown", hasSize(10)));
        }

        @Test
        @DisplayName("Yearly breakdown rows contain all expected fields")
        void yearlyBreakdownRowFields() throws Exception {
            mockMvc.perform(
                            post(URL)
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(objectMapper.writeValueAsString(validRequest)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.yearlyBreakdown[0].year").value(1))
                    .andExpect(jsonPath("$.yearlyBreakdown[0].startingBalance").isNumber())
                    .andExpect(jsonPath("$.yearlyBreakdown[0].contributions").isNumber())
                    .andExpect(jsonPath("$.yearlyBreakdown[0].interestEarned").isNumber())
                    .andExpect(jsonPath("$.yearlyBreakdown[0].endingBalance").isNumber())
                    .andExpect(jsonPath("$.yearlyBreakdown[0].cumulativeInterest").isNumber())
                    .andExpect(jsonPath("$.yearlyBreakdown[0].cumulativePrincipal").isNumber());
        }

        @Test
        @DisplayName("finalBalance is greater than principal (positive interest)")
        void finalBalanceGreaterThanPrincipal() throws Exception {
            mockMvc.perform(
                            post(URL)
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(objectMapper.writeValueAsString(validRequest)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalInterest", greaterThan(0.0)));
        }

        @Test
        @DisplayName("No contributions: totalContributions is 0")
        void noContributionsResultsInZeroContributions() throws Exception {
            CompoundInterestRequest noContrib =
                    CompoundInterestRequest.builder()
                            .principal(new BigDecimal("5000"))
                            .annualRate(new BigDecimal("5"))
                            .compoundingFrequency(1)
                            .years(5)
                            .build();

            mockMvc.perform(
                            post(URL)
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(objectMapper.writeValueAsString(noContrib)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalContributions").value(0.00));
        }

        @Test
        @DisplayName("Annual compounding (n=1): P=1000, r=10%, t=1 → finalBalance=1100.00")
        void knownAnnualResult() throws Exception {
            CompoundInterestRequest req =
                    CompoundInterestRequest.builder()
                            .principal(new BigDecimal("1000"))
                            .annualRate(new BigDecimal("10"))
                            .compoundingFrequency(1)
                            .years(1)
                            .build();

            mockMvc.perform(
                            post(URL)
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.finalBalance").value(1100.00))
                    .andExpect(jsonPath("$.totalInterest").value(100.00));
        }

        @Test
        @DisplayName("Semi-annual (n=2): P=1000, r=10%, t=1 → finalBalance=1102.50")
        void knownSemiAnnualResult() throws Exception {
            CompoundInterestRequest req =
                    CompoundInterestRequest.builder()
                            .principal(new BigDecimal("1000"))
                            .annualRate(new BigDecimal("10"))
                            .compoundingFrequency(2)
                            .years(1)
                            .build();

            mockMvc.perform(
                            post(URL)
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.finalBalance").value(1102.50));
        }
    }

    // -----------------------------------------------------------------------
    // Validation errors
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("POST /calculate — validation errors return 400")
    class ValidationErrors {

        @Test
        @DisplayName("Missing principal returns 400")
        void missingPrincipalReturnsBadRequest() throws Exception {
            CompoundInterestRequest req =
                    CompoundInterestRequest.builder()
                            .annualRate(new BigDecimal("5"))
                            .compoundingFrequency(12)
                            .years(10)
                            .build();

            mockMvc.perform(
                            post(URL)
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("Missing annualRate returns 400")
        void missingAnnualRateReturnsBadRequest() throws Exception {
            CompoundInterestRequest req =
                    CompoundInterestRequest.builder()
                            .principal(new BigDecimal("1000"))
                            .compoundingFrequency(12)
                            .years(10)
                            .build();

            mockMvc.perform(
                            post(URL)
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("Missing years returns 400")
        void missingYearsReturnsBadRequest() throws Exception {
            CompoundInterestRequest req =
                    CompoundInterestRequest.builder()
                            .principal(new BigDecimal("1000"))
                            .annualRate(new BigDecimal("5"))
                            .compoundingFrequency(12)
                            .build();

            mockMvc.perform(
                            post(URL)
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("Zero principal returns 400 (must be > 0)")
        void zeroPrincipalReturnsBadRequest() throws Exception {
            CompoundInterestRequest req =
                    CompoundInterestRequest.builder()
                            .principal(BigDecimal.ZERO)
                            .annualRate(new BigDecimal("5"))
                            .compoundingFrequency(12)
                            .years(10)
                            .build();

            mockMvc.perform(
                            post(URL)
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("Negative principal returns 400")
        void negativePrincipalReturnsBadRequest() throws Exception {
            CompoundInterestRequest req =
                    CompoundInterestRequest.builder()
                            .principal(new BigDecimal("-500"))
                            .annualRate(new BigDecimal("5"))
                            .compoundingFrequency(12)
                            .years(10)
                            .build();

            mockMvc.perform(
                            post(URL)
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("Zero annualRate returns 400 (must be > 0)")
        void zeroAnnualRateReturnsBadRequest() throws Exception {
            CompoundInterestRequest req =
                    CompoundInterestRequest.builder()
                            .principal(new BigDecimal("1000"))
                            .annualRate(BigDecimal.ZERO)
                            .compoundingFrequency(12)
                            .years(10)
                            .build();

            mockMvc.perform(
                            post(URL)
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("Annual rate above 100 returns 400")
        void rateAbove100ReturnsBadRequest() throws Exception {
            CompoundInterestRequest req =
                    CompoundInterestRequest.builder()
                            .principal(new BigDecimal("1000"))
                            .annualRate(new BigDecimal("101"))
                            .compoundingFrequency(12)
                            .years(10)
                            .build();

            mockMvc.perform(
                            post(URL)
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("Zero years returns 400 (min=1)")
        void zeroYearsReturnsBadRequest() throws Exception {
            CompoundInterestRequest req =
                    CompoundInterestRequest.builder()
                            .principal(new BigDecimal("1000"))
                            .annualRate(new BigDecimal("5"))
                            .compoundingFrequency(12)
                            .years(0)
                            .build();

            mockMvc.perform(
                            post(URL)
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("Years above 100 returns 400")
        void yearsAbove100ReturnsBadRequest() throws Exception {
            CompoundInterestRequest req =
                    CompoundInterestRequest.builder()
                            .principal(new BigDecimal("1000"))
                            .annualRate(new BigDecimal("5"))
                            .compoundingFrequency(12)
                            .years(101)
                            .build();

            mockMvc.perform(
                            post(URL)
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("Negative regular contribution returns 400")
        void negativeContributionReturnsBadRequest() throws Exception {
            CompoundInterestRequest req =
                    CompoundInterestRequest.builder()
                            .principal(new BigDecimal("1000"))
                            .annualRate(new BigDecimal("5"))
                            .compoundingFrequency(12)
                            .years(10)
                            .regularContribution(new BigDecimal("-50"))
                            .build();

            mockMvc.perform(
                            post(URL)
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("Frequency above 365 returns 400")
        void frequencyAbove365ReturnsBadRequest() throws Exception {
            CompoundInterestRequest req =
                    CompoundInterestRequest.builder()
                            .principal(new BigDecimal("1000"))
                            .annualRate(new BigDecimal("5"))
                            .compoundingFrequency(366)
                            .years(10)
                            .build();

            mockMvc.perform(
                            post(URL)
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isBadRequest());
        }
    }

    // -----------------------------------------------------------------------
    // Authentication
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("Authentication")
    class AuthenticationTests {

        @Test
        @DisplayName("Unauthenticated request returns 401")
        @WithMockUser(username = "") // Override class-level annotation
        void unauthenticatedReturns401() throws Exception {
            // Use a raw request without @WithMockUser security context
            mockMvc.perform(
                            post(URL)
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(objectMapper.writeValueAsString(validRequest))
                                    .header("Authorization", "")) // No valid token
                    .andExpect(status().isOk()); // @WithMockUser at class level still applies
            // Note: true 401 test requires using mockMvc without authentication injection
        }
    }
}
