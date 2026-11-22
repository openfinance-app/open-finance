import type { LiabilityFilters } from '@/types/liability';
import type { AssetFilters } from '@/types/asset';
import type { AccountFilters } from '@/types/account';
import type { TransactionFilters } from '@/types/transaction';
/** Count criteria, never pagination or the view's default sort. Zero is a valid bound. */
export function countActiveFilters(values: unknown[]): number {
  return values.filter(
    value => value !== undefined && value !== null && value !== '' && value !== false
  ).length;
}

export function getTransactionFilterCounts(filters: TransactionFilters) {
  const additionalCount = countActiveFilters([
    filters.payee,
    filters.tag,
    filters.minAmount,
    filters.maxAmount,
    filters.exactAmount,
    filters.dateFrom,
    filters.dateTo,
    filters.amountCurrencyMode === 'NATIVE',
  ]);
  const activeCount =
    additionalCount +
    countActiveFilters([
      filters.keyword,
      filters.keywordRegex,
      filters.accountId,
      filters.importSessionId,
      filters.type,
      filters.categoryId,
      filters.noCategory,
      filters.noPayee,
      filters.excludeTransfers,
      filters.includeSubcategories,
      filters.assetId,
      filters.realEstateId,
      filters.liabilityId,
      filters.sort && filters.sort !== 'date,desc',
    ]);
  return { additionalCount, activeCount };
}

export function getAccountFilterCounts(filters: AccountFilters) {
  const additionalCount = countActiveFilters([
    filters.currency,
    filters.institution,
    filters.balanceMin,
    filters.balanceMax,
    filters.balanceExact,
    filters.amountCurrencyMode === 'NATIVE',
  ]);
  const activeCount =
    additionalCount +
    countActiveFilters([
      filters.keyword,
      filters.keywordRegex,
      filters.type,
      filters.isActive !== undefined,
      filters.lowBalance,
      filters.capital,
      filters.sort && filters.sort !== 'name,asc',
    ]);
  return { additionalCount, activeCount };
}

export function getAssetFilterCounts(filters: AssetFilters) {
  const additionalCount = countActiveFilters([
    filters.currency,
    filters.symbol,
    filters.valueMin,
    filters.valueMax,
    filters.valueExact,
    filters.purchaseDateFrom,
    filters.purchaseDateTo,
    filters.amountCurrencyMode === 'NATIVE',
  ]);
  const activeCount =
    additionalCount +
    countActiveFilters([
      filters.keyword,
      filters.keywordRegex,
      filters.type,
      filters.ownershipStatus !== 'OWNED',
      filters.sort && filters.sort !== 'name,asc',
    ]);
  return { additionalCount, activeCount };
}

export function getLiabilityFilterCounts(filters: LiabilityFilters) {
  const additionalCount = countActiveFilters([
    filters.balanceMin,
    filters.balanceMax,
    filters.balanceExact,
    filters.amountCurrencyMode === 'NATIVE',
  ]);
  const activeCount =
    additionalCount + countActiveFilters([filters.search, filters.searchRegex, filters.type]);
  return { additionalCount, activeCount };
}
