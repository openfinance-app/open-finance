package org.openfinance.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.openfinance.entity.AcquisitionType;
import org.openfinance.entity.Asset;
import org.openfinance.entity.AssetType;
import org.openfinance.entity.EntityType;
import org.openfinance.entity.OperationType;
import org.openfinance.entity.Transaction;
import org.openfinance.entity.TransactionType;
import org.openfinance.exception.InvalidTransactionException;
import org.openfinance.repository.AssetRepository;
import org.openfinance.repository.RealEstateRepository;
import org.openfinance.repository.TransactionRepository;
import org.openfinance.service.history.ReversibleOperation;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
@RequiredArgsConstructor
public class PurchasePaymentServiceImpl implements PurchasePaymentService {
    private final TransactionRepository transactions;
    private final AssetRepository assets;
    private final RealEstateRepository properties;

    @Override
    @ReversibleOperation(
            entity = EntityType.TRANSACTION,
            operation = OperationType.UPDATE,
            userArgument = 0,
            idArgument = 2)
    public void linkExisting(Long userId, Long assetId, Long transactionId) {
        Asset item =
                assets.findByIdAndUserId(assetId, userId)
                        .orElseThrow(() -> invalid("Asset not found"));
        if ((!item.isPhysical() && item.getType() != AssetType.REAL_ESTATE)
                || item.getAcquisitionType() != AcquisitionType.PURCHASE
                || item.getSoldDate() != null) {
            throw invalid("Link a purchase payment to an owned physical asset or property");
        }
        Transaction payment =
                transactions
                        .findByIdAndUserId(transactionId, userId)
                        .orElseThrow(() -> invalid("Purchase payment not found"));
        Long propertyId =
                item.getType() == AssetType.REAL_ESTATE
                        ? properties
                                .findByAssetIdAndUserId(assetId, userId)
                                .filter(property -> property.isActive())
                                .orElseThrow(() -> invalid("Property not found or inactive"))
                                .getId()
                        : null;
        link(
                userId,
                transactionId,
                payment.getAccountId(),
                item.getCurrency(),
                payment.getAmount(),
                payment.getDate(),
                propertyId == null ? assetId : null,
                propertyId);
    }

    @Override
    public void link(
            Long userId,
            Long transactionId,
            Long accountId,
            String currency,
            BigDecimal amount,
            LocalDate date,
            Long assetId,
            Long propertyId) {
        Transaction payment =
                transactions
                        .findByIdAndUserId(transactionId, userId)
                        .orElseThrow(() -> invalid("Purchase payment not found"));
        if (Boolean.TRUE.equals(payment.getIsDeleted())
                || payment.getType() != TransactionType.EXPENSE
                || payment.getTransferId() != null
                || payment.getInvestmentQuantity() != null
                || payment.getLiabilityId() != null
                || payment.getMovementType() != null
                || (payment.getAssetId() != null && !Objects.equals(payment.getAssetId(), assetId))
                || (payment.getRealEstateId() != null
                        && !Objects.equals(payment.getRealEstateId(), propertyId))) {
            throw invalid("Choose an ordinary expense that is not linked to another operation");
        }
        if (!Objects.equals(payment.getAccountId(), accountId)
                || !Objects.equals(payment.getCurrency(), currency)
                || payment.getAmount().compareTo(amount) != 0
                || payment.getDate().isAfter(date)
                || payment.getDate().isAfter(LocalDate.now())) {
            throw invalid(
                    "The payment must match the account, currency and amount, and occur by the purchase date");
        }
        payment.setAssetId(assetId);
        payment.setRealEstateId(propertyId);
        transactions.save(payment);
    }

    private InvalidTransactionException invalid(String message) {
        return new InvalidTransactionException(message);
    }
}
