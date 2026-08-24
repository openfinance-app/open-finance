/**
 * FinancialMap – Dashboard card showing a world map of where the user's finances
 * are located.
 *
 * Aggregates value by country from two sources, all client-side:
 *  - Institutions: account balances + linked asset values, grouped by the
 *    institution's country (ISO alpha-2).
 *  - Real estate: each property is attributed to a country by reverse-geocoding
 *    its latitude/longitude offline; when coordinates are missing the country is
 *    inferred from the free-form address string.
 *
 * Highlight dots are sized (by area) relative to the value held in each country,
 * the user's own location (General settings → country) is marked distinctly, and
 * connector lines relate the user's location to each finance location.
 */

import { useMemo, useRef, useState, type MouseEvent } from 'react';
import { Globe2, Building2, Home, Eye, EyeOff } from 'lucide-react';
import {
  ComposableMap,
  Geographies,
  Geography,
  Marker,
  Line,
  ZoomableGroup,
} from 'react-simple-maps';
import { useTranslation } from 'react-i18next';
import { useAccounts } from '@/hooks/useAccounts';
import { useAssets } from '@/hooks/useAssets';
import { useProperties } from '@/hooks/useRealEstate';
import { useUserSettings } from '@/hooks/useUserSettings';
import { useSecondaryConversion } from '@/hooks/useSecondaryConversion';
import { ConvertedAmount } from '../ui/ConvertedAmount';
import { DEFAULT_CURRENCY } from '@/utils/currency';
import { add } from '@/utils/money';
import { countryFlagClass } from '@/utils/countryUtils';
import {
  WORLD_TOPOLOGY,
  countryCentroid,
  countryOfPoint,
  countryFromAddress,
  countryDisplayName,
  alpha2FromNumericId,
} from '@/utils/countryGeo';

interface FinancialMapProps {
  baseCurrency?: string;
}

interface CountryDatum {
  code: string;
  amount: number;
  institutionsAmount: number;
  realEstateAmount: number;
  accountCount: number;
  propertyCount: number;
}

interface Aggregation {
  countries: CountryDatum[];
  maxAbs: number;
  userCountry?: string;
  unmappedPropertyCount: number;
  unmappedPropertyAmount: number;
}

/** Shape of a geography passed to the `<Geographies>` render prop. */
interface GeoItem {
  rsmKey: string;
  id?: string | number;
  properties?: { name?: string };
}

interface TooltipState {
  code: string;
  name: string;
  x: number;
  y: number;
}

const MIN_DOT_RADIUS = 1;
const MAX_DOT_RADIUS = 12;

export default function FinancialMap({ baseCurrency = DEFAULT_CURRENCY }: FinancialMapProps) {
  const { t } = useTranslation('dashboard');
  const { data: accounts, isLoading: accountsLoading, error: accountsError } = useAccounts();
  const { data: assets, isLoading: assetsLoading, error: assetsError } = useAssets();
  const { data: properties, isLoading: propertiesLoading, error: propertiesError } = useProperties();
  const { data: settings } = useUserSettings();
  const {
    convert,
    secondaryCurrency: secCurrency,
    secondaryExchangeRate,
  } = useSecondaryConversion(baseCurrency);

  const [tooltip, setTooltip] = useState<TooltipState | null>(null);
  const [showLegend, setShowLegend] = useState(false);
  const mapContainerRef = useRef<HTMLDivElement>(null);

  // Coordinates relative to the map container (not the viewport): the dashboard
  // card sits inside a react-grid-layout item whose CSS transform would break
  // viewport-based fixed positioning.
  const relativePoint = (e: MouseEvent) => {
    const rect = mapContainerRef.current?.getBoundingClientRect();
    return { x: e.clientX - (rect?.left ?? 0), y: e.clientY - (rect?.top ?? 0) };
  };

  const showTip = (code: string, e: MouseEvent) => {
    const { x, y } = relativePoint(e);
    setTooltip({ code, name: countryDisplayName(code) || code, x, y });
  };

  const showGeoTip = (geo: GeoItem, e: MouseEvent) => {
    const code = geo.id != null ? alpha2FromNumericId(geo.id) : undefined;
    const name = (code ? countryDisplayName(code) : undefined) || geo.properties?.name || '';
    const { x, y } = relativePoint(e);
    setTooltip({ code: code ?? '', name, x, y });
  };

  const aggregation = useMemo<Aggregation>(() => {
    const map = new Map<string, CountryDatum>();

    const bump = (
      code: string,
      value: number,
      kind: 'institution' | 'realEstate',
    ) => {
      const key = code.toUpperCase();
      const existing =
        map.get(key) ??
        {
          code: key,
          amount: 0,
          institutionsAmount: 0,
          realEstateAmount: 0,
          accountCount: 0,
          propertyCount: 0,
        };
      existing.amount = add(existing.amount, value);
      if (kind === 'institution') {
        existing.institutionsAmount = add(existing.institutionsAmount, value);
        existing.accountCount += 1;
      } else {
        existing.realEstateAmount = add(existing.realEstateAmount, value);
        existing.propertyCount += 1;
      }
      map.set(key, existing);
    };

    // Accounts → institution country. Balances already include the value of any
    // linked assets (added server-side), so assets are not aggregated separately
    // here to avoid double counting and inflating the account count.
    if (accounts) {
      for (const account of accounts) {
        const country = account.institution?.country;
        if (!country) continue;
        bump(country, account.balanceInBaseCurrency ?? account.balance, 'institution');
      }
    }

    // Real estate → reverse-geocode coordinates, else infer from the address
    let unmappedPropertyCount = 0;
    let unmappedPropertyAmount = 0;
    if (properties) {
      for (const property of properties) {
        const value = property.valueInBaseCurrency ?? property.currentValue;
        let country: string | undefined;
        if (property.latitude != null && property.longitude != null) {
          country = countryOfPoint(property.longitude, property.latitude);
        }
        if (!country) {
          country = countryFromAddress(property.address);
        }
        if (country) {
          bump(country, value, 'realEstate');
        } else {
          unmappedPropertyCount += 1;
          unmappedPropertyAmount = add(unmappedPropertyAmount, value);
        }
      }
    }

    const countries = Array.from(map.values()).sort(
      (a, b) => Math.abs(b.amount) - Math.abs(a.amount),
    );
    const maxAbs = countries.reduce((max, c) => Math.max(max, Math.abs(c.amount)), 0);

    return {
      countries,
      maxAbs,
      userCountry: settings?.country?.toUpperCase(),
      unmappedPropertyCount,
      unmappedPropertyAmount,
    };
  }, [accounts, assets, properties, settings?.country]);

  const isLoading = accountsLoading || assetsLoading || propertiesLoading;
  const error = accountsError || assetsError || propertiesError;

  /* ── Loading ─────────────────────────────────────────────────────────────── */
  if (isLoading) {
    return (
      <div className="bg-surface rounded-lg p-6 border border-border animate-pulse h-full">
        <div className="h-6 bg-surface-elevated rounded w-48 mb-4" />
        <div className="h-56 bg-surface-elevated rounded mb-4" />
        <div className="space-y-3">
          {[1, 2, 3].map((i) => (
            <div key={i} className="h-8 bg-surface-elevated rounded" />
          ))}
        </div>
      </div>
    );
  }

  /* ── Error ───────────────────────────────────────────────────────────────── */
  if (error) {
    return (
      <div className="bg-surface rounded-lg p-6 border border-red-500/50 h-full">
        <div className="flex items-center gap-2 mb-4">
          <Globe2 className="h-5 w-5 text-red-500" />
          <h3 className="text-lg font-semibold text-text-primary">{t('financialMap.title')}</h3>
        </div>
        <p className="text-sm text-red-500">
          {error instanceof Error ? error.message : t('financialMap.loadError')}
        </p>
      </div>
    );
  }

  const { countries, maxAbs, userCountry, unmappedPropertyCount, unmappedPropertyAmount } =
    aggregation;
  const userCentroid = userCountry ? countryCentroid(userCountry) : null;

  const dotRadius = (amount: number): number => {
    if (maxAbs <= 0) return 0;
    return MIN_DOT_RADIUS + (MAX_DOT_RADIUS - MIN_DOT_RADIUS) * Math.sqrt(Math.abs(amount) / maxAbs);
  };

  const markers = countries
    .map((c) => ({ datum: c, centroid: countryCentroid(c.code) }))
    .filter((m): m is { datum: CountryDatum; centroid: [number, number] } => m.centroid !== null);

  const hasData = countries.length > 0;
  const tooltipDatum = tooltip ? countries.find((c) => c.code === tooltip.code) : undefined;

  // Overall aggregates for the legend (not a per-country breakdown).
  const institutionsTotal = countries.reduce((s, c) => add(s, c.institutionsAmount), 0);
  const realEstateTotal = add(
    countries.reduce((s, c) => add(s, c.realEstateAmount), 0),
    unmappedPropertyAmount,
  );
  const grandTotal = add(institutionsTotal, realEstateTotal);
  const totalAccounts = countries.reduce((s, c) => s + c.accountCount, 0);
  const totalProperties =
    countries.reduce((s, c) => s + c.propertyCount, 0) + unmappedPropertyCount;

  /* ── Empty ───────────────────────────────────────────────────────────────── */
  if (!hasData && !userCentroid) {
    return (
      <div className="bg-surface rounded-lg p-6 border border-border h-full flex flex-col">
        <div className="flex items-center gap-2 mb-4">
          <Globe2 className="h-5 w-5 text-primary" />
          <h3 className="text-lg font-semibold text-text-primary">{t('financialMap.title')}</h3>
        </div>
        <p className="text-sm text-text-secondary">{t('financialMap.empty')}</p>
      </div>
    );
  }

  /* ── Render ──────────────────────────────────────────────────────────────── */
  return (
    <div className="bg-surface rounded-lg p-6 border border-border hover:border-border/70 transition-colors h-full flex flex-col">
      {/* Header */}
      <div className="flex items-center gap-2 mb-4">
        <Globe2 className="h-5 w-5 text-primary" />
        <h3 className="text-lg font-semibold text-text-primary">{t('financialMap.title')}</h3>
        {hasData && (
          <button
            type="button"
            onClick={() => setShowLegend((v) => !v)}
            aria-pressed={showLegend}
            title={t('financialMap.toggleLegend')}
            className="inline-flex items-center justify-center rounded-md p-1 text-text-secondary hover:text-text-primary hover:bg-surface-elevated transition-colors"
          >
            {showLegend ? <Eye className="h-4 w-4" /> : <EyeOff className="h-4 w-4" />}
            <span className="sr-only">{t('financialMap.toggleLegend')}</span>
          </button>
        )}
      </div>

      {/* Map */}
      <div
        ref={mapContainerRef}
        className="relative flex-1 min-h-0"
        onMouseLeave={() => setTooltip(null)}
      >
        <ComposableMap
          projection="geoEqualEarth"
          projectionConfig={{ scale: 150 }}
          width={800}
          height={380}
          style={{ width: '100%', height: 'auto' }}
        >
          <ZoomableGroup center={[0, 8]} zoom={1} minZoom={1} maxZoom={8}>
            <Geographies geography={WORLD_TOPOLOGY}>
              {({ geographies }: { geographies: GeoItem[] }) =>
                geographies.map((geo) => (
                  <Geography
                    key={geo.rsmKey}
                    geography={geo}
                    onMouseEnter={(e: MouseEvent) => showGeoTip(geo, e)}
                    onMouseMove={(e: MouseEvent) => showGeoTip(geo, e)}
                    onMouseLeave={() => setTooltip(null)}
                    style={{
                      default: {
                        fill: 'var(--color-surface-elevated)',
                        stroke: 'var(--color-border)',
                        strokeWidth: 0.4,
                        outline: 'none',
                      },
                      hover: {
                        fill: 'var(--color-border)',
                        stroke: 'var(--color-border)',
                        strokeWidth: 0.4,
                        outline: 'none',
                      },
                      pressed: { fill: 'var(--color-border)', outline: 'none' },
                    }}
                  />
                ))
              }
            </Geographies>

            {/* Relation lines: user location → finance locations */}
            {userCentroid &&
              markers
                .filter((m) => m.datum.code !== userCountry)
                .map((m) => (
                  <Line
                    key={`line-${m.datum.code}`}
                    from={userCentroid}
                    to={m.centroid}
                    stroke="var(--color-primary)"
                    strokeWidth={0.8}
                    strokeOpacity={0.35}
                    strokeLinecap="round"
                  />
                ))}

            {/* Highlight dots sized by amount — glowing, pulsing markers */}
            {markers.map((m, idx) => {
              const r = dotRadius(m.datum.amount);
              const core = Math.max(1.5, r * 0.6);
              // Stagger each pulse so the map feels alive rather than synchronized.
              const begin = `${(idx % 6) * 0.35}s`;
              return (
                <Marker key={`dot-${m.datum.code}`} coordinates={m.centroid}>
                  <g
                    style={{ cursor: 'pointer' }}
                    onMouseEnter={(e) => showTip(m.datum.code, e)}
                    onMouseMove={(e) => showTip(m.datum.code, e)}
                    onMouseLeave={() => setTooltip(null)}
                  >
                    {/* Expanding, fading pulse ring */}
                    <circle r={core} fill="var(--color-primary)" pointerEvents="none">
                      <animate
                        attributeName="r"
                        values={`${core};${r * 1.8}`}
                        dur="2.4s"
                        begin={begin}
                        repeatCount="indefinite"
                      />
                      <animate
                        attributeName="opacity"
                        values="0.5;0"
                        dur="2.4s"
                        begin={begin}
                        repeatCount="indefinite"
                      />
                    </circle>
                    {/* Glowing core dot */}
                    <circle
                      r={core}
                      fill="var(--color-primary)"
                      stroke="var(--color-surface)"
                      strokeWidth={0.5}
                      style={{ filter: 'drop-shadow(0 0 3px var(--color-primary))' }}
                    />
                  </g>
                </Marker>
              );
            })}

            {/* User location marker */}
            {userCentroid && (
              <Marker coordinates={userCentroid}>
                <g
                  transform="translate(-7, -14)"
                  onMouseEnter={(e) => userCountry && showTip(userCountry, e)}
                  onMouseLeave={() => setTooltip(null)}
                >
                  <path
                    d="M7 0C3.13 0 0 3.13 0 7c0 5.25 7 13 7 13s7-7.75 7-13c0-3.87-3.13-7-7-7Z"
                    fill="var(--color-primary)"
                    stroke="var(--color-surface)"
                    strokeWidth={1}
                  />
                  <circle cx={7} cy={7} r={2.5} fill="var(--color-surface)" />
                </g>
              </Marker>
            )}
          </ZoomableGroup>
        </ComposableMap>

        {/* Tooltip */}
        {tooltip && (
          <div
            className="absolute z-50 pointer-events-none bg-surface-elevated border border-border rounded-md shadow-lg px-3 py-2 text-xs"
            style={{ left: tooltip.x + 12, top: tooltip.y + 12 }}
          >
            <div className="flex items-center gap-2">
              {tooltip.code && <span className={countryFlagClass(tooltip.code)} />}
              <span className="font-semibold text-text-primary">{tooltip.name}</span>
              {tooltip.code && tooltip.code === userCountry && (
                <span className="text-[10px] text-primary">{t('financialMap.you')}</span>
              )}
            </div>
            {tooltipDatum && (
              <>
                <div className="font-mono text-text-primary mt-1">
                  <ConvertedAmount
                    amount={tooltipDatum.amount}
                    currency={baseCurrency}
                    isConverted={false}
                    secondaryAmount={convert(tooltipDatum.amount)}
                    secondaryCurrency={secCurrency}
                    secondaryExchangeRate={secondaryExchangeRate}
                    inline
                  />
                </div>
                {tooltipDatum.accountCount > 0 && (
                  <div className="text-text-secondary mt-0.5">
                    {t('financialMap.institutionsLine', { count: tooltipDatum.accountCount })}
                  </div>
                )}
                {tooltipDatum.propertyCount > 0 && (
                  <div className="text-text-secondary">
                    {t('financialMap.realEstateLine', { count: tooltipDatum.propertyCount })}
                  </div>
                )}
              </>
            )}
          </div>
        )}
      </div>

      {/* Legend — overall aggregates */}
      {hasData && showLegend && (
        <div className="mt-4 space-y-2 w-full max-w-xs">
          <div className="flex items-center justify-between gap-2">
            <span className="flex items-center gap-1.5 text-sm text-text-secondary">
              <Building2 className="h-3.5 w-3.5" />
              {t('financialMap.institutions')}
              {totalAccounts > 0 && (
                <span className="text-xs text-text-muted">({totalAccounts})</span>
              )}
            </span>
            <span className="text-sm font-mono text-text-primary">
              <ConvertedAmount className='z-50 relative'
                amount={institutionsTotal}
                currency={baseCurrency}
                isConverted={false}
                secondaryAmount={convert(institutionsTotal)}
                secondaryCurrency={secCurrency}
                secondaryExchangeRate={secondaryExchangeRate}
                inline
              />
            </span>
          </div>
          <div className="flex items-center justify-between gap-2">
            <span className="flex items-center gap-1.5 text-sm text-text-secondary">
              <Home className="h-3.5 w-3.5" />
              {t('financialMap.realEstate')}
              {totalProperties > 0 && (
                <span className="text-xs text-text-muted">({totalProperties})</span>
              )}
            </span>
            <span className="text-sm font-mono text-text-primary">
              <ConvertedAmount className='z-50 relative'
                amount={realEstateTotal}
                currency={baseCurrency}
                isConverted={false}
                secondaryAmount={convert(realEstateTotal)}
                secondaryCurrency={secCurrency}
                secondaryExchangeRate={secondaryExchangeRate}
                inline
              />
            </span>
          </div>
          <div className="flex items-center justify-between gap-2 border-t border-border pt-2">
            <span className="text-sm font-medium text-text-primary">
              {t('financialMap.total')}
            </span>
            <span className="text-sm font-mono font-semibold text-text-primary">
              <ConvertedAmount className='z-50 relative'
                amount={grandTotal}
                currency={baseCurrency}
                isConverted={false}
                secondaryAmount={convert(grandTotal)}
                secondaryCurrency={secCurrency}
                secondaryExchangeRate={secondaryExchangeRate}
                inline
              />
            </span>
          </div>
          {unmappedPropertyCount > 0 && (
            <p className="text-xs text-text-muted pt-1">
              {t('financialMap.unmapped', { count: unmappedPropertyCount })}
            </p>
          )}
        </div>
      )}
    </div>
  );
}
