import type { Account } from './account';
import type { Transaction } from './transaction';
import type { AssetType } from './asset';

/**
 * Net Worth Summary with monthly change statistics
 */
export interface INetWorthSummary {
  date: string; // ISO date string
  totalAssets: number;
  totalLiabilities: number;
  netWorth: number;
  monthlyChangeAmount: number | null;
  monthlyChangePercentage: number | null;
  currency: string;
}

/**
 * Account Summary for dashboard display
 */
export interface IAccountSummary {
  id: number;
  name: string;
  type: Account['type'];
  balance: number;
  currency: string;
  isActive: boolean;
  description?: string;
}

/**
 * Complete Dashboard Summary
 */
export interface IDashboardSummary {
  netWorth: INetWorthSummary;
  accounts: IAccountSummary[];
  recentTransactions: Transaction[];
  snapshotDate: string; // ISO date string
  totalAccounts: number;
  totalTransactions: number;
  baseCurrency: string;
}

/**
 * Cash Flow data (income vs expenses)
 */
export interface ICashFlow {
  income: number;
  expenses: number;
  netCashFlow: number;
}

/**
 * Spending by Category data
 * Map of category ID/name to spending amount
 */
export type ISpendingByCategory = Record<string, number>;

/**
 * Asset Allocation by type for treemap visualization
 * Task 4.3.6: AssetAllocationChart component data
 */
export interface IAssetAllocation {
  type: AssetType;
  typeName: string;
  totalValue: number;
  percentage: number;
  assetCount: number;
  currency: string;
}

/**
 * Historical data point for sparkline charts
 * Task 4.3.8: PortfolioPerformanceCards component data
 */
export interface IHistoricalDataPoint {
  date: string; // ISO date string
  value: number;
}

/**
 * Portfolio Performance metric with sparkline data
 * Task 4.3.8: PortfolioPerformanceCards component data
 */
export interface IPortfolioPerformance {
  label: string;
  currentValue: number;
  changeAmount: number;
  changePercentage: number;
  currency: string;
  sparklineData: IHistoricalDataPoint[];
}

/**
 * Borrowing Capacity analysis
 * Dashboard Borrowing Capacity Card
 */
export interface IBorrowingCapacity {
  monthlyIncome: number;
  monthlyExpenses: number;
  monthlyDebtPayments: number;
  debtToIncomeRatio: number;
  recommendedMaxBorrowing: number;
  availableBorrowingCapacity: number;
  financialHealthStatus: 'EXCELLENT' | 'GOOD' | 'FAIR' | 'POOR';
  currency: string;
  analysisPeriod: number;
}

/**
 * Net Worth Allocation category
 * Dashboard Net Worth Allocation Card
 */
export interface INetWorthAllocation {
  category: string;
  value: number;
  percentage: number;
  itemCount: number;
  isLiability: boolean;
  currency: string;
  color?: string;
}

export interface IDailyCashFlow {
  date: string;
  income: number;
  expense: number;
}

/**
 * A single node (income source or expense category) in the Cashflow Sankey diagram
 */
export interface ICashflowSankeyNode {
  name: string;
  amount: number;
  color?: string | null;
  icon?: string | null;
}

/**
 * Cashflow Sankey diagram data
 * Dashboard CashflowSankeyCard component data
 */
export interface ICashflowSankey {
  totalIncome: number;
  totalExpenses: number;
  surplus: number;
  incomeSources: ICashflowSankeyNode[];
  expenseCategories: ICashflowSankeyNode[];
  period: number;
}

/**
 * Interest calculations for a single account
 */
export interface IAccountInterest {
  accountId: number;
  accountName: string;
  interestEarned: number;
  projectedInterest: number;
}

/**
 * Summary of estimated interest across accounts
 */
export interface IEstimatedInterestSummary {
  accounts: IAccountInterest[];
  totalEarned: number;
  totalProjected: number;
  currency: string;
}

/**
 * A single year's data point with amount and variation percentage
 */
export interface IYearlyDataPoint {
  year: number;
  amount: number;
  variationPercentage: number | null;
}

/**
 * Named entry (account or institution) with yearly data
 */
export interface IYearlyBalanceEntry {
  id: number;
  name: string;
  data: IYearlyDataPoint[];
}

/**
 * Yearly balance variation response for dashboard visualization
 */
export interface IYearlyBalanceResponse {
  years: number[];
  netWorth: IYearlyDataPoint[];
  accounts: IYearlyBalanceEntry[];
  institutions: IYearlyBalanceEntry[];
  currency: string;
}
