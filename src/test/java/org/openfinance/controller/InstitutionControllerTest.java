package org.openfinance.controller;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.openfinance.dto.InstitutionRequest;
import org.openfinance.dto.InstitutionResponse;
import org.openfinance.entity.User;
import org.openfinance.exception.InstitutionNotFoundException;
import org.openfinance.service.InstitutionService;
import org.openfinance.service.JwtService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Integration tests for InstitutionController.
 *
 * <p>Tests REST API endpoints for institution management including CRUD operations, validation, and
 * error handling.
 */
@WebMvcTest(InstitutionController.class)
@org.springframework.context.annotation.Import({
    org.openfinance.config.LocalizationConfig.class,
    org.openfinance.config.RateLimitConfig.class,
    org.openfinance.config.RateLimitInterceptor.class
})
@ActiveProfiles("test")
class InstitutionControllerTest {

    @Autowired private MockMvc mockMvc;

    @Autowired private ObjectMapper objectMapper;

    @MockBean private InstitutionService institutionService;

    @MockBean private JwtService jwtService;

    @MockBean private org.openfinance.repository.UserRepository userRepository;

    @MockBean private org.openfinance.security.EncryptionKeyCache encryptionKeyCache;

    private static final Long USER_ID = 1L;

    private InstitutionResponse systemInstitutionResponse;
    private InstitutionResponse customInstitutionResponse;
    private InstitutionRequest validRequest;

    @BeforeEach
    void setUp() {
        systemInstitutionResponse =
                InstitutionResponse.builder()
                        .id(1L)
                        .name("BNP Paribas")
                        .bic("BNPAFRPP")
                        .country("FR")
                        .logo("base64logo")
                        .isSystem(true)
                        .createdAt(LocalDateTime.now().minusDays(1))
                        .updatedAt(LocalDateTime.now().minusDays(1))
                        .build();

        customInstitutionResponse =
                InstitutionResponse.builder()
                        .id(2L)
                        .name("My Custom Bank")
                        .bic("CUSTFRPP")
                        .country("FR")
                        .logo("customlogo")
                        .isSystem(false)
                        .createdAt(LocalDateTime.now().minusHours(1))
                        .updatedAt(LocalDateTime.now().minusHours(1))
                        .build();

        validRequest =
                InstitutionRequest.builder()
                        .name("New Bank")
                        .bic("NEWBFRPP")
                        .country("FR")
                        .logo("newlogo")
                        .build();
    }

    private Authentication createAuthentication(Long userId) {
        User user =
                User.builder()
                        .id(userId)
                        .username("testuser")
                        .email("test@example.com")
                        .passwordHash("hashed")
                        .masterPasswordSalt("salt")
                        .baseCurrency("EUR")
                        .build();

        return new UsernamePasswordAuthenticationToken(
                user, null, Collections.singletonList(new SimpleGrantedAuthority("USER")));
    }

    @Test
    void shouldGetAllInstitutions() throws Exception {
        // Arrange
        List<InstitutionResponse> institutions =
                List.of(systemInstitutionResponse, customInstitutionResponse);
        when(institutionService.getAllInstitutions(USER_ID)).thenReturn(institutions);

        // Act & Assert
        mockMvc.perform(
                        get("/api/v1/institutions")
                                .with(authentication(createAuthentication(USER_ID))))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[0].name").value("BNP Paribas"))
                .andExpect(jsonPath("$[0].isSystem").value(true))
                .andExpect(jsonPath("$[1].id").value(2))
                .andExpect(jsonPath("$[1].name").value("My Custom Bank"))
                .andExpect(jsonPath("$[1].isSystem").value(false));

        verify(institutionService).getAllInstitutions(USER_ID);
    }

    @Test
    void shouldGetInstitutionById() throws Exception {
        // Arrange
        when(institutionService.getInstitutionById(1L)).thenReturn(systemInstitutionResponse);

        // Act & Assert
        mockMvc.perform(
                        get("/api/v1/institutions/{id}", 1L)
                                .with(authentication(createAuthentication(USER_ID))))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.name").value("BNP Paribas"))
                .andExpect(jsonPath("$.bic").value("BNPAFRPP"))
                .andExpect(jsonPath("$.country").value("FR"))
                .andExpect(jsonPath("$.isSystem").value(true));

        verify(institutionService).getInstitutionById(1L);
    }

    @Test
    void shouldReturn404WhenInstitutionNotFound() throws Exception {
        // Arrange
        when(institutionService.getInstitutionById(999L))
                .thenThrow(new InstitutionNotFoundException(999L));

        // Act & Assert
        mockMvc.perform(
                        get("/api/v1/institutions/{id}", 999L)
                                .header("Accept-Language", "en")
                                .with(authentication(createAuthentication(USER_ID))))
                .andDo(print())
                .andExpect(status().isNotFound())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.message").value("Institution not found with id: 999"));

        verify(institutionService).getInstitutionById(999L);
    }

    @Test
    void shouldGetInstitutionsByCountry() throws Exception {
        // Arrange
        List<InstitutionResponse> frenchInstitutions =
                List.of(systemInstitutionResponse, customInstitutionResponse);
        when(institutionService.getInstitutionsByCountry("FR", USER_ID))
                .thenReturn(frenchInstitutions);

        // Act & Assert
        mockMvc.perform(
                        get("/api/v1/institutions/country/{country}", "FR")
                                .with(authentication(createAuthentication(USER_ID))))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].country").value("FR"))
                .andExpect(jsonPath("$[1].country").value("FR"));

        verify(institutionService).getInstitutionsByCountry("FR", USER_ID);
    }

    @Test
    void shouldGetSystemInstitutions() throws Exception {
        // Arrange
        List<InstitutionResponse> systemInstitutions = List.of(systemInstitutionResponse);
        when(institutionService.getSystemInstitutions()).thenReturn(systemInstitutions);

        // Act & Assert
        mockMvc.perform(
                        get("/api/v1/institutions/system")
                                .with(authentication(createAuthentication(USER_ID))))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].isSystem").value(true));

        verify(institutionService).getSystemInstitutions();
    }

    @Test
    void shouldGetCustomInstitutions() throws Exception {
        // Arrange
        List<InstitutionResponse> customInstitutions = List.of(customInstitutionResponse);
        when(institutionService.getCustomInstitutions(USER_ID)).thenReturn(customInstitutions);

        // Act & Assert
        mockMvc.perform(
                        get("/api/v1/institutions/custom")
                                .with(authentication(createAuthentication(USER_ID))))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].isSystem").value(false));

        verify(institutionService).getCustomInstitutions(USER_ID);
    }

    @Test
    void shouldSearchInstitutions() throws Exception {
        // Arrange
        List<InstitutionResponse> searchResults = List.of(systemInstitutionResponse);
        when(institutionService.searchInstitutions("BNP", USER_ID)).thenReturn(searchResults);

        // Act & Assert
        mockMvc.perform(
                        get("/api/v1/institutions/search")
                                .param("query", "BNP")
                                .with(authentication(createAuthentication(USER_ID))))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].name").value("BNP Paribas"));

        verify(institutionService).searchInstitutions("BNP", USER_ID);
    }

    @Test
    void shouldGetCountries() throws Exception {
        // Arrange
        List<String> countries = List.of("FR", "DE", "IT");
        when(institutionService.getCountries(USER_ID)).thenReturn(countries);

        // Act & Assert
        mockMvc.perform(
                        get("/api/v1/institutions/countries")
                                .with(authentication(createAuthentication(USER_ID))))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$", containsInAnyOrder("FR", "DE", "IT")));

        verify(institutionService).getCountries(USER_ID);
    }

    @Test
    void shouldCreateInstitutionSuccessfully() throws Exception {
        // Arrange
        InstitutionResponse createdResponse =
                InstitutionResponse.builder()
                        .id(3L)
                        .name("New Bank")
                        .bic("NEWBFRPP")
                        .country("FR")
                        .logo("newlogo")
                        .isSystem(false)
                        .createdAt(LocalDateTime.now())
                        .updatedAt(LocalDateTime.now())
                        .build();

        when(institutionService.createInstitution(any(InstitutionRequest.class), eq(USER_ID)))
                .thenReturn(createdResponse);

        // Act & Assert
        mockMvc.perform(
                        post("/api/v1/institutions")
                                .with(csrf())
                                .with(authentication(createAuthentication(USER_ID)))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(validRequest)))
                .andDo(print())
                .andExpect(status().isCreated())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").value(3))
                .andExpect(jsonPath("$.name").value("New Bank"))
                .andExpect(jsonPath("$.isSystem").value(false));

        verify(institutionService).createInstitution(any(InstitutionRequest.class), eq(USER_ID));
    }

    @Test
    void shouldReturn400ForInvalidRequest() throws Exception {
        // Arrange
        InstitutionRequest invalidRequest =
                InstitutionRequest.builder()
                        .name("") // Invalid: blank name
                        .bic("INVALID") // Invalid: BIC too short
                        .country("FRA") // Invalid: country too long
                        .build();

        // Act & Assert
        mockMvc.perform(
                        post("/api/v1/institutions")
                                .with(csrf())
                                .with(authentication(createAuthentication(USER_ID)))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(invalidRequest)))
                .andDo(print())
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.message").exists());

        verify(institutionService, never()).createInstitution(any(InstitutionRequest.class), any());
    }

    @Test
    void shouldUpdateInstitutionSuccessfully() throws Exception {
        // Arrange
        InstitutionResponse updatedResponse =
                InstitutionResponse.builder()
                        .id(2L)
                        .name("Updated Bank")
                        .bic("UPDTFRPP")
                        .country("FR")
                        .logo("updatedlogo")
                        .isSystem(false)
                        .createdAt(customInstitutionResponse.getCreatedAt())
                        .updatedAt(LocalDateTime.now())
                        .build();

        when(institutionService.updateInstitution(
                        eq(2L), any(InstitutionRequest.class), eq(USER_ID)))
                .thenReturn(updatedResponse);

        // Act & Assert
        mockMvc.perform(
                        put("/api/v1/institutions/{id}", 2L)
                                .with(csrf())
                                .with(authentication(createAuthentication(USER_ID)))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(validRequest)))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").value(2))
                .andExpect(jsonPath("$.name").value("Updated Bank"));

        verify(institutionService)
                .updateInstitution(eq(2L), any(InstitutionRequest.class), eq(USER_ID));
    }

    @Test
    void shouldReturn404WhenUpdatingNonExistentInstitution() throws Exception {
        // Arrange
        when(institutionService.updateInstitution(
                        eq(999L), any(InstitutionRequest.class), eq(USER_ID)))
                .thenThrow(new InstitutionNotFoundException(999L));

        // Act & Assert
        mockMvc.perform(
                        put("/api/v1/institutions/{id}", 999L)
                                .header("Accept-Language", "en")
                                .with(csrf())
                                .with(authentication(createAuthentication(USER_ID)))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(validRequest)))
                .andDo(print())
                .andExpect(status().isNotFound())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.message").value("Institution not found with id: 999"));

        verify(institutionService)
                .updateInstitution(eq(999L), any(InstitutionRequest.class), eq(USER_ID));
    }

    @Test
    void shouldReturn400WhenUpdatingSystemInstitution() throws Exception {
        // Arrange
        when(institutionService.updateInstitution(
                        eq(1L), any(InstitutionRequest.class), eq(USER_ID)))
                .thenThrow(new IllegalStateException("Cannot update system institutions"));

        // Act & Assert
        mockMvc.perform(
                        put("/api/v1/institutions/{id}", 1L)
                                .with(csrf())
                                .with(authentication(createAuthentication(USER_ID)))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(validRequest)))
                .andDo(print())
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.message").value("Cannot update system institutions"));

        verify(institutionService)
                .updateInstitution(eq(1L), any(InstitutionRequest.class), eq(USER_ID));
    }

    @Test
    void shouldDeleteInstitutionSuccessfully() throws Exception {
        // Arrange
        doNothing().when(institutionService).deleteInstitution(2L, USER_ID);

        // Act & Assert
        mockMvc.perform(
                        delete("/api/v1/institutions/{id}", 2L)
                                .with(csrf())
                                .with(authentication(createAuthentication(USER_ID))))
                .andDo(print())
                .andExpect(status().isNoContent());

        verify(institutionService).deleteInstitution(2L, USER_ID);
    }

    @Test
    void shouldReturn404WhenDeletingNonExistentInstitution() throws Exception {
        // Arrange
        doThrow(new InstitutionNotFoundException(999L))
                .when(institutionService)
                .deleteInstitution(999L, USER_ID);

        // Act & Assert
        mockMvc.perform(
                        delete("/api/v1/institutions/{id}", 999L)
                                .header("Accept-Language", "en")
                                .with(csrf())
                                .with(authentication(createAuthentication(USER_ID))))
                .andDo(print())
                .andExpect(status().isNotFound())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.message").value("Institution not found with id: 999"));

        verify(institutionService).deleteInstitution(999L, USER_ID);
    }

    @Test
    void shouldReturn400WhenDeletingSystemInstitution() throws Exception {
        // Arrange
        doThrow(new IllegalStateException("Cannot delete system institutions"))
                .when(institutionService)
                .deleteInstitution(1L, USER_ID);

        // Act & Assert
        mockMvc.perform(
                        delete("/api/v1/institutions/{id}", 1L)
                                .with(csrf())
                                .with(authentication(createAuthentication(USER_ID))))
                .andDo(print())
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.message").value("Cannot delete system institutions"));

        verify(institutionService).deleteInstitution(1L, USER_ID);
    }

    @Test
    void shouldReturn400WhenDeletingInstitutionInUse() throws Exception {
        // Arrange
        doThrow(
                        new IllegalStateException(
                                "Cannot delete institution that is associated with accounts"))
                .when(institutionService)
                .deleteInstitution(2L, USER_ID);

        // Act & Assert
        mockMvc.perform(
                        delete("/api/v1/institutions/{id}", 2L)
                                .with(csrf())
                                .with(authentication(createAuthentication(USER_ID))))
                .andDo(print())
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(
                        jsonPath("$.message")
                                .value(
                                        "Cannot delete institution that is associated with accounts"));

        verify(institutionService).deleteInstitution(2L, USER_ID);
    }
}
