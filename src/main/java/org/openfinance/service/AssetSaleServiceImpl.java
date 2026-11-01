package org.openfinance.service;

import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.openfinance.dto.AssetSaleRequest;
import org.openfinance.dto.InvestmentMovementRequest;
import org.openfinance.dto.TransactionRequest;
import org.openfinance.entity.Account;
import org.openfinance.entity.AcquisitionType;
import org.openfinance.entity.Asset;
import org.openfinance.entity.AssetType;
import org.openfinance.entity.EntityType;
import org.openfinance.entity.OperationType;
import org.openfinance.entity.RealEstateProperty;
import org.openfinance.entity.TransactionType;
import org.openfinance.exception.InvalidTransactionException;
import org.openfinance.repository.AccountRepository;
import org.openfinance.repository.AssetRepository;
import org.openfinance.repository.NetWorthRepository;
import org.openfinance.repository.RealEstateRepository;
import org.openfinance.service.history.ReversibleOperation;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
@RequiredArgsConstructor
public class AssetSaleServiceImpl implements AssetSaleService {
    private final AssetRepository assets;
    private final RealEstateRepository properties;
    private final AccountRepository accounts;
    private final TransactionService transactions;
    private final NetWorthRepository snapshots;
    private final OwnershipReceipts receipts;
    private final org.openfinance.repository.TransactionRepository payments;

    @Override
    @CacheEvict(
            value = {
                "dashboardSummary",
                "netWorthSummary",
                "networthAllocation",
                "portfolioPerformance",
                "borrowingCapacity"
            },
            allEntries = true)
    @ReversibleOperation(
            entity = EntityType.ASSET,
            operation = OperationType.UPDATE,
            userArgument = 0,
            idArgument = 1)
    public Long sell(Long userId, Long assetId, AssetSaleRequest request) {
        String hash = receipts.fingerprint(List.of("sale", assetId, request));
        Long previous = receipts.previous(userId, request.getOperationId(), hash);
        if (previous != null) return previous;
        Asset asset =
                assets.findByIdAndUserId(assetId, userId)
                        .orElseThrow(() -> invalid("Asset not found"));
        RealEstateProperty property =
                asset.getType() == AssetType.REAL_ESTATE
                        ? properties
                                .findByAssetIdAndUserId(assetId, userId)
                                .orElseThrow(() -> invalid("Property not found"))
                        : null;
        validate(userId, asset, property, request);
        boolean financial = !asset.isPhysical() && property == null;
        TransactionRequest payment = payment(asset, property, request, financial);
        Long transactionId = transactions.createTransaction(userId, payment).getId();
        org.openfinance.entity.Transaction savedPayment =
                payments.findByIdAndUserId(transactionId, userId).orElseThrow();
        savedPayment.setSaleFees(request.getFees());
        payments.save(savedPayment);
        if (!financial) close(asset, property, request, transactionId);
        snapshots.deleteByUserIdAndSnapshotDateBetween(
                userId, request.getDate().withDayOfMonth(1), LocalDate.now());
        receipts.remember(userId, request.getOperationId(), hash, transactionId);
        return transactionId;
    }

    private void validate(
            Long userId, Asset asset, RealEstateProperty property, AssetSaleRequest request) {
        if (asset.getAcquisitionType() == AcquisitionType.PLANNED
                || asset.getSoldDate() != null
                || (property != null && (!property.isActive() || property.getSoldDate() != null))
                || request.getDate().isBefore(asset.getPurchaseDate())
                || request.getDate().isAfter(LocalDate.now())
                || request.getFees().compareTo(request.getProceeds()) >= 0) {
            throw invalid(
                    "Sell an owned asset on or after acquisition, with positive net proceeds");
        }
        if (request.getQuantity().compareTo(asset.getQuantity()) > 0
                || request.getQuantity().signum() <= 0
                || ((asset.isPhysical() || property != null)
                        && request.getQuantity().compareTo(asset.getQuantity()) != 0)) {
            throw invalid(
                    "Choose available units; physical assets and properties are sold as a whole");
        }
        Account account =
                accounts.findByIdAndUserId(request.getAccountId(), userId)
                        .orElseThrow(() -> invalid("Receiving account not found"));
        if (!Boolean.TRUE.equals(account.getIsActive())
                || !account.getCurrency().equals(asset.getCurrency())) {
            throw invalid("The receiving account must be active and use the asset currency");
        }
        if (!asset.isPhysical()
                && property == null
                && !request.getAccountId().equals(asset.getAccountId())) {
            throw invalid("Investment proceeds must be received in the holding's linked account");
        }
    }

    private TransactionRequest payment(
            Asset asset, RealEstateProperty property, AssetSaleRequest request, boolean financial) {
        TransactionRequest.TransactionRequestBuilder payment =
                TransactionRequest.builder()
                        .type(TransactionType.INCOME)
                        .accountId(request.getAccountId())
                        .date(request.getDate())
                        .amount(request.getProceeds().subtract(request.getFees()))
                        .currency(asset.getCurrency())
                        .description(
                                request.getDescription() == null
                                        ? asset.getName()
                                        : request.getDescription())
                        .notes(request.getNotes());
        if (property != null) payment.realEstateId(property.getId());
        else payment.assetId(asset.getId());
        if (financial)
            payment.investment(
                    InvestmentMovementRequest.builder()
                            .assetId(asset.getId())
                            .operation(InvestmentMovementRequest.Operation.SELL)
                            .pricing(InvestmentMovementRequest.Pricing.QUANTITY)
                            .quantity(request.getQuantity())
                            .build());
        return payment.build();
    }

    private void close(
            Asset asset,
            RealEstateProperty property,
            AssetSaleRequest request,
            Long transactionId) {
        asset.setSoldDate(request.getDate());
        asset.setSaleTransactionId(transactionId);
        assets.save(asset);
        if (property != null) {
            property.setSoldDate(request.getDate());
            property.setSaleTransactionId(transactionId);
            properties.save(property);
        }
    }

    private InvalidTransactionException invalid(String message) {
        return new InvalidTransactionException(message);
    }
}
