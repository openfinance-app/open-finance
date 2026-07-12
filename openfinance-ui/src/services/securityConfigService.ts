import apiClient from '@/services/apiClient';

export interface SecurityConfig {
  encryptionEnabled: boolean;
}

export async function fetchSecurityConfig(): Promise<SecurityConfig> {
  const response = await apiClient.get<SecurityConfig>('/config/security');
  return response.data;
}
