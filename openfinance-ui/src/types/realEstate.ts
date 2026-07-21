/**
 * Real Estate TypeScript Type Definitions
 * 
 * Matches backend DTOs and entities for Real Estate management
 */
import { DEFAULT_CURRENCY, formatCurrency } from '@/utils/currency';
import i18next from 'i18next';

/**
 * Property Type Enum
 * Matches: org.openfinance.entity.PropertyType
 */
export const PropertyType = {
  RESIDENTIAL: 'RESIDENTIAL',
  COMMERCIAL: 'COMMERCIAL',
  LAND: 'LAND',
  MIXED_USE: 'MIXED_USE',
  INDUSTRIAL: 'INDUSTRIAL',
  OTHER: 'OTHER'
} as const;

export type PropertyType = typeof PropertyType[keyof typeof PropertyType];

/**
 * Real Estate Property Interface
 * Matches: org.openfinance.dto.RealEstatePropertyResponse
 */
export interface RealEstateProperty {
  id: number;
  userId: number;
  name: string;
  address: string;
  propertyType: PropertyType;
  purchasePrice: number;
  purchaseDate: string; // ISO date format
  currentValue: number;
  currency: string; // ISO 4217 currency code
  mortgageId?: number | null;
  mortgageName?: string | null;
  mortgageBalance?: number | null;
  rentalIncome?: number | null;
  notes?: string | null;
  documents?: string | null; // JSON string of document references
  latitude?: number | null;
  longitude?: number | null;
  isActive: boolean;
  createdAt: string; // ISO datetime
  updatedAt: string; // ISO datetime

  // Calculated fields
  appreciation?: number;
  appreciationPercentage?: number;
  equity?: number;
  rentalYield?: number;

  // Requirement REQ-2.4: Currency conversion fields
  valueInBaseCurrency?: number;
  baseCurrency?: string;
  exchangeRate?: number;
  isConverted?: boolean;
  // Requirement REQ-3.5: Secondary currency conversion fields
  valueInSecondaryCurrency?: number;
  secondaryCurrency?: string;
  secondaryExchangeRate?: number;
}

/**
 * Real Estate Property Request DTO
 * Matches: org.openfinance.dto.RealEstatePropertyRequest
 */
export interface RealEstatePropertyRequest {
  name: string;
  address: string;
  propertyType: PropertyType;
  purchasePrice: number;
  purchaseDate: string; // ISO date format (YYYY-MM-DD)
  currentValue: number;
  currency: string; // ISO 4217 currency code (e.g., "USD")
  mortgageId?: number | null;
  rentalIncome?: number | null;
  notes?: string | null;
  documents?: string | null; // JSON string
  latitude?: number | null;
  longitude?: number | null;
  isActive?: boolean; // Optional, defaults to true on backend
}

/**
 * Property Equity Response
 * Matches: org.openfinance.dto.PropertyEquityResponse
 */
export interface PropertyEquityResponse {
  propertyId: number;
  propertyName: string;
  currentValue: number;
  mortgageBalance: number;
  equity: number;
  equityPercentage: number;
  loanToValueRatio: number;
  mortgageId?: number | null;
  hasMortgage: boolean;
  currency: string;
}

/**
 * Property ROI Response
 * Matches: org.openfinance.dto.PropertyROIResponse
 */
export interface PropertyROIResponse {
  propertyId: number;
  propertyName: string;
  purchasePrice: number;
  currentValue: number;
  purchaseDate: string; // ISO date
  yearsOwned: number;
  appreciation: number;
  appreciationPercentage: number;
  annualizedReturn: number;
  totalRentalIncome: number | null;
  monthlyRentalIncome: number | null;
  rentalYield: number | null;
  totalROI: number;
  isRentalProperty: boolean;
  currency: string;
}

/**
 * Property Filters for API requests
 */
export interface PropertyFilters {
  propertyType?: PropertyType;
  includeInactive?: boolean;
}

/**
 * Property filters for search with pagination
 */
export interface PropertySearchFilters {
  keyword?: string;
  propertyType?: PropertyType;
  currency?: string;
  isActive?: boolean;
  hasMortgage?: boolean;
  purchaseDateFrom?: string;
  purchaseDateTo?: string;
  valueMin?: number;
  valueMax?: number;
  priceMin?: number;
  priceMax?: number;
  rentalIncomeMin?: number;
  // Pagination
  page?: number;
  size?: number;
  sort?: string;
}

/**
 * Property Summary for Dashboard/Overview
 */
export interface PropertySummary {
  totalProperties: number;
  totalValue: number;
  totalEquity: number;
  totalMortgageDebt: number;
  averageAppreciation: number;
  totalRentalIncome: number;
  currency: string;
}

/**
 * Helper Functions
 */

/**
 * Get display name for PropertyType
 */
export function getPropertyTypeName(type: PropertyType): string {
  const keyMap: Record<PropertyType, string> = {
    [PropertyType.RESIDENTIAL]: 'realEstate:filters.residential',
    [PropertyType.COMMERCIAL]: 'realEstate:filters.commercial',
    [PropertyType.LAND]: 'realEstate:filters.land',
    [PropertyType.MIXED_USE]: 'realEstate:filters.mixedUse',
    [PropertyType.INDUSTRIAL]: 'realEstate:filters.industrial',
    [PropertyType.OTHER]: 'realEstate:filters.other',
  };
  const key = keyMap[type];
  if (!key) return type;
  const translated = i18next.t(key);
  return translated !== key ? translated : type;
}

/**
 * Get icon name for PropertyType (lucide-react icon names)
 */
export function getPropertyTypeIcon(type: PropertyType): string {
  const icons: Record<PropertyType, string> = {
    [PropertyType.RESIDENTIAL]: 'Home',
    [PropertyType.COMMERCIAL]: 'Building2',
    [PropertyType.LAND]: 'Mountain',
    [PropertyType.MIXED_USE]: 'Building',
    [PropertyType.INDUSTRIAL]: 'Factory',
    [PropertyType.OTHER]: 'MapPin'
  };
  return icons[type] || 'MapPin';
}

/**
 * Get badge color for PropertyType
 */
export function getPropertyTypeBadgeColor(type: PropertyType): string {
  const colors: Record<PropertyType, string> = {
    [PropertyType.RESIDENTIAL]: 'bg-blue-500/10 text-blue-500 border-blue-500/20',
    [PropertyType.COMMERCIAL]: 'bg-purple-500/10 text-purple-500 border-purple-500/20',
    [PropertyType.LAND]: 'bg-green-500/10 text-green-500 border-green-500/20',
    [PropertyType.MIXED_USE]: 'bg-yellow-500/10 text-yellow-500 border-yellow-500/20',
    [PropertyType.INDUSTRIAL]: 'bg-orange-500/10 text-orange-500 border-orange-500/20',
    [PropertyType.OTHER]: 'bg-surface-elevated text-text-muted border-border'
  };
  return colors[type] || 'bg-surface-elevated text-text-muted border-border';
}

/**
 * Format appreciation with sign and color
 * @param appreciation - Appreciation amount in the property's native currency
 * @param percentage - Appreciation as a percentage
 * @param currency - ISO 4217 currency code of the property (e.g. "JPY", "EUR")
 */
export function formatAppreciation(
  appreciation: number | undefined,
  percentage: number | undefined,
  currency: string = DEFAULT_CURRENCY
): {
  text: string;
  color: string;
} {
  if (appreciation === undefined || percentage === undefined) {
    return { text: 'N/A', color: 'text-text-muted' };
  }

  const sign = appreciation >= 0 ? '+' : '';
  const color = appreciation >= 0 ? 'text-green-400' : 'text-red-400';
  const formattedAmount = formatCurrency(Math.abs(appreciation), currency);
  const text = `${sign}${formattedAmount} (${sign}${percentage.toFixed(2)}%)`;

  return { text, color };
}

/**
 * Calculate property age in years
 */
export function calculatePropertyAge(purchaseDate: string): number {
  const purchase = new Date(purchaseDate);
  const now = new Date();
  const diffMs = now.getTime() - purchase.getTime();
  const diffYears = diffMs / (1000 * 60 * 60 * 24 * 365.25);
  return Math.floor(diffYears);
}
