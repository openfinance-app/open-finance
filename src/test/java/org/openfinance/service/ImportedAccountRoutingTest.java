package org.openfinance.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.openfinance.dto.ImportedTransaction;
import org.openfinance.entity.Account;
import org.openfinance.exception.InvalidTransactionException;

@DisplayName("Imported account routing honors authorized identities and fallback boundaries")
class ImportedAccountRoutingTest {
    @ParameterizedTest
    @CsvSource({"Checking,,true", ",FR123,true", ",,false", "' ',' ',false"})
    void sourceReferencesSelectAuthorizedMappings(String name, String number, boolean identified) {
        ImportedTransaction tx =
                ImportedTransaction.builder().accountName(name).accountNumber(number).build();
        String key = ImportStatementPositions.accountKey(name, number);
        Map<String, Long> mappings = identified ? Map.of(key, 7L) : Map.of();
        assertThat(ImportedAccountRouting.resolveImportedAccountId(tx, mappings, 99L))
                .isEqualTo(identified ? 7L : 99L);
        assertThat(
                        ImportedAccountRouting.resolveImportedAccountId(
                                tx, Map.of("name:unrelated", 8L), 99L))
                .isEqualTo(99L);
        assertThat(ImportedAccountRouting.shouldUseImportedAccountRouting(List.of(tx)))
                .isEqualTo(identified);
        assertThat(ImportedAccountRouting.requiresFallbackAccount(List.of(tx)))
                .isEqualTo(!identified);
    }

    @ParameterizedTest
    @CsvSource({"Savings,,true", ",FR456,true", ",,false", "' ',' ',false"})
    void destinationNumbersAloneAreSufficientToRouteATransfer(
            String name, String number, boolean identified) {
        ImportedTransaction tx =
                ImportedTransaction.builder()
                        .toAccountName(name)
                        .toAccountNumber(number)
                        .transfer(true)
                        .build();
        String key = ImportStatementPositions.accountKey(name, number);
        Map<String, Long> mappings = identified ? Map.of(key, 8L) : Map.of();
        assertThat(ImportedAccountRouting.resolveImportedDestinationAccountId(tx, mappings, 99L))
                .isEqualTo(identified ? 8L : 99L);
        assertThat(ImportedAccountRouting.resolveImportedDestinationAccountId(tx, Map.of(), 99L))
                .isEqualTo(99L);
        assertThat(ImportedAccountRouting.requiresFallbackAccount(List.of(tx))).isFalse();
        assertThat(ImportedAccountRouting.shouldUseImportedAccountRouting(List.of(tx)))
                .isEqualTo(identified);
    }

    @ParameterizedTest
    @CsvSource({
        "Checking,FR123,checking,,true",
        "Checking,FR123,Different,fr123,true",
        "Checking,FR123,Different,FR999,false",
        "Checking,FR123,,,false",
        "Checking,FR123,,' ',false",
        ",FR123,Checking,FR123,true",
        "Checking,,checking,FR123,true",
        ",,Checking,FR123,false"
    })
    void namesAndAccountNumbersMatchCaseInsensitivelyWithoutMatchingAbsentIdentifiers(
            String name, String number, String importedName, String importedNumber, boolean match) {
        Account account = Account.builder().id(7L).name(name).accountNumber(number).build();
        assertThat(
                        ImportedAccountRouting.matchesImportedAccount(
                                account, importedName, importedNumber))
                .isEqualTo(match);
        account.setDeletedDate(LocalDate.now());
        assertThat(
                        ImportedAccountRouting.matchesImportedAccount(
                                account, importedName, importedNumber))
                .isFalse();
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(booleans = {false})
    void inactiveOrUnknownStatusCannotReceiveImportedFunds(Boolean active) {
        Account account = Account.builder().id(7L).isActive(active).build();
        assertThatThrownBy(() -> ImportedAccountRouting.requireActiveImportAccount(account))
                .isInstanceOf(InvalidTransactionException.class)
                .hasMessageContaining("7");
    }

    @Test
    void activeAccountsAndEmptyFilesNeedNoFallback() {
        assertThatCode(
                        () ->
                                ImportedAccountRouting.requireActiveImportAccount(
                                        Account.builder().isActive(true).build()))
                .doesNotThrowAnyException();
        assertThat(ImportedAccountRouting.shouldUseImportedAccountRouting(List.of())).isFalse();
        assertThat(ImportedAccountRouting.requiresFallbackAccount(List.of())).isFalse();
    }

    @Test
    void aMixedFileStillRequiresAFallbackForItsUnnamedNonTransfer() {
        ImportedTransaction identified =
                ImportedTransaction.builder().accountName("Checking").build();
        ImportedTransaction unnamed = ImportedTransaction.builder().build();
        assertThat(
                        ImportedAccountRouting.shouldUseImportedAccountRouting(
                                List.of(unnamed, identified)))
                .isTrue();
        assertThat(ImportedAccountRouting.requiresFallbackAccount(List.of(identified, unnamed)))
                .isTrue();
    }
}
