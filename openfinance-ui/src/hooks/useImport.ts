/**
 * Import hooks
 * Task 7.4.13: Create import service hooks
 *
 * Provides React Query hooks for import operations
 */
import {
  useMutation,
  useQuery,
  useQueryClient,
  type UseQueryResult,
  type UseMutationResult,
} from '@tanstack/react-query';
import { resolveEncryptionEnabled, useSecurityConfig } from '@/hooks/useSecurityConfig';
import { importService } from '@/services/importService';
import type {
  ImportProcessRequest,
  ImportSessionResponse,
  ImportSessionStatus,
  ImportTransactionDTO,
} from '@/types/import';

/**
 * Session statuses in which parsing has finished and review remains editable.
 */
const REVIEW_STATUSES: ImportSessionStatus[] = ['PARSED', 'REVIEWING'];

/**
 * Start import from uploaded file
 *
 * @example
 * const startImport = useStartImport();
 * startImport.mutate({ uploadId: 'abc123', accountId: 1 });
 */
export function useStartImport(): UseMutationResult<
  ImportSessionResponse,
  Error,
  ImportProcessRequest
> {
  const queryClient = useQueryClient();
  const securityConfig = useSecurityConfig();
  const encryptionEnabled = resolveEncryptionEnabled(securityConfig.data, securityConfig.isError);

  return useMutation<ImportSessionResponse, Error, ImportProcessRequest>({
    mutationFn: data => importService.startImport(data, encryptionEnabled),
    onSuccess: data => {
      // Cache the session data
      queryClient.setQueryData(['import-sessions', data.id, encryptionEnabled], data);
      queryClient.invalidateQueries({ queryKey: ['import-sessions'] });
    },
  });
}

/**
 * Get import session by ID with optional polling
 *
 * @param sessionId - Import session ID
 * @param options - Query options including poll interval
 *
 * @example
 * // Poll every 2 seconds while status is pending
 * const { data: session } = useImportSession(sessionId, { pollInterval: 2000 });
 */
export function useImportSession(
  sessionId: number | null,
  options?: { pollInterval?: number }
): UseQueryResult<ImportSessionResponse> {
  const securityConfig = useSecurityConfig();
  const encryptionEnabled = resolveEncryptionEnabled(securityConfig.data, securityConfig.isError);

  return useQuery<ImportSessionResponse>({
    queryKey: ['import-sessions', sessionId, encryptionEnabled],
    queryFn: () => {
      if (!sessionId) throw new Error('Session ID is required');
      return importService.getSession(sessionId, encryptionEnabled);
    },
    enabled: !!sessionId,
    // Poll if status is PENDING or PARSING
    refetchInterval: query => {
      const data = query.state.data;
      if (!data) return false;
      const isPending = ['PENDING', 'PARSING', 'IMPORTING'].includes(data.status);
      return isPending ? options?.pollInterval || 2000 : false;
    },
    refetchIntervalInBackground: false,
  });
}

/**
 * Get transactions for review
 *
 * The /review endpoint is only available while the session has NOT yet reached
 * a terminal state (COMPLETED, FAILED, CANCELLED). Passing the current session
 * status lets this hook disable itself automatically so it never fires a 400.
 *
 * @param sessionId     - Import session ID
 * @param sessionStatus - Current session status (used to gate fetching)
 *
 * @example
 * const { data: transactions } = useImportTransactions(sessionId, session?.status);
 */
export function useImportTransactions(
  sessionId: number | null,
  sessionStatus?: ImportSessionStatus
): UseQueryResult<ImportTransactionDTO[]> {
  const canReview = !!sessionStatus && REVIEW_STATUSES.includes(sessionStatus);
  const securityConfig = useSecurityConfig();
  const encryptionEnabled = resolveEncryptionEnabled(securityConfig.data, securityConfig.isError);

  return useQuery<ImportTransactionDTO[]>({
    queryKey: ['import-transactions', sessionId, encryptionEnabled],
    queryFn: ({ signal }) => {
      if (!sessionId) throw new Error('Session ID is required');
      return importService.getTransactions(sessionId, encryptionEnabled, signal);
    },
    // Wait for parsing and stop fetching once confirmation starts.
    enabled: !!sessionId && canReview,
    staleTime: 5 * 60 * 1000, // 5 minutes
    retry: 1, // AI categorization is slow; avoid aggressive retries
  });
}

/**
 * Confirm import with category mappings
 *
 * @example
 * const confirmImport = useConfirmImport();
 * confirmImport.mutate({
 *   sessionId: 123,
 *   accountId: 1,
 *   categoryMappings: { 'Groceries': 5 },
 *   skipDuplicates: true
 * });
 */
export function useConfirmImport(): UseMutationResult<
  ImportSessionResponse,
  Error,
  {
    sessionId: number;
    accountId: number | null;
    categoryMappings: Record<string, number>;
    skipDuplicates: boolean;
  }
> {
  const queryClient = useQueryClient();
  const securityConfig = useSecurityConfig();
  const encryptionEnabled = resolveEncryptionEnabled(securityConfig.data, securityConfig.isError);

  return useMutation<
    ImportSessionResponse,
    Error,
    {
      sessionId: number;
      accountId: number | null;
      categoryMappings: Record<string, number>;
      skipDuplicates: boolean;
    }
  >({
    mutationFn: ({ sessionId, ...data }) =>
      importService.confirmImport(sessionId, data, encryptionEnabled),
    onSuccess: (data, variables) => {
      // Confirmation is now asynchronous: the backend returns the session in
      // IMPORTING status (HTTP 202) and does the heavy work on a background
      // thread. Update the session cache so polling (useImportSession) takes
      // over immediately. The transaction/account/dashboard/category lists are
      // intentionally NOT invalidated here — the imported data does not exist
      // yet. They are refreshed once the session poll reports COMPLETED (see
      // ImportWizard's completion effect).
      queryClient.setQueryData(['import-sessions', variables.sessionId, encryptionEnabled], data);
      queryClient.invalidateQueries({ queryKey: ['import-sessions'] });
    },
    onError: (_error, variables) => {
      // A lost response does not prove that the server rejected confirmation.
      // Fetch status so a completed or running import can recover without another write.
      queryClient.invalidateQueries({ queryKey: ['import-sessions', variables.sessionId] });
    },
  });
}

/**
 * Update the account for an import session
 */
export function useUpdateAccount(): UseMutationResult<
  ImportSessionResponse,
  Error,
  { sessionId: number; accountId: number | null }
> {
  const queryClient = useQueryClient();
  const securityConfig = useSecurityConfig();
  const encryptionEnabled = resolveEncryptionEnabled(securityConfig.data, securityConfig.isError);

  return useMutation<ImportSessionResponse, Error, { sessionId: number; accountId: number | null }>(
    {
      mutationFn: ({ sessionId, accountId }) =>
        importService.updateAccount(sessionId, accountId, encryptionEnabled),
      onSuccess: async (data, variables) => {
        // Invalidation alone reuses an initial fetch with no cached data. Cancel it
        // first so a response calculated for the old account cannot win the race.
        await queryClient.cancelQueries({ queryKey: ['import-transactions', variables.sessionId] });
        queryClient.setQueryData(['import-sessions', variables.sessionId, encryptionEnabled], data);
        await Promise.all([
          queryClient.invalidateQueries({ queryKey: ['import-sessions'] }),
          queryClient.invalidateQueries({ queryKey: ['import-transactions', variables.sessionId] }),
        ]);
      },
    }
  );
}

/**
 * Update modifying transactions back to the session
 */
export function useUpdateTransactions(): UseMutationResult<
  ImportSessionResponse,
  Error,
  { sessionId: number; transactions: ImportTransactionDTO[] }
> {
  const queryClient = useQueryClient();
  const securityConfig = useSecurityConfig();
  const encryptionEnabled = resolveEncryptionEnabled(securityConfig.data, securityConfig.isError);

  return useMutation<
    ImportSessionResponse,
    Error,
    { sessionId: number; transactions: ImportTransactionDTO[] }
  >({
    mutationFn: ({ sessionId, transactions }) =>
      importService.updateTransactions(sessionId, transactions, encryptionEnabled),
    onSuccess: (data, variables) => {
      queryClient.setQueryData(['import-sessions', variables.sessionId, encryptionEnabled], data);
      queryClient.invalidateQueries({ queryKey: ['import-sessions'] });
      queryClient.invalidateQueries({ queryKey: ['import-transactions', variables.sessionId] });
    },
  });
}

/**
 * Cancel import session
 *
 * @example
 * const cancelImport = useCancelImport();
 * cancelImport.mutate(sessionId);
 */
export function useCancelImport(): UseMutationResult<ImportSessionResponse, Error, number> {
  const queryClient = useQueryClient();
  const securityConfig = useSecurityConfig();
  const encryptionEnabled = resolveEncryptionEnabled(securityConfig.data, securityConfig.isError);

  return useMutation<ImportSessionResponse, Error, number>({
    mutationFn: sessionId => importService.cancelImport(sessionId, encryptionEnabled),
    onSuccess: (data, sessionId) => {
      // Update session cache
      queryClient.setQueryData(['import-sessions', sessionId], data);
      queryClient.invalidateQueries({ queryKey: ['import-sessions'] });
    },
  });
}

/**
 * List all import sessions for current user
 *
 * @example
 * const { data: sessions } = useImportSessions();
 */
export function useImportSessions(): UseQueryResult<ImportSessionResponse[]> {
  const securityConfig = useSecurityConfig();
  const encryptionEnabled = resolveEncryptionEnabled(securityConfig.data, securityConfig.isError);

  return useQuery<ImportSessionResponse[]>({
    queryKey: ['import-sessions', encryptionEnabled],
    queryFn: () => importService.listSessions(encryptionEnabled),
    staleTime: 30 * 1000, // 30 seconds
  });
}
