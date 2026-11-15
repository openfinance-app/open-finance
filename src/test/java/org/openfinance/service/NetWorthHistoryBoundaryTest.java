package org.openfinance.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.openfinance.entity.Account;
import org.openfinance.entity.Liability;
import org.openfinance.entity.LiabilityTranche;
import org.openfinance.entity.MovementType;
import org.openfinance.entity.Transaction;
import org.openfinance.entity.TransactionType;
import org.openfinance.repository.AccountRepository;
import org.openfinance.repository.LiabilityTrancheRepository;
import org.openfinance.repository.NetWorthRepository;
import org.openfinance.repository.TransactionRepository;

@ExtendWith(MockitoExtension.class)
@DisplayName("Net worth history respects debt start dates and dated principal movements")
class NetWorthHistoryBoundaryTest {
    @Mock private AccountRepository accounts;
    @Mock private TransactionRepository transactions;
    @Mock private LiabilityTrancheRepository tranches;
    @Mock private NetWorthRepository snapshots;
    @Mock private NetWorthSnapshotWriter writer;
    @Mock private AccountCurrencyService accountCurrency;
    @InjectMocks private NetWorthService service;
    private final LocalDate date = LocalDate.of(2025, 1, 10);

    @BeforeEach
    void nativePositions() {
        lenient()
                .when(accountCurrency.historicalPosition(any(), any(), any(), any()))
                .thenAnswer(
                        call -> new AccountCurrencyService.Position(call.getArgument(1), "EUR"));
    }

    private Liability loan() {
        Liability loan = new Liability();
        loan.setId(3L);
        loan.setUserId(7L);
        loan.setCurrency("EUR");
        loan.setCurrentBalance("100.01");
        return loan;
    }

    private Account account() {
        return Account.builder()
                .id(2L)
                .userId(7L)
                .currency("EUR")
                .balance(new BigDecimal("-100.01"))
                .openingDate(date.minusYears(1))
                .openingBalance(new BigDecimal("-100.01"))
                .build();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t"})
    void unknownPrincipalDoesNotInventHistoricalDebt(String balance) {
        Liability loan = loan();
        loan.setCurrentBalance(balance);
        assertThat(service.getLiabilityBalanceAt(loan, date)).isEqualByComparingTo("0");
    }

    @ParameterizedTest
    @CsvSource({"-1,0", "0,100.01", "1,100.01"})
    void debtExistsFromTheStartDateInclusively(int offset, String balance) {
        Liability loan = loan();
        loan.setStartDate(date);
        assertThat(service.getLiabilityBalanceAt(loan, date.plusDays(offset)))
                .isEqualByComparingTo(balance);
    }

    @ParameterizedTest
    @ValueSource(strings = {"history", "selected-dates"})
    void accountBackedDebtCannotBeCountedAgainAsStandaloneDebt(String api) {
        Liability loan = loan();
        loan.setRepresentedByAccountId(2L);
        assertThatThrownBy(
                        () -> {
                            if (api.equals("history"))
                                service.getStandaloneLiabilityBalanceHistory(
                                        loan, date, date.plusDays(1));
                            else service.getStandaloneLiabilityBalancesAt(loan, List.of(date));
                        })
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Account-backed");
        verifyNoInteractions(transactions, tranches, accounts);
    }

    @Test
    void anEmptyRequestedTimelineDoesNotLoadDebtMovements() {
        assertThat(service.getStandaloneLiabilityBalancesAt(loan(), List.of())).isEmpty();
        verifyNoInteractions(transactions, tranches, accounts);
    }

    @ParameterizedTest
    @CsvSource(
            value = {"NULL,3", "-1,3", "1,2", "3,0"},
            nullValues = "NULL")
    void dailyDebtTimelineBeginsAtTheLaterOfRequestedAndContractStart(Integer offset, int count) {
        Liability loan = loan();
        if (offset != null) loan.setStartDate(date.plusDays(offset));
        List<org.openfinance.dto.BalanceHistoryPoint> history =
                service.getStandaloneLiabilityBalanceHistory(loan, date, date.plusDays(2));
        assertThat(history).hasSize(count);
        if (count > 0) {
            assertThat(history.getFirst().date())
                    .isEqualTo(offset != null && offset > 0 ? date.plusDays(offset) : date);
            assertThat(history)
                    .allSatisfy(
                            point -> assertThat(point.balance()).isEqualByComparingTo("100.01"));
        }
    }

    @ParameterizedTest
    @CsvSource({"-42.01,42.01", "0,0", "42.01,0"})
    void representedDebtUsesTheAccountsOutstandingDebitOnly(String cash, String debt) {
        Liability loan = loan();
        loan.setRepresentedByAccountId(2L);
        Account account = account();
        account.setBalance(new BigDecimal(cash));
        when(accounts.findByUserId(7L)).thenReturn(List.of(account));
        assertThat(service.getLiabilityBalanceAt(loan, date)).isEqualByComparingTo(debt);
    }

    @ParameterizedTest
    @ValueSource(strings = {"missing", "not-yet-open", "converted", "deleted-movement"})
    void convertedDebtUsesSourceHistoryOnlyBeforeTheConversion(String state) {
        Liability loan = loan();
        loan.setConvertedFromAccountId(2L);
        loan.setBalanceStartDate(date.plusDays(1));
        Account source = account();
        if (state.equals("not-yet-open")) source.setOpeningDate(date.plusDays(1));
        if (!state.equals("missing")) {
            when(accounts.findByIdAndUserId(2L, 7L)).thenReturn(Optional.of(source));
            if (state.equals("deleted-movement")) {
                Transaction deleted =
                        Transaction.builder()
                                .accountId(2L)
                                .date(date.plusDays(2))
                                .type(TransactionType.EXPENSE)
                                .amount(new BigDecimal("500"))
                                .isDeleted(true)
                                .build();
                when(transactions.findAllForAccount(2L, 7L)).thenReturn(List.of(deleted));
            }
        }
        Map<LocalDate, BigDecimal> balances =
                service.getStandaloneLiabilityBalancesAt(loan, List.of(date, date.plusDays(1)));
        assertThat(balances.get(date))
                .isEqualByComparingTo(
                        state.equals("missing") || state.equals("not-yet-open") ? "0" : "100.01");
        assertThat(balances.get(date.plusDays(1))).isEqualByComparingTo("100.01");
    }

    @ParameterizedTest
    @ValueSource(strings = {"native", "foreign"})
    void principalHistoryReversesOnlyLaterMovementsAndUsesTheDebtCurrencyForDrawdowns(
            String currency) {
        Liability loan = loan();
        Transaction paid =
                Transaction.builder()
                        .date(date.plusDays(1))
                        .amount(new BigDecimal("22"))
                        .principalAmount(new BigDecimal("20"))
                        .movementType(MovementType.REPAYMENT)
                        .build();
        Transaction drawn =
                Transaction.builder()
                        .date(date.plusDays(2))
                        .amount(new BigDecimal("30"))
                        .movementType(MovementType.DISBURSEMENT)
                        .build();
        if (currency.equals("foreign")) {
            drawn.setOriginalCurrency("EUR");
            drawn.setOriginalAmount(new BigDecimal("40"));
        }
        Transaction sameDay =
                Transaction.builder()
                        .date(date)
                        .principalAmount(new BigDecimal("500"))
                        .movementType(MovementType.REPAYMENT)
                        .build();
        when(transactions.findByLiabilityIdAndUserId(3L, 7L))
                .thenReturn(List.of(paid, drawn, sameDay));
        assertThat(service.getLiabilityBalanceAt(loan, date))
                .isEqualByComparingTo(currency.equals("foreign") ? "80.01" : "90.01");
    }

    @ParameterizedTest
    @CsvSource(
            value = {
                "true,1,NULL,70.01",
                "true,0,NULL,100.01",
                "true,1,2,100.01",
                "true,-1,1,130.01",
                "true,-1,0,100.01",
                "false,1,NULL,100.01"
            },
            nullValues = "NULL")
    void datedDirectTranchesAndTheirReversalsReconstructPrincipal(
            boolean direct, int drawnOffset, Integer reversedOffset, String expected) {
        LiabilityTranche tranche =
                LiabilityTranche.builder()
                        .directDisbursement(direct)
                        .drawnAmount(new BigDecimal("30"))
                        .drawnDate(date.plusDays(drawnOffset))
                        .reversedDate(reversedOffset == null ? null : date.plusDays(reversedOffset))
                        .build();
        when(tranches.findByLiabilityIdAndUserId(3L, 7L)).thenReturn(List.of(tranche));
        assertThat(service.getStandaloneLiabilityBalancesAt(loan(), List.of(date)).get(date))
                .isEqualByComparingTo(expected);
    }

    static Stream<Arguments> invalidReads() {
        LocalDate date = LocalDate.of(2025, 1, 10);
        return Stream.<Consumer<NetWorthService>>of(
                        s -> s.getLatestNetWorth(null), s -> s.getNetWorthOneMonthAgo(null),
                        s -> s.calculateMonthlyChange(null),
                                s -> s.calculateChangeForPeriod(null, 30),
                        s -> s.cleanupOldSnapshots(null, 30), s -> s.cleanupOldSnapshots(7L, -1),
                        s -> s.getNetWorthHistory(null, date, date),
                                s -> s.getNetWorthHistory(7L, null, date),
                        s -> s.getNetWorthHistory(7L, date, null),
                                s -> s.getNetWorthHistory(7L, date.plusDays(1), date),
                        s -> s.saveNetWorthSnapshot(null, date, "EUR"),
                                s -> s.saveNetWorthSnapshot(7L, null, "EUR"),
                        s -> s.saveNetWorthSnapshot(7L, date, null),
                                s -> s.saveNetWorthSnapshot(7L, date, " "))
                .map(Arguments::of);
    }

    @ParameterizedTest
    @MethodSource("invalidReads")
    void invalidHistoryRequestsDoNotReadOrWriteSnapshots(Consumer<NetWorthService> read) {
        assertThatThrownBy(() -> read.accept(service)).isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(snapshots, writer, transactions, accounts);
    }

    @ParameterizedTest
    @CsvSource({
        "INCOME,false,100.01",
        "EXPENSE,false,100.01",
        "INCOME,true,90.01",
        "EXPENSE,true,110.01"
    })
    void historicalCashReversesInvestmentRowsOnlyWhenTheyActuallyMovedCash(
            TransactionType type, boolean cash, String expected) {
        Account account = account();
        account.setBalance(new BigDecimal("100.01"));
        Transaction movement =
                Transaction.builder()
                        .accountId(2L)
                        .date(date.plusDays(1))
                        .type(type)
                        .amount(new BigDecimal("10"))
                        .investmentQuantity(BigDecimal.ONE)
                        .investmentCash(cash)
                        .accountAmount(cash ? new BigDecimal("10") : BigDecimal.ZERO)
                        .build();
        when(accounts.findByUserId(7L)).thenReturn(List.of(account));
        when(transactions.findByUserId(7L)).thenReturn(List.of(movement));
        assertThat(service.getAccountBalancesAt(7L, date, "EUR").get(2L))
                .isEqualByComparingTo(expected);
    }
}
