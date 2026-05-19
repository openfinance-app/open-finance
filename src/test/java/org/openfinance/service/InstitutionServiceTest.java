package org.openfinance.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.openfinance.dto.InstitutionRequest;
import org.openfinance.dto.InstitutionResponse;
import org.openfinance.entity.Institution;
import org.openfinance.exception.InstitutionNotFoundException;
import org.openfinance.repository.InstitutionRepository;

/**
 * Unit tests for InstitutionService.
 *
 * <p>Tests business logic for institution CRUD operations, including system institution protection
 * and validation.
 */
@ExtendWith(MockitoExtension.class)
class InstitutionServiceTest {

    @Mock private InstitutionRepository institutionRepository;

    @Mock private OperationHistoryService operationHistoryService;

    @Mock private LogoFetchService logoFetchService;

    @InjectMocks private InstitutionService institutionService;

    private Institution systemInstitution;
    private Institution customInstitution;
    private InstitutionRequest validRequest;
    private static final Long USER_ID = 1L;

    @BeforeEach
    void setUp() {
        systemInstitution =
                Institution.builder()
                        .id(1L)
                        .name("BNP Paribas")
                        .bic("BNPAFRPP")
                        .country("FR")
                        .logo("base64logo")
                        .isSystem(true)
                        .createdAt(LocalDateTime.now().minusDays(1))
                        .updatedAt(LocalDateTime.now().minusDays(1))
                        .build();

        customInstitution =
                Institution.builder()
                        .id(2L)
                        .name("My Custom Bank")
                        .bic("CUSTFRPP")
                        .country("FR")
                        .logo("customlogo")
                        .isSystem(false)
                        .userId(USER_ID)
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

    @Test
    void shouldGetAllInstitutions() {
        // Arrange
        List<Institution> institutions = List.of(systemInstitution, customInstitution);
        when(institutionRepository.findAllByUser(USER_ID)).thenReturn(institutions);

        // Act
        List<InstitutionResponse> result = institutionService.getAllInstitutions(USER_ID);

        // Assert
        assertThat(result).hasSize(2);
        assertThat(result.get(0).getId()).isEqualTo(1L);
        assertThat(result.get(1).getId()).isEqualTo(2L);
        verify(institutionRepository).findAllByUser(USER_ID);
    }

    @Test
    void shouldGetInstitutionByIdWhenFound() {
        // Arrange
        when(institutionRepository.findById(1L)).thenReturn(Optional.of(systemInstitution));

        // Act
        InstitutionResponse result = institutionService.getInstitutionById(1L);

        // Assert
        assertThat(result.getId()).isEqualTo(1L);
        assertThat(result.getName()).isEqualTo("BNP Paribas");
        assertThat(result.getIsSystem()).isTrue();
        verify(institutionRepository).findById(1L);
    }

    @Test
    void shouldThrowWhenInstitutionNotFound() {
        // Arrange
        when(institutionRepository.findById(999L)).thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(
                InstitutionNotFoundException.class,
                () -> institutionService.getInstitutionById(999L));
        verify(institutionRepository).findById(999L);
    }

    @Test
    void shouldGetInstitutionsByCountry() {
        // Arrange
        List<Institution> frenchInstitutions = List.of(systemInstitution, customInstitution);
        when(institutionRepository.findByCountryAndUser("FR", USER_ID))
                .thenReturn(frenchInstitutions);

        // Act
        List<InstitutionResponse> result =
                institutionService.getInstitutionsByCountry("FR", USER_ID);

        // Assert
        assertThat(result).hasSize(2);
        assertThat(result.get(0).getCountry()).isEqualTo("FR");
        verify(institutionRepository).findByCountryAndUser("FR", USER_ID);
    }

    @Test
    void shouldGetSystemInstitutions() {
        // Arrange
        List<Institution> systemInstitutions = List.of(systemInstitution);
        when(institutionRepository.findByIsSystemTrueOrderByCountryAscNameAsc())
                .thenReturn(systemInstitutions);

        // Act
        List<InstitutionResponse> result = institutionService.getSystemInstitutions();

        // Assert
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getIsSystem()).isTrue();
        verify(institutionRepository).findByIsSystemTrueOrderByCountryAscNameAsc();
    }

    @Test
    void shouldGetCustomInstitutions() {
        // Arrange
        List<Institution> customInstitutions = List.of(customInstitution);
        when(institutionRepository.findByIsSystemFalseAndUserIdOrderByNameAsc(USER_ID))
                .thenReturn(customInstitutions);

        // Act
        List<InstitutionResponse> result = institutionService.getCustomInstitutions(USER_ID);

        // Assert
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getIsSystem()).isFalse();
        verify(institutionRepository).findByIsSystemFalseAndUserIdOrderByNameAsc(USER_ID);
    }

    @Test
    void shouldSearchInstitutions() {
        // Arrange
        List<Institution> searchResults = List.of(systemInstitution);
        when(institutionRepository.searchByNameAndUser("BNP", USER_ID)).thenReturn(searchResults);

        // Act
        List<InstitutionResponse> result = institutionService.searchInstitutions("BNP", USER_ID);

        // Assert
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getName()).isEqualTo("BNP Paribas");
        verify(institutionRepository).searchByNameAndUser("BNP", USER_ID);
    }

    @Test
    void shouldGetCountries() {
        // Arrange
        List<String> countries = List.of("FR", "DE", "IT");
        when(institutionRepository.findDistinctCountriesByUser(USER_ID)).thenReturn(countries);

        // Act
        List<String> result = institutionService.getCountries(USER_ID);

        // Assert
        assertThat(result).containsExactly("FR", "DE", "IT");
        verify(institutionRepository).findDistinctCountriesByUser(USER_ID);
    }

    @Test
    void shouldCreateInstitutionSuccessfully() {
        // Arrange
        Institution savedInstitution =
                Institution.builder()
                        .id(3L)
                        .name("New Bank")
                        .bic("NEWBFRPP")
                        .country("FR")
                        .logo("newlogo")
                        .isSystem(false)
                        .createdAt(LocalDateTime.now())
                        .updatedAt(LocalDateTime.now())
                        .build();

        when(institutionRepository.save(any(Institution.class))).thenReturn(savedInstitution);

        // Act
        InstitutionResponse result = institutionService.createInstitution(validRequest, USER_ID);

        // Assert
        assertThat(result.getId()).isEqualTo(3L);
        assertThat(result.getName()).isEqualTo("New Bank");
        assertThat(result.getIsSystem()).isFalse();
        verify(institutionRepository).save(any(Institution.class));
    }

    @Test
    void shouldUpdateInstitutionSuccessfully() {
        // Arrange
        InstitutionRequest updateRequest =
                InstitutionRequest.builder()
                        .name("Updated Bank")
                        .bic("UPDTFRPP")
                        .country("FR")
                        .logo("updatedlogo")
                        .build();

        Institution updatedInstitution =
                Institution.builder()
                        .id(2L)
                        .name("Updated Bank")
                        .bic("UPDTFRPP")
                        .country("FR")
                        .logo("updatedlogo")
                        .isSystem(false)
                        .createdAt(customInstitution.getCreatedAt())
                        .updatedAt(LocalDateTime.now())
                        .build();

        when(institutionRepository.findById(2L)).thenReturn(Optional.of(customInstitution));
        when(institutionRepository.save(any(Institution.class))).thenReturn(updatedInstitution);

        // Act
        InstitutionResponse result =
                institutionService.updateInstitution(2L, updateRequest, USER_ID);

        // Assert
        assertThat(result.getName()).isEqualTo("Updated Bank");
        assertThat(result.getBic()).isEqualTo("UPDTFRPP");
        verify(institutionRepository).findById(2L);
        verify(institutionRepository).save(any(Institution.class));
    }

    @Test
    void shouldThrowWhenUpdatingNonExistentInstitution() {
        // Arrange
        when(institutionRepository.findById(999L)).thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(
                InstitutionNotFoundException.class,
                () -> institutionService.updateInstitution(999L, validRequest, USER_ID));
        verify(institutionRepository).findById(999L);
        verify(institutionRepository, never()).save(any(Institution.class));
    }

    @Test
    void shouldThrowWhenUpdatingSystemInstitution() {
        // Arrange
        when(institutionRepository.findById(1L)).thenReturn(Optional.of(systemInstitution));

        // Act & Assert
        assertThrows(
                IllegalStateException.class,
                () -> institutionService.updateInstitution(1L, validRequest, USER_ID));
        verify(institutionRepository).findById(1L);
        verify(institutionRepository, never()).save(any(Institution.class));
    }

    @Test
    void shouldDeleteInstitutionSuccessfully() {
        // Arrange
        when(institutionRepository.findById(2L)).thenReturn(Optional.of(customInstitution));
        when(institutionRepository.isInUse(2L)).thenReturn(false);

        // Act
        institutionService.deleteInstitution(2L, USER_ID);

        // Assert
        verify(institutionRepository).findById(2L);
        verify(institutionRepository).isInUse(2L);
        verify(institutionRepository).delete(customInstitution);
    }

    @Test
    void shouldThrowWhenDeletingNonExistentInstitution() {
        // Arrange
        when(institutionRepository.findById(999L)).thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(
                InstitutionNotFoundException.class,
                () -> institutionService.deleteInstitution(999L, USER_ID));
        verify(institutionRepository).findById(999L);
        verify(institutionRepository, never()).delete(any(Institution.class));
    }

    @Test
    void shouldThrowWhenDeletingSystemInstitution() {
        // Arrange
        when(institutionRepository.findById(1L)).thenReturn(Optional.of(systemInstitution));

        // Act & Assert
        assertThrows(
                IllegalStateException.class,
                () -> institutionService.deleteInstitution(1L, USER_ID));
        verify(institutionRepository).findById(1L);
        verify(institutionRepository, never()).isInUse(anyLong());
        verify(institutionRepository, never()).delete(any(Institution.class));
    }

    @Test
    void shouldThrowWhenInstitutionInUse() {
        // Arrange
        when(institutionRepository.findById(2L)).thenReturn(Optional.of(customInstitution));
        when(institutionRepository.isInUse(2L)).thenReturn(true);

        // Act & Assert
        assertThrows(
                IllegalStateException.class,
                () -> {
                    institutionService.deleteInstitution(2L, USER_ID);
                });
        verify(institutionRepository).findById(2L);
        verify(institutionRepository).isInUse(2L);
        verify(institutionRepository, never()).delete(any(Institution.class));
    }

    @Test
    void shouldReturnTrueWhenInstitutionExists() {
        // Arrange
        when(institutionRepository.existsById(1L)).thenReturn(true);

        // Act
        boolean result = institutionService.existsById(1L);

        // Assert
        assertThat(result).isTrue();
        verify(institutionRepository).existsById(1L);
    }

    @Test
    void shouldReturnFalseWhenInstitutionDoesNotExist() {
        // Arrange
        when(institutionRepository.existsById(999L)).thenReturn(false);

        // Act
        boolean result = institutionService.existsById(999L);

        // Assert
        assertThat(result).isFalse();
        verify(institutionRepository).existsById(999L);
    }

    @Test
    @org.junit.jupiter.api.DisplayName("createInstitution fetches logo when request has no logo")
    void shouldFetchLogoWhenCreatingInstitutionWithNoLogo() {
        // Arrange
        InstitutionRequest noLogoRequest =
                InstitutionRequest.builder()
                        .name("New Bank")
                        .bic("NEWBFRPP")
                        .country("FR")
                        .logo(null)
                        .build();

        String fetchedLogo = "data:image/png;base64,AAAA";
        when(logoFetchService.fetchLogo("New Bank")).thenReturn(Optional.of(fetchedLogo));

        Institution savedInstitution =
                Institution.builder()
                        .id(3L)
                        .name("New Bank")
                        .logo(fetchedLogo)
                        .isSystem(false)
                        .createdAt(LocalDateTime.now())
                        .updatedAt(LocalDateTime.now())
                        .build();
        when(institutionRepository.save(any(Institution.class))).thenReturn(savedInstitution);

        // Act
        InstitutionResponse result = institutionService.createInstitution(noLogoRequest, USER_ID);

        // Assert
        verify(logoFetchService).fetchLogo("New Bank");
        assertThat(result.getLogo()).isEqualTo(fetchedLogo);
    }

    @Test
    @org.junit.jupiter.api.DisplayName(
            "createInstitution skips logo fetch when logo is already provided")
    void shouldNotFetchLogoWhenLogoAlreadyProvided() {
        // Arrange
        InstitutionRequest requestWithLogo =
                InstitutionRequest.builder()
                        .name("New Bank")
                        .bic("NEWBFRPP")
                        .country("FR")
                        .logo("data:image/png;base64,EXISTING")
                        .build();

        Institution savedInstitution =
                Institution.builder()
                        .id(3L)
                        .name("New Bank")
                        .logo("data:image/png;base64,EXISTING")
                        .isSystem(false)
                        .createdAt(LocalDateTime.now())
                        .updatedAt(LocalDateTime.now())
                        .build();
        when(institutionRepository.save(any(Institution.class))).thenReturn(savedInstitution);

        // Act
        institutionService.createInstitution(requestWithLogo, USER_ID);

        // Assert
        verify(logoFetchService, never()).fetchLogo(any());
    }

    @Test
    @org.junit.jupiter.api.DisplayName("createInstitution saves null logo when fetch returns empty")
    void shouldSaveWithNullLogoWhenFetchFails() {
        // Arrange
        InstitutionRequest noLogoRequest =
                InstitutionRequest.builder().name("Unknown Corp").logo(null).build();

        when(logoFetchService.fetchLogo("Unknown Corp")).thenReturn(Optional.empty());

        Institution savedInstitution =
                Institution.builder()
                        .id(4L)
                        .name("Unknown Corp")
                        .logo(null)
                        .isSystem(false)
                        .createdAt(LocalDateTime.now())
                        .updatedAt(LocalDateTime.now())
                        .build();
        when(institutionRepository.save(any(Institution.class))).thenReturn(savedInstitution);

        // Act
        InstitutionResponse result = institutionService.createInstitution(noLogoRequest, USER_ID);

        // Assert
        verify(logoFetchService).fetchLogo("Unknown Corp");
        assertThat(result.getLogo()).isNull();
    }
}
