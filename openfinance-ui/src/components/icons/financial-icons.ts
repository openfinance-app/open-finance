/**
 * Financial icon mappings for common account types and transaction categories
 * Uses lucide-react icons
 */

import {
  Wallet,
  PiggyBank,
  LockKeyhole,
  CreditCard,
  TrendingUp,
  Banknote,
  CircleDollarSign,
  Home,
  Car,
  ShoppingCart,
  Utensils,
  Zap,
  Smartphone,
  Heart,
  Plane,
  Gift,
  GraduationCap,
  type LucideIcon,
} from 'lucide-react';

/**
 * Account type icon mappings
 */
export const accountTypeIcons: Record<string, LucideIcon> = {
  CHECKING: Wallet,
  SAVINGS: PiggyBank,
  LOCKED_SAVINGS: LockKeyhole,
  CREDIT_CARD: CreditCard,
  INVESTMENT: TrendingUp,
  CASH: Banknote,
  OTHER: CircleDollarSign,
};

/**
 * Transaction category icon mappings
 */
export const categoryIcons: Record<string, LucideIcon> = {
  // Income
  SALARY: Banknote,
  INVESTMENT_INCOME: TrendingUp,
  OTHER_INCOME: CircleDollarSign,

  // Housing
  RENT: Home,
  MORTGAGE: Home,
  UTILITIES: Zap,
  HOME_INSURANCE: Home,

  // Transportation
  CAR_PAYMENT: Car,
  GAS: Car,
  PUBLIC_TRANSPORT: Car,
  CAR_INSURANCE: Car,

  // Food
  GROCERIES: ShoppingCart,
  RESTAURANTS: Utensils,

  // Personal
  CLOTHING: ShoppingCart,
  HEALTH: Heart,
  ENTERTAINMENT: Gift,
  EDUCATION: GraduationCap,

  // Technology
  PHONE: Smartphone,
  INTERNET: Smartphone,

  // Travel
  VACATION: Plane,
  TRAVEL: Plane,

  // Other
  GIFTS: Gift,
  OTHER: CircleDollarSign,
};

/**
 * Get icon for account type
 */
export function getAccountTypeIcon(accountType: string): LucideIcon {
  return accountTypeIcons[accountType] || CircleDollarSign;
}

/**
 * Get icon for transaction category
 */
export function getCategoryIcon(category: string): LucideIcon {
  return categoryIcons[category.toUpperCase().replace(/\s+/g, '_')] || CircleDollarSign;
}
