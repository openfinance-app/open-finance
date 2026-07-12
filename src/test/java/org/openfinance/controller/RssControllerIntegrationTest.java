package org.openfinance.controller;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.openfinance.config.TestDatabaseConfig;
import org.openfinance.dto.LoginRequest;
import org.openfinance.dto.RssFeedItem;
import org.openfinance.dto.UserRegistrationRequest;
import org.openfinance.service.OperationHistoryService;
import org.openfinance.service.RssService;
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

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestDatabaseConfig.class)
@ActiveProfiles("test")
public class RssControllerIntegrationTest {

    @MockBean private OperationHistoryService operationHistoryService;

    @Autowired private MockMvc mockMvc;

    @MockBean private RssService rssService;

    @Autowired private UserService userService;

    @Autowired private DatabaseCleanupService databaseCleanupService;

    @Autowired private ObjectMapper objectMapper;

    private String validToken;
    private String encryptionSession;

    @BeforeEach
    void setUp() throws Exception {
        databaseCleanupService.execute();

        UserRegistrationRequest reg =
                UserRegistrationRequest.builder()
                        .username("rssuser")
                        .email("rss@example.com")
                        .password("Password123!")
                        .masterPassword("Master123!")
                        .skipSeeding(true)
                        .build();
        userService.registerUser(reg);

        LoginRequest login =
                LoginRequest.builder()
                        .username("rssuser")
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

        validToken = objectMapper.readTree(resp).get("token").asText();
        encryptionSession = objectMapper.readTree(resp).get("encryptionKey").asText();
    }

    @AfterEach
    void tearDown() {
        databaseCleanupService.execute();
    }

    @Test
    void shouldReturnFinanceFeeds() throws Exception {
        RssFeedItem item =
                RssFeedItem.builder()
                        .title("Test News Title")
                        .link("http://example.com/news")
                        .description("Test description")
                        .source("Test Source")
                        .pubDate(LocalDateTime.of(2023, 1, 1, 10, 0))
                        .build();

        when(rssService.getFinanceFeeds(any(Locale.class))).thenReturn(List.of(item));

        mockMvc.perform(
                        get("/api/v1/rss/finance")
                                .header("Authorization", "Bearer " + validToken)
                                .header("X-Encryption-Session", encryptionSession)
                                .header("Accept-Language", "fr")
                                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].title", is("Test News Title")))
                .andExpect(jsonPath("$[0].source", is("Test Source")));
    }
}
