package org.openfinance.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.openfinance.dto.calculator.CompoundInterestRequest;
import org.openfinance.dto.calculator.CompoundInterestResult;
import org.openfinance.service.CompoundInterestService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for compound interest calculations.
 *
 * <p>API Endpoint:
 *
 * <ul>
 *   <li>POST /api/v1/calculator/compound-interest/calculate
 * </ul>
 */
@RestController
@RequestMapping("/api/v1/calculator/compound-interest")
@RequiredArgsConstructor
@Slf4j
public class CompoundInterestController {

    private final CompoundInterestService compoundInterestService;

    /**
     * Calculate compound interest.
     *
     * @param request validated input parameters
     * @return calculation result including year-by-year breakdown
     */
    @PostMapping("/calculate")
    public ResponseEntity<CompoundInterestResult> calculate(
            @Valid @RequestBody CompoundInterestRequest request) {

        log.info(
                "Received compound interest calculation request: principal={}, rate={}%, freq={}, years={}",
                request.getPrincipal(),
                request.getAnnualRate(),
                request.getCompoundingFrequency(),
                request.getYears());

        CompoundInterestResult result = compoundInterestService.calculate(request);

        return ResponseEntity.ok(result);
    }
}
