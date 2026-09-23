import { useState } from 'react';
import { addMonths, addYears, format, isSameMonth, isSameYear, startOfMonth } from 'date-fns';
import { enUS, fr } from 'date-fns/locale';
import { Calendar, ChevronLeft, ChevronRight } from 'lucide-react';
import { useTranslation } from 'react-i18next';
import { Card, CardContent, CardHeader, CardTitle, CardDescription } from '@/components/ui/Card';
import { Button } from '@/components/ui/Button';
import { Skeleton } from '@/components/ui/Skeleton';
import DailyCashFlowGrid from '@/components/dashboard/DailyCashFlowGrid';
import CashFlowPeriodView from '@/components/dashboard/CashFlowPeriodView';
import { useCashFlowHistory } from '@/hooks/useDashboard';
import type { CashFlowGranularity } from '@/types/dashboard';
import { DEFAULT_CURRENCY } from '@/utils/currency';
import { cn } from '@/lib/utils';

interface CashFlowCalendarProps {
  className?: string;
  baseCurrency?: string;
}

export default function CashFlowCalendar({
  className,
  baseCurrency = DEFAULT_CURRENCY,
}: CashFlowCalendarProps) {
  const { t, i18n } = useTranslation('dashboard');
  const [currentDate, setCurrentDate] = useState(() => startOfMonth(new Date()));
  const [granularity, setGranularity] = useState<CashFlowGranularity>('DAY');
  const year = currentDate.getFullYear();
  const {
    data = [],
    isLoading,
    isError,
    refetch,
  } = useCashFlowHistory(granularity, year, currentDate.getMonth() + 1);
  const locale = i18n.language.startsWith('fr') ? fr : enUS;
  const periodLabel =
    granularity === 'DAY'
      ? format(currentDate, 'MMMM yyyy', { locale })
      : granularity === 'MONTH'
        ? String(year)
        : `${year - 9}–${year}`;
  const isCurrentPeriod =
    granularity === 'DAY'
      ? isSameMonth(currentDate, new Date())
      : isSameYear(currentDate, new Date());

  function navigatePeriod(direction: number): void {
    setCurrentDate(date =>
      granularity === 'DAY'
        ? addMonths(date, direction)
        : addYears(date, direction * (granularity === 'YEAR' ? 10 : 1))
    );
  }

  return (
    <Card className={cn('flex h-full min-h-0 flex-col bg-surface shadow-sm border', className)}>
      <CardHeader className="flex flex-row flex-wrap items-center justify-between gap-2 pb-2">
        <div>
          <CardTitle className="flex items-center gap-2 text-base font-semibold text-text-primary">
            <Calendar className="h-4 w-4 text-primary" />
            {t('calendar.title')}
          </CardTitle>
          <CardDescription className="text-xs text-text-secondary">
            {t(`calendar.subtitles.${granularity}`)}
          </CardDescription>
        </div>
        <div className="flex flex-wrap items-center gap-2">
          <select
            aria-label={t('calendar.groupBy')}
            value={granularity}
            onChange={event => setGranularity(event.target.value as CashFlowGranularity)}
            className="h-8 rounded-lg border border-border bg-surface px-2 text-sm text-text-primary focus-visible:outline-primary"
          >
            {(['DAY', 'MONTH', 'YEAR'] as const).map(value => (
              <option key={value} value={value}>
                {t(`calendar.granularity.${value}`)}
              </option>
            ))}
          </select>
          <div className="flex items-center gap-1">
            <Button
              variant="ghost"
              size="icon"
              className="h-8 w-8 text-text-secondary"
              aria-label={t(`calendar.previous.${granularity}`)}
              disabled={year <= (granularity === 'YEAR' ? 19 : 1)}
              onClick={() => navigatePeriod(-1)}
            >
              <ChevronLeft className="h-4 w-4" />
            </Button>
            <span
              aria-live="polite"
              className={cn(
                'text-center text-sm font-medium capitalize text-text-primary',
                granularity === 'DAY' ? 'min-w-28' : 'min-w-20'
              )}
            >
              {periodLabel}
            </span>
            <Button
              variant="ghost"
              size="icon"
              className="h-8 w-8 text-text-secondary"
              aria-label={t(`calendar.next.${granularity}`)}
              disabled={year >= (granularity === 'YEAR' ? 9990 : 9999)}
              onClick={() => navigatePeriod(1)}
            >
              <ChevronRight className="h-4 w-4" />
            </Button>
            {!isCurrentPeriod && (
              <Button
                variant="ghost"
                size="sm"
                className="h-8 px-2 text-xs text-primary"
                onClick={() => setCurrentDate(startOfMonth(new Date()))}
              >
                {t('calendar.today')}
              </Button>
            )}
          </div>
        </div>
      </CardHeader>
      <CardContent className="@container flex min-h-0 flex-1 flex-col overflow-auto pt-2">
        <div className="mb-2 flex flex-wrap items-center gap-3 text-[10px] text-text-secondary">
          <span className="flex items-center gap-1">
            <span className="h-2 w-2 rounded-sm bg-success/80" />
            {t('calendar.income')}
          </span>
          <span className="flex items-center gap-1">
            <span className="h-2 w-2 rounded-sm bg-error/80" />
            {t('calendar.expense')}
          </span>
          {granularity !== 'DAY' && (
            <span className="flex items-center gap-1">
              <span className="h-0.5 w-3 bg-primary" />
              {t('calendar.netTrend')}
            </span>
          )}
        </div>
        {isError ? (
          <div
            role="alert"
            className="flex flex-1 flex-col items-center justify-center gap-2 text-sm text-error"
          >
            {t('calendar.loadError')}
            <Button variant="outline" size="sm" onClick={() => void refetch()}>
              {t('calendar.retry')}
            </Button>
          </div>
        ) : isLoading ? (
          <div
            role="status"
            aria-label={t('calendar.loading')}
            className="grid flex-1 grid-cols-7 gap-1"
          >
            {Array.from({ length: 35 }, (_, index) => (
              <Skeleton key={index} className="min-h-12 rounded-sm" />
            ))}
          </div>
        ) : granularity === 'DAY' ? (
          <DailyCashFlowGrid
            currentDate={currentDate}
            dailyData={data}
            baseCurrency={baseCurrency}
          />
        ) : (
          <CashFlowPeriodView data={data} granularity={granularity} baseCurrency={baseCurrency} />
        )}
      </CardContent>
    </Card>
  );
}
