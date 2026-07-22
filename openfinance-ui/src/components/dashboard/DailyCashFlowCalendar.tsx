import { useState, useMemo } from 'react';
import { Card, CardContent, CardHeader, CardTitle, CardDescription } from '../ui/Card';
import { Button } from '../ui/Button';
import { ChevronLeft, ChevronRight, Calendar as CalendarIcon } from 'lucide-react';
import { useTranslation } from 'react-i18next';
import { Tooltip, TooltipContent, TooltipProvider, TooltipTrigger } from '../ui/Tooltip';
import { useDailyCashFlow } from '../../hooks/useDashboard';
import { useFormatCurrency } from '@/hooks/useFormatCurrency';
import { DEFAULT_CURRENCY } from '@/utils/currency';
import { subtract } from '@/utils/money';
import { PrivateAmount } from '../ui/PrivateAmount';
import {
  format as formatDate,
  startOfMonth,
  endOfMonth,
  eachDayOfInterval,
  getDay,
  addMonths,
  subMonths,
  isSameMonth,
  isToday,
} from 'date-fns';
import { enUS, fr } from 'date-fns/locale';
import { cn } from '@/lib/utils';
import { Skeleton } from '../ui/Skeleton';

// Fixed pixel height for the bar area inside each day cell.
// This gives CSS percentage heights a concrete reference, which is required
// for `height: X%` to work (a % height only resolves when the parent has
// an explicit height — flex-grow alone is not sufficient).
const BAR_AREA_HEIGHT_PX = 28;

interface DailyCashFlowCalendarProps {
  className?: string;
  baseCurrency?: string;
}

const DailyCashFlowCalendar = ({ className, baseCurrency = DEFAULT_CURRENCY }: DailyCashFlowCalendarProps) => {
  const { t, i18n } = useTranslation('dashboard');
  const [currentDate, setCurrentDate] = useState(new Date());
  const { format } = useFormatCurrency();

  const locale = i18n.language === 'fr' ? fr : enUS;

  const year = currentDate.getFullYear();
  const month = currentDate.getMonth() + 1;

  const { data: dailyData, isLoading, isError } = useDailyCashFlow(year, month);

  const handlePrevMonth = () => setCurrentDate(prev => subMonths(prev, 1));
  const handleNextMonth = () => setCurrentDate(prev => addMonths(prev, 1));
  const handleToday = () => setCurrentDate(new Date());

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

  if (isError) {
    return (
      <Card className={cn('flex flex-col h-full', className)}>
        <CardHeader className="pb-2">
          <CardTitle className="text-sm font-medium text-text-secondary flex items-center gap-2">
            <CalendarIcon className="h-4 w-4" />
            {t('calendar.title')}
          </CardTitle>
        </CardHeader>
        <CardContent className="flex-1 flex items-center justify-center text-error text-sm">
          {t('calendar.loadError')}
        </CardContent>
      </Card>
    );
  }

  return (
    <Card
      className={cn(
        'flex flex-col h-full bg-surface shadow-sm border',
        className
      )}
    >
      <CardHeader className="pb-2 flex flex-row items-center justify-between">
        <div>
          <CardTitle className="text-base font-semibold text-text-primary flex items-center gap-2">
            <CalendarIcon className="h-4 w-4 text-primary" />
            {t('calendar.title')}
          </CardTitle>
          <CardDescription className="text-xs text-text-secondary">
            {t('calendar.subtitle')}
          </CardDescription>
        </div>

        {/* Month navigation + legend */}
        <div className="flex items-center gap-3">
          {/* Income / Expense legend */}
          <div className="hidden sm:flex items-center gap-3 text-[10px] text-text-secondary">
            <span className="flex items-center gap-1">
              <span className="inline-block w-2 h-2 rounded-sm bg-success/80" />
              {t('calendar.income')}
            </span>
            <span className="flex items-center gap-1">
              <span className="inline-block w-2 h-2 rounded-sm bg-error/80" />
              {t('calendar.expense')}
            </span>
          </div>

          {/* Navigation controls */}
          <div className="flex items-center space-x-1">
            <Button
              variant="ghost"
              size="icon"
              className="h-8 w-8 text-text-secondary hover:text-text-primary hover:bg-surface-elevated"
              onClick={handlePrevMonth}
            >
              <ChevronLeft className="h-4 w-4" />
            </Button>
            <span className="text-sm font-medium w-28 text-center text-text-primary capitalize">
              {formatDate(currentDate, 'MMMM yyyy', { locale })}
            </span>
            <Button
              variant="ghost"
              size="icon"
              className="h-8 w-8 text-text-secondary hover:text-text-primary hover:bg-surface-elevated"
              onClick={handleNextMonth}
            >
              <ChevronRight className="h-4 w-4" />
            </Button>
            {!isSameMonth(currentDate, new Date()) && (
              <Button
                variant="ghost"
                size="sm"
                className="h-8 text-xs px-2 text-primary hover:bg-primary/10 ml-1"
                onClick={handleToday}
              >
                {t('calendar.today')}
              </Button>
            )}
          </div>
        </div>
      </CardHeader>

      <CardContent className="flex-1 min-h-0 pt-2 flex flex-col">
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
          {isLoading ? (
            Array.from({ length: 35 }).map((_, i) => (
              <Skeleton
                key={i}
                className="w-full rounded-sm bg-surface-elevated"
                style={{ minHeight: `${BAR_AREA_HEIGHT_PX + 24}px` }}
              />
            ))
          ) : (
            calendarDays.map((date, i) => {
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
                      <div
                        className={cn(
                          'flex flex-col p-1 rounded-sm border transition-colors duration-150 cursor-default overflow-hidden',
                          isToday(date)
                            ? 'border-primary bg-primary/5'
                            : 'border-border bg-surface hover:bg-surface-elevated'
                        )}
                        style={{ minHeight: `${BAR_AREA_HEIGHT_PX + 24}px` }}
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
                      </div>
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
                          <PrivateAmount inline>
                            {format(income, baseCurrency)}
                          </PrivateAmount>
                        </span>

                        <span className="text-text-secondary flex items-center gap-1">
                          <span className="inline-block w-1.5 h-1.5 rounded-sm bg-error/80" />
                          {t('calendar.expense')}:
                        </span>
                        <span className="text-error font-medium text-right">
                          <PrivateAmount inline>
                            {format(expense, baseCurrency)}
                          </PrivateAmount>
                        </span>

                        <span className="text-text-secondary font-medium pt-1 border-t mt-0.5">
                          {t('calendar.net')}:
                        </span>
                        <span
                          className={cn(
                            'font-bold text-right pt-1 border-t mt-0.5',
                            net > 0
                              ? 'text-success'
                              : net < 0
                              ? 'text-error'
                              : 'text-text-secondary'
                          )}
                        >
                          {net > 0 ? '+' : ''}
                          <PrivateAmount inline>
                            {format(net, baseCurrency)}
                          </PrivateAmount>
                        </span>
                      </div>
                    </TooltipContent>
                  </Tooltip>
                </TooltipProvider>
              );
            })
          )}
        </div>
      </CardContent>
    </Card>
  );
};

export default DailyCashFlowCalendar;
