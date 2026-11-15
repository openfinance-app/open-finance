import { fireEvent, screen, within } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { mockAuthentication, renderWithProviders } from '@/test/test-utils';
import i18n from '@/test/i18n-test';
import RssFeedCard from '@/components/dashboard/RssFeedCard';
import type { RssFeedItem } from '@/hooks/useRssFeed';
const state = vi.hoisted(() => ({ query: vi.fn(), refresh: vi.fn() }));
vi.mock('@/hooks/useRssFeed', () => ({ useFinanceNews: state.query }));
const item: RssFeedItem = {
  title: 'Market report',
  link: 'https://example.test/report',
  description: '<p>Useful details</p>',
  source: 'Financial news',
  pubDate: '2026-01-09T12:00:00Z',
};
function feeds(items: RssFeedItem[], fetching = false): void {
  state.query.mockReturnValue({
    data: items,
    isLoading: false,
    isFetching: fetching,
    error: null,
    refetch: state.refresh,
  });
}
beforeEach(async () => {
  vi.resetAllMocks();
  mockAuthentication();
  await i18n.changeLanguage('en');
  vi.useFakeTimers({ toFake: ['Date'] });
  vi.setSystemTime(new Date('2026-01-10T12:00:00Z'));
  feeds([item]);
});
afterEach(async () => {
  vi.useRealTimers();
  await i18n.changeLanguage('en');
});

describe('Finance news feed boundaries', () => {
  it.each(['en', 'fr'])(
    'localizes recent and older publication dates in both the list and article dialog: %s',
    async language => {
      await i18n.changeLanguage(language);
      feeds([
        item,
        {
          ...item,
          title: 'Older report',
          link: 'https://example.test/older',
          pubDate: '2025-12-01T12:00:00Z',
        },
      ]);
      renderWithProviders(<RssFeedCard />);
      expect(screen.getByText(language === 'fr' ? 'il y a 1 jour' : '1 day ago')).toBeVisible();
      fireEvent.click(screen.getByRole('heading', { name: 'Market report' }));
      expect(
        within(screen.getByRole('dialog')).getByText(
          language === 'fr' ? 'il y a 1 jour' : '1 day ago'
        )
      ).toBeVisible();
      fireEvent.keyDown(screen.getByRole('dialog'), { key: 'Escape' });
      fireEvent.click(screen.getByRole('heading', { name: 'Older report' }));
      expect(screen.getByRole('dialog')).toHaveTextContent(
        language === 'fr' ? '1 déc. 2025' : 'Dec 1, 2025'
      );
      expect(state.query).toHaveBeenCalledWith(language);
    }
  );

  it.each(['', 'not-a-date'])(
    'keeps an article readable when its publication date is absent or malformed: %s',
    date => {
      feeds([{ ...item, pubDate: date, source: '' }]);
      renderWithProviders(<RssFeedCard />);
      expect(screen.getByText(i18n.t('dashboard:cards.rssFeed.recently'))).toBeVisible();
      fireEvent.click(screen.getByRole('heading', { name: item.title }));
      expect(screen.getByRole('dialog')).toHaveTextContent('Useful details');
      expect(within(screen.getByRole('dialog')).getByRole('link')).toHaveAttribute(
        'href',
        item.link
      );
    }
  );

  it('sanitizes article markup while keeping readable text and safe links', () => {
    feeds([
      {
        ...item,
        title: '<b>Safe title</b>',
        description:
          '<p>Readable summary</p><img src="x" onerror="alert(1)"><script>alert(1)</script>',
      },
    ]);
    renderWithProviders(<RssFeedCard />);
    fireEvent.click(screen.getByRole('heading', { name: 'Safe title' }));
    const dialog = screen.getByRole('dialog');
    expect(dialog).toHaveTextContent('Readable summary');
    expect(dialog.querySelector('script')).toBeNull();
    expect(dialog.querySelector('img')).not.toHaveAttribute('onerror');
    expect(within(dialog).getByRole('link')).toHaveAttribute('rel', 'noopener noreferrer');
  });

  it('limits each source independently and promotes its next article after a dismissal', () => {
    feeds([
      ...Array.from({ length: 6 }, (_, index) => ({
        ...item,
        title: `Source A ${index}`,
        link: `https://example.test/a${index}`,
        source: 'A',
      })),
      { ...item, title: 'Source B', link: 'https://example.test/b', source: 'B' },
    ]);
    renderWithProviders(<RssFeedCard />);
    expect(screen.queryByText('Source A 5')).not.toBeInTheDocument();
    expect(screen.getByText('Source B')).toBeVisible();
    fireEvent.click(screen.getAllByTitle(i18n.t('dashboard:cards.rssFeed.discard'))[0]);
    expect(screen.queryByText('Source A 0')).not.toBeInTheDocument();
    expect(screen.getByText('Source A 5')).toBeVisible();
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
  });

  it.each(['constructor', 'toString', '__proto__'])(
    'accepts a source name that also names an object property: %s',
    source => {
      feeds([{ ...item, source }]);
      renderWithProviders(<RssFeedCard />);
      expect(screen.getByRole('heading', { name: item.title })).toBeVisible();
    }
  );

  it('ignores articles without a link and shows the discarded state after the last dismissal', () => {
    feeds([item, { ...item, title: 'No link', link: '' }]);
    renderWithProviders(<RssFeedCard />);
    expect(screen.queryByText('No link')).not.toBeInTheDocument();
    fireEvent.click(screen.getByTitle(i18n.t('dashboard:cards.rssFeed.discard')));
    expect(screen.getByText(i18n.t('dashboard:cards.rssFeed.allDiscarded'))).toBeVisible();
  });

  it('prevents duplicate refresh requests while already fetching', () => {
    feeds([item], true);
    renderWithProviders(<RssFeedCard />);
    const refresh = screen.getByRole('button', { name: i18n.t('dashboard:insightsCard.refresh') });
    expect(refresh).toBeDisabled();
    expect(refresh).toHaveAttribute('aria-busy', 'true');
    fireEvent.click(refresh);
    expect(state.refresh).not.toHaveBeenCalled();
  });

  it('allows retrying an unknown fetch error without exposing its raw object', () => {
    state.query.mockReturnValue({ error: { message: 'Internal payload' }, refetch: state.refresh });
    renderWithProviders(<RssFeedCard />);
    expect(screen.getByText(i18n.t('dashboard:errors.unexpected'))).toBeVisible();
    fireEvent.click(screen.getByRole('button', { name: i18n.t('dashboard:insightsCard.retry') }));
    expect(state.refresh).toHaveBeenCalledOnce();
  });
});
