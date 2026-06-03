package org.openfinance.controller;

import jakarta.validation.Valid;
import java.math.BigDecimal;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.openfinance.dto.InterestRateVariationRequest;
import org.openfinance.dto.InterestRateVariationResponse;
import org.openfinance.entity.User;
import org.openfinance.service.InterestCalculatorService;
import org.openfinance.service.InterestRateVariationService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/accounts/{accountId}")
@RequiredArgsConstructor
@Slf4j
public class InterestRateVariationController {
        private final InterestRateVariationService variationService;
        private final InterestCalculatorService calculatorService;

        @GetMapping("/interest-variations")
        public ResponseEntity<List<InterestRateVariationResponse>> getVariations(
                        @PathVariable("accountId") Long accountId,
                        Authentication authentication) {
                User user = (User) authentication.getPrincipal();
                List<InterestRateVariationResponse> responses = variationService.getVariations(accountId, user.getId());
                return ResponseEntity.ok(responses);
        }

        @PostMapping("/interest-variations")
        public ResponseEntity<InterestRateVariationResponse> addVariation(
                        @PathVariable("accountId") Long accountId,
                        @Valid @RequestBody InterestRateVariationRequest request,
                        Authentication authentication) {
                User user = (User) authentication.getPrincipal();
                InterestRateVariationResponse response = variationService.addVariation(accountId, user.getId(),
                                request);
                return ResponseEntity.status(HttpStatus.CREATED).body(response);
        }

        @DeleteMapping("/interest-variations/{variationId}")
        public ResponseEntity<Void> deleteVariation(
                        @PathVariable("accountId") Long accountId,
                        @PathVariable("variationId") Long variationId,
                        Authentication authentication) {
                User user = (User) authentication.getPrincipal();
                variationService.deleteVariation(accountId, variationId, user.getId());
                return ResponseEntity.noContent().build();
        }

        @GetMapping("/interest-estimate")
        public ResponseEntity<InterestEstimateResponse> getInterestEstimate(
                        @PathVariable("accountId") Long accountId,
                        @RequestParam(value = "period", required = false, defaultValue = "1Y") String period,
                        Authentication authentication) {
                User user = (User) authentication.getPrincipal();
                BigDecimal projected = calculatorService.calculateInterestEstimate(
                                accountId, user.getId(), period);
                BigDecimal historical = calculatorService.calculateHistoricalAccumulated(
                                accountId, user.getId(), period);
                return ResponseEntity.ok(new InterestEstimateResponse(projected, historical));
        }

        /** DTO returned by the interest-estimate endpoint. */
        public static record InterestEstimateResponse(
                        BigDecimal estimate, BigDecimal historicalAccumulated) {
        }
}
