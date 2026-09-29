package org.openfinance.service.ai;

/** A fact's identity binds its amount to a specific metric, entity, currency and period. */
public record FinancialFact(
        String id, String label, String amount, String currency, String period, String entity) {}
