package org.openfinance.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.stream.IntStream;
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
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.openfinance.dto.LiabilityConversionPayment;
import org.openfinance.dto.LiabilityConversionPaymentCandidate;
import org.openfinance.entity.Account;
import org.openfinance.entity.Category;
import org.openfinance.entity.Liability;
import org.openfinance.entity.LoanComponent;
import org.openfinance.entity.MovementType;
import org.openfinance.entity.Transaction;
import org.openfinance.entity.TransactionSplit;
import org.openfinance.entity.TransactionType;
import org.openfinance.exception.InvalidTransactionException;
import org.openfinance.repository.TransactionRepository;
import org.openfinance.repository.TransactionSplitRepository;

@ExtendWith(MockitoExtension.class)
@DisplayName("Conversion links only reviewed historical payments without changing cash")
class LiabilityConversionPaymentsTest {
    @Mock private TransactionRepository transactions;
    @Mock private TransactionSplitRepository splits;
    private LiabilityConversionPayments service;
    private final Account source =
            Account.builder().id(1L).userId(7L).name("Prêt 0012").currency("EUR").build();

    @BeforeEach
    void setUp() {
        service =
                new LiabilityConversionPayments(
                        transactions, splits, new ObjectMapper().findAndRegisterModules());
    }

    static Stream<Arguments> ineligiblePayments() {
        return Stream.of(
                mutation("another user", t -> t.setUserId(8L)),
                mutation("loan source account", t -> t.setAccountId(1L)),
                mutation("deleted", t -> t.setIsDeleted(true)),
                mutation("income", t -> t.setType(TransactionType.INCOME)),
                mutation("transfer link", t -> t.setTransferId("transfer")),
                mutation("transfer destination", t -> t.setToAccountId(3L)),
                mutation("existing loan", t -> t.setLiabilityId(3L)),
                mutation("tranche", t -> t.setTrancheId(3L)),
                mutation("property", t -> t.setRealEstateId(3L)),
                mutation("investment", t -> t.setAssetId(3L)),
                mutation("investment quantity", t -> t.setInvestmentQuantity(BigDecimal.ONE)),
                mutation("future", t -> t.setDate(LocalDate.now().plusDays(1))),
                mutation("different currency", t -> t.setCurrency("USD")),
                mutation("foreign original currency", t -> t.setOriginalCurrency("USD")));
    }

    @ParameterizedTest(name = "exclude {0}")
    @MethodSource("ineligiblePayments")
    void filtersUnsafePaymentsAndRejectsForgedSelections(
            String reason, Consumer<Transaction> mutate) {
        Transaction tx = payment(4L);
        mutate.accept(tx);
        when(transactions.findByUserIdAndType(7L, TransactionType.EXPENSE)).thenReturn(List.of(tx));
        assertThat(service.candidates(source, null).items()).as(reason).isEmpty();
        when(transactions.findByIdAndUserId(4L, 7L)).thenReturn(Optional.of(tx));
        assertThatThrownBy(
                        () ->
                                service.review(
                                        source,
                                        List.of(
                                                new LiabilityConversionPayment(
                                                        4L,
                                                        "a".repeat(64),
                                                        LoanComponent.PRINCIPAL,
                                                        null)),
                                        new BigDecimal("100")))
                .isInstanceOf(InvalidTransactionException.class);
        verifyNoInteractions(splits);
        verify(transactions, never()).save(tx);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"  ", "PRET 12", "prêt   00012"})
    void matchesNormalizedAccentsWhitespaceAndLoanNumbers(String query) {
        Transaction tx = payment(4L);
        tx.setOriginalCurrency("EUR");
        when(transactions.findByUserIdAndType(7L, TransactionType.EXPENSE)).thenReturn(List.of(tx));
        assertThat(service.candidates(source, query).items())
                .singleElement()
                .satisfies(
                        candidate -> {
                            assertThat(candidate.transactionId()).isEqualTo(4);
                            assertThat(candidate.accountName()).isEqualTo("Current account");
                            assertThat(candidate.amount()).isEqualByComparingTo("100");
                            assertThat(candidate.categoryName()).isNull();
                            assertThat(candidate.suggestedComponent()).isNull();
                        });
    }

    @ParameterizedTest
    @CsvSource({
        "Interest,INTEREST",
        "Intérêts,INTEREST",
        "Insurance,INSURANCE",
        "Assurance,INSURANCE",
        "Fees,FEE",
        "Frais,FEE",
        "Commission,FEE",
        "Capital,PRINCIPAL",
        "Principal,PRINCIPAL",
        "category.loan.repayment,PRINCIPAL",
        "Groceries,"
    })
    void suggestsComponentsButPreservesExplicitSplitClassification(
            String category, LoanComponent expected) {
        Transaction tx = payment(4L);
        tx.setCategory(Category.builder().name(category).build());
        TransactionSplit inferred =
                TransactionSplit.builder()
                        .id(9L)
                        .amount(new BigDecimal("75"))
                        .category(Category.builder().nameKey(category).build())
                        .build();
        TransactionSplit explicit =
                TransactionSplit.builder()
                        .id(10L)
                        .amount(new BigDecimal("25"))
                        .category(tx.getCategory())
                        .loanComponent(LoanComponent.INSURANCE)
                        .build();
        when(transactions.findByUserIdAndType(7L, TransactionType.EXPENSE)).thenReturn(List.of(tx));
        when(splits.findByTransactionIdOrderById(4L)).thenReturn(List.of(inferred, explicit));
        LiabilityConversionPaymentCandidate candidate =
                service.candidates(source, null).items().getFirst();
        assertThat(candidate.suggestedComponent()).isEqualTo(expected);
        assertThat(candidate.splits().getFirst().suggestedComponent()).isEqualTo(expected);
        assertThat(candidate.splits().get(1).suggestedComponent())
                .isEqualTo(LoanComponent.INSURANCE);
    }

    @ParameterizedTest
    @ValueSource(ints = {100, 101, 110})
    void indicatesTruncationOnlyWhenMoreThanOneHundredPaymentsMatch(int count) {
        when(transactions.findByUserIdAndType(7L, TransactionType.EXPENSE))
                .thenReturn(
                        IntStream.rangeClosed(1, count).mapToObj(n -> payment((long) n)).toList());
        LiabilityConversionPayments.Candidates result = service.candidates(source, null);
        assertThat(result.items()).hasSize(100);
        assertThat(result.truncated()).isEqualTo(count > 100);
        verify(splits, never()).findByTransactionIdOrderById(101L);
    }

    @ParameterizedTest
    @CsvSource({
        "PRINCIPAL,REPAYMENT,100",
        "INTEREST,INTEREST,0",
        "INSURANCE,INSURANCE,0",
        "FEE,FEE,0"
    })
    void associatesUnsplitComponentsWithoutChangingBookedAmount(
            LoanComponent component, MovementType movement, String principal) {
        Transaction tx = payment(4L);
        String revision = prepareReview(tx, List.of());
        List<LiabilityConversionPayments.ReviewedPayment> reviewed =
                service.review(
                        source,
                        List.of(new LiabilityConversionPayment(4L, revision, component, null)),
                        new BigDecimal("100"));
        assertThat(reviewed.getFirst().principal()).isEqualByComparingTo(principal);
        service.associate(loan(), reviewed);
        assertThat(tx.getLiabilityId()).isEqualTo(20);
        assertThat(tx.getMovementType()).isEqualTo(movement);
        assertThat(tx.getPrincipalAmount()).isEqualByComparingTo(principal);
        assertThat(tx.getAmount()).isEqualByComparingTo("100");
        assertThat(tx.getAccountId()).isEqualTo(2);
        verify(transactions).save(tx);
    }

    @Test
    void sumsOnlyPrincipalSplitsAndRejectsOverstatementOfPreviouslyPaidPrincipal() {
        Transaction tx = payment(4L);
        List<TransactionSplit> parts = List.of(part(9L, "80"), part(10L, "20"));
        String revision = prepareReview(tx, parts);
        LiabilityConversionPayment selection =
                new LiabilityConversionPayment(
                        4L,
                        revision,
                        null,
                        List.of(
                                new LiabilityConversionPayment.Split(10L, LoanComponent.INTEREST),
                                new LiabilityConversionPayment.Split(9L, LoanComponent.PRINCIPAL)));
        assertThatThrownBy(
                        () -> service.review(source, List.of(selection), new BigDecimal("79.99")))
                .isInstanceOf(InvalidTransactionException.class);
        List<LiabilityConversionPayments.ReviewedPayment> reviewed =
                service.review(source, List.of(selection), new BigDecimal("80"));
        service.associate(loan(), reviewed);
        assertThat(tx.getPrincipalAmount()).isEqualByComparingTo("80");
        assertThat(tx.getMovementType()).isEqualTo(MovementType.REPAYMENT);
        assertThat(parts)
                .extracting(TransactionSplit::getLoanComponent)
                .containsExactly(LoanComponent.PRINCIPAL, LoanComponent.INTEREST);
        assertThat(parts)
                .extracting(TransactionSplit::getAmount)
                .containsExactly(new BigDecimal("80"), new BigDecimal("20"));
        verify(splits).saveAll(parts);
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "top-level component",
                "missing split",
                "forged split",
                "unassigned split",
                "unbalanced amounts",
                "duplicate split"
            })
    void requiresAnExactCompleteSplitAssignment(String invalid) {
        Transaction tx = payment(4L);
        List<TransactionSplit> parts =
                List.of(
                        part(9L, "80"),
                        part(10L, invalid.equals("unbalanced amounts") ? "19.99" : "20"));
        String revision = prepareReview(tx, parts);
        LiabilityConversionPayment.Split a =
                new LiabilityConversionPayment.Split(9L, LoanComponent.PRINCIPAL);
        LiabilityConversionPayment.Split b =
                new LiabilityConversionPayment.Split(10L, LoanComponent.INTEREST);
        List<LiabilityConversionPayment.Split> selected =
                switch (invalid) {
                    case "missing split" -> List.of(a);
                    case "forged split" -> List.of(
                            a, new LiabilityConversionPayment.Split(11L, LoanComponent.INTEREST));
                    case "unassigned split" -> List.of(
                            a, new LiabilityConversionPayment.Split(10L, null));
                    case "duplicate split" -> List.of(a, a);
                    default -> List.of(a, b);
                };
        LiabilityConversionPayment selection =
                new LiabilityConversionPayment(
                        4L,
                        revision,
                        invalid.equals("top-level component") ? LoanComponent.PRINCIPAL : null,
                        selected);
        assertThatThrownBy(() -> service.review(source, List.of(selection), new BigDecimal("100")))
                .isInstanceOf(InvalidTransactionException.class);
        verify(transactions, never()).save(tx);
    }

    @Test
    void nullOrEmptySelectionDoesNotReadOrWritePayments() {
        assertThat(service.review(source, null, BigDecimal.ZERO)).isEmpty();
        assertThat(service.review(source, List.of(), BigDecimal.ZERO)).isEmpty();
        verifyNoInteractions(transactions, splits);
    }

    private String prepareReview(Transaction tx, List<TransactionSplit> parts) {
        when(transactions.findByUserIdAndType(7L, TransactionType.EXPENSE)).thenReturn(List.of(tx));
        when(splits.findByTransactionIdOrderById(tx.getId())).thenReturn(parts);
        String revision = service.candidates(source, null).items().getFirst().sourceRevision();
        when(transactions.findByIdAndUserId(tx.getId(), 7L)).thenReturn(Optional.of(tx));
        return revision;
    }

    private static Arguments mutation(String name, Consumer<Transaction> action) {
        return Arguments.of(name, action);
    }

    private static Transaction payment(long id) {
        return Transaction.builder()
                .id(id)
                .userId(7L)
                .accountId(2L)
                .account(Account.builder().id(2L).name("Current account").build())
                .type(TransactionType.EXPENSE)
                .amount(new BigDecimal("100"))
                .currency("EUR")
                .date(LocalDate.now().minusDays(10))
                .createdAt(LocalDateTime.now().minusDays(9))
                .description("Prêt 0012")
                .isDeleted(false)
                .build();
    }

    private static TransactionSplit part(long id, String amount) {
        return TransactionSplit.builder()
                .id(id)
                .transactionId(4L)
                .amount(new BigDecimal(amount))
                .build();
    }

    private static Liability loan() {
        Liability loan = new Liability();
        loan.setId(20L);
        loan.setConvertedFromAccountId(1L);
        loan.setBalanceStartDate(LocalDate.now());
        loan.setCreatedAt(LocalDateTime.now());
        return loan;
    }
}
