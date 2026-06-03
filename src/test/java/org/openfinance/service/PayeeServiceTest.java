package org.openfinance.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.openfinance.dto.PayeeRequest;
import org.openfinance.dto.PayeeResponse;
import org.openfinance.entity.Payee;
import org.openfinance.repository.CategoryRepository;
import org.openfinance.repository.PayeeRepository;
import org.openfinance.repository.TransactionRepository;

/**
 * Unit tests for PayeeService — focuses on logo auto-fetch integration.
 *
 * <p>
 * Validates that {@link LogoFetchService} is called at the right times in
 * {@code createPayee}
 * and {@code findOrCreatePayee}.
 */
@ExtendWith(MockitoExtension.class)
class PayeeServiceTest {

        @Mock
        private PayeeRepository payeeRepository;

        @Mock
        private CategoryRepository categoryRepository;

        @Mock
        private TransactionRepository transactionRepository;

        @Mock
        private LogoFetchService logoFetchService;

        @Mock
        private SearchTokenService searchTokenService;

        @InjectMocks
        private PayeeService payeeService;

        private Payee existingPayee;
        private static final Long USER_ID = 1L;

        @BeforeEach
        void setUp() {
                existingPayee = Payee.builder()
                                .id(1L)
                                .name("Netflix")
                                .logo("data:image/png;base64,EXISTING")
                                .isSystem(false)
                                .isActive(true)
                                .userId(USER_ID)
                                .createdAt(LocalDateTime.now().minusDays(1))
                                .updatedAt(LocalDateTime.now().minusDays(1))
                                .build();
        }

        // -------------------------------------------------------------------------
        // createPayee
        // -------------------------------------------------------------------------

        @Test
        @DisplayName("createPayee fetches logo when request has no logo")
        void shouldFetchLogoWhenCreatePayeeHasNoLogo() {
                // Arrange
                PayeeRequest request = PayeeRequest.builder().name("Spotify").logo(null).build();
                when(payeeRepository.findAllByUser(USER_ID)).thenReturn(Collections.emptyList());

                String fetchedLogo = "data:image/png;base64,FETCHED";
                when(logoFetchService.fetchLogo("Spotify")).thenReturn(Optional.of(fetchedLogo));

                Payee saved = Payee.builder()
                                .id(2L)
                                .name("Spotify")
                                .logo(fetchedLogo)
                                .isSystem(false)
                                .isActive(true)
                                .createdAt(LocalDateTime.now())
                                .updatedAt(LocalDateTime.now())
                                .build();
                when(payeeRepository.save(any(Payee.class))).thenReturn(saved);

                // Act
                PayeeResponse result = payeeService.createPayee(request, USER_ID);

                // Assert
                verify(logoFetchService).fetchLogo("Spotify");
                assertThat(result.getLogo()).isEqualTo(fetchedLogo);
        }

        @Test
        @DisplayName("createPayee skips logo fetch when logo is already provided")
        void shouldNotFetchLogoWhenCreatePayeeHasLogo() {
                // Arrange
                PayeeRequest request = PayeeRequest.builder().name("Spotify").logo("data:image/png;base64,MANUAL")
                                .build();
                when(payeeRepository.findAllByUser(USER_ID)).thenReturn(Collections.emptyList());

                Payee saved = Payee.builder()
                                .id(2L)
                                .name("Spotify")
                                .logo("data:image/png;base64,MANUAL")
                                .isSystem(false)
                                .isActive(true)
                                .createdAt(LocalDateTime.now())
                                .updatedAt(LocalDateTime.now())
                                .build();
                when(payeeRepository.save(any(Payee.class))).thenReturn(saved);

                // Act
                payeeService.createPayee(request, USER_ID);

                // Assert
                verify(logoFetchService, never()).fetchLogo(any());
        }

        @Test
        @DisplayName("createPayee saves null logo when fetch returns empty")
        void shouldSaveWithNullLogoWhenFetchFails() {
                // Arrange
                PayeeRequest request = PayeeRequest.builder().name("UnknownCorp").logo(null).build();
                when(payeeRepository.findAllByUser(USER_ID)).thenReturn(Collections.emptyList());
                when(logoFetchService.fetchLogo("UnknownCorp")).thenReturn(Optional.empty());

                Payee saved = Payee.builder()
                                .id(3L)
                                .name("UnknownCorp")
                                .logo(null)
                                .isSystem(false)
                                .isActive(true)
                                .createdAt(LocalDateTime.now())
                                .updatedAt(LocalDateTime.now())
                                .build();
                when(payeeRepository.save(any(Payee.class))).thenReturn(saved);

                // Act
                PayeeResponse result = payeeService.createPayee(request, USER_ID);

                // Assert
                verify(logoFetchService).fetchLogo("UnknownCorp");
                assertThat(result.getLogo()).isNull();
        }

        // -------------------------------------------------------------------------
        // findOrCreatePayee
        // -------------------------------------------------------------------------

        @Test
        @DisplayName("findOrCreatePayee fetches logo only for newly created payees")
        void shouldFetchLogoWhenFindOrCreateCreatesNewPayee() {
                // Arrange
                when(payeeRepository.findAllByUser(USER_ID))
                                .thenReturn(Collections.emptyList()); // not found

                String fetchedLogo = "data:image/png;base64,FETCHED";
                when(logoFetchService.fetchLogo("Deliveroo")).thenReturn(Optional.of(fetchedLogo));

                Payee saved = Payee.builder()
                                .id(5L)
                                .name("Deliveroo")
                                .logo(fetchedLogo)
                                .isSystem(false)
                                .isActive(true)
                                .createdAt(LocalDateTime.now())
                                .updatedAt(LocalDateTime.now())
                                .build();
                when(payeeRepository.save(any(Payee.class))).thenReturn(saved);

                // Act
                PayeeResponse result = payeeService.findOrCreatePayee("Deliveroo", USER_ID);

                // Assert
                verify(logoFetchService).fetchLogo("Deliveroo");
                assertThat(result.getLogo()).isEqualTo(fetchedLogo);
        }

        @Test
        @DisplayName("findOrCreatePayee skips logo fetch when payee already exists")
        void shouldNotFetchLogoWhenFindOrCreateFindsExistingPayee() {
                // Arrange
                when(payeeRepository.findAllByUser(USER_ID))
                                .thenReturn(List.of(existingPayee));

                // Act
                payeeService.findOrCreatePayee("Netflix", USER_ID);

                // Assert
                verify(logoFetchService, never()).fetchLogo(any());
        }

        @Test
        @DisplayName("findOrCreatePayee skips logo fetch when reactivating an inactive payee")
        void shouldNotFetchLogoWhenFindOrCreateReactivatesInactivePayee() {
                // Arrange
                Payee inactivePayee = Payee.builder()
                                .id(1L)
                                .name("Netflix")
                                .logo("data:image/png;base64,OLD")
                                .isSystem(false)
                                .isActive(false)
                                .userId(USER_ID)
                                .createdAt(LocalDateTime.now().minusDays(10))
                                .updatedAt(LocalDateTime.now().minusDays(10))
                                .build();
                when(payeeRepository.findAllByUser(USER_ID))
                                .thenReturn(List.of(inactivePayee));

                Payee reactivated = Payee.builder()
                                .id(1L)
                                .name("Netflix")
                                .logo("data:image/png;base64,OLD")
                                .isSystem(false)
                                .isActive(true)
                                .createdAt(inactivePayee.getCreatedAt())
                                .updatedAt(LocalDateTime.now())
                                .build();
                when(payeeRepository.save(any(Payee.class))).thenReturn(reactivated);

                // Act
                payeeService.findOrCreatePayee("Netflix", USER_ID);

                // Assert
                verify(logoFetchService, never()).fetchLogo(any());
        }
}
