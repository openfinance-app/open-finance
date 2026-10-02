package org.openfinance.service;

/** Synchronous budget changes are evaluated before the operation's history snapshot is captured. */
public record BudgetChanged(Long userId) {}
