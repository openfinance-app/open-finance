/**
 * InsightsCard component
 * TASK-11.4.5: Display AI-Powered Insights in Dashboard
 * 
 * Displays top 3 AI-powered insights with:
 * - Priority badges (HIGH/MEDIUM/LOW)
 * - Insight icons based on type
 * - Dismiss functionality
 * - Generate new insights button
 * - Empty, loading, and error states
 */
import { useState } from 'react';
import { DISMISS_ANIMATION_DELAY_MS } from '@/constants/timing';
import {
  AlertTriangle,
  AlertCircle,
  PiggyBank,
  TrendingDown,
  TrendingUp,
  CreditCard,
  Target,
  Calculator,
  Trophy,
  Lightbulb,
  X,
  Sparkles,
  RefreshCw,
  Globe,
  Receipt,
  FileText,
} from 'lucide-react';
import { useTopInsights, useGenerateInsights, useDismissInsight } from '@/hooks/useInsights';
import { Card, CardHeader, CardTitle, CardContent } from '@/components/ui/Card';
import { Badge } from '@/components/ui/Badge';
import { Button } from '@/components/ui/Button';
import { HelpTooltip } from '@/components/ui/HelpTooltip';
import { Dialog, DialogContent, DialogHeader, DialogTitle } from '@/components/ui/Dialog';
import { InsightType, InsightPriority, type Insight } from '@/types/insight';
import { formatDistanceToNow } from 'date-fns';
import { fr, enUS } from 'date-fns/locale';
import { useTranslation } from 'react-i18next';

/**
 * Get icon component for insight type
 */
const getInsightIcon = (type: InsightType) => {
  switch (type) {
    case InsightType.SPENDING_ANOMALY:
      return <AlertTriangle className="h-5 w-5 text-primary" />;
    case InsightType.BUDGET_WARNING:
      return <AlertCircle className="h-5 w-5 text-primary" />;
    case InsightType.SAVINGS_OPPORTUNITY:
      return <PiggyBank className="h-5 w-5 text-primary" />;
    case InsightType.CASH_FLOW_WARNING:
      return <TrendingDown className="h-5 w-5 text-primary" />;
    case InsightType.INVESTMENT_SUGGESTION:
      return <TrendingUp className="h-5 w-5 text-primary" />;
    case InsightType.DEBT_ALERT:
      return <CreditCard className="h-5 w-5 text-primary" />;
    case InsightType.BUDGET_RECOMMENDATION:
      return <Target className="h-5 w-5 text-primary" />;
    case InsightType.TAX_OPTIMIZATION:
      return <Calculator className="h-5 w-5 text-primary" />;
    case InsightType.GOAL_PROGRESS:
      return <Trophy className="h-5 w-5 text-primary" />;
    case InsightType.GENERAL_TIP:
      return <Lightbulb className="h-5 w-5 text-primary" />;
    case InsightType.REGION_COMPARISON:
      return <Globe className="h-5 w-5 text-primary" />;
    case InsightType.TAX_OBLIGATION:
      return <FileText className="h-5 w-5 text-primary" />;
    case InsightType.RECURRING_BILLING:
      return <Receipt className="h-5 w-5 text-primary" />;
    default:
      return <Lightbulb className="h-5 w-5 text-primary" />;
  }
};

/**
 * Get badge variant for priority
 */
const getPriorityVariant = (priority: InsightPriority): 'error' | 'warning' | 'info' => {
  switch (priority) {
    case InsightPriority.HIGH:
      return 'error';
    case InsightPriority.MEDIUM:
      return 'warning';
    case InsightPriority.LOW:
      return 'info';
    default:
      return 'info';
  }
};

/**
 * Individual insight item component
 */
interface InsightItemProps {
  insight: Insight;
  onDismiss: (id: number) => void;
  onClick: () => void;
  isDismissing: boolean;
  t: (key: string) => string;
}

function InsightItem({ insight, onDismiss, onClick, isDismissing, t }: InsightItemProps) {
  const [isExiting, setIsExiting] = useState(false);
  const { i18n } = useTranslation('dashboard');
  const dateLocale = i18n.language === 'fr' ? fr : enUS;

  const handleDismiss = (e: React.MouseEvent) => {
    e.stopPropagation();
    setIsExiting(true);
    // Wait for animation before calling onDismiss
    setTimeout(() => {
      onDismiss(insight.id);
    }, DISMISS_ANIMATION_DELAY_MS);
  };

  return (
    <div
      onClick={onClick}
      className={`
        group relative py-3 px-4 rounded-lg border border-border bg-surface-elevated 
        hover:bg-surface-elevated/70 transition-all duration-300 cursor-pointer
        ${isExiting ? 'opacity-0 scale-95 -translate-x-4' : 'opacity-100 scale-100 translate-x-0'}
      `}
    >
      {/* Dismiss button */}
      <button
        onClick={handleDismiss}
        disabled={isDismissing}
        className="absolute top-2 right-2 p-1.5 rounded-md opacity-0 group-hover:opacity-100 
          hover:bg-surface transition-all duration-200 disabled:opacity-50 z-10"
        title={t('insightsCard.dismiss')}
      >
        <X className="h-4 w-4 text-text-secondary hover:text-text-primary" />
      </button>

      <div className="flex items-start gap-3 pr-6">
        {/* Icon */}
        <div className="flex-shrink-0 mt-0.5">
          {getInsightIcon(insight.type)}
        </div>

        {/* Content */}
        <div className="flex-1 min-w-0">
          {/* Title and Priority Badge */}
          <div className="flex items-start gap-2 mb-1">
            <h4 className="text-sm font-semibold text-text-primary leading-tight flex-1">
              {insight.title}
            </h4>
            <Badge variant={getPriorityVariant(insight.priority)} size="sm" className="shrink-0 uppercase">
              {insight.priority ? t(`insightsCard.priority.${insight.priority.toLowerCase()}`) : insight.priority}
            </Badge>
          </div>

          {/* Timestamp */}
          <p className="text-xs text-text-muted">
            {formatDistanceToNow(new Date(insight.createdAt), { addSuffix: true, locale: dateLocale })}
          </p>
        </div>
      </div>
    </div>
  );
}

/**
 * InsightsCard component
 */
export default function InsightsCard() {
  const { t, i18n } = useTranslation('dashboard');
  const dateLocale = i18n.language === 'fr' ? fr : enUS;
  const { data: insights, isLoading, error, refetch } = useTopInsights(3);
  const generateInsights = useGenerateInsights();
  const dismissInsight = useDismissInsight();
  const [selectedInsight, setSelectedInsight] = useState<Insight | null>(null);

  const handleGenerate = async () => {
    try {
      await generateInsights.mutateAsync();
    } catch (error) {
      console.error('Failed to generate insights:', error);
    }
  };

  const handleDismiss = async (id: number) => {
    try {
      await dismissInsight.mutateAsync(id);
    } catch (error) {
      console.error('Failed to dismiss insight:', error);
    }
  };

  const handleRetry = () => {
    refetch();
  };

  // Loading state
  if (isLoading) {
    return (
      <Card className="h-full flex flex-col">
        <CardHeader>
          <div className="flex items-center justify-between">
            <CardTitle className="flex items-center gap-2">
              <Sparkles className="h-5 w-5 text-primary" />
              {t('insightsCard.title')}
            </CardTitle>
          </div>
        </CardHeader>
        <CardContent className="flex-1 overflow-y-auto scrollbar-thin min-h-0 pr-2">
          <div className="space-y-3">
            {[1, 2, 3].map((i) => (
              <div key={i} className="animate-pulse">
                <div className="h-24 bg-surface-elevated rounded-lg"></div>
              </div>
            ))}
          </div>
        </CardContent>
      </Card>
    );
  }

  // Error state
  if (error) {
    return (
      <Card className="h-full flex flex-col">
        <CardHeader>
          <div className="flex items-center justify-between">
            <CardTitle className="flex items-center gap-2">
              <Sparkles className="h-5 w-5 text-primary" />
              {t('insightsCard.title')}
            </CardTitle>
          </div>
        </CardHeader>
        <CardContent className="flex-1 overflow-y-auto scrollbar-thin min-h-0 pr-2">
          <div className="text-center py-8">
            <AlertCircle className="h-12 w-12 text-red-500 mx-auto mb-3" />
            <p className="text-red-500 font-semibold mb-2">{t('insightsCard.loadFailed')}</p>
            <p className="text-text-secondary text-sm mb-4">
              {error instanceof Error ? error.message : t('errors.unexpected')}
            </p>
            <Button onClick={handleRetry} variant="secondary" size="sm">
              <RefreshCw className="h-4 w-4 mr-2" />
              {t('insightsCard.retry')}
            </Button>
          </div>
        </CardContent>
      </Card>
    );
  }

  // Empty state
  if (!insights || insights.length === 0) {
    return (
      <Card className="h-full flex flex-col">
        <CardHeader>
          <div className="flex items-center justify-between">
            <CardTitle className="flex items-center gap-2">
              <Sparkles className="h-5 w-5 text-primary" />
              {t('insightsCard.title')}
            </CardTitle>
            <Button
              onClick={handleGenerate}
              isLoading={generateInsights.isPending}
              variant="primary"
              size="sm"
            >
              <Sparkles className="h-4 w-4 mr-2" />
              {t('insightsCard.generate')}
            </Button>
          </div>
        </CardHeader>
        <CardContent className="flex-1 overflow-y-auto scrollbar-thin min-h-0 pr-2">
          <div className="text-center py-8">
            <Lightbulb className="h-12 w-12 text-text-muted mx-auto mb-3" />
            <p className="text-text-secondary text-sm mb-2">{t('insightsCard.noInsights')}</p>
            <p className="text-text-muted text-xs">
              {t('insightsCard.generatePrompt')}
            </p>
          </div>
        </CardContent>
      </Card>
    );
  }

  // Success state with insights
  return (
    <Card className="h-full flex flex-col">
      <CardHeader>
        <div className="flex items-center justify-between">
          <div className="flex items-center gap-2">
            <Sparkles className="h-5 w-5 text-primary" />
            <CardTitle>{t('insightsCard.title')}</CardTitle>
            <HelpTooltip
              text={t('insightsCard.tooltip')}
              side="right"
            />
            <span className="text-xs text-text-secondary">
              {t('insightsCard.active', { count: insights.length })}
            </span>
          </div>
          <Button
            onClick={handleGenerate}
            isLoading={generateInsights.isPending}
            variant="secondary"
            size="sm"
          >
            <RefreshCw className="h-4 w-4 mr-2" />
            {t('insightsCard.refresh')}
          </Button>
        </div>
      </CardHeader>
      <CardContent className="flex-1 overflow-y-auto scrollbar-thin min-h-0 pr-2">
        <div className="space-y-3">
          {insights.map((insight) => (
            <InsightItem
              key={insight.id}
              insight={insight}
              onDismiss={handleDismiss}
              onClick={() => setSelectedInsight(insight)}
              isDismissing={dismissInsight.isPending}
              t={t}
            />
          ))}
        </div>
      </CardContent>

      <Dialog open={!!selectedInsight} onOpenChange={(open) => !open && setSelectedInsight(null)}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle className="flex flex-row items-start gap-3 pr-6">
              <div className="flex-shrink-0 mt-0.5">
                {selectedInsight && getInsightIcon(selectedInsight.type)}
              </div>
              <div className="flex-1 leading-tight mt-0.5">
                {selectedInsight?.title}
              </div>
            </DialogTitle>
          </DialogHeader>
          <div className="py-2">
            <p className="text-sm text-text-secondary leading-relaxed">
              {selectedInsight?.description}
            </p>
          </div>
          <div className="flex items-center justify-between mt-1">
            <span className="text-xs text-text-muted">
              {selectedInsight && formatDistanceToNow(new Date(selectedInsight.createdAt), { addSuffix: true, locale: dateLocale })}
            </span>
            {selectedInsight?.priority && (
              <Badge variant={getPriorityVariant(selectedInsight.priority)} size="sm" className="uppercase">
                {t(`insightsCard.priority.${selectedInsight.priority.toLowerCase()}`)}
              </Badge>
            )}
          </div>
        </DialogContent>
      </Dialog>
    </Card>
  );
}
