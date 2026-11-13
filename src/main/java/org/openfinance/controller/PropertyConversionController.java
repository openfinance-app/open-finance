package org.openfinance.controller;

import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.openfinance.config.ImportProperties;
import org.openfinance.dto.PropertyConversionCandidate;
import org.openfinance.dto.PropertyConversionRequest;
import org.openfinance.dto.PropertyPurchaseResponse;
import org.openfinance.service.PropertyConversionService;
import org.openfinance.util.ControllerUtil;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/real-estate/conversion")
@RequiredArgsConstructor
public class PropertyConversionController {
    private final PropertyConversionService propertyConversionService;
    private final ImportProperties importProperties;

    @GetMapping("/candidates")
    public ResponseEntity<List<PropertyConversionCandidate>> candidates(
            Authentication authentication) {
        if (!importProperties.isSkroogeJsonEnabled()) return ResponseEntity.notFound().build();
        return ResponseEntity.ok(
                propertyConversionService.candidates(ControllerUtil.extractUserId(authentication)));
    }

    @PostMapping
    public ResponseEntity<PropertyPurchaseResponse> convert(
            Authentication authentication, @Valid @RequestBody PropertyConversionRequest request) {
        if (!importProperties.isSkroogeJsonEnabled()) return ResponseEntity.notFound().build();
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(
                        propertyConversionService.convert(
                                ControllerUtil.extractUserId(authentication), request));
    }
}
