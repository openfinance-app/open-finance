package org.openfinance.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.openfinance.dto.AssetPurchaseRequest;
import org.openfinance.dto.AssetRequest;
import org.openfinance.dto.AssetResponse;
import org.openfinance.dto.LiabilityRequest;
import org.openfinance.dto.LiabilityResponse;
import org.openfinance.dto.PropertyPurchaseRequest.Route;
import org.openfinance.dto.TransactionRequest;
import org.openfinance.entity.Account;
import org.openfinance.entity.AcquisitionType;
import org.openfinance.entity.EntityType;
import org.openfinance.entity.OperationType;
import org.openfinance.entity.TransactionType;
import org.openfinance.exception.InvalidTransactionException;
import org.openfinance.repository.AccountRepository;
import org.openfinance.service.history.ReversibleOperation;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
@RequiredArgsConstructor
public class AssetPurchaseServiceImpl implements AssetPurchaseService {
    private final AssetService assets;
    private final AccountRepository accounts;
    private final TransactionService transactions;
    private final PurchasePaymentService payments;
    private final OwnershipReceipts receipts;
    private final LiabilityService liabilities;
    private final PurchaseLoanService funding;

    @Override
    @ReversibleOperation(
            entity = EntityType.ASSET,
            operation = OperationType.UPDATE,
            userArgument = 0,
            idArgument = 1)
    public Long buyMore(Long userId, Long assetId, org.openfinance.dto.AssetBuyRequest request) {
        String hash = receipts.fingerprint(java.util.List.of("buy", assetId, request));
        Long previous = receipts.previous(userId, request.getOperationId(), hash);
        if (previous != null) return previous;
        AssetResponse asset = assets.getAssetById(assetId, userId);
        if (asset.getType().isPhysical()
                || asset.getType() == org.openfinance.entity.AssetType.REAL_ESTATE
                || asset.getAcquisitionType() == AcquisitionType.PLANNED
                || asset.getAccountId() == null) {
            throw new InvalidTransactionException(
                    "Choose an investment holding with a linked account");
        }
        Account account = accounts.findByIdAndUserId(asset.getAccountId(), userId).orElseThrow();
        if (!Boolean.TRUE.equals(account.getIsActive())
                || !account.getCurrency().equals(asset.getCurrency())) {
            throw new InvalidTransactionException(
                    "Use the transaction form to review a cross-currency trade");
        }
        Long transactionId =
                transactions
                        .createTransaction(
                                userId,
                                TransactionRequest.builder()
                                        .accountId(account.getId())
                                        .currency(asset.getCurrency())
                                        .date(request.getDate())
                                        .type(TransactionType.EXPENSE)
                                        .amount(request.getAmount())
                                        .description(asset.getName())
                                        .investment(
                                                org.openfinance.dto.InvestmentMovementRequest
                                                        .builder()
                                                        .assetId(assetId)
                                                        .operation(
                                                                org.openfinance.dto
                                                                        .InvestmentMovementRequest
                                                                        .Operation.BUY)
                                                        .pricing(
                                                                org.openfinance.dto
                                                                        .InvestmentMovementRequest
                                                                        .Pricing.QUANTITY)
                                                        .quantity(request.getQuantity())
                                                        .build())
                                        .build())
                        .getId();
        receipts.remember(userId, request.getOperationId(), hash, transactionId);
        return transactionId;
    }

    @Override
    @ReversibleOperation(
            entity = EntityType.ASSET,
            operation = OperationType.CREATE,
            userArgument = 0)
    public Long purchase(Long userId, AssetPurchaseRequest request) {
        String hash = receipts.fingerprint(request);
        Long previous = receipts.previous(userId, request.getOperationId(), hash);
        if (previous != null) return previous;
        AssetRequest item = request.getAsset();
        BigDecimal price = validate(userId, request);
        Long loanId = loan(userId, request);
        Long id = acquire(userId, request);
        if (loanId != null)
            funding.fund(
                    userId,
                    loanId,
                    request.getLoanAmount(),
                    item.getCurrency(),
                    item.getPurchaseDate(),
                    request.getRoute(),
                    request.getAccountId(),
                    request.getExistingLoanReceiptId(),
                    id,
                    null);
        BigDecimal cashPayment =
                request.getRoute() == Route.DIRECT
                        ? price.subtract(request.getLoanAmount())
                        : price;
        if (request.getExistingPaymentId() != null) {
            payments.link(
                    userId,
                    request.getExistingPaymentId(),
                    request.getAccountId(),
                    item.getCurrency(),
                    cashPayment,
                    item.getPurchaseDate(),
                    id,
                    null);
        } else if (cashPayment.signum() > 0) {
            transactions.createTransaction(
                    userId,
                    TransactionRequest.builder()
                            .accountId(request.getAccountId())
                            .type(TransactionType.EXPENSE)
                            .amount(cashPayment)
                            .currency(item.getCurrency())
                            .date(item.getPurchaseDate())
                            .description(
                                    request.getPaymentDescription() == null
                                            ? item.getName()
                                            : request.getPaymentDescription())
                            .assetId(id)
                            .build());
        }
        receipts.remember(userId, request.getOperationId(), hash, id);
        return id;
    }

    private BigDecimal validate(Long userId, AssetPurchaseRequest request) {
        AssetRequest item = request.getAsset();
        BigDecimal price = item.getQuantity().multiply(item.getPurchasePrice());
        if (!item.getType().isPhysical()
                || price.signum() <= 0
                || item.getPurchaseDate().isAfter(LocalDate.now())
                || (item.getAcquisitionType() != null
                        && item.getAcquisitionType() != AcquisitionType.PURCHASE)) {
            throw new InvalidTransactionException(
                    "Choose a physical asset, positive purchase price and completed purchase date");
        }
        boolean financed = request.getLoanAmount().signum() > 0;
        int sources =
                (request.getNewLoan() != null ? 1 : 0)
                        + (request.getExistingLoanId() != null ? 1 : 0);
        if ((financed ? sources != 1 : sources != 0)
                || request.getLoanAmount().compareTo(price) > 0
                || (request.getExistingLoanReceiptId() != null
                        && request.getExistingLoanId() == null)) {
            throw new InvalidTransactionException(
                    "Choose one loan source within the purchase price");
        }
        BigDecimal cash =
                request.getRoute() == Route.DIRECT
                        ? price.subtract(request.getLoanAmount())
                        : price;
        if (cash.signum() == 0) {
            if (request.getExistingPaymentId() != null)
                throw new InvalidTransactionException("No account payment to link");
            return price;
        }
        Account account =
                accounts.findByIdAndUserId(request.getAccountId(), userId)
                        .orElseThrow(
                                () -> new InvalidTransactionException("Payment account not found"));
        if (!Boolean.TRUE.equals(account.getIsActive())
                || !account.getCurrency().equals(item.getCurrency())) {
            throw new InvalidTransactionException(
                    "The payment account must be active and use the asset currency");
        }
        return price;
    }

    private Long loan(Long userId, AssetPurchaseRequest request) {
        AssetRequest item = request.getAsset();
        if (request.getNewLoan() != null) {
            LiabilityRequest loan = request.getNewLoan();
            if (loan.getType() == org.openfinance.entity.LiabilityType.CREDIT_CARD
                    || loan.getCurrentBalance().signum() != 0
                    || loan.getPrincipal().compareTo(request.getLoanAmount()) != 0
                    || !loan.getCurrency().equals(item.getCurrency())
                    || !loan.getStartDate().equals(item.getPurchaseDate())
                    || Boolean.TRUE.equals(loan.getPreviouslyFunded())
                    || loan.getRealEstateId() != null
                    || loan.getRepresentedByAccountId() != null) {
                throw new InvalidTransactionException(
                        "The new loan must match the purchase and start undrawn");
            }
            return liabilities.createLiability(userId, loan).getId();
        }
        if (request.getExistingLoanId() != null) {
            LiabilityResponse loan =
                    liabilities.getLiabilityById(request.getExistingLoanId(), userId);
            if (loan.getType() == org.openfinance.entity.LiabilityType.CREDIT_CARD
                    || !loan.getCurrency().equals(item.getCurrency())) {
                throw new InvalidTransactionException("Choose a loan using the asset currency");
            }
        }
        return request.getExistingLoanId();
    }

    private Long acquire(Long userId, AssetPurchaseRequest request) {
        if (request.getPlannedAssetId() == null)
            return assets.createAsset(userId, request.getAsset()).getId();
        AssetResponse plan = assets.getAssetById(request.getPlannedAssetId(), userId);
        if (plan.getAcquisitionType() != AcquisitionType.PLANNED || !plan.getType().isPhysical()) {
            throw new InvalidTransactionException(
                    "Only a physical asset purchase plan can be completed");
        }
        request.getAsset().setAcquisitionType(AcquisitionType.PURCHASE);
        return assets.completePurchasePlan(plan.getId(), userId, request.getAsset()).getId();
    }
}
