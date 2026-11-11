package org.openfinance.service;

import static org.openfinance.service.ImportedAccountRouting.resolveImportedAccountId;
import static org.openfinance.service.ImportedAccountRouting.resolveImportedDestinationAccountId;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiFunction;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.openfinance.dto.ImportedTransaction;
import org.openfinance.entity.ImportSession;
import org.openfinance.entity.Transaction;
import org.openfinance.repository.AccountRepository;
import org.openfinance.repository.NetWorthRepository;
import org.openfinance.repository.TransactionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Keeps transfer postings and source references together. The import owns the transaction and
 * handles per-row validation failures; repository failures retain their existing rollback behavior.
 */
@Service
@Transactional(propagation = Propagation.MANDATORY, noRollbackFor = RuntimeException.class)
@RequiredArgsConstructor
public class ImportedTransferServiceImpl implements ImportedTransferService {
    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;
    private final ImportedInvestmentService investmentImportService;
    private final TransactionService transactionService;
    private final AccountCurrencyService accountCurrencyService;
    private final NetWorthRepository netWorthRepository;

    @Override
    @Transactional(propagation = Propagation.SUPPORTS, readOnly = true)
    public void resolveOfxTransfers(ImportSession session, List<ImportedTransaction> rows) {
        if ("OFX".equalsIgnoreCase(session.getFileFormat())
                || "QFX".equalsIgnoreCase(session.getFileFormat())) {
            OfxTransferResolver.resolve(rows, accountRepository.findByUserId(session.getUserId()));
        }
    }

    /** Keep recorded legs, including quantities, currencies and account-scoped FITIDs. */
    @Override
    public void save(
            List<ImportedTransaction> rows,
            Map<String, Long> accounts,
            Long fallback,
            Long userId,
            BiFunction<ImportedTransaction, Long, Transaction> converter) {
        if (rows.isEmpty() || rows.size() > 2)
            throw new IllegalArgumentException("Invalid transfer pair");
        ImportedTransaction first = rows.getFirst();
        Long source = resolveImportedAccountId(first, accounts, fallback);
        Long target = resolveImportedDestinationAccountId(first, accounts, fallback);
        if (source == null || target == null || source.equals(target)) {
            throw new IllegalArgumentException("A transfer requires two different accounts");
        }
        List<ImportedTransaction> legs = new ArrayList<>(rows);
        if (legs.size() == 1) legs.add(mirrorImportedTransfer(first));
        List<Transaction> booked =
                bookImportedTransferLegs(legs, source, target, userId, converter);
        if (booked.getFirst().getType() == booked.getLast().getType()) {
            throw new IllegalArgumentException("Transfer legs must have opposite directions");
        }
        String sourceReference = first.getOfxTransferReference();
        String transferId = transferId(sourceReference, source, target, userId);
        if (attachImportedTransferReferences(
                booked, userId, sourceReference == null ? null : transferId)) return;
        for (int index = 0; index < 2; index++) {
            Transaction transaction = booked.get(index);
            transaction.setTransferId(transferId);
            investmentImportService.prepare(transaction, legs.get(index));
        }
        transactionRepository.saveAll(booked);
        for (int index = 0; index < 2; index++) {
            transactionService.syncTransactionFts(
                    booked.get(index), legs.get(index).getPayee(), legs.get(index).getMemo());
        }
    }

    private String transferId(String reference, Long source, Long target, Long userId) {
        if (reference == null) return UUID.randomUUID().toString();
        String identity =
                "ofx:"
                        + userId
                        + ":"
                        + Math.min(source, target)
                        + ":"
                        + Math.max(source, target)
                        + ":"
                        + reference;
        return UUID.nameUUIDFromBytes(identity.getBytes(StandardCharsets.UTF_8)).toString();
    }

    private List<Transaction> bookImportedTransferLegs(
            List<ImportedTransaction> legs,
            Long source,
            Long target,
            Long userId,
            BiFunction<ImportedTransaction, Long, Transaction> converter) {
        List<Transaction> booked = new ArrayList<>();
        for (int index = 0; index < 2; index++) {
            ImportedTransaction row = legs.get(index);
            Long account = index == 0 ? source : target;
            Transaction transaction = converter.apply(row, account);
            transaction.setCategoryId(null);
            transaction.setToAccountId(index == 0 ? target : source);
            accountCurrencyService.book(transaction, userId);
            booked.add(transaction);
        }
        return booked;
    }

    private ImportedTransaction mirrorImportedTransfer(ImportedTransaction row) {
        BigDecimal amount =
                row.getTransferAmount() == null
                        ? row.getAmount().abs()
                        : row.getTransferAmount().abs();
        ImportedTransaction mirror =
                row.toBuilder()
                        .amount(row.getAmount().signum() > 0 ? amount.negate() : amount)
                        .referenceNumber(
                                row.isOfxTransfer()
                                        ? "ofx:transfer-leg:" + UUID.randomUUID()
                                        : null)
                        .authoritativeReference(row.isOfxTransfer())
                        .originalAmount(null)
                        .originalCurrency(null)
                        .conversionRate(null)
                        .sourceAccountBalanceDelta(null)
                        .category(null)
                        .splits(List.of())
                        .build();
        if (row.isHoldingTransfer()) {
            mirror.setInvestmentQuantity(row.getInvestmentQuantity().negate());
        } else {
            mirror.setInvestmentQuantity(null);
            mirror.setInvestmentSecurity(null);
            mirror.setInvestmentPrice(null);
            mirror.setInvestmentName(null);
            mirror.setInvestmentType(null);
            mirror.setInvestmentCash(false);
        }
        return mirror;
    }

    /** A later statement may supply the FITID of a counter-leg created by an earlier import. */
    private boolean attachImportedTransferReferences(
            List<Transaction> booked, Long userId, String sourceTransferId) {
        List<Transaction> matches =
                sourceTransferId == null
                        ? List.of()
                        : transactionRepository.findByTransferId(sourceTransferId);
        boolean nativeReference = !matches.isEmpty();
        if (matches.isEmpty()) matches = findImportedTransferMatches(booked, userId);
        if (matches.isEmpty()) return false;
        Set<String> ids =
                matches.stream().map(Transaction::getTransferId).collect(Collectors.toSet());
        if (ids.size() != 1)
            throw new IllegalArgumentException("Ambiguous imported transfer counterpart");
        List<Transaction> existing = transactionRepository.findByTransferId(ids.iterator().next());
        // Validate the entire pair before changing either reference.
        Map<Transaction, Transaction> replacements = new IdentityHashMap<>();
        for (Transaction row : booked) {
            if (row.getExternalReference() == null || generatedTransferLeg(row)) continue;
            Transaction leg =
                    existing.stream()
                            .filter(
                                    tx ->
                                            userId.equals(tx.getUserId())
                                                    && row.getAccountId().equals(tx.getAccountId()))
                            .findFirst()
                            .orElseThrow();
            if (row.getExternalReference().equals(leg.getExternalReference())) continue;
            if (!generatedTransferLeg(leg)
                    || (!nativeReference && !sameImportedTransferAmount(row, leg))) {
                throw new IllegalArgumentException(
                        "Imported transfer differs from its existing counterpart");
            }
            replacements.put(leg, row);
        }
        replacements.forEach(this::replaceGeneratedTransferLeg);
        return true;
    }

    /** Replace an inferred counter-leg with its own bank statement, preserving its link and ID. */
    private void replaceGeneratedTransferLeg(Transaction existing, Transaction recorded) {
        if (existing.getDate().isBefore(recorded.getDate())) {
            // Confirmation invalidates from the new posting month; also clear the inferred one.
            netWorthRepository.deleteByUserIdAndSnapshotDateBetween(
                    existing.getUserId(), existing.getDate().withDayOfMonth(1), LocalDate.now());
        }
        existing.setExternalReference(recorded.getExternalReference());
        existing.setDate(recorded.getDate());
        existing.setAmount(recorded.getAmount());
        existing.setCurrency(recorded.getCurrency());
        existing.setCurrencyId(recorded.getCurrencyId());
        existing.setAccountAmount(recorded.getAccountAmount());
        existing.setAccountCurrency(recorded.getAccountCurrency());
        existing.setOriginalAmount(recorded.getOriginalAmount());
        existing.setOriginalCurrency(recorded.getOriginalCurrency());
        existing.setConversionRate(recorded.getConversionRate());
        existing.setDescription(recorded.getDescription());
        existing.setNotes(recorded.getNotes());
        existing.setPayee(recorded.getPayee());
        existing.setPayeeId(recorded.getPayeeId());
        existing.setIsReconciled(recorded.getIsReconciled());
        transactionRepository.save(existing);
        transactionService.syncTransactionFts(existing, existing.getPayee(), existing.getNotes());
    }

    private List<Transaction> findImportedTransferMatches(List<Transaction> booked, Long userId) {
        List<Transaction> matches = new ArrayList<>();
        for (Transaction row : booked) {
            if (row.getExternalReference() == null || generatedTransferLeg(row)) continue;
            for (Transaction existing :
                    transactionRepository.findActiveByAccountId(row.getAccountId())) {
                if (!userId.equals(existing.getUserId())
                        || existing.getTransferId() == null
                        || !Objects.equals(row.getToAccountId(), existing.getToAccountId()))
                    continue;
                if (row.getExternalReference().equals(existing.getExternalReference())
                        || (generatedTransferLeg(existing)
                                && sameImportedTransferAmount(row, existing))) {
                    matches.add(existing);
                }
            }
        }
        return matches;
    }

    private boolean generatedTransferLeg(Transaction row) {
        return row.getExternalReference() != null
                && row.getExternalReference().startsWith("ofx:transfer-leg:");
    }

    private boolean sameImportedTransferAmount(Transaction first, Transaction second) {
        return first.getType() == second.getType()
                && first.getCurrency().equals(second.getCurrency())
                && first.getAmount().compareTo(second.getAmount()) == 0
                && (first.getInvestmentQuantity() == null
                        ? second.getInvestmentQuantity() == null
                        : second.getInvestmentQuantity() != null
                                && first.getInvestmentQuantity()
                                                .compareTo(second.getInvestmentQuantity())
                                        == 0)
                && Math.abs(ChronoUnit.DAYS.between(first.getDate(), second.getDate())) <= 1;
    }
}
