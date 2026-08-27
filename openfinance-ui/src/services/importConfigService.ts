import apiClient from '@/services/apiClient';

export interface ImportConfig {
  skroogeJsonEnabled: boolean;
}

export async function fetchImportConfig(): Promise<ImportConfig> {
  const response = await apiClient.get<ImportConfig>('/config/import');
  return response.data;
}
