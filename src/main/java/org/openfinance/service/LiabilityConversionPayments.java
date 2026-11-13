package org.openfinance.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.Normalizer;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.openfinance.dto.LiabilityConversionPayment;
import org.openfinance.dto.LiabilityConversionPaymentCandidate;
import org.openfinance.entity.Account;
import org.openfinance.entity.Category;
import org.openfinance.entity.Liability;
import org.openfinance.entity.LoanComponent;
import org.openfinance.entity.MovementType;
import org.openfinance.entity.Transaction;
import org.openfinance.entity.TransactionSplit;
import org.openfinance.entity.TransactionType;
import org.openfinance.exception.InvalidTransactionException;
import org.openfinance.repository.TransactionRepository;
import org.openfinance.repository.TransactionSplitRepository;
import org.openfinance.util.LoanPostingPolicy;
import org.springframework.stereotype.Component;

/** Reviews and attaches already booked payments within the conversion's single transaction. */
@Component
@RequiredArgsConstructor
public class LiabilityConversionPayments {
    private final TransactionRepository transactions;
    private final TransactionSplitRepository splits;
    private final ObjectMapper mapper;

    public record Candidates(List<LiabilityConversionPaymentCandidate> items, boolean truncated) {}

    public Candidates candidates(Account source, String search) {
        String needle = normalize(search == null || search.isBlank() ? source.getName() : search);
        List<Transaction> matches =
                transactions
                        .findByUserIdAndType(source.getUserId(), TransactionType.EXPENSE)
                        .stream()
                        .filter(tx -> eligible(source, tx))
                        .filter(tx -> searchable(tx).contains(needle))
                        .limit(101)
                        .toList();
        return new Candidates(
                matches.stream().limit(100).map(this::candidate).toList(), matches.size() > 100);
    }

    private boolean eligible(Account source, Transaction tx) {
        return Objects.equals(source.getUserId(), tx.getUserId())
                && !Objects.equals(source.getId(), tx.getAccountId())
                && !Boolean.TRUE.equals(tx.getIsDeleted())
                && tx.getType() == TransactionType.EXPENSE
                && tx.getTransferId() == null
                && tx.getToAccountId() == null
                && tx.getLiabilityId() == null
                && tx.getTrancheId() == null
                && tx.getRealEstateId() == null
                && tx.getAssetId() == null
                && tx.getInvestmentQuantity() == null
                && !tx.getDate().isAfter(LocalDate.now())
                && source.getCurrency().equals(tx.getCurrency())
                && (tx.getOriginalCurrency() == null
                        || source.getCurrency().equals(tx.getOriginalCurrency()));
    }

    private String searchable(Transaction tx) {
        return normalize(
                String.join(
                        " ",
                        Objects.toString(tx.getDescription(), ""),
                        Objects.toString(tx.getNotes(), ""),
                        Objects.toString(tx.getPayee(), ""),
                        Objects.toString(tx.getTags(), "")));
    }

    private static String normalize(String text) {
        return Normalizer.normalize(text, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("(?<!\\d)0+(?=\\d)", "")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private LiabilityConversionPaymentCandidate candidate(Transaction tx) {
        List<TransactionSplit> parts = splits.findByTransactionIdOrderById(tx.getId());
        Category category = tx.getCategory();
        return new LiabilityConversionPaymentCandidate(
                tx.getId(),
                revision(tx, parts),
                tx.getDate(),
                tx.getDescription(),
                tx.getNotes(),
                tx.getAccount().getName(),
                tx.getCurrency(),
                tx.getAmount(),
                name(category),
                key(category),
                suggested(category),
                parts.stream()
                        .map(
                                part ->
                                        new LiabilityConversionPaymentCandidate.Split(
                                                part.getId(),
                                                part.getAmount(),
                                                name(part.getCategory()),
                                                key(part.getCategory()),
                                                part.getLoanComponent() == null
                                                        ? suggested(part.getCategory())
                                                        : part.getLoanComponent()))
                        .toList());
    }

    private static String name(Category category) {
        return category == null ? null : category.getName();
    }

    private static String key(Category category) {
        return category == null ? null : category.getNameKey();
    }

    private static LoanComponent suggested(Category category) {
        if (category == null) return null;
        String text =
                normalize(
                        Objects.toString(category.getNameKey(), "")
                                + " "
                                + Objects.toString(category.getName(), ""));
        if (text.contains("interest") || text.contains("interet")) return LoanComponent.INTEREST;
        if (text.contains("insurance") || text.contains("assurance"))
            return LoanComponent.INSURANCE;
        if (text.contains("fee") || text.contains("frais") || text.contains("commission"))
            return LoanComponent.FEE;
        if (text.contains("category.loan.repayment")
                || text.equals("capital")
                || text.equals("principal")) return LoanComponent.PRINCIPAL;
        return null;
    }

    public record ReviewedPayment(
            Transaction transaction,
            List<TransactionSplit> splits,
            LiabilityConversionPayment selection,
            BigDecimal principal) {}

    public List<ReviewedPayment> review(
            Account source,
            List<LiabilityConversionPayment> selections,
            BigDecimal previouslyPaidPrincipal) {
        if (selections == null || selections.isEmpty()) return List.of();
        Set<Long> seen = new HashSet<>();
        List<ReviewedPayment> result = new ArrayList<>();
        for (LiabilityConversionPayment selection : selections) {
            Transaction tx =
                    transactions
                            .findByIdAndUserId(selection.transactionId(), source.getUserId())
                            .orElseThrow(
                                    () ->
                                            invalid(
                                                    "payment",
                                                    "Choose an available payment belonging to this user"));
            if (!seen.add(tx.getId()) || !eligible(source, tx))
                throw invalid(
                        "payment",
                        "Choose an unlinked expense in the loan currency, outside the source account");
            List<TransactionSplit> parts = splits.findByTransactionIdOrderById(tx.getId());
            if (!revision(tx, parts).equals(selection.sourceRevision()))
                throw invalid(
                        "paymentChanged",
                        "A selected payment changed. Reload the payments and review them again");
            result.add(new ReviewedPayment(tx, parts, selection, principal(tx, parts, selection)));
        }
        BigDecimal linkedPrincipal =
                result.stream()
                        .map(ReviewedPayment::principal)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (linkedPrincipal.compareTo(previouslyPaidPrincipal) > 0)
            throw invalid(
                    "paymentPrincipal",
                    "Selected principal repayments exceed the principal already repaid. Review the borrowed total and payment components");
        return result;
    }

    private BigDecimal principal(
            Transaction tx, List<TransactionSplit> parts, LiabilityConversionPayment selection) {
        List<LiabilityConversionPayment.Split> selected =
                selection.splits() == null ? List.of() : selection.splits();
        if (parts.isEmpty()) {
            if (selection.component() == null || !selected.isEmpty()) throw components();
            return selection.component() == LoanComponent.PRINCIPAL
                    ? tx.getAmount()
                    : BigDecimal.ZERO;
        }
        Set<Long> actualIds =
                parts.stream().map(TransactionSplit::getId).collect(Collectors.toSet());
        Set<Long> selectedIds =
                selected.stream()
                        .map(LiabilityConversionPayment.Split::splitId)
                        .collect(Collectors.toSet());
        if (selection.component() != null
                || selected.size() != parts.size()
                || !actualIds.equals(selectedIds)
                || selected.stream().anyMatch(part -> part.component() == null)
                || parts.stream()
                                .map(TransactionSplit::getAmount)
                                .reduce(BigDecimal.ZERO, BigDecimal::add)
                                .compareTo(tx.getAmount())
                        != 0) throw components();
        Map<Long, LoanComponent> components =
                selected.stream()
                        .collect(
                                Collectors.toMap(
                                        LiabilityConversionPayment.Split::splitId,
                                        LiabilityConversionPayment.Split::component));
        return parts.stream()
                .filter(part -> components.get(part.getId()) == LoanComponent.PRINCIPAL)
                .map(TransactionSplit::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public void associate(Liability loan, List<ReviewedPayment> reviewed) {
        for (ReviewedPayment item : reviewed) {
            Transaction tx = item.transaction();
            if (!LoanPostingPolicy.isHistorical(loan, tx.getDate(), tx.getCreatedAt()))
                throw components();
            tx.setLiabilityId(loan.getId());
            tx.setPrincipalAmount(item.principal());
            tx.setMovementType(movement(item.selection().component(), item.splits().isEmpty()));
            transactions.save(tx);
            Map<Long, LoanComponent> components =
                    (item.selection().splits() == null
                                    ? List.<LiabilityConversionPayment.Split>of()
                                    : item.selection().splits())
                            .stream()
                                    .collect(
                                            Collectors.toMap(
                                                    LiabilityConversionPayment.Split::splitId,
                                                    LiabilityConversionPayment.Split::component));
            for (TransactionSplit part : item.splits())
                part.setLoanComponent(components.get(part.getId()));
            splits.saveAll(item.splits());
        }
    }

    private MovementType movement(LoanComponent component, boolean unsplit) {
        if (!unsplit) return MovementType.REPAYMENT;
        return switch (component) {
            case INTEREST -> MovementType.INTEREST;
            case INSURANCE -> MovementType.INSURANCE;
            case FEE -> MovementType.FEE;
            default -> MovementType.REPAYMENT;
        };
    }

    private String revision(Transaction tx, List<TransactionSplit> parts) {
        try {
            Object state =
                    Arrays.asList(
                            tx.getId(),
                            tx.getUserId(),
                            tx.getAccountId(),
                            tx.getType(),
                            tx.getAmount(),
                            tx.getCurrency(),
                            tx.getOriginalAmount(),
                            tx.getOriginalCurrency(),
                            tx.getConversionRate(),
                            tx.getDate(),
                            tx.getCreatedAt(),
                            tx.getUpdatedAt(),
                            tx.getCategoryId(),
                            tx.getDescription(),
                            tx.getNotes(),
                            tx.getTags(),
                            tx.getLiabilityId(),
                            tx.getMovementType(),
                            tx.getTransferId(),
                            tx.getAssetId(),
                            tx.getRealEstateId(),
                            tx.getIsDeleted(),
                            parts.stream()
                                    .map(
                                            part ->
                                                    Arrays.asList(
                                                            part.getId(),
                                                            part.getAmount(),
                                                            part.getCategoryId(),
                                                            part.getLoanComponent(),
                                                            part.getDescription(),
                                                            part.getTags(),
                                                            part.getUpdatedAt()))
                                    .toList());
            return HexFormat.of()
                    .formatHex(
                            MessageDigest.getInstance("SHA-256")
                                    .digest(
                                            mapper.writeValueAsString(state)
                                                    .getBytes(StandardCharsets.UTF_8)));
        } catch (JsonProcessingException | NoSuchAlgorithmException ex) {
            throw new IllegalStateException("Cannot identify a conversion payment", ex);
        }
    }

    private InvalidTransactionException components() {
        return invalid(
                "paymentComponents",
                "Assign a loan component to every selected payment and split without changing the booked amounts");
    }

    private InvalidTransactionException invalid(String key, String fallback) {
        return InvalidTransactionException.liabilityConversion(key, fallback);
    }
}
