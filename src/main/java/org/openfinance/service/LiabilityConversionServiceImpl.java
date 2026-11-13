package org.openfinance.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.openfinance.dto.LiabilityConversionCandidate;
import org.openfinance.dto.LiabilityConversionRequest;
import org.openfinance.dto.LiabilityRequest;
import org.openfinance.dto.LiabilityResponse;
import org.openfinance.entity.Account;
import org.openfinance.entity.EntityType;
import org.openfinance.entity.Liability;
import org.openfinance.entity.LiabilityConversionReceipt;
import org.openfinance.entity.OperationType;
import org.openfinance.entity.Transaction;
import org.openfinance.exception.AccountNotFoundException;
import org.openfinance.exception.InvalidTransactionException;
import org.openfinance.repository.AccountRepository;
import org.openfinance.repository.AssetRepository;
import org.openfinance.repository.LiabilityConversionReceiptRepository;
import org.openfinance.repository.LiabilityRepository;
import org.openfinance.repository.TransactionRepository;
import org.openfinance.service.history.ReversibleOperation;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
@RequiredArgsConstructor
public class LiabilityConversionServiceImpl implements LiabilityConversionService {
    private final AccountRepository accounts;
    private final AssetRepository assets;
    private final TransactionRepository transactions;
    private final LiabilityRepository liabilities;
    private final LiabilityConversionReceiptRepository receipts;
    private final AccountService accountService;
    private final LiabilityService liabilityService;
    private final LiabilityConversionPayments paymentAssociations;
    private final ObjectMapper mapper;

    @Override
    @Transactional(readOnly = true)
    public LiabilityConversionPayments.Candidates payments(
            Long userId, Long accountId, String search) {
        Account source =
                accounts.findByIdAndUserId(accountId, userId)
                        .orElseThrow(() -> AccountNotFoundException.byIdAndUser(accountId, userId));
        if (!eligible(source))
            throw invalid("source", "Choose an imported debt account that has not been converted");
        return paymentAssociations.candidates(source, search);
    }

    @Override
    @Transactional(readOnly = true)
    public List<LiabilityConversionCandidate> candidates(Long userId) {
        return accounts.findByUserId(userId).stream()
                .filter(this::eligible)
                .sorted(Comparator.comparing(Account::getName, String.CASE_INSENSITIVE_ORDER))
                .map(account -> candidate(account, movements(account)))
                .toList();
    }

    private boolean eligible(Account account) {
        if (!account.isPresentAt(LocalDate.now())
                || account.getBalance().signum() >= 0
                || account.getOpeningDate().isAfter(LocalDate.now())
                || accounts.isLiabilityBalanceSource(account.getId(), account.getUserId())
                || !assets.findByAccountId(account.getId()).isEmpty()) return false;
        return "SKROOGE_JSON".equals(account.getImportSource())
                || movements(account).stream()
                        .anyMatch(
                                tx ->
                                        tx.getExternalReference() != null
                                                && tx.getExternalReference()
                                                        .startsWith("skrooge:"));
    }

    private List<Transaction> movements(Account account) {
        return transactions.findAllForAccount(account.getId(), account.getUserId()).stream()
                .filter(tx -> !Boolean.TRUE.equals(tx.getIsDeleted()))
                .sorted(Comparator.comparing(Transaction::getId))
                .toList();
    }

    private LiabilityConversionCandidate candidate(Account account, List<Transaction> movements) {
        return new LiabilityConversionCandidate(
                account.getId(),
                account.getName(),
                account.getCurrency(),
                account.getBalance().negate(),
                account.getOpeningDate(),
                movements.size(),
                revision(account, movements));
    }

    @Override
    @CacheEvict(
            value = {
                "dashboardSummary",
                "accountSummaries",
                "netWorthSummary",
                "networthAllocation",
                "assetAllocation",
                "portfolioPerformance",
                "borrowingCapacity"
            },
            allEntries = true)
    @ReversibleOperation(
            entity = EntityType.LIABILITY,
            operation = OperationType.CREATE,
            userArgument = 0)
    public LiabilityResponse convert(Long userId, LiabilityConversionRequest request) {
        String hash = fingerprint(request);
        LiabilityConversionReceipt previous =
                receipts.findByUserIdAndOperationId(userId, request.operationId().toString())
                        .orElse(null);
        if (previous != null) return replay(userId, previous, hash);
        Account account =
                accounts.findByIdAndUserId(request.accountId(), userId)
                        .orElseThrow(
                                () ->
                                        AccountNotFoundException.byIdAndUser(
                                                request.accountId(), userId));
        if (!eligible(account))
            throw invalid("source", "Choose an imported debt account that has not been converted");
        List<Transaction> movements = movements(account);
        if (!revision(account, movements).equals(request.sourceRevision()))
            throw invalid(
                    "changed",
                    "The account changed. Select it again to review its current balance");
        if (movements.stream().anyMatch(tx -> tx.getDate().isAfter(LocalDate.now())))
            throw invalid("future", "Resolve future transactions before converting this account");
        validateTerms(account, request);
        List<LiabilityConversionPayments.ReviewedPayment> reviewedPayments =
                paymentAssociations.review(
                        account,
                        request.payments(),
                        request.fundedPrincipal().add(account.getBalance()));
        LiabilityRequest terms = mapper.convertValue(request.liability(), LiabilityRequest.class);
        // Ownership and contract validation still go through the normal liability creation path.
        terms.setInstitutionId(
                account.getInstitution() == null ? null : account.getInstitution().getId());
        terms.setPreviouslyFunded(true);
        Long id = liabilityService.createLiability(userId, terms).getId();
        Liability liability = liabilities.findByIdAndUserId(id, userId).orElseThrow();
        liability.setConvertedFromAccountId(account.getId());
        liability.setBalanceStartDate(LocalDate.now());
        liability.setOpeningPrincipal(request.fundedPrincipal());
        liabilities.save(liability);
        paymentAssociations.associate(liability, reviewedPayments);
        archiveSource(account, userId);
        saveReceipt(userId, request, hash, id);
        return liabilityService.getLiabilityById(id, userId);
    }

    private void archiveSource(Account account, Long userId) {
        if (Boolean.TRUE.equals(account.getIsActive()))
            accountService.closeAccount(account.getId(), userId);
        // Accounting cutover, not a cash movement. The old ledger reconstructs earlier balances.
        account.setLiabilityTransferredBalance(account.getBalance());
        account.setBalance(BigDecimal.ZERO);
        account.setIsInterestEnabled(false);
        account.setLiabilityConvertedAt(LocalDate.now());
        accounts.save(account);
    }

    private void validateTerms(Account account, LiabilityConversionRequest request) {
        LiabilityRequest terms = request.liability();
        BigDecimal debt = account.getBalance().negate();
        if (terms.getRepresentedByAccountId() != null
                || !account.getCurrency().equals(terms.getCurrency())
                || terms.getCurrentBalance().compareTo(debt) != 0
                || request.fundedPrincipal().compareTo(debt) < 0
                || request.fundedPrincipal().compareTo(terms.getPrincipal()) > 0
                || terms.getStartDate().isAfter(LocalDate.now()))
            throw invalid(
                    "terms",
                    "Keep the source currency and outstanding balance; total borrowed must cover the outstanding debt and cannot exceed the agreed principal");
    }

    private void saveReceipt(
            Long userId, LiabilityConversionRequest request, String hash, Long id) {
        LiabilityConversionReceipt receipt = new LiabilityConversionReceipt();
        receipt.setUserId(userId);
        receipt.setOperationId(request.operationId().toString());
        receipt.setRequestHash(hash);
        receipt.setAccountId(request.accountId());
        receipt.setLiabilityId(id);
        receipts.save(receipt);
    }

    private LiabilityResponse replay(Long userId, LiabilityConversionReceipt receipt, String hash) {
        if (!receipt.getRequestHash().equals(hash))
            throw invalid(
                    "retry", "This conversion identifier was already used for different details");
        if (!liabilities.existsByIdAndUserId(receipt.getLiabilityId(), userId))
            throw invalid("undone", "This conversion was undone. Start a new conversion");
        return liabilityService.getLiabilityById(receipt.getLiabilityId(), userId);
    }

    private String revision(Account account, List<Transaction> movements) {
        return fingerprint(
                Arrays.asList(
                        LocalDate.now(),
                        account.getId(),
                        account.getVersion(),
                        account.getBalance(),
                        account.getOpeningBalance(),
                        account.getOpeningDate(),
                        account.getCurrency(),
                        account.getIsActive(),
                        movements.stream()
                                .map(
                                        tx ->
                                                Arrays.asList(
                                                        tx.getId(),
                                                        tx.getDate(),
                                                        tx.getType(),
                                                        tx.getAmount(),
                                                        tx.getOriginalAmount(),
                                                        tx.getOriginalCurrency(),
                                                        tx.getUpdatedAt()))
                                .toList()));
    }

    private String fingerprint(Object value) {
        try {
            return HexFormat.of()
                    .formatHex(
                            MessageDigest.getInstance("SHA-256")
                                    .digest(mapper.writeValueAsBytes(value)));
        } catch (JsonProcessingException | NoSuchAlgorithmException ex) {
            throw new IllegalStateException("Cannot identify liability conversion", ex);
        }
    }

    private InvalidTransactionException invalid(String key, String fallback) {
        return InvalidTransactionException.liabilityConversion(key, fallback);
    }
}
