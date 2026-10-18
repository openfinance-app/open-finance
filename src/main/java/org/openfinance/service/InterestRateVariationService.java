package org.openfinance.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.openfinance.dto.InterestRateVariationRequest;
import org.openfinance.dto.InterestRateVariationResponse;
import org.openfinance.entity.InterestRateVariation;
import org.openfinance.repository.InterestRateVariationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class InterestRateVariationService {

    private final InterestRateVariationRepository variationRepository;
    private final AccountService accountService;

    @Transactional(readOnly = true)
    public List<InterestRateVariationResponse> getVariations(Long accountId, Long userId) {
        // Verify account ownership
        accountService.getAccountById(accountId, userId);

        return variationRepository.findByAccountIdOrderByValidFromDesc(accountId).stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    @Transactional
    public InterestRateVariationResponse addVariation(
            Long accountId, Long userId, InterestRateVariationRequest request) {
        // Verify account ownership
        accountService.getAccountById(accountId, userId);

        validateEffectiveDate(accountId, request.getValidFrom(), null);
        InterestRateVariation variation =
                InterestRateVariation.builder()
                        .accountId(accountId)
                        .rate(request.getRate())
                        .taxRate(
                                request.getTaxRate() != null
                                        ? request.getTaxRate()
                                        : BigDecimal.ZERO)
                        .validFrom(request.getValidFrom())
                        .creditValueDateMode(request.getCreditValueDateMode())
                        .debitValueDateMode(request.getDebitValueDateMode())
                        .dayCountBasis(request.getDayCountBasis())
                        .build();

        variation = variationRepository.save(variation);
        return toResponse(variation);
    }

    @Transactional
    public InterestRateVariationResponse updateVariation(
            Long accountId, Long variationId, Long userId, InterestRateVariationRequest request) {
        accountService.getAccountById(accountId, userId);
        InterestRateVariation variation =
                variationRepository
                        .findById(variationId)
                        .filter(v -> v.getAccountId().equals(accountId))
                        .orElseThrow(() -> new IllegalArgumentException("Variation not found"));
        validateEffectiveDate(accountId, request.getValidFrom(), variationId);
        variation.setRate(request.getRate());
        variation.setTaxRate(request.getTaxRate() == null ? BigDecimal.ZERO : request.getTaxRate());
        variation.setValidFrom(request.getValidFrom());
        variation.setCreditValueDateMode(request.getCreditValueDateMode());
        variation.setDebitValueDateMode(request.getDebitValueDateMode());
        variation.setDayCountBasis(request.getDayCountBasis());
        return toResponse(variationRepository.save(variation));
    }

    private void validateEffectiveDate(Long accountId, LocalDate date, Long excludedId) {
        if (variationRepository.findByAccountIdOrderByValidFromDesc(accountId).stream()
                .anyMatch(
                        v ->
                                v.getValidFrom().equals(date)
                                        && !Objects.equals(v.getId(), excludedId))) {
            throw new IllegalArgumentException(
                    "An interest rate already exists for this effective date");
        }
    }

    @Transactional
    public void deleteVariation(Long accountId, Long variationId, Long userId) {
        // Verify account ownership
        accountService.getAccountById(accountId, userId);

        InterestRateVariation variation =
                variationRepository
                        .findById(variationId)
                        .orElseThrow(() -> new IllegalArgumentException("Variation not found"));

        if (!variation.getAccountId().equals(accountId)) {
            throw new IllegalArgumentException("Variation does not belong to this account");
        }

        variationRepository.delete(variation);
    }

    private InterestRateVariationResponse toResponse(InterestRateVariation entity) {
        return InterestRateVariationResponse.builder()
                .id(entity.getId())
                .accountId(entity.getAccountId())
                .rate(entity.getRate())
                .taxRate(entity.getTaxRate())
                .validFrom(entity.getValidFrom())
                .creditValueDateMode(entity.getCreditValueDateMode())
                .debitValueDateMode(entity.getDebitValueDateMode())
                .dayCountBasis(entity.getDayCountBasis())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }
}
