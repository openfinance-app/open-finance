import apiClient from './apiClient';
import type { OperationHistoryResponse, PageableResponse, EntityType } from '../types/history';

export const historyService = {
  /**
   * Retrieves a paginated list of operation history.
   *
   * @param page Page index (0-based)
   * @param size Page size
   * @param entityType Optional filter by entity type
   * @param since Optional ISO date-time string to filter entries created after this timestamp
   * @returns A promise resolving to a page of operation history response
   */
  getHistory: async (
    page: number = 0,
    size: number = 20,
    entityType?: EntityType,
    since?: string
  ): Promise<PageableResponse<OperationHistoryResponse>> => {
    const params = new URLSearchParams({
      page: page.toString(),
      size: size.toString(),
      sort: 'createdAt,desc',
    });

    if (entityType) {
      params.append('entityType', entityType);
    }

    if (since) {
      params.append('since', since);
    }

    const { data } = await apiClient.get<PageableResponse<OperationHistoryResponse>>(
      `/history?${params.toString()}`
    );
    return data;
  },

  /**
   * Undoes the specific history entry.
   *
   * @param historyId The history entry ID.
   * @returns The updated operation history entry.
   */
  undo: async (historyId: number): Promise<OperationHistoryResponse> => {
    const { data } = await apiClient.post<OperationHistoryResponse>(`/history/${historyId}/undo`);
    return data;
  },

  /**
   * Redoes the specific history entry.
   *
   * @param historyId The history entry ID.
   * @returns The updated operation history entry.
   */
  redo: async (historyId: number): Promise<OperationHistoryResponse> => {
    const { data } = await apiClient.post<OperationHistoryResponse>(`/history/${historyId}/redo`);
    return data;
  },
};
