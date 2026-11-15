package org.openfinance.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.StringReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.openfinance.dto.DataExportRequest;
import org.openfinance.entity.Account;
import org.openfinance.entity.AccountType;
import org.openfinance.entity.Asset;
import org.openfinance.entity.AssetType;
import org.openfinance.entity.Category;
import org.openfinance.entity.PropertyType;
import org.openfinance.entity.RealEstateProperty;
import org.openfinance.entity.Transaction;
import org.openfinance.entity.TransactionSplit;
import org.openfinance.entity.TransactionType;
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
    @ValueSource(strings = {"JSON", "CSV"})
    void omittedCollectionsAreNeitherReadNorIncludedInTheDownload(String format) throws Exception {
        DataExportRequest request =
                DataExportRequest.builder()
                        .format(format)
                        .includeAccounts(false)
                        .includeTransactions(false)
                        .includeAssets(false)
                        .includeLiabilities(false)
                        .includeBudgets(false)
                        .includeCategories(false)
                        .includeRealEstate(false)
                        .build();
        DataExportService.DataExportFile file = service.exportUserData(7L, request);
        assertThat(file.metadata().getAccountCount()).isZero();
        assertThat(file.metadata().getTransactionCount()).isZero();
        assertThat(file.metadata().getFileSizeBytes()).isEqualTo(file.content().length);
        assertThat(file.metadata().getExpiresAt())
                .isEqualTo(file.metadata().getGeneratedAt().plusHours(24));
        verifyNoInteractions(
                accounts,
                transactions,
                assets,
                liabilities,
                budgets,
                categories,
                properties,
                splits);
        if (format.equals("JSON")) {
            JsonNode json = new ObjectMapper().readTree(file.content());
            assertThat(json.size()).isEqualTo(1);
            assertThat(json.path("exportMetadata").path("userId").asLong()).isEqualTo(7L);
        } else {
            assertThat(new String(file.content(), StandardCharsets.UTF_8))
                    .contains("=== Export Metadata ===")
                    .doesNotContain("=== Accounts ===", "=== Transactions ===");
        }
    }

    @ParameterizedTest
    @CsvSource({
        ",,false,4",
        "2024-06-10,,false,3",
        ",2024-06-10,false,2",
        "2024-06-10,2024-06-10,false,1",
        "2024-06-10,2024-06-10,true,2",
        ",,true,5"
    })
    void dateBoundsAreInclusiveAndDeletedRowsAreOptIn(
            LocalDate from, LocalDate to, boolean includeDeleted, int expected) throws Exception {
        LocalDate day = LocalDate.of(2024, 6, 10);
        List<Transaction> rows =
                List.of(
                        exportTransaction(1, day.minusDays(1)),
                        exportTransaction(2, day),
                        exportTransaction(3, day.plusDays(1)),
                        exportTransaction(4, day.plusDays(2)),
                        exportTransaction(5, day));
        rows.get(3).setIsDeleted(null);
        rows.get(4).setIsDeleted(true);
        when(transactions.findAllForExport(1L)).thenReturn(rows);
        DataExportService.DataExportFile file =
                service.exportUserData(
                        1L,
                        DataExportRequest.builder()
                                .format("JSON")
                                .startDate(from)
                                .endDate(to)
                                .includeDeleted(includeDeleted)
                                .build());
        JsonNode exported = new ObjectMapper().readTree(file.content()).path("transactions");
        assertThat(file.metadata().getTransactionCount()).isEqualTo(expected);
        assertThat(exported.size()).isEqualTo(expected);
        for (JsonNode row : exported) {
            LocalDate date = LocalDate.parse(row.path("date").asText());
            if (from != null) assertThat(date).isAfterOrEqualTo(from);
            if (to != null) assertThat(date).isBeforeOrEqualTo(to);
            if (!includeDeleted) assertThat(row.path("isDeleted").asBoolean()).isFalse();
        }
        verify(splits)
                .findByTransactionIdIn(
                        rows.stream()
                                .filter(
                                        tx ->
                                                includeDeleted
                                                        || !Boolean.TRUE.equals(tx.getIsDeleted()))
                                .filter(tx -> from == null || !tx.getDate().isBefore(from))
                                .filter(tx -> to == null || !tx.getDate().isAfter(to))
                                .map(Transaction::getId)
                                .toList());
    }

    @Test
    void largeExportsBatchSplitQueriesAndKeepSplitsWithTheirOwnTransactionInStableOrder()
            throws Exception {
        List<Transaction> rows =
                java.util.stream.LongStream.rangeClosed(1, 501)
                        .mapToObj(id -> exportTransaction(id, LocalDate.of(2024, 6, 10)))
                        .toList();
        when(transactions.findAllForExport(1L)).thenReturn(rows);
        when(splits.findByTransactionIdIn(
                        rows.subList(0, 500).stream().map(Transaction::getId).toList()))
                .thenReturn(
                        List.of(
                                TransactionSplit.builder()
                                        .id(2L)
                                        .transactionId(1L)
                                        .amount(BigDecimal.ONE)
                                        .build(),
                                TransactionSplit.builder()
                                        .id(1L)
                                        .transactionId(1L)
                                        .amount(BigDecimal.TEN)
                                        .build()));
        when(splits.findByTransactionIdIn(List.of(501L)))
                .thenReturn(
                        List.of(
                                TransactionSplit.builder()
                                        .id(3L)
                                        .transactionId(501L)
                                        .amount(BigDecimal.ZERO)
                                        .build()));
        JsonNode exported =
                new ObjectMapper()
                        .readTree(
                                service.exportUserData(
                                                1L,
                                                DataExportRequest.builder().format("JSON").build())
                                        .content())
                        .path("transactions");
        assertThat(exported.size()).isEqualTo(501);
        assertThat(exported.get(0).path("splits").get(0).path("id").asLong()).isEqualTo(1);
        assertThat(exported.get(0).path("splits").get(1).path("id").asLong()).isEqualTo(2);
        assertThat(exported.get(1).path("splits").isEmpty()).isTrue();
        assertThat(exported.get(500).path("splits").get(0).path("id").asLong()).isEqualTo(3);
        verify(splits, org.mockito.Mockito.times(2)).findByTransactionIdIn(anyList());
    }

    @Test
    void soldAssetsAndPropertiesRetainOwnershipDatesAndSaleLinks() throws Exception {
        LocalDate bought = LocalDate.of(2020, 1, 2);
        LocalDate sold = LocalDate.of(2024, 3, 4);
        LocalDateTime created = bought.atTime(15, 30);
        when(assets.findByUserId(1L))
                .thenReturn(
                        List.of(
                                Asset.builder()
                                        .id(1L)
                                        .type(AssetType.VEHICLE)
                                        .purchaseDate(bought)
                                        .soldDate(sold)
                                        .saleTransactionId(91L)
                                        .createdAt(created)
                                        .build()));
        when(properties.findByUserId(1L))
                .thenReturn(
                        List.of(
                                RealEstateProperty.builder()
                                        .id(2L)
                                        .propertyType(PropertyType.RESIDENTIAL)
                                        .purchaseDate(bought)
                                        .soldDate(sold)
                                        .saleTransactionId(92L)
                                        .createdAt(created)
                                        .build()));
        when(categories.findByUserId(1L))
                .thenReturn(
                        List.of(
                                Category.builder()
                                        .id(3L)
                                        .name("Sale proceeds")
                                        .parentId(4L)
                                        .icon("home")
                                        .color("#123456")
                                        .isSystem(false)
                                        .createdAt(created)
                                        .build()));
        JsonNode exported =
                new ObjectMapper()
                        .readTree(
                                service.exportUserData(
                                                1L,
                                                DataExportRequest.builder().format("JSON").build())
                                        .content());
        for (String collection : List.of("assets", "realEstate")) {
            JsonNode row = exported.path(collection).get(0);
            assertThat(row.path("purchaseDate").asText()).isEqualTo(bought.toString());
            assertThat(row.path("soldDate").asText()).isEqualTo(sold.toString());
            assertThat(row.path("saleTransactionId").asLong())
                    .isEqualTo(collection.equals("assets") ? 91 : 92);
            assertThat(row.path("createdAt").asText()).isEqualTo("2020-01-02T15:30:00");
        }
        assertThat(exported.path("categories").get(0).path("parentId").asLong()).isEqualTo(4);
        assertThat(exported.path("categories").get(0).path("name").asText())
                .isEqualTo("Sale proceeds");
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void loanAndBudgetExportPreservesOptionalScheduleDates(boolean dated) throws Exception {
        LocalDate date = LocalDate.of(2024, 1, 31);
        org.openfinance.entity.Liability loan = new org.openfinance.entity.Liability();
        loan.setId(1L);
        loan.setType(org.openfinance.entity.LiabilityType.PERSONAL_LOAN);
        loan.setStartDate(dated ? date : null);
        loan.setEndDate(dated ? date.plusYears(2) : null);
        loan.setAmortizationStartDate(dated ? date.plusMonths(1) : null);
        org.openfinance.entity.Budget budget =
                org.openfinance.entity.Budget.builder()
                        .id(2L)
                        .period(org.openfinance.entity.BudgetPeriod.MONTHLY)
                        .startDate(dated ? date : null)
                        .endDate(dated ? date.plusMonths(1) : null)
                        .build();
        when(liabilities.findByUserIdOrderByCreatedAtDesc(7L)).thenReturn(List.of(loan));
        when(budgets.findByUserId(7L)).thenReturn(List.of(budget));
        JsonNode data =
                new ObjectMapper()
                        .readTree(
                                service.exportUserData(
                                                7L,
                                                DataExportRequest.builder().format("JSON").build())
                                        .content());
        assertThat(data.at("/liabilities/0/startDate").asText(null))
                .isEqualTo(dated ? date.toString() : null);
        assertThat(data.at("/liabilities/0/endDate").asText(null))
                .isEqualTo(dated ? date.plusYears(2).toString() : null);
        assertThat(data.at("/liabilities/0/amortizationStartDate").asText(null))
                .isEqualTo(dated ? date.plusMonths(1).toString() : null);
        assertThat(data.at("/budgets/0/startDate").asText(null))
                .isEqualTo(dated ? date.toString() : null);
        assertThat(data.at("/budgets/0/endDate").asText(null))
                .isEqualTo(dated ? date.plusMonths(1).toString() : null);
    }

    private Transaction exportTransaction(long id, LocalDate date) {
        return Transaction.builder()
                .id(id)
                .userId(1L)
                .accountId(2L)
                .type(TransactionType.EXPENSE)
                .date(date)
                .amount(BigDecimal.ONE)
                .currency("EUR")
                .isDeleted(false)
                .build();
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "=1+1",
                "+1+1",
                "-1+1",
                "@SUM(A1)",
                "  =1+1",
                "\t=1+1",
                "\uFEFF=1+1",
                "\u00a0=1+1",
                "\u202f@SUM(A1)"
            })
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
    @DisplayName("Gift exports retain received value separately from actual acquisition cost")
    void giftExportsRetainTheirPerformanceBaseline() throws Exception {
        BigDecimal received = new BigDecimal("150000.25");
        when(assets.findByUserId(1L))
                .thenReturn(
                        List.of(
                                org.openfinance.entity.Asset.builder()
                                        .id(1L)
                                        .name("Gifted possession")
                                        .type(org.openfinance.entity.AssetType.COLLECTIBLE)
                                        .acquisitionType(
                                                org.openfinance.entity.AcquisitionType.GIFT)
                                        .receivedValue(received)
                                        .quantity(BigDecimal.ONE)
                                        .purchasePrice(BigDecimal.ZERO)
                                        .currentPrice(received)
                                        .currency("EUR")
                                        .build()));
        when(properties.findByUserId(1L))
                .thenReturn(
                        List.of(
                                org.openfinance.entity.RealEstateProperty.builder()
                                        .id(2L)
                                        .name("Gifted property")
                                        .propertyType(
                                                org.openfinance.entity.PropertyType.RESIDENTIAL)
                                        .acquisitionType(
                                                org.openfinance.entity.AcquisitionType.GIFT)
                                        .receivedValue(received)
                                        .purchasePrice("0")
                                        .currentValue(received.toPlainString())
                                        .currency("EUR")
                                        .build()));
        byte[] content =
                service.exportUserData(1L, DataExportRequest.builder().format("JSON").build())
                        .content();
        com.fasterxml.jackson.databind.JsonNode exported =
                new com.fasterxml.jackson.databind.ObjectMapper().readTree(content);
        for (String section : List.of("assets", "realEstate")) {
            com.fasterxml.jackson.databind.JsonNode row = exported.path(section).get(0);
            assertThat(row.path("acquisitionType").asText()).isEqualTo("GIFT");
            assertThat(row.path("receivedValue").decimalValue()).isEqualByComparingTo(received);
            assertThat(row.path("purchasePrice").asText()).isEqualTo("0");
        }
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
                                        .loanComponent(
                                                org.openfinance.entity.LoanComponent.INTEREST)
                                        .amount(new BigDecimal("100.00"))
                                        .description("Line one, \"quoted\"\nline two")
                                        .tags("Holiday,Work")
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
            assertThat(split.path("loanComponent").asText()).isEqualTo("INTEREST");
            assertThat(split.path("tags").asText()).isEqualTo("Holiday,Work");
            assertThat(split.path("amount").decimalValue()).isEqualByComparingTo("100.00");
            assertThat(split.path("description").asText())
                    .isEqualTo("Line one, \"quoted\"\nline two");
        }
    }

    @Test
    void liabilityAndBudgetExportsRetainExactAmountsDatesAndCurrency() throws Exception {
        org.openfinance.entity.Liability liability = new org.openfinance.entity.Liability();
        liability.setId(5L);
        liability.setUserId(1L);
        liability.setName("Mortgage");
        liability.setType(org.openfinance.entity.LiabilityType.MORTGAGE);
        liability.setPrincipal("150000.123456789012345678");
        liability.setCurrentBalance("120000.25");
        liability.setInterestRate("2.15");
        liability.setMinimumPayment("825.99");
        liability.setCurrency("EUR");
        liability.setStartDate(java.time.LocalDate.of(2025, 1, 1));
        liability.setAmortizationStartDate(java.time.LocalDate.of(2026, 7, 5));
        liability.setEndDate(java.time.LocalDate.of(2045, 1, 1));
        when(liabilities.findByUserIdOrderByCreatedAtDesc(1L)).thenReturn(List.of(liability));
        when(budgets.findByUserId(1L))
                .thenReturn(
                        List.of(
                                org.openfinance.entity.Budget.builder()
                                        .id(7L)
                                        .userId(1L)
                                        .categoryId(9L)
                                        .amount("123.456789012345678")
                                        .currency("GBP")
                                        .period(org.openfinance.entity.BudgetPeriod.MONTHLY)
                                        .rollover(true)
                                        .startDate(java.time.LocalDate.of(2026, 1, 1))
                                        .endDate(java.time.LocalDate.of(2026, 1, 31))
                                        .build()));
        byte[] bytes =
                service.exportUserData(1L, DataExportRequest.builder().format("JSON").build())
                        .content();
        com.fasterxml.jackson.databind.JsonNode json =
                new com.fasterxml.jackson.databind.ObjectMapper().readTree(bytes);
        assertThat(json.path("liabilities").get(0).path("principal").asText())
                .isEqualTo("150000.123456789012345678");
        assertThat(json.path("liabilities").get(0).path("currency").asText()).isEqualTo("EUR");
        assertThat(json.path("liabilities").get(0).path("endDate").asText())
                .isEqualTo("2045-01-01");
        assertThat(json.path("liabilities").get(0).path("amortizationStartDate").asText())
                .isEqualTo("2026-07-05");
        assertThat(json.path("budgets").get(0).path("amount").asText())
                .isEqualTo("123.456789012345678");
        assertThat(json.path("budgets").get(0).path("currency").asText()).isEqualTo("GBP");
        assertThat(json.path("budgets").get(0).path("rollover").asBoolean()).isTrue();
    }
}
