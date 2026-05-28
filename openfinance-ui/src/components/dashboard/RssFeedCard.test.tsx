import { beforeEach, describe, expect, it, vi } from 'vitest';
import { screen, fireEvent } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import RssFeedCard from './RssFeedCard';
import { mockAuthentication, renderWithProviders } from '@/test/test-utils';

const useFinanceNewsMock = vi.fn();
const refetchMock = vi.fn();

vi.mock('dompurify', () => ({
  default: {
    sanitize: (value: string) => value,
  },
}));

vi.mock('@/hooks/useRssFeed', () => ({
  useFinanceNews: (...args: unknown[]) => useFinanceNewsMock(...args),
}));

describe('RssFeedCard', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockAuthentication();

    useFinanceNewsMock.mockReturnValue({
      data: [],
      isLoading: false,
      error: null,
      refetch: refetchMock,
      isFetching: false,
    });
  });

  it('renders loading state', () => {
    useFinanceNewsMock.mockReturnValue({
      data: undefined,
      isLoading: true,
      error: null,
      refetch: refetchMock,
      isFetching: false,
    });

    renderWithProviders(<RssFeedCard />);
    expect(screen.getByText('Finance News')).toBeInTheDocument();
  });

  it('renders error state and retries', async () => {
    const user = userEvent.setup();
    useFinanceNewsMock.mockReturnValue({
      data: undefined,
      isLoading: false,
      error: new Error('network failed'),
      refetch: refetchMock,
      isFetching: false,
    });

    renderWithProviders(<RssFeedCard />);
    expect(screen.getByText('Failed to load news')).toBeInTheDocument();

    await user.click(screen.getByRole('button', { name: /retry/i }));
    expect(refetchMock).toHaveBeenCalled();
  });

  it('renders feed items, opens dialog, and discards an item', async () => {
    const user = userEvent.setup();
    useFinanceNewsMock.mockReturnValue({
      data: [
        {
          title: 'Market updates today',
          description: '<p>Some details</p>',
          link: 'https://example.com/a',
          source: 'Source A',
          pubDate: '2026-05-20T00:00:00.000Z',
        },
        {
          title: 'Rates and inflation',
          description: '<p>Another detail</p>',
          link: 'https://example.com/b',
          source: 'Source A',
          pubDate: '2026-05-18T00:00:00.000Z',
        },
      ],
      isLoading: false,
      error: null,
      refetch: refetchMock,
      isFetching: false,
    });

    renderWithProviders(<RssFeedCard />);

    const discardButtons = screen.getAllByTitle('Discard this feed item');
    fireEvent.click(discardButtons[0]);
    expect(screen.queryByText('Market updates today')).not.toBeInTheDocument();
  });
});