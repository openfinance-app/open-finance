package org.openfinance.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class TransactionRuleValidationIntegrationTest extends AuditApiTestSupport {
    @Test
    void incompatibleCreateAndUpdateLeaveRulesAndHistoryUnchanged() throws Exception {
        Map<String, Object> valid = rule("EQUALS");
        JsonNode saved = json("POST", "/transaction-rules", valid, owner, 201);
        long id = saved.path("id").asLong();
        JsonNode historyBefore =
                json("GET", "/history?entityType=TRANSACTION_RULE", null, owner, 200);

        json("POST", "/transaction-rules", rule("CONTAINS"), owner, 400);
        json("PUT", "/transaction-rules/" + id, rule("NOT_CONTAINS"), owner, 400);

        JsonNode after = json("GET", "/transaction-rules/" + id, null, owner, 200);
        assertThat(after).isEqualTo(saved);
        assertThat(json("GET", "/transaction-rules", null, owner, 200).size()).isEqualTo(1);
        assertThat(json("GET", "/history?entityType=TRANSACTION_RULE", null, owner, 200))
                .isEqualTo(historyBefore);
    }

    private Map<String, Object> rule(String operator) {
        return data(
                "name",
                "Twenty euro purchases",
                "priority",
                0,
                "isEnabled",
                true,
                "conditionMatch",
                "AND",
                "conditions",
                List.of(data("field", "AMOUNT", "operator", operator, "value", "20")),
                "actions",
                List.of(data("actionType", "SET_DESCRIPTION", "actionValue", "Reviewed")));
    }
}
