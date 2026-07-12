import { useQuery } from '@tanstack/react-query';

import { fetchSecurityConfig, type SecurityConfig } from '@/services/securityConfigService';

export type { SecurityConfig };

export function useSecurityConfig() {
  return useQuery<SecurityConfig>({
    queryKey: ['config', 'security'],
    queryFn: fetchSecurityConfig,
    staleTime: Infinity,
    gcTime: Infinity,
    retry: 1,
  });
}

export function resolveEncryptionEnabled(
  config: SecurityConfig | null | undefined,
  isError: boolean
): boolean {
  if (isError) {
    return true;
  }

  return config?.encryptionEnabled === false ? false : true;
}
