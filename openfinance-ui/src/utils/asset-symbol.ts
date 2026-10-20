import type { Asset } from '@/types/asset';

/** Read the held coin from a crypto market symbol, independently of its quote currency. */
export function getCryptoCurrency(asset: Pick<Asset, 'type' | 'symbol'>): string | null {
  if (asset.type !== 'CRYPTO') return null;
  const match = asset.symbol
    ?.trim()
    .toUpperCase()
    .match(/^([A-Z0-9]+)(?:-([A-Z0-9]+))?$/);
  return match?.[1] || null;
}
