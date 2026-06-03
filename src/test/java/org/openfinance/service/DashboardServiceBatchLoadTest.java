package org.openfinance.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.openfinance.dto.TransactionResponse;
import org.openfinance.entity.Account;
import org.openfinance.entity.Category;
import org.openfinance.entity.Transaction;
import org.openfinance.entity.TransactionType;
import org.openfinance.mapper.TransactionMapper;
import org.openfinance.repository.AccountRepository;
import org.openfinance.repository.AssetRepository;
import org.openfinance.repository.CategoryRepository;
import org.openfinance.repository.LiabilityRepository;
import org.openfinance.repository.TransactionRepository;
import org.openfinance.repository.UserRepository;
import org.openfinance.security.EncryptionService;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

/**
 * Unit tests for DashboardService batch loading functionality. Verifies that
 * REQ-3.1 performance
 * optimizations (avoiding N+1 queries) are working.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("DashboardService Batch Loading Unit Tests")
class DashboardServiceBatchLoadTest {

        private DashboardService dashboardService;

        @Mock
        private NetWorthService netWorthService;
        @Mock
        private AccountRepository accountRepository;
        @Mock
        private AssetRepository assetRepository;
        @Mock
        private LiabilityRepository liabilityRepository;
        @Mock
        private TransactionRepository transactionRepository;
        @Mock
        private CategoryRepository categoryRepository;
        @Mock
        private UserRepository userRepository;
        @Mock
        private TransactionMapper transactionMapper;
        @Mock
        private EncryptionService encryptionService;
        @Mock
        private InterestCalculatorService interestCalculatorService;
        @Mock
        private ExchangeRateService exchangeRateService;

        private final Long userId = 1L;

        @BeforeEach
        void setUp() {
                dashboardService = new DashboardService(
                                netWorthService,
                                accountRepository,
                                assetRepository,
                                liabilityRepository,
                                transactionRepository,
                                categoryRepository,
                                userRepository,
                                transactionMapper,
                                encryptionService,
                                interestCalculatorService,
                                exchangeRateService);
        }

        @Test
        @DisplayName("should batch load accounts and avoid per-row queries")
        void getRecentTransactions_batchLoadsAccounts_noPerRowQueries() {
                // Arrange
                int limit = 5;
                List<Transaction> transactions = List.of(
                                createTransaction(1L, 100L, null, null),
                                createTransaction(2L, 101L, null, null),
                                createTransaction(3L, 100L, null, null),
                                createTransaction(4L, 102L, null, null),
                                createTransaction(5L, 101L, null, null));

                when(transactionRepository.findByUserId(eq(userId), any(Pageable.class)))
                                .thenReturn(new PageImpl<>(transactions));

                when(accountRepository.findAllByIds(anyCollection()))
                                .thenReturn(
                                                List.of(
                                                                createAccount(100L, "Account 100"),
                                                                createAccount(101L, "Account 101"),
                                                                createAccount(102L, "Account 102")));

                when(transactionMapper.toResponse(any(Transaction.class)))
                                .thenAnswer(
                                                inv -> {
                                                        Transaction t = inv.getArgument(0);
                                                        TransactionResponse r = new TransactionResponse();
                                                        r.setId(t.getId());
                                                        r.setAccountId(t.getAccountId());
                                                        return r;
                                                });

                // Act
                dashboardService.getRecentTransactions(userId, limit);

                // Assert
                verify(accountRepository, never()).findById(anyLong());
                verify(accountRepository, times(1)).findAllByIds(anyCollection());

                @SuppressWarnings("unchecked")
                ArgumentCaptor<Collection<Long>> captor = ArgumentCaptor.forClass(Collection.class);
                verify(accountRepository).findAllByIds(captor.capture());
                assertThat(captor.getValue()).containsExactlyInAnyOrder(100L, 101L, 102L);
        }

        @Test
        @DisplayName("should not call repositories when transaction list is empty")
        void getRecentTransactions_emptyTransactionList_noRepositoryCallsForBatchLoad() {
                // Arrange
                when(transactionRepository.findByUserId(eq(userId), any(Pageable.class)))
                                .thenReturn(new PageImpl<>(List.of()));

                // Act
                dashboardService.getRecentTransactions(userId, 10);

                // Assert
                verify(accountRepository, never()).findAllByIds(anyCollection());
                verify(categoryRepository, never()).findAllByIds(anyCollection());
        }

        @Test
        @DisplayName("should include destination account IDs in batch load for transfers")
        void getRecentTransactions_withTransfers_includesDestinationAccountIds() {
                // Arrange
                List<Transaction> transactions = List.of(createTransaction(1L, 100L, 200L, null));

                when(transactionRepository.findByUserId(eq(userId), any(Pageable.class)))
                                .thenReturn(new PageImpl<>(transactions));

                when(accountRepository.findAllByIds(anyCollection()))
                                .thenReturn(
                                                List.of(createAccount(100L, "Source"),
                                                                createAccount(200L, "Destination")));

                when(transactionMapper.toResponse(any(Transaction.class)))
                                .thenAnswer(
                                                inv -> {
                                                        Transaction t = inv.getArgument(0);
                                                        TransactionResponse r = new TransactionResponse();
                                                        r.setId(t.getId());
                                                        r.setAccountId(t.getAccountId());
                                                        r.setToAccountId(t.getToAccountId());
                                                        return r;
                                                });

                // Act
                dashboardService.getRecentTransactions(userId, 10);

                // Assert
                @SuppressWarnings("unchecked")
                ArgumentCaptor<Collection<Long>> captor = ArgumentCaptor.forClass(Collection.class);
                verify(accountRepository).findAllByIds(captor.capture());
                assertThat(captor.getValue()).containsExactlyInAnyOrder(100L, 200L);
        }

        @Test
        @DisplayName("should batch load categories and avoid per-row queries")
        void getRecentTransactions_batchLoadCategories_noPerRowQueries() {
                // Arrange
                List<Transaction> transactions = List.of(
                                createTransaction(1L, 100L, null, 10L),
                                createTransaction(2L, 100L, null, 11L),
                                createTransaction(3L, 100L, null, 10L));

                when(transactionRepository.findByUserId(eq(userId), any(Pageable.class)))
                                .thenReturn(new PageImpl<>(transactions));

                when(accountRepository.findAllByIds(anyCollection()))
                                .thenReturn(List.of(createAccount(100L, "Acc")));
                when(categoryRepository.findAllByIds(anyCollection()))
                                .thenReturn(List.of(createCategory(10L, "Cat 10"), createCategory(11L, "Cat 11")));

                when(transactionMapper.toResponse(any())).thenReturn(new TransactionResponse());

                // Act
                dashboardService.getRecentTransactions(userId, 10);

                // Assert
                verify(categoryRepository, never()).findById(anyLong());
                verify(categoryRepository, times(1)).findAllByIds(anyCollection());

                @SuppressWarnings("unchecked")
                ArgumentCaptor<Collection<Long>> captor = ArgumentCaptor.forClass(Collection.class);
                verify(categoryRepository).findAllByIds(captor.capture());
                assertThat(captor.getValue()).containsExactlyInAnyOrder(10L, 11L);
        }

        @Test
        @DisplayName("should handle null category IDs without exception")
        void getRecentTransactions_nullCategoryId_noExceptionThrown() {
                // Arrange
                List<Transaction> transactions = List.of(createTransaction(1L, 100L, null, null));

                when(transactionRepository.findByUserId(eq(userId), any(Pageable.class)))
                                .thenReturn(new PageImpl<>(transactions));

                when(accountRepository.findAllByIds(anyCollection()))
                                .thenReturn(List.of(createAccount(100L, "Acc")));
                when(transactionMapper.toResponse(any())).thenReturn(new TransactionResponse());

                // Act & Assert
                List<TransactionResponse> result = dashboardService.getRecentTransactions(userId, 10);

                assertThat(result).hasSize(1);
                verify(categoryRepository, never()).findAllByIds(anyCollection());
        }

        @Test
        @DisplayName("should fallback to placeholder name when account not found in map")
        void getRecentTransactions_accountNotInMap_fallsBackToPlaceholderName() {
                // Arrange
                List<Transaction> transactions = List.of(createTransaction(1L, 999L, null, null));

                when(transactionRepository.findByUserId(eq(userId), any(Pageable.class)))
                                .thenReturn(new PageImpl<>(transactions));

                // Return empty list so account 999 is "not found"
                when(accountRepository.findAllByIds(anyCollection())).thenReturn(List.of());

                when(transactionMapper.toResponse(any(Transaction.class)))
                                .thenAnswer(
                                                inv -> {
                                                        Transaction t = inv.getArgument(0);
                                                        TransactionResponse r = new TransactionResponse();
                                                        r.setId(t.getId());
                                                        r.setAccountId(t.getAccountId());
                                                        return r;
                                                });

                // Act
                List<TransactionResponse> result = dashboardService.getRecentTransactions(userId, 10);

                // Assert
                assertThat(result.get(0).getAccountName()).isEqualTo("Account #999");
        }

        private Transaction createTransaction(
                        Long id, Long accountId, Long toAccountId, Long categoryId) {
                return Transaction.builder()
                                .id(id)
                                .userId(userId)
                                .accountId(accountId)
                                .toAccountId(toAccountId)
                                .categoryId(categoryId)
                                .type(TransactionType.EXPENSE)
                                .amount(new BigDecimal("100.00"))
                                .currency("USD")
                                .date(LocalDate.now())
                                .isDeleted(false)
                                .build();
        }

        private Account createAccount(Long id, String name) {
                return Account.builder().id(id).name(name).build();
        }

        private Category createCategory(Long id, String name) {
                return Category.builder().id(id).name(name).build();
        }
}
