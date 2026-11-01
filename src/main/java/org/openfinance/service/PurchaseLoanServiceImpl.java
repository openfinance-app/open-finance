package org.openfinance.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.openfinance.dto.DisbursementRequest;
import org.openfinance.dto.PropertyPurchaseRequest.Route;
import org.openfinance.entity.MovementType;
import org.openfinance.entity.Transaction;
import org.openfinance.entity.TransactionType;
import org.openfinance.exception.InvalidTransactionException;
import org.openfinance.repository.TransactionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
@RequiredArgsConstructor
public class PurchaseLoanServiceImpl implements PurchaseLoanService {
    private final LiabilityService liabilities;
    private final TransactionRepository transactions;
    private final AssetFinancingService financing;

    @Override
    public void fund(
            Long userId,
            Long loanId,
            BigDecimal amount,
            String currency,
            LocalDate date,
            Route route,
            Long accountId,
            Long receiptId,
            Long assetId,
            Long propertyId) {
        if (receiptId != null) {
            Transaction receipt =
                    transactions
                            .findByIdAndUserId(receiptId, userId)
                            .orElseThrow(
                                    () ->
                                            new InvalidTransactionException(
                                                    "Loan receipt not found"));
            if (route != Route.ACCOUNT
                    || Boolean.TRUE.equals(receipt.getIsDeleted())
                    || receipt.getType() != TransactionType.INCOME
                    || receipt.getMovementType() != MovementType.DISBURSEMENT
                    || !Objects.equals(receipt.getLiabilityId(), loanId)
                    || !Objects.equals(receipt.getAccountId(), accountId)
                    || !currency.equals(receipt.getCurrency())
                    || receipt.getAmount().compareTo(amount) != 0
                    || receipt.getDate().isAfter(date)) {
                throw new InvalidTransactionException(
                        "Choose this loan's matching receipt, received in the payment account by the purchase date");
            }
        } else {
            liabilities.disburse(
                    userId,
                    loanId,
                    DisbursementRequest.builder()
                            .amount(amount)
                            .date(date)
                            .toAccountId(route == Route.ACCOUNT ? accountId : null)
                            .directRealEstateId(route == Route.DIRECT ? propertyId : null)
                            .directAssetId(
                                    route == Route.DIRECT && propertyId == null ? assetId : null)
                            .build());
        }
        financing.ensureDirectFinancing(userId, loanId, assetId);
    }
}
