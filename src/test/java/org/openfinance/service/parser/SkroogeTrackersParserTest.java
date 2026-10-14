package org.openfinance.service.parser;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.openfinance.dto.ImportedTransaction;

class SkroogeTrackersParserTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final SkroogeJsonParser parser = new SkroogeJsonParser(objectMapper);

    @Test
    @DisplayName("Import tracker names from standard, split and both transfer operations")
    void importsTrackersAsTags() throws Exception {
        Map<String, ImportedTransaction> transactions = parse(sample());

        assertThat(transactions).hasSize(5);
        assertThat(transactions.get("skrooge:operation:10").getTags())
                .containsExactly("Summer holiday");
        assertThat(transactions.get("skrooge:operation:20").getTags()).isEmpty();
        assertThat(transactions.get("skrooge:operation:20").getSplits())
                .extracting(ImportedTransaction.SplitEntry::getTags)
                .containsExactly(
                        java.util.List.of("Summer holiday"),
                        java.util.List.of("Work trip"),
                        java.util.List.of("Summer holiday"),
                        java.util.List.of(),
                        java.util.List.of());
        assertThat(transactions.get("skrooge:operation:30").getTags())
                .containsExactly("Summer holiday");
        assertThat(transactions.get("skrooge:operation:40").getTags()).containsExactly("Work trip");
        assertThat(transactions.get("skrooge:operation:30").isTransfer()).isTrue();
        assertThat(transactions.get("skrooge:operation:40").isTransfer()).isTrue();
        // A tracker alone must keep an otherwise unlabelled operation from being discarded.
        assertThat(transactions.get("skrooge:operation:50").getTags()).containsExactly("Work trip");
    }

    @Test
    @DisplayName("Missing tracker definitions do not invent tags or reject transactions")
    void toleratesMissingTrackers() throws Exception {
        ObjectNode root = (ObjectNode) objectMapper.readTree(sample());
        root.remove("refund");

        assertThat(parse(root.toString()).values())
                .allSatisfy(
                        transaction -> {
                            assertThat(transaction.getTags()).isEmpty();
                            assertThat(transaction.getSplits())
                                    .allSatisfy(split -> assertThat(split.getTags()).isEmpty());
                        });
    }

    private Map<String, ImportedTransaction> parse(String json) throws Exception {
        return parser
                .parseFile(
                        new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8)),
                        "trackers.json")
                .getTransactions()
                .stream()
                .collect(Collectors.toMap(ImportedTransaction::getReferenceNumber, tx -> tx));
    }

    private String sample() {
        return """
                {
                  "account": [
                    {"id":1,"t_name":"Checking","t_type":"C"},
                    {"id":2,"t_name":"Savings","t_type":"S"}
                  ],
                  "unit": [{"id":1,"t_symbol":"EUR","t_type":"1"}],
                  "refund": [
                    {"id":7,"t_name":"Summer holiday"},
                    {"id":8,"t_name":" Work trip ","t_close":true},
                    {"id":9,"t_name":" "}
                  ],
                  "operation": [
                    {"id":10,"rd_account_id":1,"rc_unit_id":1,"d_date":"2026-01-10","t_comment":"Groceries"},
                    {"id":20,"rd_account_id":1,"rc_unit_id":1,"d_date":"2026-01-11","t_comment":"Split"},
                    {"id":30,"rd_account_id":1,"rc_unit_id":1,"d_date":"2026-01-12","i_group_id":99},
                    {"id":40,"rd_account_id":2,"rc_unit_id":1,"d_date":"2026-01-12","i_group_id":99},
                    {"id":50,"rd_account_id":1,"rc_unit_id":1,"d_date":"2026-01-13"}
                  ],
                  "suboperation": [
                    {"id":1,"rd_operation_id":10,"f_value":-10,"r_refund_id":7},
                    {"id":2,"rd_operation_id":20,"f_value":-10,"r_refund_id":7},
                    {"id":3,"rd_operation_id":20,"f_value":-10,"r_refund_id":8},
                    {"id":4,"rd_operation_id":20,"f_value":-10,"r_refund_id":7},
                    {"id":5,"rd_operation_id":20,"f_value":-10,"r_refund_id":9},
                    {"id":6,"rd_operation_id":20,"f_value":-10,"r_refund_id":999},
                    {"id":7,"rd_operation_id":30,"f_value":-20,"r_refund_id":7},
                    {"id":8,"rd_operation_id":40,"f_value":20,"r_refund_id":8},
                    {"id":9,"rd_operation_id":50,"f_value":-10,"r_refund_id":8}
                  ]
                }
                """;
    }
}
