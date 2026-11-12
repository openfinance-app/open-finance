import { useState } from 'react';
import { Coins, Gem, Landmark, Layers, Package, TrendingUp } from 'lucide-react';
import type { LucideIcon } from 'lucide-react';
import { cn } from '@/lib/utils';
import type { Asset } from '@/types/asset';
import { useAssetLogo } from '@/hooks/useAssetLogo';
import type { AssetLogoIdentity } from '@/hooks/useAssetLogo';

interface AssetLogoProps {
  asset: AssetLogoIdentity;
}

const TYPE_ICONS: Partial<Record<Asset['type'], LucideIcon>> = {
  STOCK: TrendingUp,
  CRYPTO: Coins,
  ETF: Layers,
  MUTUAL_FUND: Layers,
  BOND: Landmark,
  COMMODITY: Gem,
};

/** Decorative identity next to the asset name, with a stable footprint on load/error. */
export function AssetLogo({ asset }: AssetLogoProps) {
  const { data } = useAssetLogo(asset);
  const source = data ?? null;
  const [failedSource, setFailedSource] = useState<string | null>(null);
  const showLogo = source !== null && source !== failedSource;
  const Icon = TYPE_ICONS[asset.type] ?? Package;

  return (
    <span
      aria-hidden="true"
      className={cn(
        'flex size-8 shrink-0 items-center justify-center overflow-hidden rounded-full',
        showLogo
          ? 'border border-border bg-white'
          : 'border border-border bg-surface-elevated text-text-secondary'
      )}
    >
      {showLogo ? (
        <img
          src={source}
          alt=""
          width={32}
          height={32}
          loading="lazy"
          decoding="async"
          className="size-full object-contain"
          onError={() => setFailedSource(source)}
        />
      ) : (
        <Icon className="size-4" />
      )}
    </span>
  );
}
