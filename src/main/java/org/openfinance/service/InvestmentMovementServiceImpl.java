package org.openfinance.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.openfinance.dto.HistoricalPrice;
import org.openfinance.dto.InvestmentMovementRequest;
import org.openfinance.dto.InvestmentMovementRequest.Operation;
import org.openfinance.dto.InvestmentMovementRequest.Pricing;
import org.openfinance.dto.InvestmentQuote;
import org.openfinance.entity.AcquisitionType;
import org.openfinance.entity.Asset;
import org.openfinance.entity.AssetType;
import org.openfinance.entity.Transaction;
import org.openfinance.entity.TransactionType;
import org.openfinance.exception.InvalidTransactionException;
import org.openfinance.repository.AssetRepository;
import org.openfinance.repository.TransactionRepository;
import org.openfinance.util.InvestmentHoldings;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
@RequiredArgsConstructor
public class InvestmentMovementServiceImpl implements InvestmentMovementService {
    private final AssetRepository assets;
    private final TransactionRepository transactions;
    private final MarketDataService marketData;
    private final ExchangeRateService exchangeRates;

    @Override
    @Transactional(readOnly = true)
    public InvestmentQuote quote(
            Long assetId,
            Long userId,
            BigDecimal amount,
            String currency,
            LocalDate date,
            Long transactionId) {
        Asset asset = holding(assetId, userId);
        Transaction previous =
                transactionId == null
                        ? null
                        : transactions
                                .findByIdAndUserId(transactionId, userId)
                                .orElseThrow(
                                        () ->
                                                invalid(
                                                        "holding",
                                                        "Transaction not found or access denied"));
        Transaction instruction =
                Transaction.builder().amount(amount).currency(currency).date(date).build();
        BigDecimal value = movementValue(asset, instruction, previous);
        BigDecimal price = referencePrice(asset, instruction, previous);
        return InvestmentQuote.builder()
                .assetId(assetId)
                .quantity(units(value, price))
                .availableQuantity(removableQuantity(asset, date, transactionId))
                .price(price)
                .value(value)
                .currency(asset.getCurrency())
                .priceDate(date)
                .source("HISTORICAL_CLOSE")
                .build();
    }

    private BigDecimal removableQuantity(Asset asset, LocalDate date, Long excludedTransactionId) {
        if (date.isBefore(asset.getPurchaseDate())) return BigDecimal.ZERO;
        List<Transaction> movements =
                transactions.findByAssetIdAndUserId(asset.getId(), asset.getUserId()).stream()
                        .filter(tx -> tx.getInvestmentQuantity() != null)
                        .sorted(
                                Comparator.comparing(Transaction::getDate)
                                        .thenComparing(Transaction::getId))
                        .toList();
        BigDecimal quantity = asset.getImportOpeningQuantity();
        if (quantity == null)
            quantity =
                    asset.getImportSource() == null && movements.isEmpty()
                            ? asset.getQuantity()
                            : BigDecimal.ZERO;
        BigDecimal available = null;
        for (Transaction movement : movements) {
            if (Objects.equals(excludedTransactionId, movement.getId())) continue;
            if (available == null && movement.getDate().isAfter(date)) available = quantity;
            quantity = quantity.add(movement.getInvestmentQuantity());
            if (available != null) available = available.min(quantity);
        }
        return (available == null ? quantity : available).max(BigDecimal.ZERO);
    }

    @Override
    public void apply(
            Transaction tx,
            InvestmentMovementRequest request,
            boolean present,
            Transaction previous) {
        if (previous != null
                && tx.getInvestmentImportIdentity() == null
                && (previous.getInvestmentQuantity() != null || request != null)) {
            tx.setInvestmentImportIdentity(ImportDuplicateMatcher.investmentIdentity(previous));
            tx.setInvestmentImportAccountId(previous.getAccountId());
        }
        if (!present) {
            if (previous == null || previous.getInvestmentQuantity() == null) return;
            request = retainedInstruction(previous);
        } else if (request == null) {
            if (previous != null && previous.getInvestmentQuantity() != null) clear(tx);
            return;
        }
        Asset asset = holding(request.getAssetId(), tx.getUserId());
        validateContext(tx, asset, request);
        if (unchanged(tx, request, previous)) {
            tx.setAssetId(previous.getAssetId());
            return;
        }
        initializeOpening(asset, tx, previous);
        BigDecimal value = movementValue(asset, tx, previous);
        boolean historical = request.getPricing() == Pricing.HISTORICAL;
        BigDecimal price = historical ? referencePrice(asset, tx, previous) : null;
        BigDecimal quantity = historical ? units(value, price) : request.getQuantity();
        if (quantity == null
                || quantity.signum() <= 0
                || quantity.scale() > 18
                || quantity.precision() - quantity.scale() > 26)
            throw invalid("quantity", "Enter a positive quantity with at most 18 decimal places");
        boolean removes =
                request.getOperation() == Operation.REMOVE
                        || request.getOperation() == Operation.SELL;
        tx.setAssetId(asset.getId());
        tx.setInvestmentQuantity(removes ? quantity.negate() : quantity);
        tx.setInvestmentCash(
                request.getOperation() == Operation.BUY
                        || request.getOperation() == Operation.SELL);
        tx.setInvestmentValue(value);
        tx.setInvestmentPrice(
                historical ? price : value.divide(quantity, 18, RoundingMode.HALF_UP));
        tx.setInvestmentPriceDate(tx.getDate());
        tx.setInvestmentPriceSource(historical ? "HISTORICAL_CLOSE" : "RECORDED");
        tx.setInvestmentEdited(true);
    }

    private boolean unchanged(
            Transaction tx, InvestmentMovementRequest request, Transaction previous) {
        if (previous == null
                || previous.getInvestmentQuantity() == null
                || !Objects.equals(previous.getAssetId(), request.getAssetId())
                || !Objects.equals(previous.getDate(), tx.getDate())
                || !Objects.equals(previous.getCurrency(), tx.getCurrency())
                || !sameOriginal(tx, previous)
                || previous.getAmount().compareTo(tx.getAmount()) != 0
                || retainedInstruction(previous).getOperation() != request.getOperation())
            return false;
        return request.getPricing() == Pricing.HISTORICAL
                ? "HISTORICAL_CLOSE".equals(previous.getInvestmentPriceSource())
                : !"HISTORICAL_CLOSE".equals(previous.getInvestmentPriceSource())
                        && request.getQuantity() != null
                        && previous.getInvestmentQuantity().abs().compareTo(request.getQuantity())
                                == 0;
    }

    private boolean sameOriginal(Transaction tx, Transaction previous) {
        return Objects.equals(tx.getOriginalCurrency(), previous.getOriginalCurrency())
                && (tx.getOriginalAmount() == null || previous.getOriginalAmount() == null
                        ? tx.getOriginalAmount() == previous.getOriginalAmount()
                        : tx.getOriginalAmount().compareTo(previous.getOriginalAmount()) == 0);
    }

    private BigDecimal movementValue(Asset asset, Transaction tx, Transaction previous) {
        if (previous != null
                && previous.getInvestmentValue() != null
                && Objects.equals(previous.getAssetId(), asset.getId())
                && Objects.equals(previous.getDate(), tx.getDate())
                && Objects.equals(previous.getCurrency(), tx.getCurrency())
                && sameOriginal(tx, previous)
                && previous.getAmount().compareTo(tx.getAmount()) == 0)
            return previous.getInvestmentValue();
        if (tx.getOriginalCurrency() != null && tx.getOriginalAmount() != null)
            return valueInAssetCurrency(
                    asset, tx.getOriginalAmount(), tx.getOriginalCurrency(), tx.getDate());
        return valueInAssetCurrency(asset, tx.getAmount(), tx.getCurrency(), tx.getDate());
    }

    private InvestmentMovementRequest retainedInstruction(Transaction previous) {
        boolean adds = previous.getInvestmentQuantity().signum() > 0;
        Operation operation =
                previous.isInvestmentCash()
                        ? (adds ? Operation.BUY : Operation.SELL)
                        : (adds ? Operation.ADD : Operation.REMOVE);
        return InvestmentMovementRequest.builder()
                .assetId(previous.getAssetId())
                .operation(operation)
                .quantity(previous.getInvestmentQuantity().abs())
                .pricing(
                        "HISTORICAL_CLOSE".equals(previous.getInvestmentPriceSource())
                                ? Pricing.HISTORICAL
                                : Pricing.QUANTITY)
                .build();
    }

    private void validateContext(Transaction tx, Asset asset, InvestmentMovementRequest request) {
        if (!Objects.equals(asset.getAccountId(), tx.getAccountId()))
            throw invalid("account", "Choose a holding linked to the selected account");
        if (request.getOperation() == null || request.getPricing() == null)
            throw invalid("operation", "Choose an investment operation and quantity method");
        boolean income =
                request.getOperation() == Operation.ADD || request.getOperation() == Operation.SELL;
        if (income != (tx.getType() == TransactionType.INCOME)
                || (tx.getTransferId() != null && request.getOperation() != Operation.REMOVE))
            throw invalid(
                    "operation", "The investment operation does not match this transaction type");
        if (tx.getLiabilityId() != null
                || tx.getRealEstateId() != null
                || tx.getMovementType() != null)
            throw invalid(
                    "conflict",
                    "An investment movement cannot also be a liability or property movement");
    }

    private Asset holding(Long id, Long userId) {
        if (id == null) throw invalid("holding", "Select an investment holding you own");
        Asset asset =
                assets.findByIdAndUserId(id, userId)
                        .orElseThrow(
                                () -> invalid("holding", "Select an investment holding you own"));
        if (asset.getType().isPhysical()
                || asset.getType() == AssetType.REAL_ESTATE
                || asset.getAcquisitionType() == AcquisitionType.PLANNED)
            throw invalid("holding", "Select an active financial holding");
        return asset;
    }

    private void initializeOpening(Asset asset, Transaction tx, Transaction previous) {
        boolean tracked =
                asset.getImportSource() != null
                        || asset.getImportOpeningQuantity() != null
                        || (previous != null
                                && previous.getInvestmentQuantity() != null
                                && Objects.equals(previous.getAssetId(), asset.getId()))
                        || transactions
                                .findByAssetIdAndUserId(asset.getId(), tx.getUserId())
                                .stream()
                                .anyMatch(
                                        t ->
                                                t.getInvestmentQuantity() != null
                                                        && !Objects.equals(t.getId(), tx.getId()));
        if (!tracked) {
            asset.setImportOpeningQuantity(asset.getQuantity());
            asset.setImportOpeningCost(asset.getTotalCost());
            assets.save(asset);
        }
        if (asset.getImportOpeningQuantity() != null
                && asset.getImportOpeningQuantity().signum() > 0
                && tx.getDate().isBefore(asset.getPurchaseDate()))
            throw invalid(
                    "openingDate", "The movement cannot precede the holding's opening position");
    }

    private BigDecimal valueInAssetCurrency(
            Asset asset, BigDecimal amount, String currency, LocalDate date) {
        if (amount == null || amount.signum() <= 0 || currency == null || date == null)
            throw invalid("valuation", "Enter a positive amount, currency and date");
        BigDecimal value =
                asset.getCurrency().equalsIgnoreCase(currency)
                        ? amount
                        : exchangeRates.convert(amount, currency, asset.getCurrency(), date);
        if (value == null || value.signum() <= 0)
            throw invalid(
                    "valuation", "The holding's historical currency conversion is unavailable");
        return value;
    }

    private BigDecimal referencePrice(Asset asset, Transaction tx, Transaction previous) {
        if (previous != null
                && Objects.equals(previous.getAssetId(), asset.getId())
                && tx.getDate().equals(previous.getInvestmentPriceDate())
                && "HISTORICAL_CLOSE".equals(previous.getInvestmentPriceSource())
                && previous.getInvestmentPrice() != null
                && previous.getInvestmentPrice().signum() > 0) return previous.getInvestmentPrice();
        return historicalPrice(asset, tx.getDate());
    }

    private BigDecimal historicalPrice(Asset asset, LocalDate date) {
        if (asset.getSymbol() == null
                || asset.getSymbol().isBlank()
                || date == null
                || date.isAfter(LocalDate.now()))
            throw invalid(
                    "priceUnavailable",
                    "No historical price is available for this holding and date; enter the quantity instead");
        List<HistoricalPrice> prices =
                marketData.getHistoricalPrices(asset.getSymbol(), date, date, asset.getCurrency());
        return prices.stream()
                .filter(
                        p ->
                                date.equals(p.getDate())
                                        && asset.getCurrency().equalsIgnoreCase(p.getCurrency()))
                .map(HistoricalPrice::getClose)
                .filter(p -> p != null && p.signum() > 0)
                .findFirst()
                .orElseThrow(
                        () ->
                                invalid(
                                        "priceUnavailable",
                                        "No historical price is available for this holding and date; enter the quantity instead"));
    }

    private BigDecimal units(BigDecimal value, BigDecimal price) {
        BigDecimal quantity = value.divide(price, 18, RoundingMode.HALF_UP).stripTrailingZeros();
        if (quantity.signum() <= 0)
            throw invalid("quantity", "The calculated quantity is too small");
        return quantity;
    }

    private void clear(Transaction tx) {
        tx.setAssetId(null);
        tx.setInvestmentQuantity(null);
        tx.setInvestmentCash(false);
        tx.setInvestmentValue(null);
        tx.setInvestmentPrice(null);
        tx.setInvestmentPriceDate(null);
        tx.setInvestmentPriceSource(null);
        tx.setInvestmentEdited(true);
    }

    @Override
    public void recalculate(Transaction previous, Transaction current) {
        Set<Long> ids = new LinkedHashSet<>();
        if (previous != null && previous.getInvestmentQuantity() != null)
            ids.add(previous.getAssetId());
        if (current.getInvestmentQuantity() != null) ids.add(current.getAssetId());
        transactions.flush();
        for (Long id : ids) {
            Asset asset = holding(id, current.getUserId());
            List<Transaction> movements =
                    transactions.findByAssetIdAndUserId(id, current.getUserId());
            if (asset.getImportOpeningQuantity() == null
                    || asset.getImportOpeningQuantity().signum() == 0) {
                movements.stream()
                        .filter(t -> t.getInvestmentQuantity() != null)
                        .map(Transaction::getDate)
                        .min(LocalDate::compareTo)
                        .ifPresent(asset::setPurchaseDate);
            }
            InvestmentHoldings.update(asset, movements);
            assets.save(asset);
        }
    }

    private InvalidTransactionException invalid(String key, String message) {
        return InvalidTransactionException.investment(key, message);
    }
}
