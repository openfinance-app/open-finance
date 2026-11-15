package org.openfinance.service.parser;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.openfinance.dto.SkroogeImportMetadata;
import org.openfinance.dto.SkroogeImportParseResult;
import org.openfinance.entity.AccountType;

@DisplayName("Skrooge metadata keeps account identity, lifecycle and valid dated prices")
class SkroogeMetadataDecisionTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private final SkroogeJsonParser parser = new SkroogeJsonParser(mapper);

    private ObjectNode document() throws IOException {
        return (ObjectNode)
                mapper.readTree(
                        """
                {"account":[{"id":10,"t_name":"Cash","t_type":"C"}],
                 "unit":[{"id":1,"t_type":"1","t_name":"Euro (EUR)"}],
                 "operation":[{"id":1,"rd_account_id":10,"rc_unit_id":1,"d_date":"2025-01-01","t_mode":"Deposit"}],
                 "suboperation":[{"id":1,"rd_operation_id":1,"f_value":"10.01"}]}
                """);
    }

    private SkroogeImportParseResult parse(ObjectNode root) throws IOException {
        return parser.parseFile(
                new ByteArrayInputStream(root.toString().getBytes(StandardCharsets.UTF_8)),
                "metadata.json");
    }

    private ObjectNode row(ObjectNode root, String collection) {
        return (ObjectNode) root.path(collection).get(0);
    }

    @ParameterizedTest
    @CsvSource({
        "C,CHECKING",
        "S,SAVINGS",
        "D,SAVINGS",
        "I,INVESTMENT",
        "P,INVESTMENT",
        "A,INVESTMENT",
        "W,CASH",
        "L,OTHER",
        "UNKNOWN,OTHER",
        "'',OTHER"
    })
    void preservesSourceAccountTypes(String type, AccountType expected) throws IOException {
        ObjectNode root = document();
        row(root, "account").put("t_type", type);
        assertThat(parse(root).getSkroogeMetadata().getAccounts())
                .singleElement()
                .satisfies(account -> assertThat(account.getAccountType()).isEqualTo(expected));
    }

    @ParameterizedTest
    @CsvSource({
        "true,false",
        "false,true",
        "\"Y\",false",
        "\"y\",false",
        "\"true\",false",
        "\"N\",true",
        "null,true",
        "\"\",true"
    })
    void preservesClosedAccountsAcrossExportBooleanRepresentations(String raw, boolean active)
            throws IOException {
        ObjectNode root = document();
        row(root, "account").set("t_close", mapper.readTree(raw));
        assertThat(parse(root).getSkroogeMetadata().getAccounts())
                .singleElement()
                .satisfies(account -> assertThat(account.getActive()).isEqualTo(active));
    }

    @ParameterizedTest
    @CsvSource({"Cash,IBAN-42,IBAN-42", "123456,'',123456", "12345,'',", "Cash,'',"})
    void onlyTreatsLongNumericNamesAsFallbackAccountNumbers(
            String name, String number, String expected) throws IOException {
        ObjectNode root = document();
        row(root, "account").put("t_name", name).put("t_number", number);
        SkroogeImportParseResult result = parse(root);
        assertThat(result.getSkroogeMetadata().getAccounts())
                .singleElement()
                .satisfies(account -> assertThat(account.getAccountNumber()).isEqualTo(expected));
        assertThat(result.getTransactions())
                .singleElement()
                .satisfies(
                        transaction ->
                                assertThat(transaction.getAccountNumber()).isEqualTo(expected));
    }

    @Test
    void missingInstitutionsDoNotDiscardTheAccountOrItsTransactions() throws IOException {
        ObjectNode root = document();
        row(root, "account").put("rd_bank_id", 99);
        root.withArray("bank").addObject().put("id", 100).put("t_name", "Unused bank");
        SkroogeImportParseResult result = parse(root);
        assertThat(result.getSkroogeMetadata().getInstitutions()).isEmpty();
        assertThat(result.getSkroogeMetadata().getAccounts())
                .singleElement()
                .satisfies(account -> assertThat(account.getSourceInstitutionId()).isEqualTo(99L));
        assertThat(result.getTransactions()).hasSize(1);
    }

    @ParameterizedTest
    @ValueSource(strings = {"null", "\"\"", "\"bad-id\"", "\"9223372036854775808\""})
    void malformedOperationIdsCannotAttachUnrelatedSplitRows(String id) throws IOException {
        ObjectNode root = document();
        row(root, "operation").set("id", mapper.readTree(id));
        root.withArray("suboperation")
                .addObject()
                .put("rd_operation_id", "bad-id")
                .put("f_value", 999);
        SkroogeImportParseResult result = parse(root);
        assertThat(result.getTransactions()).isEmpty();
        assertThat(result.getSkroogeMetadata().getAccounts()).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "{}",
                "{\"rd_account_id\":999,\"d_date\":\"2025-01-01\",\"f_rate\":2}",
                "{\"rd_account_id\":10,\"d_date\":\"not-a-date\",\"f_rate\":2}",
                "{\"rd_account_id\":10,\"d_date\":\"2025-01-01\",\"f_rate\":-2}",
                "{\"rd_account_id\":10,\"d_date\":\"2025-01-01\"}",
                "{\"rd_account_id\":10,\"d_date\":\"2025-01-01\",\"f_rate\":null}",
                "{\"rd_account_id\":10,\"d_date\":\"2025-01-01\",\"f_rate\":\"invalid\"}"
            })
    void reportsInvalidInterestRowsWithoutLosingCashTransactions(String interest)
            throws IOException {
        ObjectNode root = document();
        root.withArray("interest").add(mapper.readTree(interest));
        SkroogeImportParseResult result = parse(root);
        SkroogeImportMetadata metadata = result.getSkroogeMetadata();
        assertThat(metadata.getSourceInterestRateCount()).isEqualTo(1);
        assertThat(metadata.getSkippedInterestRateCount()).isEqualTo(1);
        assertThat(metadata.getInterestWarnings()).hasSize(1);
        assertThat(metadata.getInterestRates()).isEmpty();
        assertThat(result.getTransactions())
                .singleElement()
                .satisfies(
                        transaction ->
                                assertThat(transaction.getAmount()).isEqualByComparingTo("10.01"));
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "{}",
                "{\"rd_unit_id\":3,\"f_quantity\":2}",
                "{\"rd_unit_id\":3,\"d_date\":\"invalid\",\"f_quantity\":2}",
                "{\"rd_unit_id\":3,\"d_date\":\"2025-01-01\",\"f_quantity\":0}",
                "{\"rd_unit_id\":3,\"d_date\":\"2025-01-01\",\"f_quantity\":-2}",
                "{\"rd_unit_id\":3,\"d_date\":\"2025-01-01\",\"f_quantity\":\"\"}",
                "{\"rd_unit_id\":3,\"d_date\":\"2099-01-01\",\"f_quantity\":2}"
            })
    void doesNotInventAHoldingPriceFromUnusableQuotes(String quote) throws IOException {
        ObjectNode root = document();
        root.withArray("unit")
                .addObject()
                .put("id", 3)
                .put("t_type", "S")
                .put("t_name", "Share")
                .put("rd_unit_id", 1);
        row(root, "operation").put("rc_unit_id", 3);
        root.withArray("unitvalue").add(mapper.readTree(quote));
        assertThat(parse(root).getSkroogeMetadata().getHoldings())
                .singleElement()
                .satisfies(
                        holding -> {
                            assertThat(holding.currentPrice()).isNull();
                            assertThat(holding.priceDate()).isNull();
                            assertThat(holding.prices()).isEmpty();
                        });
    }

    @Test
    void absentParentQuoteDoesNotRelabelAChildQuoteAsACurrencyPrice() throws IOException {
        ObjectNode root = document();
        root.withArray("unit")
                .addObject()
                .put("id", 2)
                .put("t_type", "S")
                .put("t_name", "Fund")
                .put("rd_unit_id", 1);
        root.withArray("unit")
                .addObject()
                .put("id", 3)
                .put("t_type", "S")
                .put("t_name", "Share")
                .put("rd_unit_id", 2);
        row(root, "operation").put("rc_unit_id", 3);
        root.withArray("unitvalue")
                .addObject()
                .put("rd_unit_id", 3)
                .put("d_date", "2025-01-01")
                .put("f_quantity", 2);
        assertThat(parse(root).getSkroogeMetadata().getHoldings())
                .singleElement()
                .satisfies(holding -> assertThat(holding.prices()).isEmpty());
    }

    static Stream<Arguments> currencyMetadata() {
        return Stream.of(
                Arguments.of("{\"t_type\":\"2\",\"t_name\":\"US dollar (USD)\"}", "USD", false),
                Arguments.of(
                        "{\"t_type\":\"2\",\"t_name\":\"Malformed (USD\",\"t_symbol\":\"£\"}",
                        "GBP",
                        false),
                Arguments.of(
                        "{\"t_type\":\"2\",\"t_name\":\"Unknown (ZZZ)\",\"t_symbol\":\"$\"}",
                        "USD",
                        false),
                Arguments.of("{\"t_type\":\"2\",\"t_name\":\"Unknown (EU)\"}", "EUR", false),
                Arguments.of("{\"t_type\":\"2\",\"t_internet_code\":\"OTHER/GBP\"}", "GBP", false),
                Arguments.of(
                        "{\"t_type\":\"2\",\"t_internet_code\":\"EUR/UNKNOWN\"}", "EUR", false),
                Arguments.of(
                        "{\"t_type\":\"O\",\"t_internet_code\":\"GBP/UNKNOWN\"}", "GBP", false),
                Arguments.of("{\"t_type\":\"O\",\"t_internet_code\":\"BAD/UNKNOWN\"}", "EUR", true),
                Arguments.of("{\"t_type\":\"S\",\"rd_unit_id\":1}", "EUR", true),
                Arguments.of("{\"t_type\":\"S\",\"rd_unit_id\":999}", "EUR", true),
                Arguments.of("{\"t_type\":\"S\",\"rd_unit_id\":0}", "EUR", true),
                Arguments.of("{\"t_type\":\"S\"}", "EUR", true),
                Arguments.of("{\"t_type\":\"O\",\"t_symbol\":\"£\"}", "GBP", false),
                Arguments.of("{\"t_type\":\"O\",\"t_name\":\"Pounds (GBP)\"}", "GBP", false),
                Arguments.of("{\"t_type\":\"O\",\"t_name\":\"Unrecognized )GBP(\"}", "EUR", true));
    }

    @ParameterizedTest
    @MethodSource("currencyMetadata")
    @DisplayName(
            "Currency metadata fallbacks preserve denomination without classifying currencies as holdings")
    void resolvesPartialCurrencyMetadata(String json, String expectedCurrency, boolean holding)
            throws IOException {
        ObjectNode root = document();
        ObjectNode unit = (ObjectNode) mapper.readTree(json);
        unit.put("id", 2);
        root.withArray("unit").add(unit);
        row(root, "operation").put("rc_unit_id", 2);
        SkroogeImportParseResult result = parse(root);
        assertThat(result.getSkroogeMetadata().getAccounts())
                .singleElement()
                .satisfies(
                        account -> assertThat(account.getCurrency()).isEqualTo(expectedCurrency));
        assertThat(result.getSkroogeMetadata().getHoldings()).hasSize(holding ? 1 : 0);
        assertThat(result.getTransactions())
                .singleElement()
                .satisfies(
                        transaction -> {
                            assertThat(transaction.getCurrency()).isEqualTo(expectedCurrency);
                            assertThat(transaction.getAmount()).isEqualByComparingTo("10.01");
                        });
    }

    @ParameterizedTest
    @ValueSource(strings = {"missing-unit", "unknown-unit", "missing-parent", "empty-primary"})
    @DisplayName(
            "An incomplete unit reference preserves a cash movement without inventing a holding")
    void preservesCashWithIncompleteUnitReferences(String missing) throws IOException {
        ObjectNode root = document();
        if (missing.equals("missing-unit")) row(root, "operation").remove("rc_unit_id");
        else if (missing.equals("unknown-unit")) row(root, "operation").put("rc_unit_id", 999);
        else if (missing.equals("empty-primary")) root.remove("unit");
        else {
            root.withArray("unit")
                    .addObject()
                    .put("id", 2)
                    .put("t_type", "2")
                    .put("rd_unit_id", 999);
            row(root, "operation").put("rc_unit_id", 2);
            root.withArray("unitvalue")
                    .addObject()
                    .put("rd_unit_id", 2)
                    .put("d_date", "2025-01-01")
                    .put("f_quantity", "2");
        }
        SkroogeImportParseResult result = parse(root);
        assertThat(result.getSkroogeMetadata().getHoldings()).isEmpty();
        assertThat(result.getTransactions())
                .singleElement()
                .satisfies(
                        transaction -> {
                            assertThat(transaction.getCurrency())
                                    .isEqualTo(missing.equals("empty-primary") ? "USD" : "EUR");
                            assertThat(transaction.getAmount())
                                    .isEqualByComparingTo(
                                            missing.equals("missing-parent") ? "20.02" : "10.01");
                        });
    }

    @Test
    @DisplayName("A missing account reference does not borrow another account's identity")
    void missingAccountDoesNotBorrowMetadata() throws IOException {
        ObjectNode root = document();
        row(root, "operation").put("rd_account_id", 999);
        SkroogeImportParseResult result = parse(root);
        assertThat(result.getSkroogeMetadata().getAccounts()).isEmpty();
        assertThat(result.getTransactions())
                .singleElement()
                .satisfies(
                        transaction -> {
                            assertThat(transaction.getAccountName()).isNull();
                            assertThat(transaction.getAccountNumber()).isNull();
                            assertThat(transaction.getAmount()).isEqualByComparingTo("10.01");
                        });
    }
}
