package org.openfinance.service;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.openfinance.entity.*;
import org.openfinance.repository.*;
import org.openfinance.security.EncryptionService;
import org.openfinance.service.ai.AIProvider;
import org.springframework.context.MessageSource;
import reactor.core.publisher.Mono;

/**
 * Unit tests for InsightService. Focuses on ensuring that net worth
 * calculations include Real
 * Estate and Assets.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("InsightService Unit Tests")
class InsightServiceTest {

        @Mock
        private InsightRepository insightRepository;
        @Mock
        private TransactionRepository transactionRepository;
        @Mock
        private BudgetRepository budgetRepository;
        @Mock
        private AccountRepository accountRepository;
        @Mock
        private CategoryRepository categoryRepository;
        @Mock
        private UserRepository userRepository;
        @Mock
        private UserSettingsRepository userSettingsRepository;
        @Mock
        private RecurringTransactionRepository recurringTransactionRepository;
        @Mock
        private RealEstateRepository realEstateRepository;
        @Mock
        private AssetRepository assetRepository;
        @Mock
        private LiabilityRepository liabilityRepository;
        @Mock
        private EncryptionService encryptionService;
        @Mock
        private MessageSource messageSource;
        @Mock
        private AIProvider aiProvider;
        @Spy
        private ObjectMapper objectMapper = new ObjectMapper();

        @Mock
        private OperationHistoryService operationHistoryService;

        @InjectMocks
        private InsightService insightService;

        private Long testUserId = 1L;

        @BeforeEach
        void setUp() {
                // Default mocks to prevent NPEs
                when(userSettingsRepository.findByUserId(testUserId)).thenReturn(Optional.empty());
                when(userRepository.findById(testUserId))
                                .thenReturn(Optional.of(User.builder().id(testUserId).build()));
                when(accountRepository.findByUserIdAndIsActive(testUserId, true))
                                .thenReturn(Collections.emptyList());
                when(realEstateRepository.findByUserIdAndIsActive(testUserId, true))
                                .thenReturn(Collections.emptyList());
                when(assetRepository.findByUserId(testUserId)).thenReturn(Collections.emptyList());
                when(liabilityRepository.findById(anyLong())).thenReturn(Optional.empty());
                when(transactionRepository.findByUserIdAndType(anyLong(), any()))
                                .thenReturn(Collections.emptyList());
                when(aiProvider.sendPrompt(anyString(), anyString())).thenReturn(Mono.just("{}"));
        }

        @Test
        @DisplayName("generateInsights should call repositories for Net Worth calculation")
        void shouldCallRepositoriesForNetWorthCalculation() throws Exception {
                // Arrange
                // 1. User Settings (Country: US)
                User user = User.builder().id(testUserId).build();
                UserSettings userSettings = UserSettings.builder().user(user).country("US").language("en").build();
                when(userSettingsRepository.findByUserId(testUserId)).thenReturn(Optional.of(userSettings));

                // 2. Account Balances: $10,000
                Account account = Account.builder()
                                .balance(new BigDecimal("10000.00"))
                                .currency("USD")
                                .isActive(true)
                                .build();
                when(accountRepository.findByUserIdAndIsActive(testUserId, true))
                                .thenReturn(List.of(account));

                // 3. Real Estate: Value $300,000, Mortgage $200,000 -> Equity $100,000
                RealEstateProperty property = RealEstateProperty.builder()
                                .id(1L)
                                .userId(testUserId)
                                .currentValue("300000.00")
                                .currency("USD")
                                .isActive(true)
                                .mortgageId(10L)
                                .build();
                when(realEstateRepository.findByUserIdAndIsActive(testUserId, true))
                                .thenReturn(List.of(property));

                Liability mortgage = new Liability();
                mortgage.setId(10L);
                mortgage.setCurrentBalance("200000.00");
                mortgage.setCurrency("USD");
                when(liabilityRepository.findById(10L)).thenReturn(Optional.of(mortgage));

                // 4. Other Assets: Value $5,000
                Asset asset = Asset.builder()
                                .userId(testUserId)
                                .quantity(new BigDecimal("1"))
                                .currentPrice(new BigDecimal("5000.00"))
                                .currency("USD")
                                .build();
                when(assetRepository.findByUserId(testUserId)).thenReturn(List.of(asset));

                // Mock message source to avoid breakage
                when(messageSource.getMessage(anyString(), any(), any())).thenReturn("Mocked Insight");

                // Act
                insightService.generateInsights(testUserId);

                // Assert
                verify(realEstateRepository).findByUserIdAndIsActive(testUserId, true);
                verify(assetRepository).findByUserId(testUserId);
                verify(liabilityRepository).findById(10L);
        }
}
