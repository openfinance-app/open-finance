package org.openfinance.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.openfinance.dto.ImportedTransaction;
import org.openfinance.entity.Account;

class OfxTransferResolverTest {
    @Test
    void onlyResolvesCounterpartyNumbersBelongingToThisUser() {
        ImportedTransaction internal = row("A", "-100", "B", null);
        ImportedTransaction external = row("A", "-50", "C", null);
        ImportedTransaction unspecified = row("A", "-20", null, null);
        OfxTransferResolver.resolve(
                List.of(internal, external, unspecified),
                List.of(Account.builder().id(2L).name("Savings").accountNumber("B").build()));
        assertThat(internal.isTransfer()).isTrue();
        assertThat(internal.getToAccountName()).isEqualTo("Savings");
        assertThat(internal.getToAccountNumber()).isEqualTo("B");
        assertThat(external.isTransfer()).isFalse();
        assertThat(external.getToAccountName()).isNull();
        assertThat(unspecified.isTransfer()).isFalse();
    }

    @Test
    void rejectsAmbiguousOwnedNumbersAndSelfTransfers() {
        ImportedTransaction ambiguous = row("A", "-100", "B", null);
        ImportedTransaction self = row("A", "100", "A", null);
        OfxTransferResolver.resolve(
                List.of(ambiguous, self),
                List.of(
                        Account.builder().id(1L).name("First").accountNumber("B").build(),
                        Account.builder().id(2L).name("Second").accountNumber("B").build()));
        assertThat(ambiguous.isTransfer()).isFalse();
        assertThat(self.isTransfer()).isFalse();
    }

    @Test
    void sharedServerReferenceRequiresTwoOppositeAccountSides() {
        ImportedTransaction outgoing = row("A", "-100", null, "server:42");
        ImportedTransaction incoming = row("B", "100", null, "server:42");
        OfxTransferResolver.resolve(List.of(outgoing, incoming), List.of());
        assertThat(outgoing.isTransfer()).isTrue();
        assertThat(incoming.getToAccountNumber()).isEqualTo("A");
        ImportedTransaction collision = row("C", "100", null, "server:42");
        OfxTransferResolver.resolve(List.of(outgoing, incoming, collision), List.of());
        assertThat(outgoing.isTransfer()).isFalse();
        assertThat(incoming.isTransfer()).isFalse();
        assertThat(collision.isTransfer()).isFalse();
    }

    @Test
    void equalAmountDoesNotLinkUnidentifiedBankTransfers() {
        ImportedTransaction outgoing = row("A", "-100", null, null);
        ImportedTransaction incoming = row("B", "100", null, null);
        OfxTransferResolver.resolve(List.of(outgoing, incoming), List.of());
        assertThat(outgoing.isTransfer()).isFalse();
        assertThat(incoming.isTransfer()).isFalse();
    }

    private ImportedTransaction row(String account, String amount, String other, String reference) {
        return ImportedTransaction.builder()
                .accountNumber(account)
                .accountName(account)
                .amount(new BigDecimal(amount))
                .transactionDate(LocalDate.of(2026, 9, 28))
                .ofxTransfer(true)
                .ofxCounterpartyAccount(other)
                .ofxTransferReference(reference)
                .build();
    }
}
