package org.openfinance.service.history;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.openfinance.dto.HistoryRecordChange;
import org.openfinance.dto.HistoryRecordChange.FieldChange;
import org.openfinance.entity.OperationHistory;
import org.openfinance.entity.OperationType;
import org.openfinance.service.history.HistoryChangeSet.Change;
import org.openfinance.service.history.HistoryChangeSet.Key;
import org.springframework.stereotype.Component;

/** Presents captured values without exposing restoration payloads or technical columns. */
@Component
@RequiredArgsConstructor
public class HistoryChangePresenter {
    private final HistoryStateStore states;
    private final ObjectMapper mapper;

    private static final List<String> FIELDS =
            List.of(
                    "name",
                    "description",
                    "notes",
                    "file_name",
                    "symbol",
                    "isin",
                    "serial_number",
                    "brand",
                    "model",
                    "condition",
                    "warranty_expiration",
                    "useful_life_years",
                    "address",
                    "latitude",
                    "longitude",
                    "account_number",
                    "currency",
                    "from_currency",
                    "to_currency",
                    "account_currency",
                    "original_currency",
                    "type",
                    "account_type",
                    "transaction_type",
                    "asset_type",
                    "liability_type",
                    "property_type",
                    "movement_type",
                    "acquisition_type",
                    "amount",
                    "account_amount",
                    "original_amount",
                    "conversion_rate",
                    "balance",
                    "opening_balance",
                    "quantity",
                    "current_price",
                    "purchase_price",
                    "current_value",
                    "recorded_value",
                    "received_value",
                    "rental_income",
                    "current_balance",
                    "principal",
                    "opening_principal",
                    "principal_amount",
                    "credit_limit",
                    "minimum_payment",
                    "insurance_percentage",
                    "additional_fees",
                    "fee",
                    "drawn_amount",
                    "principal_repaid",
                    "planned_amount",
                    "allocated_amount",
                    "allocation_percentage",
                    "investment_quantity",
                    "investment_value",
                    "investment_price",
                    "investment_cash",
                    "sale_fees",
                    "valuation_change",
                    "import_opening_quantity",
                    "import_opening_cost",
                    "payment_method",
                    "external_reference",
                    "relationship",
                    "transaction_date",
                    "opening_date",
                    "purchase_date",
                    "sold_date",
                    "deleted_date",
                    "start_date",
                    "end_date",
                    "valid_from",
                    "effective_date",
                    "planned_date",
                    "drawn_date",
                    "reversed_date",
                    "investment_price_date",
                    "interest_only_until",
                    "tranche_no",
                    "anchor_day",
                    "frequency",
                    "period",
                    "next_occurrence",
                    "interest_rate",
                    "rate",
                    "tax_rate",
                    "interest_period",
                    "credit_value_date_mode",
                    "debit_value_date_mode",
                    "day_count_basis",
                    "is_interest_enabled",
                    "is_active",
                    "active",
                    "is_enabled",
                    "is_reconciled",
                    "is_deleted",
                    "is_adjustment",
                    "direct_disbursement",
                    "interest_only",
                    "status",
                    "priority",
                    "rollover",
                    "recurring",
                    "rollover_overspending",
                    "payee",
                    "tags",
                    "threshold",
                    "threshold_percentage",
                    "alert_type",
                    "field",
                    "operator",
                    "value",
                    "action_type",
                    "action_value",
                    "action_value2",
                    "action_value3",
                    "condition_type",
                    "condition_match",
                    "sort_order",
                    "icon",
                    "color",
                    "mcc_code",
                    "file_type",
                    "file_size",
                    "file_format",
                    "total_transactions",
                    "imported_count",
                    "error_count",
                    "duplicate_count",
                    "skipped_count",
                    "account_id",
                    "to_account_id",
                    "category_id",
                    "payee_id",
                    "asset_id",
                    "liability_id",
                    "represented_by_account_id",
                    "mortgage_id",
                    "tranche_id",
                    "source_tranche_id",
                    "property_id",
                    "real_estate_id",
                    "budget_id",
                    "rule_id",
                    "institution_id",
                    "parent_id",
                    "transaction_id");
    private static final Set<String> FLAGS =
            Set.of(
                    "is_active",
                    "active",
                    "is_enabled",
                    "is_reconciled",
                    "is_deleted",
                    "is_interest_enabled",
                    "rollover",
                    "recurring",
                    "rollover_overspending",
                    "investment_cash",
                    "is_adjustment",
                    "direct_disbursement",
                    "interest_only");
    private static final List<String> CONTEXT =
            List.of(
                    "name",
                    "description",
                    "file_name",
                    "symbol",
                    "account_id",
                    "category_id",
                    "liability_id",
                    "property_id",
                    "transaction_id",
                    "budget_id",
                    "rule_id",
                    "tranche_no",
                    "action_type",
                    "field",
                    "operator",
                    "currency",
                    "amount",
                    "transaction_date",
                    "valid_from",
                    "start_date",
                    "end_date");

    public List<HistoryRecordChange> present(OperationHistory entry, HistoryChangeSet state) {
        if (state == null) return legacy(entry);
        Values values = new Values(entry.getUserId(), state);
        List<HistoryRecordChange> result = new ArrayList<>();
        for (Change change : state.changes()) {
            HistoryRecordChange record = present(entry, change, values);
            if (record != null) result.add(record);
        }
        return result;
    }

    private HistoryRecordChange present(OperationHistory entry, Change change, Values values) {
        List<FieldChange> fields = fields(change, values);
        if (fields.isEmpty()) return null;
        Map<String, String> row = change.after() == null ? change.before() : change.after();
        Map<String, Object> context = new LinkedHashMap<>();
        for (String field : CONTEXT) {
            if (row.get(field) != null) {
                context.put(field, values.present(field, row.get(field), change.after() != null));
            }
        }
        String label = label(row);
        if (label == null
                && change.id().equals(String.valueOf(entry.getEntityId()))
                && change.table()
                        .equals(HistoryDomainRegistry.ENTITIES.get(entry.getEntityType()))) {
            label = entry.getEntityLabel();
        }
        return new HistoryRecordChange(
                change.table(),
                change.id(),
                label,
                change.before() == null
                        ? OperationType.CREATE
                        : change.after() == null ? OperationType.DELETE : OperationType.UPDATE,
                context,
                fields);
    }

    private List<FieldChange> fields(Change change, Values values) {
        Map<String, String> before = change.before() == null ? Map.of() : change.before();
        Map<String, String> after = change.after() == null ? Map.of() : change.after();
        List<FieldChange> fields = new ArrayList<>();
        for (String field : FIELDS) {
            if (!Objects.equals(before.get(field), after.get(field))) {
                fields.add(
                        new FieldChange(
                                field,
                                values.present(field, before.get(field), false),
                                values.present(field, after.get(field), true)));
            }
        }
        return fields;
    }

    /**
     * Index captured references and cache owner-scoped lookups once per request, including imports.
     */
    private class Values {
        private final Long userId;
        private final Map<Key, Change> captured = new HashMap<>();
        private final Map<Key, String> labels = new HashMap<>();

        Values(Long userId, HistoryChangeSet state) {
            this.userId = userId;
            state.changes().forEach(change -> captured.put(change.key(), change));
        }

        Object present(String field, String raw, boolean after) {
            if (raw == null) return null;
            if (FLAGS.contains(field)) return raw.equals("1") || Boolean.parseBoolean(raw);
            String table = HistoryDomainRegistry.REFERENCES.get(field);
            if (table == null || !raw.matches("[0-9]+")) return raw;
            Key key = new Key(table, raw);
            Change change = captured.get(key);
            if (change != null) {
                Map<String, String> row = after ? change.after() : change.before();
                if (row == null) row = after ? change.before() : change.after();
                if (row != null && label(row) != null) return label(row);
            }
            return labels.computeIfAbsent(
                    key,
                    ignored -> {
                        String label = states.label(table, Long.valueOf(raw), userId);
                        return label == null ? "#" + raw : label;
                    });
        }
    }

    private String label(Map<String, String> row) {
        for (String key : List.of("name", "description", "file_name", "symbol")) {
            if (row.get(key) != null && !row.get(key).isBlank()) return row.get(key);
        }
        return null;
    }

    private List<HistoryRecordChange> legacy(OperationHistory entry) {
        List<FieldChange> fields = new ArrayList<>();
        if (entry.getChangedFieldsJson() != null) {
            try {
                JsonNode diff = mapper.readTree(entry.getChangedFieldsJson());
                diff.fields()
                        .forEachRemaining(
                                field -> {
                                    JsonNode value = field.getValue();
                                    if (value.isObject()
                                            && (value.has("before") || value.has("after"))) {
                                        fields.add(
                                                new FieldChange(
                                                        field.getKey(),
                                                        mapper.convertValue(
                                                                value.get("before"), Object.class),
                                                        mapper.convertValue(
                                                                value.get("after"), Object.class)));
                                    }
                                });
            } catch (com.fasterxml.jackson.core.JsonProcessingException ignored) {
                // Older entries may have no readable diff; identity is still useful.
            }
        }
        return List.of(
                new HistoryRecordChange(
                        HistoryDomainRegistry.ENTITIES.get(entry.getEntityType()),
                        String.valueOf(entry.getEntityId()),
                        entry.getEntityLabel(),
                        entry.getOperationType(),
                        Map.of(),
                        fields));
    }
}
