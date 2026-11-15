package org.openfinance.service.parser;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.openfinance.dto.SkroogeImportParseResult;

@DisplayName("Skrooge import rejects malformed containers instead of silently losing their rows")
class SkroogeJsonParserValidationTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private final SkroogeJsonParser parser = new SkroogeJsonParser(mapper);

    private SkroogeImportParseResult parse(String json) throws IOException {
        return parser.parseFile(
                new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8)), "export.json");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "null", "true", "42", "[]", "{}"})
    void nonExportDocumentsProduceAnImportError(String json) {
        assertThatThrownBy(() -> parse(json)).isInstanceOf(IOException.class);
    }

    @ParameterizedTest(name = "{0} must not accept {1}")
    @MethodSource("invalidCollections")
    void requiredCollectionsMustBeArrays(String collection, String value) throws IOException {
        ObjectNode root =
                (ObjectNode)
                        mapper.readTree("{\"account\":[],\"operation\":[],\"suboperation\":[]}");
        root.set(collection, mapper.readTree(value));
        assertThatThrownBy(() -> parse(root.toString()))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("Skrooge");
    }

    static Stream<Arguments> invalidCollections() {
        return Stream.of("account", "operation", "suboperation")
                .flatMap(
                        collection ->
                                Stream.of("null", "{}", "42", "\"rows\"")
                                        .map(value -> Arguments.of(collection, value)));
    }

    @ParameterizedTest(name = "recognizes currency metadata {0}")
    @MethodSource("currencies")
    void currencyMetadataFallbacksDoNotRelabelNativeMoney(String currencyMetadata, String expected)
            throws IOException {
        String json =
                """
                {"account":[{"id":10,"t_name":"Cash","t_type":"C"}],
                 "unit":[{"id":1,"t_type":"1",%s}],
                 "operation":[{"id":1,"rd_account_id":10,"rc_unit_id":1,"d_date":"2024-01-01","t_mode":"Deposit","t_comment":"Test deposit"}],
                 "suboperation":[{"id":1,"rd_operation_id":1,"f_value":"12.123456789012345678"}]}
                """
                        .formatted(currencyMetadata);
        SkroogeImportParseResult result = parse(json);
        assertThat(result.getCurrency()).isEqualTo(expected);
        assertThat(result.getTransactions())
                .singleElement()
                .satisfies(
                        transaction -> {
                            assertThat(transaction.getCurrency()).isEqualTo(expected);
                            assertThat(transaction.getAmount())
                                    .isEqualByComparingTo("12.123456789012345678");
                        });
    }

    static Stream<Arguments> currencies() {
        return Stream.of(
                Arguments.of("\"t_name\":\"US Dollar ( usd )\"", "USD"),
                Arguments.of("\"t_name\":\"Invalid (ZZZ)\",\"t_symbol\":\"€\"", "EUR"),
                Arguments.of("\"t_name\":\"Unlabelled\",\"t_symbol\":\"£\"", "GBP"),
                Arguments.of(
                        "\"t_name\":\"Unlabelled\",\"t_internet_code\":\"UNKNOWN/JPY\"", "JPY"));
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "{\"id\":2,\"t_type\":\"S\",\"rd_unit_id\":2}",
                "{\"id\":2,\"t_type\":\"S\",\"rd_unit_id\":3},{\"id\":3,\"t_type\":\"O\",\"rd_unit_id\":2}"
            })
    void rejectsCyclicUnitParentsInsteadOfOverflowingTheImportThread(String units) {
        String json =
                """
                {"account":[{"id":1,"t_name":"Investment"}],
                 "operation":[{"id":1,"rd_account_id":1,"rc_unit_id":2,"d_date":"2025-01-01","t_mode":"Buy"}],
                 "suboperation":[{"id":1,"rd_operation_id":1,"f_value":"1"}],"unit":[%s]}
                """
                        .formatted(units);
        assertThatThrownBy(() -> parse(json))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("Cyclic unit");
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "{\"id\":2,\"rd_category_id\":2}",
                "{\"id\":2,\"rd_category_id\":3},{\"id\":3,\"rd_category_id\":2}"
            })
    void rejectsCategoryCyclesBeforeSortingTheirDepths(String categories) {
        String json =
                """
                {"account":[],"operation":[],"suboperation":[],"category":[%s]}
                """
                        .formatted(categories);
        assertThatThrownBy(() -> parse(json))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("Cyclic category");
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "{\"id\":1,\"t_type\":\"1\",\"t_symbol\":\"€\",\"rd_unit_id\":1}",
                "{\"id\":2,\"t_type\":\"S\",\"rd_unit_id\":999}",
                "{\"id\":2,\"t_type\":\"S\",\"rd_unit_id\":3},{\"id\":3,\"t_type\":\"1\",\"t_symbol\":\"€\"},{\"id\":4,\"t_type\":\"S\",\"rd_unit_id\":3}"
            })
    void acceptsTerminalReferenceCurrenciesAndSharedOrAbsentParents(String units)
            throws IOException {
        String json =
                """
                {"account":[],"operation":[],"suboperation":[],"unit":[%s]}
                """
                        .formatted(units);
        assertThat(parse(json).getTransactions()).isEmpty();
    }
}
