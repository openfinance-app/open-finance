package org.openfinance.service.history;

import java.util.List;
import java.util.Map;
import org.openfinance.entity.EntityType;

/** Explicit financial-domain boundary and parent-first restoration order. */
public final class HistoryDomainRegistry {
    private HistoryDomainRegistry() {}

    public static final List<String> TABLES =
            List.of(
                    "institutions",
                    "categories",
                    "accounts",
                    "account_currency_changes",
                    "account_status_history",
                    "assets",
                    "liabilities",
                    "real_estate_properties",
                    "liability_tranches",
                    "payees",
                    "budgets",
                    "transactions",
                    "transactions_archive",
                    "transaction_splits",
                    "liability_principal_allocations",
                    "liability_asset_links",
                    "recurring_transactions",
                    "interest_rate_variations",
                    "real_estate_value_history",
                    "budget_alerts",
                    "transaction_rules",
                    "transaction_rule_conditions",
                    "transaction_rule_actions",
                    "attachments",
                    "import_sessions");
    public static final Map<String, String> CHILDREN =
            Map.of(
                    "transaction_splits",
                    "transaction_id:transactions",
                    "interest_rate_variations",
                    "account_id:accounts",
                    "budget_alerts",
                    "budget_id:budgets",
                    "transaction_rule_conditions",
                    "rule_id:transaction_rules",
                    "transaction_rule_actions",
                    "rule_id:transaction_rules");
    public static final Map<EntityType, String> ENTITIES =
            Map.ofEntries(
                    Map.entry(EntityType.ACCOUNT, "accounts"),
                    Map.entry(EntityType.TRANSACTION, "transactions"),
                    Map.entry(EntityType.ASSET, "assets"),
                    Map.entry(EntityType.LIABILITY, "liabilities"),
                    Map.entry(EntityType.REAL_ESTATE, "real_estate_properties"),
                    Map.entry(EntityType.BUDGET, "budgets"),
                    Map.entry(EntityType.CATEGORY, "categories"),
                    Map.entry(EntityType.PAYEE, "payees"),
                    Map.entry(EntityType.RECURRING_TRANSACTION, "recurring_transactions"),
                    Map.entry(EntityType.TRANSACTION_RULE, "transaction_rules"),
                    Map.entry(EntityType.IMPORT, "import_sessions"));
    public static final Map<String, String> REFERENCES =
            Map.ofEntries(
                    Map.entry("user_id", "users"), Map.entry("account_id", "accounts"),
                    Map.entry("represented_by_account_id", "accounts"),
                            Map.entry("to_account_id", "accounts"),
                    Map.entry("institution_id", "institutions"),
                            Map.entry("currency_id", "currencies"),
                    Map.entry("category_id", "categories"),
                            Map.entry("default_category_id", "categories"),
                    Map.entry("parent_id", "categories"), Map.entry("asset_id", "assets"),
                    Map.entry("liability_id", "liabilities"),
                            Map.entry("mortgage_id", "liabilities"),
                    Map.entry("tranche_id", "liability_tranches"),
                            Map.entry("source_tranche_id", "liability_tranches"),
                    Map.entry("real_estate_id", "real_estate_properties"),
                            Map.entry("property_id", "real_estate_properties"),
                    Map.entry("budget_id", "budgets"), Map.entry("transaction_id", "transactions"),
                    Map.entry("rule_id", "transaction_rules"), Map.entry("payee_id", "payees"));

    public static String owned(String table) {
        String child = CHILDREN.get(table);
        if (child == null) return "user_id = ?";
        String[] parts = child.split(":");
        return parts[0] + " IN (SELECT id FROM " + parts[1] + " WHERE user_id = ?)";
    }
}
