package org.openfinance.service;

import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.openfinance.config.EncryptionProperties;
import org.openfinance.dto.AccountRequest;
import org.openfinance.dto.AccountResponse;
import org.openfinance.entity.Account;
import org.openfinance.entity.AccountType;
import org.openfinance.entity.User;
import org.openfinance.exception.AccountHasTransactionsException;
import org.openfinance.exception.AccountNotFoundException;
import org.openfinance.mapper.AccountMapper;
import org.openfinance.repository.AccountRepository;
import org.openfinance.repository.AssetRepository;
import org.openfinance.repository.InstitutionRepository;
import org.openfinance.repository.InterestRateVariationRepository;
import org.openfinance.repository.TransactionRepository;
import org.openfinance.repository.UserRepository;
import org.openfinance.security.EncryptionContext;
import org.openfinance.security.EncryptionService;

/** Unit tests for AccountService. */
@ExtendWith(MockitoExtension.class)
@DisplayName("AccountService Unit Tests")
class AccountServiceTest {

    @Mock private AccountRepository accountRepository;

    @Mock private AssetRepository assetRepository;

    @Mock private AccountMapper accountMapper;

    @Mock private EncryptionService encryptionService;

    @Mock private TransactionRepository transactionRepository;

    @Mock private UserRepository userRepository;

    @Mock private ExchangeRateService exchangeRateService;

    @Mock private InstitutionService institutionService;

    @Mock private InstitutionRepository institutionRepository;

    @Mock private InterestRateVariationRepository interestRateVariationRepository;

    @Mock private OperationHistoryService operationHistoryService;

    @Mock private SearchTokenService searchTokenService;

    @Mock private EncryptionProperties encryptionProperties;

    @InjectMocks private AccountService accountService;

    @BeforeEach
    void setUp() {
        EncryptionContext.setKey(new SecretKeySpec(new byte[32], "AES"));
        lenient().when(encryptionProperties.isEnabled()).thenReturn(true);
    }

    @AfterEach
    void tearDown() {
        EncryptionContext.clear();
    }

    @Test
    @DisplayName("Should create account successfully and encrypt sensitive fields")
    void shouldCreateAccountSuccessfully() {
        // Arrange
        AccountRequest req =
                AccountRequest.builder()
                        .name("My Account")
                        .type(AccountType.CHECKING)
                        .currency("USD")
                        .initialBalance(new BigDecimal("100.00"))
                        .description("desc")
                        .build();

        Account mapped =
                Account.builder()
                        .currency("USD")
                        .type(AccountType.CHECKING)
                        .balance(new BigDecimal("100.00"))
                        .isActive(true)
                        .build();

        Account saved =
                Account.builder()
                        .id(10L)
                        .userId(1L)
                        .currency("USD")
                        .type(AccountType.CHECKING)
                        .balance(new BigDecimal("100.00"))
                        .name("My Account")
                        .description("desc")
                        .isActive(true)
                        .build();

        AccountResponse resp =
                AccountResponse.builder()
                        .id(10L)
                        .name("My Account")
                        .type(AccountType.CHECKING)
                        .currency("USD")
                        .balance(new BigDecimal("100.00"))
                        .description("desc")
                        .isActive(true)
                        .build();

        when(accountMapper.toEntity(req)).thenReturn(mapped);
        when(accountRepository.save(any(Account.class))).thenReturn(saved);
        when(accountMapper.toResponse(saved)).thenReturn(resp);

        // Act
        AccountResponse created = accountService.createAccount(1L, req);

        // Assert
        assertThat(created).isNotNull();
        assertThat(created.getId()).isEqualTo(10L);
        assertThat(created.getName()).isEqualTo("My Account");
        verify(accountRepository).save(any(Account.class));
    }

    @Test
    @DisplayName("Should create account without encryption key when encryption is disabled")
    void shouldCreateAccountWithoutEncryptionKeyWhenEncryptionDisabled() {
        EncryptionContext.clear();
        when(encryptionProperties.isEnabled()).thenReturn(false);

        AccountRequest req =
                AccountRequest.builder()
                        .name("Cash Wallet")
                        .type(AccountType.CASH)
                        .currency("USD")
                        .initialBalance(new BigDecimal("25.00"))
                        .description("Plaintext")
                        .build();

        Account mapped =
                Account.builder()
                        .currency("USD")
                        .type(AccountType.CASH)
                        .balance(new BigDecimal("25.00"))
                        .isActive(true)
                        .build();

        Account saved =
                Account.builder()
                        .id(12L)
                        .userId(1L)
                        .currency("USD")
                        .type(AccountType.CASH)
                        .balance(new BigDecimal("25.00"))
                        .name("Cash Wallet")
                        .description("Plaintext")
                        .isActive(true)
                        .build();

        AccountResponse response =
                AccountResponse.builder()
                        .id(12L)
                        .type(AccountType.CASH)
                        .currency("USD")
                        .balance(new BigDecimal("25.00"))
                        .isActive(true)
                        .build();

        when(accountMapper.toEntity(req)).thenReturn(mapped);
        when(accountRepository.save(any(Account.class))).thenReturn(saved);
        when(accountMapper.toResponse(saved)).thenReturn(response);

        AccountResponse created = accountService.createAccount(1L, req);

        assertThat(created.getName()).isEqualTo("Cash Wallet");
        assertThat(created.getDescription()).isEqualTo("Plaintext");
        verify(accountRepository).save(any(Account.class));
    }

    @Test
    @DisplayName("Should validate null parameters on createAccount")
    void shouldValidateNullParametersOnCreate() {
        AccountRequest req = AccountRequest.builder().build();

        assertThrows(IllegalArgumentException.class, () -> accountService.createAccount(null, req));
        assertThrows(IllegalArgumentException.class, () -> accountService.createAccount(1L, null));
    }

    @Test
    @DisplayName("Should update account successfully")
    void shouldUpdateAccountSuccessfully() {
        // Arrange
        AccountRequest req =
                AccountRequest.builder()
                        .name("Updated")
                        .type(AccountType.SAVINGS)
                        .currency("EUR")
                        .initialBalance(new BigDecimal("50.00"))
                        .description("newdesc")
                        .build();

        Account existing =
                Account.builder()
                        .id(5L)
                        .userId(2L)
                        .name("Old Account")
                        .description("Old Desc")
                        .balance(new BigDecimal("10.00"))
                        .isActive(true)
                        .type(AccountType.CHECKING)
                        .currency("USD")
                        .build();

        Account saved =
                Account.builder()
                        .id(5L)
                        .userId(2L)
                        .name("Updated")
                        .description("newdesc")
                        .balance(new BigDecimal("50.00"))
                        .isActive(true)
                        .type(AccountType.SAVINGS)
                        .currency("EUR")
                        .build();

        AccountResponse response =
                AccountResponse.builder()
                        .id(5L)
                        .name("Updated")
                        .description("newdesc")
                        .balance(new BigDecimal("50.00"))
                        .currency("EUR")
                        .type(AccountType.SAVINGS)
                        .isActive(true)
                        .build();

        when(accountRepository.findByIdAndUserId(5L, 2L)).thenReturn(Optional.of(existing));
        // mapper.updateEntityFromRequest is void - let it be
        doAnswer(
                        invocation -> {
                            // simulate updating balance/type/currency
                            AccountRequest r = invocation.getArgument(0);
                            Account acc = invocation.getArgument(1);
                            acc.setBalance(r.getInitialBalance());
                            acc.setType(r.getType());
                            acc.setCurrency(r.getCurrency());
                            return null;
                        })
                .when(accountMapper)
                .updateEntityFromRequest(any(AccountRequest.class), any(Account.class));

        when(accountRepository.save(any(Account.class))).thenReturn(saved);
        when(accountMapper.toResponse(saved)).thenReturn(response);

        // Act
        AccountResponse updated = accountService.updateAccount(5L, 2L, req);

        // Assert
        assertThat(updated).isNotNull();
        assertThat(updated.getId()).isEqualTo(5L);
        assertThat(updated.getName()).isEqualTo("Updated");
        verify(accountRepository).findByIdAndUserId(5L, 2L);
        verify(accountRepository).save(any(Account.class));
    }

    @Test
    @DisplayName("Should throw AccountNotFoundException when updating non-existent account")
    void shouldThrowWhenUpdatingNotFound() {
        when(accountRepository.findByIdAndUserId(99L, 1L)).thenReturn(Optional.empty());

        AccountRequest req =
                AccountRequest.builder()
                        .name("x")
                        .type(AccountType.CASH)
                        .currency("USD")
                        .initialBalance(BigDecimal.ZERO)
                        .build();

        assertThatThrownBy(() -> accountService.updateAccount(99L, 1L, req))
                .isInstanceOf(AccountNotFoundException.class);
    }

    @Test
    @DisplayName("Should soft-delete account successfully when no transactions")
    void shouldSoftDeleteAccount() {
        Account existing =
                Account.builder().id(7L).userId(3L).name("My Account").isActive(true).build();
        when(accountRepository.findByIdAndUserId(7L, 3L)).thenReturn(Optional.of(existing));
        when(transactionRepository.countByAccountId(7L)).thenReturn(0L);
        when(accountRepository.save(any(Account.class))).thenAnswer(i -> i.getArgument(0));

        accountService.deleteAccount(7L, 3L);

        assertThat(existing.getIsActive()).isFalse();
        verify(accountRepository).save(existing);
        verify(transactionRepository).countByAccountId(7L);
    }

    @Test
    @DisplayName("Should throw AccountNotFoundException when deleting non-existent account")
    void shouldThrowWhenDeletingNotFound() {
        when(accountRepository.findByIdAndUserId(100L, 1L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> accountService.deleteAccount(100L, 1L))
                .isInstanceOf(AccountNotFoundException.class);
    }

    @Test
    @DisplayName(
            "Should throw AccountHasTransactionsException when deleting account with transactions")
    void shouldThrowWhenDeletingAccountWithTransactions() {
        Account existing =
                Account.builder().id(5L).userId(2L).name("My Checking").isActive(true).build();
        when(accountRepository.findByIdAndUserId(5L, 2L)).thenReturn(Optional.of(existing));
        when(transactionRepository.countByAccountId(5L)).thenReturn(3L);

        assertThatThrownBy(() -> accountService.deleteAccount(5L, 2L))
                .isInstanceOf(AccountHasTransactionsException.class)
                .hasMessageContaining("Cannot delete account 'My Checking'")
                .hasMessageContaining("3 active transactions");

        verify(transactionRepository).countByAccountId(5L);
        verify(accountRepository, never()).save(any(Account.class));
    }

    @Test
    @DisplayName("Should retrieve account by id and decrypt sensitive fields")
    void shouldGetAccountByIdSuccessfully() {
        Account acc =
                Account.builder()
                        .id(11L)
                        .userId(4L)
                        .name("MyName")
                        .description("MyDesc")
                        .balance(new BigDecimal("123.45"))
                        .currency("USD")
                        .type(AccountType.INVESTMENT)
                        .build();

        AccountResponse response = AccountResponse.builder().id(11L).build();

        when(accountRepository.findByIdAndUserId(11L, 4L)).thenReturn(Optional.of(acc));
        when(accountMapper.toResponse(acc)).thenReturn(response);

        AccountResponse got = accountService.getAccountById(11L, 4L);

        assertThat(got).isNotNull();
        assertThat(got.getName()).isEqualTo("MyName");
        assertThat(got.getDescription()).isEqualTo("MyDesc");
    }

    @Test
    @DisplayName("Should throw when getAccountById not found")
    void shouldThrowWhenGetByIdNotFound() {
        when(accountRepository.findByIdAndUserId(50L, 2L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> accountService.getAccountById(50L, 2L))
                .isInstanceOf(AccountNotFoundException.class);
    }

    @Test
    @DisplayName("Should return list of accounts for user")
    void shouldGetAccountsByUserId() {
        Account a1 =
                Account.builder()
                        .id(1L)
                        .userId(9L)
                        .name("One")
                        .description(null)
                        .isActive(true)
                        .build();
        Account a2 =
                Account.builder()
                        .id(2L)
                        .userId(9L)
                        .name("Two")
                        .description("Desc")
                        .isActive(true)
                        .build();

        when(accountRepository.findByUserIdAndIsActive(9L, true)).thenReturn(List.of(a1, a2));
        when(accountMapper.toResponse(a1)).thenReturn(AccountResponse.builder().id(1L).build());
        when(accountMapper.toResponse(a2)).thenReturn(AccountResponse.builder().id(2L).build());

        List<AccountResponse> results = accountService.getAccountsByUserId(9L);

        assertThat(results).hasSize(2);
    }

    @Test
    @DisplayName("Should return empty list when user has no accounts")
    void shouldReturnEmptyListWhenNoAccounts() {
        when(accountRepository.findByUserIdAndIsActive(8L, true)).thenReturn(List.of());
        List<AccountResponse> results = accountService.getAccountsByUserId(8L);
        assertThat(results).isEmpty();
    }

    @Test
    @DisplayName("Should return account balance")
    void shouldGetAccountBalance() {
        Account acc =
                Account.builder().id(20L).userId(6L).balance(new BigDecimal("999.99")).build();
        when(accountRepository.findByIdAndUserId(20L, 6L)).thenReturn(Optional.of(acc));
        BigDecimal bal = accountService.getAccountBalance(20L, 6L);
        assertThat(bal).isEqualByComparingTo(new BigDecimal("999.99"));
    }

    @Test
    @DisplayName("EncryptionService round-trip should preserve plaintext")
    void encryptionRoundTrip() {
        // This test uses the real EncryptionService implementation to verify
        // round-trip.
        EncryptionService real = new EncryptionService("AES/GCM/NoPadding", 12, 128);
        byte[] keyBytes = new byte[16];
        for (int i = 0; i < keyBytes.length; i++) keyBytes[i] = (byte) (i + 1);
        SecretKey key = new SecretKeySpec(keyBytes, "AES");

        String plaintext = "Sensitive Account Name";
        String cipher = real.encrypt(plaintext, key);
        String decrypted = real.decrypt(cipher, key);

        assertThat(decrypted).isEqualTo(plaintext);
    }

    @Test
    @DisplayName("Should calculate basic balance history correctly")
    void shouldCalculateBasicBalanceHistory() {
        // Arrange
        Account account =
                Account.builder()
                        .id(1L)
                        .userId(1L)
                        .name("Test Account")
                        .type(AccountType.CHECKING)
                        .openingBalance(new BigDecimal("100.00"))
                        .openingDate(java.time.LocalDate.of(2023, 1, 1))
                        .build();

        List<org.openfinance.entity.Transaction> transactions =
                List.of(
                        org.openfinance.entity.Transaction.builder()
                                .id(1L)
                                .accountId(1L)
                                .amount(new BigDecimal("50.00"))
                                .date(java.time.LocalDate.of(2023, 1, 5))
                                .build(),
                        org.openfinance.entity.Transaction.builder()
                                .id(2L)
                                .accountId(1L)
                                .amount(new BigDecimal("25.00"))
                                .date(java.time.LocalDate.of(2023, 1, 10))
                                .build());

        when(accountRepository.findByIdAndUserId(1L, 1L)).thenReturn(Optional.of(account));
        when(transactionRepository
                        .findByAccountIdAndTransactionDateBetweenOrderByTransactionDateAsc(
                                eq(1L),
                                any(java.time.LocalDate.class),
                                any(java.time.LocalDate.class)))
                .thenReturn(transactions);

        // Act
        List<org.openfinance.dto.BalanceHistoryPoint> history =
                accountService.getAccountBalanceHistory(1L, 1L, "ALL");

        // Assert
        assertThat(history).hasSize(3);
        assertThat(history.get(0).date()).isEqualTo(java.time.LocalDate.of(2023, 1, 1));
        assertThat(history.get(0).balance()).isEqualByComparingTo(new BigDecimal("100.00"));
        assertThat(history.get(1).date()).isEqualTo(java.time.LocalDate.of(2023, 1, 5));
        assertThat(history.get(1).balance()).isEqualByComparingTo(new BigDecimal("150.00"));
        assertThat(history.get(2).date()).isEqualTo(java.time.LocalDate.of(2023, 1, 10));
        assertThat(history.get(2).balance()).isEqualByComparingTo(new BigDecimal("175.00"));
    }

    @Test
    @DisplayName("Should handle multiple transactions on same day")
    void shouldHandleMultipleTransactionsOnSameDay() {
        // Arrange
        Account account =
                Account.builder()
                        .id(1L)
                        .userId(1L)
                        .name("Test Account")
                        .type(AccountType.CHECKING)
                        .openingBalance(new BigDecimal("100.00"))
                        .openingDate(java.time.LocalDate.of(2023, 1, 1))
                        .build();

        List<org.openfinance.entity.Transaction> transactions =
                List.of(
                        org.openfinance.entity.Transaction.builder()
                                .id(1L)
                                .accountId(1L)
                                .amount(new BigDecimal("20.00"))
                                .date(java.time.LocalDate.of(2023, 1, 5))
                                .build(),
                        org.openfinance.entity.Transaction.builder()
                                .id(2L)
                                .accountId(1L)
                                .amount(new BigDecimal("30.00"))
                                .date(java.time.LocalDate.of(2023, 1, 5))
                                .build(),
                        org.openfinance.entity.Transaction.builder()
                                .id(3L)
                                .accountId(1L)
                                .amount(new BigDecimal("10.00"))
                                .date(java.time.LocalDate.of(2023, 1, 10))
                                .build());

        when(accountRepository.findByIdAndUserId(1L, 1L)).thenReturn(Optional.of(account));
        when(transactionRepository
                        .findByAccountIdAndTransactionDateBetweenOrderByTransactionDateAsc(
                                eq(1L),
                                any(java.time.LocalDate.class),
                                any(java.time.LocalDate.class)))
                .thenReturn(transactions);

        // Act
        List<org.openfinance.dto.BalanceHistoryPoint> history =
                accountService.getAccountBalanceHistory(1L, 1L, "ALL");

        // Assert
        assertThat(history).hasSize(3);
        assertThat(history.get(0).date()).isEqualTo(java.time.LocalDate.of(2023, 1, 1));
        assertThat(history.get(0).balance()).isEqualByComparingTo(new BigDecimal("100.00"));
        assertThat(history.get(1).date()).isEqualTo(java.time.LocalDate.of(2023, 1, 5));
        assertThat(history.get(1).balance())
                .isEqualByComparingTo(new BigDecimal("150.00")); // 100 + 20 + 30
        assertThat(history.get(2).date()).isEqualTo(java.time.LocalDate.of(2023, 1, 10));
        assertThat(history.get(2).balance())
                .isEqualByComparingTo(new BigDecimal("160.00")); // 150 + 10
    }

    @Test
    @DisplayName("Should reverse transaction signs for credit card accounts")
    void shouldReverseSignsForCreditCardAccounts() {
        // Arrange
        Account account =
                Account.builder()
                        .id(1L)
                        .userId(1L)
                        .name("Test Account")
                        .type(AccountType.CREDIT_CARD)
                        .openingBalance(new BigDecimal("0.00"))
                        .openingDate(java.time.LocalDate.of(2023, 1, 1))
                        .build();

        List<org.openfinance.entity.Transaction> transactions =
                List.of(
                        org.openfinance.entity.Transaction.builder()
                                .id(1L)
                                .accountId(1L)
                                .amount(new BigDecimal("-100.00")) // Charge stored as negative
                                .date(java.time.LocalDate.of(2023, 1, 5))
                                .build(),
                        org.openfinance.entity.Transaction.builder()
                                .id(2L)
                                .accountId(1L)
                                .amount(new BigDecimal("50.00")) // Payment stored as positive
                                .date(java.time.LocalDate.of(2023, 1, 10))
                                .build());

        when(accountRepository.findByIdAndUserId(1L, 1L)).thenReturn(Optional.of(account));
        when(transactionRepository
                        .findByAccountIdAndTransactionDateBetweenOrderByTransactionDateAsc(
                                eq(1L),
                                any(java.time.LocalDate.class),
                                any(java.time.LocalDate.class)))
                .thenReturn(transactions);

        // Act
        List<org.openfinance.dto.BalanceHistoryPoint> history =
                accountService.getAccountBalanceHistory(1L, 1L, "ALL");

        // Assert
        assertThat(history).hasSize(3);
        assertThat(history.get(0).date()).isEqualTo(java.time.LocalDate.of(2023, 1, 1));
        assertThat(history.get(0).balance()).isEqualByComparingTo(new BigDecimal("0.00"));
        assertThat(history.get(1).date()).isEqualTo(java.time.LocalDate.of(2023, 1, 5));
        assertThat(history.get(1).balance())
                .isEqualByComparingTo(new BigDecimal("100.00")); // 0 + (-(-100)) = +100
        assertThat(history.get(2).date()).isEqualTo(java.time.LocalDate.of(2023, 1, 10));
        assertThat(history.get(2).balance())
                .isEqualByComparingTo(new BigDecimal("50.00")); // 100 + (-50) = +50
    }

    @Test
    @DisplayName("Should calculate different periods correctly")
    void shouldCalculateDifferentPeriods() {
        // Arrange
        Account account =
                Account.builder()
                        .id(1L)
                        .userId(1L)
                        .name("Test Account")
                        .type(AccountType.CHECKING)
                        .openingBalance(new BigDecimal("100.00"))
                        .openingDate(java.time.LocalDate.of(2023, 1, 1))
                        .build();

        List<org.openfinance.entity.Transaction> transactions =
                List.of(
                        org.openfinance.entity.Transaction.builder()
                                .id(1L)
                                .accountId(1L)
                                .amount(new BigDecimal("50.00"))
                                .date(java.time.LocalDate.of(2023, 6, 1)) // Within 1M period
                                .build());

        when(accountRepository.findByIdAndUserId(1L, 1L)).thenReturn(Optional.of(account));
        when(transactionRepository
                        .findByAccountIdAndTransactionDateBetweenOrderByTransactionDateAsc(
                                eq(1L),
                                any(java.time.LocalDate.class),
                                any(java.time.LocalDate.class)))
                .thenReturn(transactions);

        // Test 1M period
        List<org.openfinance.dto.BalanceHistoryPoint> history1M =
                accountService.getAccountBalanceHistory(1L, 1L, "1M");
        assertThat(history1M).hasSize(2); // opening + transaction

        // Test 3M period
        List<org.openfinance.dto.BalanceHistoryPoint> history3M =
                accountService.getAccountBalanceHistory(1L, 1L, "3M");
        assertThat(history3M).hasSize(2);

        // Test ALL period
        List<org.openfinance.dto.BalanceHistoryPoint> historyALL =
                accountService.getAccountBalanceHistory(1L, 1L, "ALL");
        assertThat(historyALL).hasSize(2);
    }

    @Test
    @DisplayName("Should handle empty transactions list")
    void shouldHandleEmptyTransactionsList() {
        // Arrange
        Account account =
                Account.builder()
                        .id(1L)
                        .userId(1L)
                        .name("Test Account")
                        .type(AccountType.CHECKING)
                        .openingBalance(new BigDecimal("100.00"))
                        .openingDate(java.time.LocalDate.of(2023, 1, 1))
                        .build();

        when(accountRepository.findByIdAndUserId(1L, 1L)).thenReturn(Optional.of(account));
        when(transactionRepository
                        .findByAccountIdAndTransactionDateBetweenOrderByTransactionDateAsc(
                                eq(1L),
                                any(java.time.LocalDate.class),
                                any(java.time.LocalDate.class)))
                .thenReturn(List.of());

        // Act
        List<org.openfinance.dto.BalanceHistoryPoint> history =
                accountService.getAccountBalanceHistory(1L, 1L, "ALL");

        // Assert
        assertThat(history).hasSize(1);
        assertThat(history.get(0).date()).isEqualTo(java.time.LocalDate.of(2023, 1, 1));
        assertThat(history.get(0).balance()).isEqualByComparingTo(new BigDecimal("100.00"));
    }

    @Test
    @DisplayName("Should throw IllegalArgumentException for null parameters")
    void shouldThrowForNullParametersInBalanceHistory() {
        assertThrows(
                IllegalArgumentException.class,
                () -> accountService.getAccountBalanceHistory(null, 1L, "ALL"));
        assertThrows(
                IllegalArgumentException.class,
                () -> accountService.getAccountBalanceHistory(1L, null, "ALL"));
        assertThrows(
                IllegalArgumentException.class,
                () -> accountService.getAccountBalanceHistory(1L, 1L, null));
        assertThrows(
                IllegalArgumentException.class,
                () -> accountService.getAccountBalanceHistory(1L, 1L, ""));
    }

    @Test
    @DisplayName("Should throw AccountNotFoundException for invalid account ID")
    void shouldThrowForInvalidAccountIdInBalanceHistory() {
        when(accountRepository.findByIdAndUserId(99L, 1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> accountService.getAccountBalanceHistory(99L, 1L, "ALL"))
                .isInstanceOf(AccountNotFoundException.class);
    }

    // Tests for closeAccount method

    @Test
    @DisplayName("Should close active account successfully")
    void shouldCloseActiveAccountSuccessfully() {
        // Arrange
        Account activeAccount =
                Account.builder().id(10L).userId(1L).name("Test Account").isActive(true).build();

        when(accountRepository.findByIdAndUserId(10L, 1L)).thenReturn(Optional.of(activeAccount));
        when(accountRepository.save(any(Account.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        accountService.closeAccount(10L, 1L);

        // Assert
        assertThat(activeAccount.getIsActive()).isFalse();
        verify(accountRepository).findByIdAndUserId(10L, 1L);
        verify(accountRepository).save(activeAccount);
    }

    @Test
    @DisplayName("Should handle already closed account gracefully")
    void shouldHandleAlreadyClosedAccountGracefully() {
        // Arrange
        Account closedAccount =
                Account.builder().id(11L).userId(1L).name("Test Account").isActive(false).build();

        when(accountRepository.findByIdAndUserId(11L, 1L)).thenReturn(Optional.of(closedAccount));

        // Act
        accountService.closeAccount(11L, 1L);

        // Assert
        assertThat(closedAccount.getIsActive()).isFalse(); // Remains false
        verify(accountRepository).findByIdAndUserId(11L, 1L);
        verify(accountRepository, never()).save(any(Account.class)); // No save since already closed
    }

    @Test
    @DisplayName("Should throw AccountNotFoundException when closing non-existent account")
    void shouldThrowWhenClosingNonExistentAccount() {
        when(accountRepository.findByIdAndUserId(99L, 1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> accountService.closeAccount(99L, 1L))
                .isInstanceOf(AccountNotFoundException.class);
    }

    @Test
    @DisplayName("Should throw IllegalArgumentException for null parameters when closing account")
    void shouldThrowForNullParametersWhenClosingAccount() {
        assertThrows(IllegalArgumentException.class, () -> accountService.closeAccount(null, 1L));
        assertThrows(IllegalArgumentException.class, () -> accountService.closeAccount(10L, null));
    }

    // Tests for reopenAccount method

    @Test
    @DisplayName("Should reopen closed account successfully")
    void shouldReopenClosedAccountSuccessfully() {
        // Arrange
        Account closedAccount =
                Account.builder().id(12L).userId(1L).name("Test Account").isActive(false).build();

        when(accountRepository.findByIdAndUserId(12L, 1L)).thenReturn(Optional.of(closedAccount));
        when(accountRepository.save(any(Account.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        accountService.reopenAccount(12L, 1L);

        // Assert
        assertThat(closedAccount.getIsActive()).isTrue();
        verify(accountRepository).findByIdAndUserId(12L, 1L);
        verify(accountRepository).save(closedAccount);
    }

    @Test
    @DisplayName("Should handle already active account gracefully")
    void shouldHandleAlreadyActiveAccountGracefully() {
        // Arrange
        Account activeAccount =
                Account.builder().id(13L).userId(1L).name("Test Account").isActive(true).build();

        when(accountRepository.findByIdAndUserId(13L, 1L)).thenReturn(Optional.of(activeAccount));

        // Act
        accountService.reopenAccount(13L, 1L);

        // Assert
        assertThat(activeAccount.getIsActive()).isTrue(); // Remains true
        verify(accountRepository).findByIdAndUserId(13L, 1L);
        verify(accountRepository, never()).save(any(Account.class)); // No save since already active
    }

    @Test
    @DisplayName("Should throw AccountNotFoundException when reopening non-existent account")
    void shouldThrowWhenReopeningNonExistentAccount() {
        when(accountRepository.findByIdAndUserId(99L, 1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> accountService.reopenAccount(99L, 1L))
                .isInstanceOf(AccountNotFoundException.class);
    }

    @Test
    @DisplayName("Should throw IllegalArgumentException for null parameters when reopening account")
    void shouldThrowForNullParametersWhenReopeningAccount() {
        assertThrows(IllegalArgumentException.class, () -> accountService.reopenAccount(null, 1L));
        assertThrows(IllegalArgumentException.class, () -> accountService.reopenAccount(12L, null));
    }

    // Tests for permanentDeleteAccount method

    @Test
    @DisplayName("Should permanently delete account and associated transactions")
    void shouldPermanentlyDeleteAccountAndTransactions() {
        // Arrange
        Account accountToDelete =
                Account.builder().id(14L).userId(1L).name("Test Account").isActive(true).build();

        List<org.openfinance.entity.Transaction> fromTransactions =
                List.of(
                        org.openfinance.entity.Transaction.builder().id(1L).accountId(14L).build(),
                        org.openfinance.entity.Transaction.builder().id(2L).accountId(14L).build());

        List<org.openfinance.entity.Transaction> toTransactions =
                List.of(
                        org.openfinance.entity.Transaction.builder()
                                .id(3L)
                                .toAccountId(14L)
                                .build());

        when(accountRepository.findByIdAndUserId(14L, 1L)).thenReturn(Optional.of(accountToDelete));
        when(transactionRepository.findByAccountId(14L)).thenReturn(fromTransactions);
        when(transactionRepository.findByToAccountId(14L)).thenReturn(toTransactions);

        // Act
        accountService.permanentDeleteAccount(14L, 1L);

        // Assert
        verify(accountRepository).findByIdAndUserId(14L, 1L);
        verify(transactionRepository).findByAccountId(14L);
        verify(transactionRepository).findByToAccountId(14L);
        verify(transactionRepository).deleteAll(fromTransactions);
        verify(transactionRepository)
                .deleteAll(toTransactions); // Since ids are different, all toTransactions are
        // additional
        verify(accountRepository).delete(accountToDelete);
    }

    @Test
    @DisplayName("Should handle permanent deletion of account with no transactions")
    void shouldHandlePermanentDeletionWithNoTransactions() {
        // Arrange
        Account accountToDelete =
                Account.builder().id(15L).userId(1L).name("Test Account").isActive(true).build();

        when(accountRepository.findByIdAndUserId(15L, 1L)).thenReturn(Optional.of(accountToDelete));
        when(transactionRepository.findByAccountId(15L)).thenReturn(List.of());
        when(transactionRepository.findByToAccountId(15L)).thenReturn(List.of());

        // Act
        accountService.permanentDeleteAccount(15L, 1L);

        // Assert
        verify(accountRepository).findByIdAndUserId(15L, 1L);
        verify(transactionRepository).findByAccountId(15L);
        verify(transactionRepository).findByToAccountId(15L);
        verify(transactionRepository, never()).deleteAll(anyList()); // No transactions to delete
        verify(accountRepository).delete(accountToDelete);
    }

    @Test
    @DisplayName(
            "Should throw AccountNotFoundException when permanently deleting non-existent account")
    void shouldThrowWhenPermanentlyDeletingNonExistentAccount() {
        when(accountRepository.findByIdAndUserId(99L, 1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> accountService.permanentDeleteAccount(99L, 1L))
                .isInstanceOf(AccountNotFoundException.class);
    }

    @Test
    @DisplayName(
            "Should throw IllegalArgumentException for null parameters when permanently deleting account")
    void shouldThrowForNullParametersWhenPermanentlyDeletingAccount() {
        assertThrows(
                IllegalArgumentException.class,
                () -> accountService.permanentDeleteAccount(null, 1L));
        assertThrows(
                IllegalArgumentException.class,
                () -> accountService.permanentDeleteAccount(14L, null));
    }

    // Currency conversion tests (REQ-3.1, REQ-3.5, REQ-3.6)

    @Test
    @DisplayName("Should populate conversion fields when currency different from base currency")
    void shouldPopulateConversionFieldsWhenCurrencyDifferentFromBase() {
        // Arrange
        Account account =
                Account.builder()
                        .id(1L)
                        .userId(1L)
                        .name("My Account")
                        .balance(new BigDecimal("100.00"))
                        .currency("EUR")
                        .build();

        User user = User.builder().id(1L).baseCurrency("USD").build();

        AccountResponse response =
                AccountResponse.builder()
                        .id(1L)
                        .name("My Account")
                        .balance(new BigDecimal("100.00"))
                        .currency("EUR")
                        .build();

        when(accountRepository.findByIdAndUserId(1L, 1L)).thenReturn(Optional.of(account));
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(exchangeRateService.getExchangeRate("EUR", "USD", null))
                .thenReturn(new BigDecimal("1.1"));
        when(exchangeRateService.convert(new BigDecimal("100.00"), "EUR", "USD"))
                .thenReturn(new BigDecimal("110.00"));
        when(accountMapper.toResponse(account)).thenReturn(response);

        // Act
        AccountResponse result = accountService.getAccountById(1L, 1L);

        // Assert
        assertThat(result.getIsConverted()).isTrue();
        assertThat(result.getBaseCurrency()).isEqualTo("USD");
        assertThat(result.getBalanceInBaseCurrency())
                .isEqualByComparingTo(new BigDecimal("110.00"));
        assertThat(result.getExchangeRate()).isEqualByComparingTo(new BigDecimal("1.1"));
        verify(userRepository).findById(1L);
        verify(exchangeRateService).convert(new BigDecimal("100.00"), "EUR", "USD");
    }

    @Test
    @DisplayName("Should fallback to native currency when exchange rate missing")
    void shouldFallbackToNativeCurrencyWhenExchangeRateMissing() {
        // Arrange
        Account account =
                Account.builder()
                        .id(1L)
                        .userId(1L)
                        .name("My Account")
                        .balance(new BigDecimal("100.00"))
                        .currency("EUR")
                        .build();

        User user = User.builder().id(1L).baseCurrency("USD").build();

        AccountResponse response =
                AccountResponse.builder()
                        .id(1L)
                        .name("My Account")
                        .balance(new BigDecimal("100.00"))
                        .currency("EUR")
                        .build();

        when(accountRepository.findByIdAndUserId(1L, 1L)).thenReturn(Optional.of(account));
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(exchangeRateService.convert(new BigDecimal("100.00"), "EUR", "USD"))
                .thenThrow(new RuntimeException("Rate not found"));
        when(accountMapper.toResponse(account)).thenReturn(response);

        // Act
        AccountResponse result = accountService.getAccountById(1L, 1L);

        // Assert
        assertThat(result.getIsConverted()).isFalse();
        assertThat(result.getBalanceInBaseCurrency())
                .isEqualByComparingTo(new BigDecimal("100.00"));
        assertThat(result.getBaseCurrency()).isEqualTo("USD");
        assertThat(result.getExchangeRate()).isNull();
        verify(userRepository).findById(1L);
        verify(exchangeRateService).convert(new BigDecimal("100.00"), "EUR", "USD");
    }

    @Test
    @DisplayName("Should not convert when currency matches base currency")
    void shouldNotConvertWhenCurrencyMatchesBaseCurrency() {
        // Arrange
        Account account =
                Account.builder()
                        .id(1L)
                        .userId(1L)
                        .name("My Account")
                        .balance(new BigDecimal("100.00"))
                        .currency("USD")
                        .build();

        User user = User.builder().id(1L).baseCurrency("USD").build();

        AccountResponse response =
                AccountResponse.builder()
                        .id(1L)
                        .name("My Account")
                        .balance(new BigDecimal("100.00"))
                        .currency("USD")
                        .build();

        when(accountRepository.findByIdAndUserId(1L, 1L)).thenReturn(Optional.of(account));
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(accountMapper.toResponse(account)).thenReturn(response);

        // Act
        AccountResponse result = accountService.getAccountById(1L, 1L);

        // Assert
        assertThat(result.getIsConverted()).isFalse();
        assertThat(result.getBalanceInBaseCurrency())
                .isEqualByComparingTo(new BigDecimal("100.00"));
        assertThat(result.getBaseCurrency()).isEqualTo("USD");
        assertThat(result.getExchangeRate()).isNull();
        verify(userRepository).findById(1L);
        verify(exchangeRateService, never())
                .convert(any(BigDecimal.class), anyString(), anyString());
    }

    // Secondary currency conversion tests (REQ-4.1, REQ-4.5, REQ-4.6)

    @Test
    @DisplayName(
            "Should populate secondary currency fields when secondary currency is configured and differs from native")
    void shouldPopulateSecondaryCurrencyFieldsWhenConfigured() {
        // Arrange — native=EUR, base=USD, secondary=JPY
        Account account =
                Account.builder()
                        .id(1L)
                        .userId(1L)
                        .name("My Account")
                        .balance(new BigDecimal("100.00"))
                        .currency("EUR")
                        .build();

        User user = User.builder().id(1L).baseCurrency("USD").secondaryCurrency("JPY").build();

        AccountResponse response =
                AccountResponse.builder()
                        .id(1L)
                        .name("My Account")
                        .balance(new BigDecimal("100.00"))
                        .currency("EUR")
                        .build();

        when(accountRepository.findByIdAndUserId(1L, 1L)).thenReturn(Optional.of(account));
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(accountMapper.toResponse(account)).thenReturn(response);
        when(exchangeRateService.getExchangeRate("EUR", "USD", null))
                .thenReturn(new BigDecimal("1.1"));
        when(exchangeRateService.convert(new BigDecimal("100.00"), "EUR", "USD"))
                .thenReturn(new BigDecimal("110.00"));
        when(exchangeRateService.getExchangeRate("EUR", "JPY", null))
                .thenReturn(new BigDecimal("155.0"));
        when(exchangeRateService.convert(new BigDecimal("100.00"), "EUR", "JPY"))
                .thenReturn(new BigDecimal("15500.00"));

        // Act
        AccountResponse result = accountService.getAccountById(1L, 1L);

        // Assert — Requirement REQ-4.1: secondary fields populated
        assertThat(result.getBalanceInSecondaryCurrency())
                .isEqualByComparingTo(new BigDecimal("15500.00"));
        assertThat(result.getSecondaryCurrency()).isEqualTo("JPY");
        assertThat(result.getSecondaryExchangeRate()).isEqualByComparingTo(new BigDecimal("155.0"));
        // Base conversion also works
        assertThat(result.getIsConverted()).isTrue();
        assertThat(result.getBalanceInBaseCurrency())
                .isEqualByComparingTo(new BigDecimal("110.00"));
    }

    @Test
    @DisplayName(
            "Should set secondary fields to null when native currency equals secondary currency")
    void shouldSkipSecondaryCurrencyConversionWhenNativeEqualsSecondary() {
        // Arrange — native=EUR, base=USD, secondary=EUR (same as native → no
        // conversion)
        Account account =
                Account.builder()
                        .id(1L)
                        .userId(1L)
                        .name("My Account")
                        .balance(new BigDecimal("200.00"))
                        .currency("EUR")
                        .build();

        User user = User.builder().id(1L).baseCurrency("USD").secondaryCurrency("EUR").build();

        AccountResponse response =
                AccountResponse.builder()
                        .id(1L)
                        .name("My Account")
                        .balance(new BigDecimal("200.00"))
                        .currency("EUR")
                        .build();

        when(accountRepository.findByIdAndUserId(1L, 1L)).thenReturn(Optional.of(account));
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(accountMapper.toResponse(account)).thenReturn(response);
        when(exchangeRateService.getExchangeRate("EUR", "USD", null))
                .thenReturn(new BigDecimal("1.1"));
        when(exchangeRateService.convert(new BigDecimal("200.00"), "EUR", "USD"))
                .thenReturn(new BigDecimal("220.00"));

        // Act
        AccountResponse result = accountService.getAccountById(1L, 1L);

        // Assert — Requirement REQ-4.6: no redundant conversion when native equals
        // secondary
        assertThat(result.getBalanceInSecondaryCurrency()).isNull();
        assertThat(result.getSecondaryCurrency()).isEqualTo("EUR");
        assertThat(result.getSecondaryExchangeRate()).isNull();
        // Base conversion is unaffected
        assertThat(result.getIsConverted()).isTrue();
        assertThat(result.getBalanceInBaseCurrency())
                .isEqualByComparingTo(new BigDecimal("220.00"));
        // Verify exchange rate service was NOT called for secondary conversion
        verify(exchangeRateService, never()).convert(any(BigDecimal.class), eq("EUR"), eq("EUR"));
    }

    @Test
    @DisplayName(
            "Should leave balanceInSecondaryCurrency null but set secondaryCurrency when secondary rate is unavailable")
    void shouldLeaveSecondaryAmountNullWhenSecondaryRateUnavailable() {
        // Arrange — native=EUR, base=USD, secondary=JPY — JPY rate throws
        Account account =
                Account.builder()
                        .id(1L)
                        .userId(1L)
                        .name("My Account")
                        .balance(new BigDecimal("100.00"))
                        .currency("EUR")
                        .build();

        User user = User.builder().id(1L).baseCurrency("USD").secondaryCurrency("JPY").build();

        AccountResponse response =
                AccountResponse.builder()
                        .id(1L)
                        .name("My Account")
                        .balance(new BigDecimal("100.00"))
                        .currency("EUR")
                        .build();

        when(accountRepository.findByIdAndUserId(1L, 1L)).thenReturn(Optional.of(account));
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(accountMapper.toResponse(account)).thenReturn(response);
        when(exchangeRateService.getExchangeRate("EUR", "USD", null))
                .thenReturn(new BigDecimal("1.1"));
        when(exchangeRateService.convert(new BigDecimal("100.00"), "EUR", "USD"))
                .thenReturn(new BigDecimal("110.00"));
        when(exchangeRateService.convert(new BigDecimal("100.00"), "EUR", "JPY"))
                .thenThrow(new RuntimeException("JPY rate unavailable"));

        // Act — must not throw
        AccountResponse result = accountService.getAccountById(1L, 1L);

        // Assert — Requirement REQ-4.5: graceful fallback; amount null, currency code
        // still set
        assertThat(result.getBalanceInSecondaryCurrency()).isNull();
        assertThat(result.getSecondaryCurrency()).isEqualTo("JPY");
        // Base conversion is unaffected
        assertThat(result.getIsConverted()).isTrue();
        assertThat(result.getBalanceInBaseCurrency())
                .isEqualByComparingTo(new BigDecimal("110.00"));
    }

    @Test
    @DisplayName("Should leave all secondary fields null when no secondary currency is configured")
    void shouldLeaveSecondaryFieldsNullWhenNoSecondaryCurrencyConfigured() {
        // Arrange — native=EUR, base=USD, secondary=null
        Account account =
                Account.builder()
                        .id(1L)
                        .userId(1L)
                        .name("My Account")
                        .balance(new BigDecimal("100.00"))
                        .currency("EUR")
                        .build();

        User user =
                User.builder()
                        .id(1L)
                        .baseCurrency("USD")
                        .build(); // secondaryCurrency is null by default

        AccountResponse response =
                AccountResponse.builder()
                        .id(1L)
                        .name("My Account")
                        .balance(new BigDecimal("100.00"))
                        .currency("EUR")
                        .build();

        when(accountRepository.findByIdAndUserId(1L, 1L)).thenReturn(Optional.of(account));
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(accountMapper.toResponse(account)).thenReturn(response);
        when(exchangeRateService.getExchangeRate("EUR", "USD", null))
                .thenReturn(new BigDecimal("1.1"));
        when(exchangeRateService.convert(new BigDecimal("100.00"), "EUR", "USD"))
                .thenReturn(new BigDecimal("110.00"));

        // Act
        AccountResponse result = accountService.getAccountById(1L, 1L);

        // Assert — Requirement REQ-4.1: all secondary fields must be null
        assertThat(result.getBalanceInSecondaryCurrency()).isNull();
        assertThat(result.getSecondaryCurrency()).isNull();
        assertThat(result.getSecondaryExchangeRate()).isNull();
        // Base conversion works normally
        assertThat(result.getIsConverted()).isTrue();
        assertThat(result.getBalanceInBaseCurrency())
                .isEqualByComparingTo(new BigDecimal("110.00"));
    }
}
