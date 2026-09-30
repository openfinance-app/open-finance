package org.openfinance.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import lombok.RequiredArgsConstructor;
import org.openfinance.dto.DisbursementRequest;
import org.openfinance.dto.LiabilityRequest;
import org.openfinance.dto.LiabilityResponse;
import org.openfinance.dto.PropertyPurchaseRequest;
import org.openfinance.dto.PropertyPurchaseResponse;
import org.openfinance.dto.TransactionRequest;
import org.openfinance.entity.Account;
import org.openfinance.entity.AcquisitionType;
import org.openfinance.entity.EntityType;
import org.openfinance.entity.LiabilityType;
import org.openfinance.entity.OperationType;
import org.openfinance.entity.PropertyPurchaseReceipt;
import org.openfinance.entity.TransactionType;
import org.openfinance.exception.InvalidTransactionException;
import org.openfinance.repository.AccountRepository;
import org.openfinance.repository.PropertyPurchaseReceiptRepository;
import org.openfinance.service.history.ReversibleOperation;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
@RequiredArgsConstructor
public class PropertyPurchaseServiceImpl implements PropertyPurchaseService {
    private final PropertyPurchaseReceiptRepository receipts;
    private final RealEstateService properties;
    private final LiabilityService liabilities;
    private final TransactionService transactions;
    private final AccountRepository accounts;
    private final ObjectMapper objectMapper;

    @Override
    @ReversibleOperation(
            entity = EntityType.REAL_ESTATE,
            operation = OperationType.CREATE,
            userArgument = 0)
    public PropertyPurchaseResponse purchase(Long userId, PropertyPurchaseRequest request) {
        String hash = fingerprint(request);
        PropertyPurchaseReceipt receipt =
                receipts.findByUserIdAndOperationId(userId, request.getOperationId().toString())
                        .orElse(null);
        if (receipt != null) {
            if (!receipt.getRequestHash().equals(hash)) {
                throw new InvalidTransactionException(
                        "This purchase retry identifier was already used for different details");
            }
            return new PropertyPurchaseResponse(receipt.getPropertyId(), receipt.getMortgageId());
        }
        validateFunding(userId, request);
        Long mortgageId = mortgage(userId, request);
        request.getProperty().setMortgageId(mortgageId);
        Long propertyId = properties.createProperty(userId, request.getProperty()).getId();
        if (mortgageId != null) drawMortgage(userId, request, mortgageId, propertyId);
        paySeller(userId, request, propertyId);
        receipt = new PropertyPurchaseReceipt();
        receipt.setUserId(userId);
        receipt.setOperationId(request.getOperationId().toString());
        receipt.setRequestHash(hash);
        receipt.setPropertyId(propertyId);
        receipt.setMortgageId(mortgageId);
        receipts.save(receipt);
        return new PropertyPurchaseResponse(propertyId, mortgageId);
    }

    private void validateFunding(Long userId, PropertyPurchaseRequest request) {
        boolean financed = request.getLoanAmount().signum() > 0;
        int sources =
                (request.getNewMortgage() != null ? 1 : 0)
                        + (request.getExistingMortgageId() != null ? 1 : 0);
        if ((financed ? sources != 1 : sources != 0)
                || request.getLoanAmount()
                                .add(request.getDownPayment())
                                .compareTo(request.getProperty().getPurchasePrice())
                        != 0
                || !request.getProperty().isActive()
                || (request.getProperty().getAcquisitionType() != null
                        && request.getProperty().getAcquisitionType()
                                != AcquisitionType.PURCHASE)) {
            throw new InvalidTransactionException(
                    "The purchase price must be funded exactly once by the loan and down payment");
        }
        if (cashPayment(request).signum() > 0) {
            if (request.getAccountId() == null)
                throw new InvalidTransactionException("A payment account is required");
            Account account =
                    accounts.findByIdAndUserId(request.getAccountId(), userId)
                            .orElseThrow(
                                    () ->
                                            new InvalidTransactionException(
                                                    "Payment account not found"));
            if (!Boolean.TRUE.equals(account.getIsActive())
                    || !account.getCurrency().equals(request.getProperty().getCurrency())) {
                throw new InvalidTransactionException(
                        "The payment account must be active and use the property currency");
            }
        }
    }

    private Long mortgage(Long userId, PropertyPurchaseRequest request) {
        if (request.getNewMortgage() != null) {
            LiabilityRequest loan = request.getNewMortgage();
            if (loan.getType() != LiabilityType.MORTGAGE
                    || loan.getCurrentBalance().signum() != 0
                    || loan.getPrincipal().compareTo(request.getLoanAmount()) != 0
                    || !loan.getCurrency().equals(request.getProperty().getCurrency())
                    || !loan.getStartDate().equals(request.getProperty().getPurchaseDate())
                    || Boolean.TRUE.equals(loan.getPreviouslyFunded())
                    || loan.getRealEstateId() != null
                    || loan.getRepresentedByAccountId() != null) {
                throw new InvalidTransactionException(
                        "The new mortgage must match the purchase and start undrawn");
            }
            return liabilities.createLiability(userId, loan).getId();
        }
        if (request.getExistingMortgageId() != null) {
            LiabilityResponse loan =
                    liabilities.getLiabilityById(request.getExistingMortgageId(), userId);
            if (loan.getType() != LiabilityType.MORTGAGE
                    || !loan.getCurrency().equals(request.getProperty().getCurrency())) {
                throw new InvalidTransactionException(
                        "The mortgage must use the property currency");
            }
        }
        return request.getExistingMortgageId();
    }

    private void drawMortgage(
            Long userId, PropertyPurchaseRequest request, Long mortgageId, Long propertyId) {
        liabilities.disburse(
                userId,
                mortgageId,
                DisbursementRequest.builder()
                        .amount(request.getLoanAmount())
                        .date(request.getProperty().getPurchaseDate())
                        .directRealEstateId(
                                request.getRoute() == PropertyPurchaseRequest.Route.DIRECT
                                        ? propertyId
                                        : null)
                        .toAccountId(
                                request.getRoute() == PropertyPurchaseRequest.Route.ACCOUNT
                                        ? request.getAccountId()
                                        : null)
                        .build());
    }

    private BigDecimal cashPayment(PropertyPurchaseRequest request) {
        return request.getRoute() == PropertyPurchaseRequest.Route.DIRECT
                ? request.getDownPayment()
                : request.getProperty().getPurchasePrice();
    }

    private void paySeller(Long userId, PropertyPurchaseRequest request, Long propertyId) {
        BigDecimal amount = cashPayment(request);
        if (amount.signum() == 0) return;
        transactions.createTransaction(
                userId,
                TransactionRequest.builder()
                        .accountId(request.getAccountId())
                        .type(TransactionType.EXPENSE)
                        .amount(amount)
                        .currency(request.getProperty().getCurrency())
                        .date(request.getProperty().getPurchaseDate())
                        .description(request.getPaymentDescription())
                        .realEstateId(propertyId)
                        .build());
    }

    private String fingerprint(PropertyPurchaseRequest request) {
        try {
            return HexFormat.of()
                    .formatHex(
                            MessageDigest.getInstance("SHA-256")
                                    .digest(objectMapper.writeValueAsBytes(request)));
        } catch (JsonProcessingException | NoSuchAlgorithmException exception) {
            throw new IllegalStateException("Cannot identify purchase request", exception);
        }
    }
}
