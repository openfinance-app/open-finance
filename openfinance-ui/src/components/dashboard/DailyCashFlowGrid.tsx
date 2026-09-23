import { useMemo } from 'react';
import { useNavigate } from 'react-router';
import { useTranslation } from 'react-i18next';
import {
  format as formatDate,
  startOfMonth,
  endOfMonth,
  eachDayOfInterval,
  getDay,
  isToday,
} from 'date-fns';
import { enUS, fr } from 'date-fns/locale';
import { Tooltip, TooltipContent, TooltipProvider, TooltipTrigger } from '@/components/ui/Tooltip';
import { ConvertedAmount } from '@/components/ui/ConvertedAmount';
import { subtract } from '@/utils/money';
import { cn } from '@/lib/utils';
import type { ICashFlowPeriod } from '@/types/dashboard';

const BAR_AREA_HEIGHT_PX = 28;

interface DailyCashFlowGridProps {
  currentDate: Date;
  dailyData: ICashFlowPeriod[];
  baseCurrency: string;
}

export default function DailyCashFlowGrid({
  currentDate,
  dailyData,
  baseCurrency,
}: DailyCashFlowGridProps) {
  const { t, i18n } = useTranslation('dashboard');
  const navigate = useNavigate();
  const locale = i18n.language.startsWith('fr') ? fr : enUS;

  const calendarDays = useMemo(() => {
    const start = startOfMonth(currentDate);
    const end = endOfMonth(currentDate);

    // Convert JS getDay() (0=Sun … 6=Sat) to Monday-first index (0=Mon … 6=Sun)
    let startDayOfWeek = getDay(start);
    startDayOfWeek = startDayOfWeek === 0 ? 6 : startDayOfWeek - 1;

    const daysInMonth = eachDayOfInterval({ start, end });

    const prefixEmptyDays = Array.from({ length: startDayOfWeek }).map(() => null);

    const totalCells = prefixEmptyDays.length + daysInMonth.length;
    const suffixDaysCount = totalCells % 7 === 0 ? 0 : 7 - (totalCells % 7);
    const suffixEmptyDays = Array.from({ length: suffixDaysCount }).map(() => null);

    return [...prefixEmptyDays, ...daysInMonth, ...suffixEmptyDays];
  }, [currentDate]);

  // Pre-build a lookup map for O(1) day-data access
  const dailyDataMap = useMemo(() => {
    const map = new Map<string, { income: number; expense: number }>();
    if (!dailyData) return map;
    for (const d of dailyData) {
      map.set(d.date, { income: Number(d.income) || 0, expense: Number(d.expense) || 0 });
    }
    return map;
  }, [dailyData]);

  // Maximum value across all days — used to proportionally scale bar heights
  const maxVal = useMemo(() => {
    if (!dailyData || dailyData.length === 0) return 0;
    return Math.max(
      ...dailyData.map(d => Math.max(Number(d.income) || 0, Number(d.expense) || 0)),
      1 // never divide by zero
    );
  }, [dailyData]);

  return (
    <div className="flex min-h-0 flex-1 flex-col">
      {/* Day-of-week header row */}
      <div className="grid grid-cols-7 gap-1 mb-1">
        {['mon', 'tue', 'wed', 'thu', 'fri', 'sat', 'sun'].map(day => (
          <div key={day} className="text-center text-[10px] font-medium text-text-secondary py-1">
            {t(`calendar.days.${day}`)}
          </div>
        ))}
      </div>

      {/* Calendar grid */}
      <div className="grid grid-cols-7 gap-1 flex-1 min-h-0 auto-rows-fr">
        {calendarDays.map((date, i) => {
          if (!date) {
            return (
              <div
                key={`empty-${i}`}
                className="rounded-sm border border-border/20 bg-surface/20"
                style={{ minHeight: `${BAR_AREA_HEIGHT_PX + 24}px` }}
              />
            );
          }

          const dayStr = formatDate(date, 'yyyy-MM-dd', { locale });
          const dayData = dailyDataMap.get(dayStr);

          const income = dayData?.income ?? 0;
          const expense = dayData?.expense ?? 0;
          const net = subtract(income, expense);
          const hasActivity = income > 0 || expense > 0;

          // Scale to BAR_AREA_HEIGHT_PX so bars always render with concrete pixel values
          const incomeBarH = maxVal > 0 ? Math.round((income / maxVal) * BAR_AREA_HEIGHT_PX) : 0;
          const expenseBarH = maxVal > 0 ? Math.round((expense / maxVal) * BAR_AREA_HEIGHT_PX) : 0;

          return (
            <TooltipProvider key={dayStr}>
              <Tooltip>
                <TooltipTrigger asChild>
                  <button
                    type="button"
                    onClick={() =>
                      navigate(
                        `/transactions?dateFrom=${dayStr}&dateTo=${dayStr}&excludeTransfers=true`
                      )
                    }
                    className={cn(
                      'flex flex-col w-full text-left p-1 rounded-sm border transition-colors duration-150 cursor-pointer overflow-hidden',
                      isToday(date)
                        ? 'border-primary bg-primary/5'
                        : 'border-border bg-surface hover:bg-surface-elevated'
                    )}
                    style={{ minHeight: `${BAR_AREA_HEIGHT_PX + 24}px` }}
                    aria-label={t('calendar.viewDayTransactions', { date: dayStr })}
                  >
                    {/* Day number */}
                    <span
                      className={cn(
                        'text-[10px] font-semibold leading-none',
                        isToday(date) ? 'text-primary' : 'text-text-secondary'
                      )}
                    >
                      {formatDate(date, 'd', { locale })}
                    </span>

                    {/* Bar chart area — explicit pixel height so % bars resolve correctly */}
                    <div
                      className="mt-1 flex items-end justify-center gap-[2px] w-full"
                      style={{ height: `${BAR_AREA_HEIGHT_PX}px` }}
                    >
                      {hasActivity ? (
                        <>
                          {/* Income bar (green) */}
                          <div
                            className="flex-1 bg-success/75 rounded-t-[2px] transition-all duration-300"
                            style={{
                              height: `${incomeBarH}px`,
                              minHeight: income > 0 ? '2px' : '0px',
                            }}
                          />
                          {/* Expense bar (red) */}
                          <div
                            className="flex-1 bg-error/75 rounded-t-[2px] transition-all duration-300"
                            style={{
                              height: `${expenseBarH}px`,
                              minHeight: expense > 0 ? '2px' : '0px',
                            }}
                          />
                        </>
                      ) : (
                        /* Subtle no-activity indicator */
                        <div className="w-full flex items-end justify-center h-full pb-[1px]">
                          <div className="w-3/4 h-[2px] rounded-full bg-border-subtle/40" />
                        </div>
                      )}
                    </div>
                  </button>
                </TooltipTrigger>

                <TooltipContent
                  side="top"
                  className="flex flex-col gap-1 p-2 bg-surface-elevated text-text-primary border shadow-lg min-w-[160px]"
                >
                  <div className="font-semibold text-xs border-b pb-1 mb-1">
                    {formatDate(date, 'EEEE, MMMM d, yyyy', { locale })}
                  </div>
                  <div className="grid grid-cols-2 gap-x-4 gap-y-1 text-xs">
                    <span className="text-text-secondary flex items-center gap-1">
                      <span className="inline-block w-1.5 h-1.5 rounded-sm bg-success/80" />
                      {t('calendar.income')}:
                    </span>
                    <span className="text-success font-medium text-right">
                      <ConvertedAmount amount={income} currency={baseCurrency} inline />
                    </span>

                    <span className="text-text-secondary flex items-center gap-1">
                      <span className="inline-block w-1.5 h-1.5 rounded-sm bg-error/80" />
                      {t('calendar.expense')}:
                    </span>
                    <span className="text-error font-medium text-right">
                      <ConvertedAmount amount={expense} currency={baseCurrency} inline />
                    </span>

                    <span className="text-text-secondary font-medium pt-1 border-t mt-0.5">
                      {t('calendar.net')}:
                    </span>
                    <span
                      className={cn(
                        'font-bold text-right pt-1 border-t mt-0.5',
                        net > 0 ? 'text-success' : net < 0 ? 'text-error' : 'text-text-secondary'
                      )}
                    >
                      {net > 0 ? '+' : net < 0 ? '-' : ''}
                      <ConvertedAmount amount={Math.abs(net)} currency={baseCurrency} inline />
                    </span>
                  </div>
                </TooltipContent>
              </Tooltip>
            </TooltipProvider>
          );
        })}
      </div>
    </div>
  );
}
