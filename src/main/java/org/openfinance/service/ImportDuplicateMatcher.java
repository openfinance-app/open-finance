package org.openfinance.service;

import java.math.BigDecimal;
import lombok.extern.slf4j.Slf4j;
import org.openfinance.dto.ImportedTransaction;
import org.openfinance.entity.Transaction;
import org.openfinance.entity.TransactionType;

/** Duplicate identity within an account, including original and legacy converted amounts. */
@Slf4j
public final class ImportDuplicateMatcher {
    private ImportDuplicateMatcher() {}

    public static boolean matches(
            ImportedTransaction imported,
            Transaction existing,
            boolean useReferences,
            ExchangeRateService exchangeRateService) {
        String reference = imported.getReferenceNumber();
        if (useReferences
                && reference != null
                && !reference.isBlank()
                && existing.getExternalReference() != null
                && !existing.getExternalReference().isBlank()) {
            // Different bank-issued IDs are different movements, even with identical
            // amounts/payees.
            return reference.equals(existing.getExternalReference());
        }
        if (existing.getType() != TransactionType.INCOME
                && existing.getType() != TransactionType.EXPENSE) {
            return false;
        }
        if ((imported.getAmount().signum() >= 0)
                != (existing.getType() == TransactionType.INCOME)) {
            return false;
        }
        if (Math.abs(existing.getDate().toEpochDay() - imported.getTransactionDate().toEpochDay())
                        > 1
                || !matchesDuplicateAmount(imported, existing, exchangeRateService)) {
            return false;
        }
        String payee =
                imported.getOriginalPayee() != null
                        ? imported.getOriginalPayee()
                        : imported.getPayee();
        return isPayeeSimilar(payee, existing.getDescription());
    }

    private static boolean matchesDuplicateAmount(
            ImportedTransaction imported,
            Transaction existing,
            ExchangeRateService exchangeRateService) {
        String currency = imported.getCurrency();
        BigDecimal amount = imported.getAmount().abs();
        if (currency != null
                && currency.equalsIgnoreCase(existing.getOriginalCurrency())
                && existing.getOriginalAmount() != null) {
            return amount.compareTo(existing.getOriginalAmount().abs()) == 0;
        }
        if (currency == null
                || currency.isBlank()
                || existing.getCurrency() == null
                || currency.equalsIgnoreCase(existing.getCurrency())) {
            return amount.compareTo(existing.getAmount().abs()) == 0;
        }
        // Legacy imports did not retain their source amount. Compare the historical
        // conversion in the booked currency; never equate unlike currency amounts.
        try {
            BigDecimal converted =
                    exchangeRateService.convert(
                            amount,
                            currency,
                            existing.getCurrency(),
                            imported.getTransactionDate());
            return converted.compareTo(existing.getAmount().abs()) == 0;
        } catch (RuntimeException ex) {
            log.debug("Cannot compare legacy foreign-currency import: {}", ex.getMessage());
            return false;
        }
    }

    /**
     * Check if two payee strings are similar using Levenshtein distance algorithm. Considers payees
     * similar if they have 85%+ similarity ratio.
     *
     * @param payee1 first payee string
     * @param payee2 second payee string
     * @return true if payees are similar (85%+ match), false otherwise
     *     <p>Requirement: REQ-2.10.4 (Duplicate transaction detection)
     */
    private static boolean isPayeeSimilar(String payee1, String payee2) {
        boolean empty1 = payee1 == null || payee1.isBlank();
        boolean empty2 = payee2 == null || payee2.isBlank();
        if (empty1 && empty2) {
            return true; // Both missing ΓÇö treat as same unknown payee
        }
        if (empty1 || empty2) {
            return false;
        }

        // Normalize: lowercase, trim, remove extra spaces
        String normalized1 =
                payee1.toLowerCase(java.util.Locale.ROOT).trim().replaceAll("\\s+", " ");
        String normalized2 =
                payee2.toLowerCase(java.util.Locale.ROOT).trim().replaceAll("\\s+", " ");

        // Exact match
        if (normalized1.equals(normalized2)) {
            return true;
        }

        // Contains match (one string contains the other)
        if (normalized1.contains(normalized2) || normalized2.contains(normalized1)) {
            return true;
        }

        // Levenshtein distance similarity (85%+ threshold)
        double similarity = calculateStringSimilarity(normalized1, normalized2);
        return similarity >= 0.85;
    }

    /**
     * Calculate similarity between two strings using Levenshtein distance. Returns similarity ratio
     * from 0.0 (completely different) to 1.0 (identical).
     *
     * @param s1 first string
     * @param s2 second string
     * @return similarity ratio (0.0 to 1.0)
     */
    static double calculateStringSimilarity(String s1, String s2) {
        if (s1 == null || s2 == null) {
            return 0.0;
        }

        // Normalize strings
        String normalized1 = s1.toLowerCase(java.util.Locale.ROOT).trim();
        String normalized2 = s2.toLowerCase(java.util.Locale.ROOT).trim();

        if (normalized1.equals(normalized2)) {
            return 1.0;
        }

        // Calculate Levenshtein distance
        int distance = levenshteinDistance(normalized1, normalized2);
        int maxLength = Math.max(normalized1.length(), normalized2.length());

        if (maxLength == 0) {
            return 1.0;
        }

        return 1.0 - ((double) distance / maxLength);
    }

    /**
     * Calculate Levenshtein distance between two strings. The Levenshtein distance is the minimum
     * number of single-character edits (insertions, deletions, or substitutions) required to change
     * one string into the other.
     *
     * @param s1 first string
     * @param s2 second string
     * @return the Levenshtein distance
     */
    private static int levenshteinDistance(String s1, String s2) {
        int len1 = s1.length();
        int len2 = s2.length();

        // Create DP table
        int[][] dp = new int[len1 + 1][len2 + 1];

        // Initialize base cases
        for (int i = 0; i <= len1; i++) {
            dp[i][0] = i;
        }
        for (int j = 0; j <= len2; j++) {
            dp[0][j] = j;
        }

        // Fill DP table
        for (int i = 1; i <= len1; i++) {
            for (int j = 1; j <= len2; j++) {
                if (s1.charAt(i - 1) == s2.charAt(j - 1)) {
                    dp[i][j] = dp[i - 1][j - 1];
                } else {
                    dp[i][j] = 1 + Math.min(Math.min(dp[i - 1][j], dp[i][j - 1]), dp[i - 1][j - 1]);
                }
            }
        }

        return dp[len1][len2];
    }
}
