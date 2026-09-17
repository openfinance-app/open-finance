import { useQuery } from '@tanstack/react-query';
import apiClient from '@/services/apiClient';

interface Category {
  id: number;
  name: string;
  type: string;
  parentId?: number | null;
  parentName?: string | null;
  icon?: string;
  color?: string;
  isSystem?: boolean;
  subcategoryCount?: number;
}

export function useCategories() {
  return useQuery<Category[]>({
    queryKey: ['categories'],
    queryFn: async () => {
      const response = await apiClient.get<Category[]>('/categories');
      return response.data;
    },
    staleTime: 5 * 60 * 1000,
  });
}
