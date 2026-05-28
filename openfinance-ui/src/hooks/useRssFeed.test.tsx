import { describe, it, expect, vi, beforeEach } from 'vitest';
import { renderHook, waitFor } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import type { ReactNode } from 'react';
import apiClient from '@/services/apiClient';

vi.mock('@/services/apiClient', () => ({
  default: {
    get: vi.fn(),
  },
}));

import { useFinanceNews } from './useRssFeed';

function createWrapper() {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });
  return function Wrapper({ children }: { children: ReactNode }) {
    return <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>;
  };
}

describe('useFinanceNews', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    sessionStorage.setItem('encryption_key', 'test-key');
  });

  it('fetches finance news from API', async () => {
    const mockNews = [
      { title: 'Finance News', link: 'https://example.com', description: 'Desc', pubDate: '2024-01-01', source: 'Reuters' },
    ];
    vi.mocked(apiClient.get).mockResolvedValue({ data: mockNews });

    const { result } = renderHook(() => useFinanceNews('en'), { wrapper: createWrapper() });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(result.current.data).toEqual(mockNews);
  });
});
