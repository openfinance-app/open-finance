import { useState, useCallback } from 'react';
import type {
    EarlyPayoffInput,
    EarlyPayoffResult,
    EarlyPayoffScenario,
    EarlyPayoffYearRow,
    LumpSumPayment,
} from '../types/calculator';
import { DEFAULT_EARLY_PAYOFF_INPUT } from '../types/calculator';
import type { EarlyPayoffCountryConfig } from '@/configs/tools/earlyPayoffConfig';
import { add, divide, multiply, pow, subtract, sum } from '@/utils/money';

// ── Pure calculation helpers ─────────────────────────────────────────────────

function calcMonthlyPayment(balance: number, monthlyRate: number, months: number): number {
    if (months <= 0 || balance <= 0) return 0;
    if (monthlyRate === 0) return divide(balance, months);
    return divide(multiply(balance, monthlyRate), subtract(1, pow(add(1, monthlyRate), -months)));
}

/**
 * Early-repayment penalty calculation driven by country config.
 * Uses the lower of:
 *   (a) iraMonthsInterestCap months of interest on the reimbursed amount
 *   (b) iraCapitalPercentCap × outstanding capital before repayment
 */
function computeIRA(
    lumpSumAmount: number,
    balanceBefore: number,
    monthlyRate: number,
    cfg: EarlyPayoffCountryConfig,
): number {
    if (!cfg.hasIRA || lumpSumAmount <= 0) return 0;
    const cap1 = cfg.iraMonthsInterestCap > 0
        ? multiply(multiply(lumpSumAmount, monthlyRate), cfg.iraMonthsInterestCap)
        : Infinity;
    const cap2 = cfg.iraCapitalPercentCap > 0
        ? multiply(balanceBefore, cfg.iraCapitalPercentCap)
        : Infinity;
    return Math.min(cap1, cap2);
}

interface MonthRow {
    month: number;
    payment: number;
    principal: number;
    interest: number;
    lumpSum: number;
    ira: number;
    balance: number;
}

/**
 * Simulate one amortisation path.
 *
 * @param mode
 *   'base'           – no lump sums, standard schedule
 *   'reduceDuration' – apply lump sums, keep monthly payment → shorter term
 *   'reducePayment'  – apply lump sums, keep original end-date → lower payment
 */
function simulate(
    initialBalance: number,
    monthlyRate: number,
    totalRemainingMonths: number,
    lumpSums: LumpSumPayment[],
    monthlyExtraPayment: number,
    mode: 'base' | 'reduceDuration' | 'reducePayment',
    cfg: EarlyPayoffCountryConfig,
): { rows: MonthRow[]; baseMonthlyPayment: number; finalMonthlyPayment: number } {
    const basePayment = calcMonthlyPayment(initialBalance, monthlyRate, totalRemainingMonths);

    // Aggregate lump sums by month
    const lumpSumByMonth = new Map<number, number>();
    if (mode !== 'base') {
        for (const ls of lumpSums) {
            if (ls.amount > 0 && ls.month > 0) {
                lumpSumByMonth.set(ls.month, add(lumpSumByMonth.get(ls.month) ?? 0, ls.amount));
            }
        }
    }

    // Monthly extra payment is only applied in non-base scenarios
    const effectiveMonthlyExtra = mode !== 'base' ? monthlyExtraPayment : 0;

    let balance = initialBalance;
    let currentMonthlyPayment = basePayment;
    const rows: MonthRow[] = [];
    const maxMonths = Math.max(totalRemainingMonths * 2, 600);

    for (let month = 1; month <= maxMonths && balance > 0.005; month++) {
        const interest = multiply(balance, monthlyRate);
        // Cap payment at remaining balance + interest (handles last payment rounding)
        const payment = Math.min(currentMonthlyPayment, add(balance, interest));
        const principal = Math.max(0, subtract(payment, interest));
        balance = Math.max(0, subtract(balance, principal));

        // Apply monthly extra payment if balance remains
        let extraApplied = 0;
        if (effectiveMonthlyExtra > 0 && balance > 0.005) {
            extraApplied = Math.min(effectiveMonthlyExtra, balance);
            balance = Math.max(0, subtract(balance, extraApplied));
        }

        let lumpSumApplied = 0;
        let iraApplied = 0;
        const lsAmount = lumpSumByMonth.get(month) ?? 0;

        if (lsAmount > 0 && balance > 0.005) {
            const balanceBefore = balance;
            lumpSumApplied = Math.min(lsAmount, balance);
            iraApplied = computeIRA(lumpSumApplied, balanceBefore, monthlyRate, cfg);
            balance = Math.max(0, subtract(balance, lumpSumApplied));

            if (balance > 0.005) {
                if (mode === 'reducePayment') {
                    // Maintain original payoff date: recalculate payment for remaining original months
                    const origRemainingFromHere = totalRemainingMonths - month;
                    if (origRemainingFromHere > 0) {
                        currentMonthlyPayment = calcMonthlyPayment(balance, monthlyRate, origRemainingFromHere);
                    }
                }
                // For 'reduceDuration': keep same payment; the loop naturally ends sooner.
            }
        }

        // Accumulate extra payment into lump sum for reporting purposes
        const totalLumpSumThisMonth = add(lumpSumApplied, extraApplied);
        rows.push({ month, payment, principal, interest, lumpSum: totalLumpSumThisMonth, ira: iraApplied, balance });
        if (balance <= 0.005) break;
    }

    return { rows, baseMonthlyPayment: basePayment, finalMonthlyPayment: currentMonthlyPayment };
}

/** Aggregate monthly rows into a yearly schedule summary. */
function buildYearlySchedule(rows: MonthRow[]): EarlyPayoffYearRow[] {
    const map = new Map<number, EarlyPayoffYearRow>();
    for (const row of rows) {
        const year = Math.ceil(row.month / 12);
        const existing = map.get(year) ?? { year, totalPayment: 0, principalPaid: 0, interestPaid: 0, lumpSum: 0, ira: 0, endBalance: 0 };
        existing.totalPayment = add(existing.totalPayment, sum([row.payment, row.lumpSum, row.ira]));
        existing.principalPaid = add(existing.principalPaid, add(row.principal, row.lumpSum));
        existing.interestPaid = add(existing.interestPaid, row.interest);
        existing.lumpSum = add(existing.lumpSum, row.lumpSum);
        existing.ira = add(existing.ira, row.ira);
        existing.endBalance = row.balance;
        map.set(year, existing);
    }
    return Array.from(map.values());
}

function buildScenario(
    rows: MonthRow[],
    baseMonthlyPayment: number,
    finalMonthlyPayment: number,
    baseScenario: EarlyPayoffScenario | null,
): EarlyPayoffScenario {
    const totalInterest = sum(rows.map(r => r.interest));
    const totalIRA = sum(rows.map(r => r.ira));
    const totalLumpSum = sum(rows.map(r => r.lumpSum));
    const totalMonths = rows.length;
    const totalPayments = sum(rows.map(r => r.payment));
    const totalCost = sum([totalPayments, totalLumpSum, totalIRA]);

    const interestSaved = baseScenario ? Math.max(0, subtract(baseScenario.totalInterest, totalInterest)) : 0;
    const timeSavedMonths = baseScenario ? Math.max(0, baseScenario.totalMonths - totalMonths) : 0;
    const netSavings = subtract(interestSaved, totalIRA);

    return {
        monthlyPayment: baseMonthlyPayment,
        finalMonthlyPayment,
        totalMonths,
        totalInterest,
        totalIRA,
        totalLumpSum,
        totalCost,
        interestSaved,
        timeSavedMonths,
        netSavings,
        yearlySchedule: buildYearlySchedule(rows),
    };
}

// ── Hook ────────────────────────────────────────────────────────────────────

let _idCounter = 2;
function nextId(): string { return String(++_idCounter); }

export function useEarlyPayoffCalculator(cfg: EarlyPayoffCountryConfig) {
    const [input, setInput] = useState<EarlyPayoffInput>(DEFAULT_EARLY_PAYOFF_INPUT);
    const [result, setResult] = useState<EarlyPayoffResult | null>(null);

    const updateInput = useCallback(<K extends keyof EarlyPayoffInput>(key: K, value: EarlyPayoffInput[K]) => {
        setInput(prev => ({ ...prev, [key]: value }));
        setResult(null);
    }, []);

    const addLumpSum = useCallback(() => {
        setInput(prev => ({
            ...prev,
            lumpSumPayments: [
                ...prev.lumpSumPayments,
                { id: nextId(), month: 12, amount: 10000 },
            ],
        }));
        setResult(null);
    }, []);

    const updateLumpSum = useCallback((id: string, field: 'month' | 'amount', value: number) => {
        setInput(prev => ({
            ...prev,
            lumpSumPayments: prev.lumpSumPayments.map(ls =>
                ls.id === id ? { ...ls, [field]: value } : ls
            ),
        }));
        setResult(null);
    }, []);

    const removeLumpSum = useCallback((id: string) => {
        setInput(prev => ({
            ...prev,
            lumpSumPayments: prev.lumpSumPayments.filter(ls => ls.id !== id),
        }));
        setResult(null);
    }, []);

    const resetInputs = useCallback(() => {
        setInput(DEFAULT_EARLY_PAYOFF_INPUT);
        setResult(null);
    }, []);

    const calculate = useCallback(() => {
        const { loanBalance, annualRate, remainingYears, remainingMonthsExtra, lumpSumPayments, monthlyExtraPayment } = input;

        if (loanBalance <= 0 || annualRate <= 0 || (remainingYears <= 0 && remainingMonthsExtra <= 0)) {
            setResult(null);
            return;
        }

        const monthlyRate = divide(divide(annualRate, 100), 12);
        const totalRemainingMonths = remainingYears * 12 + remainingMonthsExtra;

        // No-IRA config for base scenario (never penalise the base)
        const noIraCfg: EarlyPayoffCountryConfig = { ...cfg, hasIRA: false };

        // Base scenario – no extra payments
        const { rows: baseRows, baseMonthlyPayment } = simulate(
            loanBalance, monthlyRate, totalRemainingMonths, [], 0, 'base', noIraCfg,
        );
        const base = buildScenario(baseRows, baseMonthlyPayment, baseMonthlyPayment, null);

        // Reduce-duration scenario – keep same payment, loan ends earlier
        const { rows: rdRows, baseMonthlyPayment: rdBase, finalMonthlyPayment: rdFinal } = simulate(
            loanBalance, monthlyRate, totalRemainingMonths, lumpSumPayments, monthlyExtraPayment, 'reduceDuration', cfg,
        );
        const reduceDuration = buildScenario(rdRows, rdBase, rdFinal, base);

        // Reduce-payment scenario – keep original end date, lower monthly payment
        const { rows: rpRows, baseMonthlyPayment: rpBase, finalMonthlyPayment: rpFinal } = simulate(
            loanBalance, monthlyRate, totalRemainingMonths, lumpSumPayments, monthlyExtraPayment, 'reducePayment', cfg,
        );
        const reducePayment = buildScenario(rpRows, rpBase, rpFinal, base);

        setResult({ base, reduceDuration, reducePayment });
    }, [input]);

    return {
        input,
        result,
        updateInput,
        addLumpSum,
        updateLumpSum,
        removeLumpSum,
        resetInputs,
        calculate,
    };
}
