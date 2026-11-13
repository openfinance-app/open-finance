import type { AmountCurrencyMode } from '@/types/amount-filter';
/**
 * Asset-related types
 * Task 5.2.10: Asset type definitions
 * Task 9.2.5: Added physical asset types and fields
 */

export type AssetType =
  | 'STOCK'
  | 'ETF'
  | 'CRYPTO'
  | 'BOND'
  | 'MUTUAL_FUND'
  | 'REAL_ESTATE'
  | 'COMMODITY'
  | 'VEHICLE'
  | 'JEWELRY'
  | 'COLLECTIBLE'
  | 'ELECTRONICS'
  | 'FURNITURE'
  | 'OTHER';

export type AssetCondition = 'NEW' | 'EXCELLENT' | 'GOOD' | 'FAIR' | 'POOR';

export type AssetOwnershipStatus = 'OWNED' | 'PLANNED' | 'CLOSED';

/**
 * Asset filters for search
 */
export interface AssetFilters {
  group?: 'PHYSICAL' | 'FINANCIAL';
  ownershipStatus?: AssetOwnershipStatus;
  keyword?: string;
  /** When true, `keyword` is treated as a regular expression instead of a plain substring. */
  keywordRegex?: boolean;
  type?: AssetType;
  accountId?: number;
  currency?: string;
  symbol?: string;
  purchaseDateFrom?: string;
  purchaseDateTo?: string;
  valueMin?: number;
  valueMax?: number;
  valueExact?: number;
  amountCurrencyMode?: AmountCurrencyMode;
  // Pagination
  page?: number;
  size?: number;
  sort?: string;
}

export interface Asset {
  investmentTracked?: boolean;
  exactValues?: Record<string, string>;
  ownershipStatus?: AssetOwnershipStatus;
  soldDate?: string;
  saleTransactionId?: number;
  saleProceeds?: number;
  realizedGain?: number | null;
  /** False for the retained backing asset of an inactive property. */
  isActive?: boolean;
  realEstateId?: number;
  id: number;
  userId: number;
  accountId?: number;
  accountName?: string;
  name: string;
  type: AssetType;
  symbol?: string;
  quantity: number;
  purchasePrice: number;
  receivedValue?: number | null;
  performanceBasis?: number | null;
  acquisitionType?: 'PURCHASE' | 'GIFT' | 'PLANNED';
  currentPrice: number;
  currency: string;
  purchaseDate: string;
  notes?: string;
  lastUpdated: string;
  createdAt: string;
  updatedAt: string;
  totalValue: number; // Calculated: quantity * currentPrice
  totalCost: number; // Calculated: quantity * purchasePrice
  unrealizedGain: number | null; // Calculated: totalValue - totalCost
  gainPercentage: number | null; // Calculated: (unrealizedGain / totalCost) * 100
  holdingDays: number; // Calculated: days since purchase

  // Physical asset fields (Task 9.2.5)
  serialNumber?: string;
  brand?: string;
  model?: string;
  condition?: AssetCondition | null;
  warrantyExpiration?: string | null; // ISO date string
  usefulLifeYears?: number | null;
  photoPath?: string;
  depreciatedValue?: number; // Calculated: depreciated value for physical assets
  conditionAdjustedValue?: number; // Calculated: condition-adjusted value
  isPhysical?: boolean; // Calculated: true if type is physical asset
  isWarrantyValid?: boolean; // Calculated: true if warranty is still valid

  // Requirement REQ-2.2: Currency conversion fields
  valueInBaseCurrency?: number;
  baseCurrency?: string;
  exchangeRate?: number;
  isConverted?: boolean;
  // Requirement REQ-3.5: Secondary currency conversion fields
  valueInSecondaryCurrency?: number;
  secondaryCurrency?: string;
  secondaryExchangeRate?: number;
}

export interface AssetRequest {
  accountId?: number | null;
  name: string;
  type: AssetType;
  symbol?: string;
  /** Exact decimal strings as entered by the user — never JS numbers. */
  quantity: string;
  purchasePrice: string;
  receivedValue?: string | null;
  acquisitionType?: 'PURCHASE' | 'GIFT' | 'PLANNED';
  currentPrice: string;
  currency: string;
  purchaseDate: string;
  notes?: string;

  // Physical asset fields (Task 9.2.5)
  serialNumber?: string;
  brand?: string;
  model?: string;
  condition?: AssetCondition | null;
  warrantyExpiration?: string | null; // ISO date string (YYYY-MM-DD)
  usefulLifeYears?: number | null;
  photoPath?: string;
}

export interface AssetFilters {
  accountId?: number;
  type?: AssetType;
}
