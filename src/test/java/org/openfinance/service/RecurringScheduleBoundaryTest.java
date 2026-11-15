package org.openfinance.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.openfinance.dto.RecurringTransactionRequest;
import org.openfinance.dto.RecurringTransactionResponse;
import org.openfinance.entity.Account;
import org.openfinance.entity.RecurringFrequency;
import org.openfinance.entity.RecurringTransaction;
import org.openfinance.entity.TransactionType;
import org.openfinance.exception.InvalidTransactionException;
import org.openfinance.repository.AccountRepository;
import org.openfinance.repository.CategoryRepository;
import org.openfinance.repository.CurrencyRepository;
import org.openfinance.repository.RecurringTransactionRepository;
import org.openfinance.security.EncryptionService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

@ExtendWith(MockitoExtension.class)
@DisplayName("Recurring schedules preserve past history and bound backlog posting")
class RecurringScheduleBoundaryTest {
    @Mock private OperationHistoryService history;
    @Mock private RecurringTransactionRepository repository;
    @Mock private AccountRepository accounts;
    @Mock private CategoryRepository categories;
    @Mock private EncryptionService encryption;
    @Mock private TransactionService transactions;
    @Mock private RecurringOccurrenceService occurrences;
    @Mock private CurrencyRepository currencies;
    @Mock private SearchTokenService tokens;
    @InjectMocks private RecurringTransactionService service;

    private RecurringTransaction template() {
        return RecurringTransaction.builder()
                .id(1L)
                .userId(7L)
                .accountId(9L)
                .amount(new BigDecimal("100.01"))
                .currency("EUR")
                .type(TransactionType.EXPENSE)
                .description("Rent")
                .notes("Lease")
                .payee("Landlord")
                .frequency(RecurringFrequency.MONTHLY)
                .anchorDay(31)
                .nextOccurrence(LocalDate.of(2024, 2, 29))
                .endDate(LocalDate.of(2024, 3, 31))
                .isActive(true)
                .build();
    }

    private RecurringTransactionRequest.RecurringTransactionRequestBuilder request() {
        return RecurringTransactionRequest.builder()
                .accountId(9L)
                .amount(new BigDecimal("100.01"))
                .currency("EUR")
                .type(TransactionType.EXPENSE)
                .description("Edited rent")
                .frequency(RecurringFrequency.MONTHLY)
                .nextOccurrence(LocalDate.of(2024, 2, 29))
                .endDate(LocalDate.of(2024, 3, 31));
    }

    private void owned(RecurringTransaction template) {
        when(repository.findByIdAndUserId(1L, 7L)).thenReturn(Optional.of(template));
        when(accounts.findByIdAndUserId(9L, 7L))
                .thenReturn(
                        Optional.of(Account.builder().id(9L).userId(7L).name("Checking").build()));
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "create-user",
                "create-request",
                "update-id",
                "update-user",
                "update-request",
                "delete-id",
                "delete-user",
                "read-id",
                "read-user",
                "all-user",
                "active-user",
                "due-user",
                "frequency-user",
                "frequency-value",
                "pause-id",
                "pause-user",
                "resume-id",
                "resume-user",
                "process-user",
                "filter-user",
                "filter-page"
            })
    void missingScopeNeverQueriesOrChangesScheduledTransactions(String operation) {
        RecurringTransactionRequest request = request().build();
        assertThatThrownBy(
                        () -> {
                            switch (operation) {
                                case "create-user" -> service.createRecurringTransaction(
                                        null, request);
                                case "create-request" -> service.createRecurringTransaction(
                                        7L, null);
                                case "update-id" -> service.updateRecurringTransaction(
                                        null, 7L, request);
                                case "update-user" -> service.updateRecurringTransaction(
                                        1L, null, request);
                                case "update-request" -> service.updateRecurringTransaction(
                                        1L, 7L, null);
                                case "delete-id" -> service.deleteRecurringTransaction(null, 7L);
                                case "delete-user" -> service.deleteRecurringTransaction(1L, null);
                                case "read-id" -> service.getRecurringTransactionById(null, 7L);
                                case "read-user" -> service.getRecurringTransactionById(1L, null);
                                case "all-user" -> service.getAllRecurringTransactions(null);
                                case "active-user" -> service.getActiveRecurringTransactions(null);
                                case "due-user" -> service.getDueRecurringTransactions(null);
                                case "frequency-user" -> service
                                        .getRecurringTransactionsByFrequency(
                                                null, RecurringFrequency.MONTHLY);
                                case "frequency-value" -> service
                                        .getRecurringTransactionsByFrequency(7L, null);
                                case "pause-id" -> service.pauseRecurringTransaction(null, 7L);
                                case "pause-user" -> service.pauseRecurringTransaction(1L, null);
                                case "resume-id" -> service.resumeRecurringTransaction(null, 7L);
                                case "resume-user" -> service.resumeRecurringTransaction(1L, null);
                                case "process-user" -> service.processRecurringTransactionsForUser(
                                        null);
                                case "filter-user" -> service.getRecurringTransactionsWithFilters(
                                        null,
                                        null,
                                        null,
                                        null,
                                        null,
                                        null,
                                        false,
                                        PageRequest.of(0, 10));
                                case "filter-page" -> service.getRecurringTransactionsWithFilters(
                                        7L, null, null, null, null, null, false, null);
                                default -> throw new AssertionError(operation);
                            }
                        })
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(repository, accounts, occurrences, transactions);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"  ", " New detail "})
    void editingMetadataOnAnEndedScheduleKeepsItsAnchorAndNormalizesOptionalText(String text) {
        RecurringTransaction template = template();
        owned(template);
        when(repository.save(template)).thenReturn(template);
        RecurringTransactionResponse response =
                service.updateRecurringTransaction(
                        1L, 7L, request().notes(text).payee(text).build());
        assertThat(template.getAnchorDay()).isEqualTo(31);
        assertThat(template.getNextOccurrence()).isEqualTo(LocalDate.of(2024, 2, 29));
        assertThat(response.getNotes()).isEqualTo(text == null || text.isBlank() ? null : text);
        assertThat(response.getPayee())
                .isEqualTo(text == null || text.isBlank() ? null : text.trim());
        assertThat(response.getDescription()).isEqualTo("Edited rent");
        verifyNoInteractions(occurrences);
    }

    @ParameterizedTest
    @ValueSource(strings = {"frequency", "next-date", "end-date"})
    void modifyingAnyPartOfAnExpiredScheduleRequiresAValidNewEndDate(String part) {
        RecurringTransaction template = template();
        owned(template);
        RecurringTransactionRequest request = request().build();
        switch (part) {
            case "frequency" -> request.setFrequency(RecurringFrequency.WEEKLY);
            case "next-date" -> request.setNextOccurrence(LocalDate.of(2024, 3, 1));
            case "end-date" -> request.setEndDate(LocalDate.of(2024, 4, 1));
            default -> throw new AssertionError(part);
        }
        assertThatThrownBy(() -> service.updateRecurringTransaction(1L, 7L, request))
                .isInstanceOf(InvalidTransactionException.class);
        verify(repository, never()).save(any());
        assertThat(template.getAnchorDay()).isEqualTo(31);
        assertThat(template.getNextOccurrence()).isEqualTo(LocalDate.of(2024, 2, 29));
    }

    @ParameterizedTest
    @ValueSource(strings = {"frequency", "next-date"})
    void anExplicitRescheduleUsesTheNewOccurrenceDayAsAnchor(String part) {
        RecurringTransaction template = template();
        owned(template);
        when(repository.save(template)).thenReturn(template);
        LocalDate next =
                part.equals("frequency")
                        ? template.getNextOccurrence()
                        : LocalDate.now().plusDays(1).withDayOfMonth(15);
        service.updateRecurringTransaction(
                1L,
                7L,
                request()
                        .endDate(null)
                        .nextOccurrence(next)
                        .frequency(
                                part.equals("frequency")
                                        ? RecurringFrequency.QUARTERLY
                                        : RecurringFrequency.MONTHLY)
                        .build());
        assertThat(template.getAnchorDay()).isEqualTo(next.getDayOfMonth());
        assertThat(template.getNextOccurrence()).isEqualTo(next);
    }

    @ParameterizedTest
    @CsvSource({"1200,1000", "1,2", "0,1"})
    void dailyBacklogIncludesTodayAndStopsAtThePerRunLimit(int daysAgo, int count) {
        RecurringTransaction template = template();
        template.setFrequency(RecurringFrequency.DAILY);
        template.setNextOccurrence(LocalDate.now().minusDays(daysAgo));
        template.setEndDate(null);
        when(repository.findDueRecurringTransactionsByUserId(7L, LocalDate.now()))
                .thenReturn(List.of(template));
        when(occurrences.post(eq(1L), eq(7L), any())).thenReturn(true);
        RecurringTransactionService.ProcessingResult result =
                service.processRecurringTransactionsForUser(7L);
        assertThat(result.getProcessedCount()).isEqualTo(count);
        assertThat(result.getFailedCount()).isZero();
        assertThat(result.getErrors()).isEmpty();
        verify(occurrences, org.mockito.Mockito.times(count)).post(eq(1L), eq(7L), any());
        assertThat(template.getNextOccurrence())
                .isEqualTo(LocalDate.now().minusDays(daysAgo).plusDays(count));
    }

    @ParameterizedTest
    @CsvSource({"0,1", "-1,0", "1,2"})
    void anEndDateIsInclusiveAndNeverCreatesAnOccurrenceAfterIt(int endOffset, int count) {
        RecurringTransaction template = template();
        template.setFrequency(RecurringFrequency.DAILY);
        LocalDate first = LocalDate.now().minusDays(2);
        template.setNextOccurrence(first);
        template.setEndDate(first.plusDays(endOffset));
        when(repository.findDueRecurringTransactionsByUserId(7L, LocalDate.now()))
                .thenReturn(List.of(template));
        if (count > 0) when(occurrences.post(eq(1L), eq(7L), any())).thenReturn(true);
        assertThat(service.processRecurringTransactionsForUser(7L).getProcessedCount())
                .isEqualTo(count);
        verify(occurrences, org.mockito.Mockito.times(count)).post(eq(1L), eq(7L), any());
    }

    @ParameterizedTest
    @CsvSource(
            value = {
                "NULL,0,1",
                "   ,0,1",
                "rent,0,1",
                "lease,0,1",
                "landlord,0,1",
                "missing,0,0",
                "rent,5,0"
            },
            nullValues = "NULL")
    void searchMatchesDecryptedDescriptionsNotesAndPayeesBeforePagination(
            String search, int page, int expectedSize) {
        RecurringTransaction template = template();
        when(repository.findByUserIdWithFilters(7L, null, null, null, null))
                .thenReturn(List.of(template));
        Page<RecurringTransactionResponse> result =
                service.getRecurringTransactionsWithFilters(
                        7L, null, null, null, null, search, false, PageRequest.of(page, 10));
        assertThat(result.getContent()).hasSize(expectedSize);
        assertThat(result.getTotalElements()).isEqualTo("missing".equals(search) ? 0 : 1);
        if (expectedSize == 1)
            assertThat(result.getContent().getFirst().getAmount()).isEqualByComparingTo("100.01");
    }
}
