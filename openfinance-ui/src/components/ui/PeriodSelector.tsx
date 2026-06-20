/* eslint-disable react-refresh/only-export-components */
/**
 * PeriodSelector Component
 *
 * Provides preset period buttons (1D, 7D, 1M, YTD, 1Y, ALL) plus a
 * "Custom" mode that reveals a compact from/to date-range picker.
 *
 * Design principles:
 *  - Single row — presets + custom toggle sit together, no wrapping on desktop
 *  - Date inputs appear inline to the right of the presets when Custom is active
 *  - Accessible: each button has aria-pressed; date inputs have visible labels
 *  - Dark-theme consistent with the rest of the dashboard
 */
import { useState, useRef, useEffect } from 'react';
import { Calendar, ChevronDown } from 'lucide-react';
import { useTranslation } from 'react-i18next';
import { cn } from '@/lib/utils';

// ─── Types ────────────────────────────────────────────────────────────────────

export type Period = '1D' | '7D' | '1M' | 'YTD' | '1Y' | 'ALL' | 'CUSTOM';

export interface DateRange {
  from: string; // ISO date string YYYY-MM-DD
  to: string;   // ISO date string YYYY-MM-DD
}

interface PeriodOption {
  label: string;
  value: Exclude<Period, 'CUSTOM'>;
  days: number | null;
}

// ─── Helpers ──────────────────────────────────────────────────────────────────

function getYTDDays(): number {
  const now = new Date();
  const startOfYear = new Date(now.getFullYear(), 0, 1);
  return Math.ceil((now.getTime() - startOfYear.getTime()) / (1000 * 60 * 60 * 24));
}

/** Format today / start-of-year as ISO date strings for default custom range */
function toISODate(d: Date): string {
  return d.toISOString().split('T')[0];
}

function defaultCustomRange(): DateRange {
  const to = new Date();
  const from = new Date(to.getFullYear(), to.getMonth() - 1, to.getDate());
  return { from: toISODate(from), to: toISODate(to) };
}

/** Number of days between two ISO date strings (inclusive) */
export function dateRangeToDays(range: DateRange): number {
  const from = new Date(range.from).getTime();
  const to   = new Date(range.to).getTime();
  return Math.max(1, Math.ceil((to - from) / (1000 * 60 * 60 * 24)) + 1);
}

// ─── Preset options ───────────────────────────────────────────────────────────

const PRESET_OPTIONS: PeriodOption[] = [
  { label: '1D',  value: '1D',  days: 1   },
  { label: '7D',  value: '7D',  days: 7   },
  { label: '1M',  value: '1M',  days: 30  },
  { label: 'YTD', value: 'YTD', days: getYTDDays() },
  { label: '1Y',  value: '1Y',  days: 365 },
  { label: 'ALL', value: 'ALL', days: null },
];

// ─── Props ────────────────────────────────────────────────────────────────────

export interface PeriodSelectorProps {
  /** Currently selected period */
  selectedPeriod: Period;
  /** Active custom date range, used for initialization */
  activeDateRange?: DateRange;
  /** Callback when a preset period changes */
  onPeriodChange: (period: Period, days: number | null, dateRange?: DateRange) => void;
  /** Optional className for the wrapper */
  className?: string;
}

// ─── Component ────────────────────────────────────────────────────────────────

export function PeriodSelector({ selectedPeriod, activeDateRange, onPeriodChange, className }: PeriodSelectorProps) {
  const { t, i18n } = useTranslation('common');
  const [customRange, setCustomRange] = useState<DateRange>(() => activeDateRange ?? defaultCustomRange());
  const [customOpen, setCustomOpen] = useState(false);
  const popoverRef = useRef<HTMLDivElement>(null);

  const formatCustomLabelDate = (isoDate: string): string => {
    const date = new Date(`${isoDate}T00:00:00`);
    if (Number.isNaN(date.getTime())) {
      return isoDate;
    }

    return new Intl.DateTimeFormat(i18n.language || undefined, {
      year: 'numeric',
      month: '2-digit',
      day: '2-digit',
    }).format(date);
  };

  // Close popover on outside click
  useEffect(() => {
    if (!customOpen) return;
    const handler = (e: MouseEvent) => {
      if (popoverRef.current && !popoverRef.current.contains(e.target as Node)) {
        setCustomOpen(false);
      }
    };
    document.addEventListener('mousedown', handler);
    return () => document.removeEventListener('mousedown', handler);
  }, [customOpen]);

  const isCustomActive = selectedPeriod === 'CUSTOM';

  const handlePreset = (opt: PeriodOption) => {
    setCustomOpen(false);
    onPeriodChange(opt.value, opt.days);
  };

  const handleCustomToggle = () => {
    const next = !customOpen;
    setCustomOpen(next);
    if (next && !isCustomActive) {
      // Immediately activate custom with current range
      const range = customRange;
      onPeriodChange('CUSTOM', dateRangeToDays(range), range);
    }
  };

  const handleFromChange = (value: string) => {
    if (!value) return;
    const next: DateRange = { from: value, to: customRange.to < value ? value : customRange.to };
    setCustomRange(next);
    onPeriodChange('CUSTOM', dateRangeToDays(next), next);
  };

  const handleToChange = (value: string) => {
    if (!value) return;
    const next: DateRange = { from: customRange.from > value ? value : customRange.from, to: value };
    setCustomRange(next);
    onPeriodChange('CUSTOM', dateRangeToDays(next), next);
  };

  // Label shown on the custom button
  const customLabel = isCustomActive
    ? `${formatCustomLabelDate(customRange.from)} → ${formatCustomLabelDate(customRange.to)}`
    : t('dateRange.custom');

  return (
    <div className={cn('flex items-center gap-1 bg-surface rounded-lg p-1 flex-wrap', className)}>

      {/* ── Preset buttons ── */}
      {PRESET_OPTIONS.map((opt) => {
        const isActive = opt.value === selectedPeriod;
        const translatedLabel = t(`periodSelector.presets.${opt.value}`, { defaultValue: opt.label });
        
        return (
          <button
            key={opt.value}
            onClick={() => handlePreset(opt)}
            aria-pressed={isActive}
            className={cn(
              'px-3 min-h-[36px] min-w-[2.5rem] rounded-md text-sm font-medium transition-all duration-150 flex items-center justify-center whitespace-nowrap',
              'hover:bg-surface-elevated',
              isActive
                ? 'bg-primary text-background font-semibold shadow-sm'
                : 'text-text-secondary hover:text-text-primary',
            )}
          >
            {translatedLabel}
          </button>
        );
      })}

      {/* ── Divider ── */}
      <div className="w-px h-5 bg-border mx-1 self-center" />

      {/* ── Custom button + popover ── */}
      <div className="relative" ref={popoverRef}>
        <button
          onClick={handleCustomToggle}
          aria-pressed={isCustomActive}
          className={cn(
            'px-3 min-h-[36px] rounded-md text-sm font-medium transition-all duration-150 flex items-center gap-1.5',
            'hover:bg-surface-elevated',
            isCustomActive
              ? 'bg-primary text-background font-semibold shadow-sm'
              : 'text-text-secondary hover:text-text-primary',
          )}
        >
          <Calendar className="h-3.5 w-3.5 shrink-0" />
          <span className="whitespace-nowrap">{customLabel}</span>
          <ChevronDown
            className={cn('h-3 w-3 shrink-0 transition-transform', customOpen && 'rotate-180')}
          />
        </button>

        {/* ── Date range popover ── */}
        {customOpen && (
          <div
            className={cn(
              'absolute top-full mt-2 z-50',
              'bg-surface border border-border rounded-xl shadow-xl p-4',
              'animate-in fade-in slide-in-from-top-2 duration-150',
              // Align right on large screens, left on small
              'right-0 sm:right-auto sm:left-0',
              'min-w-[280px]',
            )}
          >
            <p className="text-xs font-semibold text-text-secondary uppercase tracking-wide mb-3">
              {t('dateRange.title')}
            </p>

            <div className="flex flex-col gap-3">
              {/* From */}
              <div className="flex flex-col gap-1">
                <label className="text-xs text-text-secondary font-medium">{t('dateRange.from')}</label>
                <input
                  type="date"
                  value={customRange.from}
                  max={customRange.to}
                  onChange={(e) => handleFromChange(e.target.value)}
                  className={cn(
                    'w-full bg-surface-elevated border border-border rounded-lg px-3 py-2',
                    'text-sm text-text-primary',
                    'focus:outline-none focus:ring-2 focus:ring-primary/50 focus:border-primary',
                    '[color-scheme:dark]',
                  )}
                />
              </div>

              {/* To */}
              <div className="flex flex-col gap-1">
                <label className="text-xs text-text-secondary font-medium">{t('dateRange.to')}</label>
                <input
                  type="date"
                  value={customRange.to}
                  min={customRange.from}
                  max={toISODate(new Date())}
                  onChange={(e) => handleToChange(e.target.value)}
                  className={cn(
                    'w-full bg-surface-elevated border border-border rounded-lg px-3 py-2',
                    'text-sm text-text-primary',
                    'focus:outline-none focus:ring-2 focus:ring-primary/50 focus:border-primary',
                    '[color-scheme:dark]',
                  )}
                />
              </div>
            </div>

            {/* Range summary */}
            <div className="mt-3 pt-3 border-t border-border text-center">
              <span className="text-xs text-text-secondary">
                {t('dateRange.daysSelected', { count: dateRangeToDays(customRange) })}
              </span>
            </div>
          </div>
        )}
      </div>
    </div>
  );
}

export default PeriodSelector;
