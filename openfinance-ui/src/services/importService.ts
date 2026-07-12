/**
 * Import Service
 * Task 7.4.13: Create import service API client
 *
 * Provides API client functions for import operations
 */
import apiClient from './apiClient';
import type {
  ImportProcessRequest,
  ImportConfirmRequest,
  ImportSessionResponse,
  ImportTransactionDTO,
} from '@/types/import';
import { buildEncryptionHeaders } from '@/utils/encryption';

/**
 * Import service API client
 */
export const importService = {
  /**
   * Start import process from uploaded file
   */
  startImport: async (
    data: ImportProcessRequest,
    encryptionEnabled = true
  ): Promise<ImportSessionResponse> => {
    const response = await apiClient.post<ImportSessionResponse>('/import/process', data, {
      headers: buildEncryptionHeaders(encryptionEnabled),
    });
    return response.data;
  },

  /**
   * Get import session by ID
   */
  getSession: async (
    sessionId: number,
    encryptionEnabled = true
  ): Promise<ImportSessionResponse> => {
    const response = await apiClient.get<ImportSessionResponse>(`/import/sessions/${sessionId}`, {
      headers: buildEncryptionHeaders(encryptionEnabled),
    });
    return response.data;
  },

  /**
   * Get transactions for review (includes AI categorization which may take longer)
   */
  getTransactions: async (
    sessionId: number,
    encryptionEnabled = true
  ): Promise<ImportTransactionDTO[]> => {
    const response = await apiClient.get<ImportTransactionDTO[]>(
      `/import/sessions/${sessionId}/review`,
      {
        headers: buildEncryptionHeaders(encryptionEnabled),
        timeout: 600000, // 10 minutes — AI categorization with local models can be slow
      }
    );
    return response.data;
  },

  /**
   * Confirm import with category mappings
   */
  confirmImport: async (
    sessionId: number,
    data: ImportConfirmRequest,
    encryptionEnabled = true
  ): Promise<ImportSessionResponse> => {
    const response = await apiClient.post<ImportSessionResponse>(
      `/import/sessions/${sessionId}/confirm`,
      data,
      {
        headers: buildEncryptionHeaders(encryptionEnabled),
      }
    );
    return response.data;
  },

  /**
   * Cancel import session
   */
  cancelImport: async (
    sessionId: number,
    encryptionEnabled = true
  ): Promise<ImportSessionResponse> => {
    const response = await apiClient.post<ImportSessionResponse>(
      `/import/sessions/${sessionId}/cancel`,
      {},
      {
        headers: buildEncryptionHeaders(encryptionEnabled),
      }
    );
    return response.data;
  },

  /**
   * List all import sessions for current user
   */
  listSessions: async (encryptionEnabled = true): Promise<ImportSessionResponse[]> => {
    const response = await apiClient.get<ImportSessionResponse[]>('/import/sessions', {
      headers: buildEncryptionHeaders(encryptionEnabled),
    });
    return response.data;
  },

  /**
   * Update the account for an import session
   */
  updateAccount: async (
    sessionId: number,
    accountId: number,
    encryptionEnabled = true
  ): Promise<ImportSessionResponse> => {
    const response = await apiClient.put<ImportSessionResponse>(
      `/import/sessions/${sessionId}/account?accountId=${accountId}`,
      {},
      {
        headers: buildEncryptionHeaders(encryptionEnabled),
      }
    );
    return response.data;
  },

  /**
   * Save modifying transactions back to the session
   */
  updateTransactions: async (
    sessionId: number,
    transactions: ImportTransactionDTO[],
    encryptionEnabled = true
  ): Promise<ImportSessionResponse> => {
    const response = await apiClient.put<ImportSessionResponse>(
      `/import/sessions/${sessionId}/transactions`,
      transactions,
      {
        headers: buildEncryptionHeaders(encryptionEnabled),
      }
    );
    return response.data;
  },
};
