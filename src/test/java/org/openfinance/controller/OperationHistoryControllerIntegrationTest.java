package org.openfinance.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
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
    void undo_Redo_MarkStatusOnlyForNonCreate() throws Exception {
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
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.undoneAt").isNotEmpty())
                .andExpect(jsonPath("$.redoneAt").isEmpty());

        // Redo
        mockMvc.perform(
                        post("/api/v1/history/" + historyId + "/redo")
                                .header("Authorization", "Bearer " + authToken)
                                .header("X-Encryption-Session", encryptionSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.undoneAt").isNotEmpty())
                .andExpect(jsonPath("$.redoneAt").isNotEmpty());
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
