package org.openfinance.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.openfinance.dto.PropertyConversionCandidate;
import org.openfinance.dto.PropertyConversionRequest;
import org.openfinance.dto.PropertyPurchaseResponse;
import org.openfinance.dto.RealEstatePropertyRequest;
import org.openfinance.entity.Account;
import org.openfinance.entity.AcquisitionType;
import org.openfinance.entity.Asset;
import org.openfinance.entity.AssetPriceHistory;
import org.openfinance.entity.AssetType;
import org.openfinance.entity.EntityType;
import org.openfinance.entity.OperationType;
import org.openfinance.entity.PropertyConversionReceipt;
import org.openfinance.entity.RealEstateValueHistory;
import org.openfinance.entity.Transaction;
import org.openfinance.exception.AssetNotFoundException;
import org.openfinance.exception.InvalidTransactionException;
import org.openfinance.repository.AccountRepository;
import org.openfinance.repository.AssetPriceHistoryRepository;
import org.openfinance.repository.AssetRepository;
import org.openfinance.repository.PropertyConversionReceiptRepository;
import org.openfinance.repository.RealEstateRepository;
import org.openfinance.repository.RealEstateValueHistoryRepository;
import org.openfinance.repository.TransactionRepository;
import org.openfinance.service.history.ReversibleOperation;
import org.openfinance.util.ImportedPropertyValuations;
import org.openfinance.util.InvestmentHoldings;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
@RequiredArgsConstructor
public class PropertyConversionServiceImpl implements PropertyConversionService {
    private final AssetRepository assets;
    private final AccountRepository accounts;
    private final TransactionRepository transactions;
    private final AssetPriceHistoryRepository prices;
    private final RealEstateRepository properties;
    private final RealEstateValueHistoryRepository valuations;
    private final PropertyConversionReceiptRepository receipts;
    private final RealEstateService realEstate;
    private final AccountService accountService;
    private final ObjectMapper mapper;

    @Override
    @Transactional(readOnly = true)
    public List<PropertyConversionCandidate> candidates(Long userId) {
        Map<Long, Account> owners =
                accounts.findByUserId(userId).stream()
                        .collect(Collectors.toMap(Account::getId, account -> account));
        return assets.findByUserId(userId).stream()
                .filter(asset -> eligible(asset, owners.get(asset.getAccountId())))
                .sorted(Comparator.comparing(Asset::getName, String.CASE_INSENSITIVE_ORDER))
                .map(asset -> candidate(asset, owners.get(asset.getAccountId())))
                .toList();
    }

    private boolean eligible(Asset asset, Account account) {
        return asset.getImportSource() != null
                && asset.getType() != AssetType.REAL_ESTATE
                && !asset.isPhysical()
                && asset.getSoldDate() == null
                && asset.getAcquisitionType() != AcquisitionType.PLANNED
                && asset.getQuantity().signum() > 0
                && asset.getPurchaseDate() != null
                && !asset.getPurchaseDate().isAfter(LocalDate.now())
                && account != null
                && account.isPresentAt(LocalDate.now());
    }

    private List<Transaction> movements(Asset asset) {
        return transactions.findByAssetIdAndUserId(asset.getId(), asset.getUserId()).stream()
                .filter(tx -> !Boolean.TRUE.equals(tx.getIsDeleted()))
                .sorted(
                        Comparator.comparing(Transaction::getDate)
                                .thenComparing(Transaction::getId))
                .toList();
    }

    private PropertyConversionCandidate candidate(Asset asset, Account account) {
        List<Transaction> movements = movements(asset);
        List<AssetPriceHistory> history =
                prices.findByAssetIdAndUserIdOrderByPriceDateAsc(asset.getId(), asset.getUserId());
        boolean closesSourceAccount = closesSourceAccount(asset, account);
        return new PropertyConversionCandidate(
                asset.getId(),
                asset.getName(),
                account.getName(),
                closesSourceAccount,
                asset.getCurrency(),
                asset.getTotalValue(),
                asset.getTotalCost(),
                asset.getPurchaseDate(),
                movements.size(),
                ImportedPropertyValuations.history(asset, movements, history, LocalDate.now())
                        .size(),
                revision(asset, movements, history, account, closesSourceAccount));
    }

    @Override
    @CacheEvict(
            value = {
                "dashboardSummary",
                "accountSummaries",
                "netWorthSummary",
                "networthAllocation",
                "assetAllocation",
                "portfolioPerformance"
            },
            allEntries = true)
    @ReversibleOperation(
            entity = EntityType.REAL_ESTATE,
            operation = OperationType.CREATE,
            userArgument = 0)
    public PropertyPurchaseResponse convert(Long userId, PropertyConversionRequest request) {
        String hash = fingerprint(request);
        PropertyConversionReceipt receipt =
                receipts.findByUserIdAndOperationId(userId, request.operationId().toString())
                        .orElse(null);
        if (receipt != null) return replay(userId, receipt, hash);
        Asset asset =
                assets.findByIdAndUserId(request.assetId(), userId)
                        .orElseThrow(
                                () ->
                                        AssetNotFoundException.byIdAndUser(
                                                request.assetId(), userId));
        Account account =
                asset.getAccountId() == null
                        ? null
                        : accounts.findByIdAndUserId(asset.getAccountId(), userId).orElse(null);
        if (!eligible(asset, account)
                || properties.findByAssetIdAndUserId(asset.getId(), userId).isPresent())
            throw invalid(
                    "source", "Choose an imported holding that has not already been converted");
        List<Transaction> movements = movements(asset);
        List<AssetPriceHistory> history =
                prices.findByAssetIdAndUserIdOrderByPriceDateAsc(asset.getId(), userId);
        boolean closesSourceAccount = closesSourceAccount(asset, account);
        if (!revision(asset, movements, history, account, closesSourceAccount)
                .equals(request.sourceRevision()))
            throw invalid(
                    "changed", "The holding changed. Select it again to review the updated values");
        if (movements.stream().anyMatch(tx -> tx.getDate().isAfter(LocalDate.now())))
            throw invalid(
                    "future", "Resolve future investment movements before converting this holding");
        List<ImportedPropertyValuations.Point> values =
                ImportedPropertyValuations.history(asset, movements, history, LocalDate.now());
        RealEstatePropertyRequest property = propertyRequest(asset, movements, request);
        Long propertyId =
                realEstate.createPropertyFromImportedAsset(userId, property, asset.getId()).getId();
        copyValuations(userId, propertyId, values);
        if (closesSourceAccount && Boolean.TRUE.equals(account.getIsActive()))
            accountService.closeAccount(account.getId(), userId);
        receipt = new PropertyConversionReceipt();
        receipt.setUserId(userId);
        receipt.setOperationId(request.operationId().toString());
        receipt.setRequestHash(hash);
        receipt.setAssetId(asset.getId());
        receipt.setPropertyId(propertyId);
        receipts.save(receipt);
        return new PropertyPurchaseResponse(propertyId, request.mortgageId());
    }

    private boolean closesSourceAccount(Asset source, Account account) {
        if (!Boolean.TRUE.equals(account.getIsActive())) return true;
        if (account.getBalance().signum() != 0
                || accounts.isLiabilityBalanceSource(account.getId(), account.getUserId()))
            return false;
        // Shared accounts remain useful until their other financial holdings are gone.
        return assets.findByAccountId(account.getId()).stream()
                .filter(asset -> !asset.getId().equals(source.getId()))
                .filter(asset -> asset.getType() != AssetType.REAL_ESTATE && !asset.isPhysical())
                .noneMatch(
                        asset ->
                                asset.getSoldDate() == null
                                        && (asset.getQuantity().signum() > 0
                                                || asset.getAcquisitionType()
                                                        == AcquisitionType.PLANNED));
    }

    private PropertyPurchaseResponse replay(
            Long userId, PropertyConversionReceipt receipt, String hash) {
        if (!receipt.getRequestHash().equals(hash))
            throw invalid(
                    "retry", "This conversion identifier was already used for different details");
        return properties
                .findByIdAndUserId(receipt.getPropertyId(), userId)
                .map(
                        property ->
                                new PropertyPurchaseResponse(
                                        property.getId(), property.getMortgageId()))
                .orElseThrow(
                        () ->
                                invalid(
                                        "undone",
                                        "This conversion was undone. Start a new conversion"));
    }

    private RealEstatePropertyRequest propertyRequest(
            Asset asset, List<Transaction> movements, PropertyConversionRequest request) {
        return RealEstatePropertyRequest.builder()
                .name(request.name().trim())
                .address(request.address().trim())
                .propertyType(request.propertyType())
                .purchasePrice(asset.getTotalCost())
                .currentValue(asset.getTotalValue())
                .purchaseDate(asset.getPurchaseDate())
                .currency(asset.getCurrency())
                .acquisitionType(asset.getAcquisitionType())
                .receivedValue(
                        InvestmentHoldings.at(asset, movements, LocalDate.now())
                                .remainingReceivedValue())
                .notes(asset.getNotes())
                .mortgageId(request.mortgageId())
                .rentalIncome(request.rentalIncome())
                .isActive(true)
                .build();
    }

    private void copyValuations(
            Long userId, Long propertyId, List<ImportedPropertyValuations.Point> values) {
        // Replace the default creation appraisal with the complete imported timeline.
        valuations.deleteAll(
                valuations.findByPropertyIdAndUserIdOrderByEffectiveDateAsc(propertyId, userId));
        for (ImportedPropertyValuations.Point value : values)
            valuations.save(
                    RealEstateValueHistory.builder()
                            .propertyId(propertyId)
                            .userId(userId)
                            .effectiveDate(value.date())
                            .recordedValue(value.value().toPlainString())
                            .currency(value.currency())
                            .build());
    }

    private String revision(
            Asset asset,
            List<Transaction> movements,
            List<AssetPriceHistory> history,
            Account account,
            boolean closesSourceAccount) {
        // Only scalar financial state; never serialize JPA relationships or encryption keys.
        return fingerprint(
                java.util.Arrays.asList(
                        asset.getId(),
                        asset.getName(),
                        asset.getAccountId(),
                        account.getName(),
                        account.getBalance(),
                        account.getIsActive(),
                        closesSourceAccount,
                        asset.getType(),
                        asset.getCurrency(),
                        asset.getQuantity(),
                        asset.getPurchasePrice(),
                        asset.getPurchaseDate(),
                        asset.getCurrentPrice(),
                        asset.getLastUpdated(),
                        asset.getImportSource(),
                        asset.getImportOpeningQuantity(),
                        asset.getImportOpeningCost(),
                        asset.getAcquisitionType(),
                        asset.getReceivedValue(),
                        asset.getNotes(),
                        movements.stream()
                                .map(
                                        tx ->
                                                java.util.Arrays.asList(
                                                        tx.getId(),
                                                        tx.getDate(),
                                                        tx.getType(),
                                                        tx.getAmount(),
                                                        tx.getCurrency(),
                                                        tx.getOriginalAmount(),
                                                        tx.getOriginalCurrency(),
                                                        tx.getInvestmentQuantity(),
                                                        tx.getInvestmentValue(),
                                                        tx.getInvestmentPrice(),
                                                        tx.getInvestmentPriceDate(),
                                                        tx.isInvestmentCash()))
                                .toList(),
                        history.stream()
                                .map(
                                        price ->
                                                java.util.Arrays.asList(
                                                        price.getPriceDate(),
                                                        price.getPrice(),
                                                        price.getCurrency(),
                                                        price.getSource()))
                                .toList()));
    }

    private String fingerprint(Object value) {
        try {
            return HexFormat.of()
                    .formatHex(
                            MessageDigest.getInstance("SHA-256")
                                    .digest(mapper.writeValueAsBytes(value)));
        } catch (JsonProcessingException | NoSuchAlgorithmException ex) {
            throw new IllegalStateException("Cannot identify property conversion", ex);
        }
    }

    private InvalidTransactionException invalid(String key, String fallback) {
        return InvalidTransactionException.investment("propertyConversion." + key, fallback);
    }
}
