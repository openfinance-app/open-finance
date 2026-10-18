package org.openfinance.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.openfinance.dto.InterestPreviewRequest;
import org.openfinance.service.InterestCalculatorService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/accounts/interest-preview")
@RequiredArgsConstructor
public class InterestPreviewController {
    private final InterestCalculatorService calculatorService;

    @PostMapping
    public InterestCalculatorService.Preview preview(
            @Valid @RequestBody InterestPreviewRequest request) {
        return calculatorService.preview(request);
    }
}
