package org.openfinance.service.parser;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
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
    private static final Set<String> GENERIC_PAYEES =
            Set.of(
                    "FACTURE CARTE",
                    "PRLV SEPA",
                    "VIR SEPA RECU",
                    "RETRAIT DAB",
                    "CR",
                    "DEBIT",
                    "CREDIT");

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
        Map<Long, List<JsonNode>> subOperationsByOperationId =
                groupByLong(root.path("suboperation"), "rd_operation_id");
        Map<Long, List<JsonNode>> operationsByGroupId =
                groupByPositiveLong(root.path("operation"), "i_group_id");

        Set<Long> transferOperationIds =
                detectTransferOperationIds(
                        operationsByGroupId, subOperationsByOperationId, categoriesById);
        Set<Long> referencedAccountIds =
                collectReferencedAccountIds(
                        root.path("operation"), transferOperationIds, subOperationsByOperationId);
        Set<Long> referencedCategoryIds =
                collectReferencedCategoryIds(
                        subOperationsByOperationId, transferOperationIds, categoriesById);

        SkroogeImportMetadata metadata =
                SkroogeImportMetadata.builder()
                        .institutions(
                                buildInstitutions(banksById, referencedAccountIds, accountsById))
                        .accounts(
                                buildAccounts(
                                        accountsById,
                                        unitsById,
                                        root.path("operation"),
                                        subOperationsByOperationId,
                                        referencedAccountIds))
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
                                    unitsById));
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
                            fileName);
            if (transaction != null) {
                transactions.add(transaction);
            }
        }

        String defaultCurrency =
                metadata.getAccounts().stream()
                        .map(SkroogeImportMetadata.SkroogeAccount::getCurrency)
                        .filter(Objects::nonNull)
                        .findFirst()
                        .orElse("USD");

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

    private Set<Long> detectTransferOperationIds(
            Map<Long, List<JsonNode>> operationsByGroupId,
            Map<Long, List<JsonNode>> subOperationsByOperationId,
            Map<Long, JsonNode> categoriesById) {
        Set<Long> transferOperationIds = new HashSet<>();
        for (Map.Entry<Long, List<JsonNode>> entry : operationsByGroupId.entrySet()) {
            if (isTransferGroup(entry.getValue(), subOperationsByOperationId, categoriesById)) {
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
            Map<Long, JsonNode> categoriesById) {
        if (operations.size() != 2) {
            return false;
        }

        BigDecimal signedTotal = BigDecimal.ZERO;
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
            if (!isTransferCategory(category)) {
                return false;
            }
            signedTotal = signedTotal.add(decimalValue(subOperation, "f_value"));
        }

        return signedTotal.compareTo(BigDecimal.ZERO) == 0;
    }

    private boolean isTransferCategory(JsonNode categoryNode) {
        if (categoryNode == null || categoryNode.isMissingNode()) {
            return false;
        }
        String fullName = textValue(categoryNode, "t_fullname").toLowerCase(Locale.ROOT);
        String name = textValue(categoryNode, "t_name").toLowerCase(Locale.ROOT);
        return fullName.contains("transfert")
                || fullName.contains("transfer")
                || name.contains("transfert")
                || name.contains("transfer");
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
                            .name(textValue(bank, "t_name"))
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
            JsonNode operationsNode,
            Map<Long, List<JsonNode>> subOperationsByOperationId,
            Set<Long> referencedAccountIds) {
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
            BigDecimal openingBalance =
                    deriveOpeningBalance(accountOperations, subOperationsByOperationId);
            LocalDate openingDate = deriveOpeningDate(accountOperations);
            String currency =
                    deriveAccountCurrency(accountOperations, unitsById, subOperationsByOperationId);

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

            String fullName = textValue(entry.getValue(), "t_fullname").toLowerCase(Locale.ROOT);
            if (containsAny(
                    fullName,
                    List.of(
                            "revenu",
                            "salaire",
                            "intér",
                            "cadeaux reçus",
                            "revente",
                            "plus-values"))) {
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
            Map<Long, JsonNode> unitsById) {
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

        ImportedTransaction firstTransaction =
                buildTransferTransaction(
                        firstOperation,
                        secondOperation,
                        firstSubOperation,
                        payeesById,
                        unitsById,
                        accountsById,
                        categoriesById,
                        groupId);
        ImportedTransaction secondTransaction =
                buildTransferTransaction(
                        secondOperation,
                        firstOperation,
                        secondSubOperation,
                        payeesById,
                        unitsById,
                        accountsById,
                        categoriesById,
                        groupId);

        return List.of(firstTransaction, secondTransaction);
    }

    private ImportedTransaction buildTransferTransaction(
            JsonNode operation,
            JsonNode otherOperation,
            JsonNode subOperation,
            Map<Long, JsonNode> payeesById,
            Map<Long, JsonNode> unitsById,
            Map<Long, JsonNode> accountsById,
            Map<Long, JsonNode> categoriesById,
            Long groupId) {
        JsonNode account = accountsById.get(longValue(operation, "rd_account_id"));
        JsonNode otherAccount = accountsById.get(longValue(otherOperation, "rd_account_id"));
        JsonNode payee = payeesById.get(longValue(operation, "r_payee_id"));
        JsonNode category = categoriesById.get(longValue(subOperation, "r_category_id"));

        BigDecimal signedAmount = decimalValue(subOperation, "f_value");
        String comment = textValue(operation, "t_comment");

        return ImportedTransaction.builder()
                .transactionDate(parseDate(operation))
                .payee(resolvePayee(payee, comment, textValue(operation, "t_mode")))
                .originalPayee(payee != null ? textValue(payee, "t_name") : null)
                .amount(signedAmount)
                .memo(comment)
                .category(category != null ? textValue(category, "t_fullname") : null)
                .sourceCategoryId(longValue(subOperation, "r_category_id"))
                .referenceNumber(skroogeOperationReference(operation))
                .accountName(account != null ? textValue(account, "t_name") : null)
                .sourceAccountId(longValue(operation, "rd_account_id"))
                .accountNumber(account != null ? resolveAccountNumber(account) : null)
                .currency(resolveCurrencyCode(unitsById.get(longValue(operation, "rc_unit_id"))))
                .transfer(true)
                .transferGroupKey(skroogeTransferReference(groupId))
                .toAccountName(otherAccount != null ? textValue(otherAccount, "t_name") : null)
                .toAccountSourceId(longValue(otherOperation, "rd_account_id"))
                .clearedStatus(resolveClearedStatus(operation))
                .sourceFileName("skrooge-json")
                .build();
    }

    private ImportedTransaction buildStandardTransaction(
            JsonNode operation,
            Map<Long, List<JsonNode>> subOperationsByOperationId,
            Map<Long, JsonNode> accountsById,
            Map<Long, JsonNode> categoriesById,
            Map<Long, JsonNode> payeesById,
            Map<Long, JsonNode> unitsById,
            String fileName) {
        Long operationId = longValue(operation, "id");
        List<JsonNode> subOperations =
                subOperationsByOperationId.getOrDefault(operationId, List.of());

        if (!hasValidDate(operation)
                || subOperations.isEmpty()
                || isSyntheticBalanceOperation(operation, subOperations)) {
            return null;
        }

        JsonNode account = accountsById.get(longValue(operation, "rd_account_id"));
        JsonNode payee = payeesById.get(longValue(operation, "r_payee_id"));
        String comment = textValue(operation, "t_comment");
        String payeeName = resolvePayee(payee, comment, textValue(operation, "t_mode"));
        BigDecimal signedAmount =
                subOperations.stream()
                        .map(node -> decimalValue(node, "f_value"))
                        .reduce(BigDecimal.ZERO, BigDecimal::add);

        ImportedTransaction.ImportedTransactionBuilder builder =
                ImportedTransaction.builder()
                        .transactionDate(parseDate(operation))
                        .payee(payeeName)
                        .originalPayee(payee != null ? textValue(payee, "t_name") : null)
                        .amount(signedAmount)
                        .memo(comment)
                        .referenceNumber(skroogeOperationReference(operation))
                        .accountName(account != null ? textValue(account, "t_name") : null)
                        .sourceAccountId(longValue(operation, "rd_account_id"))
                        .accountNumber(account != null ? resolveAccountNumber(account) : null)
                        .currency(
                                resolveCurrencyCode(
                                        unitsById.get(longValue(operation, "rc_unit_id"))))
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
                                        return ImportedTransaction.SplitEntry.builder()
                                                .category(
                                                        category != null
                                                                ? textValue(category, "t_fullname")
                                                                : null)
                                                .memo(textValue(subOperation, "t_comment"))
                                                .sourceCategoryId(
                                                        longValue(subOperation, "r_category_id"))
                                                .amount(decimalValue(subOperation, "f_value").abs())
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
            List<JsonNode> operations, Map<Long, List<JsonNode>> subOperationsByOperationId) {
        for (JsonNode operation : operations) {
            if (!SYNTHETIC_DATE.equals(textValue(operation, "d_date"))) {
                continue;
            }
            List<JsonNode> subOperations =
                    subOperationsByOperationId.getOrDefault(longValue(operation, "id"), List.of());
            if (!subOperations.isEmpty()) {
                return decimalValue(subOperations.get(0), "f_value");
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
                String syntheticCurrency =
                        resolveCurrencyCode(unitsById.get(longValue(operation, "rc_unit_id")));
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
                    resolveCurrencyCode(unitsById.get(longValue(operation, "rc_unit_id")));
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
                return AccountType.SAVINGS;
            case "I":
            case "P":
                return AccountType.INVESTMENT;
            case "W":
                return AccountType.CASH;
            default:
                return AccountType.OTHER;
        }
    }

    private String resolveCurrencyCode(JsonNode unitNode) {
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

        String symbol = textValue(unitNode, "t_symbol");
        if ("€".equals(symbol)) {
            return "EUR";
        }
        if ("$".equals(symbol)) {
            return "USD";
        }
        if ("CFA".equalsIgnoreCase(symbol)) {
            return "XOF";
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

        return null;
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

    private String resolvePayee(JsonNode payeeNode, String comment, String fallbackMode) {
        String payeeName = payeeNode != null ? textValue(payeeNode, "t_name") : "";
        if (!payeeName.isBlank() && !GENERIC_PAYEES.contains(payeeName.toUpperCase(Locale.ROOT))) {
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
