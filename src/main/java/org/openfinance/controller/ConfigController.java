package org.openfinance.controller;

import lombok.RequiredArgsConstructor;
import org.openfinance.config.EncryptionProperties;
import org.openfinance.dto.SecurityConfigResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Exposes public runtime configuration required by the unauthenticated UI. */
@RestController
@RequestMapping("/api/v1/config")
@RequiredArgsConstructor
public class ConfigController {

    private final EncryptionProperties encryptionProperties;

    @GetMapping("/security")
    public SecurityConfigResponse getSecurityConfig() {
        return SecurityConfigResponse.builder()
                .encryptionEnabled(encryptionProperties.isEnabled())
                .build();
    }
}
