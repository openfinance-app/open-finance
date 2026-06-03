package org.openfinance.service;

import java.util.List;
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
        public List<InterestRateVariationResponse> getVariations(
                        Long accountId, Long userId) {
                // Verify account ownership
                accountService.getAccountById(accountId, userId);

                return variationRepository.findByAccountIdOrderByValidFromDesc(accountId).stream()
                                .map(this::toResponse)
                                .collect(Collectors.toList());
        }

        @Transactional
        public InterestRateVariationResponse addVariation(
                        Long accountId,
                        Long userId,
                        InterestRateVariationRequest request) {
                // Verify account ownership
                accountService.getAccountById(accountId, userId);

                InterestRateVariation variation = InterestRateVariation.builder()
                                .accountId(accountId)
                                .rate(request.getRate())
                                .taxRate(
                                                request.getTaxRate() != null
                                                                ? request.getTaxRate()
                                                                : java.math.BigDecimal.ZERO)
                                .validFrom(request.getValidFrom())
                                .build();

                variation = variationRepository.save(variation);
                return toResponse(variation);
        }

        @Transactional
        public void deleteVariation(
                        Long accountId, Long variationId, Long userId) {
                // Verify account ownership
                accountService.getAccountById(accountId, userId);

                InterestRateVariation variation = variationRepository
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
                                .createdAt(entity.getCreatedAt())
                                .updatedAt(entity.getUpdatedAt())
                                .build();
        }
}
