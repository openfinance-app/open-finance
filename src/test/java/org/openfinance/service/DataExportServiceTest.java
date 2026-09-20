package org.openfinance.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.io.StringReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.openfinance.dto.DataExportRequest;
import org.openfinance.entity.Account;
import org.openfinance.entity.AccountType;
import org.openfinance.repository.AccountRepository;
import org.openfinance.repository.AssetRepository;
import org.openfinance.repository.BudgetRepository;
import org.openfinance.repository.CategoryRepository;
import org.openfinance.repository.LiabilityRepository;
import org.openfinance.repository.RealEstateRepository;
import org.openfinance.repository.TransactionRepository;
import org.openfinance.repository.TransactionSplitRepository;

@ExtendWith(MockitoExtension.class)
class DataExportServiceTest {
    @Mock AccountRepository accounts;
    @Mock TransactionRepository transactions;
    @Mock AssetRepository assets;
    @Mock LiabilityRepository liabilities;
    @Mock BudgetRepository budgets;
    @Mock CategoryRepository categories;
    @Mock RealEstateRepository properties;
    @Mock TransactionSplitRepository splits;
    @InjectMocks DataExportService service;

    @ParameterizedTest
    @ValueSource(strings = {"=1+1", "+1+1", "-1+1", "@SUM(A1)", "  =1+1", "\t=1+1", "\uFEFF=1+1"})
    @DisplayName("CSV neutralizes formula-leading text without changing numeric monetary cells")
    void preventsSpreadsheetFormulas(String name) {
        when(accounts.findByUserId(1L))
                .thenReturn(
                        List.of(
                                Account.builder()
                                        .id(1L)
                                        .name(name)
                                        .type(AccountType.CHECKING)
                                        .currency("EUR")
                                        .balance(new BigDecimal("-12.50"))
                                        .build()));
        String csv =
                new String(
                        service.exportUserData(
                                        1L, DataExportRequest.builder().format("CSV").build())
                                .content(),
                        StandardCharsets.UTF_8);
        assertThat(csv).contains("1,'" + name + ",CHECKING,EUR,-12.50,");
    }

    @Test
    @DisplayName("JSON exports retain original text without spreadsheet-specific escaping")
    void jsonPreservesSourceText() throws Exception {
        when(accounts.findByUserId(1L))
                .thenReturn(
                        List.of(
                                Account.builder()
                                        .id(1L)
                                        .name("=1+1")
                                        .type(AccountType.CHECKING)
                                        .currency("EUR")
                                        .balance(BigDecimal.TEN)
                                        .build()));
        byte[] content =
                service.exportUserData(1L, DataExportRequest.builder().format("JSON").build())
                        .content();
        assertThat(
                        new com.fasterxml.jackson.databind.ObjectMapper()
                                .readTree(content)
                                .path("accounts")
                                .get(0)
                                .path("name")
                                .asText())
                .isEqualTo("=1+1");
    }

    @Test
    @DisplayName("CSV retains booked amounts, movement links and JSON split details in valid cells")
    void csvPreservesFinancialMetadataAndQuotedSplitText() throws Exception {
        org.openfinance.entity.Transaction transaction =
                org.openfinance.entity.Transaction.builder()
                        .id(10L)
                        .accountId(20L)
                        .type(org.openfinance.entity.TransactionType.EXPENSE)
                        .amount(new BigDecimal("100.00"))
                        .currency("USD")
                        .date(LocalDate.of(2026, 1, 31))
                        .accountAmount(new BigDecimal("90.00"))
                        .accountCurrency("EUR")
                        .originalAmount(new BigDecimal("100.00"))
                        .originalCurrency("USD")
                        .conversionRate(new BigDecimal("0.9"))
                        .movementType(org.openfinance.entity.MovementType.REPAYMENT)
                        .principalAmount(new BigDecimal("75.00"))
                        .liabilityId(30L)
                        .trancheId(31L)
                        .description("=HYPERLINK(\"https://example.invalid\",\"label\")")
                        .build();
        when(transactions.findAllForExport(1L)).thenReturn(List.of(transaction));
        when(splits.findByTransactionIdIn(List.of(10L)))
                .thenReturn(
                        List.of(
                                org.openfinance.entity.TransactionSplit.builder()
                                        .id(40L)
                                        .transactionId(10L)
                                        .categoryId(50L)
                                        .amount(new BigDecimal("100.00"))
                                        .description("Line one, \"quoted\"\nline two")
                                        .build()));
        String csv =
                new String(
                        service.exportUserData(
                                        1L, DataExportRequest.builder().format("CSV").build())
                                .content(),
                        StandardCharsets.UTF_8);
        String section =
                csv.substring(
                        csv.indexOf("=== Transactions ===\n") + "=== Transactions ===\n".length());
        try (com.opencsv.CSVReader reader =
                new com.opencsv.CSVReaderBuilder(new StringReader(section))
                        .withCSVParser(new com.opencsv.RFC4180ParserBuilder().build())
                        .build()) {
            List<String> headers = Arrays.asList(reader.readNext());
            String[] row = reader.readNext();
            assertThat(row).hasSize(headers.size());
            assertThat(row[headers.indexOf("accountAmount")]).isEqualTo("90.00");
            assertThat(row[headers.indexOf("accountCurrency")]).isEqualTo("EUR");
            assertThat(row[headers.indexOf("originalAmount")]).isEqualTo("100.00");
            assertThat(row[headers.indexOf("conversionRate")]).isEqualTo("0.9");
            assertThat(row[headers.indexOf("movementType")]).isEqualTo("REPAYMENT");
            assertThat(row[headers.indexOf("principalAmount")]).isEqualTo("75.00");
            assertThat(row[headers.indexOf("liabilityId")]).isEqualTo("30");
            assertThat(row[headers.indexOf("trancheId")]).isEqualTo("31");
            assertThat(row[headers.indexOf("description")]).startsWith("'=HYPERLINK(");
            com.fasterxml.jackson.databind.JsonNode split =
                    new com.fasterxml.jackson.databind.ObjectMapper()
                            .readTree(row[headers.indexOf("splits")])
                            .get(0);
            assertThat(split.path("categoryId").asLong()).isEqualTo(50L);
            assertThat(split.path("amount").decimalValue()).isEqualByComparingTo("100.00");
            assertThat(split.path("description").asText())
                    .isEqualTo("Line one, \"quoted\"\nline two");
        }
    }
}
