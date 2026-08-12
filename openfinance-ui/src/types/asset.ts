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

export type AssetCondition =
  | 'NEW'
  | 'EXCELLENT'
  | 'GOOD'
  | 'FAIR'
  | 'POOR';

/**
 * Asset filters for search
 */
export interface AssetFilters {
  keyword?: string;
  type?: AssetType;
  accountId?: number;
  currency?: string;
  symbol?: string;
  purchaseDateFrom?: string;
  purchaseDateTo?: string;
  valueMin?: number;
  valueMax?: number;
  // Pagination
  page?: number;
  size?: number;
  sort?: string;
}

export interface Asset {
  id: number;
  userId: number;
  accountId?: number;
  accountName?: string;
  name: string;
  type: AssetType;
  symbol?: string;
  quantity: number;
  purchasePrice: number;
  currentPrice: number;
  currency: string;
  purchaseDate: string;
  notes?: string;
  lastUpdated: string;
  createdAt: string;
  updatedAt: string;
  totalValue: number;      // Calculated: quantity * currentPrice
  totalCost: number;       // Calculated: quantity * purchasePrice
  unrealizedGain: number;  // Calculated: totalValue - totalCost
  gainPercentage: number;  // Calculated: (unrealizedGain / totalCost) * 100
  holdingDays: number;     // Calculated: days since purchase

  // Physical asset fields (Task 9.2.5)
  serialNumber?: string;
  brand?: string;
  model?: string;
  condition?: AssetCondition;
  warrantyExpiration?: string;  // ISO date string
  usefulLifeYears?: number;
  photoPath?: string;
  depreciatedValue?: number;     // Calculated: depreciated value for physical assets
  conditionAdjustedValue?: number; // Calculated: condition-adjusted value
  isPhysical?: boolean;          // Calculated: true if type is physical asset
  isWarrantyValid?: boolean;     // Calculated: true if warranty is still valid

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
  accountId?: number;
  name: string;
  type: AssetType;
  symbol?: string;
  /** Exact decimal strings as entered by the user — never JS numbers. */
  quantity: string;
  purchasePrice: string;
  currentPrice: string;
  currency: string;
  purchaseDate: string;
  notes?: string;

  // Physical asset fields (Task 9.2.5)
  serialNumber?: string;
  brand?: string;
  model?: string;
  condition?: AssetCondition;
  warrantyExpiration?: string;  // ISO date string (YYYY-MM-DD)
  usefulLifeYears?: number;
  photoPath?: string;
}

export interface AssetFilters {
  accountId?: number;
  type?: AssetType;
}
