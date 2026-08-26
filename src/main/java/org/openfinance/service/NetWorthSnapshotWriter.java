package org.openfinance.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.openfinance.entity.NetWorth;
import org.openfinance.repository.NetWorthRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Persists net worth snapshots in a dedicated, short-lived transaction.
 *
 * <p>Extracted from {@link NetWorthService} to fix {@code SQLITE_BUSY_SNAPSHOT}. The caller ({@link
 * NetWorthService#saveNetWorthSnapshot}) runs the read-heavy asset/liability calculation with
 * {@link Propagation#NOT_SUPPORTED} — i.e. outside any transaction — and hands the pre-computed
 * totals to {@link #upsert}. Because {@code upsert} opens its <em>own</em> fresh {@link
 * Transactional} context, the connection used for the {@code INSERT/UPDATE net_worth} statement has
 * never held a prior WAL read-snapshot, eliminating the stale-snapshot conflict that SQLite raises
 * when a connection tries to write after another connection committed under its read snapshot.
 *
 * <p>Mirrors the {@link UserLoginStateService} / {@link AuthService} split used for the same WAL
 * safety reason.
 *
 * <p>Requirement REQ-2.5.1: Save current net worth snapshot.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NetWorthSnapshotWriter {

    private final NetWorthRepository netWorthRepository;

    /**
     * Upserts the {@code (user_id, snapshot_date)} net worth row using pre-computed totals.
     *
     * <p>Runs in a fresh {@link Propagation#REQUIRED} transaction. The caller serializes concurrent
     * invocations for the same user via a per-user monitor whose critical section spans this call,
     * so the winning INSERT is committed and visible before the next caller reads.
     *
     * @param userId the user ID
     * @param date the snapshot date
     * @param baseCurrency the base currency code for the snapshot
     * @param totalAssets pre-computed total assets in {@code baseCurrency}
     * @param totalLiabilities pre-computed total liabilities in {@code baseCurrency}
     * @param netWorth pre-computed net worth ({@code totalAssets - totalLiabilities})
     * @param currencyId resolved currency id for {@code baseCurrency}, or {@code null}
     * @return the persisted snapshot
     */
    @Transactional
    public NetWorth upsert(
            Long userId,
            LocalDate date,
            String baseCurrency,
            BigDecimal totalAssets,
            BigDecimal totalLiabilities,
            BigDecimal netWorth,
            Long currencyId) {
        Optional<NetWorth> existingSnapshot =
                netWorthRepository.findByUserIdAndSnapshotDate(userId, date);

        NetWorth snapshot;
        if (existingSnapshot.isPresent()) {
            snapshot = existingSnapshot.get();
            snapshot.setTotalAssets(totalAssets);
            snapshot.setTotalLiabilities(totalLiabilities);
            snapshot.setNetWorth(netWorth);
            snapshot.setCurrency(baseCurrency);
            snapshot.setCurrencyId(currencyId);
            log.debug("Updating existing net worth snapshot: id={}", snapshot.getId());
        } else {
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
}
