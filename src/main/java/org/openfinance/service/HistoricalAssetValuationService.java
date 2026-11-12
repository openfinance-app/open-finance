package org.openfinance.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.openfinance.entity.Asset;
import org.openfinance.entity.Transaction;

public interface HistoricalAssetValuationService {
    /** Load missing quotes before checking or rebuilding cached snapshots. */
    void preparePrices(Asset asset, LocalDate from, LocalDate to);

    BigDecimal valueAt(
            Asset asset, List<Transaction> transactions, LocalDate date, String currency);
}
