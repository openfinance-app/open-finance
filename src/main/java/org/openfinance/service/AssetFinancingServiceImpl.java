package org.openfinance.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.openfinance.dto.AssetFinancingLink;
import org.openfinance.entity.Asset;
import org.openfinance.entity.EntityType;
import org.openfinance.entity.Liability;
import org.openfinance.entity.LiabilityAssetLink;
import org.openfinance.entity.OperationType;
import org.openfinance.exception.InvalidTransactionException;
import org.openfinance.exception.ResourceNotFoundException;
import org.openfinance.repository.AssetRepository;
import org.openfinance.repository.LiabilityAssetLinkRepository;
import org.openfinance.repository.LiabilityRepository;
import org.openfinance.repository.RealEstateRepository;
import org.openfinance.service.history.ReversibleOperation;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
@RequiredArgsConstructor
public class AssetFinancingServiceImpl implements AssetFinancingService {
    private final LiabilityAssetLinkRepository links;
    private final LiabilityRepository liabilities;
    private final org.openfinance.repository.AccountRepository accounts;
    private final AssetRepository assets;
    private final RealEstateRepository properties;
    private final ExchangeRateService rates;

    @Override
    @Transactional(readOnly = true)
    public List<AssetFinancingLink> forLiability(Long userId, Long liabilityId) {
        liability(userId, liabilityId);
        return links.findByLiabilityIdAndUserId(liabilityId, userId).stream()
                .map(this::response)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<AssetFinancingLink> forAsset(Long userId, Long assetId) {
        asset(userId, assetId);
        return links.findByAssetIdAndUserId(assetId, userId).stream().map(this::response).toList();
    }

    @Override
    @CacheEvict(
            value = {"dashboardSummary", "netWorthSummary", "networthAllocation"},
            key = "#userId")
    @ReversibleOperation(
            entity = EntityType.LIABILITY,
            operation = OperationType.UPDATE,
            userArgument = 0,
            idArgument = 1)
    public List<AssetFinancingLink> replace(
            Long userId, Long liabilityId, List<AssetFinancingLink> requested) {
        liability(userId, liabilityId);
        BigDecimal total = BigDecimal.ZERO;
        Set<String> unique = new HashSet<>();
        for (AssetFinancingLink request : requested) {
            asset(userId, request.getAssetId());
            if (!unique.add(request.getAssetId() + ":" + request.getRelationship())) {
                throw new InvalidTransactionException("Duplicate asset relationship");
            }
            BigDecimal percentage = request.getAllocationPercentage();
            if (percentage == null
                    || percentage.signum() < 0
                    || percentage.compareTo(new BigDecimal("100")) > 0
                    || !Set.of("FINANCING", "COLLATERAL").contains(request.getRelationship())) {
                throw new InvalidTransactionException("Invalid financing allocation");
            }
            if (request.getRelationship().equals("FINANCING")) {
                if (percentage.signum() == 0)
                    throw new InvalidTransactionException(
                            "Financing allocation must be greater than zero");
                total = total.add(percentage);
            } else if (percentage.signum() != 0) {
                throw new InvalidTransactionException(
                        "Collateral alone does not allocate principal");
            }
        }
        if (total.compareTo(new BigDecimal("100")) > 0) {
            throw new InvalidTransactionException(
                    "Allocated financing cannot exceed 100% of a liability");
        }
        links.deleteByLiabilityIdAndUserId(liabilityId, userId);
        links.flush();
        for (AssetFinancingLink request : requested) {
            links.save(
                    LiabilityAssetLink.builder()
                            .userId(userId)
                            .liabilityId(liabilityId)
                            .assetId(request.getAssetId())
                            .relationship(request.getRelationship())
                            .allocationPercentage(request.getAllocationPercentage())
                            .build());
        }
        return forLiability(userId, liabilityId);
    }

    @Override
    @Transactional(readOnly = true)
    public BigDecimal propertyDebt(Long userId, Long assetId, Long mortgageId, String currency) {
        BigDecimal debt =
                assetId == null
                        ? BigDecimal.ZERO
                        : forAsset(userId, assetId).stream()
                                .map(AssetFinancingLink::getAllocatedBalance)
                                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (mortgageId != null
                && links.findByLiabilityIdAndUserId(mortgageId, userId).stream()
                        .noneMatch(link -> link.getRelationship().equals("FINANCING"))) {
            Liability loan = liability(userId, mortgageId);
            debt = debt.add(convert(balance(loan), loan.getCurrency(), currency));
        }
        return debt;
    }

    @Override
    @Transactional(readOnly = true)
    public boolean hasFinancingAllocations(Long userId, Long liabilityId) {
        return links.findByLiabilityIdAndUserId(liabilityId, userId).stream()
                .anyMatch(link -> link.getRelationship().equals("FINANCING"));
    }

    @Override
    public void ensureDirectFinancing(Long userId, Long liabilityId, Long assetId) {
        List<LiabilityAssetLink> existing = links.findByLiabilityIdAndUserId(liabilityId, userId);
        if (existing.stream()
                .anyMatch(
                        l ->
                                l.getAssetId().equals(assetId)
                                        && l.getRelationship().equals("FINANCING")
                                        && l.getAllocationPercentage().signum() > 0)) return;
        boolean allocated =
                existing.stream().anyMatch(l -> l.getRelationship().equals("FINANCING"));
        boolean anotherPrimary =
                properties.findByMortgageId(liabilityId).stream()
                        .anyMatch(
                                p ->
                                        p.isActive()
                                                && !java.util.Objects.equals(
                                                        p.getAssetId(), assetId));
        if (allocated || anotherPrimary) {
            throw new InvalidTransactionException(
                    "Allocate a financing share to this property before drawing a shared loan");
        }
        links.save(
                LiabilityAssetLink.builder()
                        .userId(userId)
                        .liabilityId(liabilityId)
                        .assetId(assetId)
                        .relationship("FINANCING")
                        .allocationPercentage(new BigDecimal("100"))
                        .build());
    }

    private AssetFinancingLink response(LiabilityAssetLink link) {
        Asset asset = asset(link.getUserId(), link.getAssetId());
        Liability loan = liability(link.getUserId(), link.getLiabilityId());
        BigDecimal allocated =
                link.getRelationship().equals("FINANCING")
                        ? balance(loan)
                                .multiply(link.getAllocationPercentage())
                                .divide(new BigDecimal("100"), 8, RoundingMode.HALF_UP)
                        : BigDecimal.ZERO;
        return AssetFinancingLink.builder()
                .assetId(asset.getId())
                .assetName(asset.getName())
                .liabilityId(loan.getId())
                .liabilityName(loan.getName())
                .relationship(link.getRelationship())
                .allocationPercentage(link.getAllocationPercentage())
                .assetCurrency(asset.getCurrency())
                .liabilityCurrency(loan.getCurrency())
                .allocatedBalance(convert(allocated, loan.getCurrency(), asset.getCurrency()))
                .build();
    }

    private BigDecimal convert(BigDecimal amount, String from, String to) {
        return from.equalsIgnoreCase(to) ? amount : rates.convert(amount, from, to);
    }

    @Override
    @Transactional(readOnly = true)
    public BigDecimal outstandingBalance(Long userId, Long liabilityId) {
        return balance(liability(userId, liabilityId));
    }

    private BigDecimal balance(Liability loan) {
        return loan.getRepresentedByAccountId() == null
                ? new BigDecimal(loan.getCurrentBalance())
                : accounts.findByIdAndUserId(loan.getRepresentedByAccountId(), loan.getUserId())
                        .orElseThrow()
                        .getBalance()
                        .negate()
                        .max(BigDecimal.ZERO);
    }

    private Asset asset(Long userId, Long id) {
        return assets.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Asset not found"));
    }

    private Liability liability(Long userId, Long id) {
        return liabilities
                .findByIdAndUserId(id, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Liability not found"));
    }
}
