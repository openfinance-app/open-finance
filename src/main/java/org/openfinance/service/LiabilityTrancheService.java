package org.openfinance.service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.openfinance.entity.Liability;
import org.openfinance.entity.LiabilityPrincipalAllocation;
import org.openfinance.entity.LiabilityTranche;
import org.openfinance.entity.TrancheStatus;
import org.openfinance.entity.Transaction;
import org.openfinance.exception.InvalidLiabilityStateException;
import org.openfinance.exception.InvalidTransactionException;
import org.openfinance.repository.LiabilityPrincipalAllocationRepository;
import org.openfinance.repository.LiabilityTrancheRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Records exact payment allocations across drawn tranches and the opening loan position. */
@Service
@Transactional
@RequiredArgsConstructor
public class LiabilityTrancheService {
    private static final Comparator<LiabilityTranche> FIFO_ORDER =
            Comparator.comparing(
                            LiabilityTranche::getDrawnDate,
                            Comparator.nullsLast(Comparator.naturalOrder()))
                    .thenComparing(LiabilityTranche::getTrancheNo);

    private final LiabilityTrancheRepository liabilityTrancheRepository;
    private final LiabilityPrincipalAllocationRepository allocationRepository;

    @Transactional(readOnly = true)
    public BigDecimal allocatedPrincipal(LiabilityTranche tranche) {
        return allocationRepository
                .findByTrancheIdAndUserId(tranche.getId(), tranche.getUserId())
                .stream()
                .map(LiabilityPrincipalAllocation::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    @Transactional(readOnly = true)
    public BigDecimal remainingOf(LiabilityTranche tranche) {
        if (tranche.getStatus() != TrancheStatus.DRAWN || tranche.getDrawnAmount() == null) {
            return BigDecimal.ZERO;
        }
        return tranche.getDrawnAmount().subtract(allocatedPrincipal(tranche)).max(BigDecimal.ZERO);
    }

    public void allocateRepayment(
            Long userId, Liability liability, Transaction tx, BigDecimal principal) {
        org.openfinance.util.LoanPostingPolicy.validateDate(liability, tx.getDate());
        BigDecimal balance = balanceOf(liability);
        if (principal.signum() < 0 || principal.compareTo(balance) > 0) {
            throw new InvalidTransactionException("Principal payment exceeds outstanding debt");
        }
        List<LiabilityTranche> all =
                liabilityTrancheRepository.findByLiabilityIdAndUserId(liability.getId(), userId);
        List<LiabilityTranche> ordered = new ArrayList<>(all);
        ordered.sort(FIFO_ORDER);
        if (tx.getTrancheId() != null) {
            LiabilityTranche selected =
                    all.stream()
                            .filter(t -> tx.getTrancheId().equals(t.getId()))
                            .findFirst()
                            .orElseThrow(
                                    () ->
                                            new InvalidTransactionException(
                                                    "Repayment tranche does not belong to this liability"));
            if (selected.getStatus() != TrancheStatus.DRAWN) {
                throw InvalidLiabilityStateException.repaymentTargetNotDrawn(
                        selected.getId(), selected.getStatus());
            }
            ordered.remove(selected);
            ordered.add(0, selected);
        }
        BigDecimal futureDraws =
                all.stream()
                        .filter(
                                t ->
                                        t.getStatus() == TrancheStatus.DRAWN
                                                && t.getDrawnDate() != null
                                                && t.getDrawnDate().isAfter(tx.getDate()))
                        .map(this::remainingOf)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (principal.compareTo(balance.subtract(futureDraws).max(BigDecimal.ZERO)) > 0) {
            throw new InvalidTransactionException("Cannot repay principal before it was drawn");
        }
        BigDecimal unallocated = principal;
        for (LiabilityTranche tranche : ordered) {
            if (unallocated.signum() == 0) break;
            if (tranche.getDrawnDate() != null && tranche.getDrawnDate().isAfter(tx.getDate()))
                continue;
            BigDecimal amount = remainingOf(tranche).min(unallocated);
            if (amount.signum() > 0) {
                saveAllocation(userId, liability.getId(), tx.getId(), tranche.getId(), amount);
                unallocated = unallocated.subtract(amount);
            }
        }
        if (unallocated.signum() > 0) {
            saveAllocation(userId, liability.getId(), tx.getId(), null, unallocated);
        }
    }

    private void saveAllocation(
            Long userId, Long liabilityId, Long transactionId, Long trancheId, BigDecimal amount) {
        allocationRepository.save(
                LiabilityPrincipalAllocation.builder()
                        .userId(userId)
                        .liabilityId(liabilityId)
                        .transactionId(transactionId)
                        .trancheId(trancheId)
                        .amount(amount)
                        .build());
    }

    @Transactional(readOnly = true)
    public List<org.openfinance.dto.PrincipalAllocationResponse> allocations(
            Long userId, Long transactionId) {
        return allocationRepository.findByTransactionIdAndUserId(transactionId, userId).stream()
                .map(
                        a ->
                                new org.openfinance.dto.PrincipalAllocationResponse(
                                        a.getTrancheId(), a.getAmount()))
                .toList();
    }

    public void removeRepayment(Long userId, Long transactionId) {
        allocationRepository.deleteByTransactionIdAndUserId(transactionId, userId);
    }

    /** The booked balance includes opening debt and cannot be replaced by a sum of tranches. */
    public void reconcile(Liability liability) {
        if (balanceOf(liability).signum() < 0) {
            throw new InvalidTransactionException("Outstanding debt cannot be negative");
        }
    }

    @Transactional(readOnly = true)
    public BigDecimal sumDrawnOfDrawn(Long liabilityId, Long userId) {
        return liabilityTrancheRepository.findByLiabilityIdAndUserId(liabilityId, userId).stream()
                .filter(t -> t.getStatus() == TrancheStatus.DRAWN)
                .map(LiabilityTranche::getDrawnAmount)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public void assertDisbursementAllowed(Liability liability, Long userId) {
        reconcile(liability);
    }

    @Transactional(readOnly = true)
    public Map<Long, BigDecimal> remainingByTrancheId(
            Long userId, List<LiabilityTranche> tranches) {
        Map<Long, BigDecimal> remaining = new HashMap<>();
        for (LiabilityTranche tranche : tranches) {
            if (!userId.equals(tranche.getUserId())) {
                throw new InvalidTransactionException("Tranche does not belong to this user");
            }
            remaining.put(tranche.getId(), remainingOf(tranche));
        }
        return remaining;
    }

    private BigDecimal balanceOf(Liability liability) {
        return liability.getCurrentBalance() == null
                ? BigDecimal.ZERO
                : new BigDecimal(liability.getCurrentBalance());
    }
}
