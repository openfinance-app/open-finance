package org.openfinance.service.parser;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.Currency;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.openfinance.dto.ImportedTransaction;
import org.openfinance.dto.SkroogeImportMetadata;
import org.openfinance.dto.SkroogeImportParseResult;
import org.openfinance.entity.AccountType;
import org.openfinance.entity.CategoryType;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class SkroogeJsonParser {

    private static final String SYNTHETIC_DATE = "0000-00-00";

    /**
     * Skrooge unit types that identify account-transfer-capable units: "1" = primary currency, "2"
     * = secondary currency, "C" = cryptocurrency.
     */
    private static final Set<String> CURRENCY_UNIT_TYPES = Set.of("1", "2", "C");

    /**
     * Generic bank-statement payee labels (accent-normalised, upper-case) that carry no merchant
     * information — the operation comment is a better payee than these. Covers the common
     * French/English/German/Spanish labels found in Skrooge exports.
     */
    private static final Set<String> GENERIC_PAYEES =
            Set.of(
                    // French
                    "FACTURE CARTE",
                    "PRLV SEPA",
                    "VIR SEPA RECU",
                    "RETRAIT DAB",
                    "CARTE BANCAIRE",
                    // English
                    "CR",
                    "DEBIT",
                    "CREDIT",
                    "CARD PAYMENT",
                    "DIRECT DEBIT",
                    "SEPA DIRECT DEBIT",
                    "BANK TRANSFER",
                    "WITHDRAWAL",
                    // German
                    "LASTSCHRIFT",
                    "DAUERAUFTRAG",
                    "GELDAUTOMAT",
                    // Spanish
                    "ADEUDO",
                    "TRANSFERENCIA");

    /**
     * Accent-normalised substrings identifying a transfer category, across the languages Skrooge is
     * commonly used in (fr/en/de/es/it/pt). This is only a <em>soft</em> signal: transfers are
     * detected structurally (a balanced two-operation group on currency units moving between two
     * distinct accounts), so uncategorised or differently-named transfers are still recognised. The
     * keyword additionally rescues the rare same-account group Skrooge labelled as a transfer.
     */
    private static final List<String> TRANSFER_CATEGORY_KEYWORDS =
            List.of(
                    "transfer", // English (French "transfert" matches as prefix)
                    "virement", // French
                    "uberweisung", // German (normalised from "Überweisung")
                    "transferencia", // Spanish / Portuguese
                    "trasferimento"); // Italian

    /**
     * Accent-normalised substrings suggesting an income category when no signed total is available
     * to infer the type from.
     */
    private static final List<String> INCOME_CATEGORY_KEYWORDS =
            List.of(
                    "revenu", // French "revenu(s)"
                    "salaire", // French
                    "inter", // "intérêts" (fr) and "interest" (en) — shared prefix
                    "cadeaux recus", // French "cadeaux reçus" (normalised)
                    "revente",
                    "plus-values",
                    "income",
                    "salary",
                    "dividend",
                    "gift",
                    "refund",
                    "capital gain");

    private final ObjectMapper objectMapper;

    public SkroogeImportParseResult parseFile(InputStream inputStream, String fileName)
            throws IOException {
        JsonNode root = objectMapper.readTree(inputStream);

        if (!root.has("account") || !root.has("operation") || !root.has("suboperation")) {
            throw new IOException("Invalid Skrooge JSON export: missing required collections");
        }

        Map<Long, JsonNode> banksById = indexById(root.path("bank"));
        Map<Long, JsonNode> accountsById = indexById(root.path("account"));
        Map<Long, JsonNode> categoriesById = indexById(root.path("category"));
        Map<Long, JsonNode> payeesById = indexById(root.path("payee"));
        Map<Long, JsonNode> unitsById = indexById(root.path("unit"));
        Map<Long, TreeMap<LocalDate, BigDecimal>> unitValuesByUnitId =
                indexUnitValues(root.path("unitvalue"));
        Map<Long, BigDecimal> accountBalanceDeltasByOperationId =
                deriveAccountBalanceDeltas(root.path("operation"), root.path("operationbalance"));
        Map<Long, List<JsonNode>> subOperationsByOperationId =
                groupByLong(root.path("suboperation"), "rd_operation_id");
        Map<Long, List<JsonNode>> operationsByGroupId =
                groupByPositiveLong(root.path("operation"), "i_group_id");

        Set<Long> transferOperationIds =
                detectTransferOperationIds(
                        operationsByGroupId, subOperationsByOperationId, categoriesById, unitsById);
        Set<Long> referencedAccountIds =
                collectReferencedAccountIds(
                        root.path("operation"), transferOperationIds, subOperationsByOperationId);
        Set<Long> referencedCategoryIds =
                collectReferencedCategoryIds(
                        subOperationsByOperationId, transferOperationIds, categoriesById);

        // Each account keeps its native Skrooge currency (e.g. XOF); transaction and opening
        // amounts are converted into that currency using Skrooge's own stored unit rates (pegs),
        // never live exchange rates, so imported balances match Skrooge exactly.
        Map<Long, String> accountCurrencyBySourceId =
                buildAccountCurrencies(
                        accountsById,
                        unitsById,
                        root.path("operation"),
                        subOperationsByOperationId,
                        referencedAccountIds);
        Map<String, BigDecimal> currencyRatesToPrimary =
                buildCurrencyRatesToPrimary(unitsById, unitValuesByUnitId);

        SkroogeImportMetadata metadata =
                SkroogeImportMetadata.builder()
                        .institutions(
                                buildInstitutions(banksById, referencedAccountIds, accountsById))
                        .accounts(
                                buildAccounts(
                                        accountsById,
                                        unitsById,
                                        unitValuesByUnitId,
                                        root.path("operation"),
                                        subOperationsByOperationId,
                                        referencedAccountIds,
                                        accountCurrencyBySourceId,
                                        currencyRatesToPrimary))
                        .categories(
                                buildCategories(
                                        categoriesById,
                                        subOperationsByOperationId,
                                        referencedCategoryIds))
                        .build();

        List<ImportedTransaction> transactions = new ArrayList<>();
        Set<Long> processedTransferGroups = new HashSet<>();

        for (JsonNode operation : root.path("operation")) {
            Long operationId = longValue(operation, "id");
            if (operationId == null) {
                continue;
            }

            Long groupId = positiveLongValue(operation, "i_group_id");
            if (groupId != null && transferOperationIds.contains(operationId)) {
                if (processedTransferGroups.add(groupId)) {
                    List<JsonNode> groupedOperations =
                            operationsByGroupId.getOrDefault(groupId, List.of());
                    transactions.addAll(
                            buildTransferTransactions(
                                    groupId,
                                    groupedOperations,
                                    subOperationsByOperationId,
                                    accountsById,
                                    categoriesById,
                                    payeesById,
                                    unitsById,
                                    unitValuesByUnitId,
                                    accountBalanceDeltasByOperationId,
                                    accountCurrencyBySourceId,
                                    currencyRatesToPrimary,
                                    fileName));
                }
                continue;
            }

            ImportedTransaction transaction =
                    buildStandardTransaction(
                            operation,
                            subOperationsByOperationId,
                            accountsById,
                            categoriesById,
                            payeesById,
                            unitsById,
                            unitValuesByUnitId,
                            accountBalanceDeltasByOperationId,
                            accountCurrencyBySourceId,
                            currencyRatesToPrimary,
                            fileName);
            if (transaction != null) {
                transactions.add(transaction);
            }
        }

        // Default to the document's own primary/reference currency (the type "1" unit) rather
        // than a hardcoded code — the file format declares its reference unit.
        String defaultCurrency =
                metadata.getAccounts().stream()
                        .map(SkroogeImportMetadata.SkroogeAccount::getCurrency)
                        .filter(Objects::nonNull)
                        .findFirst()
                        .orElseGet(() -> resolvePrimaryCurrencyCode(unitsById));

        log.info("Parsed {} Skrooge JSON transactions from {}", transactions.size(), fileName);
        return SkroogeImportParseResult.builder()
                .transactions(transactions)
                .skroogeMetadata(metadata)
                .currency(defaultCurrency)
                .build();
    }

    private Map<Long, JsonNode> indexById(JsonNode arrayNode) {
        Map<Long, JsonNode> results = new HashMap<>();
        for (JsonNode node : arrayNode) {
            Long id = longValue(node, "id");
            if (id != null) {
                results.put(id, node);
            }
        }
        return results;
    }

    private Map<Long, List<JsonNode>> groupByLong(JsonNode arrayNode, String fieldName) {
        Map<Long, List<JsonNode>> results = new HashMap<>();
        for (JsonNode node : arrayNode) {
            Long id = longValue(node, fieldName);
            if (id != null) {
                results.computeIfAbsent(id, ignored -> new ArrayList<>()).add(node);
            }
        }
        return results;
    }

    private Map<Long, List<JsonNode>> groupByPositiveLong(JsonNode arrayNode, String fieldName) {
        Map<Long, List<JsonNode>> results = new HashMap<>();
        for (JsonNode node : arrayNode) {
            Long id = positiveLongValue(node, fieldName);
            if (id != null) {
                results.computeIfAbsent(id, ignored -> new ArrayList<>()).add(node);
            }
        }
        return results;
    }

    private Map<Long, JsonNode> indexOperationBalances(JsonNode operationBalancesNode) {
        Map<Long, JsonNode> results = new HashMap<>();
        for (JsonNode node : operationBalancesNode) {
            Long operationId = longValue(node, "r_operation_id");
            if (operationId != null) {
                results.put(operationId, node);
            }
        }
        return results;
    }

    private Map<Long, BigDecimal> deriveAccountBalanceDeltas(
            JsonNode operationsNode, JsonNode operationBalancesNode) {
        Map<Long, JsonNode> balancesByOperationId = indexOperationBalances(operationBalancesNode);
        if (balancesByOperationId.isEmpty()) {
            return Map.of();
        }

        Map<Long, BigDecimal> deltasByOperationId = new HashMap<>();
        Map<Long, List<JsonNode>> operationsByAccountId =
                groupByLong(operationsNode, "rd_account_id");
        for (List<JsonNode> accountOperations : operationsByAccountId.values()) {
            List<JsonNode> sortedOperations =
                    accountOperations.stream()
                            .filter(
                                    operation -> {
                                        Long operationId = longValue(operation, "id");
                                        return operationId != null
                                                && balancesByOperationId.containsKey(operationId);
                                    })
                            .sorted(
                                    Comparator.comparing(
                                                    (JsonNode operation) ->
                                                            textValue(operation, "d_date"))
                                            .thenComparingLong(this::operationSortId))
                            .toList();

            BigDecimal previousBalance = BigDecimal.ZERO;
            for (JsonNode operation : sortedOperations) {
                Long operationId = longValue(operation, "id");
                BigDecimal currentBalance =
                        operationBalanceValue(balancesByOperationId.get(operationId));
                if (operationId == null || currentBalance == null) {
                    continue;
                }
                deltasByOperationId.put(operationId, currentBalance.subtract(previousBalance));
                previousBalance = currentBalance;
            }
        }
        return deltasByOperationId;
    }

    private long operationSortId(JsonNode operation) {
        Long operationId = longValue(operation, "id");
        return operationId != null ? operationId : Long.MAX_VALUE;
    }

    private BigDecimal operationBalanceValue(JsonNode balanceNode) {
        if (balanceNode == null || balanceNode.isMissingNode()) {
            return null;
        }
        if (!textValue(balanceNode, "f_balance_entered").isBlank()) {
            return decimalValue(balanceNode, "f_balance_entered");
        }
        return null;
    }

    private Set<Long> detectTransferOperationIds(
            Map<Long, List<JsonNode>> operationsByGroupId,
            Map<Long, List<JsonNode>> subOperationsByOperationId,
            Map<Long, JsonNode> categoriesById,
            Map<Long, JsonNode> unitsById) {
        Set<Long> transferOperationIds = new HashSet<>();
        for (Map.Entry<Long, List<JsonNode>> entry : operationsByGroupId.entrySet()) {
            if (isTransferGroup(
                    entry.getValue(), subOperationsByOperationId, categoriesById, unitsById)) {
                for (JsonNode operation : entry.getValue()) {
                    Long operationId = longValue(operation, "id");
                    if (operationId != null) {
                        transferOperationIds.add(operationId);
                    }
                }
            }
        }
        return transferOperationIds;
    }

    private boolean isTransferGroup(
            List<JsonNode> operations,
            Map<Long, List<JsonNode>> subOperationsByOperationId,
            Map<Long, JsonNode> categoriesById,
            Map<Long, JsonNode> unitsById) {
        if (operations.size() != 2) {
            return false;
        }

        BigDecimal signedTotal = BigDecimal.ZERO;
        boolean sameUnit = true;
        boolean anyTransferCategory = false;
        Long firstUnitId = null;
        BigDecimal firstAmount = null;
        BigDecimal secondAmount = null;
        for (JsonNode operation : operations) {
            Long operationId = longValue(operation, "id");
            List<JsonNode> subOperations =
                    operationId != null
                            ? subOperationsByOperationId.getOrDefault(operationId, List.of())
                            : List.of();
            if (subOperations.size() != 1) {
                return false;
            }
            JsonNode subOperation = subOperations.get(0);
            JsonNode category = categoriesById.get(longValue(subOperation, "r_category_id"));
            anyTransferCategory = anyTransferCategory || isTransferCategory(category);
            BigDecimal amount = decimalValue(subOperation, "f_value");
            signedTotal = signedTotal.add(amount);
            if (firstAmount == null) {
                firstAmount = amount;
            } else {
                secondAmount = amount;
            }
            Long unitId = longValue(operation, "rc_unit_id");
            if (!isAccountTransferUnit(unitsById.get(unitId))) {
                return false;
            }
            if (firstUnitId == null) {
                firstUnitId = unitId;
            } else if (!java.util.Objects.equals(unitId, firstUnitId)) {
                sameUnit = false;
            }
        }

        // Structural balance check: same-unit transfers net to zero; cross-currency transfers pair
        // a debit with a credit. Share/object groups never reach here — their non-currency unit is
        // already rejected by the isAccountTransferUnit guard above, so they stay source operations
        // and the imported rows preserve Skrooge's own per-side amounts.
        boolean balanced;
        if (!sameUnit) {
            balanced =
                    firstAmount != null
                            && secondAmount != null
                            && firstAmount.signum() * secondAmount.signum() < 0;
        } else {
            balanced = signedTotal.compareTo(BigDecimal.ZERO) == 0;
        }
        if (!balanced) {
            return false;
        }

        // A balanced movement between two DISTINCT accounts is the language-independent signature
        // of an account transfer, so structure alone classifies it — no category name required.
        // This catches transfers that are uncategorised or whose category is named outside
        // TRANSFER_CATEGORY_KEYWORDS (any language, or labels like "Between accounts"). The keyword
        // is retained only as a soft signal that additionally rescues the rare same-account pair
        // Skrooge itself labelled as a transfer, so no previously-detected transfer is lost.
        Long firstAccountId = longValue(operations.get(0), "rd_account_id");
        Long secondAccountId = longValue(operations.get(1), "rd_account_id");
        boolean crossAccount =
                firstAccountId != null
                        && secondAccountId != null
                        && !firstAccountId.equals(secondAccountId);
        return crossAccount || anyTransferCategory;
    }

    private boolean isAccountTransferUnit(JsonNode unitNode) {
        if (unitNode == null || unitNode.isMissingNode()) {
            return false;
        }
        String unitType = textValue(unitNode, "t_type");
        if (CURRENCY_UNIT_TYPES.contains(unitType)) {
            return true;
        }
        return isDirectCurrencyUnit(unitNode);
    }

    private boolean isTransferCategory(JsonNode categoryNode) {
        if (categoryNode == null || categoryNode.isMissingNode()) {
            return false;
        }
        String fullName = normalizeKeyword(textValue(categoryNode, "t_fullname"));
        String name = normalizeKeyword(textValue(categoryNode, "t_name"));
        return containsAny(fullName, TRANSFER_CATEGORY_KEYWORDS)
                || containsAny(name, TRANSFER_CATEGORY_KEYWORDS);
    }

    /** Lower-case, accent-stripped form used for language-insensitive keyword matching. */
    private String normalizeKeyword(String text) {
        return ImportParseSupport.stripAccents(text).toLowerCase(Locale.ROOT);
    }

    private Set<Long> collectReferencedAccountIds(
            JsonNode operationsNode,
            Set<Long> transferOperationIds,
            Map<Long, List<JsonNode>> subOperationsByOperationId) {
        Set<Long> referencedAccountIds = new HashSet<>();
        for (JsonNode operation : operationsNode) {
            Long operationId = longValue(operation, "id");
            if (operationId == null) {
                continue;
            }
            if (isSyntheticBalanceOperation(
                    operation, subOperationsByOperationId.getOrDefault(operationId, List.of()))) {
                // Still reference the account for synthetic balance operations so that
                // accounts whose only operation is an opening balance are not skipped.
                Long accountId = longValue(operation, "rd_account_id");
                if (accountId != null) {
                    referencedAccountIds.add(accountId);
                }
                continue;
            }
            if (!transferOperationIds.contains(operationId)
                    && !hasValidDate(operation)
                    && subOperationsByOperationId.getOrDefault(operationId, List.of()).isEmpty()) {
                continue;
            }
            Long accountId = longValue(operation, "rd_account_id");
            if (accountId != null) {
                referencedAccountIds.add(accountId);
            }
        }
        return referencedAccountIds;
    }

    private Set<Long> collectReferencedCategoryIds(
            Map<Long, List<JsonNode>> subOperationsByOperationId,
            Set<Long> transferOperationIds,
            Map<Long, JsonNode> categoriesById) {
        Set<Long> referencedCategoryIds = new HashSet<>();
        for (Map.Entry<Long, List<JsonNode>> entry : subOperationsByOperationId.entrySet()) {
            Long operationId = entry.getKey();
            for (JsonNode subOperation : entry.getValue()) {
                Long categoryId = longValue(subOperation, "r_category_id");
                if (categoryId == null || categoryId == 0L) {
                    continue;
                }
                if (transferOperationIds.contains(operationId)
                        && isTransferCategory(categoriesById.get(categoryId))) {
                    continue;
                }
                addCategoryWithParents(referencedCategoryIds, categoryId, categoriesById);
            }
        }
        return referencedCategoryIds;
    }

    private void addCategoryWithParents(
            Set<Long> categoryIds, Long categoryId, Map<Long, JsonNode> categoriesById) {
        Long currentId = categoryId;
        while (currentId != null && currentId != 0L && categoryIds.add(currentId)) {
            JsonNode current = categoriesById.get(currentId);
            currentId = current != null ? longValue(current, "rd_category_id") : null;
        }
    }

    private List<SkroogeImportMetadata.SkroogeInstitution> buildInstitutions(
            Map<Long, JsonNode> banksById,
            Set<Long> referencedAccountIds,
            Map<Long, JsonNode> accountsById) {
        Set<Long> referencedBankIds = new HashSet<>();
        for (Long accountId : referencedAccountIds) {
            JsonNode account = accountsById.get(accountId);
            Long bankId = account != null ? longValue(account, "rd_bank_id") : null;
            if (bankId != null && bankId != 0L) {
                referencedBankIds.add(bankId);
            }
        }

        List<SkroogeImportMetadata.SkroogeInstitution> institutions = new ArrayList<>();
        for (Long bankId : referencedBankIds) {
            JsonNode bank = banksById.get(bankId);
            if (bank == null) {
                continue;
            }
            institutions.add(
                    SkroogeImportMetadata.SkroogeInstitution.builder()
                            .sourceId(bankId)
                            .name(resolveInstitutionName(bank, bankId))
                            .logo(textValue(bank, "t_icon"))
                            .country(null)
                            .build());
        }
        institutions.sort(
                Comparator.comparing(
                        SkroogeImportMetadata.SkroogeInstitution::getName,
                        String.CASE_INSENSITIVE_ORDER));
        return institutions;
    }

    private List<SkroogeImportMetadata.SkroogeAccount> buildAccounts(
            Map<Long, JsonNode> accountsById,
            Map<Long, JsonNode> unitsById,
            Map<Long, TreeMap<LocalDate, BigDecimal>> unitValuesByUnitId,
            JsonNode operationsNode,
            Map<Long, List<JsonNode>> subOperationsByOperationId,
            Set<Long> referencedAccountIds,
            Map<Long, String> accountCurrencyBySourceId,
            Map<String, BigDecimal> currencyRatesToPrimary) {
        Map<Long, List<JsonNode>> operationsByAccountId =
                groupByLong(operationsNode, "rd_account_id");
        List<SkroogeImportMetadata.SkroogeAccount> accounts = new ArrayList<>();

        for (Long accountId : referencedAccountIds) {
            JsonNode account = accountsById.get(accountId);
            if (account == null) {
                continue;
            }
            List<JsonNode> accountOperations =
                    operationsByAccountId.getOrDefault(accountId, List.of());
            // The account keeps its native Skrooge currency (e.g. XOF), so OpenFinance can group
            // assets by currency and offer base/native display modes.
            String currency = accountCurrencyBySourceId.get(accountId);
            if (currency == null) {
                currency =
                        deriveAccountCurrency(
                                accountOperations, unitsById, subOperationsByOperationId);
            }
            BigDecimal openingBalance =
                    deriveOpeningBalance(
                            accountOperations,
                            subOperationsByOperationId,
                            unitsById,
                            unitValuesByUnitId,
                            currency,
                            currencyRatesToPrimary);
            LocalDate openingDate = deriveOpeningDate(accountOperations);

            accounts.add(
                    SkroogeImportMetadata.SkroogeAccount.builder()
                            .sourceId(accountId)
                            .sourceInstitutionId(longValue(account, "rd_bank_id"))
                            .name(textValue(account, "t_name"))
                            .accountNumber(resolveAccountNumber(account))
                            .currency(currency)
                            .accountType(mapAccountType(textValue(account, "t_type")))
                            .description(textValue(account, "t_comment"))
                            .openingBalance(openingBalance)
                            .openingDate(openingDate)
                            .active(!booleanValue(account, "t_close"))
                            .build());
        }

        accounts.sort(
                Comparator.comparing(
                        SkroogeImportMetadata.SkroogeAccount::getName,
                        String.CASE_INSENSITIVE_ORDER));
        return accounts;
    }

    private List<SkroogeImportMetadata.SkroogeCategory> buildCategories(
            Map<Long, JsonNode> categoriesById,
            Map<Long, List<JsonNode>> subOperationsByOperationId,
            Set<Long> referencedCategoryIds) {
        Map<Long, CategoryType> inferredTypes =
                inferCategoryTypes(categoriesById, subOperationsByOperationId);
        List<SkroogeImportMetadata.SkroogeCategory> categories =
                referencedCategoryIds.stream()
                        .map(categoriesById::get)
                        .filter(Objects::nonNull)
                        .map(
                                category ->
                                        SkroogeImportMetadata.SkroogeCategory.builder()
                                                .sourceId(longValue(category, "id"))
                                                .parentSourceId(
                                                        longValue(category, "rd_category_id"))
                                                .name(textValue(category, "t_name"))
                                                .fullName(textValue(category, "t_fullname"))
                                                .type(
                                                        inferredTypes.getOrDefault(
                                                                longValue(category, "id"),
                                                                CategoryType.EXPENSE))
                                                .build())
                        .sorted(
                                Comparator.comparing(
                                                (SkroogeImportMetadata.SkroogeCategory category) ->
                                                        depth(category, categoriesById))
                                        .thenComparing(
                                                SkroogeImportMetadata.SkroogeCategory::getFullName,
                                                String.CASE_INSENSITIVE_ORDER))
                        .collect(Collectors.toList());
        return categories;
    }

    private int depth(
            SkroogeImportMetadata.SkroogeCategory category, Map<Long, JsonNode> categoriesById) {
        int depth = 0;
        Long parentId = category.getParentSourceId();
        while (parentId != null && parentId != 0L) {
            depth++;
            JsonNode parent = categoriesById.get(parentId);
            parentId = parent != null ? longValue(parent, "rd_category_id") : null;
        }
        return depth;
    }

    private Map<Long, CategoryType> inferCategoryTypes(
            Map<Long, JsonNode> categoriesById,
            Map<Long, List<JsonNode>> subOperationsByOperationId) {
        Map<Long, BigDecimal> totals = new HashMap<>();
        for (List<JsonNode> subOperations : subOperationsByOperationId.values()) {
            for (JsonNode subOperation : subOperations) {
                Long categoryId = longValue(subOperation, "r_category_id");
                if (categoryId == null || categoryId == 0L) {
                    continue;
                }
                totals.merge(categoryId, decimalValue(subOperation, "f_value"), BigDecimal::add);
            }
        }

        Map<Long, CategoryType> types = new HashMap<>();
        for (Map.Entry<Long, JsonNode> entry : categoriesById.entrySet()) {
            Long categoryId = entry.getKey();
            BigDecimal total = totals.get(categoryId);
            if (total != null && total.compareTo(BigDecimal.ZERO) > 0) {
                types.put(categoryId, CategoryType.INCOME);
                continue;
            }
            if (total != null && total.compareTo(BigDecimal.ZERO) < 0) {
                types.put(categoryId, CategoryType.EXPENSE);
                continue;
            }

            String fullName = normalizeKeyword(textValue(entry.getValue(), "t_fullname"));
            if (containsAny(fullName, INCOME_CATEGORY_KEYWORDS)) {
                types.put(categoryId, CategoryType.INCOME);
            } else {
                types.put(categoryId, CategoryType.EXPENSE);
            }
        }
        return types;
    }

    private boolean containsAny(String text, Collection<String> fragments) {
        for (String fragment : fragments) {
            if (text.contains(fragment)) {
                return true;
            }
        }
        return false;
    }

    private List<ImportedTransaction> buildTransferTransactions(
            Long groupId,
            List<JsonNode> groupedOperations,
            Map<Long, List<JsonNode>> subOperationsByOperationId,
            Map<Long, JsonNode> accountsById,
            Map<Long, JsonNode> categoriesById,
            Map<Long, JsonNode> payeesById,
            Map<Long, JsonNode> unitsById,
            Map<Long, TreeMap<LocalDate, BigDecimal>> unitValuesByUnitId,
            Map<Long, BigDecimal> accountBalanceDeltasByOperationId,
            Map<Long, String> accountCurrencyBySourceId,
            Map<String, BigDecimal> currencyRatesToPrimary,
            String fileName) {
        if (groupedOperations.size() != 2) {
            return List.of();
        }

        JsonNode firstOperation = groupedOperations.get(0);
        JsonNode secondOperation = groupedOperations.get(1);
        JsonNode firstSubOperation =
                subOperationsByOperationId
                        .getOrDefault(longValue(firstOperation, "id"), List.of())
                        .get(0);
        JsonNode secondSubOperation =
                subOperationsByOperationId
                        .getOrDefault(longValue(secondOperation, "id"), List.of())
                        .get(0);

        // Fix: determine transfer direction by f_value sign, not by JSON operation order.
        // The operation with negative f_value is the source (sender), positive is the
        // destination (receiver). This prevents reversed transfers when the receiving
        // account's operation happens to appear first in the JSON.
        BigDecimal firstAmount = decimalValue(firstSubOperation, "f_value");
        BigDecimal secondAmount = decimalValue(secondSubOperation, "f_value");
        if (firstAmount.compareTo(BigDecimal.ZERO) > 0
                && secondAmount.compareTo(BigDecimal.ZERO) < 0) {
            // First operation is the receiver — swap so source is always first.
            JsonNode tempOp = firstOperation;
            firstOperation = secondOperation;
            secondOperation = tempOp;
            JsonNode tempSub = firstSubOperation;
            firstSubOperation = secondSubOperation;
            secondSubOperation = tempSub;
        }

        ImportedTransaction firstTransaction =
                buildTransferTransaction(
                        firstOperation,
                        secondOperation,
                        firstSubOperation,
                        payeesById,
                        unitsById,
                        unitValuesByUnitId,
                        accountsById,
                        categoriesById,
                        accountBalanceDeltasByOperationId,
                        accountCurrencyBySourceId,
                        currencyRatesToPrimary,
                        groupId,
                        fileName);
        ImportedTransaction secondTransaction =
                buildTransferTransaction(
                        secondOperation,
                        firstOperation,
                        secondSubOperation,
                        payeesById,
                        unitsById,
                        unitValuesByUnitId,
                        accountsById,
                        categoriesById,
                        accountBalanceDeltasByOperationId,
                        accountCurrencyBySourceId,
                        currencyRatesToPrimary,
                        groupId,
                        fileName);

        return List.of(firstTransaction, secondTransaction);
    }

    private ImportedTransaction buildTransferTransaction(
            JsonNode operation,
            JsonNode otherOperation,
            JsonNode subOperation,
            Map<Long, JsonNode> payeesById,
            Map<Long, JsonNode> unitsById,
            Map<Long, TreeMap<LocalDate, BigDecimal>> unitValuesByUnitId,
            Map<Long, JsonNode> accountsById,
            Map<Long, JsonNode> categoriesById,
            Map<Long, BigDecimal> accountBalanceDeltasByOperationId,
            Map<Long, String> accountCurrencyBySourceId,
            Map<String, BigDecimal> currencyRatesToPrimary,
            Long groupId,
            String fileName) {
        Long operationId = longValue(operation, "id");
        Long accountSourceId = longValue(operation, "rd_account_id");
        JsonNode account = accountsById.get(accountSourceId);
        JsonNode otherAccount = accountsById.get(longValue(otherOperation, "rd_account_id"));
        JsonNode payee = payeesById.get(longValue(operation, "r_payee_id"));
        JsonNode category = categoriesById.get(longValue(subOperation, "r_category_id"));

        BigDecimal signedAmount = decimalValue(subOperation, "f_value");
        Long unitId = longValue(operation, "rc_unit_id");
        LocalDate opDate = parseDate(operation);
        String accountCurrency =
                resolveAccountCurrency(
                        accountSourceId, unitId, accountCurrencyBySourceId, unitsById);
        signedAmount =
                convertToCurrency(
                        signedAmount,
                        unitId,
                        opDate,
                        accountCurrency,
                        unitsById,
                        unitValuesByUnitId,
                        currencyRatesToPrimary);
        String comment = textValue(operation, "t_comment");

        return ImportedTransaction.builder()
                .transactionDate(opDate)
                .payee(resolvePayee(payee, comment, textValue(operation, "t_mode")))
                .originalPayee(payee != null ? textValue(payee, "t_name") : null)
                .amount(signedAmount)
                .memo(comment)
                .category(category != null ? textValue(category, "t_fullname") : null)
                .sourceCategoryId(longValue(subOperation, "r_category_id"))
                .referenceNumber(skroogeOperationReference(operation))
                .accountName(account != null ? textValue(account, "t_name") : null)
                .sourceAccountId(accountSourceId)
                .accountNumber(account != null ? resolveAccountNumber(account) : null)
                .currency(accountCurrency)
                .sourceAccountBalanceDelta(accountBalanceDeltasByOperationId.get(operationId))
                .transfer(true)
                .transferGroupKey(skroogeTransferReference(groupId))
                .toAccountName(otherAccount != null ? textValue(otherAccount, "t_name") : null)
                .toAccountSourceId(longValue(otherOperation, "rd_account_id"))
                .clearedStatus(resolveClearedStatus(operation))
                .sourceFileName(fileName)
                .build();
    }

    private ImportedTransaction buildStandardTransaction(
            JsonNode operation,
            Map<Long, List<JsonNode>> subOperationsByOperationId,
            Map<Long, JsonNode> accountsById,
            Map<Long, JsonNode> categoriesById,
            Map<Long, JsonNode> payeesById,
            Map<Long, JsonNode> unitsById,
            Map<Long, TreeMap<LocalDate, BigDecimal>> unitValuesByUnitId,
            Map<Long, BigDecimal> accountBalanceDeltasByOperationId,
            Map<Long, String> accountCurrencyBySourceId,
            Map<String, BigDecimal> currencyRatesToPrimary,
            String fileName) {
        Long operationId = longValue(operation, "id");
        List<JsonNode> subOperations =
                subOperationsByOperationId.getOrDefault(operationId, List.of());

        if (!hasValidDate(operation)
                || subOperations.isEmpty()
                || isSyntheticBalanceOperation(operation, subOperations)) {
            return null;
        }

        Long accountSourceId = longValue(operation, "rd_account_id");
        JsonNode account = accountsById.get(accountSourceId);
        JsonNode payee = payeesById.get(longValue(operation, "r_payee_id"));
        String comment = textValue(operation, "t_comment");
        String payeeName = resolvePayee(payee, comment, textValue(operation, "t_mode"));
        Long unitId = longValue(operation, "rc_unit_id");
        LocalDate opDate = parseDate(operation);
        String accountCurrency =
                resolveAccountCurrency(
                        accountSourceId, unitId, accountCurrencyBySourceId, unitsById);

        BigDecimal signedAmount =
                subOperations.stream()
                        .map(node -> decimalValue(node, "f_value"))
                        .reduce(BigDecimal.ZERO, BigDecimal::add);

        // Convert the operation into the account's native currency using Skrooge's stored unit
        // rates (investment quantities via their unit price, foreign currencies via their peg).
        signedAmount =
                convertToCurrency(
                        signedAmount,
                        unitId,
                        opDate,
                        accountCurrency,
                        unitsById,
                        unitValuesByUnitId,
                        currencyRatesToPrimary);

        ImportedTransaction.ImportedTransactionBuilder builder =
                ImportedTransaction.builder()
                        .transactionDate(opDate)
                        .payee(payeeName)
                        .originalPayee(payee != null ? textValue(payee, "t_name") : null)
                        .amount(signedAmount)
                        .memo(comment)
                        .referenceNumber(skroogeOperationReference(operation))
                        .accountName(account != null ? textValue(account, "t_name") : null)
                        .sourceAccountId(accountSourceId)
                        .accountNumber(account != null ? resolveAccountNumber(account) : null)
                        .currency(accountCurrency)
                        .sourceAccountBalanceDelta(
                                accountBalanceDeltasByOperationId.get(operationId))
                        .clearedStatus(resolveClearedStatus(operation))
                        .sourceFileName(fileName);

        if (subOperations.size() == 1) {
            JsonNode subOperation = subOperations.get(0);
            JsonNode category = categoriesById.get(longValue(subOperation, "r_category_id"));
            builder.category(category != null ? textValue(category, "t_fullname") : null);
            builder.sourceCategoryId(longValue(subOperation, "r_category_id"));
        } else {
            List<ImportedTransaction.SplitEntry> splits =
                    subOperations.stream()
                            .map(
                                    subOperation -> {
                                        JsonNode category =
                                                categoriesById.get(
                                                        longValue(subOperation, "r_category_id"));
                                        BigDecimal splitAmount =
                                                decimalValue(subOperation, "f_value");
                                        splitAmount =
                                                convertToCurrency(
                                                        splitAmount,
                                                        unitId,
                                                        opDate,
                                                        accountCurrency,
                                                        unitsById,
                                                        unitValuesByUnitId,
                                                        currencyRatesToPrimary);
                                        return ImportedTransaction.SplitEntry.builder()
                                                .category(
                                                        category != null
                                                                ? textValue(category, "t_fullname")
                                                                : null)
                                                .memo(textValue(subOperation, "t_comment"))
                                                .sourceCategoryId(
                                                        longValue(subOperation, "r_category_id"))
                                                .amount(splitAmount.abs())
                                                .build();
                                    })
                            .collect(Collectors.toList());
            builder.splits(splits);
        }

        return builder.build();
    }

    private boolean isSyntheticBalanceOperation(JsonNode operation, List<JsonNode> subOperations) {
        if (SYNTHETIC_DATE.equals(textValue(operation, "d_date"))) {
            return true;
        }
        if (subOperations.size() == 1) {
            JsonNode subOperation = subOperations.get(0);
            Long categoryId = longValue(subOperation, "r_category_id");
            boolean noCategory = categoryId == null || categoryId == 0L;
            boolean noMode = textValue(operation, "t_mode").isBlank();
            boolean noComment =
                    textValue(operation, "t_comment").isBlank()
                            && textValue(subOperation, "t_comment").isBlank();
            return noCategory && noMode && noComment;
        }
        return false;
    }

    private BigDecimal deriveOpeningBalance(
            List<JsonNode> operations,
            Map<Long, List<JsonNode>> subOperationsByOperationId,
            Map<Long, JsonNode> unitsById,
            Map<Long, TreeMap<LocalDate, BigDecimal>> unitValuesByUnitId,
            String accountCurrency,
            Map<String, BigDecimal> currencyRatesToPrimary) {
        for (JsonNode operation : operations) {
            if (!SYNTHETIC_DATE.equals(textValue(operation, "d_date"))) {
                continue;
            }
            List<JsonNode> subOperations =
                    subOperationsByOperationId.getOrDefault(longValue(operation, "id"), List.of());
            if (!subOperations.isEmpty()) {
                BigDecimal rawValue = decimalValue(subOperations.get(0), "f_value");
                Long unitId = longValue(operation, "rc_unit_id");
                return convertToCurrency(
                        rawValue,
                        unitId,
                        null,
                        accountCurrency,
                        unitsById,
                        unitValuesByUnitId,
                        currencyRatesToPrimary);
            }
        }
        return BigDecimal.ZERO;
    }

    private LocalDate deriveOpeningDate(List<JsonNode> operations) {
        return operations.stream()
                .map(this::parseDate)
                .filter(Objects::nonNull)
                .min(Comparator.naturalOrder())
                .orElse(LocalDate.now());
    }

    private String deriveAccountCurrency(
            List<JsonNode> operations,
            Map<Long, JsonNode> unitsById,
            Map<Long, List<JsonNode>> subOperationsByOperationId) {
        for (JsonNode operation : operations) {
            if (SYNTHETIC_DATE.equals(textValue(operation, "d_date"))) {
                List<JsonNode> subOperations =
                        subOperationsByOperationId.getOrDefault(
                                longValue(operation, "id"), List.of());
                boolean hasNonZeroSyntheticBalance =
                        subOperations.stream()
                                .map(node -> decimalValue(node, "f_value"))
                                .anyMatch(value -> value.compareTo(BigDecimal.ZERO) != 0);
                if (!hasNonZeroSyntheticBalance) {
                    continue;
                }
                String syntheticCurrency =
                        resolveCurrencyCode(
                                unitsById.get(longValue(operation, "rc_unit_id")), unitsById);
                if (syntheticCurrency != null) {
                    return syntheticCurrency;
                }
            }
        }

        Map<String, Integer> counts = new HashMap<>();
        for (JsonNode operation : operations) {
            if (isSyntheticBalanceOperation(
                    operation,
                    subOperationsByOperationId.getOrDefault(
                            longValue(operation, "id"), List.of()))) {
                continue;
            }
            String currency =
                    resolveCurrencyCode(
                            unitsById.get(longValue(operation, "rc_unit_id")), unitsById);
            if (currency != null) {
                counts.merge(
                        currency,
                        Integer.valueOf(1),
                        (left, right) -> Integer.valueOf(left.intValue() + right.intValue()));
            }
        }

        return counts.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElseGet(() -> resolvePrimaryCurrencyCode(unitsById));
    }

    /**
     * The document's primary/reference currency code (resolved from the type "1" unit, per the
     * Skrooge format). Falls back to "USD" only when the file declares no primary unit at all.
     */
    private String resolvePrimaryCurrencyCode(Map<Long, JsonNode> unitsById) {
        return unitsById.values().stream()
                .filter(unit -> "1".equals(textValue(unit, "t_type")))
                .map(unit -> resolveCurrencyCode(unit, unitsById))
                .filter(Objects::nonNull)
                .findFirst()
                .orElse("USD");
    }

    private AccountType mapAccountType(String rawType) {
        if (rawType == null) {
            return AccountType.OTHER;
        }
        switch (rawType.trim().toUpperCase(Locale.ROOT)) {
            case "C":
                return AccountType.CHECKING;
            case "S":
            case "D":
                return AccountType.SAVINGS;
            case "I":
            case "P":
            case "A":
                return AccountType.INVESTMENT;
            case "W":
                return AccountType.CASH;
            case "L":
                return AccountType.OTHER;
            default:
                return AccountType.OTHER;
        }
    }

    private String resolveCurrencyCode(JsonNode unitNode, Map<Long, JsonNode> unitsById) {
        if (unitNode == null || unitNode.isMissingNode()) {
            return null;
        }

        String unitName = textValue(unitNode, "t_name");
        int start = unitName.lastIndexOf('(');
        int end = unitName.lastIndexOf(')');
        if (start >= 0 && end > start) {
            String code = unitName.substring(start + 1, end).trim().toUpperCase(Locale.ROOT);
            if (isSupportedCurrency(code)) {
                return code;
            }
        }

        String symbolCode =
                ImportParseSupport.currencyCodeForSymbol(textValue(unitNode, "t_symbol"));
        if (symbolCode != null) {
            return symbolCode;
        }

        String internetCode = textValue(unitNode, "t_internet_code");
        if (!internetCode.isBlank() && internetCode.contains("/")) {
            String rightSideCode =
                    internetCode
                            .substring(internetCode.indexOf('/') + 1)
                            .trim()
                            .toUpperCase(Locale.ROOT);
            if (isSupportedCurrency(rightSideCode)) {
                return rightSideCode;
            }
        }

        // For share (S) and object/crypto (O) units, resolve to the parent unit's currency.
        // The parent unit chain eventually reaches a currency unit (type "1" or "2") whose
        // name/symbol/internet_code encodes an ISO 4217 code.
        String unitType = textValue(unitNode, "t_type");
        if ("S".equals(unitType) || "O".equals(unitType)) {
            Long parentId = longValue(unitNode, "rd_unit_id");
            if (parentId != null && parentId != 0L) {
                JsonNode parentUnit = unitsById.get(parentId);
                if (parentUnit != null) {
                    String parentCurrency = resolveCurrencyCode(parentUnit, unitsById);
                    if (parentCurrency != null) {
                        return parentCurrency;
                    }
                }
            }
        }

        // For units like USD (type "O") whose internet_code is a pair like "EUR/USD",
        // also try the left side.
        if ("O".equals(unitType) && internetCode.contains("/")) {
            String leftSideCode =
                    internetCode
                            .substring(0, internetCode.indexOf('/'))
                            .trim()
                            .toUpperCase(Locale.ROOT);
            if (isSupportedCurrency(leftSideCode)) {
                return leftSideCode;
            }
        }

        return null;
    }

    private boolean isDirectCurrencyUnit(JsonNode unitNode) {
        if (unitNode == null || unitNode.isMissingNode()) {
            return false;
        }
        String unitName = textValue(unitNode, "t_name");
        int start = unitName.lastIndexOf('(');
        int end = unitName.lastIndexOf(')');
        if (start >= 0 && end > start && isSupportedCurrency(unitName.substring(start + 1, end))) {
            return true;
        }
        String symbol = textValue(unitNode, "t_symbol");
        if (ImportParseSupport.currencyCodeForSymbol(symbol) != null) {
            return true;
        }
        String internetCode = textValue(unitNode, "t_internet_code");
        if (!internetCode.isBlank() && internetCode.contains("/")) {
            String leftSideCode = internetCode.substring(0, internetCode.indexOf('/')).trim();
            return isSupportedCurrency(leftSideCode);
        }
        return false;
    }

    private boolean isSupportedCurrency(String currencyCode) {
        if (currencyCode == null || currencyCode.length() != 3) {
            return false;
        }
        try {
            Currency.getInstance(currencyCode);
            return true;
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }

    /**
     * Index unit values by unit ID, mapping each unit to a date-sorted price map. The {@code
     * f_quantity} field in each unitvalue entry is the conversion factor: 1 unit of the
     * share/crypto = {@code f_quantity} units of the parent unit.
     */
    private Map<Long, TreeMap<LocalDate, BigDecimal>> indexUnitValues(JsonNode unitValuesNode) {
        Map<Long, TreeMap<LocalDate, BigDecimal>> index = new HashMap<>();
        for (JsonNode uv : unitValuesNode) {
            Long unitId = longValue(uv, "rd_unit_id");
            LocalDate date = parseDate(uv);
            BigDecimal quantity = decimalValue(uv, "f_quantity");
            if (unitId != null && date != null && quantity.compareTo(BigDecimal.ZERO) != 0) {
                index.computeIfAbsent(unitId, k -> new TreeMap<>()).put(date, quantity);
            }
        }
        return index;
    }

    /** Resolve the unit price (f_quantity) at or before the given date. */
    private BigDecimal resolveUnitPrice(
            Long unitId,
            LocalDate date,
            Map<Long, TreeMap<LocalDate, BigDecimal>> unitValuesByUnitId) {
        if (unitId == null) {
            return null;
        }
        TreeMap<LocalDate, BigDecimal> prices = unitValuesByUnitId.get(unitId);
        if (prices == null || prices.isEmpty()) {
            return null;
        }
        if (date == null) {
            return prices.lastEntry().getValue();
        }
        Map.Entry<LocalDate, BigDecimal> entry = prices.floorEntry(date);
        if (entry == null) {
            entry = prices.firstEntry();
        }
        return entry.getValue();
    }

    /**
     * Resolve the primary/reference currency that a unit's value is converted into. Walks the unit
     * parent chain up to the primary (type "1") unit. Because {@link #convertToPrimaryCurrency}
     * converts every non-primary unit into the primary currency, the resulting monetary amount is
     * always denominated in this currency.
     */
    private String resolvePrimaryCurrency(Long unitId, Map<Long, JsonNode> unitsById) {
        JsonNode unit = unitsById.get(unitId);
        Set<Long> visited = new HashSet<>();
        while (unit != null && !unit.isMissingNode()) {
            if ("1".equals(textValue(unit, "t_type"))) {
                return resolveCurrencyCode(unit, unitsById);
            }
            Long parentId = longValue(unit, "rd_unit_id");
            if (parentId == null || parentId == 0L || !visited.add(parentId)) {
                break;
            }
            unit = unitsById.get(parentId);
        }
        return resolveCurrencyCode(unitsById.get(unitId), unitsById);
    }

    private BigDecimal convertToPrimaryCurrency(
            BigDecimal value,
            Long unitId,
            LocalDate date,
            Map<Long, JsonNode> unitsById,
            Map<Long, TreeMap<LocalDate, BigDecimal>> unitValuesByUnitId,
            Set<Long> visitedUnitIds) {
        if (value == null || unitId == null) {
            return value;
        }
        JsonNode unit = unitsById.get(unitId);
        if (unit == null || unit.isMissingNode()) {
            return value;
        }
        if ("1".equals(textValue(unit, "t_type"))) {
            return value; // already the primary/reference currency
        }
        if (!visitedUnitIds.add(unitId)) {
            return value; // guard against cyclic parent references
        }
        // Investment units (shares/crypto) are valued at the transaction-date price to preserve
        // historical per-transaction amounts; currency units use the latest exchange rate.
        LocalDate priceDate = isInvestmentUnit(unit) ? date : null;
        BigDecimal price = resolveUnitPrice(unitId, priceDate, unitValuesByUnitId);
        if (price == null) {
            log.warn(
                    "No unit value found for unit {}; using raw value {} without conversion",
                    unitId,
                    value);
            return value;
        }
        BigDecimal parentValue = value.multiply(price);
        Long parentId = longValue(unit, "rd_unit_id");
        if (parentId != null && parentId != 0L && !parentId.equals(unitId)) {
            return convertToPrimaryCurrency(
                    parentValue, parentId, date, unitsById, unitValuesByUnitId, visitedUnitIds);
        }
        return parentValue;
    }

    /** Returns true if the unit is a share (S) or object/crypto (O) type. */
    private boolean isInvestmentUnit(JsonNode unitNode) {
        if (unitNode == null || unitNode.isMissingNode()) {
            return false;
        }
        String unitType = textValue(unitNode, "t_type");
        return "S".equals(unitType) || ("O".equals(unitType) && !isDirectCurrencyUnit(unitNode));
    }

    /**
     * Determine each referenced account's native Skrooge currency (e.g. XOF). Uses {@link
     * #deriveAccountCurrency}, which prefers the account's opening-balance unit and otherwise the
     * most frequently used operation currency.
     */
    private Map<Long, String> buildAccountCurrencies(
            Map<Long, JsonNode> accountsById,
            Map<Long, JsonNode> unitsById,
            JsonNode operationsNode,
            Map<Long, List<JsonNode>> subOperationsByOperationId,
            Set<Long> referencedAccountIds) {
        Map<Long, List<JsonNode>> operationsByAccountId =
                groupByLong(operationsNode, "rd_account_id");
        Map<Long, String> currencies = new HashMap<>();
        for (Long accountId : referencedAccountIds) {
            if (accountsById.get(accountId) == null) {
                continue;
            }
            List<JsonNode> accountOperations =
                    operationsByAccountId.getOrDefault(accountId, List.of());
            String currency =
                    deriveAccountCurrency(accountOperations, unitsById, subOperationsByOperationId);
            if (currency != null) {
                currencies.put(accountId, currency);
            }
        }
        return currencies;
    }

    /**
     * Build a map of currency code to its exchange rate against the document's primary/reference
     * currency (type "1"), using Skrooge's own stored unit values. For example {@code XOF ->
     * 0.001524} means 1 XOF = 0.001524 EUR. The primary currency maps to 1.
     */
    private Map<String, BigDecimal> buildCurrencyRatesToPrimary(
            Map<Long, JsonNode> unitsById,
            Map<Long, TreeMap<LocalDate, BigDecimal>> unitValuesByUnitId) {
        Map<String, BigDecimal> rates = new HashMap<>();
        for (JsonNode unit : unitsById.values()) {
            String type = textValue(unit, "t_type");
            if (!CURRENCY_UNIT_TYPES.contains(type)) {
                continue;
            }
            String code = resolveCurrencyCode(unit, unitsById);
            if (code == null || rates.containsKey(code)) {
                continue;
            }
            BigDecimal rate =
                    convertToPrimaryCurrency(
                            BigDecimal.ONE,
                            longValue(unit, "id"),
                            null,
                            unitsById,
                            unitValuesByUnitId,
                            new HashSet<>());
            if (rate != null && rate.signum() != 0) {
                rates.put(code, rate);
            }
        }
        return rates;
    }

    /**
     * Resolve the native currency an operation should be stored in: the owning account's currency
     * when known, otherwise the operation unit's own currency (walked up its parent chain).
     */
    private String resolveAccountCurrency(
            Long accountSourceId,
            Long unitId,
            Map<Long, String> accountCurrencyBySourceId,
            Map<Long, JsonNode> unitsById) {
        String currency = accountCurrencyBySourceId.get(accountSourceId);
        if (currency != null) {
            return currency;
        }
        return resolvePrimaryCurrency(unitId, unitsById);
    }

    /**
     * Convert a raw f_value into the target account currency using Skrooge's stored rates. The
     * value is first converted to the primary/reference currency (investment units via their unit
     * price, foreign currencies via their peg), then divided by the target currency's rate to the
     * primary currency. All conversions use Skrooge's own unit values — never live exchange rates —
     * so imported balances reproduce Skrooge's figures exactly.
     */
    private BigDecimal convertToCurrency(
            BigDecimal rawValue,
            Long unitId,
            LocalDate date,
            String targetCurrency,
            Map<Long, JsonNode> unitsById,
            Map<Long, TreeMap<LocalDate, BigDecimal>> unitValuesByUnitId,
            Map<String, BigDecimal> currencyRatesToPrimary) {
        BigDecimal primaryAmount =
                convertToPrimaryCurrency(
                        rawValue, unitId, date, unitsById, unitValuesByUnitId, new HashSet<>());
        if (primaryAmount == null || targetCurrency == null) {
            return primaryAmount;
        }
        BigDecimal targetRate = currencyRatesToPrimary.get(targetCurrency);
        if (targetRate == null
                || targetRate.signum() == 0
                || targetRate.compareTo(BigDecimal.ONE) == 0) {
            return primaryAmount;
        }
        return primaryAmount.divide(targetRate, 10, RoundingMode.HALF_UP);
    }

    /** Resolve institution name, falling back to a generated name if blank. */
    private String resolveInstitutionName(JsonNode bank, Long bankId) {
        String name = textValue(bank, "t_name");
        if (!name.isBlank()) {
            return name;
        }
        return "Institution " + bankId;
    }

    private String resolvePayee(JsonNode payeeNode, String comment, String fallbackMode) {
        String payeeName = payeeNode != null ? textValue(payeeNode, "t_name") : "";
        if (!payeeName.isBlank()
                && !GENERIC_PAYEES.contains(normalizeKeyword(payeeName).toUpperCase(Locale.ROOT))) {
            return payeeName;
        }
        if (!comment.isBlank()) {
            return comment;
        }
        return fallbackMode;
    }

    private String resolveAccountNumber(JsonNode accountNode) {
        String accountNumber = textValue(accountNode, "t_number");
        if (!accountNumber.isBlank()) {
            return accountNumber;
        }
        String name = textValue(accountNode, "t_name");
        return name.matches("[0-9]{6,}") ? name : null;
    }

    private String resolveClearedStatus(JsonNode operation) {
        return "Y".equalsIgnoreCase(textValue(operation, "t_status")) ? "reconciled" : null;
    }

    private String skroogeOperationReference(JsonNode operation) {
        Long operationId = longValue(operation, "id");
        return operationId != null ? "skrooge:operation:" + operationId : null;
    }

    private String skroogeTransferReference(Long groupId) {
        return groupId != null ? "skrooge:transfer-group:" + groupId : null;
    }

    private boolean hasValidDate(JsonNode operation) {
        return parseDate(operation) != null;
    }

    private LocalDate parseDate(JsonNode operation) {
        String rawDate = textValue(operation, "d_date");
        if (rawDate.isBlank() || SYNTHETIC_DATE.equals(rawDate)) {
            return null;
        }
        try {
            return LocalDate.parse(rawDate);
        } catch (Exception ex) {
            log.debug("Unable to parse Skrooge date '{}'", rawDate);
            return null;
        }
    }

    private Long longValue(JsonNode node, String fieldName) {
        JsonNode valueNode = node.path(fieldName);
        if (valueNode.isMissingNode() || valueNode.isNull()) {
            return null;
        }
        if (valueNode.isIntegralNumber()) {
            return valueNode.asLong();
        }
        String rawValue = valueNode.asText();
        if (rawValue == null || rawValue.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(rawValue);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private Long positiveLongValue(JsonNode node, String fieldName) {
        Long value = longValue(node, fieldName);
        return value != null && value > 0 ? value : null;
    }

    private BigDecimal decimalValue(JsonNode node, String fieldName) {
        JsonNode valueNode = node.path(fieldName);
        if (valueNode.isMissingNode() || valueNode.isNull()) {
            return BigDecimal.ZERO;
        }
        String rawValue = valueNode.asText();
        if (rawValue == null || rawValue.isBlank()) {
            return BigDecimal.ZERO;
        }
        return new BigDecimal(rawValue);
    }

    private String textValue(JsonNode node, String fieldName) {
        if (node == null || node.isMissingNode()) {
            return "";
        }
        JsonNode valueNode = node.path(fieldName);
        if (valueNode.isMissingNode() || valueNode.isNull()) {
            return "";
        }
        return valueNode.asText("").trim();
    }

    private boolean booleanValue(JsonNode node, String fieldName) {
        JsonNode valueNode = node.path(fieldName);
        if (valueNode.isBoolean()) {
            return valueNode.asBoolean();
        }
        String rawValue = valueNode.asText();
        return "Y".equalsIgnoreCase(rawValue) || "true".equalsIgnoreCase(rawValue);
    }
}
