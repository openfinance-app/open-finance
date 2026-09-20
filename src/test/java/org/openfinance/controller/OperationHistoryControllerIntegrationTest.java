package org.openfinance.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.openfinance.config.TestDatabaseConfig;
import org.openfinance.dto.LoginRequest;
import org.openfinance.dto.UserRegistrationRequest;
import org.openfinance.entity.EntityType;
import org.openfinance.entity.OperationType;
import org.openfinance.repository.UserRepository;
import org.openfinance.service.OperationHistoryService;
import org.openfinance.util.DatabaseCleanupService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestDatabaseConfig.class)
class OperationHistoryControllerIntegrationTest {

    @Autowired private MockMvc mockMvc;

    @Autowired private ObjectMapper objectMapper;

    @Autowired private OperationHistoryService operationHistoryService;

    @Autowired private UserRepository userRepository;

    @Autowired private DatabaseCleanupService databaseCleanupService;

    private String authToken;
    private String encryptionSession;
    private Long userId;

    @Test
    void payeeRenameUpdatesEncryptedTransactionsAndHistoryWithoutChangingBalances()
            throws Exception {
        JsonNode account = createAccount("Checking");
        JsonNode payee = call(post("/api/v1/payees"), Map.of("name", "Original merchant"));
        JsonNode transaction =
                call(
                        post("/api/v1/transactions"),
                        Map.of(
                                "accountId",
                                account.get("id").asLong(),
                                "type",
                                "EXPENSE",
                                "amount",
                                "10.99",
                                "currency",
                                "EUR",
                                "date",
                                "2026-01-02",
                                "payee",
                                "Original merchant"));
        call(put("/api/v1/payees/" + payee.get("id").asLong()), Map.of("name", "Renamed merchant"));
        JsonNode updated =
                call(get("/api/v1/transactions/" + transaction.get("id").asLong()), null);
        assertThat(updated.get("payee").asText()).isEqualTo("Renamed merchant");
        assertThat(updated.get("createdAt").asText()).matches(".*(?:Z|[+-]\\d{2}:\\d{2})$");
        JsonNode balance = call(get("/api/v1/accounts/" + account.get("id").asLong()), null);
        assertThat(balance.get("balance").decimalValue()).isEqualByComparingTo("989.01");
        JsonNode history = call(get("/api/v1/history?entityType=PAYEE"), null);
        assertThat(history.get("totalElements").asInt()).isEqualTo(1);
        assertThat(history.get("content").get(0).get("entityId").asLong())
                .isEqualTo(payee.get("id").asLong());
    }

    @Test
    void propertyCreationProducesOnePropertyHistoryEntryWithoutAnAssetMirror() throws Exception {
        JsonNode property =
                call(
                        post("/api/v1/real-estate"),
                        Map.of(
                                "name",
                                "History property",
                                "propertyType",
                                "RESIDENTIAL",
                                "address",
                                "Test address",
                                "purchasePrice",
                                "1000",
                                "currentValue",
                                "1200",
                                "currency",
                                "EUR",
                                "purchaseDate",
                                "2026-01-01"));
        JsonNode history = call(get("/api/v1/history?entityType=REAL_ESTATE"), null);
        assertThat(history.get("totalElements").asInt()).isEqualTo(1);
        assertThat(history.get("content").get(0).get("entityId").asLong())
                .isEqualTo(property.get("id").asLong());
        JsonNode assets = call(get("/api/v1/history?entityType=ASSET"), null);
        assertThat(assets.get("totalElements").asInt()).isZero();
    }

    @Test
    void transferHistoryUndoReversesBothLegs() throws Exception {
        JsonNode source = createAccount("Source");
        JsonNode destination = createAccount("Destination");
        call(
                post("/api/v1/transactions/transfer"),
                Map.of(
                        "accountId",
                        source.get("id").asLong(),
                        "toAccountId",
                        destination.get("id").asLong(),
                        "type",
                        "TRANSFER",
                        "amount",
                        "125.49",
                        "currency",
                        "EUR",
                        "date",
                        "2026-01-02",
                        "description",
                        "History transfer"));
        JsonNode history = call(get("/api/v1/history?entityType=TRANSACTION"), null);
        assertThat(history.get("totalElements").asInt()).isEqualTo(1);
        JsonNode entry = history.get("content").get(0);
        assertThat(entry.get("canUndo").asBoolean()).isTrue();
        call(post("/api/v1/history/" + entry.get("id").asLong() + "/undo"), Map.of());
        for (JsonNode account : new JsonNode[] {source, destination}) {
            JsonNode restored = call(get("/api/v1/accounts/" + account.get("id").asLong()), null);
            assertThat(restored.get("balance").decimalValue()).isEqualByComparingTo("1000");
        }
    }

    private JsonNode createAccount(String name) throws Exception {
        return call(
                post("/api/v1/accounts"),
                Map.of(
                        "name",
                        name,
                        "type",
                        "CHECKING",
                        "currency",
                        "EUR",
                        "initialBalance",
                        "1000",
                        "openingDate",
                        "2026-01-01"));
    }

    private JsonNode call(MockHttpServletRequestBuilder request, Object body) throws Exception {
        request.header("Authorization", "Bearer " + authToken)
                .header("X-Encryption-Session", encryptionSession);
        if (body != null)
            request.contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsBytes(body));
        MvcResult result =
                mockMvc.perform(request).andExpect(status().is2xxSuccessful()).andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    @BeforeEach
    void setUp() throws Exception {
        databaseCleanupService.execute();

        // 1. Register a test user
        UserRegistrationRequest registerRequest = new UserRegistrationRequest();
        registerRequest.setEmail("history-test@example.com");
        registerRequest.setUsername("historyUser");
        registerRequest.setPassword("Password123!");
        registerRequest.setMasterPassword("Password123!");
        registerRequest.setSkipSeeding(true);

        mockMvc.perform(
                        post("/api/v1/auth/register")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(registerRequest)))
                .andExpect(status().isCreated());

        // 2. Login
        LoginRequest authRequest = new LoginRequest();
        authRequest.setUsername("historyUser");
        authRequest.setPassword("Password123!");
        authRequest.setMasterPassword("Password123!");

        MvcResult loginResult =
                mockMvc.perform(
                                post("/api/v1/auth/login")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(objectMapper.writeValueAsString(authRequest)))
                        .andExpect(status().isOk())
                        .andReturn();

        authToken =
                objectMapper
                        .readTree(loginResult.getResponse().getContentAsString())
                        .get("token")
                        .asText();
        encryptionSession =
                objectMapper
                        .readTree(loginResult.getResponse().getContentAsString())
                        .get("encryptionKey")
                        .asText();

        userId = userRepository.findByEmail("history-test@example.com").orElseThrow().getId();
    }

    @Test
    void getHistory_Success() throws Exception {
        // Record a mock event directly via service
        operationHistoryService.record(
                userId,
                EntityType.ACCOUNT,
                999L,
                "Test Account",
                OperationType.CREATE,
                (String) null,
                (String) null);

        mockMvc.perform(
                        get("/api/v1/history")
                                .header("Authorization", "Bearer " + authToken)
                                .header("X-Encryption-Session", encryptionSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content[0].entityType").value("ACCOUNT"))
                .andExpect(jsonPath("$.content[0].entityLabel").value("Test Account"))
                .andExpect(jsonPath("$.content[0].operationType").value("CREATE"));
    }

    @Test
    void unsupportedUndoAndRedoLeaveStatusUnchanged() throws Exception {
        // Record a mock UPDATE event directly via service
        operationHistoryService.record(
                userId, EntityType.ACCOUNT, 999L, "Test Account", OperationType.UPDATE, "{}", "{}");

        // get the history entry id
        MvcResult historyResult =
                mockMvc.perform(
                                get("/api/v1/history")
                                        .header("Authorization", "Bearer " + authToken)
                                        .header("X-Encryption-Session", encryptionSession))
                        .andReturn();
        String content = historyResult.getResponse().getContentAsString();
        Integer historyId = com.jayway.jsonpath.JsonPath.read(content, "$.content[0].id");

        // Undo
        mockMvc.perform(
                        post("/api/v1/history/" + historyId + "/undo")
                                .header("Authorization", "Bearer " + authToken)
                                .header("X-Encryption-Session", encryptionSession))
                .andExpect(status().isBadRequest());

        // Redo
        mockMvc.perform(
                        post("/api/v1/history/" + historyId + "/redo")
                                .header("Authorization", "Bearer " + authToken)
                                .header("X-Encryption-Session", encryptionSession))
                .andExpect(status().isBadRequest());
        org.assertj.core.api.Assertions.assertThat(
                        operationHistoryService
                                .getEntry(historyId.longValue(), userId)
                                .getUndoneAt())
                .isNull();
        org.assertj.core.api.Assertions.assertThat(
                        operationHistoryService
                                .getEntry(historyId.longValue(), userId)
                                .getRedoneAt())
                .isNull();
    }

    @Test
    void undo_CannotRedoIfNotUndone() throws Exception {
        // Record a mock UPDATE event directly via service
        operationHistoryService.record(
                userId, EntityType.ACCOUNT, 999L, "Test Account", OperationType.UPDATE, "{}", "{}");

        // get the history entry id
        MvcResult historyResult =
                mockMvc.perform(
                                get("/api/v1/history")
                                        .header("Authorization", "Bearer " + authToken)
                                        .header("X-Encryption-Session", encryptionSession))
                        .andReturn();
        String content = historyResult.getResponse().getContentAsString();
        Integer historyId = com.jayway.jsonpath.JsonPath.read(content, "$.content[0].id");

        // Attempt Redo before Undo
        mockMvc.perform(
                        post("/api/v1/history/" + historyId + "/redo")
                                .header("Authorization", "Bearer " + authToken)
                                .header("X-Encryption-Session", encryptionSession))
                .andExpect(status().is4xxClientError());
    }

    @Test
    void getHistory_WithSinceFilter_ReturnsOnlySessionEntries() throws Exception {
        // Record an entry BEFORE the session start
        operationHistoryService.record(
                userId,
                EntityType.ACCOUNT,
                1L,
                "Old Entry",
                OperationType.CREATE,
                (String) null,
                (String) null);

        // Sleep briefly so the old entry's createdAt is strictly less than since.
        // Without this, both timestamps could share the same millisecond and the
        // '>= since' filter would include the old entry, making the test flaky.
        Thread.sleep(5);

        // Capture the session start AFTER the old entry has been persisted
        String since = java.time.Instant.now().toString();

        // Verify no entries returned when since is now (old entry predates it)
        mockMvc.perform(
                        get("/api/v1/history")
                                .param("since", since)
                                .header("Authorization", "Bearer " + authToken)
                                .header("X-Encryption-Session", encryptionSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));

        // Record an entry AFTER the session start
        operationHistoryService.record(
                userId,
                EntityType.ASSET,
                2L,
                "New Entry",
                OperationType.CREATE,
                (String) null,
                (String) null);

        // Now the session-scoped query should return only the new entry
        mockMvc.perform(
                        get("/api/v1/history")
                                .param("since", since)
                                .header("Authorization", "Bearer " + authToken)
                                .header("X-Encryption-Session", encryptionSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].entityLabel").value("New Entry"));

        // Without since filter, both entries are returned
        mockMvc.perform(
                        get("/api/v1/history")
                                .header("Authorization", "Bearer " + authToken)
                                .header("X-Encryption-Session", encryptionSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2));
    }
}
