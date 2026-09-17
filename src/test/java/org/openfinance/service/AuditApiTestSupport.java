package org.openfinance.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.openfinance.security.EncryptionContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** Authenticated database regressions shared by encrypted and plaintext deployments. */
@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
abstract class AuditApiTestSupport {
    @Autowired protected MockMvc mvc;
    @Autowired protected ObjectMapper mapper;
    @Autowired protected JdbcTemplate jdbc;
    protected Auth owner;
    protected static final String MASTER = "AuditMaster123!";
    protected static final LocalDate START = LocalDate.now().minusMonths(6).withDayOfMonth(1);

    protected record Auth(long id, String token, String session) {}

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) throws Exception {
        String configured = System.getenv("SPRING_DATASOURCE_URL");
        if (configured == null || !configured.startsWith("jdbc:postgresql:")) {
            Files.createDirectories(Path.of("target", "test-dbs"));
            Path path =
                    Files.createTempFile(Path.of("target", "test-dbs"), "audit-integrity-", ".db");
            registry.add(
                    "spring.datasource.url",
                    () ->
                            "jdbc:sqlite:"
                                    + path
                                    + "?foreign_keys=on&journal_mode=WAL&busy_timeout=10000");
        }
    }

    @BeforeEach
    void registerOwner() throws Exception {
        owner = register();
    }

    @AfterEach
    void clearContexts() {
        SecurityContextHolder.clearContext();
        EncryptionContext.clear();
    }

    protected Auth register() throws Exception {
        String name = "audit" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        json(
                "POST",
                "/auth/register",
                data(
                        "username",
                        name,
                        "email",
                        name + "@example.invalid",
                        "password",
                        "LoginPassword123!",
                        "masterPassword",
                        MASTER,
                        "skipSeeding",
                        true),
                null,
                201);
        JsonNode result =
                json(
                        "POST",
                        "/auth/login",
                        data(
                                "username",
                                name,
                                "password",
                                "LoginPassword123!",
                                "masterPassword",
                                MASTER),
                        null,
                        200);
        return new Auth(
                jdbc.queryForObject("SELECT id FROM users WHERE username = ?", Long.class, name),
                result.path("token").asText(),
                result.path("encryptionKey").asText(null));
    }

    protected JsonNode json(String method, String path, Object body, Auth auth, int status)
            throws Exception {
        MockHttpServletRequestBuilder builder =
                request(HttpMethod.valueOf(method), "/api/v1" + path);
        if (body != null)
            builder.contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsBytes(body));
        if (auth != null) {
            builder.header("Authorization", "Bearer " + auth.token());
            if (auth.session() != null) builder.header("X-Encryption-Session", auth.session());
        }
        org.springframework.mock.web.MockHttpServletResponse response =
                mvc.perform(builder).andReturn().getResponse();
        assertThat(response.getStatus())
                .as("%s %s: %s", method, path, response.getContentAsString())
                .isEqualTo(status);
        if (response.getContentType() != null
                && response.getContentType().startsWith("text/plain")) {
            return mapper.getNodeFactory().textNode(response.getContentAsString());
        }
        return response.getContentAsString().isBlank()
                ? mapper.nullNode()
                : mapper.readTree(response.getContentAsString());
    }

    protected static Map<String, Object> data(Object... pairs) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) result.put((String) pairs[i], pairs[i + 1]);
        return result;
    }

    protected long account(Auth user) throws Exception {
        return json(
                        "POST",
                        "/accounts",
                        data(
                                "name",
                                "Private cash",
                                "type",
                                "CHECKING",
                                "currency",
                                "EUR",
                                "initialBalance",
                                1000,
                                "openingDate",
                                START.toString()),
                        user,
                        201)
                .path("id")
                .asLong();
    }

    protected JsonNode property(Auth user) throws Exception {
        return json(
                "POST",
                "/real-estate",
                data(
                        "name",
                        "Private property",
                        "propertyType",
                        "RESIDENTIAL",
                        "address",
                        "Synthetic address",
                        "purchasePrice",
                        1000,
                        "currentValue",
                        1000,
                        "purchaseDate",
                        START.toString(),
                        "currency",
                        "EUR"),
                user,
                201);
    }

    protected long loan(LocalDate start) throws Exception {
        return json(
                        "POST",
                        "/liabilities",
                        data(
                                "name",
                                "Loan",
                                "type",
                                "PERSONAL_LOAN",
                                "principal",
                                1000,
                                "currentBalance",
                                1000,
                                "currency",
                                "EUR",
                                "startDate",
                                start.toString(),
                                "interestRate",
                                0),
                        owner,
                        201)
                .path("id")
                .asLong();
    }

    protected Map<String, Object> movement(long account, int amount, LocalDate date) {
        return data(
                "accountId",
                account,
                "amount",
                amount,
                "date",
                date.toString(),
                "type",
                "EXPENSE",
                "currency",
                "EUR",
                "description",
                "Audit movement");
    }

    protected JsonNode history(LocalDate start, LocalDate end) throws Exception {
        return json(
                "GET",
                "/dashboard/networth-history?startDate="
                        + start
                        + "&endDate="
                        + end
                        + "&recalculate=true",
                null,
                owner,
                200);
    }

    protected void money(JsonNode object, String field, String expected) {
        assertThat(object.path(field).decimalValue()).as(field).isEqualByComparingTo(expected);
    }

    @Test
    void ordinaryTransactionsRejectDestinationsOnCreateAndUpdate() throws Exception {
        Auth victim = register();
        long cash = account(owner);
        long foreign = account(victim);
        Map<String, Object> payload = movement(cash, 10, LocalDate.now());
        long transaction = json("POST", "/transactions", payload, owner, 201).path("id").asLong();
        payload.put("toAccountId", foreign);
        json("POST", "/transactions", payload, owner, 400);
        json("PUT", "/transactions/" + transaction, payload, owner, 400);
        money(json("GET", "/accounts/" + cash, null, owner, 200), "ownBalance", "990");
        money(json("GET", "/accounts/" + foreign, null, victim, 200), "ownBalance", "1000");
        json("GET", "/accounts/" + foreign, null, owner, 404);
    }

    @Test
    void ordinaryTransactionsRejectForeignInstruments() throws Exception {
        Auth victim = register();
        JsonNode property = property(victim);
        long cash = account(owner);
        for (String field : new String[] {"realEstateId", "assetId"}) {
            Map<String, Object> payload = movement(cash, 10, LocalDate.now());
            long transaction =
                    json("POST", "/transactions", payload, owner, 201).path("id").asLong();
            payload.put(field, property.path(field.equals("assetId") ? "assetId" : "id").asLong());
            payload.put("movementType", "MAINTENANCE");
            json("POST", "/transactions", payload, owner, 404);
            json("PUT", "/transactions/" + transaction, payload, owner, 404);
        }
        money(json("GET", "/accounts/" + cash, null, owner, 200), "ownBalance", "980");
    }
}
