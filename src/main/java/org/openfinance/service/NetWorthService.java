package org.openfinance.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.openfinance.entity.Account;
import org.openfinance.entity.Asset;
import org.openfinance.entity.Liability;
import org.openfinance.entity.NetWorth;
import org.openfinance.entity.RealEstateProperty;
import org.openfinance.entity.RealEstateValueHistory;
import org.openfinance.entity.Transaction;
import org.openfinance.entity.TransactionType;
import org.openfinance.repository.AccountRepository;
import org.openfinance.repository.AssetRepository;
import org.openfinance.repository.CurrencyRepository;
import org.openfinance.repository.LiabilityRepository;
import org.openfinance.repository.NetWorthRepository;
import org.openfinance.repository.RealEstateValueHistoryRepository;
import org.openfinance.repository.TransactionRepository;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service for calculating and managing net worth snapshots.
 *
 * <p>This service handles:
 *
 * <ul>
 *   <li>Calculating total assets from account balances, Asset entities, and Real Estate properties
 *   <li>Calculating total liabilities from debt accounts and Liability entities
 *   <li>Creating daily net worth snapshots
 *   <li>Retrieving historical net worth data for trend analysis
 * </ul>
 *
 * <p><strong>Net Worth Calculation:</strong>
 *
 * <pre>
 * Net Worth = Total Assets - Total Liabilities
 *
 * Total Assets = Sum of positive account balances + Asset entity values + Real Estate property values
 * Total Liabilities = Absolute value of negative account balances + Liability entity balances
 * </pre>
 *
 * <p><strong>Multi-Currency Handling:</strong> This service supports multi-currency conversions
 * using exchange rates from {@link ExchangeRateService}. All amounts are converted to a specified
 * base currency (default: USD) before aggregation. If conversion fails, the original amount is used
 * with a warning logged.
 *
 * <p><strong>Encryption Note:</strong> Liability balances and real estate property values are
 * encrypted. During snapshot creation, the service requires an encryption key to decrypt these
 * values. Snapshots store the calculated totals unencrypted for historical tracking.
 *
 * <p>Requirements: REQ-2.5.1 (Net Worth Calculation), REQ-2.5.2 (Historical Tracking), REQ-6.1.7
 * (Include Liabilities), REQ-2.16.3 (Include Real Estate in Net Worth)
 *
 * @author Open-Finance Development Team
 * @since 1.0
 */
@Service
@Transactional
@RequiredArgsConstructor
@Slf4j
public class NetWorthService {

    private final NetWorthRepository netWorthRepository;
    private final AccountRepository accountRepository;
    private final CurrencyRepository currencyRepository;
    private final AssetRepository assetRepository;
    private final LiabilityRepository liabilityRepository;
    private final org.openfinance.repository.RealEstateRepository realEstateRepository;
    private final RealEstateValueHistoryRepository realEstateValueHistoryRepository;
    private final org.openfinance.security.EncryptionService encryptionService;
    private final ExchangeRateService exchangeRateService;
    private final TransactionRepository transactionRepository;

    /**
     * Calculates the current net worth for a user (backward compatibility - defaults to USD).
     *
     * <p>Calculation:
     *
     * <ol>
     *   <li>Sum all active account balances (assets)
     *   <li>Sum all liabilities (debt accounts only - Liability entities not included)
     *   <li>Net Worth = Assets - Liabilities
     * </ol>
     *
     * <p><strong>Note:</strong> This method does NOT include Liability entities. Use {@link
     * #calculateNetWorth(Long, LocalDate, SecretKey, String)} when encryption key is available.
     *
     * <p>Requirement REQ-2.5.1: Calculate net worth from accounts and assets
     *
     * @param userId the user ID
     * @param date the date for the calculation (typically today)
     * @return calculated net worth in USD
     */
    public BigDecimal calculateNetWorth(Long userId, LocalDate date) {
        return calculateNetWorth(userId, date, "USD");
    }

    /**
     * Calculates the current net worth for a user in a specified base currency.
     *
     * <p>Calculation:
     *
     * <ol>
     *   <li>Sum all active account balances (assets), converting to base currency
     *   <li>Sum all liabilities (debt accounts only - Liability entities not included)
     *   <li>Net Worth = Assets - Liabilities
     * </ol>
     *
     * <p><strong>Note:</strong> This method does NOT include Liability entities. Use {@link
     * #calculateNetWorth(Long, LocalDate, SecretKey, String)} when encryption key is available.
     *
     * <p>Requirement REQ-2.5.1: Calculate net worth from accounts and assets
     *
     * <p>Requirement REQ-6.2: Multi-currency support with exchange rate conversion
     *
     * @param userId the user ID
     * @param date the date for the calculation (typically today)
     * @param baseCurrency the base currency for net worth calculation (e.g., "USD", "EUR")
     * @return calculated net worth in the specified base currency
     */
    public BigDecimal calculateNetWorth(Long userId, LocalDate date, String baseCurrency) {
        if (userId == null) {
            throw new IllegalArgumentException("User ID cannot be null");
        }
        if (date == null) {
            throw new IllegalArgumentException("Date cannot be null");
        }
        if (baseCurrency == null || baseCurrency.isBlank()) {
            throw new IllegalArgumentException("Base currency cannot be null or blank");
        }

        log.debug("Calculating net worth for user {} on date {} in {}", userId, date, baseCurrency);

        BigDecimal totalAssets = calculateTotalAssets(userId, baseCurrency);
        BigDecimal totalLiabilities = calculateTotalLiabilities(userId, baseCurrency);

        // Net worth = assets - liabilities
        BigDecimal netWorth = totalAssets.subtract(totalLiabilities);

        log.debug(
                "Net worth calculated: totalAssets={} {}, totalLiabilities={} {}, netWorth={} {}",
                totalAssets,
                baseCurrency,
                totalLiabilities,
                baseCurrency,
                netWorth,
                baseCurrency);

        return netWorth;
    }

    /**
     * Calculates total assets for a user (defaults to USD).
     *
     * @param userId the user ID
     * @return total assets in USD
     */
    public BigDecimal calculateTotalAssets(Long userId) {
        return calculateTotalAssets(userId, "USD");
    }

    /**
     * Calculates total assets for a user in a specified base currency, including Real Estate
     * properties.
     *
     * <p>Includes:
     *
     * <ol>
     *   <li>Active account balances (positive balances only), converted to base currency
     *   <li>Active Real Estate property current values (decrypted using encryption key), converted
     *       to base currency
     * </ol>
     *
     * <p>Requirement REQ-2.16.3: Include real estate properties in net worth calculation
     *
     * <p>Requirement REQ-6.2: Multi-currency support with exchange rate conversion
     *
     * @param userId the user ID
     * @param baseCurrency the base currency for calculation (e.g., "USD", "EUR")
     * @return total assets in the specified base currency
     */
    public BigDecimal calculateTotalAssets(Long userId, String baseCurrency) {
        // Calculate account balances (already converted to base currency)
        List<Account> accounts = accountRepository.findByUserIdAndIsActive(userId, true);

        BigDecimal accountAssets =
                accounts.stream()
                        .filter(account -> account.getBalance().compareTo(BigDecimal.ZERO) > 0)
                        .map(
                                account ->
                                        convertToBaseCurrency(
                                                account.getBalance(),
                                                account.getCurrency(),
                                                baseCurrency))
                        .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal investmentAssets =
                assetRepository.findByUserId(userId).stream()
                        .filter(
                                asset ->
                                        asset.getType()
                                                != org.openfinance.entity.AssetType.REAL_ESTATE)
                        .map(
                                asset ->
                                        convertToBaseCurrency(
                                                asset.getTotalValue(),
                                                asset.getCurrency(),
                                                baseCurrency))
                        .reduce(BigDecimal.ZERO, BigDecimal::add);

        // Calculate real estate property values (requires decryption and currency
        // conversion)
        BigDecimal realEstateAssets =
                realEstateRepository.findByUserIdAndIsActive(userId, true).stream()
                        .map(
                                property -> {
                                    try {
                                        String currentValueStr = property.getCurrentValue();
                                        if (currentValueStr != null && !currentValueStr.isBlank()) {
                                            BigDecimal currentValue =
                                                    new BigDecimal(currentValueStr);
                                            return convertToBaseCurrency(
                                                    currentValue,
                                                    property.getCurrency(),
                                                    baseCurrency);
                                        }
                                        return BigDecimal.ZERO;
                                    } catch (Exception e) {
                                        log.error(
                                                "Failed to decrypt real estate property value for property ID {} (user {}): {}. Skipping property in net worth calculation.",
                                                property.getId(),
                                                userId,
                                                e.getMessage());
                                        return BigDecimal
                                                .ZERO; // Skip corrupted property rather than
                                        // failing entire calculation
                                    }
                                })
                        .reduce(BigDecimal.ZERO, BigDecimal::add);

        log.debug(
                "Total assets for user {}: accountAssets={} {}, investmentAssets={} {}, realEstateAssets={} {}, total={} {}",
                userId,
                accountAssets,
                baseCurrency,
                investmentAssets,
                baseCurrency,
                realEstateAssets,
                baseCurrency,
                accountAssets.add(investmentAssets).add(realEstateAssets),
                baseCurrency);

        return accountAssets.add(investmentAssets).add(realEstateAssets);
    }

    /**
     * Calculates total liabilities for a user (backward compatibility - defaults to USD).
     *
     * <p>Includes:
     *
     * <ol>
     *   <li>Negative account balances (e.g., credit card debt)
     *   <li>Liability entity balances
     * </ol>
     *
     * @param userId the user ID
     * @return total liabilities (positive number) in USD
     */
    public BigDecimal calculateTotalLiabilities(Long userId) {
        return calculateTotalLiabilities(userId, "USD");
    }

    /**
     * Calculates total liabilities for a user in a specified base currency.
     *
     * <p>Includes:
     *
     * <ol>
     *   <li>Negative account balances (e.g., credit card debt)
     *   <li>Liability entity balances (encrypted - requires encryption key)
     * </ol>
     *
     * <p>Requirement REQ-6.1.7: Include liabilities in net worth calculation
     *
     * <p>Requirement REQ-6.2: Multi-currency support with exchange rate conversion
     *
     * @param userId the user ID
     * @param baseCurrency the base currency for calculation (e.g., "USD", "EUR")
     * @return total liabilities (positive number) in the specified base currency
     */
    public BigDecimal calculateTotalLiabilities(Long userId, String baseCurrency) {
        List<Account> accounts = accountRepository.findByUserIdAndIsActive(userId, true);

        // Sum of negative account balances, converted to positive and to base currency
        BigDecimal negativeBalances =
                accounts.stream()
                        .filter(account -> account.getBalance().compareTo(BigDecimal.ZERO) < 0)
                        .map(
                                account ->
                                        convertToBaseCurrency(
                                                account.getBalance().abs(),
                                                account.getCurrency(),
                                                baseCurrency))
                        .reduce(BigDecimal.ZERO, BigDecimal::add);

        // Calculate liability entity balances (decrypted via JPA converters)
        BigDecimal liabilityDebts =
                liabilityRepository.findByUserIdOrderByCreatedAtDesc(userId).stream()
                        .map(
                                liability -> {
                                    String balanceStr = liability.getCurrentBalance();
                                    if (balanceStr != null && !balanceStr.isBlank()) {
                                        BigDecimal balance = new BigDecimal(balanceStr);
                                        return convertToBaseCurrency(
                                                balance, liability.getCurrency(), baseCurrency);
                                    }
                                    return BigDecimal.ZERO;
                                })
                        .reduce(BigDecimal.ZERO, BigDecimal::add);

        log.debug(
                "Total liabilities: negativeBalances={} {}, liabilityDebts={} {}, total={} {}",
                negativeBalances,
                baseCurrency,
                liabilityDebts,
                baseCurrency,
                negativeBalances.add(liabilityDebts),
                baseCurrency);

        return negativeBalances.add(liabilityDebts);
    }

    /**
     * Converts an amount from one currency to another using exchange rates.
     *
     * <p>If the currencies are the same, returns the original amount. If conversion fails, logs a
     * warning and returns the original amount as fallback.
     *
     * <p>Requirement REQ-6.2: Multi-currency support with exchange rate conversion
     *
     * @param amount the amount to convert
     * @param fromCurrency the source currency code (e.g., "EUR")
     * @param baseCurrency the target currency code (e.g., "USD")
     * @return the converted amount in base currency
     */
    private BigDecimal convertToBaseCurrency(
            BigDecimal amount, String fromCurrency, String baseCurrency) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }

        if (fromCurrency.equalsIgnoreCase(baseCurrency)) {
            return amount;
        }

        try {
            return exchangeRateService.convert(amount, fromCurrency, baseCurrency);
        } catch (Exception e) {
            log.warn(
                    "Failed to convert {} {} to {}: {}. Using original amount.",
                    amount,
                    fromCurrency,
                    baseCurrency,
                    e.getMessage());
            return amount; // Fallback: use unconverted amount
        }
    }

    /**
     * Saves a net worth snapshot for the current date (backward compatibility - defaults to USD).
     *
     * <p>If a snapshot already exists for today, it will be updated. Otherwise, a new snapshot is
     * created.
     *
     * <p>Requirement REQ-2.5.1: Save current net worth snapshot
     *
     * @param userId the user ID
     * @return the saved net worth snapshot
     */
    public NetWorth saveNetWorthSnapshot(Long userId) {
        return saveNetWorthSnapshot(userId, LocalDate.now(), "USD");
    }

    /**
     * Saves a net worth snapshot for a specific date (backward compatibility - defaults to USD).
     *
     * <p>If a snapshot already exists for the date, it will be updated. Otherwise, a new snapshot
     * is created.
     *
     * @param userId the user ID
     * @param date the snapshot date
     * @return the saved net worth snapshot
     */
    public NetWorth saveNetWorthSnapshot(Long userId, LocalDate date) {
        return saveNetWorthSnapshot(userId, date, "USD");
    }

    /**
     * Saves a net worth snapshot for a specific date in a specified base currency.
     *
     * <p>If a snapshot already exists for the date, it will be updated. Otherwise, a new snapshot
     * is created.
     *
     * <p>Requirement REQ-2.5.1: Save current net worth snapshot
     *
     * <p>Requirement REQ-6.2: Multi-currency support with exchange rate conversion
     *
     * @param userId the user ID
     * @param date the snapshot date
     * @param baseCurrency the base currency for the snapshot (e.g., "USD", "EUR")
     * @return the saved net worth snapshot
     */
    @CacheEvict(
            value = {"dashboardSummary", "netWorthSummary"},
            key = "#userId")
    public NetWorth saveNetWorthSnapshot(Long userId, LocalDate date, String baseCurrency) {
        if (userId == null) {
            throw new IllegalArgumentException("User ID cannot be null");
        }
        if (date == null) {
            throw new IllegalArgumentException("Date cannot be null");
        }
        if (baseCurrency == null || baseCurrency.isBlank()) {
            throw new IllegalArgumentException("Base currency cannot be null or blank");
        }

        log.debug(
                "Saving net worth snapshot for user {} on date {} in {}",
                userId,
                date,
                baseCurrency);

        BigDecimal totalAssets = calculateTotalAssets(userId, baseCurrency);
        BigDecimal totalLiabilities = calculateTotalLiabilities(userId, baseCurrency);
        BigDecimal netWorth = totalAssets.subtract(totalLiabilities);

        // Check if snapshot already exists for this date
        Optional<NetWorth> existingSnapshot =
                netWorthRepository.findByUserIdAndSnapshotDate(userId, date);

        Long currencyId = resolveCurrencyId(baseCurrency);

        NetWorth snapshot;
        if (existingSnapshot.isPresent()) {
            // Update existing snapshot
            snapshot = existingSnapshot.get();
            snapshot.setTotalAssets(totalAssets);
            snapshot.setTotalLiabilities(totalLiabilities);
            snapshot.setNetWorth(netWorth);
            snapshot.setCurrency(baseCurrency);
            snapshot.setCurrencyId(currencyId);
            log.debug("Updating existing net worth snapshot: id={}", snapshot.getId());
        } else {
            // Create new snapshot
            snapshot =
                    NetWorth.builder()
                            .userId(userId)
                            .snapshotDate(date)
                            .totalAssets(totalAssets)
                            .totalLiabilities(totalLiabilities)
                            .netWorth(netWorth)
                            .currency(baseCurrency)
                            .currencyId(currencyId)
                            .build();
            log.debug("Creating new net worth snapshot");
        }

        NetWorth saved = netWorthRepository.save(snapshot);
        log.info(
                "Net worth snapshot saved: id={}, userId={}, date={}, netWorth={} {}",
                saved.getId(),
                userId,
                date,
                saved.getNetWorth(),
                baseCurrency);

        return saved;
    }

    /**
     * Retrieves net worth history for a user within a date range.
     *
     * <p>Returns snapshots ordered by date ascending (oldest first) for time-series analysis.
     *
     * <p>Requirement REQ-2.5.2: Get historical net worth data
     *
     * @param userId the user ID
     * @param startDate the start date (inclusive)
     * @param endDate the end date (inclusive)
     * @return list of net worth snapshots
     */
    @Transactional(readOnly = true)
    public List<NetWorth> getNetWorthHistory(Long userId, LocalDate startDate, LocalDate endDate) {
        if (userId == null) {
            throw new IllegalArgumentException("User ID cannot be null");
        }
        if (startDate == null) {
            throw new IllegalArgumentException("Start date cannot be null");
        }
        if (endDate == null) {
            throw new IllegalArgumentException("End date cannot be null");
        }
        if (startDate.isAfter(endDate)) {
            throw new IllegalArgumentException("Start date must be before or equal to end date");
        }

        log.debug(
                "Retrieving net worth history for user {} from {} to {}",
                userId,
                startDate,
                endDate);
        return netWorthRepository.findByUserIdAndDateRange(userId, startDate, endDate);
    }

    /**
     * Retrieves the most recent net worth snapshot for a user.
     *
     * @param userId the user ID
     * @return optional containing the latest snapshot, or empty if none exist
     */
    @Transactional(readOnly = true)
    public Optional<NetWorth> getLatestNetWorth(Long userId) {
        if (userId == null) {
            throw new IllegalArgumentException("User ID cannot be null");
        }

        log.debug("Retrieving latest net worth for user {}", userId);
        return netWorthRepository.findLatestByUserId(userId);
    }

    /**
     * Retrieves the net worth snapshot from approximately one month ago. Used to calculate monthly
     * change in net worth for the dashboard.
     *
     * @param userId the user ID
     * @return optional containing the snapshot from ~1 month ago, or empty if not found
     */
    @Transactional(readOnly = true)
    public Optional<NetWorth> getNetWorthOneMonthAgo(Long userId) {
        if (userId == null) {
            throw new IllegalArgumentException("User ID cannot be null");
        }

        LocalDate oneMonthAgo = LocalDate.now().minusMonths(1);
        log.debug("Retrieving net worth for user {} from approximately {}", userId, oneMonthAgo);
        return netWorthRepository.findClosestToDate(userId, oneMonthAgo);
    }

    /**
     * Calculates the change in net worth from the previous month.
     *
     * @param userId the user ID
     * @return a NetWorthChange object with amount and percentage, or zero values if no previous
     *     data
     */
    @Transactional(readOnly = true)
    public NetWorthChange calculateMonthlyChange(Long userId) {
        if (userId == null) {
            throw new IllegalArgumentException("User ID cannot be null");
        }

        Optional<NetWorth> current = getLatestNetWorth(userId);
        Optional<NetWorth> previous = getNetWorthOneMonthAgo(userId);

        if (current.isEmpty()) {
            log.debug("No current net worth data for user {}", userId);
            return new NetWorthChange(BigDecimal.ZERO, BigDecimal.ZERO, false);
        }

        if (previous.isEmpty()) {
            log.debug(
                    "No previous month net worth data for user {}, returning no-comparison indicator",
                    userId);
            // Return the full net worth as the amount, but flag that there is no prior
            // period to compare against
            return new NetWorthChange(current.get().getNetWorth(), null, false);
        }

        BigDecimal change = current.get().calculateChangeFrom(previous.get());
        BigDecimal percentageChange = current.get().calculatePercentageChangeFrom(previous.get());

        log.debug(
                "Monthly change calculated for user {}: amount={}, percentage={}%",
                userId, change, percentageChange);

        return new NetWorthChange(change, percentageChange, true);
    }

    /**
     * Deletes old net worth snapshots beyond the retention period. Useful for data retention
     * policies (e.g., keep only last 2 years).
     *
     * @param userId the user ID
     * @param retentionDays number of days to retain (older snapshots will be deleted)
     * @return number of deleted snapshots
     */
    public int cleanupOldSnapshots(Long userId, int retentionDays) {
        if (userId == null) {
            throw new IllegalArgumentException("User ID cannot be null");
        }
        if (retentionDays < 0) {
            throw new IllegalArgumentException("Retention days must be non-negative");
        }

        LocalDate cutoffDate = LocalDate.now().minusDays(retentionDays);
        log.info("Cleaning up net worth snapshots for user {} older than {}", userId, cutoffDate);

        int deletedCount =
                netWorthRepository.deleteByUserIdAndSnapshotDateBefore(userId, cutoffDate);
        log.info("Deleted {} old net worth snapshots for user {}", deletedCount, userId);

        return deletedCount;
    }

    /** Value object representing change in net worth. */
    public record NetWorthChange(BigDecimal amount, BigDecimal percentage, boolean hasComparison) {}

    /**
     * Calculates the change in net worth from the beginning of the given period.
     *
     * @param userId the user ID
     * @param periodDays the number of days to look back
     * @return a NetWorthChange object
     */
    @Transactional(readOnly = true)
    public NetWorthChange calculateChangeForPeriod(Long userId, int periodDays) {
        if (userId == null) {
            throw new IllegalArgumentException("User ID cannot be null");
        }
        Optional<NetWorth> current = getLatestNetWorth(userId);
        if (current.isEmpty()) {
            return new NetWorthChange(BigDecimal.ZERO, BigDecimal.ZERO, false);
        }
        LocalDate targetDate = LocalDate.now().minusDays(periodDays);
        Optional<NetWorth> previous = netWorthRepository.findClosestToDate(userId, targetDate);
        if (previous.isEmpty()) {
            return new NetWorthChange(current.get().getNetWorth(), null, false);
        }
        BigDecimal change = current.get().calculateChangeFrom(previous.get());
        BigDecimal percentageChange = current.get().calculatePercentageChangeFrom(previous.get());
        return new NetWorthChange(change, percentageChange, true);
    }

    /**
     * Backfills missing monthly net worth snapshots between startDate and endDate by computing
     * historical account balances from transaction data.
     *
     * @param userId the user ID
     * @param startDate the start of the range to backfill
     * @param endDate the end of the range (capped at today)
     * @param baseCurrency the user's base currency
     * @return the number of new snapshots created
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int backfillNetWorthHistory(
            Long userId, LocalDate startDate, LocalDate endDate, String baseCurrency) {
        return backfillNetWorthHistory(userId, startDate, endDate, baseCurrency, false);
    }

    /**
     * Backfills monthly net worth snapshots between startDate and endDate.
     *
     * <p>When {@code force=true} all existing snapshots in the range are deleted first so every
     * snapshot is freshly computed â€” useful after the user edits historical transaction amounts
     * or property values.
     *
     * <p>When an encryption key is provided, real estate properties (using their value at each
     * target date from {@code real_estate_value_history}) and liabilities (balance reconstructed by
     * reversing repayment transactions that occurred after each target date) are included alongside
     * accounts and assets.
     *
     * @param userId the user ID
     * @param startDate the start of the range to backfill
     * @param endDate the end of the range (capped at today)
     * @param baseCurrency the userâ€™s base currency
     * @param encryptionKey optional; required to include real estate and liabilities
     * @param force when true, delete and recompute existing snapshots
     * @return the number of snapshots created
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int backfillNetWorthHistory(
            Long userId,
            LocalDate startDate,
            LocalDate endDate,
            String baseCurrency,
            boolean force) {
        LocalDate effectiveEnd = endDate.isAfter(LocalDate.now()) ? LocalDate.now() : endDate;

        if (force) {
            int deleted =
                    netWorthRepository.deleteByUserIdAndSnapshotDateBetween(
                            userId, startDate.withDayOfMonth(1), effectiveEnd);
            log.info(
                    "Force-recalculate: deleted {} existing net worth snapshots for user {} in range {} to {}",
                    deleted,
                    userId,
                    startDate,
                    effectiveEnd);
        }

        List<Account> accounts = accountRepository.findByUserIdAndIsActive(userId, true);
        List<Asset> assets = assetRepository.findByUserId(userId);
        List<Transaction> allTransactions = transactionRepository.findByUserId(userId);

        // Group transactions by source account and destination account
        Map<Long, List<Transaction>> txBySourceAccount =
                allTransactions.stream().collect(Collectors.groupingBy(Transaction::getAccountId));
        Map<Long, List<Transaction>> txByDestAccount =
                allTransactions.stream()
                        .filter(t -> t.getToAccountId() != null)
                        .collect(Collectors.groupingBy(Transaction::getToAccountId));

        // Build per-account earliest activity date (min of opening_date and earliest
        // transaction)
        Map<Long, LocalDate> accountEarliestDate = new java.util.HashMap<>();
        for (Account account : accounts) {
            LocalDate earliest = account.getOpeningDate();
            List<Transaction> acctTx =
                    txBySourceAccount.getOrDefault(account.getId(), new ArrayList<>());
            for (Transaction t : acctTx) {
                if (t.getDate() != null && (earliest == null || t.getDate().isBefore(earliest))) {
                    earliest = t.getDate();
                }
            }
            List<Transaction> acctDestTx =
                    txByDestAccount.getOrDefault(account.getId(), new ArrayList<>());
            for (Transaction t : acctDestTx) {
                if (t.getDate() != null && (earliest == null || t.getDate().isBefore(earliest))) {
                    earliest = t.getDate();
                }
            }
            accountEarliestDate.put(account.getId(), earliest);
        }

        // Pre-load data needed for encrypted entities (only when key is available)
        List<RealEstateProperty> realEstateProps =
                realEstateRepository.findByUserIdAndIsActive(userId, true);
        List<Liability> liabilities = liabilityRepository.findByUserIdOrderByCreatedAtDesc(userId);

        // Pre-load real estate value history, grouped by property ID
        Map<Long, List<RealEstateValueHistory>> valueHistoryByProperty =
                realEstateValueHistoryRepository.findByUserId(userId).stream()
                        .collect(Collectors.groupingBy(RealEstateValueHistory::getPropertyId));

        // Pre-group liability-linked payment transactions by liability ID
        // Payments are EXPENSE transactions with a non-null liabilityId.
        // Their amounts are always positive; each payment reduces the outstanding
        // balance.
        Map<Long, List<Transaction>> paymentsByLiability =
                allTransactions.stream()
                        .filter(t -> t.getLiabilityId() != null)
                        .collect(Collectors.groupingBy(Transaction::getLiabilityId));

        int savedCount = 0;
        LocalDate current = startDate.withDayOfMonth(1);

        while (!current.isAfter(effectiveEnd)) {
            final LocalDate targetDate = current;

            if (netWorthRepository.findByUserIdAndSnapshotDate(userId, targetDate).isEmpty()) {
                BigDecimal totalAssets = BigDecimal.ZERO;
                BigDecimal totalLiabilities = BigDecimal.ZERO;

                for (Account account : accounts) {
                    LocalDate earliest = accountEarliestDate.get(account.getId());
                    if (earliest != null && earliest.isAfter(targetDate)) {
                        continue;
                    }
                    BigDecimal historicalBalance =
                            computeHistoricalBalance(
                                    account, targetDate, txBySourceAccount, txByDestAccount);
                    BigDecimal convertedBalance;
                    try {
                        convertedBalance =
                                account.getCurrency() != null
                                                && !account.getCurrency().equals(baseCurrency)
                                        ? exchangeRateService.convert(
                                                historicalBalance,
                                                account.getCurrency(),
                                                baseCurrency)
                                        : historicalBalance;
                    } catch (Exception e) {
                        log.warn(
                                "Currency conversion failed for backfill (account {}), using raw amount",
                                account.getId());
                        convertedBalance = historicalBalance;
                    }

                    if (convertedBalance.compareTo(BigDecimal.ZERO) > 0) {
                        totalAssets = totalAssets.add(convertedBalance);
                    } else if (convertedBalance.compareTo(BigDecimal.ZERO) < 0) {
                        totalLiabilities = totalLiabilities.add(convertedBalance.abs());
                    }
                }

                // Include investment assets using cost basis (purchasePrice Ã— quantity),
                // gated by purchaseDate. Skip REAL_ESTATE type to avoid double-counting
                // with RealEstateProperty entities below.
                for (Asset asset : assets) {
                    if (asset.getType() == org.openfinance.entity.AssetType.REAL_ESTATE) {
                        continue;
                    }
                    if (asset.getPurchaseDate() != null
                            && asset.getPurchaseDate().isAfter(targetDate)) {
                        continue;
                    }
                    if (asset.getPurchasePrice() == null || asset.getQuantity() == null) {
                        continue;
                    }
                    BigDecimal costBasis = asset.getPurchasePrice().multiply(asset.getQuantity());
                    BigDecimal convertedCostBasis;
                    try {
                        convertedCostBasis =
                                asset.getCurrency() != null
                                                && !asset.getCurrency().equals(baseCurrency)
                                        ? exchangeRateService.convert(
                                                costBasis, asset.getCurrency(), baseCurrency)
                                        : costBasis;
                    } catch (Exception e) {
                        log.warn(
                                "Currency conversion failed for asset {} in backfill, using raw amount",
                                asset.getId());
                        convertedCostBasis = costBasis;
                    }
                    if (convertedCostBasis.compareTo(BigDecimal.ZERO) > 0) {
                        totalAssets = totalAssets.add(convertedCostBasis);
                    }
                }

                // Include real estate and liabilities.
                {
                    // Real estate: use the value that was in effect at targetDate
                    // (latest history entry whose effectiveDate <= targetDate),
                    // falling back to purchasePrice if no history entry exists yet.
                    for (RealEstateProperty property : realEstateProps) {
                        if (property.getPurchaseDate() != null
                                && property.getPurchaseDate().isAfter(targetDate)) {
                            continue; // not yet owned at this point in history
                        }
                        try {
                            List<RealEstateValueHistory> history =
                                    valueHistoryByProperty.getOrDefault(
                                            property.getId(), List.of());
                            // Find the most recent entry on or before targetDate
                            Optional<RealEstateValueHistory> historyEntry =
                                    history.stream()
                                            .filter(h -> !h.getEffectiveDate().isAfter(targetDate))
                                            .max(
                                                    Comparator.comparing(
                                                            RealEstateValueHistory
                                                                    ::getEffectiveDate));

                            BigDecimal value;
                            if (historyEntry.isPresent()) {
                                value = new BigDecimal(historyEntry.get().getRecordedValue());
                            } else {
                                // No history yet â€” use purchasePrice as best proxy for early
                                // dates
                                if (property.getPurchasePrice() != null
                                        && !property.getPurchasePrice().isBlank()) {
                                    value = new BigDecimal(property.getPurchasePrice());
                                } else {
                                    continue;
                                }
                            }

                            BigDecimal converted =
                                    convertToBaseCurrency(
                                            value, property.getCurrency(), baseCurrency);
                            if (converted.compareTo(BigDecimal.ZERO) > 0) {
                                totalAssets = totalAssets.add(converted);
                            }
                        } catch (Exception e) {
                            log.warn(
                                    "Could not compute historical value for real estate property {} in backfill, skipping",
                                    property.getId());
                        }
                    }

                    // Liabilities: reconstruct outstanding balance at targetDate by reversing
                    // repayment payments that occurred AFTER targetDate (since those payments
                    // reduced the balance after our target point in time).
                    for (Liability liability : liabilities) {
                        if (liability.getStartDate() != null
                                && liability.getStartDate().isAfter(targetDate)) {
                            continue; // liability didn't exist yet
                        }
                        try {
                            BigDecimal historicalBalance =
                                    computeHistoricalLiabilityBalance(
                                            liability, targetDate, paymentsByLiability);
                            BigDecimal converted =
                                    convertToBaseCurrency(
                                            historicalBalance,
                                            liability.getCurrency(),
                                            baseCurrency);
                            if (converted.compareTo(BigDecimal.ZERO) > 0) {
                                totalLiabilities = totalLiabilities.add(converted);
                            }
                        } catch (Exception e) {
                            log.warn(
                                    "Could not compute historical balance for liability {} in backfill, skipping",
                                    liability.getId());
                        }
                    }
                }

                if (totalAssets.compareTo(BigDecimal.ZERO) > 0
                        || totalLiabilities.compareTo(BigDecimal.ZERO) > 0) {
                    BigDecimal netWorthAtDate = totalAssets.subtract(totalLiabilities);
                    NetWorth snapshot =
                            NetWorth.builder()
                                    .userId(userId)
                                    .snapshotDate(targetDate)
                                    .totalAssets(totalAssets)
                                    .totalLiabilities(totalLiabilities)
                                    .netWorth(netWorthAtDate)
                                    .currency(baseCurrency)
                                    .currencyId(resolveCurrencyId(baseCurrency))
                                    .build();
                    netWorthRepository.save(snapshot);
                    savedCount++;
                    log.debug(
                            "Backfilled net worth snapshot for user {} on {}: assets={}, liabilities={}, net={}",
                            userId,
                            targetDate,
                            totalAssets,
                            totalLiabilities,
                            netWorthAtDate);
                }
            }

            current = current.plusMonths(1);
        }

        log.info(
                "Backfilled {} net worth snapshots for user {} from {} to {}",
                savedCount,
                userId,
                startDate,
                effectiveEnd);
        return savedCount;
    }

    /**
     * Computes the outstanding liability balance at a given historical date.
     *
     * <p>Starts from the current (latest) balance and adds back all repayment payments that were
     * made AFTER the target date (because those payments reduced the balance after our point in
     * time, so reversing them gives us the earlier outstanding balance).
     *
     * @param liability the liability entity (must have currentBalance decryptable)
     * @param targetDate the historical date to reconstruct the balance for
     * @param paymentsByLiability map of liabilityId â†’ list of linked payment transactions
     * @param encryptionKey key for decrypting the encrypted currentBalance field
     * @return the reconstructed outstanding balance at targetDate
     */
    private BigDecimal computeHistoricalLiabilityBalance(
            Liability liability,
            LocalDate targetDate,
            Map<Long, List<Transaction>> paymentsByLiability) {
        String balanceStr = liability.getCurrentBalance();
        if (balanceStr == null || balanceStr.isBlank()) {
            return BigDecimal.ZERO;
        }
        BigDecimal currentBalance = new BigDecimal(balanceStr);

        // Payments after targetDate reduced the balance â€” add them back to restore
        // earlier balance
        List<Transaction> paymentsAfter =
                paymentsByLiability.getOrDefault(liability.getId(), List.of()).stream()
                        .filter(t -> t.getDate() != null && t.getDate().isAfter(targetDate))
                        .collect(Collectors.toList());

        BigDecimal reversed =
                paymentsAfter.stream()
                        .map(Transaction::getAmount)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal historical = currentBalance.add(reversed);
        // Balance cannot exceed original principal (guard against data anomalies)
        if (liability.getPrincipal() != null && !liability.getPrincipal().isBlank()) {
            try {
                BigDecimal principal = new BigDecimal(liability.getPrincipal());
                if (historical.compareTo(principal) > 0) {
                    historical = principal;
                }
            } catch (Exception ignored) {
                // If we can't decrypt principal, just use the computed value
            }
        }
        return historical.max(BigDecimal.ZERO);
    }

    /**
     * Computes the historical balance of an account at a given date by reversing transactions that
     * occurred after that date.
     */
    private BigDecimal computeHistoricalBalance(
            Account account,
            LocalDate targetDate,
            Map<Long, List<Transaction>> txBySourceAccount,
            Map<Long, List<Transaction>> txByDestAccount) {
        BigDecimal currentBalance = account.getBalance();
        BigDecimal delta = BigDecimal.ZERO;

        // Reverse source-account transactions after targetDate
        List<Transaction> sourceTxAfter =
                txBySourceAccount.getOrDefault(account.getId(), new ArrayList<>()).stream()
                        .filter(t -> t.getDate() != null && t.getDate().isAfter(targetDate))
                        .collect(Collectors.toList());

        for (Transaction t : sourceTxAfter) {
            if (t.getType() == TransactionType.INCOME) {
                // Was added after targetDate â†’ reverse: subtract
                delta = delta.subtract(t.getAmount());
            } else if (t.getType() == TransactionType.EXPENSE
                    || t.getType() == TransactionType.TRANSFER) {
                // Was deducted after targetDate â†’ reverse: add
                delta = delta.add(t.getAmount());
            }
        }

        // NOTE: Do NOT reverse destination-account transfer rows here. Transfers are persisted as
        // two rows (one per account, each in its own native currency); each account's own row
        // already captures the transfer's effect on that account — mirroring
        // AccountService.recalculateBalance, which sums only own-account rows. Reversing the other
        // side's row would double-count the transfer and, for cross-currency transfers, subtract a
        // foreign-currency amount (e.g. XOF) from this account's native balance, corrupting the
        // reconstructed historical balance.

        return currentBalance.add(delta);
    }

    private Long resolveCurrencyId(String currencyCode) {
        if (currencyCode == null || currencyCode.isBlank()) return null;
        return currencyRepository
                .findByCode(currencyCode)
                .map(org.openfinance.entity.Currency::getId)
                .orElse(null);
    }
}
