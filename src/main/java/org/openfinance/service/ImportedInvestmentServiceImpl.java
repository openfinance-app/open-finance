package org.openfinance.service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.openfinance.dto.ImportedTransaction;
import org.openfinance.dto.SkroogeImportMetadata;
import org.openfinance.dto.SkroogeImportMetadata.SkroogeHolding;
import org.openfinance.entity.Asset;
import org.openfinance.entity.Transaction;
import org.openfinance.entity.TransactionType;
import org.openfinance.repository.AssetRepository;
import org.openfinance.repository.TransactionRepository;
import org.openfinance.util.InvestmentHoldings;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
@RequiredArgsConstructor
public class ImportedInvestmentServiceImpl implements ImportedInvestmentService {
    private final AssetRepository assets;
    private final TransactionRepository transactions;
    private final AccountCurrencyService accountCurrency;

    @Override
    public void prepare(Transaction transaction, ImportedTransaction source) {
        if (source.getInvestmentSecurity() == null) return;
        String reference = securityReference(source.getInvestmentSecurity());
        String currency =
                source.getCurrency() == null ? transaction.getCurrency() : source.getCurrency();
        Asset asset =
                assets.findByAccountId(transaction.getAccountId()).stream()
                        .filter(
                                a ->
                                        a.getUserId().equals(transaction.getUserId())
                                                && reference.equals(a.getImportSource()))
                        .findFirst()
                        .orElseGet(
                                () -> newSecurityAsset(transaction, source, reference, currency));
        if (!asset.getCurrency().equals(currency)) {
            throw new IllegalArgumentException(
                    "Investment currency differs from the existing holding");
        }
        if (asset.getPurchaseDate().isAfter(transaction.getDate()))
            asset.setPurchaseDate(transaction.getDate());
        if (asset.getLastUpdated() == null
                || !transaction.getDate().isBefore(asset.getLastUpdated().toLocalDate())) {
            asset.setCurrentPrice(source.getInvestmentPrice());
            asset.setLastUpdated(transaction.getDate().atStartOfDay());
        }
        transaction.setAssetId(assets.save(asset).getId());
    }

    private Asset newSecurityAsset(
            Transaction transaction,
            ImportedTransaction source,
            String reference,
            String currency) {
        return Asset.builder()
                .userId(transaction.getUserId())
                .accountId(transaction.getAccountId())
                .importSource(reference)
                .name(source.getInvestmentName())
                .type(source.getInvestmentType())
                .quantity(BigDecimal.ZERO)
                .purchasePrice(BigDecimal.ZERO)
                .currency(currency)
                .purchaseDate(transaction.getDate())
                .importOpeningQuantity(BigDecimal.ZERO)
                .importOpeningCost(BigDecimal.ZERO)
                .build();
    }

    private String securityReference(String security) {
        try {
            byte[] digest =
                    java.security.MessageDigest.getInstance("SHA-256")
                            .digest(security.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return "security:" + java.util.HexFormat.of().formatHex(digest);
        } catch (java.security.NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is unavailable", ex);
        }
    }

    @Override
    public void importHoldings(
            SkroogeImportMetadata metadata,
            List<ImportedTransaction> reviewed,
            Map<Long, Long> accountIds,
            Long userId) {
        for (SkroogeHolding source : metadata.getHoldings()) {
            Long accountId = accountIds.get(source.sourceAccountId());
            if (accountId == null) continue;
            Map<String, ImportedTransaction> sourceRows = sourceRows(source, reviewed);
            List<Transaction> persisted =
                    transactions.findActiveByAccountId(accountId).stream()
                            .filter(
                                    tx ->
                                            tx.getUserId().equals(userId)
                                                    && matches(
                                                            tx,
                                                            sourceRows.get(
                                                                    tx.getExternalReference())))
                            .toList();
            if (persisted.isEmpty() && source.openingQuantity().signum() == 0) continue;
            Asset asset = findOrCreate(source, accountId, userId);
            for (Transaction transaction : persisted) {
                ImportedTransaction sourceRow = sourceRows.get(transaction.getExternalReference());
                transaction.setAssetId(asset.getId());
                transaction.setInvestmentQuantity(sourceRow.getInvestmentQuantity());
                accountCurrency.book(transaction, userId);
                transactions.save(transaction);
            }
            recalculate(asset.getId(), userId);
        }
    }

    private Map<String, ImportedTransaction> sourceRows(
            SkroogeHolding source, List<ImportedTransaction> reviewed) {
        return reviewed.stream()
                .filter(
                        tx ->
                                Objects.equals(tx.getSourceAccountId(), source.sourceAccountId())
                                        && Objects.equals(
                                                tx.getInvestmentUnitId(), source.sourceUnitId()))
                .collect(
                        Collectors.toMap(
                                ImportedTransaction::getReferenceNumber, tx -> tx, (a, b) -> a));
    }

    private boolean matches(Transaction stored, ImportedTransaction source) {
        return source != null
                && source.getInvestmentQuantity() != null
                && stored.getDate().equals(source.getTransactionDate())
                && sameSourceAmount(stored, source)
                && (stored.getType() == TransactionType.INCOME)
                        == (source.getAmount().signum() > 0);
    }

    private boolean sameSourceAmount(Transaction stored, ImportedTransaction source) {
        if (stored.getCurrency().equals(source.getCurrency())) {
            return stored.getAmount().compareTo(source.getAmount().abs()) == 0;
        }
        return Objects.equals(stored.getOriginalCurrency(), source.getCurrency())
                && stored.getOriginalAmount() != null
                && stored.getOriginalAmount().compareTo(source.getAmount().abs()) == 0;
    }

    private Asset findOrCreate(SkroogeHolding source, Long accountId, Long userId) {
        if (source.currentPrice() == null || source.openingPrice() == null) {
            throw new IllegalArgumentException(
                    "Missing investment price for Skrooge unit " + source.sourceUnitId());
        }
        String reference = "skrooge:unit:" + source.sourceUnitId();
        Asset asset =
                assets.findByAccountId(accountId).stream()
                        .filter(
                                a ->
                                        a.getUserId().equals(userId)
                                                && reference.equals(a.getImportSource()))
                        .findFirst()
                        .orElseGet(() -> newSkroogeAsset(source, accountId, userId, reference));
        if (!asset.getCurrency().equals(source.currency())) {
            throw new IllegalArgumentException(
                    "Imported holding currency does not match its existing position");
        }
        if (asset.getLastUpdated() == null
                || !source.priceDate().isBefore(asset.getLastUpdated().toLocalDate())) {
            asset.setCurrentPrice(source.currentPrice());
            asset.setLastUpdated(source.priceDate().atStartOfDay());
        }
        return assets.save(asset);
    }

    private Asset newSkroogeAsset(
            SkroogeHolding source, Long accountId, Long userId, String reference) {
        return Asset.builder()
                .userId(userId)
                .accountId(accountId)
                .importSource(reference)
                .name(source.name())
                .symbol(source.symbol())
                .type(source.type())
                .currency(source.currency())
                .quantity(BigDecimal.ZERO)
                .purchasePrice(BigDecimal.ZERO)
                .purchaseDate(source.openingDate())
                .importOpeningQuantity(source.openingQuantity())
                .importOpeningCost(source.openingQuantity().multiply(source.openingPrice()))
                .build();
    }

    @Override
    public void recalculate(Long assetId, Long userId) {
        Asset asset = assets.findByIdAndUserId(assetId, userId).orElseThrow();
        if (asset.getImportSource() == null) return;
        InvestmentHoldings.update(asset, transactions.findActiveByAccountId(asset.getAccountId()));
        assets.save(asset);
    }
}
