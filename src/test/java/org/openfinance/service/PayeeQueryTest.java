package org.openfinance.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.openfinance.dto.PayeeResponse;
import org.openfinance.entity.Category;
import org.openfinance.entity.Payee;
import org.openfinance.entity.Transaction;
import org.openfinance.repository.CategoryRepository;
import org.openfinance.repository.PayeeRepository;
import org.openfinance.repository.TransactionRepository;

@ExtendWith(MockitoExtension.class)
@DisplayName("Payee lists and statistics operate on visible decrypted records")
class PayeeQueryTest {
    @Mock private OperationHistoryService operationHistoryService;
    @Mock private PayeeRepository payeeRepository;
    @Mock private CategoryRepository categoryRepository;
    @Mock private TransactionRepository transactionRepository;
    @Mock private LogoFetchService logoFetchService;
    @Mock private SearchTokenService searchTokenService;
    @Mock private TransactionService transactionService;
    @InjectMocks private PayeeService service;

    @Test
    void listsSortDecryptedNamesAndActiveListsKeepSystemPayeesFirst() {
        Payee custom = payee(1, "alpha", false);
        Payee system = payee(2, "Zulu", true);
        Payee beta = payee(3, "Beta", false);
        when(payeeRepository.findAllByUser(7L)).thenReturn(List.of(system, beta, custom));
        when(payeeRepository.findAllActiveByUserOrderBySystemFirst(7L))
                .thenReturn(List.of(beta, custom, system));
        assertThat(service.getAllPayees(7L))
                .extracting(PayeeResponse::getName)
                .containsExactly("alpha", "Beta", "Zulu");
        assertThat(service.getActivePayees(7L))
                .extracting(PayeeResponse::getName)
                .containsExactly("Zulu", "alpha", "Beta");
    }

    @Test
    void customSystemAndCategoryListsRetainTheCorrectScopeAndCategoryDetails() {
        Category category = Category.builder().id(9L).name("Subscriptions").build();
        Payee custom = payee(1, "Netflix", false);
        custom.setDefaultCategory(category);
        when(payeeRepository.findByIsSystemFalseAndUserIdOrderByNameAsc(7L))
                .thenReturn(List.of(custom));
        when(payeeRepository.findByIsSystemTrueOrderByNameAsc())
                .thenReturn(List.of(payee(2, "Zulu", true), payee(3, "Alpha", true)));
        when(payeeRepository.findByDefaultCategoryIdAndIsActiveTrueAndUser(9L, 7L))
                .thenReturn(List.of(custom));
        assertThat(service.getCustomPayees(7L))
                .extracting(PayeeResponse::getId)
                .containsExactly(1L);
        assertThat(service.getSystemPayees())
                .extracting(PayeeResponse::getName)
                .containsExactly("Alpha", "Zulu");
        assertThat(service.getPayeesByCategoryId(9L, 7L))
                .singleElement()
                .satisfies(
                        response -> {
                            assertThat(response.getCategoryId()).isEqualTo(9L);
                            assertThat(response.getCategoryName()).isEqualTo("Subscriptions");
                        });
    }

    @Test
    void statisticsIgnoreDeletedAndUnassignedTransactionsAndKeepDecimalPrecision() {
        when(payeeRepository.findAllByUser(7L))
                .thenReturn(List.of(payee(1, "Shop", false), payee(2, "Unused", false)));
        when(transactionRepository.findByUserId(7L))
                .thenReturn(
                        List.of(
                                Transaction.builder()
                                        .payee("Shop")
                                        .amount(new BigDecimal("0.00000001"))
                                        .build(),
                                Transaction.builder()
                                        .payee("Shop")
                                        .amount(new BigDecimal("12.34"))
                                        .build(),
                                Transaction.builder()
                                        .payee("Shop")
                                        .amount(new BigDecimal("999"))
                                        .isDeleted(true)
                                        .build(),
                                Transaction.builder().amount(BigDecimal.TEN).build()));
        List<PayeeResponse> responses = service.getAllPayeesWithStats(7L);
        assertThat(responses.getFirst().getTransactionCount()).isEqualTo(2L);
        assertThat(responses.getFirst().getTotalAmount()).isEqualByComparingTo("12.34000001");
        assertThat(responses.getLast().getTransactionCount()).isZero();
        assertThat(responses.getLast().getTotalAmount()).isZero();
    }

    @Test
    void searchingNamesIsIndependentOfTheServersDefaultLocale() {
        Locale original = Locale.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"));
            when(payeeRepository.findAllActiveByUserOrderBySystemFirst(7L))
                    .thenReturn(List.of(payee(1, "IKEA", true), payee(2, "Unrelated", false)));
            assertThat(service.searchPayees("ikea", 7L))
                    .extracting(PayeeResponse::getId)
                    .containsExactly(1L);
        } finally {
            Locale.setDefault(original);
        }
    }

    @Test
    void onlySystemPayeesCanBeHiddenAndTogglingIsReversible() {
        Payee system = payee(2, "Provider", true);
        when(payeeRepository.findById(2L)).thenReturn(Optional.of(system));
        when(payeeRepository.save(system)).thenReturn(system);
        assertThat(service.togglePayeeActive(2L).getIsActive()).isFalse();
        assertThat(service.togglePayeeActive(2L).getIsActive()).isTrue();
        when(payeeRepository.findById(1L)).thenReturn(Optional.of(payee(1, "Custom", false)));
        assertThatThrownBy(() -> service.togglePayeeActive(1L))
                .isInstanceOf(IllegalStateException.class);
        verify(payeeRepository, never())
                .save(org.mockito.ArgumentMatchers.argThat(p -> p.getId().equals(1L)));
    }

    private Payee payee(long id, String name, boolean system) {
        return Payee.builder()
                .id(id)
                .name(name)
                .userId(system ? null : 7L)
                .isSystem(system)
                .isActive(true)
                .build();
    }
}
