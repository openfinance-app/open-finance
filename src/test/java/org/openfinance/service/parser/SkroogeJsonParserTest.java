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
import org.openfinance.dto.SkroogeImportParseResult;

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
}
