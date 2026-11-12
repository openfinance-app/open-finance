package org.openfinance.controller;

import lombok.RequiredArgsConstructor;
import org.openfinance.dto.AssetLogoResponse;
import org.openfinance.dto.AssetResponse;
import org.openfinance.entity.User;
import org.openfinance.service.AssetLogoService;
import org.openfinance.service.AssetService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/assets")
@RequiredArgsConstructor
public class AssetLogoController {
    private final AssetService assetService;
    private final AssetLogoService assetLogoService;

    @GetMapping("/{id}/logo")
    public AssetLogoResponse getLogo(@PathVariable Long id, Authentication authentication) {
        User user = (User) authentication.getPrincipal();
        // Authorize and decrypt first, then finish the read transaction before any HTTP lookup.
        AssetResponse asset = assetService.getAssetById(id, user.getId());
        return new AssetLogoResponse(
                assetLogoService.resolveLogo(asset.getType(), asset.getSymbol(), asset.getName()));
    }
}
