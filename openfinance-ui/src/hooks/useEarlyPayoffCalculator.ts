import { DEFAULT_CURRENCY, getMonetaryScale } from '@/utils/currency';
import { useState, useCallback, useEffect } from 'react';
import { validateEarlyPayoff } from '@/validators/calculatorValidation';
import type {
  EarlyPayoffInput,
  EarlyPayoffResult,
  EarlyPayoffScenario,
  EarlyPayoffYearRow,
  LumpSumPayment,
} from '../types/calculator';
import { DEFAULT_EARLY_PAYOFF_INPUT } from '../types/calculator';
import type { EarlyPayoffCountryConfig } from '@/configs/tools/earlyPayoffConfig';
import type Decimal from 'decimal.js';
import { calculatedAmounts, decimalValue, ExactDecimal } from '@/utils/exact-decimal';

// ── Pure calculation helpers ─────────────────────────────────────────────────

function calcMonthlyPayment(balance: Decimal, monthlyRate: Decimal, months: number): Decimal {
  if (months <= 0 || balance.lte(0)) return new ExactDecimal(0);
  if (monthlyRate.isZero()) return balance.div(months);
  return balance
    .times(monthlyRate)
    .div(new ExactDecimal(1).minus(monthlyRate.plus(1).pow(-months)));
}

/** Apply the existing country-configured penalty caps at calculation precision. */
function computeIRA(
  lumpSumAmount: Decimal,
  balanceBefore: Decimal,
  monthlyRate: Decimal,
  cfg: EarlyPayoffCountryConfig
): Decimal {
  if (!cfg.hasIRA || lumpSumAmount.lte(0)) return new ExactDecimal(0);
  const interestCap =
    cfg.iraMonthsInterestCap > 0
      ? lumpSumAmount.times(monthlyRate).times(cfg.iraMonthsInterestCap)
      : new ExactDecimal(Infinity);
  const capitalCap =
    cfg.iraCapitalPercentCap > 0
      ? balanceBefore.times(cfg.iraCapitalPercentCap)
      : new ExactDecimal(Infinity);
  return ExactDecimal.min(interestCap, capitalCap);
}

interface MonthRow {
  month: number;
  payment: Decimal;
  principal: Decimal;
  interest: Decimal;
  lumpSum: Decimal;
  ira: Decimal;
  balance: Decimal;
}

/** Keep working values throughout each path; bound only the result records. */
function simulate(
  initialBalance: number | string,
  monthlyRate: Decimal,
  totalRemainingMonths: number,
  lumpSums: LumpSumPayment[],
  monthlyExtraPayment: number | string,
  mode: 'base' | 'reduceDuration' | 'reducePayment',
  cfg: EarlyPayoffCountryConfig,
  currency: string
): { rows: MonthRow[]; baseMonthlyPayment: Decimal; finalMonthlyPayment: Decimal } {
  const basePayment = calcMonthlyPayment(
    new ExactDecimal(initialBalance),
    monthlyRate,
    totalRemainingMonths
  );
  const lumpSumByMonth = new Map<number, Decimal>();
  if (mode !== 'base') {
    for (const ls of lumpSums) {
      if (new ExactDecimal(ls.amount).gt(0) && ls.month > 0) {
        lumpSumByMonth.set(
          ls.month,
          new ExactDecimal(lumpSumByMonth.get(ls.month) ?? 0).plus(ls.amount)
        );
      }
    }
  }
  const effectiveMonthlyExtra = new ExactDecimal(mode !== 'base' ? monthlyExtraPayment : 0);
  let balance = new ExactDecimal(initialBalance);
  let currentMonthlyPayment = basePayment;
  const rows: MonthRow[] = [];
  const maxMonths = Math.max(totalRemainingMonths * 2, 600);
  // Include the remaining settlement-unit fraction in the final payment; never drop debt.
  const payoffTolerance = new ExactDecimal(10).pow(-getMonetaryScale(currency)).div(2);
  for (let month = 1; month <= maxMonths && balance.gt(0); month++) {
    const interest = balance.times(monthlyRate);
    let payment = ExactDecimal.min(currentMonthlyPayment, balance.plus(interest));
    let principal = ExactDecimal.max(0, payment.minus(interest));
    balance = ExactDecimal.max(0, balance.minus(principal));
    const extraApplied = ExactDecimal.min(effectiveMonthlyExtra, balance);
    balance = ExactDecimal.max(0, balance.minus(extraApplied));
    let lumpSumApplied = new ExactDecimal(0);
    let iraApplied = new ExactDecimal(0);
    const lsAmount = lumpSumByMonth.get(month) ?? new ExactDecimal(0);
    if (lsAmount.gt(0) && balance.gt(0)) {
      lumpSumApplied = ExactDecimal.min(lsAmount, balance);
      iraApplied = computeIRA(lumpSumApplied, balance, monthlyRate, cfg);
      balance = ExactDecimal.max(0, balance.minus(lumpSumApplied));
      if (balance.gt(0) && mode === 'reducePayment' && totalRemainingMonths > month) {
        currentMonthlyPayment = calcMonthlyPayment(
          balance,
          monthlyRate,
          totalRemainingMonths - month
        );
      }
    }
    if (balance.gt(0) && balance.lte(payoffTolerance)) {
      payment = payment.plus(balance);
      principal = principal.plus(balance);
      balance = new ExactDecimal(0);
    }
    rows.push({
      month,
      payment,
      principal,
      interest,
      lumpSum: lumpSumApplied.plus(extraApplied),
      ira: iraApplied,
      balance,
    });
  }
  return { rows, baseMonthlyPayment: basePayment, finalMonthlyPayment: currentMonthlyPayment };
}

type YearAmounts = Record<
  'totalPayment' | 'principalPaid' | 'interestPaid' | 'lumpSum' | 'ira' | 'endBalance',
  Decimal
>;
function buildYearlySchedule(rows: MonthRow[]): EarlyPayoffYearRow[] {
  const map = new Map<number, YearAmounts>();
  for (const row of rows) {
    const year = Math.ceil(row.month / 12);
    const existing = map.get(year) ?? {
      totalPayment: new ExactDecimal(0),
      principalPaid: new ExactDecimal(0),
      interestPaid: new ExactDecimal(0),
      lumpSum: new ExactDecimal(0),
      ira: new ExactDecimal(0),
      endBalance: new ExactDecimal(0),
    };
    existing.totalPayment = existing.totalPayment.plus(row.payment).plus(row.lumpSum).plus(row.ira);
    existing.principalPaid = existing.principalPaid.plus(row.principal).plus(row.lumpSum);
    existing.interestPaid = existing.interestPaid.plus(row.interest);
    existing.lumpSum = existing.lumpSum.plus(row.lumpSum);
    existing.ira = existing.ira.plus(row.ira);
    existing.endBalance = row.balance;
    map.set(year, existing);
  }
  return Array.from(map, ([year, amounts]) => ({ year, ...calculatedAmounts(amounts) }));
}

function buildScenario(
  rows: MonthRow[],
  baseMonthlyPayment: Decimal,
  finalMonthlyPayment: Decimal,
  baseScenario: EarlyPayoffScenario | null
): EarlyPayoffScenario {
  const total = (field: keyof Omit<MonthRow, 'month'>): Decimal =>
    rows.reduce((sum, row) => sum.plus(row[field]), new ExactDecimal(0));
  const totalInterest = total('interest');
  const totalIRA = total('ira');
  const totalLumpSum = total('lumpSum');
  const interestSaved = baseScenario
    ? ExactDecimal.max(
        0,
        new ExactDecimal(decimalValue(baseScenario, 'totalInterest') ?? 0).minus(totalInterest)
      )
    : new ExactDecimal(0);
  return {
    ...calculatedAmounts({
      monthlyPayment: baseMonthlyPayment,
      finalMonthlyPayment,
      totalInterest,
      totalIRA,
      totalLumpSum,
      totalCost: total('payment').plus(totalLumpSum).plus(totalIRA),
      interestSaved,
      netSavings: interestSaved.minus(totalIRA),
    }),
    totalMonths: rows.length,
    timeSavedMonths: baseScenario ? Math.max(0, baseScenario.totalMonths - rows.length) : 0,
    yearlySchedule: buildYearlySchedule(rows),
  };
}

// ── Hook ────────────────────────────────────────────────────────────────────

let _idCounter = 2;
function nextId(): string {
  return String(++_idCounter);
}

export function useEarlyPayoffCalculator(
  cfg: EarlyPayoffCountryConfig,
  currency = DEFAULT_CURRENCY
) {
  const [input, setInput] = useState<EarlyPayoffInput>(DEFAULT_EARLY_PAYOFF_INPUT);
  const [result, setResult] = useState<EarlyPayoffResult | null>(null);
  const [error, setError] = useState<string | null>(null);
  useEffect(() => {
    setResult(null);
    setError(null);
  }, [currency]);

  const updateInput = useCallback(
    <K extends keyof EarlyPayoffInput>(key: K, value: EarlyPayoffInput[K]) => {
      setInput(prev => ({ ...prev, [key]: value }));
      setResult(null);
      setError(null);
    },
    []
  );

  const addLumpSum = useCallback(() => {
    setInput(prev => ({
      ...prev,
      lumpSumPayments: [...prev.lumpSumPayments, { id: nextId(), month: 12, amount: 10000 }],
    }));
    setResult(null);
    setError(null);
  }, []);

  const updateLumpSum = useCallback(
    <K extends 'month' | 'amount'>(id: string, field: K, value: LumpSumPayment[K]) => {
      setInput(prev => ({
        ...prev,
        lumpSumPayments: prev.lumpSumPayments.map(ls =>
          ls.id === id ? { ...ls, [field]: value } : ls
        ),
      }));
      setResult(null);
      setError(null);
    },
    []
  );

  const removeLumpSum = useCallback((id: string) => {
    setInput(prev => ({
      ...prev,
      lumpSumPayments: prev.lumpSumPayments.filter(ls => ls.id !== id),
    }));
    setResult(null);
    setError(null);
  }, []);

  const resetInputs = useCallback(() => {
    setInput(DEFAULT_EARLY_PAYOFF_INPUT);
    setResult(null);
    setError(null);
  }, []);

  const calculate = useCallback(() => {
    const {
      loanBalance,
      annualRate,
      remainingYears,
      remainingMonthsExtra,
      lumpSumPayments,
      monthlyExtraPayment,
    } = input;

    const validationError = validateEarlyPayoff(input);
    setError(validationError);
    if (validationError) {
      setResult(null);
      return;
    }

    const monthlyRate = new ExactDecimal(annualRate).div(1200);
    const totalRemainingMonths = remainingYears * 12 + remainingMonthsExtra;

    // No-IRA config for base scenario (never penalise the base)
    const noIraCfg: EarlyPayoffCountryConfig = { ...cfg, hasIRA: false };

    // Base scenario – no extra payments
    const { rows: baseRows, baseMonthlyPayment } = simulate(
      loanBalance,
      monthlyRate,
      totalRemainingMonths,
      [],
      0,
      'base',
      noIraCfg,
      currency
    );
    const base = buildScenario(baseRows, baseMonthlyPayment, baseMonthlyPayment, null);

    // Reduce-duration scenario – keep same payment, loan ends earlier
    const {
      rows: rdRows,
      baseMonthlyPayment: rdBase,
      finalMonthlyPayment: rdFinal,
    } = simulate(
      loanBalance,
      monthlyRate,
      totalRemainingMonths,
      lumpSumPayments,
      monthlyExtraPayment,
      'reduceDuration',
      cfg,
      currency
    );
    const reduceDuration = buildScenario(rdRows, rdBase, rdFinal, base);

    // Reduce-payment scenario – keep original end date, lower monthly payment
    const {
      rows: rpRows,
      baseMonthlyPayment: rpBase,
      finalMonthlyPayment: rpFinal,
    } = simulate(
      loanBalance,
      monthlyRate,
      totalRemainingMonths,
      lumpSumPayments,
      monthlyExtraPayment,
      'reducePayment',
      cfg,
      currency
    );
    const reducePayment = buildScenario(rpRows, rpBase, rpFinal, base);

    setResult({ base, reduceDuration, reducePayment });
  }, [input, cfg, currency]);

  return {
    input,
    result,
    error,
    updateInput,
    addLumpSum,
    updateLumpSum,
    removeLumpSum,
    resetInputs,
    calculate,
  };
}
