import { isAxiosError } from 'axios';
import apiClient from '@/services/apiClient';
import { buildEncryptionHeaders } from '@/utils/encryption';
import type { BulkItem, BulkResource, BulkResult, MetadataPatch } from '@/types/bulk';

/** Each request is its own transaction/undo entry. Serial writes also avoid SQLite contention. */
export async function runBulkAction(
  resource: BulkResource,
  items: BulkItem[],
  patch: MetadataPatch | undefined,
  onProgress: (completed: number) => void,
  fallbackError: string
): Promise<BulkResult> {
  const result: BulkResult = { succeeded: [], failed: [] };
  // Children must be removed before their selected parents. Other domain guards stay on the server.
  const ordered = [...items].sort((a, b) => (b.depth ?? 0) - (a.depth ?? 0));
  const handled = new Set<number>();
  for (const item of ordered) {
    if (handled.has(item.id)) continue;
    // Deleting either transfer leg removes both; never send the same deletion twice.
    const group =
      !patch && resource === 'transactions' && item.transferId
        ? ordered.filter(other => other.transferId === item.transferId)
        : [item];
    try {
      const path = `/${resource}/${item.id}`;
      const config = { headers: buildEncryptionHeaders() };
      if (patch) {
        await apiClient.patch(`${path}/metadata`, patch, config);
      } else {
        await apiClient.delete(resource === 'accounts' ? `${path}/permanent` : path, config);
      }
      result.succeeded.push(...group.map(value => value.id));
    } catch (error) {
      const message = isAxiosError<{ message?: string }>(error)
        ? error.response?.data?.message || fallbackError
        : fallbackError;
      result.failed.push(...group.map(value => ({ item: value, message })));
    }
    group.forEach(value => handled.add(value.id));
    onProgress(handled.size);
  }
  return result;
}
