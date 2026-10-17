import { useState } from 'react';
import { parseISO } from 'date-fns';
import { Calendar, ChevronDown } from 'lucide-react';
import { useTranslation } from 'react-i18next';
import { useDateFormatter } from '@/hooks/useDateFormatter';
import { toLocalISODate } from '@/utils/date';
import {
  dateRangeToDays,
  resolveDashboardPeriod,
  type DateRange,
  type PeriodSelection,
  type PresetPeriod,
} from '@/utils/dashboard-period';
import { Popover, PopoverTrigger, PopoverContent } from '@/components/ui/Popover';
import { DateInput } from '@/components/ui/DateInput';
import { cn } from '@/lib/utils';

export type { DateRange } from '@/utils/dashboard-period';

interface PeriodSelectorProps {
  selection: PeriodSelection;
  onPeriodChange: (selection: PeriodSelection) => void;
  today?: string;
  className?: string;
}

const PRESETS: PresetPeriod[] = ['1D', '7D', '30D', 'MTD', 'YTD', '365D'];

function buttonClass(active: boolean): string {
  return cn(
    'px-3 min-h-9 rounded-md text-sm font-medium transition-colors flex items-center justify-center gap-1.5 whitespace-nowrap hover:bg-surface-elevated',
    active
      ? 'bg-gradient-to-b from-brass-bright to-primary text-primary-foreground font-semibold shadow-[inset_0_1px_0_0_rgb(255_255_255/0.28)]'
      : 'text-text-secondary hover:text-text-primary'
  );
}

/** Rolling and to-date presets plus an exact custom range. */
export function PeriodSelector({
  selection,
  onPeriodChange,
  today = toLocalISODate(new Date()),
  className,
}: PeriodSelectorProps) {
  const { t } = useTranslation('common');
  const { date: formatDate } = useDateFormatter();
  const [customOpen, setCustomOpen] = useState(false);
  const [customRange, setCustomRange] = useState<DateRange>(() =>
    selection.period === 'CUSTOM'
      ? selection.range
      : resolveDashboardPeriod({ period: '30D' }, parseISO(today)).range
  );
  const customActive = selection.period === 'CUSTOM';
  const customLabel = customActive
    ? `${formatDate(selection.range.from)} → ${formatDate(selection.range.to)}`
    : t('dateRange.custom');
  const choosePreset = (period: PresetPeriod): void => {
    setCustomOpen(false);
    onPeriodChange({ period });
  };
  const openCustom = (open: boolean): void => {
    setCustomOpen(open);
    if (open) {
      if (!customActive) onPeriodChange({ period: 'CUSTOM', range: customRange });
    }
  };
  const updateCustom = (range: DateRange): void => {
    setCustomRange(range);
    onPeriodChange({ period: 'CUSTOM', range });
  };

  return (
    <div
      role="group"
      aria-label={t('periodSelector.label')}
      className={cn(
        'flex items-center gap-1 bg-surface border border-border shadow-slot rounded-lg p-1 flex-wrap',
        className
      )}
    >
      {PRESETS.map(period => (
        <button
          key={period}
          type="button"
          onClick={() => choosePreset(period)}
          aria-pressed={selection.period === period}
          className={buttonClass(selection.period === period)}
        >
          {t(`periodSelector.presets.${period}`)}
        </button>
      ))}

      <button
        type="button"
        onClick={() => choosePreset('ALL')}
        aria-pressed={selection.period === 'ALL'}
        className={buttonClass(selection.period === 'ALL')}
      >
        {t('periodSelector.presets.ALL')}
      </button>

      <Popover open={customOpen} onOpenChange={openCustom}>
        <PopoverTrigger asChild>
          <button
            type="button"
            aria-label={customLabel}
            aria-pressed={customActive}
            className={buttonClass(customActive)}
          >
            <Calendar className="h-3.5 w-3.5 shrink-0" />
            <span className="hidden sm:inline">{customLabel}</span>
            <ChevronDown className="h-3 w-3 shrink-0" />
          </button>
        </PopoverTrigger>
        <PopoverContent
          align="start"
          collisionPadding={12}
          sideOffset={8}
          aria-label={t('dateRange.title')}
          className="w-[280px] max-w-[calc(100vw-24px)] rounded-xl p-4 shadow-xl"
        >
          <p className="text-xs font-semibold text-text-secondary uppercase tracking-wide mb-3">
            {t('dateRange.title')}
          </p>
          <div className="flex flex-col gap-3">
            <div className="flex flex-col gap-1">
              <label htmlFor="period-from" className="text-xs text-text-secondary font-medium">
                {t('dateRange.from')}
              </label>
              <DateInput
                id="period-from"
                value={customRange.from}
                max={customRange.to}
                onChange={from => {
                  if (from)
                    updateCustom({ from, to: customRange.to < from ? from : customRange.to });
                }}
              />
            </div>
            <div className="flex flex-col gap-1">
              <label htmlFor="period-to" className="text-xs text-text-secondary font-medium">
                {t('dateRange.to')}
              </label>
              <DateInput
                id="period-to"
                value={customRange.to}
                min={customRange.from}
                onChange={to => {
                  if (to) updateCustom({ from: customRange.from > to ? to : customRange.from, to });
                }}
              />
            </div>
          </div>
          <div className="mt-3 pt-3 border-t border-border text-center text-xs text-text-secondary">
            {t('dateRange.daysSelected', { count: dateRangeToDays(customRange) })}
          </div>
        </PopoverContent>
      </Popover>
    </div>
  );
}

export default PeriodSelector;
