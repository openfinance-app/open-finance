package org.openfinance.controller;

import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.openfinance.config.ImportProperties;
import org.openfinance.dto.LiabilityConversionCandidate;
import org.openfinance.dto.LiabilityConversionRequest;
import org.openfinance.dto.LiabilityResponse;
import org.openfinance.service.LiabilityConversionPayments;
import org.openfinance.service.LiabilityConversionService;
import org.openfinance.util.ControllerUtil;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/liabilities/conversion")
@RequiredArgsConstructor
public class LiabilityConversionController {
    private final LiabilityConversionService liabilityConversionService;
    private final ImportProperties importProperties;

    @GetMapping("/candidates")
    public ResponseEntity<List<LiabilityConversionCandidate>> candidates(
            Authentication authentication) {
        if (!importProperties.isSkroogeJsonEnabled()) return ResponseEntity.notFound().build();
        return ResponseEntity.ok(
                liabilityConversionService.candidates(
                        ControllerUtil.extractUserId(authentication)));
    }

    @PostMapping
    public ResponseEntity<LiabilityResponse> convert(
            Authentication authentication, @Valid @RequestBody LiabilityConversionRequest request) {
        if (!importProperties.isSkroogeJsonEnabled()) return ResponseEntity.notFound().build();
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(
                        liabilityConversionService.convert(
                                ControllerUtil.extractUserId(authentication), request));
    }

    @GetMapping("/{accountId}/payments")
    public ResponseEntity<LiabilityConversionPayments.Candidates> payments(
            Authentication authentication,
            @PathVariable Long accountId,
            @RequestParam(required = false) String search) {
        if (!importProperties.isSkroogeJsonEnabled()) return ResponseEntity.notFound().build();
        return ResponseEntity.ok(
                liabilityConversionService.payments(
                        ControllerUtil.extractUserId(authentication), accountId, search));
    }
}
