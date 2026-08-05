package org.openfinance.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.openfinance.dto.TransactionSplitRequest;
import org.openfinance.dto.TransactionSplitResponse;
import org.openfinance.entity.Category;
import org.openfinance.entity.TransactionSplit;
import org.openfinance.entity.TransactionType;
import org.openfinance.exception.InvalidTransactionException;
import org.openfinance.repository.CategoryRepository;
import org.openfinance.repository.TransactionSplitRepository;
import org.openfinance.security.EncryptionService;
import org.openfinance.util.MoneyAllocation;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

/**
 * Service responsible for managing transaction split lines.
 *
 * <p>Splits allow a single transaction to be categorized across multiple categories, each with its
 * own amount. This service handles:
 *
 * <ul>
 *   <li>Validating that split amounts sum to the parent transaction total
 *   <li>Persisting (create / replace) split lines with encrypted descriptions
 *   <li>Fetching and decrypting split lines for response building
 *   <li>Explicit deletion of splits during transaction updates
 * </ul>
 *
 * <p><strong>Security:</strong> Split descriptions are encrypted with the same AES-256-GCM scheme
 * used for transaction descriptions and notes.
 *
 * <p>Requirement REQ-SPL-1.2: Sum validation (must sum exactly)
 *
 * <p>Requirement REQ-SPL-1.5: Splits only valid for INCOME/EXPENSE
 *
 * <p>Requirement REQ-SPL-2.6: Validate split amounts server-side
 *
 * <p>Requirement REQ-SPL-2.7: Delete all splits when parent is deleted
 *
 * @see TransactionSplit
 * @see TransactionSplitRepository
 */
@Service
@Transactional
@RequiredArgsConstructor
@Slf4j
public class TransactionSplitService {

    private final TransactionSplitRepository splitRepository;
    private final CategoryRepository categoryRepository;
    private final EncryptionService encryptionService;
    private final CurrencyTypeResolver currencyTypeResolver;

    // -----------------------------------------------------------------------
    // Validation
    // -----------------------------------------------------------------------

    /**
     * Validates that the provided split request list is consistent with the parent transaction.
     *
     * <p>Rules checked:
     *
     * <ol>
     *   <li>Splits are only allowed for INCOME and EXPENSE transactions.
     *   <li>If splits are provided, there must be at least 2 entries.
     *   <li>The sum of all split amounts must equal {@code totalAmount} exactly.
     * </ol>
     *
     * @param totalAmount the parent transaction amount
     * @param transactionType the type of the parent transaction
     * @param splits the list of split requests (may be null/empty)
     * @throws InvalidTransactionException if any validation rule is violated
     *     <p>Requirement REQ-SPL-1.2: Sum must equal parent amount exactly
     *     <p>Requirement REQ-SPL-1.5: Splits only for INCOME/EXPENSE
     *     <p>Requirement REQ-SPL-2.6: Server-side amount validation
     */
    public void validateSplits(
            BigDecimal totalAmount,
            TransactionType transactionType,
            List<TransactionSplitRequest> splits) {

        if (CollectionUtils.isEmpty(splits)) {
            return; // No splits — nothing to validate
        }

        // REQ-SPL-1.5: Splits are only valid for INCOME and EXPENSE
        if (transactionType == TransactionType.TRANSFER) {
            throw new InvalidTransactionException(
                    "Split transactions are not supported for TRANSFER type transactions");
        }

        // At least 2 splits make sense (1 split is pointless)
        if (splits.size() < 2) {
            throw new InvalidTransactionException(
                    "A split transaction must have at least 2 split entries");
        }

        // REQ-SPL-1.2 / REQ-SPL-2.6: sum check
        BigDecimal splitSum =
                splits.stream()
                        .map(TransactionSplitRequest::getAmount)
                        .reduce(BigDecimal.ZERO, BigDecimal::add)
                        .setScale(4, RoundingMode.HALF_UP);

        BigDecimal expected = totalAmount.setScale(4, RoundingMode.HALF_UP);
        BigDecimal difference = expected.subtract(splitSum).abs();

        if (difference.compareTo(BigDecimal.ZERO) != 0) {
            throw new InvalidTransactionException(
                    String.format(
                            "Split amounts must sum exactly to the transaction amount: "
                                    + "splits sum to %s but the transaction amount is %s "
                                    + "(difference %s)",
                            splitSum.stripTrailingZeros().toPlainString(),
                            expected.stripTrailingZeros().toPlainString(),
                            difference.stripTrailingZeros().toPlainString()));
        }

        log.debug(
                "Split validation passed: {} splits, sum={}, expected={}",
                splits.size(),
                splitSum,
                expected);
    }

    /**
     * Reconciles imported split amounts so they sum EXACTLY to {@code total}, absorbing legitimate
     * per-line rounding residue via {@link MoneyAllocation}. Differences too large to be rounding
     * (beyond one minor unit per line) throw {@link InvalidTransactionException}.
     *
     * @param total the parent transaction amount (authoritative)
     * @param currency ISO 4217 code of the transaction, used to pick the minor-unit scale
     * @param splits the imported split requests; each split amount must be non-null
     * @return new split requests with reconciled amounts (category/description preserved)
     */
    public List<TransactionSplitRequest> reconcileForImport(
            BigDecimal total, String currency, List<TransactionSplitRequest> splits) {
        if (CollectionUtils.isEmpty(splits)) {
            return splits == null ? List.of() : splits;
        }
        int scale = Math.min(currencyTypeResolver.decimalsFor(currency), 4);
        // A transaction amount with finer precision than the currency's minor unit cannot be split
        // into exactly-summing minor-unit lines; reject it explicitly so a successful reconcile
        // always satisfies the exact validateSplits check.
        if (total.stripTrailingZeros().scale() > scale) {
            throw new InvalidTransactionException(
                    String.format(
                            "Cannot reconcile split amounts: transaction amount %s has finer "
                                    + "precision than the %s minor unit (scale %d)",
                            total.toPlainString(), currency, scale));
        }
        List<BigDecimal> amounts =
                splits.stream()
                        .map(TransactionSplitRequest::getAmount)
                        .collect(Collectors.toList());
        MoneyAllocation.ReconcileResult result = MoneyAllocation.reconcile(total, amounts, scale);
        if (result.grossMismatch()) {
            throw new InvalidTransactionException(
                    String.format(
                            "Imported split amounts do not sum to the transaction amount %s "
                                    + "(currency %s)",
                            total, currency));
        }
        List<BigDecimal> fixed = result.parts();
        List<TransactionSplitRequest> out = new ArrayList<>(splits.size());
        for (int i = 0; i < splits.size(); i++) {
            TransactionSplitRequest src = splits.get(i);
            out.add(
                    TransactionSplitRequest.builder()
                            .categoryId(src.getCategoryId())
                            .amount(fixed.get(i))
                            .description(src.getDescription())
                            .build());
        }
        return out;
    }

    // -----------------------------------------------------------------------
    // Persistence
    // -----------------------------------------------------------------------

    /**
     * Persists (or replaces) all splits for a transaction.
     *
     * <p>Uses a delete-and-insert strategy: all existing splits for {@code transactionId} are
     * deleted first, then the new ones are inserted. This is safe because it happens within the
     * same database transaction.
     *
     * <p>If {@code splits} is null or empty, only the delete step is executed (all existing splits
     * are removed).
     *
     * @param transactionId the parent transaction ID
     * @param splits the new split list (may be null/empty to clear splits)
     * @param encryptionKey AES-256 key for encrypting descriptions
     *     <p>Requirement REQ-SPL-2.1: Save splits on create
     *     <p>Requirement REQ-SPL-2.2: Replace splits on update
     */
    public void saveSplits(Long transactionId, List<TransactionSplitRequest> splits) {
        // Delete existing splits first (replace semantics)
        splitRepository.deleteByTransactionId(transactionId);
        splitRepository.flush(); // Ensure deletes are flushed before inserts

        if (CollectionUtils.isEmpty(splits)) {
            log.debug("No splits to save for transaction {}", transactionId);
            return;
        }

        List<TransactionSplit> entities = new ArrayList<>(splits.size());
        for (TransactionSplitRequest req : splits) {
            TransactionSplit split =
                    TransactionSplit.builder()
                            .transactionId(transactionId)
                            .categoryId(req.getCategoryId())
                            .amount(req.getAmount())
                            .build();

            // Description set directly — JPA converter handles encryption
            if (req.getDescription() != null && !req.getDescription().isBlank()) {
                split.setDescription(req.getDescription());
            }

            entities.add(split);
        }

        splitRepository.saveAll(entities);
        log.debug("Saved {} splits for transaction {}", entities.size(), transactionId);
    }

    /**
     * Explicitly deletes all split lines for a given transaction.
     *
     * <p>This is primarily used as a service-layer operation during transaction soft-delete. The
     * database FK {@code ON DELETE CASCADE} also handles this automatically on hard-delete.
     *
     * @param transactionId the parent transaction ID
     *     <p>Requirement REQ-SPL-2.7: Delete all splits when parent is deleted
     */
    public void deleteSplitsForTransaction(Long transactionId) {
        splitRepository.deleteByTransactionId(transactionId);
        log.debug("Deleted all splits for transaction {}", transactionId);
    }

    // -----------------------------------------------------------------------
    // Retrieval
    // -----------------------------------------------------------------------

    /**
     * Fetches and decrypts all splits for a single transaction, building the full {@link
     * TransactionSplitResponse} list with denormalized category data.
     *
     * @param transactionId the parent transaction ID
     * @param encryptionKey AES-256 key for decrypting descriptions
     * @return ordered list of decrypted split responses (may be empty)
     *     <p>Requirement REQ-SPL-2.3: Return splits in single-transaction GET
     *     <p>Requirement REQ-SPL-2.5: Dedicated endpoint retrieval
     */
    @Transactional(readOnly = true)
    public List<TransactionSplitResponse> getSplitsForTransaction(Long transactionId) {
        List<TransactionSplit> splits = splitRepository.findByTransactionIdOrderById(transactionId);
        return splits.stream().map(split -> toResponse(split)).collect(Collectors.toList());
    }

    /**
     * Fetches and decrypts splits for a batch of transaction IDs, grouped by transaction ID.
     *
     * <p>Used when building list/search responses to avoid N+1 query issues.
     *
     * @param transactionIds list of parent transaction IDs
     * @param encryptionKey AES-256 key for decrypting descriptions
     * @return map from transactionId to its list of split responses
     *     <p>Requirement REQ-SPL-2.4: Include splits in list responses
     */
    @Transactional(readOnly = true)
    public Map<Long, List<TransactionSplitResponse>> getSplitsForTransactions(
            List<Long> transactionIds) {

        if (CollectionUtils.isEmpty(transactionIds)) {
            return Map.of();
        }

        List<TransactionSplit> allSplits = splitRepository.findByTransactionIdIn(transactionIds);
        return allSplits.stream()
                .collect(
                        Collectors.groupingBy(
                                TransactionSplit::getTransactionId,
                                Collectors.mapping(
                                        split -> toResponse(split), Collectors.toList())));
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    /**
     * Converts a {@link TransactionSplit} entity to a {@link TransactionSplitResponse}, decrypting
     * the description and denormalizing category metadata.
     *
     * @param split the entity to convert
     * @param encryptionKey AES-256 key for decryption
     * @return the populated response DTO
     */
    private TransactionSplitResponse toResponse(TransactionSplit split) {
        TransactionSplitResponse.TransactionSplitResponseBuilder builder =
                TransactionSplitResponse.builder()
                        .id(split.getId())
                        .transactionId(split.getTransactionId())
                        .categoryId(split.getCategoryId())
                        .amount(split.getAmount());

        // Description already decrypted by JPA converter
        if (split.getDescription() != null && !split.getDescription().isBlank()) {
            builder.description(split.getDescription());
        }

        // Denormalize category metadata via lazy-loaded relationship
        Category category = split.getCategory();
        if (category != null) {
            builder.categoryName(category.getName())
                    .categoryIcon(category.getIcon())
                    .categoryColor(category.getColor());
        } else if (split.getCategoryId() != null) {
            // Relationship not loaded — fall back to a direct repository look-up
            categoryRepository
                    .findById(split.getCategoryId())
                    .ifPresent(
                            cat -> {
                                builder.categoryName(cat.getName())
                                        .categoryIcon(cat.getIcon())
                                        .categoryColor(cat.getColor());
                            });
        }

        return builder.build();
    }
}
