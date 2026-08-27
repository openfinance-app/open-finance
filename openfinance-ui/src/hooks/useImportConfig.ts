import { useQuery } from '@tanstack/react-query';

import { fetchImportConfig, type ImportConfig } from '@/services/importConfigService';

export type { ImportConfig };

export function useImportConfig() {
  return useQuery<ImportConfig>({
    queryKey: ['config', 'import'],
    queryFn: fetchImportConfig,
    staleTime: Infinity,
    gcTime: Infinity,
    retry: 1,
  });
}

/** Fail-open: Skrooge JSON stays available unless the backend explicitly disables it. */
export function resolveSkroogeJsonEnabled(
  config: ImportConfig | null | undefined,
  isError: boolean
): boolean {
  if (isError) {
    return true;
  }

  return config?.skroogeJsonEnabled !== false;
}
