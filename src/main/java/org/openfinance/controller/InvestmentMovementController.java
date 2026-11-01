package org.openfinance.controller;

import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.openfinance.dto.InvestmentQuote;
import org.openfinance.entity.User;
import org.openfinance.service.InvestmentMovementService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/assets")
@RequiredArgsConstructor
public class InvestmentMovementController {
    private final InvestmentMovementService movements;

    @GetMapping("/{id}/investment-quote")
    public InvestmentQuote quote(
            @PathVariable Long id,
            @RequestParam BigDecimal amount,
            @RequestParam String currency,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) Long transactionId,
            Authentication authentication) {
        User user = (User) authentication.getPrincipal();
        return movements.quote(id, user.getId(), amount, currency, date, transactionId);
    }
}
