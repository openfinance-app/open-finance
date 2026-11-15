package org.openfinance.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.openfinance.dto.ImportedTransaction;

@DisplayName("Statement matching never collapses unrelated transfers")
class ImportedTransferMatcherTest {
    @Test
    void numberOnlyTransferSidesAreGroupedSoTheyAreBookedExactlyOnce() {
        ImportedTransaction outgoing = row("-100");
        outgoing.setTransfer(true);
        outgoing.setAccountNumber("CHK-001");
        outgoing.setToAccountNumber("SAV-002");
        ImportedTransaction incoming = row("100");
        incoming.setTransfer(true);
        incoming.setAccountNumber("SAV-002");
        incoming.setToAccountNumber("CHK-001");
        ImportedTransferMatcher.Groups groups =
                ImportedTransferMatcher.group(
                        List.of(outgoing, incoming),
                        Map.of("number:chk001", 1L, "number:sav002", 2L),
                        null);
        assertThat(groups.keyFor(outgoing)).isNotNull().isEqualTo(groups.keyFor(incoming));
        assertThat(groups.rowsFor(groups.keyFor(outgoing))).containsExactly(outgoing, incoming);
    }

    static Stream<Arguments> mismatches() {
        return Stream.of(
                change("same direction", row -> row.setAmount(new BigDecimal("-100"))),
                change("zero incoming amount", row -> row.setAmount(BigDecimal.ZERO)),
                change("different amount", row -> row.setAmount(new BigDecimal("99.99"))),
                change("too late", row -> row.setTransactionDate(LocalDate.of(2024, 1, 12))),
                change("too early", row -> row.setTransactionDate(LocalDate.of(2024, 1, 8))),
                change(
                        "cross currency without matching description",
                        row -> {
                            row.setCurrency("USD");
                            row.setPayee("Another transfer");
                        }),
                change("holding versus cash", row -> row.setHoldingTransfer(true)));
    }

    @ParameterizedTest(name = "does not match {0}")
    @MethodSource("mismatches")
    void rejectsSimilarButIndependentMovements(String name, Consumer<ImportedTransaction> mutate) {
        ImportedTransferMatcher matcher = new ImportedTransferMatcher();
        ImportedTransaction first = row("-100");
        ImportedTransaction second = row("100");
        mutate.accept(second);
        assertThat(matcher.keyFor(second, 2L, 1L))
                .as(name)
                .isNotEqualTo(matcher.keyFor(first, 1L, 2L));
    }

    @ParameterizedTest
    @ValueSource(ints = {-1, 0, 1})
    void matchesOnlyOneOppositeSideWithinOneDay(int days) {
        ImportedTransferMatcher matcher = new ImportedTransferMatcher();
        ImportedTransaction first = row("-100.00");
        ImportedTransaction second = row("100.000");
        second.setCurrency("eur");
        second.setTransactionDate(first.getTransactionDate().plusDays(days));
        String key = matcher.keyFor(first, 1L, 2L);
        assertThat(matcher.keyFor(second, 2L, 1L)).isEqualTo(key);
        assertThat(matcher.keyFor(row("100"), 2L, 1L)).isNotEqualTo(key);
    }

    @Test
    void respectsExplicitGroupsAndKeepsSameAccountDirectionsSeparate() {
        ImportedTransferMatcher matcher = new ImportedTransferMatcher();
        ImportedTransaction explicit = row("-100");
        explicit.setTransferGroupKey("bank:42");
        assertThat(matcher.keyFor(explicit, 1L, 2L)).isEqualTo("explicit:bank:42");
        ImportedTransaction blank = row("-100");
        blank.setTransferGroupKey(" ");
        String key = matcher.keyFor(blank, 1L, 2L);
        assertThat(matcher.keyFor(row("100"), 1L, 2L)).isNotEqualTo(key);
        assertThat(matcher.keyFor(row("100"), 2L, 3L)).isNotEqualTo(key);
        assertThat(matcher.keyFor(row("100"), 2L, 1L)).isEqualTo(key);
    }

    @Test
    void matchesNativeTransferAmountRatherThanConvertedCashAmount() {
        ImportedTransferMatcher matcher = new ImportedTransferMatcher();
        ImportedTransaction first = row("-120");
        first.setTransferAmount(new BigDecimal("100"));
        ImportedTransaction second = row("80");
        second.setTransferAmount(new BigDecimal("-100.000"));
        assertThat(matcher.keyFor(first, 1L, 2L)).isEqualTo(matcher.keyFor(second, 2L, 1L));
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "MATCH",
                "DIFFERENT",
                "FIRST_MISSING",
                "SECOND_MISSING",
                "FIRST_NOT_OFX",
                "SECOND_NOT_OFX"
            })
    void usesSharedOfxReferencesOnlyWhenBothSidesAreOfx(String scenario) {
        ImportedTransferMatcher matcher = new ImportedTransferMatcher();
        ImportedTransaction first = row("-100");
        ImportedTransaction second = row("105");
        first.setOfxTransfer(!scenario.equals("FIRST_NOT_OFX"));
        second.setOfxTransfer(!scenario.equals("SECOND_NOT_OFX"));
        first.setOfxTransferReference(scenario.equals("FIRST_MISSING") ? null : "ref1");
        second.setOfxTransferReference(
                scenario.equals("SECOND_MISSING")
                        ? null
                        : scenario.equals("DIFFERENT") ? "ref2" : "ref1");
        String firstKey = matcher.keyFor(first, 1L, 2L);
        assertThat(matcher.keyFor(second, 2L, 1L).equals(firstKey))
                .isEqualTo(scenario.equals("MATCH"));
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "MATCH",
                "DIFFERENT_SECURITY",
                "FIRST_QUANTITY_MISSING",
                "SECOND_QUANTITY_MISSING",
                "UNBALANCED",
                "SECOND_IS_CASH"
            })
    void holdingTransfersRequireTheSameSecurityAndExactlyOppositeUnits(String scenario) {
        ImportedTransferMatcher matcher = new ImportedTransferMatcher();
        ImportedTransaction first = row("-100");
        ImportedTransaction second = row("105");
        first.setHoldingTransfer(true);
        second.setHoldingTransfer(!scenario.equals("SECOND_IS_CASH"));
        first.setInvestmentSecurity("isin:FR0001");
        second.setInvestmentSecurity(
                scenario.equals("DIFFERENT_SECURITY") ? "isin:FR0002" : "isin:FR0001");
        first.setInvestmentQuantity(
                scenario.equals("FIRST_QUANTITY_MISSING")
                        ? null
                        : new BigDecimal("-0.000000000000000001"));
        second.setInvestmentQuantity(
                scenario.equals("SECOND_QUANTITY_MISSING")
                        ? null
                        : new BigDecimal(
                                scenario.equals("UNBALANCED")
                                        ? "0.000000000000000002"
                                        : "0.000000000000000001"));
        String key = matcher.keyFor(first, 1L, 2L);
        assertThat(matcher.keyFor(second, 2L, 1L).equals(key)).isEqualTo(scenario.equals("MATCH"));
    }

    @ParameterizedTest
    @ValueSource(
            strings = {"International transfer", "INTERNATIONAL TRANSFER", "", " ", "Different"})
    void crossCurrencyMatchingNeedsANonblankSharedDescription(String description) {
        ImportedTransferMatcher matcher = new ImportedTransferMatcher();
        ImportedTransaction first = row("-100");
        first.setPayee(description);
        ImportedTransaction second = row("110");
        second.setCurrency("USD");
        second.setPayee("International transfer");
        String key = matcher.keyFor(first, 1L, 2L);
        assertThat(matcher.keyFor(second, 2L, 1L).equals(key))
                .isEqualTo(description.equalsIgnoreCase("International transfer"));
    }

    @Test
    void groupsResolvedRowsByIdentityWithoutMergingSeparateEqualLookingOperations() {
        ImportedTransaction outgoing = row("-100");
        outgoing.setAccountName("Checking");
        outgoing.setToAccountName("Savings");
        ImportedTransaction incoming = row("100");
        incoming.setAccountName("Savings");
        incoming.setToAccountName("Checking");
        ImportedTransaction unpaired = row("-100");
        unpaired.setAccountName("Checking");
        unpaired.setToAccountName("Savings");
        ImportedTransaction ordinary = row("-50");
        ordinary.setTransfer(false);
        ImportedTransaction unidentified = row("-50");
        ImportedTransaction unresolved = row("-50");
        unresolved.setToAccountName("Unknown");
        ImportedTransferMatcher.Groups groups =
                ImportedTransferMatcher.group(
                        List.of(outgoing, incoming, unpaired, ordinary, unidentified, unresolved),
                        Map.of("name:checking", 1L, "name:savings", 2L),
                        null);
        assertThat(groups.rowsFor(groups.keyFor(outgoing))).containsExactly(outgoing, incoming);
        assertThat(groups.keyFor(unpaired)).isNotEqualTo(groups.keyFor(outgoing));
        assertThat(groups.keyFor(ordinary)).isNull();
        assertThat(groups.keyFor(unidentified)).isNull();
        assertThat(groups.keyFor(unresolved)).isNull();
    }

    private static Arguments change(String name, Consumer<ImportedTransaction> mutation) {
        return Arguments.of(name, mutation);
    }

    private static ImportedTransaction row(String amount) {
        return ImportedTransaction.builder()
                .transfer(true)
                .amount(new BigDecimal(amount))
                .transactionDate(LocalDate.of(2024, 1, 10))
                .currency("EUR")
                .payee("Transfer")
                .build();
    }
}
