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

/**
 * Import service API client
 */
export const importService = {
  /**
   * Start import process from uploaded file
   */
  startImport: async (data: ImportProcessRequest): Promise<ImportSessionResponse> => {
    const encryptionKey = sessionStorage.getItem('encryption_session');
    if (!encryptionKey) {
      throw new Error('Encryption key not found');
    }

    const response = await apiClient.post<ImportSessionResponse>(
      '/import/process',
      data,
      {
        headers: {
          'X-Encryption-Session': encryptionKey,
        },
      }
    );
    return response.data;
  },

  /**
   * Get import session by ID
   */
  getSession: async (sessionId: number): Promise<ImportSessionResponse> => {
    const encryptionKey = sessionStorage.getItem('encryption_session');
    if (!encryptionKey) {
      throw new Error('Encryption key not found');
    }

    const response = await apiClient.get<ImportSessionResponse>(
      `/import/sessions/${sessionId}`,
      {
        headers: {
          'X-Encryption-Session': encryptionKey,
        },
      }
    );
    return response.data;
  },

  /**
   * Get transactions for review (includes AI categorization which may take longer)
   */
  getTransactions: async (sessionId: number): Promise<ImportTransactionDTO[]> => {
    const encryptionKey = sessionStorage.getItem('encryption_session');
    if (!encryptionKey) {
      throw new Error('Encryption key not found');
    }

    const response = await apiClient.get<ImportTransactionDTO[]>(
      `/import/sessions/${sessionId}/review`,
      {
        headers: {
          'X-Encryption-Session': encryptionKey,
        },
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
    data: ImportConfirmRequest
  ): Promise<ImportSessionResponse> => {
    const encryptionKey = sessionStorage.getItem('encryption_session');
    if (!encryptionKey) {
      throw new Error('Encryption key not found');
    }

    const response = await apiClient.post<ImportSessionResponse>(
      `/import/sessions/${sessionId}/confirm`,
      data,
      {
        headers: {
          'X-Encryption-Session': encryptionKey,
        },
      }
    );
    return response.data;
  },

  /**
   * Cancel import session
   */
  cancelImport: async (sessionId: number): Promise<ImportSessionResponse> => {
    const encryptionKey = sessionStorage.getItem('encryption_session');
    if (!encryptionKey) {
      throw new Error('Encryption key not found');
    }

    const response = await apiClient.post<ImportSessionResponse>(
      `/import/sessions/${sessionId}/cancel`,
      {},
      {
        headers: {
          'X-Encryption-Session': encryptionKey,
        },
      }
    );
    return response.data;
  },

  /**
   * List all import sessions for current user
   */
  listSessions: async (): Promise<ImportSessionResponse[]> => {
    const encryptionKey = sessionStorage.getItem('encryption_session');
    if (!encryptionKey) {
      throw new Error('Encryption key not found');
    }

    const response = await apiClient.get<ImportSessionResponse[]>(
      '/import/sessions',
      {
        headers: {
          'X-Encryption-Session': encryptionKey,
        },
      }
    );
    return response.data;
  },

  /**
   * Update the account for an import session
   */
  updateAccount: async (sessionId: number, accountId: number): Promise<ImportSessionResponse> => {
    const encryptionKey = sessionStorage.getItem('encryption_session');
    if (!encryptionKey) {
      throw new Error('Encryption key not found');
    }

    const response = await apiClient.put<ImportSessionResponse>(
      `/import/sessions/${sessionId}/account?accountId=${accountId}`,
      {},
      {
        headers: {
          'X-Encryption-Session': encryptionKey,
        },
      }
    );
    return response.data;
  },

  /**
   * Save modifying transactions back to the session
   */
  updateTransactions: async (sessionId: number, transactions: ImportTransactionDTO[]): Promise<ImportSessionResponse> => {
    const encryptionKey = sessionStorage.getItem('encryption_session');
    if (!encryptionKey) {
      throw new Error('Encryption key not found');
    }

    const response = await apiClient.put<ImportSessionResponse>(
      `/import/sessions/${sessionId}/transactions`,
      transactions,
      {
        headers: {
          'X-Encryption-Session': encryptionKey,
        },
      }
    );
    return response.data;
  },
};
