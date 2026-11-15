package org.openfinance.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.openfinance.dto.ImportedTransaction;
import org.openfinance.entity.Account;

@DisplayName("OFX transfer evidence must identify a distinct owned account")
class OfxTransferBoundaryTest {
    @ParameterizedTest
    @ValueSource(
            strings = {
                "not-transfer",
                "source-errors",
                "target-errors",
                "source-amount",
                "target-amount",
                "zero",
                "same-sign",
                "source-account",
                "target-account",
                "same-account",
                "conflicting-target"
            })
    void aSharedReferenceCannotOverrideInvalidOrContradictoryEvidence(String problem) {
        ImportedTransaction source = row("A", "-100");
        ImportedTransaction target = row("B", "100");
        switch (problem) {
            case "not-transfer" -> target.setOfxTransfer(false);
            case "source-errors" -> source.setValidationErrors(List.of("Invalid date"));
            case "target-errors" -> target.setValidationErrors(List.of("Invalid date"));
            case "source-amount" -> source.setAmount(null);
            case "target-amount" -> target.setAmount(null);
            case "zero" -> target.setAmount(BigDecimal.ZERO);
            case "same-sign" -> target.setAmount(new BigDecimal("-100"));
            case "source-account" -> source.setAccountNumber(null);
            case "target-account" -> target.setAccountNumber(null);
            case "same-account" -> target.setAccountNumber(" a ");
            case "conflicting-target" -> target.setOfxCounterpartyAccount("C");
            default -> throw new AssertionError(problem);
        }
        OfxTransferResolver.resolve(List.of(source, target), List.of());
        assertThat(source.isTransfer()).isFalse();
        assertThat(source.getToAccountNumber()).isNull();
        assertThat(source.getCategory()).isEqualTo("External payment");
    }

    @Test
    void explicitReverseEvidenceCanConfirmAPairAcrossDifferentCurrencies() {
        ImportedTransaction source = row("A", "-120");
        source.setCurrency("USD");
        ImportedTransaction target = row("B", "100");
        target.setCurrency("EUR");
        target.setOfxCounterpartyAccount(" a ");
        OfxTransferResolver.resolve(List.of(source, target), List.of());
        assertThat(source.isTransfer()).isTrue();
        assertThat(source.getToAccountNumber()).isEqualTo("B");
        assertThat(source.getCategory()).isNull();
        assertThat(target.isTransfer()).isTrue();
    }

    @Test
    void deletedAndUnnumberedAccountsDoNotMakeTheLiveDestinationAmbiguous() {
        ImportedTransaction source = row("A", "-100");
        source.setOfxCounterpartyAccount(" b ");
        Account deleted =
                Account.builder().id(1L).accountNumber("B").deletedDate(LocalDate.now()).build();
        Account unnumbered = Account.builder().id(2L).name("No number").build();
        Account live = Account.builder().id(3L).accountNumber("B").name("Savings").build();
        OfxTransferResolver.resolve(List.of(source), List.of(deleted, unnumbered, live));
        assertThat(source.isTransfer()).isTrue();
        assertThat(source.getToAccountName()).isEqualTo("Savings");
    }

    @Test
    void removedTransferEvidenceClearsPreviouslyResolvedDestinationAndGroup() {
        ImportedTransaction source = row("A", "-100");
        source.setTransfer(true);
        source.setTransferGroupKey("previous");
        source.setToAccountName("Old destination");
        source.setToAccountNumber("B");
        OfxTransferResolver.resolve(List.of(source), List.of());
        assertThat(source.isTransfer()).isFalse();
        assertThat(source.getTransferGroupKey()).isNull();
        assertThat(source.getToAccountName()).isNull();
        assertThat(source.getToAccountNumber()).isNull();
    }

    @Test
    void explicitDestinationCanUseAnAccountDeclaredInTheSameFile() {
        ImportedTransaction source = row("A", "-100");
        source.setOfxCounterpartyAccount("B");
        ImportedTransaction declaration = row("B", "10");
        declaration.setOfxTransfer(false);
        declaration.setAccountName("Imported savings");
        OfxTransferResolver.resolve(List.of(source, declaration), List.of());
        assertThat(source.getToAccountName()).isEqualTo("Imported savings");
        assertThat(source.isTransfer()).isTrue();
        assertThat(declaration.isTransfer()).isFalse();
    }

    private ImportedTransaction row(String account, String amount) {
        return ImportedTransaction.builder()
                .accountNumber(account)
                .accountName(account)
                .transactionDate(LocalDate.of(2026, 1, 10))
                .amount(new BigDecimal(amount))
                .ofxTransfer(true)
                .ofxTransferReference("reference")
                .category("External payment")
                .build();
    }
}
