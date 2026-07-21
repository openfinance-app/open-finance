package org.openfinance.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.openfinance.dto.ImportedTransaction;
import org.openfinance.entity.Category;
import org.openfinance.entity.CategoryType;
import org.openfinance.entity.ImportSession;
import org.openfinance.repository.CategoryRepository;
import org.openfinance.repository.ImportSessionRepository;
import org.openfinance.service.ai.AIProvider;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * AI-powered transaction categorization using the configured LLM provider.
 *
 * <p>Designed as a last-resort tier in the import categorization pipeline. Sends uncategorized
 * transactions in batches to the AI and maps responses back to user categories.
 *
 * @since 1.0
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AICategorizationService {

    private static final int BATCH_SIZE = 15;
    private static final double AI_CONFIDENCE = 0.75;
    private static final Duration BATCH_TIMEOUT = Duration.ofSeconds(120);
    private static final long TOTAL_BUDGET_MS = 300_000;

    private final AIProvider aiProvider;
    private final ObjectMapper objectMapper;
    private final ImportSessionRepository importSessionRepository;
    private final CategoryRepository categoryRepository;
    private final MessageSource messageSource;
    private final DefaultCurrencyProvider defaultCurrencyProvider;

    /**
     * Asynchronously categorize uncategorized transactions for an import session. Loads the
     * session, runs AI categorization, and persists results back. Called after reviewTransactions()
     * returns to the frontend, so it doesn't block the HTTP response.
     *
     * @param sessionId the import session ID
     * @param userId the user ID (for loading categories)
     */
    @Async("taskExecutor")
    public void categorizeSessionAsync(Long sessionId, Long userId) {
        log.info("Starting async AI categorization for session: {}", sessionId);

        try {
            ImportSession session = importSessionRepository.findById(sessionId).orElse(null);
            if (session == null) {
                log.warn("Session {} not found for AI categorization", sessionId);
                return;
            }

            List<ImportedTransaction> transactions = deserializeTransactions(session.getMetadata());
            if (transactions.isEmpty()) {
                return;
            }

            List<Category> userCategories = categoryRepository.findByUserId(userId);
            categorizeWithAI(transactions, userCategories);

            // Check if any transactions were categorized
            boolean anyCategorized =
                    transactions.stream()
                            .anyMatch(
                                    tx ->
                                            tx.getCategory() != null
                                                    && !tx.getCategory().trim().isEmpty()
                                                    && tx.getValidationErrors() != null
                                                    && tx.getValidationErrors().stream()
                                                            .anyMatch(
                                                                    e ->
                                                                            e.startsWith(
                                                                                    "AI-CATEGORIZED:")));

            if (anyCategorized) {
                // Re-read session to get fresh state before updating
                session = importSessionRepository.findById(sessionId).orElse(null);
                if (session == null) {
                    return;
                }

                // Extract existing metadata fields
                BigDecimal ledgerBalance = BigDecimal.ZERO;
                String fileCurrency = defaultCurrencyProvider.resolveForUser(session.getUserId());
                try {
                    Map<String, Object> metadataMap =
                            objectMapper.readValue(
                                    session.getMetadata(),
                                    new TypeReference<Map<String, Object>>() {});
                    if (metadataMap.containsKey("ledgerBalance")) {
                        ledgerBalance = new BigDecimal(metadataMap.get("ledgerBalance").toString());
                    }
                    if (metadataMap.containsKey("fileCurrency")
                            && metadataMap.get("fileCurrency") != null) {
                        fileCurrency = metadataMap.get("fileCurrency").toString();
                    }
                } catch (Exception e) {
                    log.warn(
                            "Error extracting metadata during AI categorization: {}",
                            e.getMessage());
                }

                session.setMetadata(
                        serializeTransactions(transactions, ledgerBalance, fileCurrency));
                importSessionRepository.save(session);
                log.info("AI categorization persisted for session: {}", sessionId);
            }
        } catch (Exception e) {
            log.warn(
                    "Async AI categorization failed for session {}: {}", sessionId, e.getMessage());
        }
    }

    /**
     * Categorize uncategorized transactions using AI. Only processes transactions that have no
     * category assigned.
     *
     * @param transactions the full list of imported transactions (modified in place)
     * @param userCategories the user's available categories
     */
    public void categorizeWithAI(
            List<ImportedTransaction> transactions, List<Category> userCategories) {
        // Check AI availability first
        Boolean available = aiProvider.isAvailable().block(Duration.ofSeconds(3));
        if (!Boolean.TRUE.equals(available)) {
            log.info("AI provider unavailable, skipping AI categorization");
            return;
        }

        // Collect indices of uncategorized transactions
        List<Integer> uncategorizedIndices = new ArrayList<>();
        for (int i = 0; i < transactions.size(); i++) {
            ImportedTransaction tx = transactions.get(i);
            if (tx.getCategory() == null || tx.getCategory().trim().isEmpty()) {
                uncategorizedIndices.add(i);
            }
        }

        if (uncategorizedIndices.isEmpty()) {
            log.debug("No uncategorized transactions to process with AI");
            return;
        }

        log.info(
                "AI categorization: processing {} uncategorized transactions with {} user categories",
                uncategorizedIndices.size(),
                userCategories.size());

        if (userCategories.isEmpty()) {
            log.info("No user categories available — skipping AI categorization");
            return;
        }

        // Separate categories by type for targeted prompts
        List<Category> incomeCategories =
                userCategories.stream()
                        .filter(c -> c.getType() == CategoryType.INCOME)
                        .collect(Collectors.toList());
        List<Category> expenseCategories =
                userCategories.stream()
                        .filter(c -> c.getType() == CategoryType.EXPENSE)
                        .collect(Collectors.toList());

        // Process in batches with a total time budget
        long startTime = System.currentTimeMillis();
        int categorized = 0;
        for (int batchStart = 0;
                batchStart < uncategorizedIndices.size();
                batchStart += BATCH_SIZE) {
            if (System.currentTimeMillis() - startTime > TOTAL_BUDGET_MS) {
                log.info(
                        "AI categorization time budget exhausted after {} items categorized",
                        categorized);
                break;
            }

            int batchEnd = Math.min(batchStart + BATCH_SIZE, uncategorizedIndices.size());
            List<Integer> batchIndices = uncategorizedIndices.subList(batchStart, batchEnd);

            try {
                categorized +=
                        processBatch(
                                transactions, batchIndices, incomeCategories, expenseCategories);
            } catch (Exception e) {
                log.warn(
                        "AI categorization batch failed (items {}-{}): {}",
                        batchStart,
                        batchEnd - 1,
                        e.getMessage());
            }
        }
        log.info(
                "AI categorization completed: {}/{} transactions categorized in {} ms",
                categorized,
                uncategorizedIndices.size(),
                System.currentTimeMillis() - startTime);
    }

    private int processBatch(
            List<ImportedTransaction> transactions,
            List<Integer> indices,
            List<Category> incomeCategories,
            List<Category> expenseCategories) {

        int batchSize = indices.size();
        Locale locale = LocaleContextHolder.getLocale();

        // Build category lists using TRANSLATED names (matching what the frontend
        // displays)
        // Separate by type so the model knows which categories apply to income vs
        // expense
        Map<String, String> displayNameMap = new HashMap<>();
        List<String> incomeCategoryNames = new ArrayList<>();
        List<String> expenseCategoryNames = new ArrayList<>();

        for (Category cat : incomeCategories) {
            String displayName = resolveDisplayName(cat, locale);
            if (cat.getParentId() != null) {
                incomeCategoryNames.add(displayName);
                displayNameMap.put(displayName.toLowerCase().trim(), displayName);
            }
        }
        for (Category cat : expenseCategories) {
            String displayName = resolveDisplayName(cat, locale);
            if (cat.getParentId() != null) {
                expenseCategoryNames.add(displayName);
                displayNameMap.put(displayName.toLowerCase().trim(), displayName);
            }
        }
        // Add parent categories that have no children as fallback
        for (Category parent : incomeCategories) {
            if (parent.getParentId() == null) {
                boolean hasChildren =
                        incomeCategories.stream()
                                .anyMatch(child -> parent.getId().equals(child.getParentId()));
                if (!hasChildren) {
                    String displayName = resolveDisplayName(parent, locale);
                    incomeCategoryNames.add(displayName);
                    displayNameMap.put(displayName.toLowerCase().trim(), displayName);
                }
            }
        }
        for (Category parent : expenseCategories) {
            if (parent.getParentId() == null) {
                boolean hasChildren =
                        expenseCategories.stream()
                                .anyMatch(child -> parent.getId().equals(child.getParentId()));
                if (!hasChildren) {
                    String displayName = resolveDisplayName(parent, locale);
                    expenseCategoryNames.add(displayName);
                    displayNameMap.put(displayName.toLowerCase().trim(), displayName);
                }
            }
        }

        // Build prompt with category type sections and transaction amounts
        StringBuilder prompt = new StringBuilder();
        prompt.append(
                "Categorize each transaction using ONLY a category name from the lists below.\n");
        prompt.append(
                "If amount is positive, pick from [REVENUS]. If amount is negative, pick from [DEPENSES].\n");
        prompt.append("Reply with ONE JSON array containing ALL transactions:\n");
        prompt.append("[{\"index\":1,\"category\":\"")
                .append(expenseCategoryNames.size() > 0 ? expenseCategoryNames.get(0) : "Groceries")
                .append("\"},{\"index\":2,\"category\":\"")
                .append(incomeCategoryNames.size() > 0 ? incomeCategoryNames.get(0) : "Salary")
                .append("\"}]\n\n");

        prompt.append("[REVENUS]: ").append(String.join(", ", incomeCategoryNames));
        prompt.append("\n[DEPENSES]: ").append(String.join(", ", expenseCategoryNames));
        prompt.append("\n\nTransactions:\n");

        for (int i = 0; i < batchSize; i++) {
            ImportedTransaction tx = transactions.get(indices.get(i));
            String payee = tx.getPayee() != null ? tx.getPayee() : "Unknown";
            String memo = tx.getMemo() != null ? tx.getMemo() : "";
            BigDecimal amount = tx.getAmount() != null ? tx.getAmount() : BigDecimal.ZERO;
            prompt.append(
                    String.format("%d. %s %s [%s]\n", i + 1, payee, memo, amount.toPlainString()));
        }

        prompt.append("\nJSON:");

        // Call AI with timeout
        String response = aiProvider.sendPrompt(prompt.toString(), "").block(BATCH_TIMEOUT);

        if (response == null || response.isBlank()) {
            log.warn("AI returned empty response for categorization batch");
            return 0;
        }

        log.info("AI raw response: {}", response);

        // Parse the JSON response using the display name map
        return applyAIResults(transactions, indices, response, displayNameMap);
    }

    /**
     * Resolve the display name for a category in the given locale. System categories use their
     * nameKey for i18n; user categories use their stored name.
     */
    private String resolveDisplayName(Category category, Locale locale) {
        if (Boolean.TRUE.equals(category.getIsSystem())
                && category.getNameKey() != null
                && !category.getNameKey().isBlank()) {
            return messageSource.getMessage(
                    category.getNameKey(), null, category.getName(), locale);
        }
        return category.getName();
    }

    private int applyAIResults(
            List<ImportedTransaction> transactions,
            List<Integer> indices,
            String response,
            Map<String, String> displayNameMap) {

        int count = 0;
        try {
            // Extract JSON array from response (handle markdown fencing)
            String json = extractJson(response);

            // Try parsing as [{index, category}] objects first
            count = parseAsObjectArray(json, transactions, indices, displayNameMap);
            if (count > 0) {
                return count;
            }

            // Fallback: try parsing as plain string array ["Groceries", "Salary", ...]
            count = parseAsStringArray(json, transactions, indices, displayNameMap);
        } catch (Exception e) {
            log.warn("Failed to parse AI categorization response: {}", e.getMessage());
        }
        return count;
    }

    private int parseAsObjectArray(
            String json,
            List<ImportedTransaction> transactions,
            List<Integer> indices,
            Map<String, String> displayNameMap) {
        try {
            List<Map<String, Object>> results =
                    objectMapper.readValue(json, new TypeReference<List<Map<String, Object>>>() {});

            int count = 0;
            for (Map<String, Object> result : results) {
                Object indexObj = result.get("index");
                Object categoryObj = result.get("category");

                if (indexObj == null || categoryObj == null) {
                    continue;
                }

                int idx;
                try {
                    idx =
                            (indexObj instanceof Number)
                                    ? ((Number) indexObj).intValue() - 1
                                    : Integer.parseInt(indexObj.toString().trim()) - 1;
                } catch (NumberFormatException e) {
                    continue;
                }

                if (idx < 0 || idx >= indices.size()) {
                    continue;
                }

                String categoryName = categoryObj.toString().trim();
                String matchedDisplayName = displayNameMap.get(categoryName.toLowerCase());

                if (matchedDisplayName != null) {
                    int txIndex = indices.get(idx);
                    ImportedTransaction tx = transactions.get(txIndex);
                    tx.setCategory(matchedDisplayName);
                    tx.setCategorizationConfidence(AI_CONFIDENCE);
                    tx.addValidationError("AI_MATCH: Category assigned by AI");
                    count++;
                } else {
                    log.info("AI suggested '{}' — no match (index {})", categoryName, idx + 1);
                }
            }
            return count;
        } catch (Exception e) {
            // Not a valid object array — let caller try string array
            return 0;
        }
    }

    private int parseAsStringArray(
            String json,
            List<ImportedTransaction> transactions,
            List<Integer> indices,
            Map<String, String> displayNameMap) {
        try {
            List<String> categoryNames =
                    objectMapper.readValue(json, new TypeReference<List<String>>() {});

            int count = 0;
            for (int i = 0; i < categoryNames.size() && i < indices.size(); i++) {
                String categoryName = categoryNames.get(i).trim();
                String matchedDisplayName = displayNameMap.get(categoryName.toLowerCase());

                if (matchedDisplayName != null) {
                    int txIndex = indices.get(i);
                    ImportedTransaction tx = transactions.get(txIndex);
                    tx.setCategory(matchedDisplayName);
                    tx.setCategorizationConfidence(AI_CONFIDENCE);
                    tx.addValidationError("AI_MATCH: Category assigned by AI");
                    count++;
                } else {
                    log.info("AI suggested '{}' — no match (position {})", categoryName, i + 1);
                }
            }
            log.info(
                    "Parsed AI response as positional string array: {}/{} matched",
                    count,
                    categoryNames.size());
            return count;
        } catch (Exception e) {
            log.warn("Could not parse AI response as string array either: {}", e.getMessage());
            return 0;
        }
    }

    /**
     * Extract JSON array from AI response, stripping markdown fencing if present. Handles multiple
     * formats: - Single array: [{...}, {...}] - One array per line: [{...}]\n[{...}]\n... - Single
     * object: {...} - Markdown-fenced code blocks
     */
    private String extractJson(String response) {
        String trimmed = response.trim();

        // Strip markdown code fencing (```json ... ``` or ``` ... ```)
        if (trimmed.startsWith("```")) {
            int firstNewline = trimmed.indexOf('\n');
            int lastFence = trimmed.lastIndexOf("```");
            if (firstNewline > 0 && lastFence > firstNewline) {
                trimmed = trimmed.substring(firstNewline + 1, lastFence).trim();
            }
        }

        // Detect multiple JSON arrays on separate lines (e.g., [{...}]\n[{...}])
        // and merge them into a single array
        if (trimmed.contains("]\n[") || trimmed.contains("]\r\n[")) {
            StringBuilder merged = new StringBuilder("[");
            String[] lines = trimmed.split("\\r?\\n");
            boolean first = true;
            for (String line : lines) {
                line = line.trim();
                if (line.isEmpty()) continue;
                // Extract content between [ and ] for each line-array
                int ls = line.indexOf('[');
                int le = line.lastIndexOf(']');
                if (ls >= 0 && le > ls) {
                    String inner = line.substring(ls + 1, le).trim();
                    if (!inner.isEmpty()) {
                        if (!first) merged.append(",");
                        merged.append(inner);
                        first = false;
                    }
                }
            }
            merged.append("]");
            // Fix trailing double-brace typos like "}} → "}
            String result = merged.toString().replaceAll("\\}\\}", "}");
            return result;
        }

        // Find the first '[' and last ']' to extract the array
        int start = trimmed.indexOf('[');
        int end = trimmed.lastIndexOf(']');
        if (start >= 0 && end > start) {
            String extracted = trimmed.substring(start, end + 1);
            // Repair missing closing bracket: if it ends with } but no ]
            // this shouldn't happen here since we found ], but fix trailing }}
            extracted = extracted.replaceAll("\\}\\}", "}");
            return extracted;
        }

        // Handle missing ']' — AI cut off mid-response
        int startBracket = trimmed.indexOf('[');
        if (startBracket >= 0) {
            String partial = trimmed.substring(startBracket);
            // Try to repair: add missing ]
            if (partial.matches(".*\\}\\s*$")) {
                return partial + "]";
            }
        }

        // Handle single JSON object (AI returned {...} instead of [...])
        int objStart = trimmed.indexOf('{');
        int objEnd = trimmed.lastIndexOf('}');
        if (objStart >= 0 && objEnd > objStart) {
            return "[" + trimmed.substring(objStart, objEnd + 1) + "]";
        }

        return trimmed;
    }

    private List<ImportedTransaction> deserializeTransactions(String metadata) {
        if (metadata == null || metadata.trim().isEmpty()) {
            return new ArrayList<>();
        }
        try {
            Map<String, Object> metadataMap =
                    objectMapper.readValue(metadata, new TypeReference<Map<String, Object>>() {});
            if (!metadataMap.containsKey("transactions")) {
                return new ArrayList<>();
            }
            return objectMapper.convertValue(
                    metadataMap.get("transactions"),
                    new TypeReference<List<ImportedTransaction>>() {});
        } catch (JsonProcessingException e) {
            log.error("Error deserializing transactions for AI categorization: {}", e.getMessage());
            return new ArrayList<>();
        }
    }

    private String serializeTransactions(
            List<ImportedTransaction> transactions, BigDecimal ledgerBalance, String fileCurrency) {
        try {
            Map<String, Object> metadata = new HashMap<>();
            metadata.put("transactions", transactions);
            metadata.put("count", transactions.size());
            metadata.put("ledgerBalance", ledgerBalance);
            metadata.put("fileCurrency", fileCurrency);
            metadata.put("timestamp", LocalDateTime.now().toString());
            return objectMapper.writeValueAsString(metadata);
        } catch (JsonProcessingException e) {
            log.error("Error serializing transactions after AI categorization: {}", e.getMessage());
            return null;
        }
    }
}
