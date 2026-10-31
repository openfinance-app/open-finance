package org.openfinance.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.openfinance.dto.AccountResponse;
import org.openfinance.dto.AssetResponse;
import org.openfinance.dto.LiabilityResponse;
import org.openfinance.dto.MetadataUpdateRequest;
import org.openfinance.dto.PayeeResponse;
import org.openfinance.dto.TransactionResponse;
import org.openfinance.entity.User;
import org.openfinance.service.AccountService;
import org.openfinance.service.AssetService;
import org.openfinance.service.LiabilityService;
import org.openfinance.service.PayeeService;
import org.openfinance.service.TransactionService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Field-scoped edits used by bulk actions; each record keeps its own validation and undo entry. */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class MetadataUpdateController {
    private final AssetService assetService;
    private final AccountService accountService;
    private final LiabilityService liabilityService;
    private final TransactionService transactionService;
    private final PayeeService payeeService;

    @PatchMapping("/assets/{id}/metadata")
    public ResponseEntity<AssetResponse> updateAsset(
            @PathVariable Long id,
            @Valid @RequestBody MetadataUpdateRequest.Asset request,
            Authentication authentication) {
        User user = (User) authentication.getPrincipal();
        return ResponseEntity.ok(assetService.updateMetadata(id, user.getId(), request));
    }

    @PatchMapping("/accounts/{id}/metadata")
    public ResponseEntity<AccountResponse> updateAccount(
            @PathVariable Long id,
            @Valid @RequestBody MetadataUpdateRequest.Account request,
            Authentication authentication) {
        User user = (User) authentication.getPrincipal();
        return ResponseEntity.ok(accountService.updateMetadata(id, user.getId(), request));
    }

    @PatchMapping("/liabilities/{id}/metadata")
    public ResponseEntity<LiabilityResponse> updateLiability(
            @PathVariable Long id,
            @Valid @RequestBody MetadataUpdateRequest.Liability request,
            Authentication authentication) {
        User user = (User) authentication.getPrincipal();
        return ResponseEntity.ok(liabilityService.updateMetadata(id, user.getId(), request));
    }

    @PatchMapping("/transactions/{id}/metadata")
    public ResponseEntity<TransactionResponse> updateTransaction(
            @PathVariable Long id,
            @Valid @RequestBody MetadataUpdateRequest.Transaction request,
            Authentication authentication) {
        User user = (User) authentication.getPrincipal();
        return ResponseEntity.ok(transactionService.updateMetadata(id, user.getId(), request));
    }

    @PatchMapping("/payees/{id}/metadata")
    public ResponseEntity<PayeeResponse> updatePayee(
            @PathVariable Long id,
            @Valid @RequestBody MetadataUpdateRequest.Payee request,
            Authentication authentication) {
        User user = (User) authentication.getPrincipal();
        return ResponseEntity.ok(payeeService.updateMetadata(id, user.getId(), request));
    }
}
