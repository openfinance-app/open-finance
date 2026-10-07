package org.openfinance.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.openfinance.config.TestDatabaseConfig;
import org.openfinance.dto.LiabilityRequest;
import org.openfinance.dto.LoginRequest;
import org.openfinance.dto.UserRegistrationRequest;
import org.openfinance.entity.LiabilityType;
import org.openfinance.service.OperationHistoryService;
import org.openfinance.service.UserService;
import org.openfinance.util.DatabaseCleanupService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * Integration tests for liability disbursement and tranche endpoints (Task 4).
 *
 * <p>Covers POST /api/v1/liabilities/{id}/disburse (to-account and direct-to-property routes), GET
 * /api/v1/liabilities/{id}/tranches, POST /api/v1/liabilities/{id}/tranches and PATCH
 * /api/v1/tranches/{trancheId}.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestDatabaseConfig.class)
@ActiveProfiles("test")
@DisplayName("Liability disbursement and tranche API tests")
class LiabilityDisburseApiTest {

    @MockBean private OperationHistoryService operationHistoryService;

    @Autowired private MockMvc mockMvc;

    @Autowired private ObjectMapper objectMapper;

    @Autowired private UserService userService;

    @Autowired private DatabaseCleanupService databaseCleanupService;

    private String token;
    private String encKey;

    @BeforeEach
    void setUp() throws Exception {
        databaseCleanupService.execute();

        Session alice = registerAndLogin("alice");
        token = alice.token();
        encKey = alice.encKey();
    }

    /** Auth material for a registered-and-logged-in user. */
    private record Session(String token, String encKey) {}

    private Session registerAndLogin(String username) throws Exception {
        UserRegistrationRequest reg =
                UserRegistrationRequest.builder()
                        .username(username)
                        .email(username + "@example.com")
                        .password("Password123!")
                        .masterPassword("Master123!")
                        .skipSeeding(true)
                        .build();
        userService.registerUser(reg);

        LoginRequest login =
                LoginRequest.builder()
                        .username(username)
                        .password("Password123!")
                        .masterPassword("Master123!")
                        .build();

        String resp =
                mockMvc.perform(
                                post("/api/v1/auth/login")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(objectMapper.writeValueAsString(login)))
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();

        return new Session(
                objectMapper.readTree(resp).get("token").asText(),
                objectMapper.readTree(resp).get("encryptionKey").asText());
    }

    // ---------- Helpers ----------

    private String performPost(String path, Object body) throws Exception {
        return mockMvc.perform(
                        post(path)
                                .header("Authorization", "Bearer " + token)
                                .header("X-Encryption-Session", encKey)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
    }

    /** Creates a staged construction loan starting at a zero balance. */
    private Long createStagedLiability() throws Exception {
        return createLiabilityWithBalance(BigDecimal.ZERO);
    }

    /** Creates a staged construction loan with the given starting balance. */
    private Long createLiabilityWithBalance(BigDecimal currentBalance) throws Exception {
        LiabilityRequest request = new LiabilityRequest();
        request.setName("Construction Loan");
        request.setType(LiabilityType.LOAN);
        request.setPrincipal(new BigDecimal("200000.00"));
        request.setCurrentBalance(currentBalance);
        request.setInterestRate(new BigDecimal("4.5"));
        request.setStartDate(LocalDate.now().minusMonths(1));
        request.setCurrency("USD");
        String resp = performPost("/api/v1/liabilities", request);
        return objectMapper.readTree(resp).get("id").asLong();
    }

    private Long createAccount(String currency) throws Exception {
        Map<String, Object> body =
                Map.of(
                        "name",
                        "Disbursement Account",
                        "type",
                        "CHECKING",
                        "currency",
                        currency,
                        "initialBalance",
                        new BigDecimal("1000.00"));
        String resp = performPost("/api/v1/accounts", body);
        return objectMapper.readTree(resp).get("id").asLong();
    }

    private Long createProperty() throws Exception {
        return createProperty("USD");
    }

    private Long createProperty(String currency) throws Exception {
        String resp = performPost("/api/v1/real-estate", propertyBody(currency));
        return objectMapper.readTree(resp).get("id").asLong();
    }

    /** Creates a property owned by the given session's user (not alice). */
    private Long createPropertyFor(Session session) throws Exception {
        String resp =
                mockMvc.perform(
                                post("/api/v1/real-estate")
                                        .header("Authorization", "Bearer " + session.token())
                                        .header("X-Encryption-Session", session.encKey())
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(
                                                objectMapper.writeValueAsString(
                                                        propertyBody("USD"))))
                        .andExpect(status().isCreated())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        return objectMapper.readTree(resp).get("id").asLong();
    }

    private Map<String, Object> propertyBody(String currency) {
        return Map.of(
                "name",
                "Build Site",
                "propertyType",
                "RESIDENTIAL",
                "address",
                "1 Main St",
                "purchasePrice",
                new BigDecimal("100000.00"),
                "currentValue",
                new BigDecimal("100000.00"),
                "purchaseDate",
                LocalDate.now().minusMonths(2).toString(),
                "currency",
                currency);
    }

    private Long createPlannedTranche(Long liabilityId, String plannedAmount) throws Exception {
        Map<String, Object> body =
                Map.of("plannedAmount", new BigDecimal(plannedAmount), "currency", "USD");
        String resp = performPost("/api/v1/liabilities/" + liabilityId + "/tranches", body);
        return objectMapper.readTree(resp).get("id").asLong();
    }

    private String disburse(Long liabilityId, Map<String, Object> body) throws Exception {
        return mockMvc.perform(
                        post("/api/v1/liabilities/" + liabilityId + "/disburse")
                                .header("Authorization", "Bearer " + token)
                                .header("X-Encryption-Session", encKey)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
    }

    private Map<String, Object> disbursementBody(BigDecimal amount) {
        Map<String, Object> body = new HashMap<>();
        body.put("amount", amount);
        body.put("date", LocalDate.now().toString());
        return body;
    }

    /** PATCHes as alice, expecting HTTP 200, and returns the response body. */
    private String performPatch(String path, Object body) throws Exception {
        return mockMvc.perform(
                        patch(path)
                                .header("Authorization", "Bearer " + token)
                                .header("X-Encryption-Session", encKey)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
    }

    private ResultActions getTranches(Long liabilityId) throws Exception {
        return mockMvc.perform(
                get("/api/v1/liabilities/" + liabilityId + "/tranches")
                        .header("Authorization", "Bearer " + token)
                        .header("X-Encryption-Session", encKey));
    }

    // ---------- POST /{id}/disburse: to-account route ----------

    @Test
    @DisplayName(
            "Disbursement to account creates an INCOME DISBURSEMENT transaction, bumps liability "
                    + "and account, and marks the tranche DRAWN")
    void disburseToAccountCreatesTransactionAndMarksTrancheDrawn() throws Exception {
        Long liabilityId = createStagedLiability();
        Long accountId = createAccount("USD");
        Long trancheId = createPlannedTranche(liabilityId, "50000.00");

        Map<String, Object> body = disbursementBody(new BigDecimal("40000.00"));
        body.put("trancheId", trancheId);
        body.put("toAccountId", accountId);
        String resp = disburse(liabilityId, body);

        // Liability balance bumped from 0 to 40000 exactly once (no double count)
        assertThatBalanceIs(liabilityId, resp, "40000.0");

        // Tranche is DRAWN with the drawn amount
        mockMvc.perform(
                        get("/api/v1/liabilities/" + liabilityId + "/tranches")
                                .header("Authorization", "Bearer " + token)
                                .header("X-Encryption-Session", encKey))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(trancheId))
                .andExpect(jsonPath("$[0].status").value("DRAWN"))
                .andExpect(jsonPath("$[0].drawnAmount").value(40000.00))
                .andExpect(jsonPath("$[0].drawnDate").exists())
                .andExpect(jsonPath("$[0].currency").value("USD"));

        // Account received the funds
        mockMvc.perform(
                        get("/api/v1/accounts/" + accountId)
                                .header("Authorization", "Bearer " + token)
                                .header("X-Encryption-Session", encKey))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balance").value(41000.00));

        // Exactly one linked transaction was recorded
        mockMvc.perform(
                        get("/api/v1/liabilities/" + liabilityId + "/transactions")
                                .header("Authorization", "Bearer " + token)
                                .header("X-Encryption-Session", encKey))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].movementType").value("DISBURSEMENT"))
                .andExpect(jsonPath("$[0].amount").value(40000.00));
    }

    // ---------- POST /{id}/disburse: direct-to-property route ----------

    @Test
    @DisplayName(
            "Direct disbursement increases debt while preserving property value, auto-creates a "
                    + "DRAWN tranche and records no transaction")
    void directDisbursementFundsLiabilityAndPreservesPropertyWithoutTransaction() throws Exception {
        Long liabilityId = createStagedLiability();
        Long propertyId = createProperty();

        Map<String, Object> body = disbursementBody(new BigDecimal("30000.00"));
        body.put("directRealEstateId", propertyId);
        String resp = disburse(liabilityId, body);

        assertThatBalanceIs(liabilityId, resp, "30000.0");

        // Auto-created T1 tranche is DRAWN and linked to the property
        mockMvc.perform(
                        get("/api/v1/liabilities/" + liabilityId + "/tranches")
                                .header("Authorization", "Bearer " + token)
                                .header("X-Encryption-Session", encKey))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].trancheNo").value(1))
                .andExpect(jsonPath("$[0].status").value("DRAWN"))
                .andExpect(jsonPath("$[0].drawnAmount").value(30000.00))
                .andExpect(jsonPath("$[0].realEstateId").value(propertyId));

        // No account leg: no transactions recorded against the liability
        mockMvc.perform(
                        get("/api/v1/liabilities/" + liabilityId + "/transactions")
                                .header("Authorization", "Bearer " + token)
                                .header("X-Encryption-Session", encKey))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));

        // Financing is independent of the purchase price and current valuation.
        mockMvc.perform(
                        get("/api/v1/real-estate/" + propertyId)
                                .header("Authorization", "Bearer " + token)
                                .header("X-Encryption-Session", encKey))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentValue").value(100000.00))
                .andExpect(jsonPath("$.purchasePrice").value(100000.00));
    }

    // ---------- direct-path guards ----------

    @Test
    @DisplayName(
            "Direct disbursement to a property whose currency differs from the liability is rejected")
    void directDisbursementCurrencyMismatchIsRejected() throws Exception {
        Long liabilityId = createStagedLiability();
        Long propertyId = createProperty("EUR");

        Map<String, Object> body = disbursementBody(new BigDecimal("1000.00"));
        body.put("directRealEstateId", propertyId);

        mockMvc.perform(
                        post("/api/v1/liabilities/" + liabilityId + "/disburse")
                                .header("Authorization", "Bearer " + token)
                                .header("X-Encryption-Session", encKey)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Disbursement exceeding the tranche's planned amount is rejected without mutation")
    void disbursementExceedingPlannedAmountIsRejected() throws Exception {
        Long liabilityId = createStagedLiability();
        Long propertyId = createProperty();
        Long trancheId = createPlannedTranche(liabilityId, "50000.00");

        Map<String, Object> body = disbursementBody(new BigDecimal("60000.00"));
        body.put("trancheId", trancheId);
        body.put("directRealEstateId", propertyId);

        mockMvc.perform(
                        post("/api/v1/liabilities/" + liabilityId + "/disburse")
                                .header("Authorization", "Bearer " + token)
                                .header("X-Encryption-Session", encKey)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isConflict());

        // The tranche is untouched: still PLANNED with no drawn fields
        mockMvc.perform(
                        get("/api/v1/liabilities/" + liabilityId + "/tranches")
                                .header("Authorization", "Bearer " + token)
                                .header("X-Encryption-Session", encKey))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].status").value("PLANNED"))
                .andExpect(jsonPath("$[0].drawnAmount").doesNotExist());
    }

    @Test
    @DisplayName("Disbursement preserves existing debt when adding the first tranche")
    void disbursementPreservesOpeningDebt() throws Exception {
        Long liabilityId = createLiabilityWithBalance(new BigDecimal("200000.00"));
        Long propertyId = createProperty();

        Map<String, Object> body = disbursementBody(new BigDecimal("40000.00"));
        body.put("directRealEstateId", propertyId);

        mockMvc.perform(
                        post("/api/v1/liabilities/" + liabilityId + "/disburse")
                                .header("Authorization", "Bearer " + token)
                                .header("X-Encryption-Session", encKey)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk());

        // Opening debt is retained and the new draw adds one tranche.
        String getResp =
                mockMvc.perform(
                                get("/api/v1/liabilities/" + liabilityId)
                                        .header("Authorization", "Bearer " + token)
                                        .header("X-Encryption-Session", encKey))
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        String persistedBalance = objectMapper.readTree(getResp).get("currentBalance").asText();
        assertThat(new BigDecimal(persistedBalance).compareTo(new BigDecimal("240000.00")))
                .isZero();
        mockMvc.perform(
                        get("/api/v1/liabilities/" + liabilityId + "/tranches")
                                .header("Authorization", "Bearer " + token)
                                .header("X-Encryption-Session", encKey))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }

    // ---------- route validation ----------

    @Test
    @DisplayName("Disbursement with both toAccountId and directRealEstateId is rejected")
    void disbursementWithBothRoutesIsRejected() throws Exception {
        Long liabilityId = createStagedLiability();
        Long accountId = createAccount("USD");
        Long propertyId = createProperty();

        Map<String, Object> body = disbursementBody(new BigDecimal("1000.00"));
        body.put("toAccountId", accountId);
        body.put("directRealEstateId", propertyId);

        mockMvc.perform(
                        post("/api/v1/liabilities/" + liabilityId + "/disburse")
                                .header("Authorization", "Bearer " + token)
                                .header("X-Encryption-Session", encKey)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Disbursement with neither toAccountId nor directRealEstateId is rejected")
    void disbursementWithNeitherRouteIsRejected() throws Exception {
        Long liabilityId = createStagedLiability();
        Long trancheId = createPlannedTranche(liabilityId, "50000.00");

        Map<String, Object> body = disbursementBody(new BigDecimal("1000.00"));
        body.put("trancheId", trancheId);

        mockMvc.perform(
                        post("/api/v1/liabilities/" + liabilityId + "/disburse")
                                .header("Authorization", "Bearer " + token)
                                .header("X-Encryption-Session", encKey)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Disbursement to an account whose currency differs from the liability is rejected")
    void disbursementWithCurrencyMismatchIsRejected() throws Exception {
        Long liabilityId = createStagedLiability();
        Long accountId = createAccount("EUR");
        Long trancheId = createPlannedTranche(liabilityId, "50000.00");

        Map<String, Object> body = disbursementBody(new BigDecimal("1000.00"));
        body.put("trancheId", trancheId);
        body.put("toAccountId", accountId);

        mockMvc.perform(
                        post("/api/v1/liabilities/" + liabilityId + "/disburse")
                                .header("Authorization", "Bearer " + token)
                                .header("X-Encryption-Session", encKey)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest());
    }

    // ---------- tranches ----------

    @Test
    @DisplayName("POST tranches auto-assigns trancheNo and takes the currency from the liability")
    void createTrancheAutoAssignsNumberAndCurrency() throws Exception {
        Long liabilityId = createStagedLiability();

        createPlannedTranche(liabilityId, "50000.00");
        createPlannedTranche(liabilityId, "40000.00");

        mockMvc.perform(
                        get("/api/v1/liabilities/" + liabilityId + "/tranches")
                                .header("Authorization", "Bearer " + token)
                                .header("X-Encryption-Session", encKey))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].trancheNo").value(1))
                .andExpect(jsonPath("$[1].trancheNo").value(2))
                .andExpect(jsonPath("$[0].status").value("PLANNED"))
                .andExpect(jsonPath("$[0].currency").value("USD"))
                .andExpect(jsonPath("$[1].plannedAmount").value(40000.00));
    }

    @Test
    @DisplayName("PATCH tranches updates planned fields of a PLANNED tranche")
    void patchUpdatesPlannedTrancheFields() throws Exception {
        Long liabilityId = createStagedLiability();
        Long trancheId = createPlannedTranche(liabilityId, "50000.00");

        Map<String, Object> body =
                Map.of(
                        "plannedAmount",
                        new BigDecimal("45000.00"),
                        "fee",
                        new BigDecimal("250.00"),
                        "interestOnly",
                        true,
                        "notes",
                        "Foundation work",
                        "currency",
                        "USD");

        mockMvc.perform(
                        patch("/api/v1/tranches/" + trancheId)
                                .header("Authorization", "Bearer " + token)
                                .header("X-Encryption-Session", encKey)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.plannedAmount").value(45000.00))
                .andExpect(jsonPath("$.fee").value(250.00))
                .andExpect(jsonPath("$.interestOnly").value(true))
                .andExpect(jsonPath("$.notes").value("Foundation work"));
    }

    @Test
    @DisplayName("PATCH tranches toggles a PLANNED tranche to CANCELLED and back")
    void patchTogglesPlannedCancelled() throws Exception {
        Long liabilityId = createStagedLiability();
        Long trancheId = createPlannedTranche(liabilityId, "50000.00");

        Map<String, Object> cancel =
                Map.of("plannedAmount", new BigDecimal("50000.00"), "status", "CANCELLED");
        mockMvc.perform(
                        patch("/api/v1/tranches/" + trancheId)
                                .header("Authorization", "Bearer " + token)
                                .header("X-Encryption-Session", encKey)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(cancel)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));

        Map<String, Object> replan =
                Map.of("plannedAmount", new BigDecimal("50000.00"), "status", "PLANNED");
        mockMvc.perform(
                        patch("/api/v1/tranches/" + trancheId)
                                .header("Authorization", "Bearer " + token)
                                .header("X-Encryption-Session", encKey)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(replan)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PLANNED"));
    }

    @Test
    @DisplayName("PATCH tranches rejects changing planned fields of a DRAWN tranche")
    void patchRejectsPlannedFieldChangesOnDrawnTranche() throws Exception {
        Long liabilityId = createStagedLiability();
        Long accountId = createAccount("USD");
        Long trancheId = createPlannedTranche(liabilityId, "50000.00");

        Map<String, Object> body = disbursementBody(new BigDecimal("40000.00"));
        body.put("trancheId", trancheId);
        body.put("toAccountId", accountId);
        disburse(liabilityId, body);

        Map<String, Object> plannedChange =
                Map.of("plannedAmount", new BigDecimal("60000.00"), "currency", "USD");
        mockMvc.perform(
                        patch("/api/v1/tranches/" + trancheId)
                                .header("Authorization", "Bearer " + token)
                                .header("X-Encryption-Session", encKey)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(plannedChange)))
                .andExpect(status().isBadRequest());

        // notes stay editable on a DRAWN tranche
        Map<String, Object> notesChange =
                Map.of("plannedAmount", new BigDecimal("50000.00"), "notes", "Drawn memo");
        mockMvc.perform(
                        patch("/api/v1/tranches/" + trancheId)
                                .header("Authorization", "Bearer " + token)
                                .header("X-Encryption-Session", encKey)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(notesChange)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.notes").value("Drawn memo"))
                .andExpect(jsonPath("$.plannedAmount").value(50000.00));
    }

    // ---------- PATCH null-safety ----------

    @Test
    @DisplayName("PATCH with {plannedAmount, status} preserves plannedDate/fee/notes")
    void patchWithAmountAndStatusPreservesUnsetFields() throws Exception {
        Long liabilityId = createStagedLiability();

        Map<String, Object> create =
                Map.of(
                        "plannedAmount",
                        new BigDecimal("50000.00"),
                        "plannedDate",
                        "2026-10-01",
                        "fee",
                        new BigDecimal("300.00"),
                        "notes",
                        "Groundworks",
                        "currency",
                        "USD");
        String created = performPost("/api/v1/liabilities/" + liabilityId + "/tranches", create);
        Long trancheId = objectMapper.readTree(created).get("id").asLong();

        Map<String, Object> patch =
                Map.of("plannedAmount", new BigDecimal("45000.00"), "status", "CANCELLED");
        performPatch("/api/v1/tranches/" + trancheId, patch);

        getTranches(liabilityId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].plannedAmount").value(45000.00))
                .andExpect(jsonPath("$[0].status").value("CANCELLED"))
                .andExpect(jsonPath("$[0].plannedDate").value("2026-10-01"))
                .andExpect(jsonPath("$[0].fee").value(300.00))
                .andExpect(jsonPath("$[0].notes").value("Groundworks"));
    }

    @Test
    @DisplayName("Notes-only PATCH on a DRAWN tranche preserves the real estate link")
    void notesOnlyPatchOnDrawnTranchePreservesRealEstateLink() throws Exception {
        Long liabilityId = createStagedLiability();
        Long propertyId = createProperty();

        Map<String, Object> body = disbursementBody(new BigDecimal("30000.00"));
        body.put("directRealEstateId", propertyId);
        disburse(liabilityId, body);

        String list =
                getTranches(liabilityId)
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        Long trancheId = objectMapper.readTree(list).get(0).get("id").asLong();

        Map<String, Object> patch = Map.of("notes", "Phase 1 complete");
        performPatch("/api/v1/tranches/" + trancheId, patch);

        getTranches(liabilityId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].status").value("DRAWN"))
                .andExpect(jsonPath("$[0].notes").value("Phase 1 complete"))
                .andExpect(jsonPath("$[0].realEstateId").value(propertyId))
                .andExpect(jsonPath("$[0].plannedAmount").value(30000.00));
    }

    // ---------- realEstateId ownership ----------

    @Test
    @DisplayName("Creating a tranche linked to another user's property is rejected")
    void createTrancheWithForeignPropertyIsRejected() throws Exception {
        Long liabilityId = createStagedLiability();
        Session bob = registerAndLogin("bob");
        Long bobPropertyId = createPropertyFor(bob);

        Map<String, Object> body =
                Map.of("plannedAmount", new BigDecimal("50000.00"), "realEstateId", bobPropertyId);
        mockMvc.perform(
                        post("/api/v1/liabilities/" + liabilityId + "/tranches")
                                .header("Authorization", "Bearer " + token)
                                .header("X-Encryption-Session", encKey)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isNotFound());

        // No cross-user link: no tranche was created
        getTranches(liabilityId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    @DisplayName("Linking a tranche to another user's property via PATCH is rejected")
    void patchTrancheWithForeignPropertyIsRejected() throws Exception {
        Long liabilityId = createStagedLiability();
        Long trancheId = createPlannedTranche(liabilityId, "50000.00");
        Session bob = registerAndLogin("bob");
        Long bobPropertyId = createPropertyFor(bob);

        Map<String, Object> patch = Map.of("realEstateId", bobPropertyId);
        mockMvc.perform(
                        patch("/api/v1/tranches/" + trancheId)
                                .header("Authorization", "Bearer " + token)
                                .header("X-Encryption-Session", encKey)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(patch)))
                .andExpect(status().isNotFound());

        // The tranche is untouched: no real estate link
        getTranches(liabilityId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].realEstateId").doesNotExist());
    }

    // ---------- GET /{id}/repayment-preview ----------

    @Test
    @DisplayName("Repayment preview splits a total into interest and principal")
    void repaymentPreviewSplitsTotal() throws Exception {
        // balance 50000, rate 4.5 → monthly interest = 50000 × 4.5 / 1200 = 187.50
        Long liabilityId = createLiabilityWithBalance(new BigDecimal("50000.00"));

        mockMvc.perform(
                        get("/api/v1/liabilities/" + liabilityId + "/repayment-preview")
                                .param("total", "1200.00")
                                .param("date", LocalDate.now().toString())
                                .header("Authorization", "Bearer " + token)
                                .header("X-Encryption-Session", encKey))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1200.00))
                .andExpect(jsonPath("$.interest").value(187.50))
                .andExpect(jsonPath("$.insurance").value(0.00))
                .andExpect(jsonPath("$.principal").value(1012.50))
                .andExpect(jsonPath("$.interestOnly").value(false));
    }

    @Test
    @DisplayName("Repayment preview of another user's liability is rejected")
    void repaymentPreviewOfForeignLiabilityIsRejected() throws Exception {
        Long liabilityId = createLiabilityWithBalance(new BigDecimal("50000.00"));
        Session bob = registerAndLogin("bob");

        mockMvc.perform(
                        get("/api/v1/liabilities/" + liabilityId + "/repayment-preview")
                                .param("total", "1200.00")
                                .param("date", LocalDate.now().toString())
                                .header("Authorization", "Bearer " + bob.token())
                                .header("X-Encryption-Session", bob.encKey()))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Repayment preview with total = 0 is rejected with 400")
    void repaymentPreviewRejectsZeroTotal() throws Exception {
        Long liabilityId = createLiabilityWithBalance(new BigDecimal("50000.00"));

        mockMvc.perform(
                        get("/api/v1/liabilities/" + liabilityId + "/repayment-preview")
                                .param("total", "0")
                                .param("date", LocalDate.now().toString())
                                .header("Authorization", "Bearer " + token)
                                .header("X-Encryption-Session", encKey))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Repayment preview with more than 18 decimal places is rejected with 400")
    void repaymentPreviewRejectsTooManyDecimals() throws Exception {
        Long liabilityId = createLiabilityWithBalance(new BigDecimal("50000.00"));

        mockMvc.perform(
                        get("/api/v1/liabilities/" + liabilityId + "/repayment-preview")
                                .param("total", "1200.1234567890123456789")
                                .param("date", LocalDate.now().toString())
                                .header("Authorization", "Bearer " + token)
                                .header("X-Encryption-Session", encKey))
                .andExpect(status().isBadRequest());
    }

    @Test
    void repaymentPreviewAcceptsKuwaitiDinarMinorUnits() throws Exception {
        LiabilityRequest request = new LiabilityRequest();
        request.setName("Dinar loan");
        request.setType(LiabilityType.LOAN);
        request.setPrincipal(new BigDecimal("12.345"));
        request.setCurrentBalance(new BigDecimal("12.345"));
        request.setInterestRate(new BigDecimal("1.2"));
        request.setStartDate(LocalDate.now().minusMonths(1));
        request.setCurrency("KWD");
        long id =
                objectMapper
                        .readTree(performPost("/api/v1/liabilities", request))
                        .get("id")
                        .asLong();
        mockMvc.perform(
                        get("/api/v1/liabilities/" + id + "/repayment-preview")
                                .param("total", "1.234")
                                .param("date", LocalDate.now().toString())
                                .header("Authorization", "Bearer " + token)
                                .header("X-Encryption-Session", encKey))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.principal").value(1.222))
                .andExpect(jsonPath("$.interest").value(0.012));
    }

    // ---------- assertion helper ----------

    private void assertThatBalanceIs(Long liabilityId, String disburseResponse, String expected)
            throws Exception {
        // The response body itself must carry the bumped balance...
        String responseBalance =
                objectMapper.readTree(disburseResponse).get("currentBalance").asText();
        assertThat(new BigDecimal(responseBalance).compareTo(new BigDecimal(expected))).isZero();

        // ...and the persisted liability must agree (no double count)
        String getResp =
                mockMvc.perform(
                                get("/api/v1/liabilities/" + liabilityId)
                                        .header("Authorization", "Bearer " + token)
                                        .header("X-Encryption-Session", encKey))
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        String persistedBalance = objectMapper.readTree(getResp).get("currentBalance").asText();
        assertThat(new BigDecimal(persistedBalance).compareTo(new BigDecimal(expected))).isZero();
    }
}
