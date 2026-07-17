package org.openfinance.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.openfinance.dto.DataExportRequest;
import org.openfinance.dto.DataExportResponse;
import org.openfinance.entity.*;
import org.openfinance.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service for exporting and importing user financial data.
 *
 * <p>Provides comprehensive data backup and migration functionality, allowing users to export all
 * their financial data in JSON or CSV format.
 *
 * <p>Requirement: REQ-3.4 - Data Export and Backup
 *
 * @author Open Finance Development Team
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DataExportService {

    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;
    private final AssetRepository assetRepository;
    private final LiabilityRepository liabilityRepository;
    private final BudgetRepository budgetRepository;
    private final CategoryRepository categoryRepository;
    private final RealEstateRepository realEstateRepository;

    /**
     * Export all user data based on the provided request.
     *
     * @param userId User ID
     * @param request Export request specifying format and inclusions
     * @return Export response with metadata and download information
     */
    @Transactional(readOnly = true)
    public DataExportResponse exportUserData(Long userId, DataExportRequest request) {
        log.info("Starting data export for user {} in format {}", userId, request.getFormat());

        // Collect data based on request
        Map<String, Object> exportData = new LinkedHashMap<>();
        exportData.put("exportMetadata", buildExportMetadata(userId));

        int accountCount = 0;
        int transactionCount = 0;
        int assetCount = 0;
        int liabilityCount = 0;
        int budgetCount = 0;
        int categoryCount = 0;
        int realEstateCount = 0;

        // Export accounts
        if (request.isIncludeAccounts()) {
            List<Map<String, Object>> accounts = exportAccounts(userId);
            exportData.put("accounts", accounts);
            accountCount = accounts.size();
            log.debug("Exported {} accounts", accountCount);
        }

        // Export categories (needed for transactions)
        if (request.isIncludeCategories()) {
            List<Map<String, Object>> categories = exportCategories(userId);
            exportData.put("categories", categories);
            categoryCount = categories.size();
            log.debug("Exported {} categories", categoryCount);
        }

        // Export transactions
        if (request.isIncludeTransactions()) {
            List<Map<String, Object>> transactions = exportTransactions(userId, request);
            exportData.put("transactions", transactions);
            transactionCount = transactions.size();
            log.debug("Exported {} transactions", transactionCount);
        }

        // Export assets
        if (request.isIncludeAssets()) {
            List<Map<String, Object>> assets = exportAssets(userId);
            exportData.put("assets", assets);
            assetCount = assets.size();
            log.debug("Exported {} assets", assetCount);
        }

        // Export liabilities
        if (request.isIncludeLiabilities()) {
            List<Map<String, Object>> liabilities = exportLiabilities(userId);
            exportData.put("liabilities", liabilities);
            liabilityCount = liabilities.size();
            log.debug("Exported {} liabilities", liabilityCount);
        }

        // Export budgets
        if (request.isIncludeBudgets()) {
            List<Map<String, Object>> budgets = exportBudgets(userId);
            exportData.put("budgets", budgets);
            budgetCount = budgets.size();
            log.debug("Exported {} budgets", budgetCount);
        }

        // Export real estate
        if (request.isIncludeRealEstate()) {
            List<Map<String, Object>> realEstate = exportRealEstate(userId);
            exportData.put("realEstate", realEstate);
            realEstateCount = realEstate.size();
            log.debug("Exported {} real estate properties", realEstateCount);
        }

        // Generate file content
        byte[] fileContent;
        String filename;
        try {
            if ("JSON".equalsIgnoreCase(request.getFormat())) {
                fileContent = generateJsonExport(exportData);
                filename =
                        String.format(
                                "openfinance-export-%s.json",
                                LocalDateTime.now()
                                        .format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")));
            } else {
                fileContent = generateCsvExport(exportData);
                filename =
                        String.format(
                                "openfinance-export-%s.csv",
                                LocalDateTime.now()
                                        .format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")));
            }
        } catch (IOException e) {
            log.error("Failed to generate export file for user {}", userId, e);
            throw new RuntimeException("Failed to generate export file: " + e.getMessage(), e);
        }

        String exportId = UUID.randomUUID().toString();
        LocalDateTime now = LocalDateTime.now();

        log.info(
                "Data export completed for user {}. Size: {} bytes, Accounts: {}, Transactions: {}, Assets: {}, Liabilities: {}",
                userId,
                fileContent.length,
                accountCount,
                transactionCount,
                assetCount,
                liabilityCount);

        return DataExportResponse.builder()
                .exportId(exportId)
                .format(request.getFormat())
                .filename(filename)
                .fileSizeBytes(fileContent.length)
                .accountCount(accountCount)
                .transactionCount(transactionCount)
                .assetCount(assetCount)
                .liabilityCount(liabilityCount)
                .budgetCount(budgetCount)
                .categoryCount(categoryCount)
                .realEstateCount(realEstateCount)
                .generatedAt(now)
                .expiresAt(now.plusHours(24))
                .message("Export completed successfully")
                .build();
    }

    /** Build export metadata. */
    private Map<String, Object> buildExportMetadata(Long userId) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("exportVersion", "1.0");
        metadata.put("userId", userId);
        metadata.put(
                "exportDate", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        metadata.put("application", "Open Finance");
        metadata.put(
                "disclaimer", "This export contains decrypted financial data. Store securely.");
        return metadata;
    }

    /** Export accounts. */
    private List<Map<String, Object>> exportAccounts(Long userId) {
        List<Account> accounts = accountRepository.findByUserId(userId);
        return accounts.stream()
                .map(
                        account -> {
                            Map<String, Object> data = new LinkedHashMap<>();
                            data.put("id", account.getId());
                            data.put("name", account.getName());
                            data.put("type", account.getType().name());
                            data.put("currency", account.getCurrency());
                            data.put("balance", account.getBalance());
                            data.put("description", account.getDescription());
                            data.put("isActive", account.getIsActive());
                            data.put("createdAt", formatDateTime(account.getCreatedAt()));
                            data.put("updatedAt", formatDateTime(account.getUpdatedAt()));
                            return data;
                        })
                .collect(Collectors.toList());
    }

    /** Export categories. */
    private List<Map<String, Object>> exportCategories(Long userId) {
        List<Category> categories = categoryRepository.findByUserId(userId);
        return categories.stream()
                .map(
                        category -> {
                            Map<String, Object> data = new LinkedHashMap<>();
                            data.put("id", category.getId());
                            data.put("name", category.getName());
                            data.put("type", category.getType().name());
                            data.put("parentId", category.getParentId());
                            data.put("icon", category.getIcon());
                            data.put("color", category.getColor());
                            data.put("isSystem", category.getIsSystem());
                            data.put("createdAt", formatDateTime(category.getCreatedAt()));
                            return data;
                        })
                .collect(Collectors.toList());
    }

    /** Export transactions with date filtering. */
    private List<Map<String, Object>> exportTransactions(Long userId, DataExportRequest request) {
        List<Transaction> transactions;

        if (request.getStartDate() != null && request.getEndDate() != null) {
            transactions =
                    transactionRepository.findByUserIdAndDateBetween(
                            userId, request.getStartDate(), request.getEndDate());
        } else {
            transactions = transactionRepository.findByUserId(userId);
        }

        if (!request.isIncludeDeleted()) {
            transactions =
                    transactions.stream()
                            .filter(t -> !t.getIsDeleted())
                            .collect(Collectors.toList());
        }

        return transactions.stream()
                .map(
                        transaction -> {
                            Map<String, Object> data = new LinkedHashMap<>();
                            data.put("id", transaction.getId());
                            data.put("accountId", transaction.getAccountId());
                            data.put("toAccountId", transaction.getToAccountId());
                            data.put("type", transaction.getType().name());
                            data.put("amount", transaction.getAmount());
                            data.put("currency", transaction.getCurrency());
                            data.put("categoryId", transaction.getCategoryId());
                            data.put("date", transaction.getDate().toString());
                            data.put("description", transaction.getDescription());
                            data.put("notes", transaction.getNotes());
                            data.put("tags", transaction.getTags());
                            data.put("transferId", transaction.getTransferId());
                            data.put("isReconciled", transaction.getIsReconciled());
                            data.put("isDeleted", transaction.getIsDeleted());
                            data.put("createdAt", formatDateTime(transaction.getCreatedAt()));
                            data.put("updatedAt", formatDateTime(transaction.getUpdatedAt()));
                            return data;
                        })
                .collect(Collectors.toList());
    }

    /** Export assets. */
    private List<Map<String, Object>> exportAssets(Long userId) {
        List<Asset> assets = assetRepository.findByUserId(userId);
        return assets.stream()
                .map(
                        asset -> {
                            Map<String, Object> data = new LinkedHashMap<>();
                            data.put("id", asset.getId());
                            data.put("accountId", asset.getAccountId());
                            data.put("name", asset.getName());
                            data.put("type", asset.getType().name());
                            data.put("symbol", asset.getSymbol());
                            data.put("quantity", asset.getQuantity());
                            data.put("purchasePrice", asset.getPurchasePrice());
                            data.put("currentPrice", asset.getCurrentPrice());
                            data.put("currency", asset.getCurrency());
                            data.put(
                                    "purchaseDate",
                                    asset.getPurchaseDate() != null
                                            ? asset.getPurchaseDate().toString()
                                            : null);
                            data.put("notes", asset.getNotes());
                            data.put("lastUpdated", formatDateTime(asset.getLastUpdated()));
                            data.put("createdAt", formatDateTime(asset.getCreatedAt()));
                            data.put("updatedAt", formatDateTime(asset.getUpdatedAt()));
                            return data;
                        })
                .collect(Collectors.toList());
    }

    /** Export liabilities. */
    private List<Map<String, Object>> exportLiabilities(Long userId) {
        List<Liability> liabilities = liabilityRepository.findByUserIdOrderByCreatedAtDesc(userId);
        return liabilities.stream()
                .map(
                        liability -> {
                            Map<String, Object> data = new LinkedHashMap<>();
                            data.put("id", liability.getId());
                            data.put("name", liability.getName());
                            data.put("type", liability.getType().name());
                            data.put("principal", liability.getPrincipal());
                            data.put("currentBalance", liability.getCurrentBalance());
                            data.put("interestRate", liability.getInterestRate());
                            data.put(
                                    "startDate",
                                    liability.getStartDate() != null
                                            ? liability.getStartDate().toString()
                                            : null);
                            data.put(
                                    "endDate",
                                    liability.getEndDate() != null
                                            ? liability.getEndDate().toString()
                                            : null);
                            data.put("minimumPayment", liability.getMinimumPayment());
                            data.put("currency", liability.getCurrency());
                            data.put("notes", liability.getNotes());
                            data.put("createdAt", formatDateTime(liability.getCreatedAt()));
                            data.put("updatedAt", formatDateTime(liability.getUpdatedAt()));
                            return data;
                        })
                .collect(Collectors.toList());
    }

    /** Export budgets. */
    private List<Map<String, Object>> exportBudgets(Long userId) {
        List<Budget> budgets = budgetRepository.findByUserId(userId);
        return budgets.stream()
                .map(
                        budget -> {
                            Map<String, Object> data = new LinkedHashMap<>();
                            data.put("id", budget.getId());
                            data.put("categoryId", budget.getCategoryId());
                            data.put("amount", budget.getAmount());
                            data.put("period", budget.getPeriod().name());
                            data.put(
                                    "startDate",
                                    budget.getStartDate() != null
                                            ? budget.getStartDate().toString()
                                            : null);
                            data.put(
                                    "endDate",
                                    budget.getEndDate() != null
                                            ? budget.getEndDate().toString()
                                            : null);
                            data.put("rollover", budget.getRollover());
                            data.put("createdAt", formatDateTime(budget.getCreatedAt()));
                            data.put("updatedAt", formatDateTime(budget.getUpdatedAt()));
                            return data;
                        })
                .collect(Collectors.toList());
    }

    /** Export real estate. */
    private List<Map<String, Object>> exportRealEstate(Long userId) {
        List<RealEstateProperty> properties = realEstateRepository.findByUserId(userId);
        return properties.stream()
                .map(
                        property -> {
                            Map<String, Object> data = new LinkedHashMap<>();
                            data.put("id", property.getId());
                            data.put("name", property.getName());
                            data.put("type", property.getPropertyType().name());
                            data.put("address", property.getAddress());
                            data.put("purchasePrice", property.getPurchasePrice());
                            data.put("currentValue", property.getCurrentValue());
                            data.put(
                                    "purchaseDate",
                                    property.getPurchaseDate() != null
                                            ? property.getPurchaseDate().toString()
                                            : null);
                            data.put("currency", property.getCurrency());
                            data.put("notes", property.getNotes());
                            data.put("isActive", property.isActive());
                            data.put("createdAt", formatDateTime(property.getCreatedAt()));
                            data.put("updatedAt", formatDateTime(property.getUpdatedAt()));
                            return data;
                        })
                .collect(Collectors.toList());
    }

    /** Generate JSON export. */
    private byte[] generateJsonExport(Map<String, Object> data) throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.enable(SerializationFeature.INDENT_OUTPUT);
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        return mapper.writeValueAsBytes(data);
    }

    /** Generate CSV export (simplified format with separate files for each entity type). */
    private byte[] generateCsvExport(Map<String, Object> data) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        OutputStreamWriter writer = new OutputStreamWriter(baos, StandardCharsets.UTF_8);

        // Write metadata
        writer.write("# Open Finance Data Export\n");
        writer.write(
                "# Export Date: "
                        + LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
                        + "\n");
        writer.write("# WARNING: This file contains decrypted financial data. Store securely.\n\n");

        // Export each entity type as CSV section
        @SuppressWarnings("unchecked")
        Map<String, Object> metadata = (Map<String, Object>) data.get("exportMetadata");
        if (metadata != null) {
            writer.write("=== Export Metadata ===\n");
            for (Map.Entry<String, Object> entry : metadata.entrySet()) {
                writer.write(entry.getKey() + "," + entry.getValue() + "\n");
            }
            writer.write("\n");
        }

        // Export accounts
        writeCsvSection(writer, "Accounts", (List<?>) data.get("accounts"));
        writeCsvSection(writer, "Categories", (List<?>) data.get("categories"));
        writeCsvSection(writer, "Transactions", (List<?>) data.get("transactions"));
        writeCsvSection(writer, "Assets", (List<?>) data.get("assets"));
        writeCsvSection(writer, "Liabilities", (List<?>) data.get("liabilities"));
        writeCsvSection(writer, "Budgets", (List<?>) data.get("budgets"));
        writeCsvSection(writer, "Real Estate", (List<?>) data.get("realEstate"));

        writer.flush();
        return baos.toByteArray();
    }

    /** Write CSV section for a list of entities. */
    @SuppressWarnings("unchecked")
    private void writeCsvSection(OutputStreamWriter writer, String sectionName, List<?> items)
            throws IOException {
        if (items == null || items.isEmpty()) {
            return;
        }

        writer.write("=== " + sectionName + " ===\n");

        // Write header
        Map<String, Object> firstItem = (Map<String, Object>) items.get(0);
        writer.write(String.join(",", firstItem.keySet()) + "\n");

        // Write data rows
        for (Object item : items) {
            Map<String, Object> map = (Map<String, Object>) item;
            String row =
                    map.values().stream()
                            .map(v -> v != null ? escapeCsv(v.toString()) : "")
                            .collect(Collectors.joining(","));
            writer.write(row + "\n");
        }

        writer.write("\n");
    }

    /** Escape CSV values. */
    private String escapeCsv(String value) {
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }

    /** Format LocalDateTime to ISO string. */
    private String formatDateTime(LocalDateTime dateTime) {
        return dateTime != null ? dateTime.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME) : null;
    }
}
