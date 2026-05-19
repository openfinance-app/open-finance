package org.openfinance.service;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.openfinance.dto.ImportedTransaction;
import org.openfinance.entity.Category;
import org.openfinance.entity.Transaction;
import org.openfinance.repository.CategoryRepository;
import org.openfinance.repository.TransactionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service for automatically categorizing imported transactions and assigning payees based on the
 * user's transaction history using a token-based similarity approach.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AutoCategorizationService {

    private final TransactionRepository transactionRepository;
    private final CategoryRepository categoryRepository;

    // Minimum similarity score (Jaccard index) to accept a historical match
    // automatically
    private static final double SIMILARITY_THRESHOLD = 0.4;

    /** Prediction result containing suggested category and payee. */
    public record Prediction(
            String suggestedCategoryName, String suggestedPayee, double confidenceScore) {}

    /**
     * Predicts the category and payee for a given imported transaction based on user's past
     * transactions.
     *
     * @param importedTx The imported transaction to analyze
     * @param userId The ID of the user
     * @return Optional Prediction if a confident match is found
     */
    @Transactional(readOnly = true)
    public Optional<Prediction> predictCategoryAndPayee(
            ImportedTransaction importedTx, Long userId) {
        // Build analysis string from imported transaction (payee + memo)
        String analysisString = buildAnalysisString(importedTx.getPayee(), importedTx.getMemo());
        if (analysisString.trim().isEmpty()) {
            return Optional.empty();
        }

        Set<String> importTokens = tokenize(analysisString);
        if (importTokens.isEmpty()) {
            return Optional.empty();
        }

        // Fetch user's active transactions (we can limit this in the future if the
        // history gets too large)
        // By default findByUserId returns ordered by date DESC, so we get the most
        // recent ones which are most relevant
        List<Transaction> historicalTransactions =
                transactionRepository.findByUserId(userId).stream()
                        .filter(
                                tx ->
                                        tx.getCategoryId() != null
                                                && tx.getPayee() != null
                                                && !tx.getPayee().trim().isEmpty())
                        .limit(2000) // Limit to the last 2000 categorized transactions for
                        // performance
                        .collect(Collectors.toList());

        if (historicalTransactions.isEmpty()) {
            return Optional.empty();
        }

        double bestScore = 0.0;
        Transaction bestMatch = null;

        for (Transaction historicalTx : historicalTransactions) {
            String historicalAnalysisString =
                    buildAnalysisString(historicalTx.getPayee(), historicalTx.getDescription());
            Set<String> historicalTokens = tokenize(historicalAnalysisString);

            double score = calculateJaccardSimilarity(importTokens, historicalTokens);
            if (score > bestScore) {
                bestScore = score;
                bestMatch = historicalTx;
            }
        }

        if (bestMatch != null && bestScore >= SIMILARITY_THRESHOLD) {
            Category category = categoryRepository.findById(bestMatch.getCategoryId()).orElse(null);
            if (category != null) {
                log.debug(
                        "Found historical match for '{}' -> Score: {}, Category: {}, Payee: {}",
                        analysisString,
                        bestScore,
                        category.getName(),
                        bestMatch.getPayee());
                return Optional.of(
                        new Prediction(category.getName(), bestMatch.getPayee(), bestScore));
            }
        }

        return Optional.empty();
    }

    /** Builds a single string for analysis by combining payee and memo/description. */
    private String buildAnalysisString(String payee, String memo) {
        StringBuilder sb = new StringBuilder();
        if (payee != null) {
            sb.append(payee).append(" ");
        }
        if (memo != null) {
            sb.append(memo);
        }
        return sb.toString().trim();
    }

    /** Tokenizes a string into lowercase alphanumeric tokens. */
    private Set<String> tokenize(String text) {
        if (text == null) {
            return Collections.emptySet();
        }
        // Replace non-alphanumeric with spaces and split
        String[] rawTokens = text.toLowerCase().replaceAll("[^a-z0-9]", " ").split("\\s+");
        Set<String> tokens = new HashSet<>();
        for (String t : rawTokens) {
            if (t.length() > 1) { // Ignore single-character noise like random letters
                tokens.add(t);
            }
        }
        return tokens;
    }

    /**
     * Calculates the Jaccard similarity index between two sets of tokens. Jaccard Index =
     * (Intersection size) / (Union size)
     */
    private double calculateJaccardSimilarity(Set<String> set1, Set<String> set2) {
        if (set1.isEmpty() && set2.isEmpty()) return 1.0;
        if (set1.isEmpty() || set2.isEmpty()) return 0.0;

        Set<String> intersection = new HashSet<>(set1);
        intersection.retainAll(set2);

        Set<String> union = new HashSet<>(set1);
        union.addAll(set2);

        return (double) intersection.size() / union.size();
    }
}
