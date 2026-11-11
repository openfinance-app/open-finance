package org.openfinance.service;

import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import org.openfinance.dto.ImportedTransaction;
import org.openfinance.entity.ImportSession;
import org.openfinance.entity.Transaction;

/** Classifies and persists imported transfers within the owning import transaction. */
public interface ImportedTransferService {
    void resolveOfxTransfers(ImportSession session, List<ImportedTransaction> rows);

    /**
     * Accounts are already authorized by the import pipeline. The converter retains the shared
     * source-row mapping used for ordinary transactions, including currency and payee resolution.
     */
    void save(
            List<ImportedTransaction> rows,
            Map<String, Long> accounts,
            Long fallback,
            Long userId,
            BiFunction<ImportedTransaction, Long, Transaction> converter);
}
