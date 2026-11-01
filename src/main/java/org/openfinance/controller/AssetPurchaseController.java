package org.openfinance.controller;

import jakarta.validation.Valid;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.openfinance.dto.AssetPurchaseRequest;
import org.openfinance.dto.AssetSaleRequest;
import org.openfinance.entity.User;
import org.openfinance.service.AssetPurchaseService;
import org.openfinance.service.AssetSaleService;
import org.openfinance.service.PurchasePaymentService;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/assets")
@RequiredArgsConstructor
public class AssetPurchaseController {
    private final AssetPurchaseService purchases;
    private final AssetSaleService sales;
    private final PurchasePaymentService payments;

    public record LinkPaymentRequest(@jakarta.validation.constraints.NotNull Long transactionId) {}

    @PostMapping("/{id}/buy")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Long> buyMore(
            @PathVariable Long id,
            @Valid @RequestBody org.openfinance.dto.AssetBuyRequest request,
            Authentication auth) {
        return Map.of(
                "transactionId",
                purchases.buyMore(((User) auth.getPrincipal()).getId(), id, request));
    }

    @PostMapping("/{id}/purchase-payment")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void linkPayment(
            @PathVariable Long id,
            @Valid @RequestBody LinkPaymentRequest request,
            Authentication auth) {
        payments.linkExisting(((User) auth.getPrincipal()).getId(), id, request.transactionId());
    }

    @PostMapping("/{id}/sale")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Long> sell(
            @PathVariable Long id,
            @Valid @RequestBody AssetSaleRequest request,
            Authentication auth) {
        return Map.of(
                "transactionId", sales.sell(((User) auth.getPrincipal()).getId(), id, request));
    }

    @PostMapping("/purchase")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Long> purchase(
            @Valid @RequestBody AssetPurchaseRequest request, Authentication auth) {
        return Map.of("assetId", purchases.purchase(((User) auth.getPrincipal()).getId(), request));
    }
}
