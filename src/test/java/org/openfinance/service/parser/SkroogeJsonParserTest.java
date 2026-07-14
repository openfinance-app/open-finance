package org.openfinance.service.parser;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.openfinance.dto.ImportedTransaction;
import org.openfinance.dto.SkroogeImportMetadata;
import org.openfinance.dto.SkroogeImportParseResult;
import org.openfinance.entity.AccountType;

@DisplayName("SkroogeJsonParser Tests")
class SkroogeJsonParserTest {

    private SkroogeJsonParser parser;

    @BeforeEach
    void setUp() {
        parser = new SkroogeJsonParser(new ObjectMapper());
    }

    @Test
    @DisplayName("Should parse split transactions, transfer groups, and metadata from Skrooge JSON")
    void shouldParseSplitTransactionsTransferGroupsAndMetadata() throws IOException {
        SkroogeImportParseResult result =
                parser.parseFile(
                        new ByteArrayInputStream(
                                sampleSkroogeJson().getBytes(StandardCharsets.UTF_8)),
                        "my_export.json");

        assertThat(result.getCurrency()).isEqualTo("EUR");
        assertThat(result.getSkroogeMetadata().getInstitutions()).hasSize(1);
        assertThat(result.getSkroogeMetadata().getAccounts()).hasSize(2);
        assertThat(result.getSkroogeMetadata().getCategories())
                .extracting(category -> category.getFullName())
                .containsExactly("Food", "Food:Groceries");

        ImportedTransaction groceries =
                result.getTransactions().stream()
                        .filter(transaction -> !transaction.isTransfer())
                        .findFirst()
                        .orElseThrow();
        assertThat(groceries.getTransactionDate()).isEqualTo(LocalDate.of(2024, 1, 10));
        assertThat(groceries.getAmount()).isEqualByComparingTo(new BigDecimal("-45.00"));
        assertThat(groceries.isSplitTransaction()).isTrue();
        assertThat(groceries.getSplits()).hasSize(2);
        assertThat(groceries.getSplits())
                .extracting(ImportedTransaction.SplitEntry::getAmount)
                .containsExactly(new BigDecimal("30.00"), new BigDecimal("15.00"));

        List<ImportedTransaction> transfers =
                result.getTransactions().stream().filter(ImportedTransaction::isTransfer).toList();
        assertThat(transfers).hasSize(2);
        assertThat(transfers)
                .extracting(ImportedTransaction::getTransferGroupKey)
                .containsOnly("skrooge:transfer-group:999");
        assertThat(transfers)
                .extracting(ImportedTransaction::getToAccountName)
                .contains("Savings", "Checking");

        assertThat(result.getSkroogeMetadata().getAccounts())
                .filteredOn(account -> account.getName().equals("Checking"))
                .singleElement()
                .satisfies(
                        account -> {
                            assertThat(account.getOpeningBalance())
                                    .isEqualByComparingTo(new BigDecimal("1000.00"));
                            assertThat(account.getCurrency()).isEqualTo("EUR");
                        });
    }

    @Test
    @DisplayName("Should reject JSON exports missing required Skrooge collections")
    void shouldRejectJsonWithoutRequiredCollections() {
        String invalidJson = "{" + "\"account\": []" + "}";

        assertThatThrownBy(
                        () ->
                                parser.parseFile(
                                        new ByteArrayInputStream(
                                                invalidJson.getBytes(StandardCharsets.UTF_8)),
                                        "invalid.json"))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("missing required collections");
    }

    @Test
    @DisplayName("Should determine transfer direction by f_value sign, not JSON operation order")
    void shouldDetermineTransferDirectionByFValueSign() throws IOException {
        // In this sample, the receiving account's operation (positive f_value) appears FIRST
        // in the JSON. Without the fix, the transfer would be reversed.
        SkroogeImportParseResult result =
                parser.parseFile(
                        new ByteArrayInputStream(
                                transferDirectionJson().getBytes(StandardCharsets.UTF_8)),
                        "test.json");

        List<ImportedTransaction> transfers =
                result.getTransactions().stream().filter(ImportedTransaction::isTransfer).toList();
        assertThat(transfers).hasSize(2);

        // The first transfer transaction should be the SOURCE (negative amount),
        // which is the Savings account (op id=2, f_value=-200), even though
        // the Checking operation (op id=3, f_value=+200) appears first in JSON.
        ImportedTransaction sourceTransfer = transfers.get(0);
        assertThat(sourceTransfer.getAmount()).isEqualByComparingTo(new BigDecimal("-200.00"));
        assertThat(sourceTransfer.getAccountName()).isEqualTo("Savings");
        assertThat(sourceTransfer.getToAccountName()).isEqualTo("Checking");
    }

    @Test
    @DisplayName("Should recognize French virement categories as transfers")
    void shouldRecognizeFrenchVirementCategoriesAsTransfers() throws IOException {
        SkroogeImportParseResult result =
                parser.parseFile(
                        new ByteArrayInputStream(
                                virementTransferJson().getBytes(StandardCharsets.UTF_8)),
                        "test.json");

        List<ImportedTransaction> transfers =
                result.getTransactions().stream().filter(ImportedTransaction::isTransfer).toList();
        assertThat(transfers).hasSize(2);
        assertThat(transfers)
                .extracting(ImportedTransaction::getTransferGroupKey)
                .containsOnly("skrooge:transfer-group:777");
    }

    @Test
    @DisplayName("Should derive source account balance deltas from Skrooge operation balances")
    void shouldDeriveSourceAccountBalanceDeltasFromOperationBalances() throws IOException {
        SkroogeImportParseResult result =
                parser.parseFile(
                        new ByteArrayInputStream(
                                operationBalanceDeltaJson().getBytes(StandardCharsets.UTF_8)),
                        "test.json");

        ImportedTransaction fee =
                result.getTransactions().stream()
                        .filter(
                                transaction ->
                                        "skrooge:operation:3"
                                                .equals(transaction.getReferenceNumber()))
                        .findFirst()
                        .orElseThrow();

        assertThat(fee.getAmount()).isEqualByComparingTo(new BigDecimal("-2.99"));
        assertThat(fee.getSourceAccountBalanceDelta())
                .isEqualByComparingTo(new BigDecimal("-1959.50"));
    }

    @Test
    @DisplayName("Should not derive source account balance deltas from valuation balances")
    void shouldNotDeriveSourceAccountBalanceDeltasFromValuationBalances() throws IOException {
        SkroogeImportParseResult result =
                parser.parseFile(
                        new ByteArrayInputStream(
                                operationBalanceWithoutEnteredDeltaJson()
                                        .getBytes(StandardCharsets.UTF_8)),
                        "test.json");

        ImportedTransaction fee =
                result.getTransactions().stream()
                        .filter(
                                transaction ->
                                        "skrooge:operation:3"
                                                .equals(transaction.getReferenceNumber()))
                        .findFirst()
                        .orElseThrow();

        assertThat(fee.getSourceAccountBalanceDelta()).isNull();
    }

    @Test
    @DisplayName("Should convert investment share quantities to monetary amounts using unit prices")
    void shouldConvertInvestmentQuantitiesToMonetaryAmounts() throws IOException {
        SkroogeImportParseResult result =
                parser.parseFile(
                        new ByteArrayInputStream(
                                investmentUnitJson().getBytes(StandardCharsets.UTF_8)),
                        "test.json");

        ImportedTransaction investmentTx =
                result.getTransactions().stream()
                        .filter(tx -> !tx.isTransfer())
                        .findFirst()
                        .orElseThrow();
        // 0.01 shares × 50000 EUR/share = 500 EUR
        assertThat(investmentTx.getAmount()).isEqualByComparingTo(new BigDecimal("500.00"));
        assertThat(investmentTx.getCurrency()).isEqualTo("EUR");
    }

    @Test
    @DisplayName("Should keep share purchase groups as source operations, not account transfers")
    void shouldKeepSharePurchaseGroupsAsSourceOperations() throws IOException {
        SkroogeImportParseResult result =
                parser.parseFile(
                        new ByteArrayInputStream(
                                crossUnitTransferJson().getBytes(StandardCharsets.UTF_8)),
                        "test.json");

        List<ImportedTransaction> transfers =
                result.getTransactions().stream().filter(ImportedTransaction::isTransfer).toList();
        assertThat(transfers).isEmpty();
        assertThat(result.getTransactions())
                .extracting(ImportedTransaction::getAccountName)
                .containsExactly("Checking", "Investment");
        List<BigDecimal> amounts =
                result.getTransactions().stream().map(ImportedTransaction::getAmount).toList();
        assertThat(amounts.get(0)).isEqualByComparingTo(new BigDecimal("-1000.00"));
        assertThat(amounts.get(1)).isEqualByComparingTo(new BigDecimal("1000.00"));
    }

    @Test
    @DisplayName("Should keep object crypto purchase groups as source operations")
    void shouldKeepObjectCryptoPurchaseGroupsAsSourceOperations() throws IOException {
        SkroogeImportParseResult result =
                parser.parseFile(
                        new ByteArrayInputStream(
                                objectCryptoPurchaseJson().getBytes(StandardCharsets.UTF_8)),
                        "test.json");

        List<ImportedTransaction> transfers =
                result.getTransactions().stream().filter(ImportedTransaction::isTransfer).toList();
        assertThat(transfers).isEmpty();
        assertThat(result.getTransactions())
                .extracting(ImportedTransaction::getAccountName)
                .containsExactly("Checking", "Crypto Wallet");
        List<BigDecimal> amounts =
                result.getTransactions().stream().map(ImportedTransaction::getAmount).toList();
        assertThat(amounts.get(0)).isEqualByComparingTo(new BigDecimal("-1000.00"));
        assertThat(amounts.get(1)).isEqualByComparingTo(new BigDecimal("1000.00"));
    }

    @Test
    @DisplayName("Should ignore zero synthetic balance when deriving account currency")
    void shouldIgnoreZeroSyntheticBalanceWhenDerivingAccountCurrency() throws IOException {
        SkroogeImportParseResult result =
                parser.parseFile(
                        new ByteArrayInputStream(
                                zeroSyntheticCurrencyJson().getBytes(StandardCharsets.UTF_8)),
                        "test.json");

        assertThat(result.getSkroogeMetadata().getAccounts())
                .filteredOn(account -> account.getName().equals("Polymarket"))
                .singleElement()
                .satisfies(account -> assertThat(account.getCurrency()).isEqualTo("EUR"));
    }

    @Test
    @DisplayName("Should include accounts whose only operation is a synthetic balance entry")
    void shouldIncludeAccountsWithOnlySyntheticBalance() throws IOException {
        SkroogeImportParseResult result =
                parser.parseFile(
                        new ByteArrayInputStream(
                                syntheticOnlyAccountJson().getBytes(StandardCharsets.UTF_8)),
                        "test.json");

        assertThat(result.getSkroogeMetadata().getAccounts())
                .extracting(SkroogeImportMetadata.SkroogeAccount::getName)
                .contains("Loan Account");
    }

    @Test
    @DisplayName("Should resolve share/crypto unit currency to parent unit currency")
    void shouldResolveShareUnitCurrencyToParentCurrency() throws IOException {
        SkroogeImportParseResult result =
                parser.parseFile(
                        new ByteArrayInputStream(
                                investmentUnitJson().getBytes(StandardCharsets.UTF_8)),
                        "test.json");

        // The share unit has parent=EUR, so the account currency should be EUR
        assertThat(result.getSkroogeMetadata().getAccounts())
                .filteredOn(account -> account.getName().equals("Investment Account"))
                .singleElement()
                .satisfies(account -> assertThat(account.getCurrency()).isEqualTo("EUR"));
    }

    @Test
    @DisplayName("Should map Skrooge D (Deposit) and L (Loan) account types")
    void shouldMapDepositAndLoanAccountTypes() throws IOException {
        SkroogeImportParseResult result =
                parser.parseFile(
                        new ByteArrayInputStream(
                                accountTypeMappingJson().getBytes(StandardCharsets.UTF_8)),
                        "test.json");

        assertThat(result.getSkroogeMetadata().getAccounts())
                .filteredOn(account -> account.getName().equals("Deposit Account"))
                .singleElement()
                .satisfies(
                        account ->
                                assertThat(account.getAccountType())
                                        .isEqualTo(AccountType.SAVINGS));
    }

    @Test
    @DisplayName("Should map Skrooge asset account type to investment")
    void shouldMapAssetAccountTypeToInvestment() throws IOException {
        SkroogeImportParseResult result =
                parser.parseFile(
                        new ByteArrayInputStream(
                                investmentUnitJson().getBytes(StandardCharsets.UTF_8)),
                        "test.json");

        assertThat(result.getSkroogeMetadata().getAccounts())
                .filteredOn(account -> account.getName().equals("Investment Account"))
                .singleElement()
                .satisfies(
                        account ->
                                assertThat(account.getAccountType())
                                        .isEqualTo(AccountType.INVESTMENT));
    }

    @Test
    @DisplayName("Should use fallback institution name when bank name is blank")
    void shouldUseFallbackInstitutionNameWhenBlank() throws IOException {
        SkroogeImportParseResult result =
                parser.parseFile(
                        new ByteArrayInputStream(
                                blankInstitutionJson().getBytes(StandardCharsets.UTF_8)),
                        "test.json");

        assertThat(result.getSkroogeMetadata().getInstitutions())
                .filteredOn(institution -> institution.getSourceId().equals(6L))
                .singleElement()
                .satisfies(
                        institution ->
                                assertThat(institution.getName()).isEqualTo("Institution 6"));
    }

    private String sampleSkroogeJson() {
        return """
                {
                  "bank": [
                    { "id": 1, "t_name": "Demo Bank", "t_icon": "bank-logo" }
                  ],
                  "account": [
                    {
                      "id": 10,
                      "rd_bank_id": 1,
                      "t_name": "Checking",
                      "t_type": "C",
                      "t_comment": "Main account",
                      "t_number": "CHK-001",
                      "t_close": false
                    },
                    {
                      "id": 20,
                      "rd_bank_id": 1,
                      "t_name": "Savings",
                      "t_type": "S",
                      "t_comment": "Savings account",
                      "t_number": "SAV-001",
                      "t_close": false
                    }
                  ],
                  "category": [
                    { "id": 100, "rd_category_id": 0, "t_name": "Food", "t_fullname": "Food" },
                    { "id": 101, "rd_category_id": 100, "t_name": "Groceries", "t_fullname": "Food:Groceries" },
                    { "id": 200, "rd_category_id": 0, "t_name": "Transfer", "t_fullname": "Transfer" }
                  ],
                  "payee": [
                    { "id": 1, "t_name": "Local Market" },
                    { "id": 2, "t_name": "Transfer Desk" }
                  ],
                  "unit": [
                    { "id": 1, "t_name": "Euro (EUR)", "t_symbol": "€", "t_internet_code": "EUR" }
                  ],
                  "operation": [
                    {
                      "id": 1,
                      "rd_account_id": 10,
                      "d_date": "2024-01-10",
                      "i_group_id": 0,
                      "r_payee_id": 1,
                      "t_comment": "Weekly groceries",
                      "t_mode": "CARD",
                      "t_status": "Y",
                      "rc_unit_id": 1
                    },
                    {
                      "id": 2,
                      "rd_account_id": 10,
                      "d_date": "2024-01-12",
                      "i_group_id": 999,
                      "r_payee_id": 2,
                      "t_comment": "Move to savings",
                      "t_mode": "TRANSFER",
                      "t_status": "Y",
                      "rc_unit_id": 1
                    },
                    {
                      "id": 3,
                      "rd_account_id": 20,
                      "d_date": "2024-01-12",
                      "i_group_id": 999,
                      "r_payee_id": 2,
                      "t_comment": "Move from checking",
                      "t_mode": "TRANSFER",
                      "t_status": "Y",
                      "rc_unit_id": 1
                    },
                    {
                      "id": 4,
                      "rd_account_id": 10,
                      "d_date": "0000-00-00",
                      "i_group_id": 0,
                      "r_payee_id": 0,
                      "t_comment": "",
                      "t_mode": "",
                      "t_status": "",
                      "rc_unit_id": 1
                    }
                  ],
                  "suboperation": [
                    {
                      "id": 11,
                      "rd_operation_id": 1,
                      "r_category_id": 101,
                      "f_value": "-30.00",
                      "t_comment": "Fruit"
                    },
                    {
                      "id": 12,
                      "rd_operation_id": 1,
                      "r_category_id": 101,
                      "f_value": "-15.00",
                      "t_comment": "Bread"
                    },
                    {
                      "id": 21,
                      "rd_operation_id": 2,
                      "r_category_id": 200,
                      "f_value": "-200.00",
                      "t_comment": ""
                    },
                    {
                      "id": 31,
                      "rd_operation_id": 3,
                      "r_category_id": 200,
                      "f_value": "200.00",
                      "t_comment": ""
                    },
                    {
                      "id": 41,
                      "rd_operation_id": 4,
                      "r_category_id": 0,
                      "f_value": "1000.00",
                      "t_comment": ""
                    }
                  ]
                }
                """;
    }

    /** Transfer where the receiving account's operation appears FIRST in JSON. */
    private String transferDirectionJson() {
        return """
                {
                  "bank": [{ "id": 1, "t_name": "Demo Bank" }],
                  "account": [
                    { "id": 10, "rd_bank_id": 1, "t_name": "Checking", "t_type": "C", "t_number": "CHK", "t_close": false },
                    { "id": 20, "rd_bank_id": 1, "t_name": "Savings", "t_type": "S", "t_number": "SAV", "t_close": false }
                  ],
                  "category": [{ "id": 200, "rd_category_id": 0, "t_name": "Transfer", "t_fullname": "Transfer" }],
                  "payee": [{ "id": 1, "t_name": "Transfer Desk" }],
                  "unit": [{ "id": 1, "t_name": "Euro (EUR)", "t_symbol": "€", "t_internet_code": "", "t_type": "1", "rd_unit_id": 0 }],
                  "operation": [
                    { "id": 1, "rd_account_id": 10, "d_date": "0000-00-00", "i_group_id": 0, "r_payee_id": 0, "t_mode": "", "t_status": "", "rc_unit_id": 1 },
                    { "id": 3, "rd_account_id": 10, "d_date": "2024-01-12", "i_group_id": 999, "r_payee_id": 1, "t_mode": "TRANSFER", "t_status": "Y", "rc_unit_id": 1 },
                    { "id": 2, "rd_account_id": 20, "d_date": "2024-01-12", "i_group_id": 999, "r_payee_id": 1, "t_mode": "TRANSFER", "t_status": "Y", "rc_unit_id": 1 }
                  ],
                  "suboperation": [
                    { "id": 41, "rd_operation_id": 1, "r_category_id": 0, "f_value": "500.00" },
                    { "id": 31, "rd_operation_id": 3, "r_category_id": 200, "f_value": "200.00" },
                    { "id": 21, "rd_operation_id": 2, "r_category_id": 200, "f_value": "-200.00" }
                  ],
                  "unitvalue": [{ "id": 1, "rd_unit_id": 1, "d_date": "1999-01-01", "f_quantity": 1 }]
                }
                """;
    }

    /** Same-currency transfer using Skrooge's French virement category naming. */
    private String virementTransferJson() {
        return """
                {
                  "bank": [{ "id": 1, "t_name": "Demo Bank" }],
                  "account": [
                    { "id": 10, "rd_bank_id": 1, "t_name": "Checking", "t_type": "C", "t_number": "CHK", "t_close": false },
                    { "id": 20, "rd_bank_id": 1, "t_name": "Savings", "t_type": "S", "t_number": "SAV", "t_close": false }
                  ],
                  "category": [{ "id": 200, "rd_category_id": 0, "t_name": "Virements émis", "t_fullname": "Virements émis" }],
                  "payee": [{ "id": 1, "t_name": "Transfer Desk" }],
                  "unit": [{ "id": 1, "t_name": "Euro (EUR)", "t_symbol": "€", "t_internet_code": "", "t_type": "1", "rd_unit_id": 0 }],
                  "operation": [
                    { "id": 2, "rd_account_id": 10, "d_date": "2024-01-12", "i_group_id": 777, "r_payee_id": 1, "t_mode": "Virement", "t_status": "Y", "rc_unit_id": 1 },
                    { "id": 3, "rd_account_id": 20, "d_date": "2024-01-12", "i_group_id": 777, "r_payee_id": 1, "t_mode": "Virement", "t_status": "Y", "rc_unit_id": 1 }
                  ],
                  "suboperation": [
                    { "id": 21, "rd_operation_id": 2, "r_category_id": 200, "f_value": "-200.00" },
                    { "id": 31, "rd_operation_id": 3, "r_category_id": 200, "f_value": "200.00" }
                  ],
                  "unitvalue": [{ "id": 1, "rd_unit_id": 1, "d_date": "1999-01-01", "f_quantity": 1 }]
                }
                """;
    }

    /** Operation balances expose Skrooge's source balance deltas for fallback imports. */
    private String operationBalanceDeltaJson() {
        return """
                {
                  "bank": [{ "id": 1, "t_name": "Demo Bank" }],
                  "account": [
                    { "id": 10, "rd_bank_id": 1, "t_name": "FCFA Savings", "t_type": "S", "t_number": "SAV", "t_close": false }
                  ],
                  "category": [{ "id": 100, "rd_category_id": 0, "t_name": "Fees", "t_fullname": "Fees" }],
                  "payee": [{ "id": 1, "t_name": "Bank" }],
                  "unit": [
                    { "id": 1, "t_name": "Euro (EUR)", "t_symbol": "€", "t_internet_code": "", "t_type": "1", "rd_unit_id": 0 },
                    { "id": 4, "t_name": "Franc CFA de l'Afrique de l'Ouest (XOF)", "t_symbol": "CFA", "t_internet_code": "", "t_type": "1", "rd_unit_id": 0 }
                  ],
                  "operation": [
                    { "id": 1, "rd_account_id": 10, "d_date": "0000-00-00", "i_group_id": 0, "r_payee_id": 0, "t_mode": "", "t_status": "", "rc_unit_id": 4 },
                    { "id": 2, "rd_account_id": 10, "d_date": "2024-01-01", "i_group_id": 0, "r_payee_id": 1, "t_mode": "Virement", "t_status": "Y", "rc_unit_id": 1 },
                    { "id": 3, "rd_account_id": 10, "d_date": "2024-01-02", "i_group_id": 0, "r_payee_id": 1, "t_mode": "Débit", "t_status": "Y", "rc_unit_id": 1 }
                  ],
                  "suboperation": [
                    { "id": 11, "rd_operation_id": 1, "r_category_id": 0, "f_value": "4950.00" },
                    { "id": 21, "rd_operation_id": 2, "r_category_id": 100, "f_value": "155.44" },
                    { "id": 31, "rd_operation_id": 3, "r_category_id": 100, "f_value": "-2.99" }
                  ],
                  "operationbalance": [
                    { "r_operation_id": 1, "f_balance": 7.54623040545, "f_balance_entered": 4950.00 },
                    { "r_operation_id": 2, "f_balance": 162.98623040545, "f_balance_entered": 5105.44 },
                    { "r_operation_id": 3, "f_balance": 159.99623040545, "f_balance_entered": 3145.94 }
                  ],
                  "unitvalue": [{ "id": 1, "rd_unit_id": 1, "d_date": "1999-01-01", "f_quantity": 1 }]
                }
                """;
    }

    private String operationBalanceWithoutEnteredDeltaJson() {
        return operationBalanceDeltaJson()
                .replace(", \"f_balance_entered\": 4950.00", "")
                .replace(", \"f_balance_entered\": 5105.44", "")
                .replace(", \"f_balance_entered\": 3145.94", "");
    }

    /** Investment operation with a share unit (type S) and unitvalue price. */
    private String investmentUnitJson() {
        return """
                {
                  "bank": [{ "id": 1, "t_name": "Demo Bank" }],
                  "account": [
                    { "id": 10, "rd_bank_id": 1, "t_name": "Investment Account", "t_type": "A", "t_number": "", "t_close": false }
                  ],
                  "category": [{ "id": 100, "rd_category_id": 0, "t_name": "Investment", "t_fullname": "Investment" }],
                  "payee": [{ "id": 1, "t_name": "Broker" }],
                  "unit": [
                    { "id": 1, "t_name": "Euro (EUR)", "t_symbol": "€", "t_internet_code": "", "t_type": "1", "rd_unit_id": 0 },
                    { "id": 5, "t_name": "Test Share", "t_symbol": "TST", "t_internet_code": "", "t_type": "S", "rd_unit_id": 1 }
                  ],
                  "operation": [
                    { "id": 1, "rd_account_id": 10, "d_date": "0000-00-00", "i_group_id": 0, "r_payee_id": 0, "t_mode": "", "t_status": "", "rc_unit_id": 1 },
                    { "id": 2, "rd_account_id": 10, "d_date": "2024-06-15", "i_group_id": 0, "r_payee_id": 1, "t_mode": "Buy", "t_status": "Y", "rc_unit_id": 5 }
                  ],
                  "suboperation": [
                    { "id": 11, "rd_operation_id": 1, "r_category_id": 0, "f_value": "0" },
                    { "id": 21, "rd_operation_id": 2, "r_category_id": 100, "f_value": "0.01" }
                  ],
                  "unitvalue": [
                    { "id": 1, "rd_unit_id": 1, "d_date": "1999-01-01", "f_quantity": 1 },
                    { "id": 2, "rd_unit_id": 5, "d_date": "2024-06-01", "f_quantity": 50000 }
                  ]
                }
                """;
    }

    /** Cross-unit transfer: EUR account sends EUR, share account receives shares. */
    private String crossUnitTransferJson() {
        return """
                {
                  "bank": [{ "id": 1, "t_name": "Demo Bank" }],
                  "account": [
                    { "id": 10, "rd_bank_id": 1, "t_name": "Checking", "t_type": "C", "t_number": "CHK", "t_close": false },
                    { "id": 20, "rd_bank_id": 1, "t_name": "Investment", "t_type": "A", "t_number": "", "t_close": false }
                  ],
                  "category": [{ "id": 200, "rd_category_id": 0, "t_name": "Transfer", "t_fullname": "Transfer" }],
                  "payee": [{ "id": 1, "t_name": "Broker" }],
                  "unit": [
                    { "id": 1, "t_name": "Euro (EUR)", "t_symbol": "€", "t_internet_code": "", "t_type": "1", "rd_unit_id": 0 },
                    { "id": 5, "t_name": "Test Share", "t_symbol": "TST", "t_internet_code": "", "t_type": "S", "rd_unit_id": 1 }
                  ],
                  "operation": [
                    { "id": 1, "rd_account_id": 10, "d_date": "2024-06-15", "i_group_id": 42, "r_payee_id": 1, "t_mode": "Buy", "t_status": "Y", "rc_unit_id": 1 },
                    { "id": 2, "rd_account_id": 20, "d_date": "2024-06-15", "i_group_id": 42, "r_payee_id": 1, "t_mode": "Buy", "t_status": "Y", "rc_unit_id": 5 }
                  ],
                  "suboperation": [
                    { "id": 11, "rd_operation_id": 1, "r_category_id": 200, "f_value": "-1000.00" },
                    { "id": 21, "rd_operation_id": 2, "r_category_id": 200, "f_value": "0.02" }
                  ],
                  "unitvalue": [
                    { "id": 1, "rd_unit_id": 1, "d_date": "1999-01-01", "f_quantity": 1 },
                    { "id": 2, "rd_unit_id": 5, "d_date": "2024-06-01", "f_quantity": 50000 }
                  ]
                }
                """;
    }

    /** Cross-unit purchase: EUR account sends EUR, crypto account receives object units. */
    private String objectCryptoPurchaseJson() {
        return """
                {
                  "bank": [{ "id": 1, "t_name": "Demo Bank" }],
                  "account": [
                    { "id": 10, "rd_bank_id": 1, "t_name": "Checking", "t_type": "C", "t_number": "CHK", "t_close": false },
                    { "id": 20, "rd_bank_id": 1, "t_name": "Crypto Wallet", "t_type": "A", "t_number": "", "t_close": false }
                  ],
                  "category": [{ "id": 200, "rd_category_id": 0, "t_name": "Transfer", "t_fullname": "Transfer" }],
                  "payee": [{ "id": 1, "t_name": "Broker" }],
                  "unit": [
                    { "id": 1, "t_name": "Euro (EUR)", "t_symbol": "€", "t_internet_code": "", "t_type": "1", "rd_unit_id": 0 },
                    { "id": 5, "t_name": "Solana", "t_symbol": "SOL", "t_internet_code": "SOL/EUR", "t_type": "O", "rd_unit_id": 1 }
                  ],
                  "operation": [
                    { "id": 1, "rd_account_id": 10, "d_date": "2024-06-15", "i_group_id": 43, "r_payee_id": 1, "t_mode": "Buy", "t_status": "Y", "rc_unit_id": 1 },
                    { "id": 2, "rd_account_id": 20, "d_date": "2024-06-15", "i_group_id": 43, "r_payee_id": 1, "t_mode": "Buy", "t_status": "Y", "rc_unit_id": 5 }
                  ],
                  "suboperation": [
                    { "id": 11, "rd_operation_id": 1, "r_category_id": 200, "f_value": "-1000.00" },
                    { "id": 21, "rd_operation_id": 2, "r_category_id": 200, "f_value": "0.5" }
                  ],
                  "unitvalue": [
                    { "id": 1, "rd_unit_id": 1, "d_date": "1999-01-01", "f_quantity": 1 },
                    { "id": 2, "rd_unit_id": 5, "d_date": "2024-06-01", "f_quantity": 2000 }
                  ]
                }
                """;
    }

    /** Account with zero USD synthetic balance and real EUR activity. */
    private String zeroSyntheticCurrencyJson() {
        return """
                {
                  "bank": [{ "id": 1, "t_name": "Broker" }],
                  "account": [
                    { "id": 10, "rd_bank_id": 1, "t_name": "Polymarket", "t_type": "I", "t_number": "", "t_close": false }
                  ],
                  "category": [{ "id": 100, "rd_category_id": 0, "t_name": "Investment", "t_fullname": "Investment" }],
                  "payee": [{ "id": 1, "t_name": "Broker" }],
                  "unit": [
                    { "id": 1, "t_name": "Euro (EUR)", "t_symbol": "€", "t_internet_code": "", "t_type": "1", "rd_unit_id": 0 },
                    { "id": 2, "t_name": "Dollar américain (USD)", "t_symbol": "$", "t_internet_code": "USD/EUR", "t_type": "O", "rd_unit_id": 1 }
                  ],
                  "operation": [
                    { "id": 1, "rd_account_id": 10, "d_date": "0000-00-00", "i_group_id": 0, "r_payee_id": 0, "t_mode": "", "t_status": "", "rc_unit_id": 2 },
                    { "id": 2, "rd_account_id": 10, "d_date": "2026-06-24", "i_group_id": 0, "r_payee_id": 1, "t_mode": "Deposit", "t_status": "Y", "rc_unit_id": 1 }
                  ],
                  "suboperation": [
                    { "id": 11, "rd_operation_id": 1, "r_category_id": 0, "f_value": "0" },
                    { "id": 21, "rd_operation_id": 2, "r_category_id": 100, "f_value": "1072.91" }
                  ],
                  "unitvalue": [
                    { "id": 1, "rd_unit_id": 1, "d_date": "1999-01-01", "f_quantity": 1 },
                    { "id": 2, "rd_unit_id": 2, "d_date": "2026-06-24", "f_quantity": 0.875274 }
                  ]
                }
                """;
    }

    /** Account whose only operation is a synthetic balance (date 0000-00-00). */
    private String syntheticOnlyAccountJson() {
        return """
                {
                  "bank": [{ "id": 1, "t_name": "Demo Bank" }],
                  "account": [
                    { "id": 10, "rd_bank_id": 1, "t_name": "Loan Account", "t_type": "A", "t_number": "", "t_close": false }
                  ],
                  "category": [],
                  "payee": [],
                  "unit": [{ "id": 1, "t_name": "Euro (EUR)", "t_symbol": "€", "t_internet_code": "", "t_type": "1", "rd_unit_id": 0 }],
                  "operation": [
                    { "id": 1, "rd_account_id": 10, "d_date": "0000-00-00", "i_group_id": 0, "r_payee_id": 0, "t_mode": "", "t_status": "", "rc_unit_id": 1 }
                  ],
                  "suboperation": [
                    { "id": 11, "rd_operation_id": 1, "r_category_id": 0, "f_value": "744.95" }
                  ],
                  "unitvalue": [{ "id": 1, "rd_unit_id": 1, "d_date": "1999-01-01", "f_quantity": 1 }]
                }
                """;
    }

    /** Account with type D (Deposit) to verify type mapping. */
    private String accountTypeMappingJson() {
        return """
                {
                  "bank": [{ "id": 1, "t_name": "Demo Bank" }],
                  "account": [
                    { "id": 10, "rd_bank_id": 1, "t_name": "Deposit Account", "t_type": "D", "t_number": "DEP", "t_close": false }
                  ],
                  "category": [{ "id": 100, "rd_category_id": 0, "t_name": "Food", "t_fullname": "Food" }],
                  "payee": [{ "id": 1, "t_name": "Store" }],
                  "unit": [{ "id": 1, "t_name": "Euro (EUR)", "t_symbol": "€", "t_internet_code": "", "t_type": "1", "rd_unit_id": 0 }],
                  "operation": [
                    { "id": 1, "rd_account_id": 10, "d_date": "0000-00-00", "i_group_id": 0, "r_payee_id": 0, "t_mode": "", "t_status": "", "rc_unit_id": 1 },
                    { "id": 2, "rd_account_id": 10, "d_date": "2024-01-10", "i_group_id": 0, "r_payee_id": 1, "t_mode": "CARD", "t_status": "Y", "rc_unit_id": 1 }
                  ],
                  "suboperation": [
                    { "id": 11, "rd_operation_id": 1, "r_category_id": 0, "f_value": "0" },
                    { "id": 21, "rd_operation_id": 2, "r_category_id": 100, "f_value": "-10.00" }
                  ],
                  "unitvalue": [{ "id": 1, "rd_unit_id": 1, "d_date": "1999-01-01", "f_quantity": 1 }]
                }
                """;
    }

    /** Institution with blank name to verify fallback name. */
    private String blankInstitutionJson() {
        return """
                {
                  "bank": [{ "id": 6, "t_name": "" }],
                  "account": [
                    { "id": 10, "rd_bank_id": 6, "t_name": "Wallet", "t_type": "W", "t_number": "", "t_close": false }
                  ],
                  "category": [{ "id": 100, "rd_category_id": 0, "t_name": "Food", "t_fullname": "Food" }],
                  "payee": [{ "id": 1, "t_name": "Store" }],
                  "unit": [{ "id": 1, "t_name": "Euro (EUR)", "t_symbol": "€", "t_internet_code": "", "t_type": "1", "rd_unit_id": 0 }],
                  "operation": [
                    { "id": 1, "rd_account_id": 10, "d_date": "0000-00-00", "i_group_id": 0, "r_payee_id": 0, "t_mode": "", "t_status": "", "rc_unit_id": 1 },
                    { "id": 2, "rd_account_id": 10, "d_date": "2024-01-10", "i_group_id": 0, "r_payee_id": 1, "t_mode": "CARD", "t_status": "Y", "rc_unit_id": 1 }
                  ],
                  "suboperation": [
                    { "id": 11, "rd_operation_id": 1, "r_category_id": 0, "f_value": "0" },
                    { "id": 21, "rd_operation_id": 2, "r_category_id": 100, "f_value": "-10.00" }
                  ],
                  "unitvalue": [{ "id": 1, "rd_unit_id": 1, "d_date": "1999-01-01", "f_quantity": 1 }]
                }
                """;
    }
}
