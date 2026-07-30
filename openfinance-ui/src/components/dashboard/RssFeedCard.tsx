import { useState } from 'react';
import { Rss, ExternalLink, RefreshCw, AlertCircle, Newspaper, X } from 'lucide-react';
import { useFinanceNews, type RssFeedItem } from '@/hooks/useRssFeed';
import { Card, CardHeader, CardTitle, CardContent } from '@/components/ui/Card';
import { Badge } from '@/components/ui/Badge';
import { Button } from '@/components/ui/Button';
import { HelpTooltip } from '@/components/ui/HelpTooltip';
import { Dialog, DialogContent, DialogHeader, DialogTitle } from '@/components/ui/Dialog';
import { formatDistanceToNow, differenceInDays, format } from 'date-fns';
import { fr, enUS } from 'date-fns/locale';
import { useTranslation } from 'react-i18next';
import DOMPurify from 'dompurify';

interface FeedItemProps {
  item: RssFeedItem;
  onClick: () => void;
  onDiscard: (link: string) => void;
}

function FeedItem({ item, onClick, onDiscard }: FeedItemProps) {
  const { t, i18n } = useTranslation('dashboard');
  const dateLocale = i18n.language === 'fr' ? fr : enUS;

  // Sometimes RSS feeds contain HTML entities in titles
  const cleanTitle = DOMPurify.sanitize(item.title, { ALLOWED_TAGS: [] });

  return (
    <div
      onClick={onClick}
      className={`
        group relative py-3 px-4 rounded-lg border border-border bg-surface-elevated 
        hover:bg-surface-elevated/70 transition-all duration-300 cursor-pointer
      `}
    >
      <div className="flex items-start gap-3">
        <div className="flex-shrink-0 mt-0.5">
          <Newspaper className="h-5 w-5 text-primary" />
        </div>
        <div className="flex-1 min-w-0">
          <div className="flex items-start justify-between gap-2 mb-1">
            <h4 className="text-sm font-semibold text-text-primary leading-tight flex-1 line-clamp-2">
              {cleanTitle}
            </h4>
            <button
              onClick={(e) => {
                e.stopPropagation();
                if (item.link) {
                  onDiscard(item.link);
                }
              }}
              className="p-1 -mr-2 hover:bg-surface rounded-full text-text-muted hover:text-red-500 transition-colors flex-shrink-0"
              title={t('cards.rssFeed.discard')}
            >
              <X className="h-4 w-4" />
            </button>
          </div>
          <div className="flex items-center justify-between">
            <span className="text-xs text-text-muted">
              {item.pubDate ? (
                differenceInDays(new Date(), new Date(item.pubDate)) <= 7
                  ? formatDistanceToNow(new Date(item.pubDate), { addSuffix: true, locale: dateLocale })
                  : format(new Date(item.pubDate), 'PP', { locale: dateLocale })
              )                   : t('cards.rssFeed.recently')}
            </span>
            <Badge variant="info" size="sm" className="opacity-70 text-[10px] uppercase">
              {item.source}
            </Badge>
          </div>
        </div>
      </div>
    </div>
  );
}

export default function RssFeedCard() {
  const { t, i18n } = useTranslation('dashboard');
  const dateLocale = i18n.language === 'fr' ? fr : enUS;
  const { data: feeds, isLoading, error, refetch, isFetching } = useFinanceNews(i18n.language);
  const [selectedItem, setSelectedItem] = useState<RssFeedItem | null>(null);
  const [discardedLinks, setDiscardedLinks] = useState<string[]>([]);

  const handleRetry = () => {
    refetch();
  };

  const handleDiscard = (link: string) => {
    setDiscardedLinks(prev => [...prev, link]);
  };

  // Loading state
  if (isLoading) {
    return (
      <Card className="h-full flex flex-col">
        <CardHeader>
          <div className="flex items-center justify-between">
            <CardTitle className="flex items-center gap-2">
              <Rss className="h-5 w-5 text-primary" />
              {t('cards.rssFeed.title')}
            </CardTitle>
          </div>
        </CardHeader>
        <CardContent className="flex-1 overflow-y-auto scrollbar-thin min-h-0 pr-2">
          <div className="space-y-3">
            {[1, 2, 3, 4].map((i) => (
              <div key={i} className="animate-pulse">
                <div className="h-20 bg-surface-elevated rounded-lg"></div>
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
              <Rss className="h-5 w-5 text-primary" />
              {t('cards.rssFeed.title')}
            </CardTitle>
          </div>
        </CardHeader>
        <CardContent className="flex-1 overflow-y-auto scrollbar-thin min-h-0 pr-2">
          <div className="text-center py-8">
            <AlertCircle className="h-12 w-12 text-red-500 mx-auto mb-3" />
            <p className="text-red-500 font-semibold mb-2">{t('cards.rssFeed.loadError')}</p>
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
  if (!feeds || feeds.length === 0) {
    return (
      <Card className="h-full flex flex-col">
        <CardHeader>
          <div className="flex items-center justify-between">
            <CardTitle className="flex items-center gap-2">
              <Rss className="h-5 w-5 text-primary" />
              {t('cards.rssFeed.title')}
            </CardTitle>
          </div>
        </CardHeader>
        <CardContent className="flex-1 overflow-y-auto scrollbar-thin min-h-0 pr-2">
          <div className="text-center py-8">
            <Newspaper className="h-12 w-12 text-text-muted mx-auto mb-3" />
            <p className="text-text-secondary text-sm mb-2">{t('cards.rssFeed.empty')}</p>
          </div>
        </CardContent>
      </Card>
    );
  }

  // Filter out discarded links and aggregate top 5 per source
  const filteredFeeds = feeds.filter(item => item.link && !discardedLinks.includes(item.link));
  const sourceCount: Record<string, number> = {};
  const displayedFeeds: RssFeedItem[] = [];

  for (const feed of filteredFeeds) {
    const source = feed.source || t('cards.rssFeed.unknownSource');
    if (!sourceCount[source]) {
      sourceCount[source] = 0;
    }
    if (sourceCount[source] < 5) {
      displayedFeeds.push(feed);
      sourceCount[source]++;
    }
  }

  return (
    <Card className="h-full flex flex-col">
      <CardHeader>
        <div className="flex items-center justify-between">
          <div className="flex items-center gap-2">
            <Rss className="h-5 w-5 text-primary" />
            <CardTitle>{t('cards.rssFeed.title')}</CardTitle>
            <HelpTooltip
              text={t('cards.rssFeed.rssTooltip')}
              side="right"
            />
          </div>
          <Button
            onClick={handleRetry}
            isLoading={isFetching}
            variant="secondary"
            size="sm"
          >
            <RefreshCw className={`h-4 w-4 mr-2 ${isFetching ? 'animate-spin' : ''}`} />
            {t('insightsCard.refresh')}
          </Button>
        </div>
      </CardHeader>
      <CardContent className="flex-1 overflow-y-auto scrollbar-thin min-h-0 pr-2">
        <div className="space-y-3">
          {displayedFeeds.length > 0 ? displayedFeeds.map((item, idx) => (
            <FeedItem
              key={idx}
              item={item}
              onClick={() => setSelectedItem(item)}
              onDiscard={handleDiscard}
            />
          )) : (
            <div className="text-center py-8">
              <p className="text-text-secondary text-sm">{t('cards.rssFeed.allDiscarded')}</p>
            </div>
          )}
        </div>
      </CardContent>

      <Dialog open={!!selectedItem} onOpenChange={(open) => !open && setSelectedItem(null)}>
        <DialogContent className="max-w-xl">
          <DialogHeader>
            <DialogTitle className="flex flex-row items-start gap-3 pr-6">
              <div className="flex-shrink-0 mt-0.5">
                <Newspaper className="h-5 w-5 text-primary" />
              </div>
              <div className="flex-1 leading-tight mt-0.5" dangerouslySetInnerHTML={{ __html: DOMPurify.sanitize(selectedItem?.title || '', { ALLOWED_TAGS: [] }) }}></div>
            </DialogTitle>
          </DialogHeader>
          <div className="py-4">
            <div 
              className="text-sm text-text-secondary leading-relaxed prose prose-sm dark:prose-invert max-w-none"
              dangerouslySetInnerHTML={{ __html: DOMPurify.sanitize(selectedItem?.description || '') }} 
            />
          </div>
          <div className="flex items-center justify-between mt-4 pt-4 border-t border-border">
            <div className="flex items-center gap-3">
              {selectedItem?.source && (
                <Badge variant="outline" size="sm">
                  {selectedItem.source}
                </Badge>
              )}
              <span className="text-xs text-text-muted">
                {selectedItem?.pubDate && (
                  differenceInDays(new Date(), new Date(selectedItem.pubDate)) <= 7
                    ? formatDistanceToNow(new Date(selectedItem.pubDate), { addSuffix: true, locale: dateLocale })
                    : format(new Date(selectedItem.pubDate), 'PP', { locale: dateLocale })
                )}
              </span>
            </div>
            
            {selectedItem?.link && (
              <a 
                href={selectedItem.link} 
                target="_blank" 
                rel="noopener noreferrer"
                className="flex items-center gap-1.5 text-sm font-medium text-primary hover:text-primary-hover transition-colors"
              >
                {t('cards.rssFeed.readArticle')}
                <ExternalLink className="h-4 w-4" />
              </a>
            )}
          </div>
        </DialogContent>
      </Dialog>
    </Card>
  );
}
