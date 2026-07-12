import { useQuery } from '@tanstack/react-query';
import apiClient from '@/services/apiClient';
import { buildEncryptionHeaders } from '@/utils/encryption';

export interface RssFeedItem {
  title: string;
  link: string;
  description: string;
  pubDate: string;
  source: string;
}

/**
 * Fetch top finance news via backend RSS proxy
 * GET /api/v1/rss/finance
 */
export function useFinanceNews(language: string) {
  return useQuery<RssFeedItem[]>({
    queryKey: ['rss', 'finance', language],
    queryFn: async () => {
      const response = await apiClient.get<RssFeedItem[]>('/rss/finance', {
        headers: {
          ...buildEncryptionHeaders(),
          'Accept-Language': language
        },
      });
      return response.data;
    },
    staleTime: 15 * 60 * 1000, // 15 minutes
    retry: 2,
  });
}
