package org.openfinance.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
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

    @Autowired private org.springframework.jdbc.core.JdbcTemplate jdbc;

    @Autowired private org.openfinance.service.MasterPasswordService masterPasswords;
    @Autowired private org.openfinance.service.UserBackupArchive archives;
    @Autowired private org.openfinance.service.ImportService imports;
    @Autowired private org.openfinance.repository.ImportSessionRepository importSessions;
    @Autowired private org.openfinance.config.EncryptionProperties encryptionProperties;

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
        assertThat(history.get("totalElements").asInt()).isEqualTo(2);
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

    @Test
    void transactionCreateUpdateDeleteCanBeUndoneAndRedoneRepeatedly() throws Exception {
        JsonNode account = createAccount("Lifecycle");
        JsonNode transaction = expense(account, "50");
        long created = latest("TRANSACTION").get("id").asLong();
        call(
                put("/api/v1/transactions/" + transaction.get("id").asLong()),
                expenseRequest(account, "65"));
        long updated = latest("TRANSACTION").get("id").asLong();
        assertThat(historyEntry(created).get("canUndo").asBoolean()).isFalse();
        assertThat(historyEntry(created).get("unavailableReason").asText()).isEqualTo("changed");
        for (int cycle = 0; cycle < 2; cycle++) {
            reverse(updated, false);
            assertBalance(account, "950");
            assertThat(
                            call(
                                            get(
                                                    "/api/v1/transactions/"
                                                            + transaction.get("id").asLong()),
                                            null)
                                    .get("amount")
                                    .decimalValue())
                    .isEqualByComparingTo("50");
            reverse(updated, true);
            assertBalance(account, "935");
        }
        reverse(updated, false);
        reverse(created, false);
        assertBalance(account, "1000");
        reverse(created, true);
        reverse(updated, true);
        assertBalance(account, "935");
        call(delete("/api/v1/transactions/" + transaction.get("id").asLong()), null);
        long deleted = latest("TRANSACTION").get("id").asLong();
        assertBalance(account, "1000");
        reverse(deleted, false);
        assertBalance(account, "935");
        reverse(deleted, true);
        assertBalance(account, "1000");
    }

    @Test
    void reversingOnePaymentPreservesLaterUnrelatedPayment() throws Exception {
        JsonNode account = createAccount("Independent payments");
        expense(account, "12.34");
        long first = latest("TRANSACTION").get("id").asLong();
        JsonNode second = expense(account, "56.78");
        reverse(first, false);
        assertBalance(account, "943.22");
        assertThat(
                        call(get("/api/v1/transactions/" + second.get("id").asLong()), null)
                                .get("amount")
                                .decimalValue())
                .isEqualByComparingTo("56.78");
        reverse(first, true);
        assertBalance(account, "930.88");
    }

    @Test
    void transferEditAndDeletionRestoreBothLegsAndBalances() throws Exception {
        JsonNode source = createAccount("Transfer source");
        JsonNode destination = createAccount("Transfer destination");
        JsonNode transfer =
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
                                "2026-01-02"));
        call(
                put("/api/v1/transactions/transfers/" + transfer.get("transferId").asText()),
                Map.of(
                        "fromAccountId",
                        source.get("id").asLong(),
                        "toAccountId",
                        destination.get("id").asLong(),
                        "amount",
                        "200.01",
                        "currency",
                        "EUR",
                        "date",
                        "2026-01-03"));
        long edit = latest("TRANSACTION").get("id").asLong();
        reverse(edit, false);
        assertBalance(source, "874.51");
        assertBalance(destination, "1125.49");
        reverse(edit, true);
        assertBalance(source, "799.99");
        assertBalance(destination, "1200.01");
        call(delete("/api/v1/transactions/" + transfer.get("id").asLong()), null);
        long deletion = latest("TRANSACTION").get("id").asLong();
        reverse(deletion, false);
        assertBalance(source, "799.99");
        assertBalance(destination, "1200.01");
        reverse(deletion, true);
        assertBalance(source, "1000");
        assertBalance(destination, "1000");
    }

    @Test
    void assetCreationCannotBeReversedOverLaterImprovement() throws Exception {
        JsonNode account = createAccount("Improvements");
        JsonNode asset =
                call(
                        post("/api/v1/assets"),
                        Map.of(
                                "name",
                                "Desk",
                                "type",
                                "FURNITURE",
                                "quantity",
                                1,
                                "purchasePrice",
                                "1000",
                                "currentPrice",
                                "1000",
                                "currency",
                                "EUR",
                                "purchaseDate",
                                "2026-01-01"));
        long created = latest("ASSET").get("id").asLong();
        Map<String, Object> request = new java.util.HashMap<>(expenseRequest(account, "100"));
        request.put("assetId", asset.get("id").asLong());
        request.put("movementType", "CAPITAL_IMPROVEMENT");
        JsonNode improvement = call(post("/api/v1/transactions"), request);
        long improved = latest("TRANSACTION").get("id").asLong();
        assertThat(historyEntry(created).get("canUndo").asBoolean()).isFalse();
        rejectReverse(created);
        assertThat(
                        call(get("/api/v1/transactions/" + improvement.get("id").asLong()), null)
                                .get("assetId")
                                .asLong())
                .isEqualTo(asset.get("id").asLong());
        reverse(improved, false);
        reverse(created, false);
        reverse(created, true);
        reverse(improved, true);
        assertBalance(account, "900");
        assertThat(
                        call(get("/api/v1/assets/" + asset.get("id").asLong()), null)
                                .get("totalValue")
                                .decimalValue())
                .isEqualByComparingTo("1100");
    }

    @Test
    void payeeRenameUndoRestoresLinkedTransactions() throws Exception {
        JsonNode account = createAccount("Payee changes");
        JsonNode payee = call(post("/api/v1/payees"), Map.of("name", "Old merchant"));
        Map<String, Object> request = new java.util.HashMap<>(expenseRequest(account, "9.99"));
        request.put("payee", "Old merchant");
        JsonNode transaction = call(post("/api/v1/transactions"), request);
        call(put("/api/v1/payees/" + payee.get("id").asLong()), Map.of("name", "New merchant"));
        long rename = latest("PAYEE").get("id").asLong();
        reverse(rename, false);
        assertThat(
                        call(get("/api/v1/transactions/" + transaction.get("id").asLong()), null)
                                .get("payee")
                                .asText())
                .isEqualTo("Old merchant");
        reverse(rename, true);
        assertThat(
                        call(get("/api/v1/transactions/" + transaction.get("id").asLong()), null)
                                .get("payee")
                                .asText())
                .isEqualTo("New merchant");
        assertBalance(account, "990.01");
    }

    @Test
    void accountCloseAndReopenHaveReversibleStatusHistory() throws Exception {
        JsonNode account = createAccount("Closing");
        long id = account.get("id").asLong();
        call(post("/api/v1/accounts/" + id + "/close"), Map.of());
        long close = latest("ACCOUNT").get("id").asLong();
        reverse(close, false);
        assertThat(call(get("/api/v1/accounts/" + id), null).get("isActive").asBoolean()).isTrue();
        reverse(close, true);
        assertThat(call(get("/api/v1/accounts/" + id), null).get("isActive").asBoolean()).isFalse();
        call(post("/api/v1/accounts/" + id + "/reopen"), Map.of());
        long reopen = latest("ACCOUNT").get("id").asLong();
        reverse(reopen, false);
        assertThat(call(get("/api/v1/accounts/" + id), null).get("isActive").asBoolean()).isFalse();
        reverse(reopen, true);
        assertThat(call(get("/api/v1/accounts/" + id), null).get("isActive").asBoolean()).isTrue();
    }

    @Test
    void expiryAndDuplicateUndoDoNotChangeBalances() throws Exception {
        JsonNode account = createAccount("Expired action");
        expense(account, "15");
        long id = latest("TRANSACTION").get("id").asLong();
        reverse(id, false);
        rejectReverse(id);
        assertBalance(account, "1000");
        reverse(id, true);
        jdbc.update(
                "UPDATE operation_history SET created_at = ? WHERE id = ?",
                "2020-01-01T00:00:00",
                id);
        assertThat(historyEntry(id).get("unavailableReason").asText()).isEqualTo("expired");
        rejectReverse(id);
        assertBalance(account, "985");
    }

    @Test
    void categoryAndBudgetCrudRestoreTheirOriginalIdentifiers() throws Exception {
        JsonNode category =
                call(post("/api/v1/categories"), Map.of("name", "Food", "type", "EXPENSE"));
        long categoryId = category.get("id").asLong();
        call(
                put("/api/v1/categories/" + categoryId),
                Map.of("name", "Groceries", "type", "EXPENSE"));
        long rename = latest("CATEGORY").get("id").asLong();
        reverse(rename, false);
        assertThat(call(get("/api/v1/categories/" + categoryId), null).get("name").asText())
                .isEqualTo("Food");
        reverse(rename, true);
        Map<String, Object> request =
                new java.util.HashMap<>(
                        Map.of(
                                "categoryId",
                                categoryId,
                                "amount",
                                "200.12",
                                "currency",
                                "EUR",
                                "period",
                                "MONTHLY",
                                "startDate",
                                "2026-01-01",
                                "endDate",
                                "2026-01-31",
                                "rollover",
                                false));
        JsonNode budget = call(post("/api/v1/budgets"), request);
        long budgetId = budget.get("id").asLong();
        request.put("amount", "250.34");
        call(put("/api/v1/budgets/" + budgetId), request);
        long update = latest("BUDGET").get("id").asLong();
        reverse(update, false);
        assertThat(call(get("/api/v1/budgets/" + budgetId), null).get("amount").decimalValue())
                .isEqualByComparingTo("200.12");
        reverse(update, true);
        call(delete("/api/v1/budgets/" + budgetId), null);
        long deletion = latest("BUDGET").get("id").asLong();
        reverse(deletion, false);
        assertThat(call(get("/api/v1/budgets/" + budgetId), null).get("amount").decimalValue())
                .isEqualByComparingTo("250.34");
        reverse(deletion, true);
        call(delete("/api/v1/categories/" + categoryId), null);
        long deleteCategory = latest("CATEGORY").get("id").asLong();
        reverse(deleteCategory, false);
        assertThat(call(get("/api/v1/categories/" + categoryId), null).get("name").asText())
                .isEqualTo("Groceries");
        reverse(deleteCategory, true);
    }

    @Test
    void ruleChildrenAndToggleCanBeRestored() throws Exception {
        Map<String, Object> request =
                new java.util.HashMap<>(
                        Map.of(
                                "name",
                                "Merchant rule",
                                "conditions",
                                java.util.List.of(
                                        Map.of(
                                                "field",
                                                "DESCRIPTION",
                                                "operator",
                                                "CONTAINS",
                                                "value",
                                                "shop")),
                                "actions",
                                java.util.List.of(
                                        Map.of("actionType", "ADD_TAG", "actionValue", "food"))));
        JsonNode rule = call(post("/api/v1/transaction-rules"), request);
        String path = "/api/v1/transaction-rules/" + rule.get("id").asLong();
        request.put(
                "actions",
                java.util.List.of(Map.of("actionType", "ADD_TAG", "actionValue", "household")));
        call(put(path), request);
        long update = latest("TRANSACTION_RULE").get("id").asLong();
        reverse(update, false);
        assertThat(call(get(path), null).get("actions").get(0).get("actionValue").asText())
                .isEqualTo("food");
        reverse(update, true);
        call(
                org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch(
                        path + "/toggle"),
                null);
        long toggle = latest("TRANSACTION_RULE").get("id").asLong();
        reverse(toggle, false);
        assertThat(call(get(path), null).get("isEnabled").asBoolean()).isTrue();
        reverse(toggle, true);
        call(delete(path), null);
        long deletion = latest("TRANSACTION_RULE").get("id").asLong();
        reverse(deletion, false);
        assertThat(call(get(path), null).get("actions").get(0).get("actionValue").asText())
                .isEqualTo("household");
        reverse(deletion, true);
    }

    @Test
    void recurringPauseUpdateAndDeleteAreReversible() throws Exception {
        JsonNode account = createAccount("Recurring");
        Map<String, Object> request = new java.util.HashMap<>(expenseRequest(account, "25.01"));
        request.remove("date");
        request.put("frequency", "MONTHLY");
        request.put("nextOccurrence", java.time.LocalDate.now().plusDays(1).toString());
        JsonNode recurring = call(post("/api/v1/recurring-transactions"), request);
        String path = "/api/v1/recurring-transactions/" + recurring.get("id").asLong();
        call(post(path + "/pause"), null);
        long pause = latest("RECURRING_TRANSACTION").get("id").asLong();
        reverse(pause, false);
        assertThat(call(get(path), null).get("isActive").asBoolean()).isTrue();
        reverse(pause, true);
        call(post(path + "/resume"), null);
        long resume = latest("RECURRING_TRANSACTION").get("id").asLong();
        reverse(resume, false);
        reverse(resume, true);
        request.put("amount", "30.02");
        call(put(path), request);
        long update = latest("RECURRING_TRANSACTION").get("id").asLong();
        reverse(update, false);
        assertThat(call(get(path), null).get("amount").decimalValue())
                .isEqualByComparingTo("25.01");
        reverse(update, true);
        call(delete(path), null);
        long deletion = latest("RECURRING_TRANSACTION").get("id").asLong();
        reverse(deletion, false);
        assertThat(call(get(path), null).get("amount").decimalValue())
                .isEqualByComparingTo("30.02");
        reverse(deletion, true);
    }

    @Test
    void assetDeleteUndoRestoresEncryptedAttachmentBytes() throws Exception {
        JsonNode asset =
                call(
                        post("/api/v1/assets"),
                        Map.of(
                                "name",
                                "Receipt desk",
                                "type",
                                "FURNITURE",
                                "quantity",
                                1,
                                "purchasePrice",
                                "1000",
                                "currentPrice",
                                "1000",
                                "currency",
                                "EUR",
                                "purchaseDate",
                                "2026-01-01"));
        byte[] receipt =
                "%PDF-1.7\nA synthetic receipt for 1000 EUR\n"
                        .getBytes(java.nio.charset.StandardCharsets.UTF_8);
        org.springframework.mock.web.MockMultipartFile file =
                new org.springframework.mock.web.MockMultipartFile(
                        "file", "receipt.pdf", "application/pdf", receipt);
        JsonNode attachment =
                call(
                        org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                                .multipart("/api/v1/attachments")
                                .file(file)
                                .param("entityType", "ASSET")
                                .param("entityId", asset.get("id").asText()),
                        null);
        call(delete("/api/v1/assets/" + asset.get("id").asLong()), null);
        long deletion = latest("ASSET").get("id").asLong();
        reverse(deletion, false);
        byte[] restored =
                mockMvc.perform(
                                get("/api/v1/attachments/"
                                                + attachment.get("id").asLong()
                                                + "/download")
                                        .header("Authorization", "Bearer " + authToken)
                                        .header("X-Encryption-Session", encryptionSession))
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsByteArray();
        assertThat(restored).isEqualTo(receipt);
        reverse(deletion, true);
        assertThat(
                        jdbc.queryForObject(
                                "SELECT COUNT(*) FROM attachments WHERE id = ?",
                                Integer.class,
                                attachment.get("id").asLong()))
                .isZero();
    }

    @Test
    void propertyDeletionRestoresItsMirrorAndValuations() throws Exception {
        JsonNode property =
                call(
                        post("/api/v1/real-estate"),
                        Map.of(
                                "name",
                                "Home",
                                "propertyType",
                                "RESIDENTIAL",
                                "address",
                                "Test home",
                                "purchasePrice",
                                "1000",
                                "currentValue",
                                "1200",
                                "currency",
                                "EUR",
                                "purchaseDate",
                                "2026-01-01"));
        long id = property.get("id").asLong();
        long mirror =
                jdbc.queryForObject(
                        "SELECT asset_id FROM real_estate_properties WHERE id = ?", Long.class, id);
        call(delete("/api/v1/real-estate/" + id), null);
        long deletion = latest("REAL_ESTATE").get("id").asLong();
        reverse(deletion, false);
        assertThat(
                        jdbc.queryForObject(
                                "SELECT asset_id FROM real_estate_properties WHERE id = ?",
                                Long.class,
                                id))
                .isEqualTo(mirror);
        assertThat(call(get("/api/v1/assets/" + mirror), null).get("totalValue").decimalValue())
                .isEqualByComparingTo("1200");
        reverse(deletion, true);
        assertThat(
                        jdbc.queryForObject(
                                "SELECT COUNT(*) FROM assets WHERE id = ?", Integer.class, mirror))
                .isZero();
    }

    @Test
    void laterEditBackToOriginalValueStillPreventsUndoingCreation() throws Exception {
        JsonNode account = createAccount("Changing back");
        JsonNode transaction = expense(account, "10");
        long creation = latest("TRANSACTION").get("id").asLong();
        call(
                put("/api/v1/transactions/" + transaction.get("id").asLong()),
                expenseRequest(account, "20"));
        call(
                put("/api/v1/transactions/" + transaction.get("id").asLong()),
                expenseRequest(account, "10"));
        assertThat(historyEntry(creation).get("canUndo").asBoolean()).isFalse();
        rejectReverse(creation);
        assertBalance(account, "990");
    }

    @Test
    void conflictingPayeeNamePreventsDeletionUndo() throws Exception {
        JsonNode payee = call(post("/api/v1/payees"), Map.of("name", "Merchant"));
        call(delete("/api/v1/payees/" + payee.get("id").asLong()), null);
        long deletion = latest("PAYEE").get("id").asLong();
        call(post("/api/v1/payees"), Map.of("name", "merchant"));
        assertThat(historyEntry(deletion).get("canUndo").asBoolean()).isFalse();
        rejectReverse(deletion);
        assertThat(
                        jdbc.queryForObject(
                                "SELECT COUNT(*) FROM payees WHERE user_id = ?",
                                Integer.class,
                                userId))
                .isEqualTo(1);
    }

    @Test
    void keyRotationKeepsUndoAndRedoPayloadsUsable() throws Exception {
        org.junit.jupiter.api.Assumptions.assumeTrue(encryptionProperties.isEnabled());
        JsonNode account = createAccount("Rotated history");
        expense(account, "17.23");
        long creation = latest("TRANSACTION").get("id").asLong();
        call(
                put("/api/v1/users/me/master-password"),
                Map.of(
                        "currentMasterPassword",
                        "Password123!",
                        "newMasterPassword",
                        "NewMaster123!"));
        JsonNode login =
                call(
                        post("/api/v1/auth/login"),
                        Map.of(
                                "username",
                                "historyUser",
                                "password",
                                "Password123!",
                                "masterPassword",
                                "NewMaster123!"));
        authToken = login.get("token").asText();
        encryptionSession = login.get("encryptionKey").asText();
        reverse(creation, false);
        assertBalance(account, "1000");
        reverse(creation, true);
        assertBalance(account, "982.77");
    }

    @Test
    void backupRestoreRemapsUndoneRecordsAndReservesTheirIds() throws Exception {
        org.junit.jupiter.api.Assumptions.assumeTrue(
                Boolean.TRUE.equals(
                        jdbc.execute(
                                (org.springframework.jdbc.core.ConnectionCallback<Boolean>)
                                        connection ->
                                                connection
                                                        .getMetaData()
                                                        .getDatabaseProductName()
                                                        .equals("SQLite"))));
        JsonNode account = createAccount("Restored history");
        expense(account, "42.13");
        long creation = latest("TRANSACTION").get("id").asLong();
        reverse(creation, false);
        java.nio.file.Path backup = java.nio.file.Files.createTempFile("undo-restore-", ".db");
        javax.crypto.SecretKey key = historyKey();
        try {
            org.openfinance.security.EncryptionContext.setKey(key);
            archives.write(userId, backup);
            archives.restore(userId, backup, "Password123!");
        } finally {
            org.openfinance.security.EncryptionContext.clear();
            java.nio.file.Files.deleteIfExists(backup);
        }
        JsonNode restoredAction = latest("TRANSACTION");
        long restoredAccountId =
                call(get("/api/v1/history?entityType=ACCOUNT"), null)
                        .get("content")
                        .get(0)
                        .get("entityId")
                        .asLong();
        JsonNode restoredAccount = call(get("/api/v1/accounts/" + restoredAccountId), null);
        JsonNode laterPayment = expense(restoredAccount, "5.01");
        assertThat(laterPayment.get("id").asLong())
                .isNotEqualTo(restoredAction.get("entityId").asLong());
        reverse(restoredAction.get("id").asLong(), true);
        assertBalance(restoredAccount, "952.86");
        reverse(restoredAction.get("id").asLong(), false);
        assertBalance(restoredAccount, "994.99");
    }

    @Test
    void importReversalGroupsItsTransactionsAndReusesBookedResults() throws Exception {
        JsonNode account = createAccount("Imported history");
        javax.crypto.SecretKey key = historyKey();
        long sessionId;
        try {
            org.openfinance.security.EncryptionContext.setKey(key);
            java.util.List<org.openfinance.dto.ImportedTransaction> entries =
                    java.util.List.of(
                            org.openfinance.dto.ImportedTransaction.builder()
                                    .transactionDate(java.time.LocalDate.of(2026, 1, 2))
                                    .amount(new java.math.BigDecimal("-20.55"))
                                    .currency("EUR")
                                    .payee("Imported groceries")
                                    .referenceNumber("history-import-1")
                                    .build(),
                            org.openfinance.dto.ImportedTransaction.builder()
                                    .transactionDate(java.time.LocalDate.of(2026, 1, 3))
                                    .amount(new java.math.BigDecimal("-30.00"))
                                    .currency("EUR")
                                    .payee("Imported transport")
                                    .referenceNumber("history-import-2")
                                    .build());
            org.openfinance.entity.ImportSession session =
                    importSessions.saveAndFlush(
                            org.openfinance.entity.ImportSession.builder()
                                    .userId(userId)
                                    .uploadId(java.util.UUID.randomUUID().toString())
                                    .fileName("history.qif")
                                    .fileFormat("QIF")
                                    .accountId(account.get("id").asLong())
                                    .status(
                                            org.openfinance.entity.ImportSession.ImportStatus
                                                    .IMPORTING)
                                    .totalTransactions(2)
                                    .metadata(
                                            objectMapper.writeValueAsString(
                                                    Map.of("transactions", entries)))
                                    .build());
            sessionId = session.getId();
            imports.confirmImport(sessionId, userId, account.get("id").asLong(), Map.of(), true);
        } finally {
            org.openfinance.security.EncryptionContext.clear();
        }
        assertBalance(account, "949.45");
        assertThat(
                        call(get("/api/v1/history?entityType=TRANSACTION"), null)
                                .get("totalElements")
                                .asInt())
                .isZero();
        long imported = latest("IMPORT").get("id").asLong();
        java.util.List<Long> transactionIds =
                jdbc.queryForList(
                        "SELECT id FROM transactions WHERE user_id = ? ORDER BY id",
                        Long.class,
                        userId);
        reverse(imported, false);
        assertBalance(account, "1000");
        assertThat(
                        jdbc.queryForObject(
                                "SELECT status FROM import_sessions WHERE id = ?",
                                String.class,
                                sessionId))
                .isEqualTo("REVIEWING");
        reverse(imported, true);
        assertBalance(account, "949.45");
        assertThat(
                        jdbc.queryForList(
                                "SELECT id FROM transactions WHERE user_id = ? ORDER BY id",
                                Long.class,
                                userId))
                .containsExactlyElementsOf(transactionIds);
        assertThat(
                        jdbc.queryForObject(
                                "SELECT status FROM import_sessions WHERE id = ?",
                                String.class,
                                sessionId))
                .isEqualTo("COMPLETED");

        if (Boolean.TRUE.equals(
                jdbc.execute(
                        (org.springframework.jdbc.core.ConnectionCallback<Boolean>)
                                connection ->
                                        connection
                                                .getMetaData()
                                                .getDatabaseProductName()
                                                .equals("SQLite")))) {
            reverse(imported, false);
            java.nio.file.Path backup = java.nio.file.Files.createTempFile("undo-import-", ".db");
            try {
                org.openfinance.security.EncryptionContext.setKey(historyKey());
                archives.write(userId, backup);
                archives.restore(userId, backup, "Password123!");
            } finally {
                org.openfinance.security.EncryptionContext.clear();
                java.nio.file.Files.deleteIfExists(backup);
            }
            JsonNode restored = latest("IMPORT");
            assertThat(restored.get("canRedo").asBoolean()).isTrue();
            reverse(restored.get("id").asLong(), true);
            long restoredAccount =
                    jdbc.queryForObject(
                            "SELECT account_id FROM import_sessions WHERE id = ?",
                            Long.class,
                            restored.get("entityId").asLong());
            assertBalance(call(get("/api/v1/accounts/" + restoredAccount), null), "949.45");
        }
    }

    @Test
    void recurringPostingGroupsThePaymentAndNextOccurrence() throws Exception {
        JsonNode account = createAccount("Posted recurrence");
        Map<String, Object> request = new java.util.HashMap<>(expenseRequest(account, "25.01"));
        request.remove("date");
        request.put("frequency", "MONTHLY");
        request.put("nextOccurrence", java.time.LocalDate.now().toString());
        JsonNode recurring = call(post("/api/v1/recurring-transactions"), request);
        call(post("/api/v1/recurring-transactions/process"), null);
        assertBalance(account, "974.99");
        long posting = latest("RECURRING_TRANSACTION").get("id").asLong();
        reverse(posting, false);
        assertBalance(account, "1000");
        assertThat(
                        call(
                                        get(
                                                "/api/v1/recurring-transactions/"
                                                        + recurring.get("id").asLong()),
                                        null)
                                .get("nextOccurrence")
                                .asText())
                .isEqualTo(java.time.LocalDate.now().toString());
        reverse(posting, true);
        assertBalance(account, "974.99");
        assertThat(
                        jdbc.queryForObject(
                                "SELECT COUNT(*) FROM transactions WHERE user_id = ?",
                                Integer.class,
                                userId))
                .isEqualTo(1);
    }

    @Test
    void splitAndForeignCurrencyBookingRoundTripWithoutRepricing() throws Exception {
        JsonNode account = createAccount("Booked conversion");
        long food =
                call(post("/api/v1/categories"), Map.of("name", "Food", "type", "EXPENSE"))
                        .get("id")
                        .asLong();
        long travel =
                call(post("/api/v1/categories"), Map.of("name", "Travel", "type", "EXPENSE"))
                        .get("id")
                        .asLong();
        Map<String, Object> request = new java.util.HashMap<>(expenseRequest(account, "90"));
        request.putAll(
                Map.of(
                        "originalAmount",
                        "100",
                        "originalCurrency",
                        "USD",
                        "conversionRate",
                        "0.9",
                        "splits",
                        java.util.List.of(
                                Map.of("categoryId", food, "amount", "40"),
                                Map.of("categoryId", travel, "amount", "50"))));
        JsonNode payment = call(post("/api/v1/transactions"), request);
        long created = latest("TRANSACTION").get("id").asLong();
        java.util.List<Long> splitIds =
                jdbc.queryForList(
                        "SELECT id FROM transaction_splits WHERE transaction_id = ? ORDER BY id",
                        Long.class,
                        payment.get("id").asLong());
        assertThat(splitIds).hasSize(2);
        reverse(created, false);
        assertBalance(account, "1000");
        assertThat(
                        jdbc.queryForObject(
                                "SELECT COUNT(*) FROM transaction_splits WHERE transaction_id = ?",
                                Integer.class,
                                payment.get("id").asLong()))
                .isZero();
        reverse(created, true);
        assertBalance(account, "910");
        JsonNode restored = call(get("/api/v1/transactions/" + payment.get("id").asLong()), null);
        assertThat(restored.get("originalAmount").decimalValue()).isEqualByComparingTo("100");
        assertThat(restored.get("conversionRate").decimalValue()).isEqualByComparingTo("0.9");
        assertThat(restored.get("accountAmount").decimalValue()).isEqualByComparingTo("90");
        assertThat(
                        jdbc.queryForList(
                                "SELECT id FROM transaction_splits WHERE transaction_id = ? ORDER BY id",
                                Long.class,
                                payment.get("id").asLong()))
                .containsExactlyElementsOf(splitIds);
    }

    @Test
    void loanDrawsAndPrincipalAllocationsReverseTogether() throws Exception {
        JsonNode account = createAccount("Loan cash");
        long loan =
                call(
                                post("/api/v1/liabilities"),
                                Map.of(
                                        "name",
                                        "Loan",
                                        "type",
                                        "LOAN",
                                        "principal",
                                        "1000",
                                        "currentBalance",
                                        "0",
                                        "currency",
                                        "EUR",
                                        "startDate",
                                        "2026-01-01"))
                        .get("id")
                        .asLong();
        call(
                post("/api/v1/liabilities/" + loan + "/disburse"),
                Map.of(
                        "amount",
                        "100",
                        "date",
                        "2026-01-02",
                        "toAccountId",
                        account.get("id").asLong()));
        call(
                post("/api/v1/liabilities/" + loan + "/disburse"),
                Map.of(
                        "amount",
                        "100",
                        "date",
                        "2026-01-02",
                        "toAccountId",
                        account.get("id").asLong()));
        long draw = latest("LIABILITY").get("id").asLong();
        Map<String, Object> request = new java.util.HashMap<>(expenseRequest(account, "150"));
        request.putAll(Map.of("liabilityId", loan, "date", "2026-01-03"));
        JsonNode payment = call(post("/api/v1/transactions"), request);
        long paymentAction = latest("TRANSACTION").get("id").asLong();
        assertThat(payment.get("principalAmount").decimalValue()).isEqualByComparingTo("150");
        assertThat(
                        call(get("/api/v1/liabilities/" + loan), null)
                                .get("currentBalance")
                                .decimalValue())
                .isEqualByComparingTo("50");
        assertThat(
                        jdbc.queryForObject(
                                "SELECT COUNT(*) FROM liability_principal_allocations WHERE transaction_id = ?",
                                Integer.class,
                                payment.get("id").asLong()))
                .isEqualTo(2);
        reverse(paymentAction, false);
        assertBalance(account, "1200");
        assertThat(
                        call(get("/api/v1/liabilities/" + loan), null)
                                .get("currentBalance")
                                .decimalValue())
                .isEqualByComparingTo("200");
        reverse(draw, false);
        assertBalance(account, "1100");
        assertThat(
                        call(get("/api/v1/liabilities/" + loan), null)
                                .get("currentBalance")
                                .decimalValue())
                .isEqualByComparingTo("100");
        reverse(draw, true);
        reverse(paymentAction, true);
        assertBalance(account, "1050");
        assertThat(
                        call(get("/api/v1/liabilities/" + loan), null)
                                .get("currentBalance")
                                .decimalValue())
                .isEqualByComparingTo("50");
        assertThat(
                        jdbc.queryForObject(
                                "SELECT COUNT(*) FROM liability_principal_allocations WHERE transaction_id = ?",
                                Integer.class,
                                payment.get("id").asLong()))
                .isEqualTo(2);
    }

    @Test
    void reversalInvalidatesHistoricalNetWorth() throws Exception {
        JsonNode account = createAccount("Historical balance");
        expense(account, "12.34");
        long payment = latest("TRANSACTION").get("id").asLong();
        String url = "/api/v1/dashboard/networth-history?startDate=2026-01-02&endDate=2026-01-02";
        assertThat(call(get(url), null).get(0).get("netWorth").decimalValue())
                .isEqualByComparingTo("987.66");
        reverse(payment, false);
        assertThat(call(get(url), null).get(0).get("netWorth").decimalValue())
                .isEqualByComparingTo("1000");
        reverse(payment, true);
        assertThat(call(get(url), null).get(0).get("netWorth").decimalValue())
                .isEqualByComparingTo("987.66");
    }

    @Test
    void concurrentUndoAppliesFinancialEffectsOnlyOnce() throws Exception {
        JsonNode account = createAccount("Concurrent reversal");
        expense(account, "10.99");
        long payment = latest("TRANSACTION").get("id").asLong();
        java.util.concurrent.CountDownLatch start = new java.util.concurrent.CountDownLatch(1);
        try (java.util.concurrent.ExecutorService workers =
                java.util.concurrent.Executors.newFixedThreadPool(2)) {
            java.util.concurrent.Callable<Integer> undo =
                    () -> {
                        start.await();
                        return mockMvc.perform(
                                        post("/api/v1/history/" + payment + "/undo")
                                                .header("Authorization", "Bearer " + authToken)
                                                .header("X-Encryption-Session", encryptionSession))
                                .andReturn()
                                .getResponse()
                                .getStatus();
                    };
            java.util.concurrent.Future<Integer> first = workers.submit(undo);
            java.util.concurrent.Future<Integer> second = workers.submit(undo);
            start.countDown();
            assertThat(
                            java.util.List.of(
                                    first.get(20, java.util.concurrent.TimeUnit.SECONDS),
                                    second.get(20, java.util.concurrent.TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(200, 409);
        }
        assertBalance(account, "1000");
        reverse(payment, true);
        assertBalance(account, "989.01");
    }

    @Test
    void capturedPayloadUsesDeploymentEncryptionPolicy() throws Exception {
        JsonNode account = createAccount("Private history marker");
        long action = latest("ACCOUNT").get("id").asLong();
        String raw =
                jdbc.queryForObject(
                        "SELECT action_state_json FROM operation_history WHERE id = ?",
                        String.class,
                        action);
        if (encryptionProperties.isEnabled())
            assertThat(raw).doesNotContain("Private history marker", "opening_balance");
        else assertThat(raw).contains("Private history marker", "opening_balance");
        reverse(action, false);
        reverse(action, true);
        assertBalance(account, "1000");
    }

    @Test
    void accountBalanceAdjustmentIsOneReversibleAction() throws Exception {
        JsonNode account = createAccount("Original account");
        call(
                put("/api/v1/accounts/" + account.get("id").asLong()),
                Map.of(
                        "name",
                        "Renamed account",
                        "type",
                        "CHECKING",
                        "currency",
                        "EUR",
                        "initialBalance",
                        "1200",
                        "openingDate",
                        "2026-01-01"));
        long update = latest("ACCOUNT").get("id").asLong();
        assertBalance(account, "1200");
        assertThat(
                        call(get("/api/v1/history?entityType=TRANSACTION"), null)
                                .get("totalElements")
                                .asInt())
                .isZero();
        reverse(update, false);
        assertBalance(account, "1000");
        assertThat(
                        call(get("/api/v1/accounts/" + account.get("id").asLong()), null)
                                .get("name")
                                .asText())
                .isEqualTo("Original account");
        assertThat(
                        jdbc.queryForObject(
                                "SELECT COUNT(*) FROM transactions WHERE user_id = ?",
                                Integer.class,
                                userId))
                .isZero();
        reverse(update, true);
        assertBalance(account, "1200");
        assertThat(
                        jdbc.queryForObject(
                                "SELECT COUNT(*) FROM transactions WHERE user_id = ?",
                                Integer.class,
                                userId))
                .isEqualTo(1);
    }

    @Test
    void failedFinancialActionDoesNotCreateHistoryOrPartiallyChangeBalance() throws Exception {
        JsonNode account = createAccount("Failed adjustment");
        int before = call(get("/api/v1/history"), null).get("totalElements").asInt();
        Map<String, Object> request = new java.util.HashMap<>(expenseRequest(account, "100"));
        request.put("splits", java.util.List.of(Map.of("amount", "40"), Map.of("amount", "59")));
        mockMvc.perform(
                        post("/api/v1/transactions")
                                .header("Authorization", "Bearer " + authToken)
                                .header("X-Encryption-Session", encryptionSession)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsBytes(request)))
                .andExpect(status().isBadRequest());
        assertBalance(account, "1000");
        assertThat(call(get("/api/v1/history"), null).get("totalElements").asInt())
                .isEqualTo(before);
    }

    @Test
    void historyFiltersAndOwnershipAreEnforced() throws Exception {
        JsonNode account = createAccount("Private action");
        JsonNode payment = expense(account, "10");
        long creation = latest("TRANSACTION").get("id").asLong();
        call(
                put("/api/v1/transactions/" + payment.get("id").asLong()),
                expenseRequest(account, "11"));
        JsonNode page =
                call(
                        get(
                                "/api/v1/history?entityType=TRANSACTION&operationType=UPDATE&since=2020-01-01T00:00:00Z&until=2099-01-01T00:00:00Z"),
                        null);
        assertThat(page.get("totalElements").asInt()).isEqualTo(1);
        assertThat(page.get("content").get(0).get("operationType").asText()).isEqualTo("UPDATE");
        call(
                post("/api/v1/auth/register"),
                Map.of(
                        "username",
                        "otherHistory",
                        "email",
                        "other-history@example.com",
                        "password",
                        "Password123!",
                        "masterPassword",
                        "Password123!",
                        "skipSeeding",
                        true));
        JsonNode other =
                call(
                        post("/api/v1/auth/login"),
                        Map.of(
                                "username",
                                "otherHistory",
                                "password",
                                "Password123!",
                                "masterPassword",
                                "Password123!"));
        mockMvc.perform(
                        post("/api/v1/history/" + creation + "/undo")
                                .header("Authorization", "Bearer " + other.get("token").asText())
                                .header(
                                        "X-Encryption-Session",
                                        other.path("encryptionKey").asText("")))
                .andExpect(status().isNotFound());
        assertBalance(account, "989");
    }

    @Test
    void reversalRestoresSearchTermsAndPreservesUnrelatedIndexRows() throws Exception {
        org.junit.jupiter.api.Assumptions.assumeTrue(encryptionProperties.isEnabled());
        JsonNode account = createAccount("Searchable account");
        JsonNode unrelated = expense(account, "3");
        Map<String, Object> request = new java.util.HashMap<>(expenseRequest(account, "5"));
        request.put("description", "Emerald grocery");
        JsonNode payment = call(post("/api/v1/transactions"), request);
        long id = payment.get("id").asLong();
        String query =
                "SELECT field_name, token FROM search_tokens WHERE entity_type = 'TRANSACTION' AND entity_id = ? ORDER BY field_name, token";
        java.util.List<Map<String, Object>> original = jdbc.queryForList(query, id);
        java.util.List<Map<String, Object>> unchanged =
                jdbc.queryForList(
                        "SELECT * FROM search_tokens WHERE entity_type = 'TRANSACTION' AND entity_id = ? ORDER BY id",
                        unrelated.get("id").asLong());
        assertThat(original).isNotEmpty();
        assertThat(unchanged).isNotEmpty();
        request.put("description", "Violet transport");
        call(put("/api/v1/transactions/" + id), request);
        java.util.List<Map<String, Object>> updated = jdbc.queryForList(query, id);
        assertThat(updated).isNotEqualTo(original);
        long edit = latest("TRANSACTION").get("id").asLong();
        reverse(edit, false);
        assertThat(jdbc.queryForList(query, id)).containsExactlyElementsOf(original);
        reverse(edit, true);
        assertThat(jdbc.queryForList(query, id)).containsExactlyElementsOf(updated);
        assertThat(
                        jdbc.queryForList(
                                "SELECT * FROM search_tokens WHERE entity_type = 'TRANSACTION' AND entity_id = ? ORDER BY id",
                                unrelated.get("id").asLong()))
                .containsExactlyElementsOf(unchanged);
    }

    private JsonNode expense(JsonNode account, String amount) throws Exception {
        return call(post("/api/v1/transactions"), expenseRequest(account, amount));
    }

    private javax.crypto.SecretKey historyKey() {
        return encryptionProperties.isEnabled()
                ? masterPasswords.derive(
                        "Password123!",
                        userRepository.findById(userId).orElseThrow().getMasterPasswordSalt())
                : null;
    }

    private Map<String, Object> expenseRequest(JsonNode account, String amount) {
        return Map.of(
                "accountId",
                account.get("id").asLong(),
                "type",
                "EXPENSE",
                "amount",
                amount,
                "currency",
                "EUR",
                "date",
                "2026-01-02",
                "description",
                "History payment");
    }

    private JsonNode latest(String type) throws Exception {
        return call(get("/api/v1/history?entityType=" + type + "&size=100"), null)
                .get("content")
                .get(0);
    }

    private JsonNode historyEntry(long id) throws Exception {
        for (JsonNode entry : call(get("/api/v1/history?size=100"), null).get("content"))
            if (entry.get("id").asLong() == id) return entry;
        throw new AssertionError("Missing history entry " + id);
    }

    private void reverse(long id, boolean redo) throws Exception {
        JsonNode result =
                call(post("/api/v1/history/" + id + (redo ? "/redo" : "/undo")), Map.of());
        assertThat(result.get(redo ? "canUndo" : "canRedo").asBoolean()).isTrue();
    }

    private void rejectReverse(long id) throws Exception {
        mockMvc.perform(
                        post("/api/v1/history/" + id + "/undo")
                                .header("Authorization", "Bearer " + authToken)
                                .header("X-Encryption-Session", encryptionSession))
                .andExpect(status().isConflict());
    }

    private void assertBalance(JsonNode account, String expected) throws Exception {
        assertThat(
                        call(get("/api/v1/accounts/" + account.get("id").asLong()), null)
                                .get("balance")
                                .decimalValue())
                .isEqualByComparingTo(expected);
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
        MvcResult result = mockMvc.perform(request).andReturn();
        assertThat(result.getResponse().getStatus())
                .withFailMessage(result.getResponse().getContentAsString())
                .isBetween(200, 299);
        String content = result.getResponse().getContentAsString();
        return content.isBlank() ? objectMapper.nullNode() : objectMapper.readTree(content);
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
                        .path("encryptionKey")
                        .asText("");

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
                .andExpect(status().isConflict());

        // Redo
        mockMvc.perform(
                        post("/api/v1/history/" + historyId + "/redo")
                                .header("Authorization", "Bearer " + authToken)
                                .header("X-Encryption-Session", encryptionSession))
                .andExpect(status().isConflict());
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
