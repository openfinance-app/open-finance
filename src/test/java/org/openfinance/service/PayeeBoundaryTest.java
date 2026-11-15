package org.openfinance.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.openfinance.dto.MetadataUpdateRequest;
import org.openfinance.dto.PayeeRequest;
import org.openfinance.dto.PayeeResponse;
import org.openfinance.entity.Category;
import org.openfinance.entity.Payee;
import org.openfinance.entity.Transaction;
import org.openfinance.exception.CategoryNotFoundException;
import org.openfinance.exception.DuplicatePayeeException;
import org.openfinance.repository.CategoryRepository;
import org.openfinance.repository.PayeeRepository;
import org.openfinance.repository.TransactionRepository;
import org.openfinance.security.EncryptionContext;

@ExtendWith(MockitoExtension.class)
@DisplayName("Payee ownership, metadata presence and encrypted search indexing")
class PayeeBoundaryTest {
    @Mock private PayeeRepository payees;
    @Mock private CategoryRepository categories;
    @Mock private TransactionRepository transactions;
    @Mock private LogoFetchService logos;
    @Mock private SearchTokenService tokens;
    @Mock private TransactionService transactionService;
    @Mock private OperationHistoryService history;
    @InjectMocks private PayeeService service;

    @AfterEach
    void clearKey() {
        EncryptionContext.clear();
    }

    private Payee payee(Long id, String name) {
        return Payee.builder().id(id).userId(7L).name(name).isSystem(false).isActive(true).build();
    }

    @ParameterizedTest
    @ValueSource(strings = {"omitted", "clear", "owned-category", "foreign-category"})
    void metadataUpdatesRespectExplicitClearsAndCategoryOwnership(String mode) {
        Payee payee = payee(1L, "Shop");
        Category original = Category.builder().id(2L).name("Original").build();
        payee.setDefaultCategory(original);
        when(payees.findById(1L)).thenReturn(Optional.of(payee));
        MetadataUpdateRequest.Payee patch = new MetadataUpdateRequest.Payee();
        if (mode.equals("clear")) patch.setCategoryId(null);
        if (mode.endsWith("category")) {
            patch.setCategoryId(3L);
            when(categories.findByIdAndUserId(3L, 7L))
                    .thenReturn(
                            mode.equals("owned-category")
                                    ? Optional.of(Category.builder().id(3L).name("New").build())
                                    : Optional.empty());
        }
        if (mode.equals("foreign-category")) {
            assertThatThrownBy(() -> service.updateMetadata(1L, 7L, patch))
                    .isInstanceOf(CategoryNotFoundException.class);
            verify(payees, never()).save(any());
            assertThat(payee.getDefaultCategory()).isSameAs(original);
        } else {
            PayeeResponse response = service.updateMetadata(1L, 7L, patch);
            assertThat(response.getCategoryId())
                    .isEqualTo(
                            mode.equals("clear") ? null : mode.equals("owned-category") ? 3L : 2L);
            assertThat(payee.getName()).isEqualTo("Shop");
            verify(payees).save(payee);
        }
        verifyNoInteractions(transactions, transactionService, tokens);
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "metadata-system",
                "metadata-foreign",
                "update-system",
                "update-foreign",
                "delete-system",
                "delete-foreign"
            })
    void aUserCannotModifySystemOrAnotherUsersPayees(String operation) {
        Payee payee = payee(1L, "Shop");
        if (operation.endsWith("system")) payee.setIsSystem(true);
        else payee.setUserId(8L);
        when(payees.findById(1L)).thenReturn(Optional.of(payee));
        assertThatThrownBy(
                        () -> {
                            if (operation.startsWith("metadata"))
                                service.updateMetadata(1L, 7L, new MetadataUpdateRequest.Payee());
                            else if (operation.startsWith("update"))
                                service.updatePayee(
                                        1L, PayeeRequest.builder().name("Changed").build(), 7L);
                            else service.deletePayee(1L, 7L);
                        })
                .isInstanceOf(IllegalStateException.class);
        verify(payees, never()).save(any());
        verify(payees, never()).delete(any());
        verifyNoInteractions(transactions, transactionService, tokens, categories, history);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "\t"})
    void emptyPayeeNamesDoNotCreateRecordsOrFetchLogos(String name) {
        assertThat(service.findOrCreatePayee(name, 7L)).isNull();
        verifyNoInteractions(payees, logos, tokens, history);
    }

    @Test
    void duplicateNamesAreCheckedCaseInsensitivelyBeforeAnyLogoLookup() {
        when(payees.findAllByUser(7L))
                .thenReturn(List.of(payee(1L, null), payee(2L, "Different"), payee(3L, "SHOP")));
        assertThatThrownBy(
                        () ->
                                service.createPayee(
                                        PayeeRequest.builder().name(" shop ").build(), 7L))
                .isInstanceOf(DuplicatePayeeException.class);
        verify(payees, never()).save(any());
        verifyNoInteractions(logos, tokens);
    }

    @Test
    void reactivationFindsAnExistingEncryptedNameWithoutCreatingADuplicate() {
        Payee inactive = payee(3L, "SHOP");
        inactive.setIsActive(false);
        when(payees.findAllByUser(7L))
                .thenReturn(List.of(payee(1L, null), payee(2L, "Different"), inactive));
        when(payees.save(inactive)).thenReturn(inactive);
        assertThat(service.findOrCreatePayee(" shop ", 7L).getId()).isEqualTo(3L);
        assertThat(inactive.getIsActive()).isTrue();
        verifyNoInteractions(logos);
    }

    @Test
    void aggregateStatisticsExcludeDeletedAndNamelessTransactionsAndKeepExactCents() {
        when(payees.findAllByUser(7L)).thenReturn(List.of(payee(1L, "Shop"), payee(2L, "Unused")));
        when(transactions.findByUserId(7L))
                .thenReturn(
                        List.of(
                                Transaction.builder()
                                        .payee("Shop")
                                        .amount(new BigDecimal("9007199254740993.25"))
                                        .build(),
                                Transaction.builder()
                                        .payee("Shop")
                                        .amount(new BigDecimal("0.01"))
                                        .isDeleted(false)
                                        .build(),
                                Transaction.builder().payee("Shop").amount(null).build(),
                                Transaction.builder()
                                        .payee("Shop")
                                        .amount(new BigDecimal("999"))
                                        .isDeleted(true)
                                        .build(),
                                Transaction.builder()
                                        .payee(null)
                                        .amount(new BigDecimal("888"))
                                        .build()));
        assertThat(service.getAllPayeesWithStats(7L))
                .satisfiesExactly(
                        shop -> {
                            assertThat(shop.getTransactionCount()).isEqualTo(3L);
                            assertThat(shop.getTotalAmount())
                                    .isEqualByComparingTo("9007199254740993.26");
                        },
                        unused -> {
                            assertThat(unused.getTransactionCount()).isZero();
                            assertThat(unused.getTotalAmount()).isEqualByComparingTo("0");
                        });
    }

    @Test
    void systemOnlyStatisticsDoNotQueryUserTransactions() {
        when(payees.findAllByUser(null)).thenReturn(List.of(payee(1L, "Shop")));
        assertThat(service.getAllPayeesWithStats(null))
                .singleElement()
                .satisfies(result -> assertThat(result.getTransactionCount()).isZero());
        verifyNoInteractions(transactions);
    }

    @ParameterizedTest
    @ValueSource(strings = {"system", "custom", "active", "all", "search"})
    void listsSortDecryptedNamesCaseInsensitivelyAndSearchSkipsMissingNames(String mode) {
        Payee unnamed = payee(1L, null), zebra = payee(2L, "zebra"), alpha = payee(3L, "Alpha");
        List<Payee> unsorted = List.of(zebra, unnamed, alpha);
        List<PayeeResponse> result;
        switch (mode) {
            case "system" -> {
                when(payees.findByIsSystemTrueOrderByNameAsc()).thenReturn(unsorted);
                result = service.getSystemPayees();
            }
            case "custom" -> {
                when(payees.findByIsSystemFalseAndUserIdOrderByNameAsc(7L)).thenReturn(unsorted);
                result = service.getCustomPayees(7L);
            }
            case "active" -> {
                when(payees.findAllActiveByUserOrderBySystemFirst(7L)).thenReturn(unsorted);
                result = service.getActivePayees(7L);
            }
            case "search" -> {
                when(payees.findAllActiveByUserOrderBySystemFirst(7L)).thenReturn(unsorted);
                result = service.searchPayees("A", 7L);
            }
            default -> {
                when(payees.findAllByUser(7L)).thenReturn(unsorted);
                result = service.getAllPayees(7L);
            }
        }
        assertThat(result)
                .extracting(PayeeResponse::getId)
                .containsExactlyElementsOf(
                        mode.equals("search") ? List.of(3L, 2L) : List.of(1L, 3L, 2L));
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void encryptedSearchIndexingUsesTheCurrentKeyButFailureDoesNotLoseThePayee(boolean fail) {
        SecretKeySpec key = new SecretKeySpec(new byte[32], "AES");
        EncryptionContext.setKey(key);
        when(payees.save(any()))
                .thenAnswer(
                        invocation -> {
                            Payee saved = invocation.getArgument(0);
                            saved.setId(4L);
                            return saved;
                        });
        if (fail)
            when(tokens.deriveSearchKey(key))
                    .thenThrow(new IllegalStateException("Index unavailable"));
        else when(tokens.deriveSearchKey(key)).thenReturn(key);
        PayeeResponse created =
                service.createPayee(
                        PayeeRequest.builder().name("Exact Shop").logo("/logo.svg").build(), 7L);
        assertThat(created.getId()).isEqualTo(4L);
        assertThat(created.getName()).isEqualTo("Exact Shop");
        if (fail) verify(tokens, never()).indexEntity(any(), any(), any(), any(), any());
        else
            verify(tokens)
                    .indexEntity(
                            org.mockito.ArgumentMatchers.eq(7L),
                            org.mockito.ArgumentMatchers.eq("PAYEE"),
                            org.mockito.ArgumentMatchers.eq(4L),
                            org.mockito.ArgumentMatchers.argThat(
                                    fields ->
                                            fields.size() == 1
                                                    && java.util.Arrays.equals(
                                                            fields.getFirst(),
                                                            new String[] {"name", "Exact Shop"})),
                            org.mockito.ArgumentMatchers.eq(key));
    }
}
